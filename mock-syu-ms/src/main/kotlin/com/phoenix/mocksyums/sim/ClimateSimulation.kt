package com.phoenix.mocksyums.sim

import com.phoenix.fytipc.climate.AirMode
import com.phoenix.fytipc.climate.ClimateAction
import com.phoenix.fytipc.climate.ClimateState
import com.phoenix.fytipc.climate.Setpoint
import com.phoenix.fytipc.climate.Zone
import com.phoenix.fytipc.profile.ClimateCodec
import com.phoenix.fytipc.profile.FytAirflow
import com.phoenix.fytipc.profile.FytTemperature

/** What one received write does to the vehicle. */
sealed interface ClimateEffect {
    data class Action(val action: ClimateAction) : ClimateEffect

    /** An idempotent airflow setter (WK2 indices 8..11). */
    data class SetMode(val mode: AirMode) : ClimateEffect
}

/**
 * Deliberately adversarial climate model, after `wk2-climate`'s FakeVehicleBus: every rule is
 * measured WK2 behaviour unless marked UNVERIFIED (nothing here has been measured on the F-150).
 *  - AUTO toggles. On: A/C on, airflow cleared, fan reports the AUTO sentinel. Off: fan 3, FACE.
 *  - FAN_UP/FAN_DOWN while AUTO exit AUTO (fan 3, FACE); fan clamps at [ClimateCodec.fanMax].
 *  - MAX A/C: temps to LO, recirc on, A/C on, AUTO off, fan max, FACE. Front defrost: recirc on,
 *    AUTO off, fan 6, airflow unrecognised. The two macros toggle into each other.
 *  - Setpoints step 1 F and step into/out of the LO/HI sentinels at the range ends.
 *  - DUAL polarity: `dual == false` (U_AIR_SYNC = 0) means a driver change moves both zones.
 *  - Seat heat/vent cycle 0 -> 3 -> 1 -> 0 and are mutually exclusive per seat.
 */
object ClimateSimulation {

    fun apply(state: ClimateState, effect: ClimateEffect, codec: ClimateCodec, nowMs: Long = System.currentTimeMillis()): ClimateState {
        val isPower = effect == ClimateEffect.Action(ClimateAction.Power)
        // WK2: the system wakes on any control press after N=16 switched it off.
        val s = if (state.power == false && !isPower) state.copy(power = true) else state
        val next = when (effect) {
            is ClimateEffect.SetMode -> setMode(s, effect.mode)
            is ClimateEffect.Action -> action(s, effect.action, codec)
        }
        return next.copy(lastUpdateMs = nowMs)
    }

    private fun action(s: ClimateState, a: ClimateAction, codec: ClimateCodec): ClimateState = when (a) {
        ClimateAction.Power -> s.copy(power = s.power != true)
        ClimateAction.ToggleAuto ->
            if (s.auto == true) exitAuto(s)
            else s.copy(auto = true, ac = true, fanSpeed = null, mode = AirMode.UNKNOWN, maxAc = false, frontDefrost = false)
        ClimateAction.ToggleAc -> s.copy(ac = s.ac != true)
        ClimateAction.ToggleMaxAc ->
            // UNVERIFIED: turning MAX off only clears the flag; the WK2 needs several rounds to unwind.
            if (s.maxAc == true) s.copy(maxAc = false)
            else s.copy(
                maxAc = true, frontDefrost = false, ac = true, recirculate = true, auto = false,
                fanSpeed = codec.fanMax, mode = AirMode.FACE,
                driverTemp = LO, passengerTemp = LO,
            )
        ClimateAction.ToggleFrontDefrost ->
            if (s.frontDefrost == true) s.copy(frontDefrost = false)
            else s.copy(frontDefrost = true, maxAc = false, recirculate = true, auto = false, fanSpeed = 6, mode = AirMode.UNKNOWN)
        ClimateAction.ToggleDual -> s.copy(dual = s.dual != true)
        ClimateAction.ToggleRecirculate -> s.copy(recirculate = s.recirculate != true)
        ClimateAction.ToggleRearDefrost -> s.copy(rearDefrost = s.rearDefrost != true)
        ClimateAction.FanUp -> if (s.auto == true) exitAuto(s) else s.copy(fanSpeed = ((s.fanSpeed ?: 0) + 1).coerceAtMost(codec.fanMax))
        ClimateAction.FanDown -> if (s.auto == true) exitAuto(s) else s.copy(fanSpeed = ((s.fanSpeed ?: 0) - 1).coerceAtLeast(0))
        ClimateAction.CycleMode -> setMode(s, FytAirflow.nextInCycle(s.mode))
        is ClimateAction.TempUp -> stepTemp(s, a.zone, +1, codec)
        is ClimateAction.TempDown -> stepTemp(s, a.zone, -1, codec)
        is ClimateAction.CycleSeatHeat -> cycleSeat(s, a.zone, heat = true, codec)
        is ClimateAction.CycleSeatCool -> cycleSeat(s, a.zone, heat = false, codec)
    }

