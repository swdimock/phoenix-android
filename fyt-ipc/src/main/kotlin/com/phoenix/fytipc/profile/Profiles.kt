package com.phoenix.fytipc.profile

import com.phoenix.fytipc.CanbusCodes
import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.Modules
import com.phoenix.fytipc.climate.AirMode
import com.phoenix.fytipc.climate.ClimateAction
import com.phoenix.fytipc.climate.ClimateState

/**
 * Reference profile transcribed from chrisuthe/7870-Projects (Jeep Grand Cherokee WK2, UIS7870,
 * PA canbus base protocol 374, vendor class `Car_0374_PA_Jeep_All` / `..._Wrangler`).
 * Sources: wiki "5 Climate Data Codes" (read codes, sentinels), "6 Sending Commands" (write indices),
 * the wiki home page corrections (AUTO toggle, MAX A/C side effects, seat cycle) and the
 * `wk2-climate` app (`Values.kt`: HI sentinel -3, unavailable -1). Everything here was measured on a
 * WK2 by that project; none of it has been measured on an F-150.
 */
object Wk2ReferenceProfile : VehicleProfile, ClimateCodec {
    override val id = "wk2-reference"
    override val displayName = "Jeep WK2 (reference, UIS7870)"

    /** `FinalCanbus` `U_AIR_*` read codes (universal across FYT vehicles; wiki page 5). */
    object Codes {
        const val U_AIR_POWER = 10
        const val U_AIR_AC = 11
        const val U_AIR_CYCLE = 12              // recirculate
        const val U_AIR_AUTO = 13
        const val U_AIR_DUAL = 14               // NOT what the DUAL button drives; see U_AIR_DUAL_SYNC
        const val U_AIR_REAR_DEFROST = 16
        const val U_AIR_BLOW_UP_LEFT = 18       // windshield flag
        const val U_AIR_BLOW_BODY_LEFT = 19     // face flag
        const val U_AIR_BLOW_FOOT_LEFT = 20     // feet flag
        const val U_AIR_WIND_LEVEL = 21         // U_AIR_WIND_LEVEL_LEFT: 0..7, 15 = AUTO sentinel
        const val U_AIR_TEMP_LEFT = 27
        const val U_AIR_TEMP_RIGHT = 28
        const val U_AIR_SEAT_HEAT_LEFT = 29     // U_AIR_SEAT_HOT_LEFT, 0..3
        const val U_AIR_SEAT_HEAT_RIGHT = 30
        const val U_AIR_SEAT_BLOW_LEFT = 31     // ventilation, 0..3, mutually exclusive with heat
        const val U_AIR_SEAT_BLOW_RIGHT = 32
        const val U_AIR_TEMP_UNIT = 37          // 1 = Fahrenheit
        const val U_AIR_ACMAX = 53              // MAX A/C
        const val U_AIR_DUAL_SYNC = 62          // U_AIR_SYNC, DUAL polarity: 1 = zones independent
        const val U_AIR_FRONT_DEFROST = 65
        const val U_AIR_HOT_STEER = 66          // heated steering wheel
        const val U_CANBUS_ID = 1000            // packed: low 16 = protocol base, high 16 = variant
    }

    /** Fan value reported while AUTO owns the blower. Never a speed. */
    const val FAN_AUTO = 15

    /** Write: `canbus.cmd(CMD_CODE, intArrayOf(1, index))` (wiki page 6, `sendInt(7, 6, {1, N})`). */
    const val CMD_CODE = 6

    /** First element of every write payload. */
    const val CMD_PREFIX = 1

    /** WK2 command indices from `Car_0374_PA_Jeep_All` plus the wiki's live sweep (page 6). */
    object CmdIndex {
        const val AC = 1                 // toggle
        const val AUTO = 2               // toggle + macro: on = A/C on, airflow clear, fan 15; off = fan 3, FACE
        const val RECIRC = 3             // toggle
        const val TEMP_LEFT_UP = 4       // 1 F per press
        const val TEMP_LEFT_DOWN = 5
        const val FAN_UP = 6             // exits AUTO (lands on fan 3)
        const val FAN_DOWN = 7
        const val AIRFLOW_FACE = 8       // idempotent setter
        const val AIRFLOW_FACE_FEET = 9  // idempotent setter
        const val AIRFLOW_FEET = 10      // idempotent setter (25 is a duplicate)
        const val AIRFLOW_FEET_DEFROST = 11 // idempotent setter
        const val FRONT_DEFROST = 12     // macro: recirc on, fan 6
        const val DUAL = 13              // the OEM "DUAL" button; toggles U_AIR_SYNC (62)
        const val REAR_DEFROST = 14      // toggle
        const val MAX_AC = 15            // macro: temps -> LO, recirc on, A/C on, AUTO off, fan 7
        const val POWER = 16             // toggle; turns climate OFF. Dangerous on cars with no HVAC panel
        const val SEAT_HEAT_LEFT = 17    // cycles 0 -> 3 -> 1 -> 0
        const val SEAT_HEAT_RIGHT = 18
        const val TEMP_RIGHT_UP = 20
        const val TEMP_RIGHT_DOWN = 21
        const val SEAT_COOL_LEFT = 22    // cycles (order assumed same as heat; UNVERIFIED)
        const val SEAT_COOL_RIGHT = 23
        const val HEATED_WHEEL = 24      // toggle
    }

