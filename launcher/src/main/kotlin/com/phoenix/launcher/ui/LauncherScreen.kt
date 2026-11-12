package com.phoenix.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.phoenix.uitheme.PhoenixPalette
import com.phoenix.uitheme.PhoenixShapes
import com.phoenix.uitheme.PhoenixTheme

/**
 * Root layout: header (wordmark + status chips), optional vehicle-service banner, mode switcher and the active
 * pane. The bottom [LauncherLayout.HvacBarInset] is left empty for the HVAC bar overlay.
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
            .padding(bottom = LauncherLayout.HvacBarInset),
    ) {
        Header(audioPath, connection)
        Column(
            Modifier.weight(1f).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (connection == ConnectionState.UNAVAILABLE) UnavailableBanner()
            ModeSwitcher(mode, onSelectMode)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (mode != null) pane(mode)
            }
        }
    }
}

@Composable
private fun Header(audioPath: AudioPathState, connection: ConnectionState) {
    val palette = PhoenixTheme.palette
    Column(Modifier.fillMaxWidth().background(palette.headerBand)) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HennesseyBadge()
            Spacer(Modifier.weight(1f))
            StatusChip(connection.label, connection.color(palette))
            Spacer(Modifier.width(12.dp))
            StatusChip(audioPath.label, audioPath.color(palette))
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(palette.accent))
    }
}

@Composable
private fun UnavailableBanner() {
    val palette = PhoenixTheme.palette
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PhoenixShapes.Medium,
        color = palette.errorContainer,
        contentColor = palette.text,
        border = BorderStroke(1.dp, palette.error),
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

private fun ConnectionState.color(palette: PhoenixPalette): Color = when (this) {
    ConnectionState.CONNECTED -> palette.success
    ConnectionState.CONNECTING, ConnectionState.DISCONNECTED -> palette.warning
    ConnectionState.UNAVAILABLE -> palette.error
}

private fun AudioPathState.color(palette: PhoenixPalette): Color = when (this) {
    AudioPathState.WAITING, AudioPathState.SELECTING -> palette.warning
    AudioPathState.READY -> palette.success
    AudioPathState.FAILED -> AudioFailedColor
}

private val AudioFailedColor = Color(0xFFFFAB40)