    private val LO = Setpoint(sentinel = Setpoint.Sentinel.LO)

    private fun exitAuto(s: ClimateState) = s.copy(auto = false, fanSpeed = 3, mode = AirMode.FACE)

    /** UNVERIFIED: choosing airflow manually cancels AUTO; the fan keeps 3 if AUTO owned it. */
    private fun setMode(s: ClimateState, mode: AirMode) = s.copy(
        mode = mode,
        auto = if (s.auto == true) false else s.auto,
        fanSpeed = s.fanSpeed ?: if (s.auto == true) 3 else null,
        frontDefrost = if (s.frontDefrost == true) false else s.frontDefrost,
    )

    private fun stepTemp(s: ClimateState, zone: Zone, delta: Int, codec: ClimateCodec): ClimateState {
        val range = codec.tempRangeF
        return when (zone) {
            Zone.DRIVER -> {
                val t = FytTemperature.step(s.driverTemp, delta, range)
                if (s.dual == true) s.copy(driverTemp = t) else s.copy(driverTemp = t, passengerTemp = t)
            }
            // UNVERIFIED (Ford EATC behaviour): touching the passenger setpoint switches DUAL on.
            Zone.PASSENGER -> s.copy(passengerTemp = FytTemperature.step(s.passengerTemp, delta, range), dual = true)
        }
    }

    private fun cycleSeat(s: ClimateState, zone: Zone, heat: Boolean, codec: ClimateCodec): ClimateState {
        val cycle = codec.seatLevelCycle
        fun next(level: Int?): Int {
            val i = cycle.indexOf(level ?: 0)
            return if (i < 0) cycle.first() else cycle[(i + 1) % cycle.size]
        }
        return when {
            zone == Zone.DRIVER && heat -> next(s.driverSeatHeat).let { s.copy(driverSeatHeat = it, driverSeatCool = if (it != 0) 0 else s.driverSeatCool) }
            zone == Zone.DRIVER -> next(s.driverSeatCool).let { s.copy(driverSeatCool = it, driverSeatHeat = if (it != 0) 0 else s.driverSeatHeat) }
            heat -> next(s.passengerSeatHeat).let { s.copy(passengerSeatHeat = it, passengerSeatCool = if (it != 0) 0 else s.passengerSeatCool) }
            else -> next(s.passengerSeatCool).let { s.copy(passengerSeatCool = it, passengerSeatHeat = if (it != 0) 0 else s.passengerSeatHeat) }
        }
    }

    /** WK2 snapshot from `wk2-climate` VEHICLE_BASELINE: AUTO on, 68 F both zones, DUAL lit. */
    fun baseline(): ClimateState = ClimateState(
        power = true, auto = true, ac = true, maxAc = false, dual = true, recirculate = false,
        rearDefrost = false, frontDefrost = false, fanSpeed = null, mode = AirMode.UNKNOWN,
        driverTemp = FytTemperature.ofF(68), passengerTemp = FytTemperature.ofF(68),
        driverSeatHeat = 0, passengerSeatHeat = 0, driverSeatCool = 0, passengerSeatCool = 0,
    )
}
