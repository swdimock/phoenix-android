package com.phoenix.hvacbar.data

import com.google.common.truth.Truth.assertThat
import com.phoenix.fytipc.ConnectionState
import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.Modules
import com.phoenix.fytipc.climate.ClimateAction
import com.phoenix.fytipc.climate.ClimateState
import com.phoenix.fytipc.profile.FordF150Profile
import com.phoenix.fytipc.profile.VehicleProfile
import com.phoenix.fytipc.profile.Wk2ReferenceProfile
import com.phoenix.hvacbar.FakeFytToolkit
import com.phoenix.hvacbar.data.ClimateRepository.SendResult
import com.phoenix.hvacbar.ui.BarControl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClimateRepositoryTest {

    private val toolkit = FakeFytToolkit()
    private var now = 1_000L

    private fun TestScope.repository(profile: VehicleProfile = FordF150Profile) = ClimateRepository(
        toolkit = toolkit,
        profile = profile,
        scope = backgroundScope,
        clock = { now },
    )

    @Test
    fun everySupportedControlSendsTheProfileCommand() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository()
        val expected = mutableListOf<ModuleCommand>()

        BarControl.entries.forEach { control ->
            now += 1_000
            val command = FordF150Profile.command(control.action, ClimateState.EMPTY)
            val result = repo.send(control.action)
            if (command == null) {
                assertThat(result).isEqualTo(SendResult.UNSUPPORTED)
            } else {
                assertThat(result).isEqualTo(SendResult.SENT)
                expected += command
            }
        }
        advanceUntilIdle()

        assertThat(expected).isNotEmpty()
        assertThat(toolkit.commands).containsExactlyElementsIn(expected).inOrder()
        assertThat(toolkit.commands.map { it.module }.toSet()).containsExactly(Modules.CANBUS)
    }

    @Test
    fun powerTapWritesTheFixedCommandCodeWithItsIndex() = runTest(UnconfinedTestDispatcher()) {
        repository().send(BarControl.POWER.action)
        advanceUntilIdle()

        val sent = toolkit.commands.single()
        assertThat(sent.module).isEqualTo(Modules.CANBUS)
        assertThat(sent.code).isEqualTo(Wk2ReferenceProfile.CMD_CODE)
        assertThat(sent.ints!!.toList()).contains(Wk2ReferenceProfile.CmdIndex.POWER)
    }

    @Test
    fun repeatedTapsWithin120msAreDropped() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository()
        val fanUp = BarControl.FAN_UP.action

        assertThat(repo.send(fanUp)).isEqualTo(SendResult.SENT)
        now += 60
        assertThat(repo.send(fanUp)).isEqualTo(SendResult.DEBOUNCED)
        assertThat(repo.send(BarControl.FAN_DOWN.action)).isEqualTo(SendResult.SENT)
        now += 60
        assertThat(repo.send(fanUp)).isEqualTo(SendResult.SENT)
        advanceUntilIdle()

        assertThat(toolkit.commands).hasSize(3)
    }

    @Test
    fun unsupportedActionEmitsHintAndSendsNothing() = runTest(UnconfinedTestDispatcher()) {
        val noSeatCool = object : VehicleProfile by FordF150Profile {
            override fun command(action: ClimateAction, current: ClimateState): ModuleCommand? =
                if (action is ClimateAction.CycleSeatCool) null else FordF150Profile.command(action, current)
        }
        val repo = repository(noSeatCool)
        val hints = mutableListOf<BarHint>()
        backgroundScope.launch { repo.hints.collect { hints += it } }

        assertThat(repo.send(BarControl.DRIVER_SEAT_COOL.action)).isEqualTo(SendResult.UNSUPPORTED)
        advanceUntilIdle()

        assertThat(hints).containsExactly(BarHint.NOT_SUPPORTED)
        assertThat(toolkit.commands).isEmpty()
    }

    @Test
    fun tapsAreIgnoredWhileDisconnected() = runTest(UnconfinedTestDispatcher()) {
        toolkit.connectionState.value = ConnectionState.CONNECTING
        val repo = repository()

        assertThat(repo.send(BarControl.AC.action)).isEqualTo(SendResult.IGNORED_DISCONNECTED)
        advanceUntilIdle()

        assertThat(toolkit.commands).isEmpty()
    }

    @Test
    fun updatesAreReducedThroughTheProfile() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository()
        repo.start()
        val codes = Wk2ReferenceProfile.Codes

        val ac = ModuleUpdate(Modules.CANBUS, codes.U_AIR_AC, intArrayOf(1))
        val fan = ModuleUpdate(Modules.CANBUS, codes.U_AIR_WIND_LEVEL, intArrayOf(4))
        val ignored = ModuleUpdate(Modules.CANBUS, 9_999, intArrayOf(1))
        listOf(ac, fan, ignored).forEach(toolkit::push)

        val expected = listOf(ac, fan).fold(ClimateState.EMPTY, FordF150Profile::reduce)
        assertThat(repo.state.first { it == expected }).isEqualTo(expected)
        assertThat(repo.state.value.ac).isTrue()
    }

    @Test
    fun tapsDoNotChangeStateOptimistically() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository()
        repo.start()

        repo.send(BarControl.AUTO.action)
        advanceUntilIdle()

        assertThat(repo.state.value).isEqualTo(ClimateState.EMPTY)
    }
}
