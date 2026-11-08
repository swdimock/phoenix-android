package com.phoenix.fytipc

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * One value update from a vendor module. Mirrors `IModuleCallback.update(code, int[], float[], String[])`.
 */
data class ModuleUpdate(
    val module: Int,
    val code: Int,
    val ints: IntArray? = null,
    val floats: FloatArray? = null,
    val strings: Array<String>? = null,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    val int0: Int? get() = ints?.firstOrNull()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModuleUpdate) return false
        return module == other.module && code == other.code &&
            ints.contentEquals(other.ints) && floats.contentEquals(other.floats) &&
            strings.contentEquals(other.strings)
    }

    override fun hashCode(): Int {
        var r = module
        r = 31 * r + code
        r = 31 * r + (ints?.contentHashCode() ?: 0)
        r = 31 * r + (floats?.contentHashCode() ?: 0)
        r = 31 * r + (strings?.contentHashCode() ?: 0)
        return r
    }
}

/** A write to a vendor module. Mirrors `IRemoteModule.cmd(code, int[], float[], String[])`. */
data class ModuleCommand(
    val module: Int,
    val code: Int,
    val ints: IntArray? = null,
    val floats: FloatArray? = null,
    val strings: Array<String>? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModuleCommand) return false
        return module == other.module && code == other.code &&
            ints.contentEquals(other.ints) && floats.contentEquals(other.floats) &&
            strings.contentEquals(other.strings)
    }

    override fun hashCode(): Int {
        var r = module
        r = 31 * r + code
        r = 31 * r + (ints?.contentHashCode() ?: 0)
        r = 31 * r + (floats?.contentHashCode() ?: 0)
        r = 31 * r + (strings?.contentHashCode() ?: 0)
        return r
    }
}

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, UNAVAILABLE }

/** Handle to one `IRemoteModule`. Obtained from [FytToolkit.module]. */
interface FytModule {
    val id: Int

    /** Fire-and-forget write. Suspends only while the binder call is in flight. */
    suspend fun cmd(code: Int, ints: IntArray? = null, floats: FloatArray? = null, strings: Array<String>? = null)

    suspend fun cmd(command: ModuleCommand) {
        require(command.module == id) { "command targets module ${command.module}, this is $id" }
        cmd(command.code, command.ints, command.floats, command.strings)
    }

    /**
     * Synchronous read. On real hardware `get` is answered but returns nothing for most climate codes;
     * prefer [updates] (registration replays the cached value immediately).
     */
    suspend fun get(code: Int, ints: IntArray? = null, floats: FloatArray? = null, strings: Array<String>? = null): ModuleUpdate?

    /**
     * Cold flow. Collecting registers a callback for each code (flag [SyuBinder.REGISTER_FLAG_DEFAULT]);
     * cancelling unregisters. The vendor replays the current value on registration, so the first
     * emissions are a state snapshot.
     */
    fun updates(vararg codes: Int): Flow<ModuleUpdate>
}

/**
 * Client for the SYU toolkit service. One instance per process is enough; it rebinds automatically
 * if `com.syu.ms` restarts.
 */
interface FytToolkit : AutoCloseable {
    val connectionState: StateFlow<ConnectionState>

    /** Returns a module handle. Cheap; handles are cached. Safe to call before connected. */
    fun module(id: Int): FytModule

    val canbus: FytModule get() = module(Modules.CANBUS)
    val sound: FytModule get() = module(Modules.SOUND)
    val main: FytModule get() = module(Modules.MAIN)
    val steer: FytModule get() = module(Modules.STEER)
    val radio: FytModule get() = module(Modules.RADIO)
    val bt: FytModule get() = module(Modules.BT)

    /** Every update from every registered code, across modules. Useful for recording. */
    val allUpdates: Flow<ModuleUpdate>

    /** Unbinds. Idempotent. */
    override fun close()

    companion object {
        /**
         * Binds to `com.syu.ms` (`SyuBinder.SERVICE_ACTION`). In the emulator the `mock-syu-ms` app
         * provides the same package/action, so callers do not need to know which one they hit.
         */
        fun connect(context: Context): FytToolkit = com.phoenix.fytipc.binder.BinderFytToolkit(context.applicationContext)
    }
}
