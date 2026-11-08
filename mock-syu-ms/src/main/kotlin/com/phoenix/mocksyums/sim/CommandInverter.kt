package com.phoenix.mocksyums.sim

import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.climate.AirMode
import com.phoenix.fytipc.climate.ClimateAction
import com.phoenix.fytipc.climate.ClimateActions
import com.phoenix.fytipc.climate.ClimateState
import com.phoenix.fytipc.profile.VehicleProfile

/**
 * Inverts [VehicleProfile.command]: given a received `cmd()`, which climate action does it name?
 *
 * Built once by evaluating the profile for every action, including [ClimateAction.SetMode] for every
 * [AirMode]. [ClimateAction.CycleMode] is then evaluated for every current mode: if the profile always
 * answers with the same command, that command is a true cycle button; otherwise (WK2: idempotent
 * setters 8..11) each answer is already one of the SetMode commands and inverts to that.
 */
class CommandInverter(profile: VehicleProfile) {

    private data class Key(val module: Int, val code: Int, val ints: List<Int>?)

    private val table: Map<Key, ClimateAction> = buildMap {
        for (action in ClimateActions.all) {
            if (action == ClimateAction.CycleMode) continue
            profile.command(action, ClimateState.EMPTY)?.let { putIfAbsent(key(it), action) }
        }
        val cycleKeys = AirMode.entries.mapNotNullTo(HashSet()) { m ->
            profile.command(ClimateAction.CycleMode, ClimateState.EMPTY.withMode(m))?.let(::key)
        }
        cycleKeys.singleOrNull()?.let { putIfAbsent(it, ClimateAction.CycleMode) }
    }

    fun invert(command: ModuleCommand): ClimateAction? = table[key(command)]

    /** Number of distinct commands understood; for the debug screen. */
    val size: Int get() = table.size

    private fun key(c: ModuleCommand) = Key(c.module, c.code, c.ints?.toList())
}
