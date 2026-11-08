package com.phoenix.fytipc.profile

import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.Modules
import com.phoenix.fytipc.climate.ClimateAction
import com.phoenix.fytipc.climate.ClimateState
import com.phoenix.fytipc.climate.Zone

/**
 * Shared reduce/command/encode logic for profiles that follow the FYT `U_AIR_*` conventions
 * ([Wk2ReferenceProfile] tables). Pure: everything a later update depends on (airflow flags,
 * temperature unit) is carried in [ClimateState].
 */
internal object ClimateReducers {

    fun reduceGeneric(state: ClimateState, u: ModuleUpdate): ClimateState {
        if (u.module != Modules.CANBUS) return state
        val v = u.int0 ?: return state
        val c = Wk2ReferenceProfile.Codes
        val celsius = state.tempUnitCelsius == true
        val s = when (u.code) {
            c.U_AIR_POWER -> state.copy(power = v != 0)
            c.U_AIR_AUTO -> state.copy(auto = v != 0)
            c.U_AIR_AC -> state.copy(ac = v != 0)
            c.U_AIR_ACMAX -> state.copy(maxAc = v != 0)
            c.U_AIR_DUAL_SYNC -> state.copy(dual = v != 0) // DUAL polarity: 1 = independent zones
            c.U_AIR_CYCLE -> state.copy(recirculate = v != 0)
            c.U_AIR_REAR_DEFROST -> state.copy(rearDefrost = v != 0)
            c.U_AIR_FRONT_DEFROST -> state.copy(frontDefrost = v != 0)
            c.U_AIR_WIND_LEVEL -> when (v) {
                Wk2ReferenceProfile.FAN_AUTO -> state.copy(fanAuto = true, fanSpeed = null)
                in 0..Wk2ReferenceProfile.fanMax -> state.copy(fanAuto = false, fanSpeed = v)
                else -> state.copy(fanAuto = null, fanSpeed = null)
            }
            c.U_AIR_TEMP_LEFT -> state.copy(driverTemp = FytTemperature.decode(v, celsius))
            c.U_AIR_TEMP_RIGHT -> state.copy(passengerTemp = FytTemperature.decode(v, celsius))
            c.U_AIR_SEAT_HEAT_LEFT -> state.copy(driverSeatHeat = v.takeIf { it in 0..3 })
            c.U_AIR_SEAT_HEAT_RIGHT -> state.copy(passengerSeatHeat = v.takeIf { it in 0..3 })
            c.U_AIR_SEAT_BLOW_LEFT -> state.copy(driverSeatCool = v.takeIf { it in 0..3 })
            c.U_AIR_SEAT_BLOW_RIGHT -> state.copy(passengerSeatCool = v.takeIf { it in 0..3 })
            c.U_AIR_BLOW_UP_LEFT -> state.copy(airWindshield = v != 0)
            c.U_AIR_BLOW_BODY_LEFT -> state.copy(airFace = v != 0)
            c.U_AIR_BLOW_FOOT_LEFT -> state.copy(airFeet = v != 0)
            // Setpoints already decoded keep their value; only later updates use the new unit.
            c.U_AIR_TEMP_UNIT -> state.copy(tempUnitCelsius = v == 0)
            else -> return state
        }
        return s.copy(lastUpdateMs = u.timestampMs)
    }

    fun commandGeneric(action: ClimateAction, current: ClimateState): ModuleCommand? {
        val i = Wk2ReferenceProfile.CmdIndex
        val setters = Wk2ReferenceProfile.airflowSetters
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
            ClimateAction.CycleMode -> setters.getValue(FytAirflow.next(current.mode, Wk2ReferenceProfile.settableModes))
            is ClimateAction.SetMode -> setters[action.mode] ?: return null
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

    fun encodeGeneric(state: ClimateState): Map<Int, Int> {
        val c = Wk2ReferenceProfile.Codes
        val out = LinkedHashMap<Int, Int>()
        fun put(code: Int, value: Int?) { if (value != null) out[code] = value }
        fun putFlag(code: Int, value: Boolean?) = put(code, value?.let { if (it) 1 else 0 })

        val celsius = state.tempUnitCelsius == true
        put(c.U_AIR_TEMP_UNIT, if (celsius) 0 else 1)
        putFlag(c.U_AIR_POWER, state.power)
        putFlag(c.U_AIR_AUTO, state.auto)
        putFlag(c.U_AIR_AC, state.ac)
        putFlag(c.U_AIR_ACMAX, state.maxAc)
        putFlag(c.U_AIR_DUAL_SYNC, state.dual)
        putFlag(c.U_AIR_CYCLE, state.recirculate)
        putFlag(c.U_AIR_REAR_DEFROST, state.rearDefrost)
        putFlag(c.U_AIR_FRONT_DEFROST, state.frontDefrost)
        put(c.U_AIR_WIND_LEVEL, if (state.fanAuto == true) Wk2ReferenceProfile.FAN_AUTO else state.fanSpeed)
        put(c.U_AIR_TEMP_LEFT, FytTemperature.encode(state.driverTemp, celsius))
        put(c.U_AIR_TEMP_RIGHT, FytTemperature.encode(state.passengerTemp, celsius))
        put(c.U_AIR_SEAT_HEAT_LEFT, state.driverSeatHeat)
        put(c.U_AIR_SEAT_HEAT_RIGHT, state.passengerSeatHeat)
        put(c.U_AIR_SEAT_BLOW_LEFT, state.driverSeatCool)
        put(c.U_AIR_SEAT_BLOW_RIGHT, state.passengerSeatCool)
        putFlag(c.U_AIR_BLOW_UP_LEFT, state.airWindshield)
        putFlag(c.U_AIR_BLOW_BODY_LEFT, state.airFace)
        putFlag(c.U_AIR_BLOW_FOOT_LEFT, state.airFeet)
        return out
    }
}
