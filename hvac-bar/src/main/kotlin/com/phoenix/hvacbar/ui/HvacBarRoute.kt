package com.phoenix.hvacbar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.phoenix.hvacbar.BarSettings
import com.phoenix.hvacbar.data.BarHint
import com.phoenix.hvacbar.data.ClimateRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private const val HINT_DURATION_MS = 2_000L

/** [HvacBar] wired to the live [repository] and [settings]. */
@Composable
fun HvacBarRoute(repository: ClimateRepository, settings: BarSettings, modifier: Modifier = Modifier) {
    val climate by repository.state.collectAsState()
    val connection by repository.connection.collectAsState()
    val celsius by settings.useCelsius.collectAsState()
    var hint by remember { mutableStateOf<BarHint?>(null) }

    LaunchedEffect(repository) {
        repository.hints.collectLatest {
            hint = it
            delay(HINT_DURATION_MS)
            hint = null
        }
    }

    val ui = remember(climate, connection, celsius) { barUiState(climate, connection, celsius) }
    BarTheme {
        HvacBar(
            state = ui,
            onControl = { repository.send(it.action) },
            onToggleUnits = settings::toggleUnits,
            modifier = modifier,
            hint = hint?.let { stringResource(it.messageRes) },
        )
    }
}
