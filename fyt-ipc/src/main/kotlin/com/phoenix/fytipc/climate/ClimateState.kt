package com.phoenix.fytipc.climate

import kotlinx.serialization.Serializable

enum class Zone { DRIVER, PASSENGER }

/**
 * Airflow mode as shown on the bar, derived from the three airflow flags (see [of]). Combos exist on
 * Ford (e.g. FACE_FEET, FEET_DEFROST). [AUTO] means every flag is clear: the system chooses airflow.
 * [UNKNOWN] covers both "nothing reported yet" and combinations without a name (e.g. windshield+face).
 */
enum class AirMode {
    FACE, FACE_FEET, FEET, FEET_DEFROST, DEFROST, AUTO, UNKNOWN;

    companion object {
        /** Mode for the flags; a null flag counts as clear once at least one flag is known. */
        fun of(windshield: Boolean?, face: Boolean?, feet: Boolean?): AirMode {
            if (windshield == null && face == null && feet == null) return UNKNOWN
            val flags = Airflow(windshield == true, face == true, feet == true)
            return AIRFLOW.entries.firstOrNull { it.value == flags }?.key ?: UNKNOWN
        }

        /** Flags for [mode]; null for [UNKNOWN]. */
        internal fun flagsOf(mode: AirMode): Airflow? = AIRFLOW[mode]

        private val AIRFLOW: Map<AirMode, Airflow> = mapOf(
            DEFROST to Airflow(windshield = true, face = false, feet = false),
            FACE to Airflow(windshield = false, face = true, feet = false),
            FACE_FEET to Airflow(windshield = false, face = true, feet = true),
            FEET to Airflow(windshield = false, face = false, feet = true),
            FEET_DEFROST to Airflow(windshield = true, face = false, feet = true),
            AUTO to Airflow(windshield = false, face = false, feet = false),
        )
    }
}

internal data class Airflow(val windshield: Boolean, val face: Boolean, val feet: Boolean)

/**
 * Temperature setpoint. Ford dual-zone auto climate shows 60..85 F in 1 F steps with LO/HI sentinels.
 * [tenthsC] keeps Celsius units internally to match what the CAN box usually reports.
 */
@Serializable
data class Setpoint(val tenthsC: Int? = null, val sentinel: Sentinel = Sentinel.NONE) {
    enum class Sentinel { NONE, LO, HI }

    val isKnown: Boolean get() = tenthsC != null || sentinel != Sentinel.NONE

    fun displayF(): String = when (sentinel) {
        Sentinel.LO -> "LO"
        Sentinel.HI -> "HI"
        Sentinel.NONE -> tenthsC?.let { Math.round(it / 10.0 * 9 / 5 + 32).toString() } ?: "--"
    }

    fun displayC(): String = when (sentinel) {
        Sentinel.LO -> "LO"
        Sentinel.HI -> "HI"
        Sentinel.NONE -> tenthsC?.let { "%.1f".format(it / 10.0) } ?: "--"
    }
}

/**
 * Snapshot of the vehicle climate system as reported by the CAN box. All fields nullable-ish so the
 * UI can render "unknown" until the first update for each code arrives (the vendor only notifies on
 * change and replays cached values on registration; a quiet vehicle may report nothing for a while).
 */
@Serializable
data class ClimateState(
    val power: Boolean? = null,
    val auto: Boolean? = null,
    val ac: Boolean? = null,
    val maxAc: Boolean? = null,
    val dual: Boolean? = null,
    val recirculate: Boolean? = null,
    val rearDefrost: Boolean? = null,
    val frontDefrost: Boolean? = null,
    val fanSpeed: Int? = null,          // 0..fanMax; null while fanAuto
    val fanAuto: Boolean? = null,       // blower under AUTO control (WK2: code 21 == 15)
    val fanMax: Int = 7,
    val airWindshield: Boolean? = null,
    val airFace: Boolean? = null,
    val airFeet: Boolean? = null,
    /** Unit the vehicle reports setpoints in (`U_AIR_TEMP_UNIT`); decides how later setpoints decode. */
    val tempUnitCelsius: Boolean? = null,
    val driverTemp: Setpoint = Setpoint(),
    val passengerTemp: Setpoint = Setpoint(),
    val driverSeatHeat: Int? = null,    // 0..3
    val passengerSeatHeat: Int? = null, // 0..3
    val driverSeatCool: Int? = null,    // 0..3; kept in the model for vehicles with ventilated seats (the 2011 Raptor has none; the bar hides it)
    val passengerSeatCool: Int? = null,
    val lastUpdateMs: Long = 0L,
) {
    /** Derived purely from [airWindshield], [airFace] and [airFeet]. */
    val mode: AirMode get() = AirMode.of(airWindshield, airFace, airFeet)

    val hasAnyData: Boolean
        get() = power != null || auto != null || fanSpeed != null || fanAuto != null ||
            driverTemp.isKnown || passengerTemp.isKnown

    /** Copy with the airflow flags that make up [mode]; [AirMode.UNKNOWN] clears them to unreported. */
    fun withMode(mode: AirMode): ClimateState {
        val f = AirMode.flagsOf(mode)
        return copy(airWindshield = f?.windshield, airFace = f?.face, airFeet = f?.feet)
    }

    companion object {
        val EMPTY = ClimateState()
    }
}

/** User intents from the HVAC bar. Profiles translate these into vendor `cmd()` calls. */
sealed interface ClimateAction {
    data object Power : ClimateAction
    data object ToggleAuto : ClimateAction
    data object ToggleAc : ClimateAction
    data object ToggleMaxAc : ClimateAction
    data object ToggleDual : ClimateAction
    data object ToggleRecirculate : ClimateAction
    data object ToggleRearDefrost : ClimateAction
    data object ToggleFrontDefrost : ClimateAction
    data object FanUp : ClimateAction
    data object FanDown : ClimateAction
    /** Next settable airflow mode after the current one, in the profile's order. */
    data object CycleMode : ClimateAction
    /** Select [mode] directly. Profiles return no command for modes the vehicle cannot set. */
    data class SetMode(val mode: AirMode) : ClimateAction
    data class TempUp(val zone: Zone) : ClimateAction
    data class TempDown(val zone: Zone) : ClimateAction
    data class CycleSeatHeat(val zone: Zone) : ClimateAction
    data class CycleSeatCool(val zone: Zone) : ClimateAction
}
