package com.phoenix.hvacbar.ui

import com.phoenix.fytipc.climate.ClimateAction
import com.phoenix.fytipc.climate.Zone

/**
 * Every climate control on the bar and the [ClimateAction] it issues. Seat ventilation and rear defrost are
 * deliberately absent: the truck has heated seats only and no rear-window defroster. (The HOME button is not a
 * climate control and lives in [HvacBar] directly.)
 */
enum class BarControl(val action: ClimateAction) {
    // Row 1
    POWER(ClimateAction.Power),
    AUTO(ClimateAction.ToggleAuto),
    AC(ClimateAction.ToggleAc),
    DUAL(ClimateAction.ToggleDual),

    // Row 2
    DRIVER_SEAT_HEAT(ClimateAction.CycleSeatHeat(Zone.DRIVER)),
    FRONT_DEFROST(ClimateAction.ToggleFrontDefrost),
    MODE(ClimateAction.CycleMode),
    RECIRCULATE(ClimateAction.ToggleRecirculate),
    MAX_AC(ClimateAction.ToggleMaxAc),
    PASSENGER_SEAT_HEAT(ClimateAction.CycleSeatHeat(Zone.PASSENGER)),

    // Row 3
    DRIVER_TEMP_DOWN(ClimateAction.TempDown(Zone.DRIVER)),
    DRIVER_TEMP_UP(ClimateAction.TempUp(Zone.DRIVER)),
    FAN_DOWN(ClimateAction.FanDown),
    FAN_UP(ClimateAction.FanUp),
    PASSENGER_TEMP_DOWN(ClimateAction.TempDown(Zone.PASSENGER)),
    PASSENGER_TEMP_UP(ClimateAction.TempUp(Zone.PASSENGER)),
}
