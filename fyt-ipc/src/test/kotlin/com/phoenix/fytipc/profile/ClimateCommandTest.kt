package com.phoenix.fytipc.profile

import com.google.common.truth.Truth.assertThat
import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.Modules
import com.phoenix.fytipc.climate.AirMode
import com.phoenix.fytipc.climate.ClimateAction
import com.phoenix.fytipc.climate.ClimateActions
import com.phoenix.fytipc.climate.ClimateState
import com.phoenix.fytipc.climate.Zone
import org.junit.Test

class ClimateCommandTest {

    private fun cmd(index: Int) = ModuleCommand(Modules.CANBUS, 6, intArrayOf(1, index))

    private val expected: Map<ClimateAction, Int?> = mapOf(
        ClimateAction.ToggleAc to 1,
        ClimateAction.ToggleAuto to 2,
        ClimateAction.ToggleRecirculate to 3,
        ClimateAction.TempUp(Zone.DRIVER) to 4,
        ClimateAction.TempDown(Zone.DRIVER) to 5,
        ClimateAction.FanUp to 6,
        ClimateAction.FanDown to 7,
        ClimateAction.ToggleFrontDefrost to 12,
        ClimateAction.ToggleDual to 13,
        ClimateAction.ToggleRearDefrost to 14,
        ClimateAction.ToggleMaxAc to 15,
        ClimateAction.Power to 16,
        ClimateAction.CycleSeatHeat(Zone.DRIVER) to 17,
        ClimateAction.CycleSeatHeat(Zone.PASSENGER) to 18,
        ClimateAction.TempUp(Zone.PASSENGER) to 20,
        ClimateAction.TempDown(Zone.PASSENGER) to 21,
        ClimateAction.CycleSeatCool(Zone.DRIVER) to 22,
        ClimateAction.CycleSeatCool(Zone.PASSENGER) to 23,
        ClimateAction.SetMode(AirMode.FACE) to 8,
        ClimateAction.SetMode(AirMode.FACE_FEET) to 9,
        ClimateAction.SetMode(AirMode.FEET) to 10,
        ClimateAction.SetMode(AirMode.FEET_DEFROST) to 11,
        // No direct setter: windshield-only is the front-defrost macro, AUTO is the AUTO toggle.
        ClimateAction.SetMode(AirMode.DEFROST) to null,
        ClimateAction.SetMode(AirMode.AUTO) to null,
        ClimateAction.SetMode(AirMode.UNKNOWN) to null,
    )

    @Test
    fun `every action maps to its WK2 index`() {
        for (profile in VehicleProfiles.all) {
            for (action in ClimateActions.all) {
                if (action == ClimateAction.CycleMode) continue
                check(action in expected) { "no expectation for $action" }
                assertThat(profile.command(action, ClimateState.EMPTY)).isEqualTo(expected[action]?.let(::cmd))
            }
        }
        assertThat(ClimateActions.all.toSet()).isEqualTo(expected.keys + ClimateAction.CycleMode)
    }

    @Test
    fun `cycle mode sends the setter of the next settable mode`() {
        for (p in VehicleProfiles.all) {
            fun next(m: AirMode) = p.command(ClimateAction.CycleMode, ClimateState().withMode(m))
            assertThat(next(AirMode.FACE)).isEqualTo(cmd(9))
            assertThat(next(AirMode.FACE_FEET)).isEqualTo(cmd(10))
            assertThat(next(AirMode.FEET)).isEqualTo(cmd(11))
            assertThat(next(AirMode.FEET_DEFROST)).isEqualTo(cmd(8))
            assertThat(next(AirMode.UNKNOWN)).isEqualTo(cmd(8))
            assertThat(next(AirMode.DEFROST)).isEqualTo(cmd(8))
            assertThat(next(AirMode.AUTO)).isEqualTo(cmd(8))
        }
    }

    @Test
    fun `cycle mode and set mode share one table`() {
        val codec = FordF150Profile as ClimateCodec
        assertThat(codec.settableModes)
            .containsExactly(AirMode.FACE, AirMode.FACE_FEET, AirMode.FEET, AirMode.FEET_DEFROST).inOrder()
        for (m in AirMode.entries) {
            val target = FytAirflow.next(m, codec.settableModes)
            val state = ClimateState().withMode(m)
            assertThat(FordF150Profile.command(ClimateAction.CycleMode, state))
                .isEqualTo(FordF150Profile.command(ClimateAction.SetMode(target), state))
        }
    }

    @Test
    fun `mode is derived from the three airflow flags`() {
        fun mode(w: Boolean?, f: Boolean?, ft: Boolean?) =
            ClimateState(airWindshield = w, airFace = f, airFeet = ft).mode
        assertThat(mode(null, null, null)).isEqualTo(AirMode.UNKNOWN)
        assertThat(mode(true, false, false)).isEqualTo(AirMode.DEFROST)
        assertThat(mode(false, true, false)).isEqualTo(AirMode.FACE)
        assertThat(mode(false, true, true)).isEqualTo(AirMode.FACE_FEET)
        assertThat(mode(false, false, true)).isEqualTo(AirMode.FEET)
        assertThat(mode(true, false, true)).isEqualTo(AirMode.FEET_DEFROST)
        assertThat(mode(false, false, false)).isEqualTo(AirMode.AUTO)
        assertThat(mode(true, true, false)).isEqualTo(AirMode.UNKNOWN)
        assertThat(mode(true, true, true)).isEqualTo(AirMode.UNKNOWN)
        // Partially reported: unreported flags count as clear.
        assertThat(mode(null, true, null)).isEqualTo(AirMode.FACE)
        for (m in AirMode.entries) assertThat(ClimateState().withMode(m).mode).isEqualTo(m)
        assertThat(ClimateState().withMode(AirMode.UNKNOWN)).isEqualTo(ClimateState())
    }

    @Test
    fun `profiles registry`() {
        assertThat(VehicleProfiles.default).isSameInstanceAs(FordF150Profile)
        assertThat(VehicleProfiles.byId("wk2-reference")).isSameInstanceAs(Wk2ReferenceProfile)
        assertThat(VehicleProfiles.byId("nope")).isSameInstanceAs(FordF150Profile)
        assertThat((FordF150Profile as ClimateCodec).tempRangeF).isEqualTo(60..85)
        assertThat(Wk2ReferenceProfile.seatLevelCycle).containsExactly(0, 3, 1).inOrder()
    }
}
