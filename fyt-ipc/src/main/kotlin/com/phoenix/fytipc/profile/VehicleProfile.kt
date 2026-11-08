package com.phoenix.fytipc.profile

import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.climate.ClimateAction
import com.phoenix.fytipc.climate.ClimateState

/**
 * Everything that is vehicle/CAN-box specific lives behind this interface so that the launcher and
 * HVAC bar are generic. Read codes (`U_AIR_*`) and write indices differ per CAN box; the
 * `mock-syu-ms` app uses the same profile so emulator behaviour matches whatever the profile says.
 *
 * Profiles are plain Kotlin for now. Once the 2011 F-150 values are recorded in the truck
 * (plan Phase 1b/1c), [FordF150Profile] gets real numbers; until then it mirrors the only
 * verified reference ([Wk2ReferenceProfile], Jeep Grand Cherokee on UIS7870).
 */
interface VehicleProfile {
    val id: String
    val displayName: String

    /** CANBUS codes to subscribe to for climate state. */
    val climateReadCodes: Set<Int>

    /** Fold one CANBUS update into the climate state. Return [state] unchanged if the code is not climate. */
    fun reduce(state: ClimateState, update: ModuleUpdate): ClimateState

    /** Translate a bar action into a vendor command, or null if this vehicle cannot do it. */
    fun command(action: ClimateAction, current: ClimateState): ModuleCommand?

    /**
     * Audio-path readiness. The 09-14 F-150 install keeps the OEM ACM + amp and feeds head-unit audio
     * through the ACM AUX input; the CAN box must select that source after the ACM wakes. Return true
     * when [update] indicates the CAN box / ACM is alive enough to accept the source select.
     */
    fun isAudioPathReadySignal(update: ModuleUpdate): Boolean

    /** Codes to subscribe to for [isAudioPathReadySignal]. */
    val audioReadinessCodes: Set<Int>

    /** Command that switches the OEM audio path to the head unit (AUX). Null if not yet known. */
    val audioSourceSelectCommand: ModuleCommand?

    /** Command that switches the OEM audio path back to the OEM radio/SXM. Null if not yet known. */
    val audioSourceRadioCommand: ModuleCommand?
}

/** Registry so apps can pick a profile by id from settings. */
object VehicleProfiles {
    val all: List<VehicleProfile> by lazy { listOf(FordF150Profile, Wk2ReferenceProfile) }
    val default: VehicleProfile get() = FordF150Profile
    fun byId(id: String?): VehicleProfile = all.firstOrNull { it.id == id } ?: default
}
