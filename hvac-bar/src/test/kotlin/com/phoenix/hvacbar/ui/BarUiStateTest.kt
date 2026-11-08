package com.phoenix.hvacbar.ui

import com.google.common.truth.Truth.assertThat
import com.phoenix.fytipc.ConnectionState
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.Modules
import com.phoenix.fytipc.climate.AirMode
import com.phoenix.fytipc.climate.ClimateAction
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
        assertThat(ui.fanAuto).isFalse()
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
    fun fanCode15RendersAutoInsteadOfSegments() {
        val auto = barUiState(reduced(codes.U_AIR_WIND_LEVEL to 15), ConnectionState.CONNECTED, useCelsius = false)
        assertThat(auto.fanAuto).isTrue()
        assertThat(auto.fanSegments).isNull()

        val manual = barUiState(reduced(codes.U_AIR_WIND_LEVEL to 15, codes.U_AIR_WIND_LEVEL to 4), ConnectionState.CONNECTED, useCelsius = false)
        assertThat(manual.fanAuto).isFalse()
        assertThat(manual.fanSegments).isEqualTo(4)

        assertThat(barUiState(BarSamples.fullAuto, ConnectionState.CONNECTED, useCelsius = false).fanAuto).isTrue()
    }

    @Test
    fun modeButtonShowsTheModeDerivedFromTheAirflowFlags() {
        fun shown(up: Int, body: Int, foot: Int) = barUiState(
            reduced(codes.U_AIR_BLOW_UP_LEFT to up, codes.U_AIR_BLOW_BODY_LEFT to body, codes.U_AIR_BLOW_FOOT_LEFT to foot),
            ConnectionState.CONNECTED,
            useCelsius = false,
        ).mode
        assertThat(shown(0, 1, 0)).isEqualTo("FACE")
        assertThat(shown(0, 1, 1)).isEqualTo("FACE+FEET")
        assertThat(shown(0, 0, 1)).isEqualTo("FEET")
        assertThat(shown(1, 0, 1)).isEqualTo("FEET+DEF")
        assertThat(shown(1, 0, 0)).isEqualTo("DEF")
        assertThat(shown(0, 0, 0)).isEqualTo("AUTO")
        assertThat(shown(1, 1, 0)).isEqualTo("--")
        assertThat(AirMode.entries.map(::modeText).toSet()).hasSize(AirMode.entries.size)
    }

    @Test
    fun modeButtonCyclesTheMode() {
        assertThat(BarControl.MODE.action).isEqualTo(ClimateAction.CycleMode)
    }

    @Test
    fun anythingButConnectedIsNotInteractive() {
        ConnectionState.entries.forEach { connection ->
            val ui = barUiState(BarSamples.typicalDriving, connection, useCelsius = false)
            assertThat(ui.interactive).isEqualTo(connection == ConnectionState.CONNECTED)
        }
    }
}
