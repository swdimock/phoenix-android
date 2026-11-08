package com.phoenix.launcher.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.phoenix.launcher.R
import com.phoenix.launcher.launcherApp
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the launcher process alive while the audio-path state machine runs and
 * mirrors its state in a low-priority notification. The machine itself is owned by the application so the
 * UI and system receivers share it; every start of this service kicks it.
 */
class AudioPathService : LifecycleService() {

    private val notifications by lazy { getSystemService(NotificationManager::class.java) }

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.audio_channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = getString(R.string.audio_channel_description) },
        )
        val machine = launcherApp.audioPath
        startForeground(NOTIFICATION_ID, notification(machine.state.value))
        lifecycleScope.launch {
            machine.state.collect { notifications.notify(NOTIFICATION_ID, notification(it)) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        launcherApp.audioPath.start()
        return START_STICKY
    }

    private fun notification(state: AudioPathState): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_audio)
            .setContentTitle("Phoenix audio path")
            .setContentText(state.label)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()

    companion object {
        private const val TAG = "AudioPathService"
        private const val CHANNEL_ID = "audio_path"
        private const val NOTIFICATION_ID = 1

        /** Starts (or re-kicks) the service. Returns false if Android refuses, e.g. from the background. */
        fun start(context: Context): Boolean = try {
            context.startForegroundService(Intent(context, AudioPathService::class.java))
            true
        } catch (e: Exception) {
            Log.w(TAG, "cannot start foreground service: $e")
            false
        }
    }
}

/** Human-readable status, shown in the status chip and notification. */
val AudioPathState.label: String
    get() = when (this) {
        AudioPathState.WAITING -> "Audio: waiting for vehicle\u2026"
        AudioPathState.SELECTING -> "Audio: selecting source\u2026"
        AudioPathState.READY -> "Audio: ready"
        AudioPathState.FAILED -> "Audio: no vehicle signal, proceeding"
    }
