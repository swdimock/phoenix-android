package com.phoenix.fytipc.binder

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import com.phoenix.fytipc.FytModule
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.Modules
import com.phoenix.fytipc.SyuBinder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * One `IRemoteModule` handle. Owns a single `IModuleCallback` stub for the module and fans
 * deliveries out by code to every [updates] collector.
 *
 * Remote registration is reconciled in [sync]: the set of codes with at least one collector is
 * registered, codes nobody collects any more are unregistered, and a new connection generation
 * starts from scratch (the vendor replays cached values on each registration). A collector that
 * joins a code another collector already registered gets the locally cached last value instead,
 * because registering the same callback twice with the vendor may duplicate deliveries.
 */
internal class BinderFytModule(
    override val id: Int,
    private val host: BinderFytToolkit,
) : FytModule {

    private class Remote(val generation: Int, val binder: IBinder?)

    private class Subscriber(val codes: Set<Int>, val channel: SendChannel<ModuleUpdate>) {
        @Volatile var primed = false
    }

    private val callback = CallbackStub()
    private val mutex = Mutex()
    private val subscribers = CopyOnWriteArrayList<Subscriber>()
    private val lastValues = ConcurrentHashMap<Int, ModuleUpdate>()

    @Volatile private var remote: Remote? = null

    // Guarded by [mutex].
    private val registered = HashSet<Int>()
    private var registeredGeneration = -1

    override suspend fun cmd(code: Int, ints: IntArray?, floats: FloatArray?, strings: Array<String>?) {
        val binder = remoteFor(host.awaitConnected()) ?: run {
            Log.w(TAG, "cmd($code) dropped: module ${Modules.name(id)} unavailable")
            return
        }
        withContext(host.ioDispatcher) {
            try {
                binder.call(SyuBinder.TX_MODULE_CMD, SyuBinder.DESCRIPTOR_MODULE, {
                    writeModuleArgs(code, ints, floats, strings)
                }) { }
            } catch (e: RemoteException) {
                Log.w(TAG, "cmd($code) on ${Modules.name(id)} failed", e)
            } catch (e: RuntimeException) {
                Log.w(TAG, "cmd($code) on ${Modules.name(id)} threw remotely", e)
            }
        }
    }

    override suspend fun get(code: Int, ints: IntArray?, floats: FloatArray?, strings: Array<String>?): ModuleUpdate? {
        val binder = remoteFor(host.awaitConnected()) ?: return null
        return withContext(host.ioDispatcher) {
            try {
                binder.call(SyuBinder.TX_MODULE_GET, SyuBinder.DESCRIPTOR_MODULE, {
                    writeModuleArgs(code, ints, floats, strings)
                }) { readModuleObject(code) }
            } catch (e: Exception) {
                Log.w(TAG, "get($code) on ${Modules.name(id)} failed", e)
                null
            }
        }
    }

    override fun updates(vararg codes: Int): Flow<ModuleUpdate> {
        val wanted = codes.toSet()
        return callbackFlow {
            val sub = Subscriber(wanted, channel)
            subscribers += sub
            host.scope.launch { sync() }
            awaitClose {
                subscribers -= sub
                host.scope.launch { sync() }
            }
        }.buffer(Channel.UNLIMITED)
    }

    /** Called by the toolkit after every (re)connect. */
    internal fun onConnected() {
        subscribers.forEach { it.primed = false }
        host.scope.launch { sync() }
    }

    /** Synchronous best-effort unregister of everything, used by [BinderFytToolkit.close]. */
    internal fun unregisterAllBlocking() {
        val binder = remote?.binder ?: return
        val codes = synchronized(registered) { registered.toList().also { registered.clear() } }
        codes.forEach { code -> runCatching { unregister(binder, code) } }
    }

    private suspend fun sync() = mutex.withLock {
        val conn = host.current() ?: return@withLock
        val binder = remoteFor(conn) ?: return@withLock
        if (registeredGeneration != conn.generation) {
            synchronized(registered) { registered.clear() }
            registeredGeneration = conn.generation
        }
        val desired = subscribers.flatMapTo(HashSet()) { it.codes }
        val alreadyRegistered = synchronized(registered) { registered.toSet() }
        withContext(host.ioDispatcher) {
            for (code in desired - alreadyRegistered) {
                try {
                    register(binder, code)
                    synchronized(registered) { registered += code }
                } catch (e: Exception) {
                    Log.w(TAG, "register($code) on ${Modules.name(id)} failed", e)
                }
            }
            for (code in alreadyRegistered - desired) {
                runCatching { unregister(binder, code) }
                    .onFailure { Log.w(TAG, "unregister($code) on ${Modules.name(id)} failed", it) }
                synchronized(registered) { registered -= code }
            }
        }
        // Collectors that joined codes which were already registered: replay from the local cache.
        for (sub in subscribers) {
            if (sub.primed) continue
            sub.primed = true
            for (code in sub.codes intersect alreadyRegistered) {
                lastValues[code]?.let { sub.channel.trySend(it) }
            }
        }
    }

    /** Remote module binder for [conn], fetched once per connection generation. */
    private suspend fun remoteFor(conn: BinderFytToolkit.Connected): IBinder? {
        remote?.takeIf { it.generation == conn.generation }?.let { return it.binder }
        val binder = withContext(host.ioDispatcher) {
            try {
                conn.binder.call(SyuBinder.TX_TOOLKIT_GET_REMOTE_MODULE, SyuBinder.DESCRIPTOR_TOOLKIT, {
                    writeInt(id)
                }) { readStrongBinder() }
            } catch (e: Exception) {
                Log.w(TAG, "getRemoteModule($id) failed", e)
                return@withContext null
            }
        }
        if (binder == null) Log.w(TAG, "getRemoteModule($id) returned null")
        remote = Remote(conn.generation, binder)
        return binder
    }

    private fun register(binder: IBinder, code: Int) {
        binder.call(SyuBinder.TX_MODULE_REGISTER, SyuBinder.DESCRIPTOR_MODULE, {
            writeStrongBinder(callback)
            writeInt(code)
            writeInt(SyuBinder.REGISTER_FLAG_DEFAULT)
        }) { }
    }

    private fun unregister(binder: IBinder, code: Int) {
        binder.call(SyuBinder.TX_MODULE_UNREGISTER, SyuBinder.DESCRIPTOR_MODULE, {
            writeStrongBinder(callback)
            writeInt(code)
        }) { }
    }

    /**
     * The vendor's `ModuleObject` layout is not published. Standard AIDL writes a non-zero int
     * marker before a non-null parcelable; the body is read as `(int code, int[], float[], String[])`.
     * Anything that does not fit returns null rather than throwing. The requested [code] is used for
     * the result because the embedded one is unverified.
     */
    private fun Parcel.readModuleObject(code: Int): ModuleUpdate? = try {
        if (dataAvail() < 4 || readInt() == 0) {
            null
        } else {
            readInt()
            val i = if (dataAvail() > 0) createIntArray() else null
            val f = if (dataAvail() > 0) createFloatArray() else null
            val s = if (dataAvail() > 0) createStringArray() else null
            if (i == null && f == null && s == null) null else ModuleUpdate(id, code, i, f, s)
        }
    } catch (e: Exception) {
        Log.d(TAG, "unparseable ModuleObject for get($code)", e)
        null
    }

    private fun deliver(update: ModuleUpdate) {
        lastValues[update.code] = update
        for (sub in subscribers) {
            if (update.code in sub.codes) sub.channel.trySend(update)
        }
        host.publish(update)
    }

    private inner class CallbackStub : Binder() {
        init {
            attachInterface(null, SyuBinder.DESCRIPTOR_CALLBACK)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != SyuBinder.TX_CALLBACK_UPDATE) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(SyuBinder.DESCRIPTOR_CALLBACK)
            val update = try {
                val c = data.readInt()
                ModuleUpdate(id, c, data.createIntArray(), data.createFloatArray(), data.createStringArray())
            } catch (e: RuntimeException) {
                Log.w(TAG, "malformed update on ${Modules.name(id)}", e)
                null
            }
            update?.let(::deliver)
            reply?.writeNoException()
            return true
        }
    }

    private companion object {
        const val TAG = "FytModule"
    }
}
