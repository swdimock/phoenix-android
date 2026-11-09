package com.phoenix.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.phoenix.launcher.mode.LauncherMode
import com.phoenix.uitheme.PhoenixShapes
import com.phoenix.uitheme.PhoenixTheme

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

/**
 * Active = accent fill (brushed silver for Tuxedo Black) + dark content; inactive = flat graphite, thin dim outline,
 * light-grey content; pressed = brighter accent fill.
 */
@Composable
private fun ModeButton(mode: LauncherMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val palette = PhoenixTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val filled = selected || pressed
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = PhoenixShapes.Large,
        color = if (filled) Color.Transparent else palette.inactiveFill,
        contentColor = if (filled) palette.onAccent else palette.inactiveText,
        border = if (filled) null else BorderStroke(1.dp, palette.inactiveOutline),
        interactionSource = interaction,
    ) {
        Column(
            Modifier.fillMaxSize().then(
                when {
                    pressed -> Modifier.background(palette.accentPressed)
                    selected -> Modifier.background(palette.activeFill)
                    else -> Modifier
                },
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(mode.icon, contentDescription = null, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(8.dp))
            Text(mode.label.uppercase(), style = MaterialTheme.typography.labelLarge)
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
