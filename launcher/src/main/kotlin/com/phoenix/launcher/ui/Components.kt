package com.phoenix.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phoenix.uitheme.PhoenixShapes
import com.phoenix.uitheme.PhoenixTheme

/** Chamfered status label with a status-coloured lamp. */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = PhoenixShapes.Chip,
        color = PhoenixTheme.palette.surface,
        contentColor = color,
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

/** Large primary action button. */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    StateButton(active = true, enabled = enabled, onClick = onClick, shape = PhoenixShapes.Medium,
        modifier = modifier.heightIn(min = LauncherLayout.TouchTarget)) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** Large toggle used for single-choice settings. */
@Composable
fun ChoiceButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    StateButton(active = selected, enabled = true, onClick = onClick, shape = PhoenixShapes.Small,
        modifier = modifier.heightIn(min = 72.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

/**
 * Active = accent fill (brushed silver for Tuxedo Black) + dark text; inactive = flat graphite, thin dim outline,
 * light-grey text; pressed = brighter accent fill; disabled = graphite with dimmed text.
 */
@Composable
private fun StateButton(
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val palette = PhoenixTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val filled = enabled && (active || pressed)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = shape,
        color = if (filled) Color.Transparent else palette.inactiveFill,
        contentColor = when {
            !enabled -> palette.textUnknown
            filled -> palette.onAccent
            else -> palette.inactiveText
        },
        border = if (filled) null else BorderStroke(1.dp, palette.inactiveOutline),
        interactionSource = interaction,
    ) {
        Box(
            Modifier
                .then(
                    when {
                        !filled -> Modifier
                        pressed -> Modifier.background(palette.accentPressed)
                        else -> Modifier.background(palette.activeFill)
                    },
                )
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** Card with a title, body text and optional actions. */
@Composable
fun InfoCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val palette = PhoenixTheme.palette
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PhoenixShapes.Large,
        color = palette.surface,
        border = BorderStroke(1.dp, palette.outline),
    ) {
        Column(
            Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = palette.text)
            Box(Modifier.size(width = 72.dp, height = 4.dp).background(palette.accent))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = palette.textMuted)
            actions()
        }
    }
}
