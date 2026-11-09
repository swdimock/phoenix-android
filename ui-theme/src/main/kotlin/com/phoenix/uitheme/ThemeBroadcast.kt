package com.phoenix.uitheme

import android.content.Intent

/**
 * Launcher -> other Phoenix apps: "the user picked this accent". Must be sent explicitly with a package;
 * Android 12 does not deliver implicit broadcasts to manifest receivers.
 */
object ThemeBroadcast {
    const val ACTION_ACCENT = "com.phoenix.theme.action.ACCENT"
    const val EXTRA_PRESET = "preset"

    fun encode(preset: AccentPreset): String = preset.id

    /** The preset named by an [EXTRA_PRESET] value, or null when missing or unknown. */
    fun decode(value: String?): AccentPreset? = AccentPreset.fromId(value)

    /** Explicit broadcast for [targetPackage]. Includes stopped packages: the vendor service force-stops us on sleep. */
    fun intent(preset: AccentPreset, targetPackage: String): Intent =
        Intent(ACTION_ACCENT)
            .setPackage(targetPackage)
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            .putExtra(EXTRA_PRESET, encode(preset))

    /** The preset carried by [intent], or null when it is not a valid accent broadcast. */
    fun presetFrom(intent: Intent): AccentPreset? =
        if (intent.action == ACTION_ACCENT) decode(intent.getStringExtra(EXTRA_PRESET)) else null
}
