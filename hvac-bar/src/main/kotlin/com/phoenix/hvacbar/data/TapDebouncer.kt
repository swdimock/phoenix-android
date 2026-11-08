package com.phoenix.hvacbar.data

/**
 * Drops repeats of the same key that arrive within [windowMs] of the last accepted one. Different keys
 * do not affect each other, so tapping fan-up then temp-up quickly sends both.
 */
class TapDebouncer(private val windowMs: Long = DEFAULT_WINDOW_MS) {

    private val lastAccepted = HashMap<Any, Long>()

    /** Returns true if the tap identified by [key] at [nowMs] should be acted on. */
    fun accept(key: Any, nowMs: Long): Boolean {
        val previous = lastAccepted[key]
        if (previous != null && nowMs - previous < windowMs) return false
        lastAccepted[key] = nowMs
        return true
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 120L
    }
}
