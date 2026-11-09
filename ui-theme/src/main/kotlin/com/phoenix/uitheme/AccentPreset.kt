package com.phoenix.uitheme

import androidx.compose.ui.graphics.Color

/**
 * User-selectable accent colour. Backgrounds stay near-black graphite in every preset; only the accent changes.
 *
 * @property id stable identifier persisted in preferences and sent in [ThemeBroadcast].
 * @property accent flat accent: section titles, outlines, fan segments, swatches.
 * @property accentDim inactive outlines and tracks.
 * @property accentPressed fill while a control is held down.
 * @property onAccent text and icons drawn on any accent fill.
 * @property fillTop top stop of the vertical gradient on active / filled buttons; equal to [fillBottom] for a flat fill.
 */
enum class AccentPreset(
    val id: String,
    val displayName: String,
    val accent: Color,
    val accentDim: Color,
    val accentPressed: Color,
    val onAccent: Color,
    val fillTop: Color = accent,
    val fillBottom: Color = accent,
) {
    /** Hennessey silver graphics on a Tuxedo Black truck: brushed-silver fills. */
    TUXEDO_BLACK(
        "tuxedo_black", "Tuxedo Black / Hennessey Silver",
        accent = Color(0xFFC3C8CE), accentDim = Color(0xFF5C6168), accentPressed = Color(0xFFF1F3F5), onAccent = Color(0xFF0E0F11),
        fillTop = Color(0xFFE4E7EA), fillBottom = Color(0xFFA3A9B0),
    ),
    MOLTEN_ORANGE(
        "molten_orange", "Molten Orange",
        accent = Color(0xFFF8701E), accentDim = Color(0xFF7A3510), accentPressed = Color(0xFFFF8A3D), onAccent = Color(0xFF120A05),
    ),
    RACE_RED(
        "race_red", "Race Red",
        accent = Color(0xFFD71920), accentDim = Color(0xFF7A1015), accentPressed = Color(0xFFE41E26), onAccent = Color(0xFFFFFFFF),
    ),
    BLUE_FLAME(
        "blue_flame", "Blue Flame",
        accent = Color(0xFF1F64C8), accentDim = Color(0xFF1C4384), accentPressed = Color(0xFF2A72DA), onAccent = Color(0xFFFFFFFF),
    ),
    ;

    companion object {
        val DEFAULT = TUXEDO_BLACK

        /** Ids of removed presets and what they now mean; keeps persisted values working. */
        private val Aliases = mapOf("ingot_silver" to TUXEDO_BLACK)

        /** The preset with [id] (or a retired alias of it), or null when [id] is null or unknown. */
        fun fromId(id: String?): AccentPreset? = entries.firstOrNull { it.id == id } ?: Aliases[id]

        /** The preset with [id], falling back to [DEFAULT]. */
        fun byId(id: String?): AccentPreset = fromId(id) ?: DEFAULT
    }
}
