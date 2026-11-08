package com.phoenix.fytipc.binder

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import com.phoenix.fytipc.SyuBinder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-process fake of `com.syu.ms`, written against the raw parcel layout independently of the
 * production client so that layout mistakes on either side show up.
 */
class FakeSyuToolkit : Binder() {
    val modules = ConcurrentHashMap<Int, FakeSyuModule>()
    fun module(id: Int): FakeSyuModule = modules.getOrPut(id) { FakeSyuModule(id) }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code != SyuBinder.TX_TOOLKIT_GET_REMOTE_MODULE) return super.onTransact(code, data, reply, flags)
        data.enforceInterface(SyuBinder.DESCRIPTOR_TOOLKIT)
        val id = data.readInt()
        reply!!.writeNoException()
        reply.writeStrongBinder(module(id))
        return true
    }
}

class FakeSyuModule(val id: Int) : Binder() {
    data class RawCall(val code: Int, val ints: List<Int>?, val floats: List<Float>?, val strings: List<String?>?)

    val commands = CopyOnWriteArrayList<RawCall>()
    val events = CopyOnWriteArrayList<String>()
    val cache = ConcurrentHashMap<Int, IntArray>()
    private val callbacks = ConcurrentHashMap<Int, CopyOnWriteArrayList<IBinder>>()

    fun callbackCount(code: Int) = callbacks[code]?.size ?: 0

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            SyuBinder.TX_MODULE_CMD -> {
                data.enforceInterface(SyuBinder.DESCRIPTOR_MODULE)
                commands += readRawArgs(data)
                reply!!.writeNoException()
            }
            SyuBinder.TX_MODULE_GET -> {
                data.enforceInterface(SyuBinder.DESCRIPTOR_MODULE)
                val args = readRawArgs(data)
                reply!!.writeNoException()
                val v = cache[args.code]
                if (v == null) {
                    reply.writeInt(0)
                } else {
                    reply.writeInt(1)
                    reply.writeInt(args.code)
                    reply.writeIntArray(v)
                    reply.writeFloatArray(null)
                    reply.writeStringArray(null)
                }
            }
            SyuBinder.TX_MODULE_REGISTER -> {
                data.enforceInterface(SyuBinder.DESCRIPTOR_MODULE)
                val cb = data.readStrongBinder()
                val c = data.readInt()
                val flag = data.readInt()
                events += "register $c flag=$flag"
                val list = callbacks.getOrPut(c) { CopyOnWriteArrayList() }
                if (list.none { it == cb }) list += cb
                reply!!.writeNoException()
                cache[c]?.let { send(cb, c, it) }
            }
            SyuBinder.TX_MODULE_UNREGISTER -> {
                data.enforceInterface(SyuBinder.DESCRIPTOR_MODULE)
                val cb = data.readStrongBinder()
                val c = data.readInt()
                events += "unregister $c"
                callbacks[c]?.remove(cb)
                reply!!.writeNoException()
            }
            else -> return super.onTransact(code, data, reply, flags)
        }
        return true
    }

    /** Vehicle-side change: caches and notifies like the vendor. */
    fun push(code: Int, vararg ints: Int) {
        cache[code] = ints
        callbacks[code]?.forEach { send(it, code, ints) }
    }

    private fun send(cb: IBinder, code: Int, ints: IntArray) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SyuBinder.DESCRIPTOR_CALLBACK)
            data.writeInt(code)
            data.writeIntArray(ints)
            data.writeFloatArray(null)
            data.writeStringArray(null)
            cb.transact(SyuBinder.TX_CALLBACK_UPDATE, data, reply, 0)
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /** Reads `(int, int[], float[], String[])` element by element: length -1 means null. */
    private fun readRawArgs(p: Parcel): RawCall {
        val code = p.readInt()
        val ni = p.readInt()
        val ints = if (ni < 0) null else List(ni) { p.readInt() }
        val nf = p.readInt()
        val floats = if (nf < 0) null else List(nf) { p.readFloat() }
        val ns = p.readInt()
        val strings = if (ns < 0) null else List(ns) { p.readString() }
        return RawCall(code, ints, floats, strings)
    }
}
