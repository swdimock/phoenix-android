package com.phoenix.hvacbar.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClockFormatTest {

    @Test
    fun msUntilNextMinuteIsWithinAMinute() {
        assertThat(msUntilNextMinute(0L)).isEqualTo(60_000L)
        assertThat(msUntilNextMinute(59_999L)).isEqualTo(1L)
        assertThat(msUntilNextMinute(30_000L)).isEqualTo(30_000L)
    }
}
