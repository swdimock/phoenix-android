package com.phoenix.hvacbar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
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

private val RowGap = 3.dp

/**
 * The three-row climate bar. Stateless: fills whatever height it is given (227 px in the overlay).
 * While [BarUiState.interactive] is false the bar is dimmed, labelled, and ignores taps.
 */
@Composable
fun HvacBar(
    state: BarUiState,
    onControl: (BarControl) -> Unit,
    onToggleUnits: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    val enabled = state.interactive
    val palette = PhoenixTheme.palette
    Box(modifier.fillMaxSize().background(palette.backgroundRaised)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(palette.accent))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (enabled) 1f else 0.4f)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            ToggleRow(state, enabled, onControl, Modifier.weight(1f))
            AuxRow(state, enabled, onControl, Modifier.weight(1f))
            ControlRow(state, enabled, onControl, onToggleUnits, Modifier.weight(1f))
        }
        val label = if (!enabled) stringResource(R.string.vehicle_link) else hint
        if (label != null) StatusChip(label, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun ToggleRow(state: BarUiState, enabled: Boolean, onControl: (BarControl) -> Unit, modifier: Modifier) {
    BarRow(modifier) {
        IconToggle(Icons.Filled.PowerSettingsNew, "power", state.power, enabled, { onControl(BarControl.POWER) }, Modifier.weight(1f))
        LabelButton("AUTO", state.auto, enabled, { onControl(BarControl.AUTO) }, Modifier.weight(1f))
        LabelButton("REAR", state.rearDefrost, enabled, { onControl(BarControl.REAR_DEFROST) }, Modifier.weight(1f))
        LabelButton("DUAL", state.dual, enabled, { onControl(BarControl.DUAL) }, Modifier.weight(1f))
        LabelButton("A/C", state.ac, enabled, { onControl(BarControl.AC) }, Modifier.weight(1f))
    }
}

@Composable
private fun AuxRow(state: BarUiState, enabled: Boolean, onControl: (BarControl) -> Unit, modifier: Modifier) {
    BarRow(modifier) {
        SeatButton(state.driverSeatHeat, heat = true, "driver seat heat", enabled, { onControl(BarControl.DRIVER_SEAT_HEAT) }, Modifier.weight(1f))
        SeatButton(state.driverSeatCool, heat = false, "driver seat cool", enabled, { onControl(BarControl.DRIVER_SEAT_COOL) }, Modifier.weight(1f))
        LabelButton("FRONT", state.frontDefrost, enabled, { onControl(BarControl.FRONT_DEFROST) }, Modifier.weight(1f), caption = "DEFROST", fontSize = 18)
        LabelButton(state.mode, Lamp.OFF, enabled, { onControl(BarControl.MODE) }, Modifier.weight(1f), caption = "MODE", fontSize = 18)
        LabelButton("RECIRC", state.recirculate, enabled, { onControl(BarControl.RECIRCULATE) }, Modifier.weight(1f), fontSize = 18)
        LabelButton("MAX A/C", state.maxAc, enabled, { onControl(BarControl.MAX_AC) }, Modifier.weight(1f), fontSize = 18)
        SeatButton(state.passengerSeatCool, heat = false, "passenger seat cool", enabled, { onControl(BarControl.PASSENGER_SEAT_COOL) }, Modifier.weight(1f))
        SeatButton(state.passengerSeatHeat, heat = true, "passenger seat heat", enabled, { onControl(BarControl.PASSENGER_SEAT_HEAT) }, Modifier.weight(1f))
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
        Box(Modifier.width(8.dp))
        FanControl(
            segments = state.fanSegments,
            auto = state.fanAuto,
            enabled = enabled,
            onDown = { onControl(BarControl.FAN_DOWN) },
            onUp = { onControl(BarControl.FAN_UP) },
            weight = 1f,
        )
        Box(Modifier.width(8.dp))
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
