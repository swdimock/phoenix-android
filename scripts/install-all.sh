#!/usr/bin/env bash
# Builds debug APKs for every app module and installs them on the connected device/emulator.
# Order matters: the mock vendor service must exist before the launcher binds to it.
# With a phone also attached, pick the target: ANDROID_SERIAL=emulator-5554 bash scripts/install-all.sh
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh

./gradlew --no-daemon :mock-syu-ms:assembleDebug :hvac-bar:assembleDebug :launcher:assembleDebug "$@"

adb install -r -g mock-syu-ms/build/outputs/apk/debug/mock-syu-ms-debug.apk
adb install -r -g hvac-bar/build/outputs/apk/debug/hvac-bar-debug.apk
adb install -r -g launcher/build/outputs/apk/debug/launcher-debug.apk

# Overlay permission for the bar.
adb shell appops set com.phoenix.hvacbar SYSTEM_ALERT_WINDOW allow || true

# Android Auto receiver (Open Headunit fork, built by scripts/build-receiver.sh). Optional.
RECEIVER_APK=build/receiver/aa-receiver-debug.apk
RECEIVER_PKG=com.andrerinas.headunitrevived
if [ -f "$RECEIVER_APK" ]; then
  adb install -r -g "$RECEIVER_APK"
  # -g covers runtime permissions; overlay and battery exemption are app-ops / whitelist, not runtime.
  adb shell appops set "$RECEIVER_PKG" SYSTEM_ALERT_WINDOW allow || true
  adb shell dumpsys deviceidle whitelist +"$RECEIVER_PKG" >/dev/null 2>&1 || true
  echo "Installed AA receiver ($RECEIVER_PKG)"
  # Emulator only: make the receiver's 127.0.0.1:5277 reach the PC, where `adb -s <phone> forward tcp:5277 tcp:5277`
  # exposes the phone's head unit server (see docs/ANDROID-AUTO-EMULATOR.md). Survives until the emulator restarts.
  if adb get-serialno | grep -q '^emulator-'; then
    adb reverse tcp:5277 tcp:5277 >/dev/null && echo "adb reverse tcp:5277 -> PC set (TCP Android Auto)"
  fi
else
  echo "No $RECEIVER_APK (run scripts/build-receiver.sh); the AA pane will show the not-installed placeholder"
fi

# HOME role for the launcher. The emulator's role service is flaky (set-home-activity can fail and
# add-role-holder can time out), so try both and fall back to starting the activity directly.
HOME_SET=0
if adb shell cmd package set-home-activity com.phoenix.launcher/.MainActivity 2>&1 | grep -qi success; then HOME_SET=1; fi
if [ "$HOME_SET" = 0 ]; then
  timeout 20 adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.phoenix.launcher >/dev/null 2>&1 && HOME_SET=1 || true
fi
if [ "$HOME_SET" = 1 ]; then
  echo "Launcher set as HOME"
else
  echo "Could not set HOME role on this image; starting launcher directly"
fi

# Bring up the stack: mock vendor service first, then bar, then launcher.
adb shell monkey -p com.syu.ms -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
sleep 2
adb shell am broadcast -a com.phoenix.hvacbar.action.SHOW >/dev/null 2>&1 || true
adb shell am start -n com.phoenix.launcher/.MainActivity >/dev/null 2>&1 || true

echo "Installed. Launcher: adb shell am start -n com.phoenix.launcher/.MainActivity"
echo "Mock debug UI:      adb shell monkey -p com.syu.ms -c android.intent.category.LAUNCHER 1"
