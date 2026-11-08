package com.phoenix.hvacbar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Fixed palette for the bar. High contrast for daylight readability in the cab. */
object BarColors {
    val background = Color(0xFF0B0B0D)
    val surface = Color(0xFF1C1D22)
    val outline = Color(0xFF34363D)
    val text = Color(0xFFF2F2F2)
    val textUnknown = Color(0xFF8A8C93)
    val lit = Color(0xFFFFA726)
    val heat = Color(0xFFFF5A36)
    val cool = Color(0xFF4FC3F7)
    val fan = Color(0xFFE8F4FF)
    val fanOff = Color(0xFF2E3138)
}

private val barColorScheme = darkColorScheme(
    primary = BarColors.lit,
    onPrimary = Color.Black,
    secondary = BarColors.cool,
    background = BarColors.background,
    onBackground = BarColors.text,
    surface = BarColors.surface,
    onSurface = BarColors.text,
    onSurfaceVariant = BarColors.textUnknown,
    outline = BarColors.outline,
)

/** Material3 theme with the bar's dark color scheme. */
@Composable
fun BarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = barColorScheme, content = content)
}
