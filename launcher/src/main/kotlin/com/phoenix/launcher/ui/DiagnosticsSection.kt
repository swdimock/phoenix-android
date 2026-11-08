package com.phoenix.launcher.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.phoenix.fytipc.ConnectionState
import com.phoenix.fytipc.MockSyuDebug
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.Modules
import com.phoenix.launcher.audio.AudioPathState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Snapshot of everything the diagnostics section shows. */
data class DiagnosticsUi(
    val connection: ConnectionState,
    val audioPath: AudioPathState,
    val mockAudioPathReady: Int?,
    val mockAudible: Int?,
    val recentUpdates: List<ModuleUpdate>,
    val recordingFile: File?,
)

/** Vehicle IPC diagnostics and capture recording. */
@Composable
fun DiagnosticsSection(ui: DiagnosticsUi, onToggleRecording: () -> Unit) {
    Section("Diagnostics") {
        Hint("Vehicle service: ${ui.connection}")
        Hint("Audio path: ${ui.audioPath}")
        Hint(
            "Mock debug (module ${MockSyuDebug.MODULE}): " +
                "audioPathReady[${MockSyuDebug.AUDIO_PATH_READY}]=${ui.mockAudioPathReady ?: "-"}  " +
                "audible[${MockSyuDebug.AUDIBLE}]=${ui.mockAudible ?: "-"}",
        )
        BigButton(
            text = if (ui.recordingFile == null) "Start recording" else "Stop recording",
            onClick = onToggleRecording,
            modifier = Modifier.fillMaxWidth(),
        )
        ui.recordingFile?.let { Hint("Writing ${it.absolutePath}") }
        Text("Last ${ui.recentUpdates.size} updates", style = MaterialTheme.typography.titleMedium)
        if (ui.recentUpdates.isEmpty()) Hint("No updates received yet.")
        ui.recentUpdates.forEach { Text(it.describe(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium) }
    }
}

private val TimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

private fun ModuleUpdate.describe(): String = buildString {
    append(TimeFormat.format(Date(timestampMs))).append("  ")
    append(Modules.name(module)).append('/').append(code)
    ints?.let { append(" i=").append(it.joinToString(",", "[", "]")) }
    floats?.let { append(" f=").append(it.joinToString(",", "[", "]")) }
    strings?.let { append(" s=").append(it.joinToString(",", "[", "]")) }
}