    /**
     * Idempotent airflow setters in OEM cycle order (wiki page 5). There is no cycle command:
     * [ClimateAction.CycleMode] sends the setter for the next entry, [ClimateAction.SetMode] its own.
     */
    val airflowSetters: Map<AirMode, Int> = linkedMapOf(
        AirMode.FACE to CmdIndex.AIRFLOW_FACE,
        AirMode.FACE_FEET to CmdIndex.AIRFLOW_FACE_FEET,
        AirMode.FEET to CmdIndex.AIRFLOW_FEET,
        AirMode.FEET_DEFROST to CmdIndex.AIRFLOW_FEET_DEFROST,
    )

    override val tempRangeF: IntRange = 60..84    // WK2 driver setpoint measured 60..84
    override val fanMax: Int = 7
    override val seatLevelCycle: List<Int> = listOf(0, 3, 1)
    override val settableModes: List<AirMode> = airflowSetters.keys.toList()

    override val climateReadCodes: Set<Int> = setOf(
        Codes.U_AIR_POWER, Codes.U_AIR_AC, Codes.U_AIR_CYCLE, Codes.U_AIR_AUTO, Codes.U_AIR_REAR_DEFROST,
        Codes.U_AIR_BLOW_UP_LEFT, Codes.U_AIR_BLOW_BODY_LEFT, Codes.U_AIR_BLOW_FOOT_LEFT,
        Codes.U_AIR_WIND_LEVEL, Codes.U_AIR_TEMP_LEFT, Codes.U_AIR_TEMP_RIGHT,
        Codes.U_AIR_SEAT_HEAT_LEFT, Codes.U_AIR_SEAT_HEAT_RIGHT, Codes.U_AIR_SEAT_BLOW_LEFT, Codes.U_AIR_SEAT_BLOW_RIGHT,
        Codes.U_AIR_TEMP_UNIT, Codes.U_AIR_ACMAX, Codes.U_AIR_DUAL_SYNC, Codes.U_AIR_FRONT_DEFROST,
    )

    override fun reduce(state: ClimateState, update: ModuleUpdate): ClimateState =
        ClimateReducers.reduceGeneric(state, update)

    override fun command(action: ClimateAction, current: ClimateState): ModuleCommand? =
        ClimateReducers.commandGeneric(action, current)

    override fun encode(state: ClimateState): Map<Int, Int> = ClimateReducers.encodeGeneric(state)

    override val audioReadinessCodes: Set<Int> = setOf(CanbusCodes.U_EXIST_AIR)
    override fun isAudioPathReadySignal(update: ModuleUpdate): Boolean =
        update.module == Modules.CANBUS && update.code in audioReadinessCodes && (update.int0 ?: 0) > 0

    override val audioSourceSelectCommand: ModuleCommand? = null
    override val audioSourceRadioCommand: ModuleCommand? = null
}

/**
 * 2009-2014 Ford F-150 (2011 Raptor, Sync 2, dual-zone auto climate) on Phoenix Automotive FYT UIS7862.
 * CAN box: Phoenix "SimpleCan"/ChuangYuanTong Ford protocol; Car Model shows "...-FordF150".
 *
 * UNVERIFIED, all of it: delegates to [Wk2ReferenceProfile] until values are recorded in the truck.
 * The `U_AIR_*` read codes are universal FYT constants, so they are the most likely to hold; the
 * write indices are per CAN-box profile and are the most likely to be wrong. The wiki's supported-vehicle
 * list puts the 2014/2020 F-150 on the same PA base-374 profile as the WK2 (canbus id 3015030), but the
 * 2011 truck's box may be a different protocol entirely. Fields to confirm in plan Phase 1:
 *  - read codes for each bar element (baseline/diff method), and the canbus id (code 1000)
 *  - write indices for each bar button (two-pass sweep of `cmd(6, {1, N})`, wiki page 6)
 *  - setpoint range (Ford panel shows 60..85 F; UNVERIFIED). Seats: heated only, no ventilation (owner confirmed)
 *  - audio readiness signal + source select command (Phase 1c recording)
 */
object FordF150Profile : VehicleProfile by Wk2ReferenceProfile, ClimateCodec by Wk2ReferenceProfile {
    override val id = "ford-f150-2009-2014"
    override val displayName = "Ford F-150 2009-2014 (Phoenix)"

    // UNVERIFIED: Ford dual-zone EATC range.
    override val tempRangeF: IntRange = 60..85

    // UNVERIFIED placeholder: Ford selects OEM AUX via the CAN box. Replace with the recorded command.
    override val audioSourceSelectCommand: ModuleCommand? = null
    override val audioSourceRadioCommand: ModuleCommand? = null
}
