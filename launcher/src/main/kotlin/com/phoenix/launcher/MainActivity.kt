package com.phoenix.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import com.phoenix.fytipc.ModuleCommand
import com.phoenix.launcher.audio.AudioPathService
import com.phoenix.launcher.host.AaLaunchExtras
import com.phoenix.launcher.host.AaHost
import com.phoenix.launcher.host.ExternalApp
import com.phoenix.launcher.host.PackageAaHost
import com.phoenix.launcher.host.VendorApp
import com.phoenix.launcher.mode.LauncherMode
import com.phoenix.launcher.mode.LauncherViewModel
import com.phoenix.launcher.settings.AaTransport
import com.phoenix.launcher.ui.AaPane
import com.phoenix.launcher.ui.DiagnosticsSection
import com.phoenix.launcher.ui.DiagnosticsUi
import com.phoenix.launcher.ui.LauncherScreen
import com.phoenix.launcher.ui.SettingsActions
import com.phoenix.launcher.ui.SettingsScreen
import com.phoenix.launcher.ui.VendorAppPane
import com.phoenix.launcher.ui.theme.LauncherTheme
import com.phoenix.launcher.ui.theme.ThemeRepository
import com.phoenix.uitheme.AccentPreset
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * HOME activity. Shows the mode switcher and hands AA / Radio / Phone to external apps. On a fresh start
 * (boot, or relaunch after the vendor service force-stopped us) it immediately opens the default mode.
 */
class MainActivity : ComponentActivity() {

    private val app get() = launcherApp
    private val viewModel: LauncherViewModel by viewModels { LauncherViewModel.factory(launcherApp.settingsRepository) }

    private val aaHost: AaHost by lazy {
        PackageAaHost(this, { app.settings.value }, { app.audioPath.state.value.audioReady })
    }
    private val radio: ExternalApp by lazy { VendorApp(this, RADIO_PACKAGE, VENDOR_PREFIX, listOf("Radio")) }
    private val phone: ExternalApp by lazy { VendorApp(this, PHONE_PACKAGE, VENDOR_PREFIX, listOf("Phone", "Bluetooth")) }
    private val themeRepository by lazy { ThemeRepository(this) }

    /** Bumped on every resume so panes re-check which apps are installed. */
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AudioPathService.start(this)

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val bootMode = viewModel.mode.filterNotNull().first()
                // AA waits for the audio path (ready or timed out) below; other modes open right away.
                if (bootMode == LauncherMode.AA) app.aaAutoLaunch.arm() else enter(bootMode)
            }
        }
        lifecycleScope.launch {
            combine(viewModel.mode.filterNotNull(), app.audioPath.state) { mode, audio -> mode to audio }
                .collect { (mode, audio) ->
                    if (app.aaAutoLaunch.shouldLaunch(mode == LauncherMode.AA, audio.audioReady, aaHost.isInstalled())) {
                        enter(LauncherMode.AA)
                    }
                }
        }

        lifecycleScope.launch { themeRepository.preset.collect(themeRepository::broadcast) }

        setContent {
            val preset by themeRepository.preset.collectAsState(initial = AccentPreset.DEFAULT)
            LauncherTheme(preset) {
                val mode by viewModel.mode.collectAsState()
                val audio by app.audioPath.state.collectAsState()
                val connection by app.toolkit.connectionState.collectAsState()
                LauncherScreen(mode, audio, connection, onSelectMode = ::select) { Pane(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    private fun select(mode: LauncherMode) {
        viewModel.selectMode(mode)
        enter(mode)
    }

    /** Side effects of entering [mode]: audio source commands and launching the external app. */
    private fun enter(mode: LauncherMode) {
        val profile = app.settings.value.vehicleProfile
        when (mode) {
            LauncherMode.AA -> {
                if (app.audioPath.state.value.audioReady) sendInBackground(profile.audioSourceSelectCommand, "enter AA")
                aaHost.launch()
            }
            LauncherMode.RADIO -> {
                sendInBackground(profile.audioSourceRadioCommand, "enter Radio")
                radio.launch()
            }
            LauncherMode.PHONE -> phone.launch()
            LauncherMode.SETTINGS -> Unit
        }
    }

    private fun sendInBackground(command: ModuleCommand?, note: String) {
        command ?: return
        app.appScope.launch { app.send(command, note) }
    }

    @Composable
    private fun Pane(mode: LauncherMode) {
        val tick = resumeTick.intValue
        when (mode) {
            LauncherMode.AA -> {
                val settings by app.settings.collectAsState()
                val installed = remember(tick, settings.aaReceiverPackage) { aaHost.isInstalled() }
                AaPane(
                    installed, settings.aaReceiverPackage, AaLaunchExtras.describe(settings),
                    { aaHost.launch() }, { select(LauncherMode.RADIO) },
                )
            }
            LauncherMode.RADIO -> VendorAppPane(
                title = "Radio",
                installed = remember(tick) { radio.resolve() != null },
                openLabel = "Open Radio",
                onOpen = { radio.launch(); resumeTick.intValue++ },
                missingNote = "Looked for $RADIO_PACKAGE and any $VENDOR_PREFIX* app labelled \"Radio\". " +
                    "This is expected on the emulator. The OEM radio is still available from the truck's own controls.",
            )
            LauncherMode.PHONE -> VendorAppPane(
                title = "Phone",
                installed = remember(tick) { phone.resolve() != null },
                openLabel = "Open Phone",
                onOpen = { phone.launch(); resumeTick.intValue++ },
                missingNote = "Looked for $PHONE_PACKAGE and any $VENDOR_PREFIX* app labelled \"Phone\" or \"Bluetooth\". " +
                    "This is expected on the emulator. Phone calls also work through Android Auto.",
            )
            LauncherMode.SETTINGS -> SettingsPane()
        }
    }

    @Composable
    private fun SettingsPane() {
        val settings by app.settings.collectAsState()
        SettingsScreen(settings, settingsActions, app.updater) {
            val ui = DiagnosticsUi(
                connection = app.toolkit.connectionState.collectAsState().value,
                audioPath = app.audioPath.state.collectAsState().value,
                mockAudioPathReady = app.diagnostics.mockAudioPathReady.collectAsState().value,
                mockAudible = app.diagnostics.mockAudible.collectAsState().value,
                recentUpdates = app.diagnostics.recentUpdates.collectAsState().value,
                recordingFile = app.recorder.recording.collectAsState().value,
            )
            DiagnosticsSection(ui) {
                if (app.recorder.recording.value == null) app.recorder.start() else app.recorder.stop()
            }
        }
    }

    private val settingsActions = object : SettingsActions {
        override fun setDefaultMode(mode: LauncherMode) = viewModel.setDefaultMode(mode)
        override fun setVehicleProfile(id: String) = viewModel.setVehicleProfile(id)
        override fun setAudioReadyTimeout(ms: Long) = viewModel.setAudioReadyTimeout(ms)
        override fun setAaReceiverPackage(pkg: String) = viewModel.setAaReceiverPackage(pkg)
        override fun setAaTransport(transport: AaTransport) = viewModel.setAaTransport(transport)
        override fun setAaTcpEndpoint(host: String, port: Int) = viewModel.setAaTcpEndpoint(host, port)
        override fun openAndroidSettings() {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    private companion object {
        const val RADIO_PACKAGE = "com.syu.radio"
        const val PHONE_PACKAGE = "com.syu.bt"
        const val VENDOR_PREFIX = "com.syu."
    }
}
