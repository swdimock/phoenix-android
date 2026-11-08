package com.phoenix.launcher.settings

import com.phoenix.fytipc.profile.VehicleProfile
import com.phoenix.fytipc.profile.VehicleProfiles
import com.phoenix.launcher.mode.LauncherMode

/** User-configurable launcher settings, persisted in DataStore by [SettingsRepository]. */
data class LauncherSettings(
    val defaultMode: LauncherMode = LauncherMode.AA,
    val vehicleProfileId: String = VehicleProfiles.default.id,
    val audioReadyTimeoutMs: Long = DEFAULT_AUDIO_READY_TIMEOUT_MS,
    val aaReceiverPackage: String = DEFAULT_AA_RECEIVER_PACKAGE,
) {
    val vehicleProfile: VehicleProfile get() = VehicleProfiles.byId(vehicleProfileId)

    companion object {
        const val DEFAULT_AUDIO_READY_TIMEOUT_MS = 15_000L

        /** Open Headunit (headunit-revived). Replaced by our own fork later. */
        const val DEFAULT_AA_RECEIVER_PACKAGE = "com.andrerinas.headunitrevived"
    }
}
