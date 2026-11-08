package com.phoenix.launcher.vehicle

import com.phoenix.fytipc.ConnectionState
import com.phoenix.fytipc.FytModule
import com.phoenix.fytipc.FytToolkit
import com.phoenix.fytipc.ModuleUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/** Fallback used when [FytToolkit.connect] itself throws, so the launcher still runs without vehicle IPC. */
internal object UnavailableToolkit : FytToolkit {
    override val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.UNAVAILABLE)
    override val allUpdates: Flow<ModuleUpdate> = emptyFlow()

    override fun module(id: Int): FytModule = object : FytModule {
        override val id: Int = id
        override suspend fun cmd(code: Int, ints: IntArray?, floats: FloatArray?, strings: Array<String>?) {
            error("vehicle service unavailable")
        }
        override suspend fun get(code: Int, ints: IntArray?, floats: FloatArray?, strings: Array<String>?): ModuleUpdate? = null
        override fun updates(vararg codes: Int): Flow<ModuleUpdate> = emptyFlow()
    }

    override fun close() = Unit
}
