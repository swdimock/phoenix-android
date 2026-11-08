package com.phoenix.fytipc.profile

import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.Modules
import com.phoenix.fytipc.climate.AirMode
import com.phoenix.fytipc.climate.ClimateAction
import com.phoenix.fytipc.climate.ClimateState
import com.phoenix.fytipc.climate.Zone
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared reduce/command/encode logic for profiles that follow the FYT `U_AIR_*` conventions
 * ([Wk2ReferenceProfile] tables).
 *
 * Two facts do not fit in [ClimateState] and are latched here, process-wide (one process talks to
 * one vehicle): the raw airflow flags, because three independent flags arrive one update at a time
 * and several combinations all read as [AirMode.UNKNOWN]; and `U_AIR_TEMP_UNIT`, which decides how
 * setpoints decode. The flag latch is only consulted while the state's mode is UNKNOWN; a named
 * mode is always authoritative.
 */
internal object ClimateReducers {

    private val airflowLatch = AtomicInteger(0)
    private val celsius = AtomicBoolean(false)

    /** Test hook: forget latched airflow flags and temperature unit. */
    internal fun resetLatches() {
        airflowLatch.set(0)
        celsius.set(false)
    }

    fun reduceGeneric(state: ClimateState, u: ModuleUpdate): ClimateState {
        if (u.module != Modules.CANBUS) return state
        val v = u.int0 ?: return state
        val c = Wk2ReferenceProfile.Codes
        val s = when (u.code) {
            c.U_AIR_POWER -> state.copy(power = v != 0)
            c.U_AIR_AUTO -> state.copy(auto = v != 0)
            c.U_AIR_AC -> state.copy(ac = v != 0)
            c.U_AIR_ACMAX -> state.copy(maxAc = v != 0)
            c.U_AIR_DUAL_SYNC -> state.copy(dual = v != 0) // DUAL polarity: 1 = independent zones
            c.U_AIR_CYCLE -> state.copy(recirculate = v != 0)
            c.U_AIR_REAR_DEFROST -> state.copy(rearDefrost = v != 0)
            c.U_AIR_FRONT_DEFROST -> state.copy(frontDefrost = v != 0)
            // 15 = AUTO owns the blower: no speed to show; `auto` (code 13) carries the meaning.
            c.U_AIR_WIND_LEVEL -> state.copy(fanSpeed = v.takeIf { it in 0..Wk2ReferenceProfile.fanMax })
            c.U_AIR_TEMP_LEFT -> state.copy(driverTemp = FytTemperature.decode(v, celsius.get()))
            c.U_AIR_TEMP_RIGHT -> state.copy(passengerTemp = FytTemperature.decode(v, celsius.get()))
            c.U_AIR_SEAT_HEAT_LEFT -> state.copy(driverSeatHeat = v.takeIf { it in 0..3 })
            c.U_AIR_SEAT_HEAT_RIGHT -> state.copy(passengerSeatHeat = v.takeIf { it in 0..3 })
            c.U_AIR_SEAT_BLOW_LEFT -> state.copy(driverSeatCool = v.takeIf { it in 0..3 })
            c.U_AIR_SEAT_BLOW_RIGHT -> state.copy(passengerSeatCool = v.takeIf { it in 0..3 })
            c.U_AIR_BLOW_UP_LEFT -> state.copy(mode = foldAirflow(state.mode, FytAirflow.UP, v != 0))
            c.U_AIR_BLOW_BODY_LEFT -> state.copy(mode = foldAirflow(state.mode, FytAirflow.BODY, v != 0))
            c.U_AIR_BLOW_FOOT_LEFT -> state.copy(mode = foldAirflow(state.mode, FytAirflow.FOOT, v != 0))
            c.U_AIR_TEMP_UNIT -> {
                // Setpoints already decoded keep their value; only later updates use the new unit.
                celsius.set(v == 0)
                state
            }
            else -> return state
        }
        return s.copy(lastUpdateMs = u.timestampMs)
    }

    private fun foldAirflow(mode: AirMode, bit: Int, on: Boolean): AirMode {
        val base = FytAirflow.flagsOf(mode)
            ?: airflowLatch.get().takeIf { FytAirflow.modeOf(it) == AirMode.UNKNOWN }
            ?: 0
        val flags = if (on) base or bit else base and bit.inv()
        airflowLatch.set(flags)
        return FytAirflow.modeOf(flags)
    }

