package com.phoenix.hvacbar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoenix.hvacbar.R
import com.phoenix.uitheme.PhoenixShapes
import com.phoenix.uitheme.PhoenixTheme

/** Gap between keys, both within a row and between rows. */
private val RowGap = 6.dp

/** Width of the HOME rail on the left of the bar. */
private val HomeRailWidth = 88.dp

/** Fixed width for the clock + OAT cluster at the right end of the top row. */
private val StatusWidth = 176.dp

/**
 * The climate bar: a full-height HOME rail on the left and three rows of climate keys, with the system clock and
 * truck OAT at the right of the top row. Stateless: fills whatever height it is given (227 px in the overlay).
 * While [BarUiState.interactive] is false the climate keys are dimmed, labelled, and ignore taps; HOME keeps working.
 */
@Composable
fun HvacBar(
    state: BarUiState,
    onControl: (BarControl) -> Unit,
    onToggleUnits: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    homeActive: Boolean = false,
    clock: String = "",
    hint: String? = null,
) {
    val enabled = state.interactive
    val palette = PhoenixTheme.palette
    Box(modifier.fillMaxSize().background(palette.backgroundRaised)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(palette.accent))
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HomeButton(homeActive, onHome, Modifier.width(HomeRailWidth).fillMaxHeight())
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(RowGap),
            ) {
                // Clock / OAT stay full opacity; only the climate keys dim when the vehicle link is down.
                ToggleRow(state, enabled, onControl, clock, Modifier.weight(1f))
                Column(
                    modifier = Modifier.weight(2f).alpha(if (enabled) 1f else 0.4f),
                    verticalArrangement = Arrangement.spacedBy(RowGap),
                ) {
                    AuxRow(state, enabled, onControl, Modifier.weight(1f))
                    ControlRow(state, enabled, onControl, onToggleUnits, Modifier.weight(1f))
                }
            }
        }
        val label = if (!enabled) stringResource(R.string.vehicle_link) else hint
        if (label != null) StatusChip(label, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun ToggleRow(
    state: BarUiState,
    enabled: Boolean,
    onControl: (BarControl) -> Unit,
    clock: String,
    modifier: Modifier,
) {
    BarRow(modifier) {
        Row(
            modifier = Modifier.weight(1f).fillMaxHeight().alpha(if (enabled) 1f else 0.4f),
            horizontalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            IconToggle(Icons.Filled.PowerSettingsNew, "power", state.power, enabled, { onControl(BarControl.POWER) }, Modifier.weight(1f))
            LabelButton("AUTO", state.auto, enabled, { onControl(BarControl.AUTO) }, Modifier.weight(1f))
            LabelButton("A/C", state.ac, enabled, { onControl(BarControl.AC) }, Modifier.weight(1f))
            LabelButton("DUAL", state.dual, enabled, { onControl(BarControl.DUAL) }, Modifier.weight(1f))
        }
        StatusReadouts(clock, state.outsideTemp, Modifier.width(StatusWidth))
    }
}

@Composable
private fun AuxRow(state: BarUiState, enabled: Boolean, onControl: (BarControl) -> Unit, modifier: Modifier) {
    BarRow(modifier) {
        SeatHeatButton(state.driverSeatHeat, "driver seat heat", enabled, { onControl(BarControl.DRIVER_SEAT_HEAT) }, Modifier.weight(1f))
        IconToggle(HvacGlyphs.FrontDefrost, "front defrost", state.frontDefrost, enabled, { onControl(BarControl.FRONT_DEFROST) }, Modifier.weight(1f), iconSize = 46)
        ModeButton(state.mode, enabled, { onControl(BarControl.MODE) }, Modifier.weight(1f))
        LabelButton("RECIRC", state.recirculate, enabled, { onControl(BarControl.RECIRCULATE) }, Modifier.weight(1f), fontSize = 18)
        LabelButton("MAX A/C", state.maxAc, enabled, { onControl(BarControl.MAX_AC) }, Modifier.weight(1f), fontSize = 18)
        SeatHeatButton(state.passengerSeatHeat, "passenger seat heat", enabled, { onControl(BarControl.PASSENGER_SEAT_HEAT) }, Modifier.weight(1f), mirrored = true)
    }
}

@Composable
private fun ControlRow(
    state: BarUiState,
    enabled: Boolean,
    onControl: (BarControl) -> Unit,
    onToggleUnits: () -> Unit,
    modifier: Modifier,
) {
    BarRow(modifier) {
        TempControl(
            temp = state.driverTemp,
            enabled = enabled,
            onDown = { onControl(BarControl.DRIVER_TEMP_DOWN) },
            onUp = { onControl(BarControl.DRIVER_TEMP_UP) },
            onToggleUnits = onToggleUnits,
            weight = 1f,
        )
        Box(Modifier.width(2.dp))
        FanControl(
            segments = state.fanSegments,
            auto = state.fanAuto,
            enabled = enabled,
            onDown = { onControl(BarControl.FAN_DOWN) },
            onUp = { onControl(BarControl.FAN_UP) },
            weight = 1f,
        )
        Box(Modifier.width(2.dp))
        TempControl(
            temp = state.passengerTemp,
            enabled = enabled,
            onDown = { onControl(BarControl.PASSENGER_TEMP_DOWN) },
            onUp = { onControl(BarControl.PASSENGER_TEMP_UP) },
            onToggleUnits = onToggleUnits,
            weight = 1f,
        )
    }
}

@Composable
private fun BarRow(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RowGap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun StatusChip(text: String, modifier: Modifier) {
    val palette = PhoenixTheme.palette
    Text(
        text = text,
        color = palette.text,
        fontSize = 13.sp,
        modifier = modifier
            .padding(top = 2.dp)
            .background(palette.surface.copy(alpha = 0.92f), PhoenixShapes.Chip)
            .border(1.dp, palette.accent, PhoenixShapes.Chip)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}
