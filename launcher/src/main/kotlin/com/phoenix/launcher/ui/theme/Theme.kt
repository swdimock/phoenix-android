package com.phoenix.launcher.ui.theme

import androidx.compose.runtime.Composable
import com.phoenix.uitheme.AccentPreset
import com.phoenix.uitheme.PhoenixTheme
import com.phoenix.uitheme.PhoenixTypography

/** Dark, high-contrast launcher theme with the user's accent [preset]. */
@Composable
fun LauncherTheme(preset: AccentPreset, content: @Composable () -> Unit) {
    PhoenixTheme(preset, PhoenixTypography.Car, content)
}
