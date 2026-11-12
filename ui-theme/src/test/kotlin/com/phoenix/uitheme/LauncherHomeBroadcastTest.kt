package com.phoenix.uitheme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LauncherHomeBroadcastTest {

    @Test
    fun `wire constants are stable`() {
        assertThat(LauncherHomeBroadcast.ACTION).isEqualTo("com.phoenix.launcher.action.HOME")
        assertThat(LauncherHomeBroadcast.EXTRA_VISIBLE).isEqualTo("visible")
    }
}
