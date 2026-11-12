package com.phoenix.hvacbar.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.phoenix.hvacbar.barApp
import com.phoenix.uitheme.LauncherHomeBroadcast

/** Launcher foreground presence. A showing bar lights its HOME key while the launcher is on screen. */
class HomeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val visible = LauncherHomeBroadcast.visibleFrom(intent) ?: return
        context.barApp.settings.setLauncherHome(visible)
    }
}
