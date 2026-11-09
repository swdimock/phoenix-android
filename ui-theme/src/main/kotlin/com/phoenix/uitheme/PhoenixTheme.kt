package com.phoenix.uitheme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

val LocalPhoenixPalette = staticCompositionLocalOf { PhoenixPalette.forPreset(AccentPreset.DEFAULT) }

/** Material3 theme plus [PhoenixPalette] for [preset]. Read the palette with [PhoenixTheme.palette]. */
@Composable
fun PhoenixTheme(
    preset: AccentPreset = AccentPreset.DEFAULT,
    typography: Typography = PhoenixTypography.Car,
    content: @Composable () -> Unit,
) {
    val palette = remember(preset) { PhoenixPalette.forPreset(preset) }
    val colorScheme = remember(palette) { palette.toColorScheme() }
    MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = PhoenixShapes.Material) {
        // Text outside any Surface would otherwise default to black.
        CompositionLocalProvider(LocalPhoenixPalette provides palette, LocalContentColor provides palette.text, content = content)
    }
}

object PhoenixTheme {
    val palette: PhoenixPalette
        @Composable @ReadOnlyComposable get() = LocalPhoenixPalette.current
}

/** Every slot is set so no Material baseline (purple-tinted) container colour leaks through. */
fun PhoenixPalette.toColorScheme(): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = accentDim,
    onPrimaryContainer = text,
    inversePrimary = accentDim,
    secondary = silver,
    onSecondary = background,
    secondaryContainer = surfaceHigh,
    onSecondaryContainer = text,
    tertiary = badgeRed,
    onTertiary = text,
    tertiaryContainer = errorContainer,
    onTertiaryContainer = text,
    background = background,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = surfaceHigh,
    onSurfaceVariant = textMuted,
    surfaceTint = accent,
    inverseSurface = text,
    inverseOnSurface = background,
    error = error,
    onError = background,
    errorContainer = errorContainer,
    onErrorContainer = text,
    outline = outline,
    outlineVariant = outline,
    scrim = background,
    surfaceBright = surfaceHigh,
    surfaceDim = background,
    surfaceContainerLowest = background,
    surfaceContainerLow = backgroundRaised,
    surfaceContainer = surface,
    surfaceContainerHigh = surfaceHigh,
    surfaceContainerHighest = surfaceHigh,
)
