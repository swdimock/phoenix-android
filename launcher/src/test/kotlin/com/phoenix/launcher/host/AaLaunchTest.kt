package com.phoenix.launcher.host

import com.google.common.truth.Truth.assertThat
import com.phoenix.launcher.settings.AaTransport
import com.phoenix.launcher.settings.LauncherSettings
import org.junit.Test

class AaLaunchTest {

    @Test
    fun tcpExtras_carryHostPortAndBarInset() {
        val extras = AaLaunchExtras.from(LauncherSettings(aaTransport = AaTransport.TCP, aaTcpHost = "127.0.0.1", aaTcpPort = 5277))
        assertThat(extras).containsExactly(
            "phoenix.transport", "tcp",
            "phoenix.host", "127.0.0.1",
            "phoenix.port", 5277,
            "phoenix.inset_bottom", 227,
            "phoenix.home_button", false,
        )
    }

    @Test
    fun usbExtras_omitHostAndPort() {
        val extras = AaLaunchExtras.from(LauncherSettings(aaTransport = AaTransport.USB))
        assertThat(extras).containsExactly("phoenix.transport", "usb", "phoenix.inset_bottom", 227, "phoenix.home_button", false)
    }

    @Test
    fun describe() {
        assertThat(AaLaunchExtras.describe(LauncherSettings(aaTransport = AaTransport.TCP))).isEqualTo("TCP 127.0.0.1:5277")
        assertThat(AaLaunchExtras.describe(LauncherSettings(aaTransport = AaTransport.USB))).isEqualTo("USB")
    }

    @Test
    fun transportDefault_tcpOnEmulatorOnly() {
        assertThat(AaTransport.defaultFor("google/sdk_gphone64_x86_64/emu64x:12/generic", "ranchu")).isEqualTo(AaTransport.TCP)
        assertThat(AaTransport.defaultFor("generic/sdk_phone_x86_64/emulator_x86_64:12", "unknown")).isEqualTo(AaTransport.TCP)
        // A vendor build with "generic" in its fingerprint is NOT an emulator.
        assertThat(AaTransport.defaultFor("sprd/generic_ums512/ums512:12/SP1A.210812.016/release-keys", "ums512")).isEqualTo(AaTransport.USB)
        assertThat(AaTransport.defaultFor("alps/ums512/ums512:12/user", "goldfish")).isEqualTo(AaTransport.TCP)
        assertThat(AaTransport.defaultFor("alps/ums512_1h10/ums512:12/SP1A/user/release-keys", "ums512")).isEqualTo(AaTransport.USB)
        assertThat(AaTransport.defaultFor(null, null)).isEqualTo(AaTransport.USB)
    }

    @Test
    fun transportParse() {
        assertThat(AaTransport.parse("tcp")).isEqualTo(AaTransport.TCP)
        assertThat(AaTransport.parse("usb")).isEqualTo(AaTransport.USB)
        assertThat(AaTransport.parse("wifi")).isNull()
    }

    @Test
    fun autoLaunch_waitsForAudioThenFiresOnce() {
        val gate = AaAutoLaunchGate()
        gate.arm()
        assertThat(gate.shouldLaunch(currentModeIsAa = true, audioReady = false, installed = true)).isFalse()
        assertThat(gate.shouldLaunch(currentModeIsAa = true, audioReady = true, installed = true)).isTrue()
        assertThat(gate.shouldLaunch(currentModeIsAa = true, audioReady = true, installed = true)).isFalse()
    }

    @Test
    fun autoLaunch_freshActivityInSameProcessDoesNotRearm() {
        val gate = AaAutoLaunchGate()
        gate.arm()
        assertThat(gate.shouldLaunch(currentModeIsAa = true, audioReady = true, installed = true)).isTrue()
        gate.arm()
        assertThat(gate.shouldLaunch(currentModeIsAa = true, audioReady = true, installed = true)).isFalse()
    }

    @Test
    fun autoLaunch_neverWhenNotArmed() {
        val gate = AaAutoLaunchGate()
        assertThat(gate.shouldLaunch(currentModeIsAa = true, audioReady = true, installed = true)).isFalse()
    }

    @Test
    fun autoLaunch_cancelledByLeavingAaOrMissingReceiver() {
        val left = AaAutoLaunchGate().apply { arm() }
        assertThat(left.shouldLaunch(currentModeIsAa = false, audioReady = false, installed = true)).isFalse()
        assertThat(left.shouldLaunch(currentModeIsAa = true, audioReady = true, installed = true)).isFalse()

        val missing = AaAutoLaunchGate().apply { arm() }
        assertThat(missing.shouldLaunch(currentModeIsAa = true, audioReady = false, installed = false)).isFalse()
        assertThat(missing.shouldLaunch(currentModeIsAa = true, audioReady = true, installed = true)).isFalse()
    }
}
