package com.phoenix.hvacbar.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
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
import java.util.Calendar
import java.util.Locale
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
    val context = LocalContext.current
    var clock by remember { mutableStateOf(formatClock(context)) }

    LaunchedEffect(repository) {
        repository.hints.collectLatest {
            hint = it
            delay(HINT_DURATION_MS)
            hint = null
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            clock = formatClock(context)
            delay(msUntilNextMinute())
        }
    }

    val ui = remember(climate, connection, celsius) { barUiState(climate, connection, celsius) }
    BarTheme(accent) {
        HvacBar(
            state = ui,
            onControl = { repository.send(it.action) },
            onToggleUnits = settings::toggleUnits,
            onHome = { goHome(context) },
            modifier = modifier,
            homeActive = homeActive,
            clock = clock,
            hint = hint?.let { stringResource(it.messageRes) },
        )
    }
}

/** Wall-clock for the top-row status readouts, matching the system 12/24-hour preference. */
internal fun formatClock(context: Context, nowMs: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
    return if (DateFormat.is24HourFormat(context)) {
        String.format(Locale.getDefault(), "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    } else {
        val hour = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        String.format(Locale.getDefault(), "%d:%02d", hour, cal.get(Calendar.MINUTE))
    }
}

/** Milliseconds until the next minute boundary, so the status clock ticks without a 1 Hz wake. */
internal fun msUntilNextMinute(nowMs: Long = System.currentTimeMillis()): Long {
    val intoMinute = nowMs % 60_000L
    return (60_000L - intoMinute).coerceAtLeast(1L)
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
