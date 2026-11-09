package com.phoenix.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.phoenix.fytipc.ConnectionState
import com.phoenix.fytipc.FytToolkit
import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.Modules
import com.phoenix.launcher.host.AaAutoLaunchGate
import com.phoenix.launcher.audio.AudioPathBroadcasts
import com.phoenix.launcher.audio.AudioPathConfig
import com.phoenix.launcher.audio.AudioPathService
import com.phoenix.launcher.audio.AudioPathStateMachine
import com.phoenix.launcher.settings.DataStoreSettingsRepository
import com.phoenix.launcher.settings.LauncherSettings
import com.phoenix.launcher.settings.SettingsRepository
import com.phoenix.launcher.update.GithubReleasesUpdater
import com.phoenix.launcher.update.SelfUpdater
import com.phoenix.launcher.vehicle.CaptureRecorder
import com.phoenix.launcher.vehicle.UnavailableToolkit
import com.phoenix.launcher.vehicle.VehicleDiagnostics
import com.phoenix.launcher.vehicle.trySend
import com.phoenix.launcher.vehicle.updatesOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Process-wide object graph. The toolkit connection, settings and the audio-path state machine are created
 * once here and shared by the activity, the foreground service and the system-event receiver, so they are
 * rebuilt correctly after the vendor service force-stops us.
 */
class PhoenixLauncherApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** One automatic AA receiver launch per process; see [AaAutoLaunchGate]. */
    val aaAutoLaunch = AaAutoLaunchGate()

    lateinit var toolkit: FytToolkit
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var settings: StateFlow<LauncherSettings>
        private set
    lateinit var audioPath: AudioPathStateMachine
        private set
    lateinit var diagnostics: VehicleDiagnostics
        private set
    lateinit var recorder: CaptureRecorder
        private set
    lateinit var updater: SelfUpdater
        private set

    override fun onCreate() {
        super.onCreate()
        toolkit = try {
            FytToolkit.connect(this)
        } catch (t: Throwable) {
            Log.e(TAG, "FytToolkit.connect failed; running without vehicle IPC", t)
            UnavailableToolkit
        }
        settingsRepository = DataStoreSettingsRepository.create(this)
        settings = settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, LauncherSettings.platformDefaults())
        recorder = CaptureRecorder(appScope, toolkit, { getExternalFilesDir("captures") }, { settings.value.vehicleProfile }, ::logW)
        diagnostics = VehicleDiagnostics(appScope, toolkit, ::logW)
        updater = GithubReleasesUpdater(this, BuildConfig.UPDATE_OWNER, BuildConfig.UPDATE_REPO, BuildConfig.VERSION_NAME)
        audioPath = AudioPathStateMachine(
            scope = appScope,
            config = {
                val s = settingsRepository.settings.first()
                AudioPathConfig(s.vehicleProfile, s.audioReadyTimeoutMs)
            },
            readinessUpdates = { toolkit.updatesOf(Modules.CANBUS, *it.audioReadinessCodes.toIntArray()) },
            sendCommand = { send(it, "audio source select") },
            onTransition = { AudioPathBroadcasts.send(this, it.audioReady, settings.value.aaReceiverPackage) },
            log = { Log.i(TAG, it) },
        )
        registerSystemEvents()
        restartAudioPathOnRebind()
    }

    /** Sends a vendor command, records it if a capture is running, and never throws. */
    suspend fun send(command: ModuleCommand, note: String? = null): Boolean {
        recorder.recordCommand(command, note)
        return toolkit.trySend(command) { logW("command $command failed: $it") }
    }

    private fun registerSystemEvents() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(AudioPathBroadcasts.ACTION_ACC_ON)
            addAction(AudioPathBroadcasts.ACTION_ACC_OFF)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.i(TAG, "system event ${intent.action}")
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON, AudioPathBroadcasts.ACTION_ACC_ON -> {
                        AudioPathService.start(context)
                        audioPath.start()
                    }
                    Intent.ACTION_SCREEN_OFF, AudioPathBroadcasts.ACTION_ACC_OFF -> audioPath.reset()
                }
            }
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    /** ARCHITECTURE.md: Ready -> Waiting when com.syu.ms rebinds. */
    private fun restartAudioPathOnRebind() {
        appScope.launch {
            var everConnected = false
            var lost = false
            toolkit.connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED) {
                    if (lost) {
                        Log.i(TAG, "vehicle service rebound; restarting audio path")
                        audioPath.restart()
                    }
                    everConnected = true
                    lost = false
                } else if (everConnected) {
                    lost = true
                }
            }
        }
    }

    private fun logW(message: String) {
        Log.w(TAG, message)
    }

    companion object {
        private const val TAG = "PhoenixLauncher"
    }
}

/** The process-wide [PhoenixLauncherApp]. */
val Context.launcherApp: PhoenixLauncherApp get() = applicationContext as PhoenixLauncherApp
