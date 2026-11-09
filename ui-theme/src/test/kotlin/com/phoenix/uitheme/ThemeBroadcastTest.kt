package com.phoenix.uitheme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThemeBroadcastTest {

    @Test
    fun `encode then decode round-trips every preset`() {
        AccentPreset.entries.forEach { assertThat(ThemeBroadcast.decode(ThemeBroadcast.encode(it))).isEqualTo(it) }
    }

    @Test
    fun `encoded value is the preset id`() {
        assertThat(ThemeBroadcast.encode(AccentPreset.RACE_RED)).isEqualTo("race_red")
    }

    @Test
    fun `missing or unknown extra decodes to null`() {
        assertThat(ThemeBroadcast.decode(null)).isNull()
        assertThat(ThemeBroadcast.decode("")).isNull()
        assertThat(ThemeBroadcast.decode("chartreuse")).isNull()
    }

    @Test
    fun `wire constants are stable`() {
        assertThat(ThemeBroadcast.ACTION_ACCENT).isEqualTo("com.phoenix.theme.action.ACCENT")
        assertThat(ThemeBroadcast.EXTRA_PRESET).isEqualTo("preset")
    }
}
