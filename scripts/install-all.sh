#!/usr/bin/env bash
# Builds debug APKs for every app module and installs them on the connected device/emulator.
# Order matters: the mock vendor service must exist before the launcher binds to it.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh

./gradlew --no-daemon :mock-syu-ms:assembleDebug :hvac-bar:assembleDebug :launcher:assembleDebug "$@"

adb install -r -g mock-syu-ms/build/outputs/apk/debug/mock-syu-ms-debug.apk
adb install -r -g hvac-bar/build/outputs/apk/debug/hvac-bar-debug.apk
adb install -r -g launcher/build/outputs/apk/debug/launcher-debug.apk

# Overlay permission for the bar; HOME role for the launcher.
adb shell appops set com.phoenix.hvacbar SYSTEM_ALERT_WINDOW allow || true
adb shell cmd package set-home-activity com.phoenix.launcher/.MainActivity || true

echo "Installed. Start the mock: adb shell am start -n com.syu.ms/.DebugActivity"
echo "Go home:        adb shell input keyevent KEYCODE_HOME"
