package com.phoenix.uitheme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccentPresetTest {

    @Test
    fun `fromId finds every preset by its id`() {
        AccentPreset.entries.forEach { assertThat(AccentPreset.fromId(it.id)).isEqualTo(it) }
    }

    @Test
    fun `unknown or null id is null from fromId and default from byId`() {
        assertThat(AccentPreset.fromId(null)).isNull()
        assertThat(AccentPreset.fromId("TUXEDO_BLACK")).isNull()
        assertThat(AccentPreset.fromId("lime_green")).isNull()
        assertThat(AccentPreset.byId("lime_green")).isEqualTo(AccentPreset.TUXEDO_BLACK)
        assertThat(AccentPreset.byId(null)).isEqualTo(AccentPreset.DEFAULT)
    }

    @Test
    fun `default is tuxedo black`() {
        assertThat(AccentPreset.DEFAULT).isEqualTo(AccentPreset.TUXEDO_BLACK)
    }

    @Test
    fun `retired ingot_silver migrates to tuxedo black`() {
        assertThat(AccentPreset.fromId("ingot_silver")).isEqualTo(AccentPreset.TUXEDO_BLACK)
        assertThat(AccentPreset.byId("ingot_silver")).isEqualTo(AccentPreset.TUXEDO_BLACK)
    }

    @Test
    fun `ids are the documented stable values`() {
        assertThat(AccentPreset.entries.map { it.id })
            .containsExactly("tuxedo_black", "molten_orange", "race_red", "blue_flame")
            .inOrder()
    }

    @Test
    fun `text on every accent fill and accent on background are readable`() {
        val background = PhoenixPalette.forPreset(AccentPreset.DEFAULT).background
        AccentPreset.entries.forEach {
            listOf(it.accent, it.accentPressed, it.fillTop, it.fillBottom).forEach { fill ->
                assertThat(contrast(it.onAccent, fill)).isAtLeast(4.5)
            }
            assertThat(contrast(it.accent, background)).isAtLeast(3.0)
        }
    }

    @Test
    fun `inactive controls are readable and visibly outlined`() {
        AccentPreset.entries.forEach {
            val palette = PhoenixPalette.forPreset(it)
            assertThat(contrast(palette.inactiveText, palette.inactiveFill)).isAtLeast(7.0)
            assertThat(contrast(palette.inactiveOutline, palette.inactiveFill)).isAtLeast(1.3)
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
}
