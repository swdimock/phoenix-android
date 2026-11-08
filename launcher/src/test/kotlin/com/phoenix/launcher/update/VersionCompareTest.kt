package com.phoenix.launcher.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VersionCompareTest {
    @Test
    fun comparesNumericComponents() {
        assertThat(isNewerVersion("v0.2.0", "0.1.0")).isTrue()
        assertThat(isNewerVersion("0.10.0", "0.9.9")).isTrue()
        assertThat(isNewerVersion("v1.0", "1.0.0")).isFalse()
        assertThat(isNewerVersion("0.1.0", "0.1.0")).isFalse()
        assertThat(isNewerVersion("v0.0.9", "0.1.0")).isFalse()
        assertThat(isNewerVersion("v0.1.1-beta", "0.1.0")).isTrue()
    }
}
