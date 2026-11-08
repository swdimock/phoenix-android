package com.phoenix.hvacbar.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.phoenix.hvacbar.R
import com.phoenix.hvacbar.barApp
import com.phoenix.hvacbar.ui.HvacBarRoute

/**
 * Foreground service that keeps the climate bar drawn over everything at the bottom of the screen.
 * Sticky, so the system recreates it after `com.syu.ms` force-stops us on sleep.
 */
class BarService : Service() {

    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private var overlay: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "overlay permission missing; stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        showOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlay != null) return
        val app = barApp
        val lifecycleOwner = OverlayLifecycleOwner().apply { onCreate() }
        val view = ComposeView(this).apply {
            lifecycleOwner.attachTo(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { HvacBarRoute(app.repository, app.settings) }
        }
        try {
            windowManager.addView(view, BarGeometry.overlayLayoutParams())
        } catch (e: RuntimeException) {
            Log.e(TAG, "could not add overlay window", e)
            lifecycleOwner.onDestroy()
            stopSelf()
            return
        }
        lifecycleOwner.onResume()
        overlay = view
        owner = lifecycleOwner
    }

    private fun hideOverlay() {
        overlay?.let { view ->
            runCatching { windowManager.removeView(view) }
                .onFailure { Log.w(TAG, "removeView failed", it) }
        }
        owner?.onDestroy()
        overlay = null
        owner = null
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bar_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "HvacBarService"
        private const val CHANNEL_ID = "bar"
        private const val NOTIFICATION_ID = 1

        /** Starts (or re-shows) the bar. Safe to call repeatedly and from receivers. */
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, BarService::class.java))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "foreground start not allowed right now", e)
            }
        }
    }
}
