package com.phoenix.fytipc.binder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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
 * from a superseded attempt are ignored. On disconnect, binding death, null binding or a failed
 * `bindService` the toolkit unbinds and rebinds with exponential backoff ([BACKOFF_BASE_MS] doubling
 * to [BACKOFF_MAX_MS]); if the system reconnects the existing binding first, the pending retry is
 * dropped. Each successful connection bumps a generation number; modules drop remote handles from
 * older generations and re-register their live subscriptions.
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
            val conn = Connection(++generation)
            connection = conn
            _connectionState.value = ConnectionState.CONNECTING
            val intent = Intent(SyuBinder.SERVICE_ACTION).setPackage(SyuBinder.SERVICE_PACKAGE)
            val ok = try {
                context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                Log.w(TAG, "bindService rejected", e)
                false
            }
            if (!ok) {
                Log.w(TAG, "bindService(${SyuBinder.SERVICE_ACTION}) returned false; is ${SyuBinder.SERVICE_PACKAGE} installed?")
                _connectionState.value = ConnectionState.UNAVAILABLE
                scheduleRetryLocked()
            }
        }
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

    private fun onLost(conn: Connection, state: ConnectionState, why: String) {
        synchronized(lock) {
            if (closed || connection !== conn) return
            Log.w(TAG, "toolkit $why")
            connected.value = null
            _connectionState.value = state
            scheduleRetryLocked()
        }
    }

    private inner class Connection(val generation: Int) : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            synchronized(lock) {
                if (closed || connection !== this) return
                if (service == null) {
                    _connectionState.value = ConnectionState.UNAVAILABLE
                    scheduleRetryLocked()
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

        override fun onServiceDisconnected(name: ComponentName?) =
            onLost(this, ConnectionState.CONNECTING, "disconnected")

        override fun onBindingDied(name: ComponentName?) =
            onLost(this, ConnectionState.CONNECTING, "binding died")

        override fun onNullBinding(name: ComponentName?) =
            onLost(this, ConnectionState.UNAVAILABLE, "returned a null binding")
    }

    companion object {
        private const val TAG = "FytToolkit"
        const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000L
        const val BACKOFF_BASE_MS = 500L
        const val BACKOFF_MAX_MS = 30_000L
        private const val MAX_SHIFT = 6
    }
}
