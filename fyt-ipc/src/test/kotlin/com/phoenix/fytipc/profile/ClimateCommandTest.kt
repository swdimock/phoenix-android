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

    private val expected: Map<ClimateAction, Int> = mapOf(
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
    )

    @Test
    fun `every action maps to its WK2 index`() {
        for (profile in VehicleProfiles.all) {
            for (action in ClimateActions.all) {
                if (action == ClimateAction.CycleMode) continue
                val index = expected[action] ?: error("no expectation for $action")
                assertThat(profile.command(action, ClimateState.EMPTY)).isEqualTo(cmd(index))
            }
        }
        assertThat(ClimateActions.all.toSet()).isEqualTo(expected.keys + ClimateAction.CycleMode)
    }

    @Test
    fun `cycle mode picks the next idempotent airflow setter`() {
        val p = VehicleProfiles.default
        fun next(m: AirMode) = p.command(ClimateAction.CycleMode, ClimateState(mode = m))
        assertThat(next(AirMode.FACE)).isEqualTo(cmd(9))
        assertThat(next(AirMode.FACE_FEET)).isEqualTo(cmd(10))
        assertThat(next(AirMode.FEET)).isEqualTo(cmd(11))
        assertThat(next(AirMode.FEET_DEFROST)).isEqualTo(cmd(8))
        assertThat(next(AirMode.UNKNOWN)).isEqualTo(cmd(8))
        assertThat(next(AirMode.DEFROST)).isEqualTo(cmd(8))
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
