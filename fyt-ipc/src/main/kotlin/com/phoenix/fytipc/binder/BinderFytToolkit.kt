package com.phoenix.fytipc.binder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.phoenix.fytipc.ConnectionState
import com.phoenix.fytipc.FytModule
import com.phoenix.fytipc.FytToolkit
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.SyuBinder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Raw-Binder implementation of [FytToolkit] against the SYU AIDL surface (no generated AIDL; the
 * descriptors and transaction ids are in [SyuBinder]).
 *
 * Binding starts in the constructor. Every bind attempt gets its own [ServiceConnection] so callbacks
 * from a superseded attempt are ignored. On disconnect or binding death while the service package is
 * still installed, the toolkit is CONNECTING and rebinds with exponential backoff ([BACKOFF_BASE_MS]
 * doubling to [BACKOFF_MAX_MS]); if the system reconnects the existing binding first, the pending
 * retry is dropped. When the service is not there at all (the intent does not resolve, `bindService`
 * returns false, a null binding, or the binding dies with the package gone) the toolkit is UNAVAILABLE
 * and retries every [UNAVAILABLE_RETRY_MS], so installing the service later recovers on its own.
 * Each successful connection bumps a generation number; modules drop remote handles from older
 * generations and re-register their live subscriptions.
 *
 * Binder calls run on [ioDispatcher]. [module] handles are cached and usable before connection:
 * `cmd`/`get` suspend until connected and throw [FytTimeoutException] after [connectTimeoutMs];
 * `updates` never times out and registers whenever a connection is (re)established.
 */
