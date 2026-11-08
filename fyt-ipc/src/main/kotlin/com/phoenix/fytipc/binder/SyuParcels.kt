package com.phoenix.fytipc.binder

import android.os.IBinder
import android.os.Parcel

/**
 * Runs one synchronous AIDL-style transaction: interface token, caller-written arguments,
 * `readException()` on the reply, then caller-read result. Parcels are always recycled.
 */
internal inline fun <T> IBinder.call(
    tx: Int,
    descriptor: String,
    write: Parcel.() -> Unit,
    read: Parcel.() -> T,
): T {
    val data = Parcel.obtain()
    val reply = Parcel.obtain()
    try {
        data.writeInterfaceToken(descriptor)
        data.write()
        transact(tx, data, reply, 0)
        reply.readException()
        return reply.read()
    } finally {
        reply.recycle()
        data.recycle()
    }
}

/** Writes the `(int code, int[] i, float[] f, String[] s)` argument list shared by cmd/get/update. */
internal fun Parcel.writeModuleArgs(code: Int, ints: IntArray?, floats: FloatArray?, strings: Array<String>?) {
    writeInt(code)
    writeIntArray(ints)
    writeFloatArray(floats)
    writeStringArray(strings)
}
