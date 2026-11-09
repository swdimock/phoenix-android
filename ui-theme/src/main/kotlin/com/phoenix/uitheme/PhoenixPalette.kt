package com.phoenix.uitheme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Full colour set: fixed graphite tiers and status colours plus the accent of the selected [preset].
 * Dark only; tuned for high contrast in a sunlit cab.
 */
@Immutable
data class PhoenixPalette(
    val preset: AccentPreset,
    /** Deepest tier: screen background behind everything. */
    val background: Color = Color(0xFF09090A),
    /** Header bands and the HVAC bar backing. */
    val backgroundRaised: Color = Color(0xFF101113),
    /** Cards and inactive buttons. */
    val surface: Color = Color(0xFF17181B),
    /** Raised surfaces, the top of brushed gradients. */
    val surfaceHigh: Color = Color(0xFF24262A),
    val outline: Color = Color(0xFF3A3D43),
    val outlineStrong: Color = Color(0xFF60646C),
    val text: Color = Color(0xFFF3F3F3),
    val textMuted: Color = Color(0xFFA9ADB4),
    /** Values that are not known yet ("--"). */
    val textUnknown: Color = Color(0xFF7D8188),
    /** Brushed-silver badging. */
    val silver: Color = Color(0xFFC9CDD2),
    /** Hennessey badge red; fixed regardless of [preset]. */
    val badgeRed: Color = Color(0xFFD71920),
    val success: Color = Color(0xFF5FE08A),
    val warning: Color = Color(0xFFFFC845),
    val error: Color = Color(0xFFFF5A5A),
    val errorContainer: Color = Color(0xFF4A0E0E),
    val heat: Color = Color(0xFFFF5A36),
    val cool: Color = Color(0xFF4FC3F7),
) {
    val accent: Color get() = preset.accent
    val accentDim: Color get() = preset.accentDim
    val accentPressed: Color get() = preset.accentPressed
    val onAccent: Color get() = preset.onAccent

    /** Active / filled controls: brushed-metal gradient for Tuxedo Black, flat accent for the others. */
    val activeFill: Brush get() = Brush.verticalGradient(listOf(preset.fillTop, preset.fillBottom))

    /** Inactive controls: flat graphite, thin [inactiveOutline], [inactiveText]. */
    val inactiveFill: Color get() = surface
    val inactiveOutline: Color get() = accentDim
    val inactiveText: Color = Color(0xFFC9CCD1)

    /** Unlit fan segments and level pips. */
    val unlit: Color = Color(0xFF232529)

    /** Header band: a touch lighter at the top edge. */
    val headerBand: Brush get() = Brush.verticalGradient(listOf(surfaceHigh, backgroundRaised))

    companion object {
        fun forPreset(preset: AccentPreset) = PhoenixPalette(preset)
    }
}
