package com.phoenix.hvacbar.ui

import com.google.common.truth.Truth.assertThat
import com.phoenix.fytipc.ConnectionState
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.Modules
import com.phoenix.fytipc.climate.ClimateState
import com.phoenix.fytipc.climate.Setpoint
import com.phoenix.fytipc.profile.FordF150Profile
import com.phoenix.fytipc.profile.Wk2ReferenceProfile
import org.junit.Test

class BarUiStateTest {

    private val codes = Wk2ReferenceProfile.Codes

    private fun reduced(vararg updates: Pair<Int, Int>): ClimateState =
        updates.fold(ClimateState.EMPTY) { state, (code, value) ->
            FordF150Profile.reduce(state, ModuleUpdate(Modules.CANBUS, code, intArrayOf(value)))
        }

    @Test
    fun emptyStateRendersUnknownEverywhere() {
        val ui = barUiState(ClimateState.EMPTY, ConnectionState.CONNECTED, useCelsius = false)

        assertThat(ui.interactive).isTrue()
        assertThat(ui.driverTemp).isEqualTo(TempText("--", null))
        assertThat(ui.passengerTemp).isEqualTo(TempText("--", null))
        assertThat(ui.mode).isEqualTo("--")
        assertThat(ui.fanSegments).isNull()
        assertThat(listOf(ui.power, ui.auto, ui.ac, ui.dual, ui.recirculate, ui.rearDefrost, ui.frontDefrost))
            .containsExactly(Lamp.UNKNOWN, Lamp.UNKNOWN, Lamp.UNKNOWN, Lamp.UNKNOWN, Lamp.UNKNOWN, Lamp.UNKNOWN, Lamp.UNKNOWN)
        assertThat(ui.driverSeatHeat.lamp).isEqualTo(Lamp.UNKNOWN)
    }

    @Test
    fun reducedUpdatesLightTheMatchingControls() {
        val climate = reduced(
            codes.U_AIR_POWER to 1,
            codes.U_AIR_AUTO to 1,
            codes.U_AIR_AC to 0,
            codes.U_AIR_DUAL_SYNC to 1,
            codes.U_AIR_WIND_LEVEL to 7,
            codes.U_AIR_SEAT_HEAT_LEFT to 2,
        )
        val ui = barUiState(climate, ConnectionState.CONNECTED, useCelsius = false)

        assertThat(ui.power).isEqualTo(Lamp.of(climate.power))
        assertThat(ui.auto).isEqualTo(Lamp.of(climate.auto))
        assertThat(ui.ac).isEqualTo(Lamp.of(climate.ac))
        assertThat(ui.dual).isEqualTo(Lamp.of(climate.dual))
        assertThat(ui.power).isEqualTo(Lamp.ON)
        assertThat(ui.ac).isEqualTo(Lamp.OFF)
        assertThat(ui.recirculate).isEqualTo(Lamp.UNKNOWN)
        assertThat(ui.fanSegments).isEqualTo(fanSegments(climate.fanSpeed, climate.fanMax))
        assertThat(ui.driverSeatHeat).isEqualTo(SeatUi(climate.driverSeatHeat))
    }

    @Test
    fun reducedTemperatureFollowsTheUnitPreference() {
        val climate = reduced(codes.U_AIR_TEMP_LEFT to 222)

        val f = barUiState(climate, ConnectionState.CONNECTED, useCelsius = false)
        val c = barUiState(climate, ConnectionState.CONNECTED, useCelsius = true)

        assertThat(f.driverTemp.value).isEqualTo(climate.driverTemp.displayF())
        assertThat(c.driverTemp.value).isEqualTo(climate.driverTemp.displayC())
        assertThat(f.passengerTemp.value).isEqualTo("--")
    }

    @Test
    fun temperatureFormatting() {
        assertThat(tempText(Setpoint(tenthsC = 222), useCelsius = false)).isEqualTo(TempText("72", "\u00B0F"))
        assertThat(tempText(Setpoint(tenthsC = 222), useCelsius = true).unit).isEqualTo("\u00B0C")
        assertThat(tempText(Setpoint(sentinel = Setpoint.Sentinel.HI), useCelsius = false)).isEqualTo(TempText("HI", null))
        assertThat(tempText(Setpoint(sentinel = Setpoint.Sentinel.LO), useCelsius = true)).isEqualTo(TempText("LO", null))
    }

    @Test
    fun fanScalesToSevenSegments() {
        assertThat(fanSegments(null, 7)).isNull()
        assertThat(fanSegments(0, 7)).isEqualTo(0)
        assertThat(fanSegments(3, 7)).isEqualTo(3)
        assertThat(fanSegments(9, 7)).isEqualTo(7)
        assertThat(fanSegments(4, 8)).isEqualTo(4)
        assertThat(fanSegments(2, 0)).isEqualTo(0)
    }

    @Test
    fun anythingButConnectedIsNotInteractive() {
        ConnectionState.entries.forEach { connection ->
            val ui = barUiState(BarSamples.typicalDriving, connection, useCelsius = false)
            assertThat(ui.interactive).isEqualTo(connection == ConnectionState.CONNECTED)
        }
    }
}
