package com.phoenix.mocksyums.sim

import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.climate.AirMode
import com.phoenix.fytipc.climate.ClimateAction
import com.phoenix.fytipc.climate.ClimateActions
import com.phoenix.fytipc.climate.ClimateState
import com.phoenix.fytipc.profile.FytAirflow
import com.phoenix.fytipc.profile.VehicleProfile

/**
 * Inverts [VehicleProfile.command]: given a received `cmd()`, which climate effect does it name?
 *
 * Built once by evaluating the profile for every action. [ClimateAction.CycleMode] is evaluated
 * for every current [AirMode]: if the profile answers with a different command per mode (WK2:
 * idempotent setters 8..11), each one inverts to "set mode = next in cycle"; if it always answers
 * with the same command, that command is a true cycle button.
 */
class CommandInverter(profile: VehicleProfile) {

    private data class Key(val module: Int, val code: Int, val ints: List<Int>?)

    private val table: Map<Key, ClimateEffect> = buildMap {
        for (action in ClimateActions.all) {
            if (action == ClimateAction.CycleMode) continue
            profile.command(action, ClimateState.EMPTY)?.let { putIfAbsent(key(it), ClimateEffect.Action(action)) }
        }
        val perMode = AirMode.entries.mapNotNull { m ->
            profile.command(ClimateAction.CycleMode, ClimateState(mode = m))?.let { m to key(it) }
        }
        if (perMode.map { it.second }.toSet().size <= 1) {
            perMode.firstOrNull()?.let { putIfAbsent(it.second, ClimateEffect.Action(ClimateAction.CycleMode)) }
        } else {
            for ((m, k) in perMode) putIfAbsent(k, ClimateEffect.SetMode(FytAirflow.nextInCycle(m)))
        }
    }

    fun invert(command: ModuleCommand): ClimateEffect? = table[key(command)]

    /** Number of distinct commands understood; for the debug screen. */
    val size: Int get() = table.size

    private fun key(c: ModuleCommand) = Key(c.module, c.code, c.ints?.toList())
}
