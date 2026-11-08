package com.phoenix.hvacbar.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TapDebouncerTest {

    @Test
    fun windowIsMeasuredFromLastAcceptedTap() {
        val d = TapDebouncer(windowMs = 120)
        assertThat(d.accept("a", 0)).isTrue()
        assertThat(d.accept("a", 119)).isFalse()
        assertThat(d.accept("a", 120)).isTrue()
        assertThat(d.accept("a", 200)).isFalse()
    }

    @Test
    fun keysAreIndependent() {
        val d = TapDebouncer(windowMs = 120)
        assertThat(d.accept("a", 0)).isTrue()
        assertThat(d.accept("b", 10)).isTrue()
    }
}
