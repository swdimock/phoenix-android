package com.phoenix.launcher.host

import android.content.Context
import com.phoenix.launcher.audio.AudioPathBroadcasts
import com.phoenix.launcher.settings.AaTransport
import com.phoenix.launcher.settings.LauncherSettings

/** Hosts the external Android Auto receiver app. */
interface AaHost {
    /** Package currently configured as the receiver. */
    val receiverPackage: String

    fun isInstalled(): Boolean

    /**
     * Tells the receiver whether the audio path is ready (so it can hold audio), then starts it with the
     * Phoenix launch extras ([AaLaunchExtras]). Returns false if the receiver is not installed.
     */
    fun launch(): Boolean
}

/** [AaHost] that starts [packageName] via its launcher intent. */
class PackageAaHost(
    private val context: Context,
    private val settings: () -> LauncherSettings,
    private val audioReady: () -> Boolean,
) : AaHost {

    private val app = VendorApp(context, { settings().aaReceiverPackage })

    override val receiverPackage: String get() = settings().aaReceiverPackage

    override fun isInstalled(): Boolean = app.resolve() != null

    override fun launch(): Boolean {
        if (!isInstalled()) return false
        AudioPathBroadcasts.send(context, audioReady(), receiverPackage)
        val extras = AaLaunchExtras.from(settings())
        return app.launch { intent ->
            extras.forEach { (key, value) ->
                if (value is Int) intent.putExtra(key, value) else intent.putExtra(key, value.toString())
            }
        }
    }
}

/** Launch extras understood by our Open Headunit fork (see docs/ANDROID-AUTO-EMULATOR.md). */
object AaLaunchExtras {
    const val EXTRA_TRANSPORT = "phoenix.transport"
    const val EXTRA_HOST = "phoenix.host"
    const val EXTRA_PORT = "phoenix.port"
    const val EXTRA_INSET_BOTTOM = "phoenix.inset_bottom"

    /** Height of the hvac-bar overlay pinned to the bottom of the screen (hvac-bar `BarGeometry.HEIGHT_PX`). */
    const val HVAC_BAR_HEIGHT_PX = 227

    fun from(settings: LauncherSettings): Map<String, Any> = buildMap {
        put(EXTRA_TRANSPORT, settings.aaTransport.id)
        if (settings.aaTransport == AaTransport.TCP) {
            put(EXTRA_HOST, settings.aaTcpHost)
            put(EXTRA_PORT, settings.aaTcpPort)
        }
        put(EXTRA_INSET_BOTTOM, HVAC_BAR_HEIGHT_PX)
    }

    /** "TCP 10.0.2.2:5277" or "USB", for the AA pane. */
    fun describe(settings: LauncherSettings): String = when (settings.aaTransport) {
        AaTransport.TCP -> "TCP ${settings.aaTcpHost}:${settings.aaTcpPort}"
        AaTransport.USB -> "USB"
    }
}
