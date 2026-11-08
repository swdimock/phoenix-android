package com.phoenix.fytipc.profile

import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.climate.ClimateAction
import com.phoenix.fytipc.climate.ClimateState

/**
 * Reference profile transcribed from chrisuthe/7870-Projects (Jeep Grand Cherokee WK2, UIS7870).
 * Wiki pages: "5 Climate Data Codes" (read codes / sentinels) and "6 Sending Commands" (write indices).
 *
 * WORKER TODO (fyt-ipc): fetch those two wiki pages and fill in the real tables. Keep the public
 * shape; the numbers below are placeholders so the project compiles.
 */
object Wk2ReferenceProfile : VehicleProfile {
    override val id = "wk2-reference"
    override val displayName = "Jeep WK2 (reference, UIS7870)"

    // Placeholder subset; see wiki page 5 for the full 87 U_AIR_* codes.
    object Codes {
        const val U_AIR_POWER = 20
        const val U_AIR_AUTO = 22
        const val U_AIR_AC = 21
        const val U_AIR_DUAL_SYNC = 62      // polarity: 1 = independent (DUAL lit)
        const val U_AIR_CYCLE = 23          // recirculate
        const val U_AIR_REAR_DEFROST = 25
        const val U_AIR_FRONT_DEFROST = 24
        const val U_AIR_WIND_LEVEL = 26     // fan
        const val U_AIR_TEMP_LEFT = 27
        const val U_AIR_TEMP_RIGHT = 28
        const val U_AIR_SEAT_HEAT_LEFT = 40
        const val U_AIR_SEAT_HEAT_RIGHT = 41
        const val U_AIR_MODE = 30
    }

    /** Write: `canbus.cmd(CMD_CODE, intArrayOf(index, value))` per wiki page 6. */
    const val CMD_CODE = 6

    object CmdIndex {
        const val POWER = 16
        const val AUTO = 2
        const val AC = 1
        const val TEMP_LEFT_UP = 4
        const val TEMP_LEFT_DOWN = 5
        const val TEMP_RIGHT_UP = 6
        const val TEMP_RIGHT_DOWN = 7
        const val FAN_UP = 8
        const val FAN_DOWN = 9
        const val MODE = 10
        const val RECIRC = 11
        const val FRONT_DEFROST = 12
        const val REAR_DEFROST = 13
        const val DUAL = 14
        const val MAX_AC = 15
        const val SEAT_HEAT_LEFT = 20
        const val SEAT_HEAT_RIGHT = 21
    }

    override val climateReadCodes: Set<Int> = setOf(
        Codes.U_AIR_POWER, Codes.U_AIR_AUTO, Codes.U_AIR_AC, Codes.U_AIR_DUAL_SYNC, Codes.U_AIR_CYCLE,
        Codes.U_AIR_REAR_DEFROST, Codes.U_AIR_FRONT_DEFROST, Codes.U_AIR_WIND_LEVEL,
        Codes.U_AIR_TEMP_LEFT, Codes.U_AIR_TEMP_RIGHT, Codes.U_AIR_SEAT_HEAT_LEFT, Codes.U_AIR_SEAT_HEAT_RIGHT,
        Codes.U_AIR_MODE,
    )

    override fun reduce(state: ClimateState, update: ModuleUpdate): ClimateState =
        ClimateReducers.reduceGeneric(this, state, update)

    override fun command(action: ClimateAction, current: ClimateState): ModuleCommand? =
        ClimateReducers.commandGeneric(CMD_CODE, action)

    override val audioReadinessCodes: Set<Int> = setOf(com.phoenix.fytipc.CanbusCodes.U_EXIST_AIR)
    override fun isAudioPathReadySignal(update: ModuleUpdate): Boolean =
        update.code in audioReadinessCodes && (update.int0 ?: 0) > 0

    override val audioSourceSelectCommand: ModuleCommand? = null
    override val audioSourceRadioCommand: ModuleCommand? = null
}

/**
 * 2009-2014 Ford F-150 (2011 Raptor, Sync 2, dual-zone auto climate) on Phoenix Automotive FYT UIS7862.
 * CAN box: Phoenix "SimpleCan"/ChuangYuanTong Ford protocol; Car Model shows "...-FordF150".
 *
 * All values are UNVERIFIED until recorded in the truck. They start as a copy of the WK2 reference so
 * the emulator has something coherent to simulate. Fields to confirm in plan Phase 1:
 *  - read codes for each bar element (baseline/diff method)
 *  - write indices for each bar button
 *  - audio readiness signal + source select command (Phase 1c recording)
 */
object FordF150Profile : VehicleProfile by Wk2ReferenceProfile {
    override val id = "ford-f150-2009-2014"
    override val displayName = "Ford F-150 2009-2014 (Phoenix)"

    // Placeholder: Ford selects OEM AUX via the CAN box. Replace with recorded command.
    override val audioSourceSelectCommand: ModuleCommand? = null
    override val audioSourceRadioCommand: ModuleCommand? = null
}
