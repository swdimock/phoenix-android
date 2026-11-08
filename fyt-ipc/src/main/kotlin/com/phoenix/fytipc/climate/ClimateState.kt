package com.phoenix.fytipc.climate

import kotlinx.serialization.Serializable

enum class Zone { DRIVER, PASSENGER }

/** Airflow mode as shown on the bar. Combos exist on Ford (e.g. FACE_FEET, FEET_DEFROST). */
enum class AirMode { FACE, FACE_FEET, FEET, FEET_DEFROST, DEFROST, UNKNOWN }

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
    val fanSpeed: Int? = null,          // 0..fanMax
    val fanMax: Int = 7,
    val mode: AirMode = AirMode.UNKNOWN,
    val driverTemp: Setpoint = Setpoint(),
    val passengerTemp: Setpoint = Setpoint(),
    val driverSeatHeat: Int? = null,    // 0..3
    val passengerSeatHeat: Int? = null, // 0..3
    val driverSeatCool: Int? = null,    // 0..3 (Raptor has cooled seats)
    val passengerSeatCool: Int? = null,
    val lastUpdateMs: Long = 0L,
) {
    val hasAnyData: Boolean
        get() = power != null || auto != null || fanSpeed != null || driverTemp.isKnown || passengerTemp.isKnown

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
    data object CycleMode : ClimateAction
    data class TempUp(val zone: Zone) : ClimateAction
    data class TempDown(val zone: Zone) : ClimateAction
    data class CycleSeatHeat(val zone: Zone) : ClimateAction
    data class CycleSeatCool(val zone: Zone) : ClimateAction
}
