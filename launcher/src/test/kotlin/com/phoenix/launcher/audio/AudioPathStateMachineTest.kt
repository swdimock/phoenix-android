package com.phoenix.launcher.audio

import com.google.common.truth.Truth.assertThat
import com.phoenix.fytipc.CanbusCodes
import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.Modules
import com.phoenix.fytipc.profile.VehicleProfile
import com.phoenix.fytipc.profile.Wk2ReferenceProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPathStateMachineTest {

    private val selectCommand = ModuleCommand(Modules.CANBUS, 6, intArrayOf(99, 1))

    private class TestProfile(override val audioSourceSelectCommand: ModuleCommand?) : VehicleProfile by Wk2ReferenceProfile {
        override val id = "test"
        override val audioReadinessCodes = setOf(CanbusCodes.U_EXIST_AIR)
        override fun isAudioPathReadySignal(update: ModuleUpdate) =
            update.code == CanbusCodes.U_EXIST_AIR && update.int0 == 1
    }

    private inner class Harness(
        scope: TestScope,
        profile: VehicleProfile = TestProfile(selectCommand),
        timeoutMs: Long = 15_000,
        updates: Flow<ModuleUpdate>? = null,
        failCommands: Boolean = false,
    ) {
        val signals = MutableSharedFlow<ModuleUpdate>()
        val sent = mutableListOf<ModuleCommand>()
        val transitions = mutableListOf<AudioPathState>()
        val machine = AudioPathStateMachine(
            scope = scope.backgroundScope,
            config = { AudioPathConfig(profile, timeoutMs) },
            readinessUpdates = { updates ?: signals },
            sendCommand = {
                if (failCommands) error("binder died")
                sent += it
            },
            onTransition = { transitions += it },
        )
    }

    private fun ready() = ModuleUpdate(Modules.CANBUS, CanbusCodes.U_EXIST_AIR, intArrayOf(1))

    @Test
    fun signalBeforeTimeout_selectsThenReady() = runTest {
        val h = Harness(this)
        h.machine.start()
        runCurrent()
        assertThat(h.machine.state.value).isEqualTo(AudioPathState.WAITING)

        advanceTimeBy(3_000)
        h.signals.emit(ModuleUpdate(Modules.CANBUS, CanbusCodes.U_EXIST_AIR, intArrayOf(0)))
        runCurrent()
        assertThat(h.machine.state.value).isEqualTo(AudioPathState.WAITING)

        h.signals.emit(ready())
        runCurrent()
        assertThat(h.machine.state.value).isEqualTo(AudioPathState.SELECTING)
        assertThat(h.sent).containsExactly(selectCommand)

        advanceTimeBy(AudioPathStateMachine.DEFAULT_SETTLE_MS - 1)
        runCurrent()
        assertThat(h.machine.state.value).isEqualTo(AudioPathState.SELECTING)
        advanceTimeBy(2)
        runCurrent()
        assertThat(h.machine.state.value).isEqualTo(AudioPathState.READY)
        assertThat(h.transitions).containsExactly(AudioPathState.SELECTING, AudioPathState.READY).inOrder()
        assertThat(h.machine.isRunning).isFalse()
    }

    @Test
    fun timeout_stillSelectsThenFailed() = runTest {
        val h = Harness(this, timeoutMs = 15_000)
        h.machine.start()
        advanceTimeBy(14_999)
        runCurrent()
        assertThat(h.machine.state.value).isEqualTo(AudioPathState.WAITING)

        advanceTimeBy(2)
        runCurrent()
        assertThat(h.machine.state.value).isEqualTo(AudioPathState.SELECTING)
        assertThat(h.sent).containsExactly(selectCommand)

        advanceTimeBy(AudioPathStateMachine.DEFAULT_SETTLE_MS + 1)
        runCurrent()
        assertThat(h.machine.state.value).isEqualTo(AudioPathState.FAILED)
        assertThat(h.machine.state.value.audioReady).isTrue()
    }

    @Test
    fun subscriptionFailure_fallsBackToTimeout() = runTest {
        val h = Harness(this, timeoutMs = 5_000, updates = flow { error("vendor service missing") })
        h.machine.start()
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(h.machine.state.value).isEqualTo(AudioPathState.WAITING)
        advanceTimeBy(4_001 + AudioPathStateMachine.DEFAULT_SETTLE_MS)
        runCurrent()
        assertThat(h.machine.state.value).isEqualTo(AudioPathState.FAILED)
    }

    @Test
    fun nullSelectCommandOrSendFailure_stillProceeds() = runTest {
        val noCommand = Harness(this, profile = TestProfile(null))
        val failing = Harness(this, failCommands = true)
        noCommand.machine.start()
        failing.machine.start()
        runCurrent()
        noCommand.signals.emit(ready())
        failing.signals.emit(ready())
        advanceTimeBy(AudioPathStateMachine.DEFAULT_SETTLE_MS + 1)
        runCurrent()
        assertThat(noCommand.sent).isEmpty()
        assertThat(noCommand.machine.state.value).isEqualTo(AudioPathState.READY)
        assertThat(failing.machine.state.value).isEqualTo(AudioPathState.READY)
    }

    @Test
    fun accOff_resetsToWaitingAndCancelsRun() = runTest {
        val h = Harness(this)
        h.machine.start()
        runCurrent()
        h.signals.emit(ready())
        advanceTimeBy(AudioPathStateMachine.DEFAULT_SETTLE_MS + 1)
        runCurrent()
        assertThat(h.machine.state.value).isEqualTo(AudioPathState.READY)

        h.machine.reset()
        assertThat(h.machine.state.value).isEqualTo(AudioPathState.WAITING)
        assertThat(h.transitions.last()).isEqualTo(AudioPathState.WAITING)

        // Idle after reset: neither the timeout nor a signal advances it until the next ACC on.
        advanceTimeBy(60_000)
        runCurrent()
        assertThat(h.machine.state.value).isEqualTo(AudioPathState.WAITING)

        h.machine.start()
        runCurrent()
        h.signals.emit(ready())
        runCurrent()
        assertThat(h.machine.state.value).isEqualTo(AudioPathState.SELECTING)
    }

    @Test
    fun accOffDuringSelecting_doesNotReachReady() = runTest {
        val h = Harness(this)
        h.machine.start()
        runCurrent()
        h.signals.emit(ready())
        runCurrent()
        assertThat(h.machine.state.value).isEqualTo(AudioPathState.SELECTING)

        h.machine.reset()
        advanceTimeBy(AudioPathStateMachine.DEFAULT_SETTLE_MS * 2)
        runCurrent()
        assertThat(h.machine.state.value).isEqualTo(AudioPathState.WAITING)
        assertThat(h.transitions).containsExactly(AudioPathState.SELECTING, AudioPathState.WAITING).inOrder()
    }

    @Test
    fun startIsIdempotent() = runTest {
        val h = Harness(this)
        h.machine.start()
        h.machine.start()
        runCurrent()
        h.signals.emit(ready())
        advanceTimeBy(AudioPathStateMachine.DEFAULT_SETTLE_MS + 1)
        runCurrent()
        h.machine.start()
        runCurrent()
        assertThat(h.sent).hasSize(1)
        assertThat(h.machine.state.value).isEqualTo(AudioPathState.READY)
    }
}
