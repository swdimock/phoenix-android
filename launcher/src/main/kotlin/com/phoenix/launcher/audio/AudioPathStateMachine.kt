package com.phoenix.launcher.audio

import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.profile.VehicleProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/** Phase of the boot audio race (see docs/ARCHITECTURE.md, "The boot audio race"). */
enum class AudioPathState(
    /** True when the AA receiver may play audio. */
    val audioReady: Boolean,
) {
    /** Waiting for the vehicle readiness signal (or the timeout). */
    WAITING(false),

    /** Source-select command sent; settling. */
    SELECTING(false),

    /** Readiness signal seen and source selected. */
    READY(true),

    /** Timed out without a readiness signal; source select was still sent and playback proceeds. */
    FAILED(true),
}

/** Inputs read at the start of every run so settings changes apply on the next ACC cycle. */
data class AudioPathConfig(val profile: VehicleProfile, val readyTimeoutMs: Long)

/**
 * Pure-Kotlin audio-path readiness state machine: WAITING -> SELECTING -> READY | FAILED, back to WAITING on
 * [reset]. Android wiring (receivers, broadcasts, foreground service) lives in [AudioPathService] and the
 * application class; this class only needs a scope, so it is unit-tested with virtual time.
 *
 * @param readinessUpdates subscription to the profile's [VehicleProfile.audioReadinessCodes]. Failures are
 *   treated as "no signal" and the timeout path is taken.
 * @param sendCommand sends a vendor command. Failures are logged and ignored.
 * @param onTransition invoked synchronously on every state change (including back to WAITING).
 */
class AudioPathStateMachine(
    private val scope: CoroutineScope,
    private val config: suspend () -> AudioPathConfig,
    private val readinessUpdates: (VehicleProfile) -> Flow<ModuleUpdate>,
    private val sendCommand: suspend (ModuleCommand) -> Unit,
    private val onTransition: (AudioPathState) -> Unit = {},
    private val log: (String) -> Unit = {},
    private val settleMs: Long = DEFAULT_SETTLE_MS,
) {
    private val _state = MutableStateFlow(AudioPathState.WAITING)
    val state: StateFlow<AudioPathState> = _state.asStateFlow()

    private var job: Job? = null

    val isRunning: Boolean
        @Synchronized get() = job?.isActive == true

    /** Begins a run from WAITING. No-op while running or once settled (call [reset] first). */
    @Synchronized
    fun start() {
        if (job?.isActive == true || _state.value != AudioPathState.WAITING) return
        job = scope.launch { run() }
    }

    /** Cancels any run and returns to an idle WAITING (ACC off / screen off). */
    @Synchronized
    fun reset() {
        job?.cancel()
        job = null
        moveTo(AudioPathState.WAITING)
    }

    /** [reset] then [start]; used when the vendor service rebinds. */
    @Synchronized
    fun restart() {
        reset()
        start()
    }

    private suspend fun run() {
        val cfg = config()
        log("waiting for ${cfg.profile.id} readiness (timeout ${cfg.readyTimeoutMs} ms)")
        val signalled = awaitSignal(cfg)
        log(if (signalled) "readiness signal received" else "no readiness signal within timeout")
        advance(AudioPathState.SELECTING)

        val command = cfg.profile.audioSourceSelectCommand
        if (command == null) {
            log("profile ${cfg.profile.id} has no audioSourceSelectCommand; proceeding")
        } else {
            try {
                sendCommand(command)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                log("audio source select failed: $t")
            }
        }
        delay(settleMs)
        advance(if (signalled) AudioPathState.READY else AudioPathState.FAILED)
    }

    private suspend fun awaitSignal(cfg: AudioPathConfig): Boolean = withTimeoutOrNull(cfg.readyTimeoutMs) {
        readinessUpdates(cfg.profile)
            .catch { log("readiness subscription failed: $it") }
            .firstOrNull { cfg.profile.isAudioPathReadySignal(it) }
            ?: awaitCancellation()
        true
    } ?: false

    private suspend fun advance(next: AudioPathState) {
        coroutineContext.ensureActive()
        synchronized(this) {
            coroutineContext.ensureActive()
            moveTo(next)
        }
    }

    private fun moveTo(next: AudioPathState) {
        if (_state.value == next) return
        _state.value = next
        log("audio path -> $next")
        onTransition(next)
    }

    companion object {
        const val DEFAULT_SETTLE_MS = 1_500L
    }
}
