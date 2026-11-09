package com.phoenix.hvacbar.ui

import androidx.compose.runtime.Composable
import com.phoenix.uitheme.AccentPreset
import com.phoenix.uitheme.PhoenixTheme

/** Shared Phoenix theme for the bar. Colours come from [PhoenixTheme.palette]. */
@Composable
fun BarTheme(preset: AccentPreset = AccentPreset.DEFAULT, content: @Composable () -> Unit) {
    PhoenixTheme(preset = preset, content = content)
}
