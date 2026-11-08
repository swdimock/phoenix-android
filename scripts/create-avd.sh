#!/usr/bin/env bash
# Creates (or recreates) the head-unit emulator profile.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh

AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager.bat"
if [ -d "$ANDROID_AVD_HOME/$PHX_AVD_NAME.avd" ]; then
  echo "AVD $PHX_AVD_NAME exists; deleting"
  "$AVDMANAGER" delete avd -n "$PHX_AVD_NAME" || true
fi

echo no | "$AVDMANAGER" create avd -n "$PHX_AVD_NAME" -k "$PHX_SYSIMG" -d "pixel_c" --force

CFG="$ANDROID_AVD_HOME/$PHX_AVD_NAME.avd/config.ini"
set_cfg() { # key value
  if grep -q "^$1=" "$CFG"; then sed -i "s|^$1=.*|$1=$2|" "$CFG"; else echo "$1=$2" >> "$CFG"; fi
}
set_cfg hw.lcd.width "$PHX_LCD_W"
set_cfg hw.lcd.height "$PHX_LCD_H"
set_cfg hw.lcd.density "$PHX_LCD_DPI"
set_cfg hw.initialOrientation portrait
set_cfg hw.keyboard yes
set_cfg hw.gpu.enabled yes
set_cfg hw.gpu.mode auto
set_cfg hw.ramSize 3072
set_cfg vm.heapSize 512
set_cfg disk.dataPartition.size 4G
set_cfg hw.audioInput yes
set_cfg hw.audioOutput yes
set_cfg hw.sensors.orientation no
set_cfg hw.mainKeys no
set_cfg skin.dynamic yes
set_cfg skin.name "${PHX_LCD_W}x${PHX_LCD_H}"
set_cfg skin.path _no_skin
set_cfg showDeviceFrame no

echo "Created $PHX_AVD_NAME (${PHX_LCD_W}x${PHX_LCD_H} @ ${PHX_LCD_DPI}dpi, $PHX_SYSIMG)"
