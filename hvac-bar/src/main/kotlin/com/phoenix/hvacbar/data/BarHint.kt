package com.phoenix.hvacbar.data

import androidx.annotation.StringRes
import com.phoenix.hvacbar.R

/** Short transient messages the bar shows in response to a tap. */
enum class BarHint(@StringRes val messageRes: Int) {
    NOT_SUPPORTED(R.string.hint_not_supported),
    SEND_FAILED(R.string.hint_send_failed),
}
