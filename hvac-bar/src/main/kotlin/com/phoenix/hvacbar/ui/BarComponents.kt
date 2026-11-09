package com.phoenix.hvacbar.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoenix.uitheme.DisplayFont
import com.phoenix.uitheme.PhoenixShapes
import com.phoenix.uitheme.PhoenixTheme

internal val MinTouch = 64.dp
private val ButtonShape = PhoenixShapes.chamfer(10.dp, 3.dp)
private val SegmentShape = CutCornerShape(topEnd = 5.dp)
private val DotShape = CutCornerShape(2.dp)

/**
 * Chamfered touch target with three unmistakable states: ON = accent fill (brushed silver for Tuxedo Black) with
 * dark content; OFF / UNKNOWN = flat graphite, thin dim outline, light-grey content; pressed = brighter accent fill.
 * [accent] / [onAccent] override the theme accent with a flat colour (seat heat / cool).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BarButton(
    lamp: Lamp,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onAccent: Color = PhoenixTheme.palette.onAccent,
    onLongClick: (() -> Unit)? = null,
    content: @Composable (color: Color) -> Unit,
) {
    val palette = PhoenixTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lit = lamp == Lamp.ON
    val fill: Brush = when {
        pressed -> SolidColor(if (accent == null) palette.accentPressed else lerp(accent, Color.White, 0.3f))
        lit -> if (accent == null) palette.activeFill else SolidColor(accent)
        else -> SolidColor(palette.inactiveFill)
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = MinTouch, minHeight = MinTouch)
            .fillMaxHeight()
            .clip(ButtonShape)
            .background(fill)
            .then(if (lit || pressed) Modifier else Modifier.border(1.dp, palette.inactiveOutline, ButtonShape))
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content(
            when {
                lit || pressed -> onAccent
                lamp == Lamp.UNKNOWN -> palette.textUnknown
                else -> palette.inactiveText
            },
        )
    }
}

/** Text-labelled toggle, optionally with a small caption above the label. */
@Composable
internal fun LabelButton(
    label: String,
    lamp: Lamp,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    fontSize: Int = 22,
) {
    BarButton(lamp = lamp, enabled = enabled, onClick = onClick, modifier = modifier) { color ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (caption != null) {
                Text(
                    caption,
                    color = if (lamp == Lamp.ON) color.copy(alpha = 0.75f) else PhoenixTheme.palette.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    letterSpacing = 1.sp,
                )
            }
            Text(
                label,
                color = color,
                fontFamily = DisplayFont,
                fontSize = fontSize.sp,
                letterSpacing = 1.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Icon-only toggle. */
@Composable
internal fun IconToggle(
    icon: ImageVector,
    description: String,
    lamp: Lamp,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BarButton(lamp = lamp, enabled = enabled, onClick = onClick, modifier = modifier) { color ->
        Icon(icon, contentDescription = description, tint = color, modifier = Modifier.size(36.dp))
    }
}

/** Seat heater or cooler: icon plus one pip per level. Lit in the heat / cool colour rather than the accent. */
@Composable
internal fun SeatButton(
    seat: SeatUi,
    heat: Boolean,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = PhoenixTheme.palette
    val accent = if (heat) palette.heat else palette.cool
    BarButton(
        lamp = seat.lamp,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        accent = accent,
        onAccent = palette.background,
    ) { color ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (heat) Icons.Filled.Whatshot else Icons.Filled.AcUnit,
                contentDescription = description,
                tint = color,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.height(4.dp))
            LevelDots(seat.level, lit = seat.lamp == Lamp.ON, accent = accent, onAccent = color)
        }
    }
}

@Composable
private fun LevelDots(level: Int?, lit: Boolean, accent: Color, onAccent: Color) {
    val palette = PhoenixTheme.palette
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(BarUiState.SEAT_LEVELS) { i ->
            val on = level != null && i < level
            val color = when {
                lit -> if (on) onAccent else onAccent.copy(alpha = 0.3f)
                on -> accent
                level == null -> palette.unlit
                else -> palette.outline
            }
            Box(Modifier.size(width = 12.dp, height = 7.dp).background(color, DotShape))
        }
    }
}

/** Minus / value / plus group for one temperature zone. Long-press on the value toggles F/C. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RowScope.TempControl(
    temp: TempText,
    enabled: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    onToggleUnits: () -> Unit,
    weight: Float,
) {
    val palette = PhoenixTheme.palette
    StepButton(Icons.Filled.Remove, "temperature down", enabled, onDown, Modifier.weight(weight))
    Box(
        modifier = Modifier
            .weight(weight * 1.4f)
            .fillMaxHeight()
            .combinedClickable(onClick = {}, onLongClick = onToggleUnits),
        contentAlignment = Alignment.Center,
    ) {
        val known = temp.value != BarUiState.UNKNOWN_TEXT
        Row(verticalAlignment = Alignment.Top) {
            Text(
                temp.value,
                color = if (known) palette.text else palette.textUnknown,
                fontFamily = DisplayFont,
                fontSize = 38.sp,
                maxLines = 1,
            )
            if (temp.unit != null) {
                Text(temp.unit, color = palette.accent, fontFamily = DisplayFont, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    StepButton(Icons.Filled.Add, "temperature up", enabled, onUp, Modifier.weight(weight))
}

/**
 * Minus / segmented bar graph / plus group for the blower. Under AUTO control every segment is
 * drawn dimmed behind an AUTO label; an unknown speed shows dark segments behind "--".
 */
@Composable
internal fun RowScope.FanControl(
    segments: Int?,
    auto: Boolean,
    enabled: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    weight: Float,
) {
    val palette = PhoenixTheme.palette
    StepButton(Icons.Filled.Remove, "fan down", enabled, onDown, Modifier.weight(weight))
    Box(
        modifier = Modifier
            .weight(weight * 2.6f)
            .fillMaxHeight()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (auto) {
            FanGraph(BarUiState.FAN_SEGMENTS, Modifier.fillMaxSize().alpha(AUTO_FAN_ALPHA))
            Text(BarUiState.AUTO_TEXT, color = palette.text, fontFamily = DisplayFont, fontSize = 28.sp, letterSpacing = 2.sp)
        } else {
            FanGraph(segments, Modifier.fillMaxSize())
            if (segments == null) {
                Text(BarUiState.UNKNOWN_TEXT, color = palette.textUnknown, fontFamily = DisplayFont, fontSize = 28.sp)
            }
        }
    }
    StepButton(Icons.Filled.Add, "fan up", enabled, onUp, Modifier.weight(weight))
}

private const val AUTO_FAN_ALPHA = 0.25f

@Composable
private fun FanGraph(segments: Int?, modifier: Modifier) {
    val palette = PhoenixTheme.palette
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(BarUiState.FAN_SEGMENTS) { i ->
            val on = segments != null && i < segments
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(0.35f + 0.65f * (i + 1) / BarUiState.FAN_SEGMENTS)
                    .background(if (on) palette.accent else palette.unlit, SegmentShape),
            )
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    BarButton(lamp = Lamp.OFF, enabled = enabled, onClick = onClick, modifier = modifier.fillMaxWidth()) { color ->
        Icon(icon, contentDescription = description, tint = color, modifier = Modifier.width(40.dp).height(40.dp))
    }
}
