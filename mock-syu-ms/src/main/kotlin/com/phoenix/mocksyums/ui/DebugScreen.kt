package com.phoenix.mocksyums.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.phoenix.fytipc.climate.ClimateAction
import com.phoenix.fytipc.climate.ClimateState
import com.phoenix.fytipc.climate.Zone
import com.phoenix.fytipc.profile.ClimateCodec
import com.phoenix.fytipc.profile.FytTemperature
import com.phoenix.mocksyums.MockRuntime
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugScreen(rt: MockRuntime) {
    val state by rt.vehicle.state.collectAsState()
    val clients by rt.clients.collectAsState()
    val registrations by rt.registrations.collectAsState()
    val ready by rt.audio.audioPathReady.collectAsState()
    val audible by rt.audio.audible.collectAsState()
    val bootDelay by rt.audio.bootDelayMs.collectAsState()
    val accOn by rt.accOn.collectAsState()
    val recent by rt.recentCommands.collectAsState()
    val replay by rt.replayStatus.collectAsState()
    val range = (rt.profile as? ClimateCodec)?.tempRangeF ?: 60..85

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Mock com.syu.ms - ${rt.profile.displayName}", style = MaterialTheme.typography.headlineSmall)
            Text("Clients (callback binders): $clients   registrations: $registrations   understood cmds: ${rt.vehicle.understoodCommands}")
            Text(
                "ACC ${if (accOn) "ON" else "OFF"}   audioPathReady=$ready   audible=$audible",
                style = MaterialTheme.typography.titleMedium,
            )

            Section("Ignition / boot race")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ACC")
                Switch(checked = accOn, onCheckedChange = { if (it) rt.accOn() else rt.accOff() })
                OutlinedButton(onClick = rt::rebootAudio) { Text("Restart boot race") }
                OutlinedButton(onClick = { rt.audio.forceAudible(!audible) }) { Text("Toggle audible") }
            }
            Text("Readiness delay after boot: ${bootDelay / 1000} s")
            Slider(
                value = bootDelay / 1000f,
                onValueChange = { rt.audio.setBootDelay((it.roundToInt() * 1000).toLong()) },
                valueRange = 0f..30f,
                steps = 29,
            )

            Section("Climate state (vehicle side)")
            Text(describe(state), fontFamily = FontFamily.Monospace)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ACTIONS.forEach { (label, action) ->
                    Button(onClick = { rt.vehicle.press(action) }) { Text(label) }
                }
            }
            Text("Fan: ${if (state.fanAuto == true) "AUTO" else state.fanSpeed ?: "--"}")
            Slider(
                value = (state.fanSpeed ?: 0).toFloat(),
                onValueChange = { v -> rt.vehicle.mutate { it.copy(fanSpeed = v.roundToInt(), fanAuto = false, auto = false) } },
                valueRange = 0f..state.fanMax.toFloat(),
                steps = state.fanMax - 1,
            )
            TempSlider("Driver", state.driverTemp.displayF(), range) { f -> rt.vehicle.mutate { it.copy(driverTemp = FytTemperature.ofF(f)) } }
            TempSlider("Passenger", state.passengerTemp.displayF(), range) { f -> rt.vehicle.mutate { it.copy(passengerTemp = FytTemperature.ofF(f)) } }

            Section("Capture replay")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = rt::replayCapture) { Text("Replay capture") }
                OutlinedButton(onClick = rt::stopReplay) { Text("Stop") }
            }
            Text(replay)
            Text("Loads files/${MockRuntime.CAPTURE_FILE} if present, else assets/${MockRuntime.SAMPLE_ASSET}")

            Section("Recent commands received")
            recent.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun TempSlider(label: String, shown: String, range: IntRange, onSet: (Int) -> Unit) {
    Text("$label setpoint: $shown")
    Slider(
        value = shown.toFloatOrNull() ?: range.first.toFloat(),
        onValueChange = { onSet(it.roundToInt()) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = range.last - range.first - 1,
    )
}

private fun describe(s: ClimateState): String = buildString {
    appendLine("power=${s.power} auto=${s.auto} ac=${s.ac} maxAc=${s.maxAc} dual=${s.dual}")
    appendLine("recirc=${s.recirculate} frontDef=${s.frontDefrost} rearDef=${s.rearDefrost} mode=${s.mode}")
    appendLine("air windshield=${s.airWindshield} face=${s.airFace} feet=${s.airFeet}")
    appendLine("temp L=${s.driverTemp.displayF()} R=${s.passengerTemp.displayF()} fan=${s.fanSpeed} fanAuto=${s.fanAuto}")
    append("seat heat L=${s.driverSeatHeat} R=${s.passengerSeatHeat}  cool L=${s.driverSeatCool} R=${s.passengerSeatCool}")
}

private val ACTIONS: List<Pair<String, ClimateAction>> = listOf(
    "Power" to ClimateAction.Power,
    "AUTO" to ClimateAction.ToggleAuto,
    "A/C" to ClimateAction.ToggleAc,
    "MAX A/C" to ClimateAction.ToggleMaxAc,
    "DUAL" to ClimateAction.ToggleDual,
    "Recirc" to ClimateAction.ToggleRecirculate,
    "Front def" to ClimateAction.ToggleFrontDefrost,
    "Rear def" to ClimateAction.ToggleRearDefrost,
    "Fan -" to ClimateAction.FanDown,
    "Fan +" to ClimateAction.FanUp,
    "Mode" to ClimateAction.CycleMode,
    "L temp -" to ClimateAction.TempDown(Zone.DRIVER),
    "L temp +" to ClimateAction.TempUp(Zone.DRIVER),
    "R temp -" to ClimateAction.TempDown(Zone.PASSENGER),
    "R temp +" to ClimateAction.TempUp(Zone.PASSENGER),
    "L seat heat" to ClimateAction.CycleSeatHeat(Zone.DRIVER),
    "R seat heat" to ClimateAction.CycleSeatHeat(Zone.PASSENGER),
    "L seat cool" to ClimateAction.CycleSeatCool(Zone.DRIVER),
    "R seat cool" to ClimateAction.CycleSeatCool(Zone.PASSENGER),
)
