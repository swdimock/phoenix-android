package com.phoenix.mocksyums.server

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import com.phoenix.fytipc.SyuBinder

/** Raw-Binder `com.syu.ipc.IRemoteToolkit`: `getRemoteModule(int)` is the only transaction. */
class ToolkitBinder(private val moduleFor: (Int) -> IBinder) : Binder() {

    init {
        attachInterface(null, SyuBinder.DESCRIPTOR_TOOLKIT)
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code != SyuBinder.TX_TOOLKIT_GET_REMOTE_MODULE) return super.onTransact(code, data, reply, flags)
        data.enforceInterface(SyuBinder.DESCRIPTOR_TOOLKIT)
        val id = data.readInt()
        reply?.writeNoException()
        reply?.writeStrongBinder(moduleFor(id))
        return true
    }
}
