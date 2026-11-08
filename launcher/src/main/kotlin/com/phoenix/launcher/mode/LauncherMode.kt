package com.phoenix.launcher.mode

/** Top-level screens of the launcher, in switcher order. */
enum class LauncherMode(val label: String) {
    AA("Android Auto"),
    RADIO("Radio"),
    PHONE("Phone"),
    SETTINGS("Settings"),
    ;

    companion object {
        fun parse(name: String?): LauncherMode? = entries.firstOrNull { it.name == name }
    }
}
