package com.phoenix.launcher.settings

import android.os.Build
import com.phoenix.fytipc.profile.VehicleProfile
import com.phoenix.fytipc.profile.VehicleProfiles
import com.phoenix.launcher.mode.LauncherMode

/** User-configurable launcher settings, persisted in DataStore by [SettingsRepository]. */
data class LauncherSettings(
    val defaultMode: LauncherMode = LauncherMode.AA,
    val vehicleProfileId: String = VehicleProfiles.default.id,
    val audioReadyTimeoutMs: Long = DEFAULT_AUDIO_READY_TIMEOUT_MS,
    val aaReceiverPackage: String = DEFAULT_AA_RECEIVER_PACKAGE,
    val aaTransport: AaTransport = AaTransport.USB,
    val aaTcpHost: String = DEFAULT_AA_TCP_HOST,
    val aaTcpPort: Int = DEFAULT_AA_TCP_PORT,
) {
    val vehicleProfile: VehicleProfile get() = VehicleProfiles.byId(vehicleProfileId)

    companion object {
        const val DEFAULT_AUDIO_READY_TIMEOUT_MS = 15_000L

        /** Open Headunit (applicationId kept from headunit-revived); our fork in ../open-headunit keeps it. */
        const val DEFAULT_AA_RECEIVER_PACKAGE = "com.andrerinas.headunitrevived"

        /**
         * Emulator loopback. `adb reverse tcp:5277 tcp:5277` (done by install-all.sh) maps it to the PC, where
         * `adb forward` exposes the phone's head unit server. The emulator's NAT alias 10.0.2.2 timed out on
         * Windows/WHPX, the adb reverse path works everywhere.
         */
        const val DEFAULT_AA_TCP_HOST = "127.0.0.1"

        /** Android Auto's developer "head unit server" port. */
        const val DEFAULT_AA_TCP_PORT = 5277

        /** Defaults for this device: TCP on the emulator (no USB phone there), USB on the head unit. */
        fun platformDefaults(): LauncherSettings =
            LauncherSettings(aaTransport = AaTransport.defaultFor(Build.FINGERPRINT, Build.HARDWARE))
    }
}

/** How the AA receiver reaches the phone. [id] is the persisted value and the receiver's launch extra. */
enum class AaTransport(val id: String, val label: String) {
    USB("usb", "USB"),
    TCP("tcp", "TCP"),
    ;

    companion object {
        fun parse(id: String?): AaTransport? = entries.firstOrNull { it.id == id }

        /**
         * Emulator detection. Deliberately narrow: some vendor builds (including FYT/Spreadtrum ones) carry
         * "generic" in their fingerprint, so we only trust the emulator kernels and the Google SDK products.
         */
        fun isEmulator(fingerprint: String?, hardware: String?): Boolean {
            val fp = fingerprint.orEmpty()
            return hardware in setOf("ranchu", "goldfish") ||
                fp.startsWith("google/sdk_") ||
                fp.startsWith("generic/sdk_") ||
                fp.contains("/emulator")
        }

        fun defaultFor(fingerprint: String?, hardware: String?): AaTransport =
            if (isEmulator(fingerprint, hardware)) TCP else USB
    }
}
