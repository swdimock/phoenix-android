#!/usr/bin/env bash
# Boots the head-unit emulator and waits for it. Headless with -no-window when HEADLESS=1.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh

EXTRA=()
if [ "${HEADLESS:-0}" = "1" ]; then EXTRA+=(-no-window -no-audio); fi

if adb devices | grep -q "emulator-"; then
  echo "An emulator is already running"
else
  nohup "$ANDROID_HOME/emulator/emulator.exe" -avd "$PHX_AVD_NAME" -accel on -gpu auto -no-snapshot-save -no-boot-anim \
    "${EXTRA[@]}" > "${TMPDIR:-/tmp}/phoenix-emulator.log" 2>&1 &
  echo "Emulator starting (log: ${TMPDIR:-/tmp}/phoenix-emulator.log)"
fi

adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
echo "Booted: $(adb shell getprop ro.build.version.release | tr -d '\r') / sdk $(adb shell getprop ro.build.version.sdk | tr -d '\r') / $(adb shell wm size | tr -d '\r')"
