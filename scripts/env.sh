#!/usr/bin/env bash
# Source this: `source scripts/env.sh`
# Local toolchain lives outside the repo (user-scope install, no admin required).
export JAVA_HOME="${JAVA_HOME:-/c/Users/Stephen/android-tools/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-/c/Users/Stephen/android-tools/sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

# Emulator profile matching the Phoenix Tesla-style portrait panel.
export PHX_AVD_NAME="${PHX_AVD_NAME:-phoenix_f150}"
export PHX_SYSIMG="${PHX_SYSIMG:-system-images;android-31;google_apis;x86_64}"
export PHX_LCD_W="${PHX_LCD_W:-1200}"
export PHX_LCD_H="${PHX_LCD_H:-1600}"
export PHX_LCD_DPI="${PHX_LCD_DPI:-160}"
