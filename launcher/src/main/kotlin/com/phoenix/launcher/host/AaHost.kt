package com.phoenix.launcher.host

import android.content.Context
import com.phoenix.launcher.audio.AudioPathBroadcasts

/** Hosts the external Android Auto receiver app. */
interface AaHost {
    /** Package currently configured as the receiver. */
    val receiverPackage: String

    fun isInstalled(): Boolean

    /**
     * Tells the receiver whether the audio path is ready (so it can hold audio), then starts it.
     * Returns false if the receiver is not installed.
     */
    fun launch(): Boolean
}

/** [AaHost] that starts [receiverPackage] via its launcher intent. */
class PackageAaHost(
    private val context: Context,
    private val packageName: () -> String,
    private val audioReady: () -> Boolean,
) : AaHost {

    private val app = VendorApp(context, packageName)

    override val receiverPackage: String get() = packageName()

    override fun isInstalled(): Boolean = app.resolve() != null

    override fun launch(): Boolean {
        if (!isInstalled()) return false
        AudioPathBroadcasts.send(context, audioReady(), receiverPackage)
        return app.launch()
    }
}
