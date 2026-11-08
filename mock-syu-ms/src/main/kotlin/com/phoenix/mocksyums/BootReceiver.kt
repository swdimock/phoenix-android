package com.phoenix.mocksyums

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.ToolkitService

/** Starts the mock service at boot, as the vendor service is on real units. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(Intent(context, ToolkitService::class.java))
        }
    }
}
