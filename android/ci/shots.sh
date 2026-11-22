#!/usr/bin/env bash
# Takes emulator screenshots of the release APK (run inside reactivecircus/android-emulator-runner).
set -x
APK=${1:-Atlas.apk}
OUT=${2:-shots}
mkdir -p "$OUT"
adb install -r "$APK"
adb shell settings put system screen_off_timeout 600000 || true
adb shell cmd uimode night yes || true
shot() {
  name=$1; shift
  adb shell am force-stop dev.huichen.atlas
  adb shell am start -W -n dev.huichen.atlas/.MainActivity "$@"
  sleep 7
  adb exec-out screencap -p > "$OUT/$name.png"
}
shot 1-wallet --ei atlasTab 0 --es atlasTheme dark
shot 2-wallet-light --ei atlasTab 0 --es atlasTheme light
shot 3-sites --ei atlasTab 1 --es atlasTheme dark
shot 4-map --ei atlasTab 2 --es atlasTheme dark
shot 5-identity --es atlasRoute identity --es atlasTheme dark
shot 6-picker --es atlasRoute picker --es atlasTheme dark
shot 7-settings --es atlasRoute settings --es atlasTheme dark
shot 8-new --es atlasRoute new --es atlasTheme dark
adb logcat -d -s AndroidRuntime:E > "$OUT/crash.log" || true
ls -la "$OUT"
