package com.phoenix.hvacbar

import com.phoenix.fytipc.ConnectionState
import com.phoenix.fytipc.FytModule
import com.phoenix.fytipc.FytToolkit
import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.ModuleUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter

/** In-memory [FytToolkit]: records every command and lets tests push updates. */
class FakeFytToolkit(initial: ConnectionState = ConnectionState.CONNECTED) : FytToolkit {

    override val connectionState = MutableStateFlow(initial)

    val commands = mutableListOf<ModuleCommand>()

    private val updates = MutableSharedFlow<ModuleUpdate>(extraBufferCapacity = 64)
    private val modules = HashMap<Int, FakeModule>()

    override fun module(id: Int): FytModule = modules.getOrPut(id) { FakeModule(id) }

    override val allUpdates: Flow<ModuleUpdate> get() = updates

    fun push(update: ModuleUpdate) {
        check(updates.tryEmit(update))
    }

    override fun close() = Unit

    private inner class FakeModule(override val id: Int) : FytModule {
        override suspend fun cmd(code: Int, ints: IntArray?, floats: FloatArray?, strings: Array<String>?) {
            commands += ModuleCommand(id, code, ints, floats, strings)
        }

        override suspend fun get(code: Int, ints: IntArray?, floats: FloatArray?, strings: Array<String>?): ModuleUpdate? = null

        override fun updates(vararg codes: Int): Flow<ModuleUpdate> {
            val wanted = codes.toSet()
            return updates.filter { it.module == id && it.code in wanted }
        }
    }
}
