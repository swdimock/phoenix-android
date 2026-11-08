package com.phoenix.launcher.audio

import android.content.Context
import android.content.Intent
import com.phoenix.fytipc.MockSyuDebug

/** Broadcast contract between the launcher and the AA receiver (see docs/ARCHITECTURE.md). */
object AudioPathBroadcasts {
    const val ACTION_AUDIO_PATH = "com.phoenix.launcher.action.AUDIO_PATH"
    const val EXTRA_READY = "ready"

    /** Emulator mock (and later the real wake hook) signals ignition. */
    const val ACTION_ACC_ON = MockSyuDebug.ACTION_ACC_ON
    const val ACTION_ACC_OFF = MockSyuDebug.ACTION_ACC_OFF

    /**
     * Sends the readiness broadcast implicitly (for runtime-registered receivers) and explicitly to
     * [receiverPackage] (for manifest receivers, which do not get implicit broadcasts).
     */
    fun send(context: Context, ready: Boolean, receiverPackage: String?) {
        val intent = Intent(ACTION_AUDIO_PATH).putExtra(EXTRA_READY, ready)
        context.sendBroadcast(intent)
        if (!receiverPackage.isNullOrBlank()) context.sendBroadcast(Intent(intent).setPackage(receiverPackage))
    }
}
