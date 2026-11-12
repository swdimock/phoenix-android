package com.phoenix.hvacbar.ui

import com.phoenix.fytipc.ConnectionState
import com.phoenix.fytipc.climate.AirMode
import com.phoenix.fytipc.climate.ClimateState
import com.phoenix.fytipc.climate.Setpoint
import kotlin.math.roundToInt

/** Rendering state of an on/off control. [UNKNOWN] until the vehicle reports a value. */
enum class Lamp {
    ON, OFF, UNKNOWN;

    companion object {
        fun of(value: Boolean?): Lamp = when (value) {
            true -> ON
            false -> OFF
            null -> UNKNOWN
        }

        fun ofLevel(level: Int?): Lamp = of(level?.let { it > 0 })
    }
}

/** A temperature as displayed: [value] is "--", "LO", "HI" or a number; [unit] is set for numbers only. */
data class TempText(val value: String, val unit: String?)

/** A seat heater/cooler: [level] 0..[BarUiState.SEAT_LEVELS], null when unknown. */
data class SeatUi(val level: Int?) {
    val lamp: Lamp get() = Lamp.ofLevel(level)
}

/** Everything [HvacBar] needs to draw, derived purely from vehicle state and preferences. */
data class BarUiState(
    val interactive: Boolean,
    val power: Lamp,
    val auto: Lamp,
    val dual: Lamp,
    val ac: Lamp,
    val maxAc: Lamp,
    val frontDefrost: Lamp,
    val recirculate: Lamp,
    /** Derived airflow mode: drawn as a glyph on the MODE button ([modeText] for AUTO / unknown and descriptions). */
    val mode: AirMode,
    val driverTemp: TempText,
    val passengerTemp: TempText,
    /** Lit fan segments out of [FAN_SEGMENTS]; null when unknown or under AUTO control. */
    val fanSegments: Int?,
    /** The blower is under AUTO control: segments are dimmed and labelled [AUTO_TEXT]. */
    val fanAuto: Boolean,
    /** Heated seats only: the 2011 Raptor has no seat ventilation, so the bar never shows cooling controls. */
    val driverSeatHeat: SeatUi,
    val passengerSeatHeat: SeatUi,
) {
    companion object {
        const val FAN_SEGMENTS = 7
        const val SEAT_LEVELS = 3
        const val UNKNOWN_TEXT = "--"
        const val AUTO_TEXT = "AUTO"
    }
}

/** Maps vehicle [climate] to what the bar shows. Taps are only accepted while [connection] is CONNECTED. */
fun barUiState(climate: ClimateState, connection: ConnectionState, useCelsius: Boolean): BarUiState =
    BarUiState(
        interactive = connection == ConnectionState.CONNECTED,
        power = Lamp.of(climate.power),
        auto = Lamp.of(climate.auto),
        dual = Lamp.of(climate.dual),
        ac = Lamp.of(climate.ac),
        maxAc = Lamp.of(climate.maxAc),
        frontDefrost = Lamp.of(climate.frontDefrost),
        recirculate = Lamp.of(climate.recirculate),
        mode = climate.mode,
        driverTemp = tempText(climate.driverTemp, useCelsius),
        passengerTemp = tempText(climate.passengerTemp, useCelsius),
        fanSegments = if (climate.fanAuto == true) null else fanSegments(climate.fanSpeed, climate.fanMax),
        fanAuto = climate.fanAuto == true,
        driverSeatHeat = SeatUi(climate.driverSeatHeat),
        passengerSeatHeat = SeatUi(climate.passengerSeatHeat),
    )

/** Short name of [mode]: the MODE button's text for AUTO / unknown, and its content description otherwise. */
internal fun modeText(mode: AirMode): String = when (mode) {
    AirMode.FACE -> "FACE"
    AirMode.FACE_FEET -> "FACE+FEET"
    AirMode.FEET -> "FEET"
    AirMode.FEET_DEFROST -> "FEET+DEF"
    AirMode.DEFROST -> "DEF"
    AirMode.AUTO -> BarUiState.AUTO_TEXT
    AirMode.UNKNOWN -> BarUiState.UNKNOWN_TEXT
}

internal fun tempText(setpoint: Setpoint, useCelsius: Boolean): TempText {
    val value = if (useCelsius) setpoint.displayC() else setpoint.displayF()
    val numeric = setpoint.sentinel == Setpoint.Sentinel.NONE && setpoint.tenthsC != null
    return TempText(value, if (numeric) (if (useCelsius) "\u00B0C" else "\u00B0F") else null)
}

internal fun fanSegments(speed: Int?, max: Int): Int? {
    if (speed == null) return null
    if (max <= 0) return 0
    val scaled = (speed.coerceIn(0, max) * BarUiState.FAN_SEGMENTS.toFloat() / max).roundToInt()
    return scaled.coerceIn(0, BarUiState.FAN_SEGMENTS)
}
