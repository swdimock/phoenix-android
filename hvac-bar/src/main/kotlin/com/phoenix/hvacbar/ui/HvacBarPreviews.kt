package com.phoenix.hvacbar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.phoenix.fytipc.ConnectionState
import com.phoenix.fytipc.climate.ClimateState

@Composable
private fun SampleBar(climate: ClimateState, connection: ConnectionState = ConnectionState.CONNECTED) {
    BarTheme {
        HvacBar(state = barUiState(climate, connection, useCelsius = false), onControl = {}, onToggleUnits = {})
    }
}

@Preview(name = "All unknown", widthDp = 1200, heightDp = 227)
@Composable
private fun PreviewAllUnknown() = SampleBar(ClimateState.EMPTY)

@Preview(name = "Typical driving", widthDp = 1200, heightDp = 227)
@Composable
private fun PreviewTypical() = SampleBar(BarSamples.typicalDriving)

@Preview(name = "AUTO with DUAL", widthDp = 1200, heightDp = 227)
@Composable
private fun PreviewAutoDual() = SampleBar(BarSamples.autoDual)

@Preview(name = "Full AUTO (fan and airflow)", widthDp = 1200, heightDp = 227)
@Composable
private fun PreviewFullAuto() = SampleBar(BarSamples.fullAuto)

@Preview(name = "Disconnected", widthDp = 1200, heightDp = 227)
@Composable
private fun PreviewDisconnected() = SampleBar(BarSamples.typicalDriving, ConnectionState.DISCONNECTED)
