package com.phoenix.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.phoenix.fytipc.ConnectionState
import com.phoenix.launcher.audio.AudioPathState
import com.phoenix.launcher.audio.label
import com.phoenix.launcher.mode.LauncherMode

/**
 * Root layout: status row, optional vehicle-service banner, mode switcher and the active pane. The bottom
 * [LauncherLayout.HvacBarInset] is left empty for the HVAC bar overlay.
 */
@Composable
fun LauncherScreen(
    mode: LauncherMode?,
    audioPath: AudioPathState,
    connection: ConnectionState,
    onSelectMode: (LauncherMode) -> Unit,
    pane: @Composable (LauncherMode) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(bottom = LauncherLayout.HvacBarInset)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        StatusRow(audioPath, connection)
        if (connection == ConnectionState.UNAVAILABLE) UnavailableBanner()
        ModeSwitcher(mode, onSelectMode)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (mode != null) pane(mode)
        }
    }
}

@Composable
private fun StatusRow(audioPath: AudioPathState, connection: ConnectionState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Phoenix", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.weight(1f))
        StatusChip(connection.label, connection.color)
        Spacer(Modifier.padding(start = 12.dp))
        StatusChip(audioPath.label, audioPath.color)
    }
}

@Composable
private fun UnavailableBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            "Vehicle service (com.syu.ms) not found. Climate, audio source selection and diagnostics are unavailable; " +
                "Android Auto still works.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(20.dp),
        )
    }
}

private val ConnectionState.label: String
    get() = when (this) {
        ConnectionState.CONNECTED -> "Vehicle: connected"
        ConnectionState.CONNECTING -> "Vehicle: connecting\u2026"
        ConnectionState.DISCONNECTED -> "Vehicle: disconnected"
        ConnectionState.UNAVAILABLE -> "Vehicle: unavailable"
    }

private val ConnectionState.color: Color
    get() = when (this) {
        ConnectionState.CONNECTED -> Color(0xFF69F0AE)
        ConnectionState.CONNECTING, ConnectionState.DISCONNECTED -> Color(0xFFFFD54F)
        ConnectionState.UNAVAILABLE -> Color(0xFFFF6E6E)
    }

private val AudioPathState.color: Color
    get() = when (this) {
        AudioPathState.WAITING, AudioPathState.SELECTING -> Color(0xFFFFD54F)
        AudioPathState.READY -> Color(0xFF69F0AE)
        AudioPathState.FAILED -> Color(0xFFFFAB40)
    }
