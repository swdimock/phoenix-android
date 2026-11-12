package com.phoenix.uitheme

import android.content.Intent

/**
 * Launcher -> HVAC bar: whether the Phoenix launcher (system HOME) is currently in the foreground.
 * Explicit package: Android 12 does not deliver implicit broadcasts to manifest receivers.
 */
object LauncherHomeBroadcast {
    const val ACTION = "com.phoenix.launcher.action.HOME"
    const val EXTRA_VISIBLE = "visible"

    /** Explicit broadcast for [targetPackage]. Includes stopped packages: the vendor service force-stops us on sleep. */
    fun intent(visible: Boolean, targetPackage: String): Intent =
        Intent(ACTION)
            .setPackage(targetPackage)
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            .putExtra(EXTRA_VISIBLE, visible)

    /** Whether the launcher is in the foreground per [intent], or null when [intent] is not a home broadcast. */
    fun visibleFrom(intent: Intent): Boolean? =
        if (intent.action == ACTION) intent.getBooleanExtra(EXTRA_VISIBLE, false) else null
}
