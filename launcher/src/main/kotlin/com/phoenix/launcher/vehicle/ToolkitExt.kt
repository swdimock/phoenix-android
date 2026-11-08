package com.phoenix.launcher.vehicle

import com.phoenix.fytipc.FytToolkit
import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.ModuleUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen

/**
 * [com.phoenix.fytipc.FytModule.updates] with module lookup deferred to collection, so any toolkit failure
 * (vendor service missing, rebinding) surfaces as a flow error instead of an exception at the call site.
 */
fun FytToolkit.updatesOf(moduleId: Int, vararg codes: Int): Flow<ModuleUpdate> = flow {
    emitAll(module(moduleId).updates(*codes))
}

/** Sends [command], returning false (never throwing) if the vendor service rejects it or is missing. */
suspend fun FytToolkit.trySend(command: ModuleCommand, onError: (Throwable) -> Unit = {}): Boolean = try {
    module(command.module).cmd(command)
    true
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    onError(t)
    false
}

/** Re-subscribes forever with a fixed back-off; for long-lived diagnostics subscriptions. */
fun <T> Flow<T>.retryForever(backoffMs: Long, onError: (Throwable) -> Unit = {}): Flow<T> =
    retryWhen { cause, _ ->
        onError(cause)
        delay(backoffMs)
        true
    }