internal class BinderFytToolkit(
    private val context: Context,
    internal val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    internal val connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
) : FytToolkit {

    /** A live toolkit binder and the bind generation it belongs to. */
    internal class Connected(val binder: IBinder, val generation: Int)

    internal val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val connected = MutableStateFlow<Connected?>(null)
    private val modules = ConcurrentHashMap<Int, BinderFytModule>()

    private val updatesBus = MutableSharedFlow<ModuleUpdate>(
        extraBufferCapacity = 1024,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val allUpdates: Flow<ModuleUpdate> = updatesBus.asSharedFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var connection: Connection? = null
    private var generation = 0
    private var connects = 0
    private var failures = 0
    private var closed = false
    private val retry = Runnable { bind() }

    init {
        bind()
    }

    override fun module(id: Int): FytModule = modules.getOrPut(id) { BinderFytModule(id, this) }

    /** Current connection or null. Never suspends. */
    internal fun current(): Connected? = connected.value

    /** Suspends until connected or throws [FytTimeoutException] after [connectTimeoutMs]. */
    internal suspend fun awaitConnected(): Connected {
        check(!closed) { "FytToolkit is closed" }
        connected.value?.let { return it }
        return withTimeoutOrNull(connectTimeoutMs) { connected.filterNotNull().first() }
            ?: throw FytTimeoutException(
                "${SyuBinder.SERVICE_PACKAGE} not connected after ${connectTimeoutMs} ms (state=${_connectionState.value})",
            )
    }

    internal fun publish(update: ModuleUpdate) {
        updatesBus.tryEmit(update)
    }

    override fun close() {
        val toUnregister: List<BinderFytModule>
        synchronized(lock) {
            if (closed) return
            closed = true
            mainHandler.removeCallbacks(retry)
            toUnregister = modules.values.toList()
            unbindCurrentLocked()
            connected.value = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }
        // Best effort, off the caller's thread: the vendor keeps calling a callback it still holds.
        Thread({ toUnregister.forEach { it.unregisterAllBlocking() } }, "fyt-ipc-close").start()
        scope.cancel()
    }

    private fun bind() {
        synchronized(lock) {
            if (closed) return
            mainHandler.removeCallbacks(retry)
            unbindCurrentLocked()
            val intent = Intent(SyuBinder.SERVICE_ACTION).setPackage(SyuBinder.SERVICE_PACKAGE)
            if (!resolves(intent)) {
                markUnavailableLocked("${SyuBinder.SERVICE_ACTION} does not resolve; is ${SyuBinder.SERVICE_PACKAGE} installed?")
                return
            }
            val conn = Connection(++generation)
            connection = conn
            _connectionState.value = ConnectionState.CONNECTING
            val ok = try {
                context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                Log.w(TAG, "bindService rejected", e)
                false
            }
            if (!ok) {
                unbindCurrentLocked()
                markUnavailableLocked("bindService(${SyuBinder.SERVICE_ACTION}) returned false")
            }
        }
    }

    private fun resolves(intent: Intent): Boolean = servicePackageInstalled() &&
        runCatching { context.packageManager.resolveService(intent, 0) != null }.getOrDefault(true)

    private fun servicePackageInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(SyuBinder.SERVICE_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** The service is not there at all: report UNAVAILABLE and look again in [UNAVAILABLE_RETRY_MS]. */
    private fun markUnavailableLocked(why: String) {
        Log.w(TAG, why)
        connected.value = null
        _connectionState.value = ConnectionState.UNAVAILABLE
        failures = 0
        if (closed) return
        mainHandler.removeCallbacks(retry)
        mainHandler.postDelayed(retry, UNAVAILABLE_RETRY_MS)
        Log.i(TAG, "unavailable; retrying in $UNAVAILABLE_RETRY_MS ms")
    }

    private fun unbindCurrentLocked() {
        val old = connection ?: return
        connection = null
        runCatching { context.unbindService(old) }
    }

    private fun scheduleRetryLocked() {
        if (closed) return
        val delay = (BACKOFF_BASE_MS shl failures.coerceAtMost(MAX_SHIFT)).coerceAtMost(BACKOFF_MAX_MS)
        failures++
        mainHandler.removeCallbacks(retry)
        mainHandler.postDelayed(retry, delay)
        Log.i(TAG, "rebind in $delay ms (attempt $failures)")
    }

    private fun onLost(conn: Connection, why: String) {
        synchronized(lock) {
            if (closed || connection !== conn) return
            if (!servicePackageInstalled()) {
                markUnavailableLocked("toolkit $why and ${SyuBinder.SERVICE_PACKAGE} is not installed")
                return
            }
            Log.w(TAG, "toolkit $why")
            connected.value = null
            _connectionState.value = ConnectionState.CONNECTING
            scheduleRetryLocked()
        }
    }

    private fun onUnavailable(conn: Connection, why: String) {
        synchronized(lock) {
            if (closed || connection !== conn) return
            markUnavailableLocked("toolkit $why")
        }
    }

    private inner class Connection(val generation: Int) : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            synchronized(lock) {
                if (closed || connection !== this) return
                if (service == null) {
                    markUnavailableLocked("toolkit connected with a null binder")
                    return
                }
                mainHandler.removeCallbacks(retry)
                failures = 0
                // A fresh generation per connect, also when the system reconnects this same binding.
                val gen = ++connects
                connected.value = Connected(service, gen)
                _connectionState.value = ConnectionState.CONNECTED
                Log.i(TAG, "connected to $name (attempt $generation, generation $gen)")
            }
            modules.values.forEach { it.onConnected() }
        }

        override fun onServiceDisconnected(name: ComponentName?) = onLost(this, "disconnected")

        override fun onBindingDied(name: ComponentName?) = onLost(this, "binding died")

        override fun onNullBinding(name: ComponentName?) = onUnavailable(this, "returned a null binding")
    }

    companion object {
        private const val TAG = "FytToolkit"
        const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000L
        const val BACKOFF_BASE_MS = 500L
        const val BACKOFF_MAX_MS = 30_000L
        const val UNAVAILABLE_RETRY_MS = 30_000L
        private const val MAX_SHIFT = 6
    }
}
