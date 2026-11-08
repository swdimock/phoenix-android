package com.phoenix.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

private val Colors = darkColorScheme(
    primary = Color(0xFFFF9800),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF5A3600),
    onPrimaryContainer = Color(0xFFFFE0B2),
    secondary = Color(0xFF80D8FF),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF111111),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF222222),
    onSurfaceVariant = Color(0xFFE0E0E0),
    outline = Color(0xFF757575),
    error = Color(0xFFFF6E6E),
    errorContainer = Color(0xFF5C1010),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val Base = Typography()

/** Larger-than-default type for glanceable use while driving. */
private val CarTypography = Typography(
    headlineMedium = Base.headlineMedium.copy(fontSize = 36.sp, lineHeight = 44.sp),
    titleLarge = Base.titleLarge.copy(fontSize = 30.sp, lineHeight = 38.sp),
    titleMedium = Base.titleMedium.copy(fontSize = 24.sp, lineHeight = 32.sp),
    bodyLarge = Base.bodyLarge.copy(fontSize = 22.sp, lineHeight = 30.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 18.sp, lineHeight = 26.sp),
    labelLarge = Base.labelLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
)

/** Dark, high-contrast launcher theme. */
@Composable
fun PhoenixTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = CarTypography, content = content)
}
