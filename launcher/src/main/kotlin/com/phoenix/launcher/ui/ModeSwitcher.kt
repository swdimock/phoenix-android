package com.phoenix.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.phoenix.launcher.mode.LauncherMode

/** Persistent row of four large mode buttons. */
@Composable
fun ModeSwitcher(selected: LauncherMode?, onSelect: (LauncherMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(128.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LauncherMode.entries.forEach { mode ->
            ModeButton(mode, mode == selected, { onSelect(mode) }, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun ModeButton(mode: LauncherMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) colors.primary else colors.surfaceVariant,
        contentColor = if (selected) colors.onPrimary else colors.onSurface,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(mode.icon, contentDescription = null, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(8.dp))
            Text(mode.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private val LauncherMode.icon: ImageVector
    get() = when (this) {
        LauncherMode.AA -> Icons.Filled.DirectionsCar
        LauncherMode.RADIO -> Icons.Filled.Radio
        LauncherMode.PHONE -> Icons.Filled.Phone
        LauncherMode.SETTINGS -> Icons.Filled.Settings
    }
