package com.phoenix.fytipc.profile

import com.phoenix.fytipc.climate.AirMode
import com.phoenix.fytipc.climate.ClimateAction
import com.phoenix.fytipc.climate.ClimateState
import com.phoenix.fytipc.climate.Setpoint
import kotlin.math.roundToInt

/**
 * Optional profile capability: the inverse of [VehicleProfile.reduce]. `mock-syu-ms` uses it to turn
 * its simulated [ClimateState] back into the vendor's CANBUS read codes, so the emulator follows
 * whatever the profile says.
 */
interface ClimateCodec {
    /** Setpoint range in whole degrees F; stepping past either end lands on the LO/HI sentinel. */
    val tempRangeF: IntRange

    /** Highest manual fan step. */
    val fanMax: Int

    /** Order a seat heat/vent level walks through on each cycle press, starting from off. */
    val seatLevelCycle: List<Int>

    /** Airflow modes the vehicle has a direct setter for, in the order [ClimateAction.CycleMode] walks. */
    val settableModes: List<AirMode>

    /** CANBUS code -> single int value for every known field of [state], in reporting order. */
    fun encode(state: ClimateState): Map<Int, Int>
}

/**
 * FYT `U_AIR_TEMP_*` value encoding (wiki page 5, plus `wk2-climate` `Values.kt`, verified on WK2):
 * with `U_AIR_TEMP_UNIT = 1` the raw value is whole degrees F; with 0 it is half-degrees C.
 * -2 = LO (also set by the MAX A/C macro), -3 = HI, -1 = unavailable / not fitted.
 * Values outside [RAW_MIN]..[RAW_MAX] are treated as unknown, matching the OEM renderer.
 */
object FytTemperature {
    const val LO = -2
    const val HI = -3
    const val UNAVAILABLE = -1
    const val RAW_MIN = 30
    const val RAW_MAX = 128

    fun decode(raw: Int, celsius: Boolean = false): Setpoint = when {
        raw == LO -> Setpoint(sentinel = Setpoint.Sentinel.LO)
        raw == HI -> Setpoint(sentinel = Setpoint.Sentinel.HI)
        raw < RAW_MIN || raw > RAW_MAX -> Setpoint()
        celsius -> Setpoint(tenthsC = raw * 5)
        else -> Setpoint(tenthsC = fToTenthsC(raw))
    }

    /** Fahrenheit wire value for [setpoint], or null when it is unknown. */
    fun encodeF(setpoint: Setpoint): Int? = encode(setpoint, celsius = false)

    /** Wire value for [setpoint] in the given unit (whole F or half C), or null when it is unknown. */
    fun encode(setpoint: Setpoint, celsius: Boolean): Int? = when (setpoint.sentinel) {
        Setpoint.Sentinel.LO -> LO
        Setpoint.Sentinel.HI -> HI
        Setpoint.Sentinel.NONE -> setpoint.tenthsC?.let { if (celsius) (it / 5.0).roundToInt() else tenthsCToF(it) }
    }

    fun fToTenthsC(f: Int): Int = ((f - 32) * 50 / 9.0).roundToInt()

    /** Same rounding as [Setpoint.displayF], so a round trip shows the same number. */
    fun tenthsCToF(tenthsC: Int): Int = Math.round(tenthsC / 10.0 * 9 / 5 + 32).toInt()

    fun ofF(f: Int): Setpoint = Setpoint(tenthsC = fToTenthsC(f))

    /**
     * One button press of [deltaF] degrees. Measured on WK2: sweeping steps *into* the sentinels at
     * the ends of the range rather than clamping, and steps back out of them.
     */
    fun step(setpoint: Setpoint, deltaF: Int, range: IntRange): Setpoint {
        val next: Int = when (setpoint.sentinel) {
            Setpoint.Sentinel.LO -> if (deltaF > 0) range.first else return setpoint
            Setpoint.Sentinel.HI -> if (deltaF < 0) range.last else return setpoint
            Setpoint.Sentinel.NONE -> {
                val f = setpoint.tenthsC?.let(::tenthsCToF) ?: return setpoint
                when {
                    deltaF > 0 && f >= range.last -> return Setpoint(sentinel = Setpoint.Sentinel.HI)
                    deltaF < 0 && f <= range.first -> return Setpoint(sentinel = Setpoint.Sentinel.LO)
                    else -> (f + deltaF).coerceIn(range)
                }
            }
        }
        return ofF(next)
    }
}

/**
 * Airflow on FYT is three independent flags, `U_AIR_BLOW_UP/BODY/FOOT_LEFT` (18/19/20), not an
 * enum (wiki page 5); [ClimateState.mode] derives the name. Four combinations are named by the OEM
 * cycle button; [AirMode.DEFROST] for "windshield only" is our mapping and UNVERIFIED. All-clear
 * reads as [AirMode.AUTO]; the front-defrost macro's up+body combination reads as [AirMode.UNKNOWN].
 */
object FytAirflow {
    /** Mode after [current] in [settable]; anything not in [settable] restarts at its first entry. */
    fun next(current: AirMode, settable: List<AirMode>): AirMode {
        require(settable.isNotEmpty()) { "no settable airflow modes" }
        val i = settable.indexOf(current)
        return settable[(i + 1) % settable.size]
    }
}
