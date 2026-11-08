package com.phoenix.fytipc.climate

/** Enumerates every [ClimateAction] value, for command tables, inversion in the mock, and tests. */
object ClimateActions {
    val all: List<ClimateAction> = buildList {
        add(ClimateAction.Power)
        add(ClimateAction.ToggleAuto)
        add(ClimateAction.ToggleAc)
        add(ClimateAction.ToggleMaxAc)
        add(ClimateAction.ToggleDual)
        add(ClimateAction.ToggleRecirculate)
        add(ClimateAction.ToggleRearDefrost)
        add(ClimateAction.ToggleFrontDefrost)
        add(ClimateAction.FanUp)
        add(ClimateAction.FanDown)
        add(ClimateAction.CycleMode)
        for (zone in Zone.entries) {
            add(ClimateAction.TempUp(zone))
            add(ClimateAction.TempDown(zone))
            add(ClimateAction.CycleSeatHeat(zone))
            add(ClimateAction.CycleSeatCool(zone))
        }
    }
}
