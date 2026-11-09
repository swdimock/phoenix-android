package com.phoenix.launcher.host

/**
 * Decides the one automatic receiver launch per launcher process (boot, or wake after `com.syu.ms` force-stopped
 * us): only when the first activity start landed in the AA mode, and only once the audio path is ready (or its
 * wait timed out). Lives in the application, not the activity: Android may create a fresh HOME activity when
 * the user returns home, and that must not launch the receiver again.
 */
class AaAutoLaunchGate {
    private var used = false
    private var armed = false

    /** Arms the gate for a fresh activity start in AA. Only the first call per process has any effect. */
    fun arm() {
        if (used) return
        used = true
        armed = true
    }

    /** True exactly once, when armed and every condition holds; then disarms. */
    fun shouldLaunch(currentModeIsAa: Boolean, audioReady: Boolean, installed: Boolean): Boolean {
        if (!armed) return false
        if (!currentModeIsAa || !installed) {
            armed = false
            return false
        }
        if (!audioReady) return false
        armed = false
        return true
    }
}
