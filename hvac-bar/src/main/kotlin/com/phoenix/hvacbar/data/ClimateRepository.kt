package com.phoenix.hvacbar.data

import android.os.SystemClock
import android.util.Log
import com.phoenix.fytipc.ConnectionState
import com.phoenix.fytipc.FytToolkit
import com.phoenix.fytipc.climate.ClimateAction
import com.phoenix.fytipc.climate.ClimateState
import com.phoenix.fytipc.profile.VehicleProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Climate state as reported by the vehicle, and the single path for sending bar actions back.
 *
 * State comes only from vendor updates reduced through [profile]; taps never modify it locally
 * (the vendor replays the real state after each command).
 */
class ClimateRepository(
    private val toolkit: FytToolkit,
    val profile: VehicleProfile,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { SystemClock.uptimeMillis() },
    private val debouncer: TapDebouncer = TapDebouncer(),
    private val retryDelayMs: Long = 2_000L,
) {
    private val _state = MutableStateFlow(ClimateState.EMPTY)
    val state: StateFlow<ClimateState> = _state.asStateFlow()

    val connection: StateFlow<ConnectionState> get() = toolkit.connectionState

    private val _hints = MutableSharedFlow<BarHint>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val hints: SharedFlow<BarHint> = _hints.asSharedFlow()

    private var subscription: Job? = null

    /** Subscribes to the profile's climate codes. Idempotent. Failures are retried, never thrown. */
    fun start() {
        if (subscription?.isActive == true) return
        val codes = profile.climateReadCodes.toIntArray()
        subscription = scope.launch {
            flow { emitAll(toolkit.canbus.updates(*codes)) }
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException) return@retryWhen false
                    if (attempt == 0L) Log.w(TAG, "climate subscription failed; retrying", cause)
                    delay(retryDelayMs)
                    true
                }
                .collect { update -> _state.update { profile.reduce(it, update) } }
        }
    }

    /** Translates [action] through the profile and sends it. Ignored while not connected. */
    fun send(action: ClimateAction): SendResult {
        if (connection.value != ConnectionState.CONNECTED) return SendResult.IGNORED_DISCONNECTED
        if (!debouncer.accept(action, clock())) return SendResult.DEBOUNCED
        val command = profile.command(action, _state.value)
        if (command == null) {
            _hints.tryEmit(BarHint.NOT_SUPPORTED)
            return SendResult.UNSUPPORTED
        }
        scope.launch {
            try {
                toolkit.module(command.module).cmd(command)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "cmd failed: $command", t)
                _hints.tryEmit(BarHint.SEND_FAILED)
            }
        }
        return SendResult.SENT
    }

    /** Outcome of [send]; mostly useful for tests and logging. */
    enum class SendResult { SENT, DEBOUNCED, UNSUPPORTED, IGNORED_DISCONNECTED }

    private companion object {
        const val TAG = "HvacBarRepo"
    }
}
