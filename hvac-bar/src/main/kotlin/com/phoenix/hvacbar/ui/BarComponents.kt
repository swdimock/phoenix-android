package com.phoenix.hvacbar.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoenix.fytipc.climate.AirMode
import com.phoenix.hvacbar.R
import com.phoenix.uitheme.DisplayFont
import com.phoenix.uitheme.PhoenixPalette
import com.phoenix.uitheme.PhoenixShapes
import com.phoenix.uitheme.PhoenixTheme

internal val MinTouch = 64.dp

/** Toggles and steppers: the asymmetric Phoenix chamfer. */
private val ToggleShape = PhoenixShapes.chamfer(10.dp, 3.dp)

/** Navigation controls (HOME): an even chamfer on all four corners, so they read as a different kind of key. */
private val ActionShape = CutCornerShape(9.dp)
private val SegmentShape = CutCornerShape(topEnd = 5.dp)
private val DotShape = CutCornerShape(2.dp)

private val CaptionSize = 11.sp

/**
 * Raised, chamfered touch target. Three unmistakable states: ON = bold top-lit accent gradient (brushed silver for
 * Tuxedo Black) with dark content; OFF / UNKNOWN = top-lit graphite slab, thin dim outline, light-grey content;
 * pressed = brighter flat accent. A lit edge along the top and a shaded edge along the bottom give every key a
 * little depth, so the bar sits with Android Auto's flat panels without disappearing into them.
 * [accent] / [onAccent] swap the theme accent for a fixed colour (seat heat).
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
    shape: Shape = ToggleShape,
    onLongClick: (() -> Unit)? = null,
    content: @Composable (color: Color) -> Unit,
) {
    val palette = PhoenixTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lit = lamp == Lamp.ON
    val fill: Brush = when {
        pressed -> SolidColor(if (accent == null) palette.accentPressed else lerp(accent, Color.White, 0.3f))
        lit -> if (accent == null) palette.activeFill else PhoenixPalette.boldFill(accent)
        else -> palette.inactiveFillBrush
    }
    val outline = when {
        lit || pressed -> palette.bevelShadow
        else -> palette.inactiveOutline
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = MinTouch, minHeight = MinTouch)
            .fillMaxHeight()
            .clip(shape)
            .background(fill)
            .bevel(palette, gloss = lit && !pressed)
            .border(1.dp, outline, shape)
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

/**
 * Lit top edge, shaded bottom edge and (for [gloss]) a soft sheen over the upper half. Drawn over the fill,
 * under the content; the enclosing clip trims it to the chamfer.
 */
private fun Modifier.bevel(palette: PhoenixPalette, gloss: Boolean): Modifier = drawWithContent {
    if (gloss) {
        drawRect(
            Brush.verticalGradient(0f to Color.White.copy(alpha = 0.16f), 0.55f to Color.Transparent),
        )
    }
    val edge = 1.5.dp.toPx()
    drawLine(palette.bevelHighlight, Offset(0f, edge / 2), Offset(size.width, edge / 2), edge)
    drawLine(palette.bevelShadow, Offset(0f, size.height - edge / 2), Offset(size.width, size.height - edge / 2), edge)
    drawContent()
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
            if (caption != null) Caption(caption, color, lamp)
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

@Composable
private fun Caption(text: String, contentColor: Color, lamp: Lamp) {
    Text(
        text,
        color = if (lamp == Lamp.ON) contentColor.copy(alpha = 0.75f) else PhoenixTheme.palette.textMuted,
        fontSize = CaptionSize,
        lineHeight = 12.sp,
        letterSpacing = 1.sp,
        maxLines = 1,
    )
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
    iconSize: Int = 36,
) {
    BarButton(lamp = lamp, enabled = enabled, onClick = onClick, modifier = modifier) { color ->
        Icon(icon, contentDescription = description, tint = color, modifier = Modifier.size(iconSize.dp))
    }
}

/**
 * Airflow MODE: cycles through the vehicle's outlets. Never "latched", so it keeps the graphite slab and shows the
 * current outlet as a glyph (seated figure with arrows); AUTO and unknown fall back to text.
 */
@Composable
internal fun ModeButton(mode: AirMode, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PhoenixTheme.palette
    BarButton(lamp = Lamp.OFF, enabled = enabled, onClick = onClick, modifier = modifier) { _ ->
        val glyph = HvacGlyphs.airflow(mode)
        if (glyph != null) {
            Icon(glyph, contentDescription = modeText(mode), tint = palette.text, modifier = Modifier.size(48.dp))
        } else {
            Text(
                modeText(mode),
                color = if (mode == AirMode.UNKNOWN) palette.textUnknown else palette.text,
                fontFamily = DisplayFont,
                fontSize = 22.sp,
                letterSpacing = 1.sp,
                maxLines = 1,
            )
        }
    }
}

/**
 * HOME: leaves whatever is projected (Android Auto, the radio app) for the launcher. Not a climate control, so it
 * stays live while the vehicle link is down. Even chamfer + accent outline when elsewhere; filled like a latched
 * toggle while the launcher is already on screen ([active]). Mark is the Hennessey "H" (untinted brand red).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeButton(active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PhoenixTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val filled = active || pressed
    val fill: Brush = when {
        pressed -> SolidColor(palette.accentPressed)
        active -> palette.activeFill
        else -> palette.inactiveFillBrush
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = MinTouch, minHeight = MinTouch)
            .fillMaxHeight()
            .clip(ActionShape)
            .background(fill)
            .bevel(palette, gloss = active && !pressed)
            .border(1.dp, if (filled) palette.bevelShadow else palette.accent, ActionShape)
            .combinedClickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.hennessey_logo_square),
            contentDescription = "home",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(0.78f).fillMaxHeight(0.55f),
        )
    }
}

/**
 * Glanceable clock + truck outside ambient for the right end of the top climate row. Not tappable — just
 * readouts, so no button chrome; a thin accent rule separates them.
 */
@Composable
internal fun StatusReadouts(clock: String, outsideTemp: TempText, modifier: Modifier = Modifier) {
    val palette = PhoenixTheme.palette
    Row(
        modifier = modifier.fillMaxHeight().padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            clock.ifEmpty { BarUiState.UNKNOWN_TEXT },
            color = palette.text,
            fontFamily = DisplayFont,
            fontSize = 30.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
        )
        Box(Modifier.width(1.dp).fillMaxHeight(0.55f).background(palette.accent))
        Row(verticalAlignment = Alignment.Top) {
            val known = outsideTemp.value != BarUiState.UNKNOWN_TEXT
            Text(
                outsideTemp.value,
                color = if (known) palette.text else palette.textUnknown,
                fontFamily = DisplayFont,
                fontSize = 28.sp,
                maxLines = 1,
            )
            if (outsideTemp.unit != null) {
                Text(
                    outsideTemp.unit,
                    color = palette.accent,
                    fontFamily = DisplayFont,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 3.dp, start = 1.dp),
                )
            }
        }
    }
}

/** Seat heater: seat glyph plus one pip per level, lit in the heat colour rather than the accent. */
@Composable
internal fun SeatHeatButton(
    seat: SeatUi,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
) {
    val palette = PhoenixTheme.palette
    val accent = palette.heat
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
                if (mirrored) HvacGlyphs.SeatHeatMirrored else HvacGlyphs.SeatHeat,
                contentDescription = description,
                tint = color,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(2.dp))
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
                    .background(if (on) palette.activeFill else SolidColor(palette.unlit), SegmentShape),
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
