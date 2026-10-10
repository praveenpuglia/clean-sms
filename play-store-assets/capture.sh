#!/usr/bin/env bash
# Capture the raw screens for the Play Store images from a running emulator (Pixel, 1080x2424).
# Uses the debug demo seed: fictional people and brands only, so no real company appears.
# Wipes the emulator's SMS store (the demo seed refuses to on real devices).
#
# Usage: ./gradlew installDebug && play-store-assets/capture.sh   then   play-store-assets/render.sh
set -euo pipefail
cd "$(dirname "$0")"
PKG=com.praveenpuglia.cleansms
RAW=raw

shot() { sleep "${2:-1.5}"; adb exec-out screencap -p > "$RAW/$1.png"; }
# Tap the first on-screen node whose text or content description is exactly $1.
tap() {
  local xy="" try
  for try in 1 2 3 4 5; do # the dump fails while the screen is still animating
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
    xy=$(adb exec-out cat /sdcard/ui.xml | python3 -c '
import re, sys
t = sys.argv[1]
for n in re.findall(r"<node [^>]*>", sys.stdin.read()):
    if f"text=\"{t}\"" in n or f"content-desc=\"{t}\"" in n:
        a, b, c, d = map(int, re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", n).groups())
        print((a + c) // 2, (b + d) // 2); break' "$1")
    [ -n "$xy" ] && break || sleep 1
  done
  [ -n "$xy" ] || { echo "No \"$1\" on screen" >&2; exit 1; }
  adb shell input tap $xy
}
launch() { adb shell am force-stop $PKG; adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 4; }
demo_bar() {
  local dm="adb shell am broadcast -a com.android.systemui.demo -e command"
  adb shell settings put global sysui_demo_allowed 1
  $dm enter >/dev/null; $dm clock -e hhmm "$(date +%H%M)" >/dev/null
  $dm battery -e level 100 -e plugged false >/dev/null; $dm network -e wifi show -e level 4 >/dev/null
  $dm network -e mobile hide >/dev/null; $dm notifications -e visible false >/dev/null
}

adb shell cmd role add-role-holder android.app.role.SMS $PKG 0
adb shell settings put secure stylus_handwriting_enabled 0
adb shell am broadcast -n $PKG/.DebugSeedReceiver -a $PKG.DEBUG_SEED --ez demo true >/dev/null
sleep 3
demo_bar

for mode in light dark; do
  adb shell cmd uimode night $([ $mode = dark ] && echo yes || echo no) >/dev/null
  launch; shot "$mode-otps"
  for tab in Personal Transactions Services; do tap $tab; shot "$mode-$(echo $tab | tr A-Z a-z)"; done
  launch; tap Personal; sleep 1.5; tap Ananya; shot "$mode-thread" 2.5
done

adb shell cmd uimode night no >/dev/null
launch; tap "Search messages"; sleep 1.5; adb shell input text flihgt; shot search 2
adb shell input keyevent HOME
adb emu sms send VM-HRBRBK-T "391742 is your OTP to log in to Harbor Bank NetBanking. Valid for 5 minutes. Never share it with anyone." >/dev/null
sleep 4; adb shell cmd statusbar expand-notifications; sleep 2
adb shell input tap 960 712; shot notification  # expand the OTP notification
adb shell cmd statusbar collapse
echo "Raw screens in $(pwd)/$RAW"
