package com.phoenix.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoenix.uitheme.DisplayFont
import com.phoenix.uitheme.PhoenixShapes
import com.phoenix.uitheme.PhoenixTheme
import kotlinx.coroutines.delay

/** How long the splash stays fully opaque before fading. */
const val SPLASH_HOLD_MS = 1_800L
private const val SPLASH_FADE_MS = 500

/**
 * Cold-start splash: Hennessey logo (or wordmark) centred on black, with the VELOCIRAPTOR 600 line and this
 * truck's build number, held for [SPLASH_HOLD_MS] and faded out. Draw it last so it covers the launcher while the
 * first pane settles. Shown once per process start ([show]); activity recreation does not replay it.
 */
@Composable
fun SplashOverlay(show: Boolean) {
    var visible by rememberSaveable { mutableStateOf(show) }
    LaunchedEffect(Unit) {
        if (visible) {
            delay(SPLASH_HOLD_MS)
            visible = false
        }
    }
    AnimatedVisibility(visible, exit = fadeOut(tween(SPLASH_FADE_MS))) {
        SplashContent()
    }
}

@Composable
private fun SplashContent() {
    val palette = PhoenixTheme.palette
    Box(Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = LauncherLayout.HvacBarInset)) {
            HennesseyMark(height = 160.dp, wordmarkSize = 72.sp)
            Spacer(Modifier.height(36.dp))
            Box(Modifier.size(width = 220.dp, height = 4.dp).background(palette.accent))
            Spacer(Modifier.height(28.dp))
            Text(
                "VELOCIRAPTOR 600",
                fontFamily = DisplayFont,
                fontSize = 30.sp,
                letterSpacing = 5.sp,
                color = palette.silver,
                maxLines = 1,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SplashBadge(VehicleIdentity.PACKAGE, palette.badgeRed)
                SplashBadge(VehicleIdentity.buildLabel, palette.surfaceHigh)
            }
        }
    }
}

@Composable
private fun SplashBadge(text: String, background: Color) {
    Text(
        text,
        fontFamily = DisplayFont,
        fontSize = 18.sp,
        letterSpacing = 3.sp,
        color = PhoenixTheme.palette.text,
        maxLines = 1,
        modifier = Modifier.background(background, PhoenixShapes.Chip).padding(horizontal = 14.dp, vertical = 4.dp),
    )
}
