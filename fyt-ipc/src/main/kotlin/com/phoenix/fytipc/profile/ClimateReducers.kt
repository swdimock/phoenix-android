package com.phoenix.fytipc.profile

import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.Modules
import com.phoenix.fytipc.climate.ClimateAction
import com.phoenix.fytipc.climate.ClimateState
import com.phoenix.fytipc.climate.Setpoint
import com.phoenix.fytipc.climate.Zone

/**
 * Shared reduce/command logic for profiles that follow the FYT `U_AIR_*` conventions.
 * WORKER TODO (fyt-ipc): complete sentinels (LO/HI), mode decoding, seat cycle order (0->3->1->0 on WK2),
 * and add unit tests against recorded updates.
 */
internal object ClimateReducers {

    fun reduceGeneric(@Suppress("UNUSED_PARAMETER") p: Wk2ReferenceProfile, state: ClimateState, u: ModuleUpdate): ClimateState {
        if (u.module != Modules.CANBUS) return state
        val v = u.int0 ?: return state
        val c = Wk2ReferenceProfile.Codes
        val s = when (u.code) {
            c.U_AIR_POWER -> state.copy(power = v != 0)
            c.U_AIR_AUTO -> state.copy(auto = v != 0)
            c.U_AIR_AC -> state.copy(ac = v != 0)
            c.U_AIR_DUAL_SYNC -> state.copy(dual = v != 0)
            c.U_AIR_CYCLE -> state.copy(recirculate = v != 0)
            c.U_AIR_REAR_DEFROST -> state.copy(rearDefrost = v != 0)
            c.U_AIR_FRONT_DEFROST -> state.copy(frontDefrost = v != 0)
            c.U_AIR_WIND_LEVEL -> state.copy(fanSpeed = v)
            c.U_AIR_TEMP_LEFT -> state.copy(driverTemp = decodeTemp(v))
            c.U_AIR_TEMP_RIGHT -> state.copy(passengerTemp = decodeTemp(v))
            c.U_AIR_SEAT_HEAT_LEFT -> state.copy(driverSeatHeat = v)
            c.U_AIR_SEAT_HEAT_RIGHT -> state.copy(passengerSeatHeat = v)
            else -> return state
        }
        return s.copy(lastUpdateMs = u.timestampMs)
    }

    /** FYT reports temperature as tenths of a degree C in many protocols; sentinels TBD by worker. */
    private fun decodeTemp(raw: Int): Setpoint = when {
        raw <= 0 -> Setpoint(sentinel = Setpoint.Sentinel.LO)
        raw >= 0x7FF -> Setpoint(sentinel = Setpoint.Sentinel.HI)
        else -> Setpoint(tenthsC = raw)
    }

    fun commandGeneric(cmdCode: Int, action: ClimateAction): ModuleCommand? {
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
            ClimateAction.CycleMode -> i.MODE
            is ClimateAction.TempUp -> if (action.zone == Zone.DRIVER) i.TEMP_LEFT_UP else i.TEMP_RIGHT_UP
            is ClimateAction.TempDown -> if (action.zone == Zone.DRIVER) i.TEMP_LEFT_DOWN else i.TEMP_RIGHT_DOWN
            is ClimateAction.CycleSeatHeat -> if (action.zone == Zone.DRIVER) i.SEAT_HEAT_LEFT else i.SEAT_HEAT_RIGHT
            is ClimateAction.CycleSeatCool -> return null // not on WK2 reference
        }
        return ModuleCommand(Modules.CANBUS, cmdCode, intArrayOf(index, 1))
    }
}
