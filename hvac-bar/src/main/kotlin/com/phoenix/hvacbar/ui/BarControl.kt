package com.phoenix.hvacbar.ui

import com.phoenix.fytipc.climate.ClimateAction
import com.phoenix.fytipc.climate.Zone

/** Every tappable element on the bar and the [ClimateAction] it issues. */
enum class BarControl(val action: ClimateAction) {
    // Row 1
    POWER(ClimateAction.Power),
    AUTO(ClimateAction.ToggleAuto),
    REAR_DEFROST(ClimateAction.ToggleRearDefrost),
    DUAL(ClimateAction.ToggleDual),
    AC(ClimateAction.ToggleAc),

    // Row 2
    DRIVER_SEAT_HEAT(ClimateAction.CycleSeatHeat(Zone.DRIVER)),
    DRIVER_SEAT_COOL(ClimateAction.CycleSeatCool(Zone.DRIVER)),
    FRONT_DEFROST(ClimateAction.ToggleFrontDefrost),
    MODE(ClimateAction.CycleMode),
    RECIRCULATE(ClimateAction.ToggleRecirculate),
    MAX_AC(ClimateAction.ToggleMaxAc),
    PASSENGER_SEAT_COOL(ClimateAction.CycleSeatCool(Zone.PASSENGER)),
    PASSENGER_SEAT_HEAT(ClimateAction.CycleSeatHeat(Zone.PASSENGER)),

    // Row 3
    DRIVER_TEMP_DOWN(ClimateAction.TempDown(Zone.DRIVER)),
    DRIVER_TEMP_UP(ClimateAction.TempUp(Zone.DRIVER)),
    FAN_DOWN(ClimateAction.FanDown),
    FAN_UP(ClimateAction.FanUp),
    PASSENGER_TEMP_DOWN(ClimateAction.TempDown(Zone.PASSENGER)),
    PASSENGER_TEMP_UP(ClimateAction.TempUp(Zone.PASSENGER)),
}
