package com.phoenix.hvacbar.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.phoenix.hvacbar.barApp
import com.phoenix.uitheme.ThemeBroadcast

/** Accent preset pushed by the launcher. Persisted; a showing bar recolours immediately via [com.phoenix.hvacbar.BarSettings]. */
class ThemeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val preset = ThemeBroadcast.presetFrom(intent)
        if (preset == null) {
            Log.w(TAG, "ignoring accent broadcast with preset=${intent.getStringExtra(ThemeBroadcast.EXTRA_PRESET)}")
            return
        }
        context.barApp.settings.setAccentPreset(preset)
    }

    private companion object {
        const val TAG = "HvacBarTheme"
    }
}
