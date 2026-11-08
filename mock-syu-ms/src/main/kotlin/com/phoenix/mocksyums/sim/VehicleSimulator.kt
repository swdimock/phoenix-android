package com.phoenix.mocksyums.sim

import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.Modules
import com.phoenix.fytipc.climate.ClimateAction
import com.phoenix.fytipc.climate.ClimateState
import com.phoenix.fytipc.profile.ClimateCodec
import com.phoenix.fytipc.profile.VehicleProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The simulated climate system, driven entirely by [profile]: received writes are inverted with
 * [CommandInverter], applied with [ClimateSimulation], then re-encoded with [codec] and only the
 * CANBUS codes whose value changed are published (the vendor notifies on change).
 */
class VehicleSimulator(
    private val profile: VehicleProfile,
    private val codec: ClimateCodec,
    private val publish: (ModuleUpdate) -> Unit,
    initial: ClimateState = ClimateSimulation.baseline(),
) {
    private val inverter = CommandInverter(profile)
    private val lastRaw = HashMap<Int, Int>()
    private val _state = MutableStateFlow(initial)

    val state: StateFlow<ClimateState> = _state.asStateFlow()

    /** Distinct commands the profile inversion understands. */
    val understoodCommands: Int get() = inverter.size

    /** Handles a received `cmd()`; returns the effect it was recognised as, or null if ignored. */
    fun onCommand(command: ModuleCommand): ClimateEffect? {
        val effect = inverter.invert(command) ?: return null
        update { ClimateSimulation.apply(it, effect, codec) }
        return effect
    }

    /** A press on the vehicle's own controls (debug screen). */
    fun press(action: ClimateAction) = update { ClimateSimulation.apply(it, ClimateEffect.Action(action), codec) }

    /** Arbitrary vehicle-side change (debug sliders). */
    fun mutate(transform: (ClimateState) -> ClimateState) = update { transform(it).copy(lastUpdateMs = System.currentTimeMillis()) }

    /**
     * A replayed vendor update: published verbatim and folded into the state through the profile,
     * without re-publishing what was just sent.
     */
    @Synchronized
    fun ingest(update: ModuleUpdate) {
        publish(update)
        if (update.module != Modules.CANBUS) return
        val next = profile.reduce(_state.value, update)
        if (next !== _state.value) {
            update.int0?.let { lastRaw[update.code] = it }
            _state.value = next
        }
    }

    /** Publishes every encoded code (cold boot: fills the vendor cache). */
    @Synchronized
    fun publishAll() {
        lastRaw.clear()
        publishDiff()
    }

    @Synchronized
    private fun update(transform: (ClimateState) -> ClimateState) {
        _state.value = transform(_state.value)
        publishDiff()
    }

    private fun publishDiff() {
        for ((code, value) in codec.encode(_state.value)) {
            if (lastRaw[code] == value) continue
            lastRaw[code] = value
            publish(ModuleUpdate(Modules.CANBUS, code, intArrayOf(value)))
        }
    }
}
