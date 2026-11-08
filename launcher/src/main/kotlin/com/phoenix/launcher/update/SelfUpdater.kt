package com.phoenix.launcher.update

/** Checks for, downloads and hands a newer launcher APK to the system installer. */
interface SelfUpdater {
    /** False when no update source is configured. */
    val isEnabled: Boolean

    suspend fun check(): UpdateCheck

    /** Downloads [update] and launches the package installer. Returns an error message, or null on success. */
    suspend fun downloadAndInstall(update: UpdateCheck.Available): String?
}

/** Result of [SelfUpdater.check]. */
sealed interface UpdateCheck {
    data object Disabled : UpdateCheck
    data class UpToDate(val latestTag: String) : UpdateCheck
    data class Available(val tag: String, val apkUrl: String) : UpdateCheck
    data class Error(val message: String) : UpdateCheck
}

/**
 * True when release [tag] (e.g. `v0.2.0`) is newer than [current] (e.g. `0.1.0`). Compares dot-separated
 * numeric components; non-numeric suffixes such as `-beta` are ignored.
 */
fun isNewerVersion(tag: String, current: String): Boolean {
    fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
        .substringBefore('-').substringBefore('+')
        .split('.').map { it.toIntOrNull() ?: 0 }
    val a = parts(tag)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
