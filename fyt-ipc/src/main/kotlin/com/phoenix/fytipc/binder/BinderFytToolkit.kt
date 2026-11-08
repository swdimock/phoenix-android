package com.phoenix.fytipc.binder

import android.content.Context
import com.phoenix.fytipc.ConnectionState
import com.phoenix.fytipc.FytModule
import com.phoenix.fytipc.FytToolkit
import com.phoenix.fytipc.ModuleUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Raw-Binder implementation of [FytToolkit] against the SYU AIDL surface.
 *
 * IMPLEMENTATION NOTES (for the worker filling this in):
 * - Do NOT generate AIDL via Gradle; hand-roll `Binder`/`IBinder` proxies using the descriptors and
 *   transaction ids in [com.phoenix.fytipc.SyuBinder]. Transaction ids must match exactly.
 * - Parcel layout for `cmd/get/register/unregister` follows standard AIDL: `writeInterfaceToken`,
 *   then `int code`, then nullable `int[]`, `float[]`, `String[]` (AIDL `in` arrays: -1 length for null).
 *   `get` returns a `ModuleObject` parcelable (the vendor's) -- treat it as nullable and best-effort.
 * - `register(IModuleCallback cb, int code, int flag)` -> `writeStrongBinder(cb)`, `writeInt(code)`, `writeInt(flag)`.
 * - The callback stub must answer `TX_CALLBACK_UPDATE` with `(int code, int[] i, float[] f, String[] s)`
 *   and reply with `writeNoException()`.
 * - Bind with `Intent(SERVICE_ACTION).setPackage(SERVICE_PACKAGE)` and `BIND_AUTO_CREATE`; re-bind on
 *   `onServiceDisconnected`/`onBindingDied` with backoff. Re-register all live subscriptions on reconnect.
 * - `updates()` is a `callbackFlow`; register on collect, unregister on cancel; share one callback stub
 *   per module and fan out by code.
 */
internal class BinderFytToolkit(private val context: Context) : FytToolkit {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    override fun module(id: Int): FytModule {
        TODO("fyt-ipc worker: return cached BinderFytModule(id) bound to this toolkit")
    }

    override val allUpdates: Flow<ModuleUpdate>
        get() = emptyFlow() // TODO("fyt-ipc worker: merge of all live module callbacks")

    override fun close() {
        // TODO("fyt-ipc worker: unbind, cancel scopes, idempotent")
    }
}
