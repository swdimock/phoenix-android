package com.phoenix.hvacbar.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val accent by settings.accentPreset.collectAsState()
    val homeActive by settings.launcherHome.collectAsState()
    var hint by remember { mutableStateOf<BarHint?>(null) }

    LaunchedEffect(repository) {
        repository.hints.collectLatest {
            hint = it
            delay(HINT_DURATION_MS)
            hint = null
        }
    }

    val ui = remember(climate, connection, celsius) { barUiState(climate, connection, celsius) }
    val context = LocalContext.current
    BarTheme(accent) {
        HvacBar(
            state = ui,
            onControl = { repository.send(it.action) },
            onToggleUnits = settings::toggleUnits,
            onHome = { goHome(context) },
            modifier = modifier,
            homeActive = homeActive,
            hint = hint?.let { stringResource(it.messageRes) },
        )
    }
}

/**
 * Brings up the system HOME (the Phoenix launcher) from the overlay. Allowed from the background because the bar
 * holds SYSTEM_ALERT_WINDOW, which exempts it from Android's background-start restriction.
 */
private fun goHome(context: Context) {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("HvacBar", "no HOME activity", e)
    }
}