    fun commandGeneric(action: ClimateAction, current: ClimateState): ModuleCommand {
        val i = Wk2ReferenceProfile.CmdIndex
        val index = when (action) {
            ClimateAction.Power -> i.POWER
            ClimateAction.ToggleAuto -> i.AUTO
            ClimateAction.ToggleAc -> i.AC
            ClimateAction.ToggleMaxAc -> i.MAX_AC
            ClimateAction.ToggleDual -> i.DUAL
            ClimateAction.ToggleRecirculate -> i.RECIRC
            ClimateAction.ToggleRearDefrost -> i.REAR_DEFROST
            ClimateAction.ToggleFrontDefrost -> i.FRONT_DEFROST
            ClimateAction.FanUp -> i.FAN_UP
            ClimateAction.FanDown -> i.FAN_DOWN
            ClimateAction.CycleMode -> airflowIndex(FytAirflow.nextInCycle(current.mode))
            is ClimateAction.TempUp -> if (action.zone == Zone.DRIVER) i.TEMP_LEFT_UP else i.TEMP_RIGHT_UP
            is ClimateAction.TempDown -> if (action.zone == Zone.DRIVER) i.TEMP_LEFT_DOWN else i.TEMP_RIGHT_DOWN
            is ClimateAction.CycleSeatHeat -> if (action.zone == Zone.DRIVER) i.SEAT_HEAT_LEFT else i.SEAT_HEAT_RIGHT
            is ClimateAction.CycleSeatCool -> if (action.zone == Zone.DRIVER) i.SEAT_COOL_LEFT else i.SEAT_COOL_RIGHT
        }
        return ModuleCommand(
            Modules.CANBUS,
            Wk2ReferenceProfile.CMD_CODE,
            intArrayOf(Wk2ReferenceProfile.CMD_PREFIX, index),
        )
    }

    /** Idempotent airflow setter index for one of the four OEM cycle modes. */
    fun airflowIndex(mode: AirMode): Int {
        val i = Wk2ReferenceProfile.CmdIndex
        return when (mode) {
            AirMode.FACE_FEET -> i.AIRFLOW_FACE_FEET
            AirMode.FEET -> i.AIRFLOW_FEET
            AirMode.FEET_DEFROST -> i.AIRFLOW_FEET_DEFROST
            else -> i.AIRFLOW_FACE
        }
    }

    fun encodeGeneric(state: ClimateState): Map<Int, Int> {
        val c = Wk2ReferenceProfile.Codes
        val out = LinkedHashMap<Int, Int>()
        fun put(code: Int, value: Int?) { if (value != null) out[code] = value }
        fun putFlag(code: Int, value: Boolean?) = put(code, value?.let { if (it) 1 else 0 })

        put(c.U_AIR_TEMP_UNIT, 1)
        putFlag(c.U_AIR_POWER, state.power)
        putFlag(c.U_AIR_AUTO, state.auto)
        putFlag(c.U_AIR_AC, state.ac)
        putFlag(c.U_AIR_ACMAX, state.maxAc)
        putFlag(c.U_AIR_DUAL_SYNC, state.dual)
        putFlag(c.U_AIR_CYCLE, state.recirculate)
        putFlag(c.U_AIR_REAR_DEFROST, state.rearDefrost)
        putFlag(c.U_AIR_FRONT_DEFROST, state.frontDefrost)
        put(c.U_AIR_WIND_LEVEL, state.fanSpeed ?: if (state.auto == true) Wk2ReferenceProfile.FAN_AUTO else null)
        put(c.U_AIR_TEMP_LEFT, FytTemperature.encodeF(state.driverTemp))
        put(c.U_AIR_TEMP_RIGHT, FytTemperature.encodeF(state.passengerTemp))
        put(c.U_AIR_SEAT_HEAT_LEFT, state.driverSeatHeat)
        put(c.U_AIR_SEAT_HEAT_RIGHT, state.passengerSeatHeat)
        put(c.U_AIR_SEAT_BLOW_LEFT, state.driverSeatCool)
        put(c.U_AIR_SEAT_BLOW_RIGHT, state.passengerSeatCool)
        val flags = FytAirflow.flagsOf(state.mode) ?: 0
        putFlag(c.U_AIR_BLOW_UP_LEFT, flags and FytAirflow.UP != 0)
        putFlag(c.U_AIR_BLOW_BODY_LEFT, flags and FytAirflow.BODY != 0)
        putFlag(c.U_AIR_BLOW_FOOT_LEFT, flags and FytAirflow.FOOT != 0)
        return out
    }
}
