package com.phoenix.mocksyums.server

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.Modules
import com.phoenix.fytipc.SyuBinder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * Raw-Binder `com.syu.ipc.IRemoteModule` for one module id.
 *
 * Mirrors the vendor behaviour the clients rely on: `register` replays the cached value for that
 * code immediately; [publish] caches and notifies every callback registered for the code; `get`
 * is answered (best effort: the cached value as a `(code, int[], float[], String[])` ModuleObject;
 * the real service returns nothing useful for climate codes). Dead callbacks are dropped via
 * `linkToDeath` and on `DeadObjectException`.
 *
 * A [passive] module accepts every call and never delivers anything, like a module the unit does
 * not implement. All callback transactions run on [delivery] (one thread) to keep ordering.
 */
class ModuleBinder(
    val id: Int,
    private val passive: Boolean,
    private val delivery: Executor,
    private val onCommand: (ModuleCommand) -> Unit,
    private val onClientsChanged: () -> Unit,
) : Binder() {

    private class Client(val binder: IBinder, val death: IBinder.DeathRecipient) {
        val codes: MutableSet<Int> = HashSet()
    }

    private val lock = Any()
    private val clients = HashMap<IBinder, Client>()
    private val cache = ConcurrentHashMap<Int, ModuleUpdate>()

    init {
        attachInterface(null, SyuBinder.DESCRIPTOR_MODULE)
    }

    /** Live callback binders registered on this module. */
    fun callbackBinders(): Set<IBinder> = synchronized(lock) { clients.keys.toSet() }

    fun registrationCount(): Int = synchronized(lock) { clients.values.sumOf { it.codes.size } }

    fun cached(code: Int): ModuleUpdate? = cache[code]

    /** Vendor-side value change: cache it and notify registered callbacks. */
    fun publish(update: ModuleUpdate) {
        if (passive) return
        cache[update.code] = update
        val targets = synchronized(lock) { clients.values.filter { update.code in it.codes }.map { it.binder } }
        targets.forEach { deliver(it, update) }
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            SyuBinder.TX_MODULE_CMD -> {
                data.enforceInterface(SyuBinder.DESCRIPTOR_MODULE)
                val cmd = ModuleCommand(id, data.readInt(), data.createIntArray(), data.createFloatArray(), data.createStringArray())
                reply?.writeNoException()
                if (!passive) {
                    runCatching { onCommand(cmd) }.onFailure { Log.e(TAG, "cmd handler failed for $cmd", it) }
                }
            }
            SyuBinder.TX_MODULE_GET -> {
                data.enforceInterface(SyuBinder.DESCRIPTOR_MODULE)
                val c = data.readInt()
                data.createIntArray(); data.createFloatArray(); data.createStringArray()
                reply?.writeNoException()
                val u = cache[c]
                if (u == null) {
                    reply?.writeInt(0)
                } else {
                    reply?.writeInt(1)
                    reply?.writeInt(u.code)
                    reply?.writeIntArray(u.ints)
                    reply?.writeFloatArray(u.floats)
                    reply?.writeStringArray(u.strings)
                }
            }
            SyuBinder.TX_MODULE_REGISTER -> {
                data.enforceInterface(SyuBinder.DESCRIPTOR_MODULE)
                val cb = data.readStrongBinder()
                val c = data.readInt()
                data.readInt() // flag; the vendor's meaning is unknown, com.syu.air passes 1
                reply?.writeNoException()
                if (cb != null) register(cb, c)
            }
            SyuBinder.TX_MODULE_UNREGISTER -> {
                data.enforceInterface(SyuBinder.DESCRIPTOR_MODULE)
                val cb = data.readStrongBinder()
                val c = data.readInt()
                reply?.writeNoException()
                if (cb != null) unregister(cb, c)
            }
            else -> return super.onTransact(code, data, reply, flags)
        }
        return true
    }

    private fun register(cb: IBinder, code: Int) {
        val added = synchronized(lock) {
            val client = clients[cb] ?: run {
                val death = IBinder.DeathRecipient { drop(cb) }
                try {
                    cb.linkToDeath(death, 0)
                } catch (e: RemoteException) {
                    return
                }
                Client(cb, death).also { clients[cb] = it }
            }
            client.codes.add(code)
        }
        if (added) onClientsChanged()
        if (!passive) cache[code]?.let { deliver(cb, it) }
    }

    private fun unregister(cb: IBinder, code: Int) {
        synchronized(lock) {
            val client = clients[cb] ?: return
            client.codes.remove(code)
            if (client.codes.isEmpty()) {
                clients.remove(cb)
                runCatching { cb.unlinkToDeath(client.death, 0) }
            }
        }
        onClientsChanged()
    }

    private fun drop(cb: IBinder) {
        val removed = synchronized(lock) { clients.remove(cb) }
        if (removed != null) {
            Log.i(TAG, "callback died on ${Modules.name(id)}; dropped ${removed.codes.size} registrations")
            onClientsChanged()
        }
    }

    private fun deliver(cb: IBinder, u: ModuleUpdate) {
        delivery.execute {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(SyuBinder.DESCRIPTOR_CALLBACK)
                data.writeInt(u.code)
                data.writeIntArray(u.ints)
                data.writeFloatArray(u.floats)
                data.writeStringArray(u.strings)
                cb.transact(SyuBinder.TX_CALLBACK_UPDATE, data, reply, 0)
                reply.readException()
            } catch (e: RemoteException) {
                drop(cb)
            } catch (e: RuntimeException) {
                Log.w(TAG, "callback threw for ${u.code} on ${Modules.name(id)}", e)
            } finally {
                reply.recycle()
                data.recycle()
            }
        }
    }

    private companion object {
        const val TAG = "MockModule"
    }
}
