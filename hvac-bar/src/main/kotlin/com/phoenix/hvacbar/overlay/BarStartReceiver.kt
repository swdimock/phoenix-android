package com.phoenix.hvacbar.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Brings the bar up on boot, app update, ACC on (mock) and an explicit show request. */
class BarStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in START_ACTIONS) BarService.start(context)
    }

    companion object {
        const val ACTION_SHOW = "com.phoenix.hvacbar.action.SHOW"
        const val ACTION_MOCK_ACC_ON = "com.phoenix.mock.ACC_ON"

        private val START_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_SHOW,
            ACTION_MOCK_ACC_ON,
        )
    }
}
