package com.phoenix.mocksyums.sim

import com.google.common.truth.Truth.assertThat
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.climate.AirMode
import com.phoenix.fytipc.climate.ClimateAction
import com.phoenix.fytipc.climate.ClimateActions
import com.phoenix.fytipc.climate.ClimateState
import com.phoenix.fytipc.climate.Setpoint
import com.phoenix.fytipc.climate.Zone
import com.phoenix.fytipc.profile.ClimateCodec
import com.phoenix.fytipc.profile.FordF150Profile
import com.phoenix.fytipc.profile.FytTemperature
import com.phoenix.fytipc.profile.Wk2ReferenceProfile.Codes
import org.junit.Test

class VehicleSimulatorTest {

    private val profile = FordF150Profile
    private val codec: ClimateCodec = FordF150Profile
    private val published = mutableListOf<ModuleUpdate>()
    private val sim = VehicleSimulator(profile, codec, { published += it })

    private fun send(action: ClimateAction, state: ClimateState = sim.state.value) =
        sim.onCommand(profile.command(action, state)!!)

    @Test
    fun `every profile command inverts to its action`() {
        for (action in ClimateActions.all) {
            val effect = CommandInverter(profile).invert(profile.command(action, ClimateState(mode = AirMode.FACE))!!)
            if (action == ClimateAction.CycleMode) {
                assertThat(effect).isEqualTo(ClimateEffect.SetMode(AirMode.FACE_FEET))
            } else {
                assertThat(effect).isEqualTo(ClimateEffect.Action(action))
            }
        }
    }

    @Test
    fun `auto toggles and fan up exits auto onto fan 3 face`() {
        assertThat(sim.state.value.auto).isTrue()
        send(ClimateAction.FanUp)
        assertThat(sim.state.value.auto).isFalse()
        assertThat(sim.state.value.fanSpeed).isEqualTo(3)
        assertThat(sim.state.value.mode).isEqualTo(AirMode.FACE)
        repeat(10) { send(ClimateAction.FanUp) }
        assertThat(sim.state.value.fanSpeed).isEqualTo(7)
        send(ClimateAction.ToggleAuto)
        assertThat(sim.state.value.auto).isTrue()
        assertThat(published.last { it.code == Codes.U_AIR_WIND_LEVEL }.int0).isEqualTo(15)
    }

    @Test
    fun `temperature steps, sync polarity and sentinels`() {
        // dual = true at baseline: driver change does not move the passenger.
        send(ClimateAction.TempUp(Zone.DRIVER))
        assertThat(sim.state.value.driverTemp.displayF()).isEqualTo("69")
        assertThat(sim.state.value.passengerTemp.displayF()).isEqualTo("68")
        send(ClimateAction.ToggleDual)
        send(ClimateAction.TempDown(Zone.DRIVER))
        assertThat(sim.state.value.passengerTemp.displayF()).isEqualTo("68")
        assertThat(sim.state.value.driverTemp.displayF()).isEqualTo("68")
        sim.mutate { it.copy(driverTemp = FytTemperature.ofF(85)) }
        send(ClimateAction.TempUp(Zone.DRIVER))
        assertThat(sim.state.value.driverTemp.sentinel).isEqualTo(Setpoint.Sentinel.HI)
        assertThat(published.last { it.code == Codes.U_AIR_TEMP_LEFT }.int0).isEqualTo(-3)
    }

    @Test
    fun `max ac macro and seat cycle`() {
        send(ClimateAction.ToggleMaxAc)
        val s = sim.state.value
        assertThat(s.maxAc).isTrue()
        assertThat(s.recirculate).isTrue()
        assertThat(s.auto).isFalse()
        assertThat(s.fanSpeed).isEqualTo(7)
        assertThat(s.driverTemp.sentinel).isEqualTo(Setpoint.Sentinel.LO)

        val levels = (1..4).map { send(ClimateAction.CycleSeatHeat(Zone.DRIVER)); sim.state.value.driverSeatHeat }
        assertThat(levels).containsExactly(3, 1, 0, 3).inOrder()
        send(ClimateAction.CycleSeatCool(Zone.DRIVER))
        assertThat(sim.state.value.driverSeatHeat).isEqualTo(0)
        assertThat(sim.state.value.driverSeatCool).isEqualTo(3)
    }

    @Test
    fun `cycle mode walks the oem order and publishes the three flags`() {
        val modes = (1..5).map { send(ClimateAction.CycleMode); sim.state.value.mode }
        assertThat(modes).containsExactly(
            AirMode.FACE, AirMode.FACE_FEET, AirMode.FEET, AirMode.FEET_DEFROST, AirMode.FACE,
        ).inOrder()
        assertThat(published.map { it.code }).containsAtLeast(Codes.U_AIR_BLOW_UP_LEFT, Codes.U_AIR_BLOW_BODY_LEFT, Codes.U_AIR_BLOW_FOOT_LEFT)
    }

    @Test
    fun `only changed codes are published`() {
        sim.publishAll()
        val before = published.size
        sim.mutate { it }
        assertThat(published.size).isEqualTo(before)
        send(ClimateAction.ToggleAc)
        assertThat(published.drop(before).map { it.code }).containsExactly(Codes.U_AIR_AC)
    }
}
