package com.phoenix.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoenix.fytipc.profile.VehicleProfiles
import com.phoenix.launcher.BuildConfig
import com.phoenix.launcher.mode.LauncherMode
import com.phoenix.launcher.settings.AaTransport
import com.phoenix.launcher.settings.LauncherSettings
import com.phoenix.launcher.update.SelfUpdater
import com.phoenix.launcher.update.UpdateCheck
import kotlinx.coroutines.launch

/** Callbacks from [SettingsScreen] into the view model / activity. */
interface SettingsActions {
    fun setDefaultMode(mode: LauncherMode)
    fun setVehicleProfile(id: String)
    fun setAudioReadyTimeout(ms: Long)
    fun setAaReceiverPackage(pkg: String)
    fun setAaTransport(transport: AaTransport)
    fun setAaTcpEndpoint(host: String, port: Int)
    fun openAndroidSettings()
}

private val TimeoutChoicesMs = listOf(5_000L, 10_000L, 15_000L, 30_000L, 60_000L)

/** The launcher's own settings pane, with diagnostics at the bottom. */
@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    actions: SettingsActions,
    updater: SelfUpdater,
    diagnostics: @Composable () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }

        item {
            Section("Start in") {
                ChoiceRow(LauncherMode.entries, settings.defaultMode, { it.label }, actions::setDefaultMode)
            }
        }
        item { ThemeSettingsSection() }
        item {
            Section("Vehicle profile") {
                VehicleProfiles.all.forEach { profile ->
                    ChoiceButton(
                        text = profile.displayName,
                        selected = profile.id == settings.vehicleProfileId,
                        onClick = { actions.setVehicleProfile(profile.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            Section("Audio-ready timeout") {
                ChoiceRow(TimeoutChoicesMs, settings.audioReadyTimeoutMs, { "${it / 1000} s" }, actions::setAudioReadyTimeout)
                Hint("How long to wait for the vehicle before selecting the audio source anyway. Applies on next wake.")
            }
        }
        item { AaPackageSection(settings.aaReceiverPackage, actions::setAaReceiverPackage) }
        item { AaTransportSection(settings, actions) }
        item {
            Section("System") {
                BigButton("Open Android Settings", actions::openAndroidSettings, Modifier.fillMaxWidth())
                UpdateButton(updater)
            }
        }
        item {
            Section("About") {
                Hint("Phoenix Launcher ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
                Hint("${BuildConfig.APPLICATION_ID} - profile: ${settings.vehicleProfile.displayName}")
            }
        }
        item { HorizontalDivider() }
        item { diagnostics() }
    }
}

@Composable
private fun AaPackageSection(current: String, onSave: (String) -> Unit) {
    var text by remember(current) { mutableStateOf(current) }
    Section("Android Auto receiver package") {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(fontSize = 22.sp),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton("Save", { onSave(text) }, Modifier.weight(1f), enabled = text.trim() != current)
            BigButton("Default", { onSave(LauncherSettings.DEFAULT_AA_RECEIVER_PACKAGE) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AaTransportSection(settings: LauncherSettings, actions: SettingsActions) {
    var host by remember(settings.aaTcpHost) { mutableStateOf(settings.aaTcpHost) }
    var port by remember(settings.aaTcpPort) { mutableStateOf(settings.aaTcpPort.toString()) }
    Section("Android Auto transport") {
        ChoiceRow(AaTransport.entries, settings.aaTransport, { it.label }, actions::setAaTransport)
        if (settings.aaTransport == AaTransport.TCP) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = host, onValueChange = { host = it }, singleLine = true, label = { Text("Host") },
                    textStyle = TextStyle(fontSize = 22.sp), modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) }, singleLine = true,
                    label = { Text("Port") }, textStyle = TextStyle(fontSize = 22.sp), modifier = Modifier.weight(1f),
                )
            }
            val changed = host.trim() != settings.aaTcpHost || port != settings.aaTcpPort.toString()
            BigButton("Save", { actions.setAaTcpEndpoint(host, port.toIntOrNull() ?: 0) }, Modifier.fillMaxWidth(), enabled = changed)
        }
        Hint("TCP uses the phone's Android Auto \"head unit server\" through adb reverse + forward (emulator: 127.0.0.1:5277). USB is for the head unit. Applies on next Open Android Auto.")
    }
}

@Composable
private fun UpdateButton(updater: SelfUpdater) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    BigButton(
        text = if (updater.isEnabled) "Check for updates" else "Updates disabled (no repository configured)",
        enabled = updater.isEnabled && !busy,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            busy = true
            status = "Checking\u2026"
            scope.launch {
                status = when (val result = updater.check()) {
                    UpdateCheck.Disabled -> "Updates disabled"
                    is UpdateCheck.UpToDate -> "Up to date (latest ${result.latestTag})"
                    is UpdateCheck.Error -> "Update check failed: ${result.message}"
                    is UpdateCheck.Available -> {
                        status = "Downloading ${result.tag}\u2026"
                        updater.downloadAndInstall(result)?.let { "Download failed: $it" } ?: "Installer opened for ${result.tag}"
                    }
                }
                busy = false
            }
        },
    )
    status?.let { Hint(it) }
}

@Composable
private fun <T> ChoiceRow(choices: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        choices.forEach { choice ->
            ChoiceButton(label(choice), choice == selected, { onSelect(choice) }, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
