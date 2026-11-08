package app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.phoenix.mocksyums.MockRuntime
import com.phoenix.mocksyums.R

/**
 * Emulator stand-in for the vendor `com.syu.ms/app.ToolkitService` (same package, component name,
 * action and Binder protocol). Bound clients get the raw `IRemoteToolkit` binder; when started
 * (debug screen, boot) it also runs in the foreground so the emulator does not reap it.
 */
class ToolkitService : Service() {

    private val runtime by lazy { MockRuntime.get(this) }

    override fun onCreate() {
        super.onCreate()
        runtime // build modules and start the simulation
    }

    override fun onBind(intent: Intent?): IBinder = runtime.toolkitBinder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Mock SYU service", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Mock com.syu.ms running")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, n)
        return START_STICKY
    }

    private companion object {
        const val CHANNEL = "mock-syu"
        const val NOTIFICATION_ID = 1
    }
}
