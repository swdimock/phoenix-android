package com.phoenix.hvacbar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.phoenix.hvacbar.overlay.BarGeometry
import com.phoenix.hvacbar.ui.BarSamples
import com.phoenix.hvacbar.ui.BarTheme
import com.phoenix.hvacbar.ui.HvacBar
import com.phoenix.hvacbar.ui.HvacBarRoute
import com.phoenix.hvacbar.ui.barUiState

/**
 * Full-screen host for the bar, for emulator screenshots without the overlay permission.
 * `adb shell am start -n com.phoenix.hvacbar/.DebugBarActivity [--es sample typical]` renders a canned
 * state from [BarSamples] instead of the live repository.
 */
class DebugBarActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sample = intent.getStringExtra(EXTRA_SAMPLE)?.let { BarSamples.byName[it] }
        val app = barApp
        setContent {
            val barHeight = with(LocalDensity.current) { BarGeometry.HEIGHT_PX.toDp() }
            val barModifier = Modifier.fillMaxWidth().height(barHeight)
            Box(Modifier.fillMaxSize().background(Color(0xFF202530))) {
                Text(
                    stringResource(R.string.debug_host_label),
                    color = Color.White,
                    fontSize = 24.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(Modifier.align(Alignment.BottomCenter)) {
                    if (sample != null) {
                        BarTheme(app.settings.accentPreset.collectAsState().value) {
                            HvacBar(
                                state = barUiState(sample.first, sample.second, useCelsius = false),
                                onControl = {},
                                onToggleUnits = {},
                                onHome = {},
                                modifier = barModifier,
                                clock = "3:04",
                                homeActive = true,
                            )
                        }
                    } else {
                        HvacBarRoute(app.repository, app.settings, barModifier)
                    }
                }
            }
        }
    }

    private companion object {
        const val EXTRA_SAMPLE = "sample"
    }
}
