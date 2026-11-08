package com.phoenix.hvacbar.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val MinTouch = 64.dp
private val ButtonShape = RoundedCornerShape(10.dp)

private fun Lamp.contentColor(accent: Color): Color = when (this) {
    Lamp.ON -> accent
    Lamp.OFF -> BarColors.text
    Lamp.UNKNOWN -> BarColors.textUnknown
}

/** Rounded touch target that lights up in [accent] when [lamp] is ON. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BarButton(
    lamp: Lamp,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = BarColors.lit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable (color: Color) -> Unit,
) {
    val lit = lamp == Lamp.ON
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = MinTouch, minHeight = MinTouch)
            .fillMaxHeight()
            .clip(ButtonShape)
            .background(if (lit) accent.copy(alpha = 0.22f) else BarColors.surface)
            .border(if (lit) 2.dp else 1.dp, if (lit) accent else BarColors.outline, ButtonShape)
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        content(lamp.contentColor(accent))
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
                Text(caption, color = BarColors.textUnknown, fontSize = 11.sp, lineHeight = 12.sp)
            }
            Text(
                label,
                color = color,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
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

/** Seat heater or cooler: icon plus one dot per level. */
@Composable
internal fun SeatButton(
    seat: SeatUi,
    heat: Boolean,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (heat) BarColors.heat else BarColors.cool
    BarButton(lamp = seat.lamp, enabled = enabled, onClick = onClick, modifier = modifier, accent = accent) { color ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (heat) Icons.Filled.Whatshot else Icons.Filled.AcUnit,
                contentDescription = description,
                tint = color,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.height(4.dp))
            LevelDots(seat.level, accent)
        }
    }
}

@Composable
private fun LevelDots(level: Int?, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(BarUiState.SEAT_LEVELS) { i ->
            val on = level != null && i < level
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (on) accent else if (level == null) BarColors.fanOff else BarColors.outline),
            )
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
                color = if (known) BarColors.text else BarColors.textUnknown,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (temp.unit != null) {
                Text(temp.unit, color = BarColors.textUnknown, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
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
            Text(BarUiState.AUTO_TEXT, color = BarColors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        } else {
            FanGraph(segments, Modifier.fillMaxSize())
            if (segments == null) {
                Text(BarUiState.UNKNOWN_TEXT, color = BarColors.textUnknown, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    StepButton(Icons.Filled.Add, "fan up", enabled, onUp, Modifier.weight(weight))
}

private const val AUTO_FAN_ALPHA = 0.25f

@Composable
private fun FanGraph(segments: Int?, modifier: Modifier) {
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
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (on) BarColors.fan else BarColors.fanOff),
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
