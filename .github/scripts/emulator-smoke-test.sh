#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.tradingview.binancefutures"
APK="android/app/build/outputs/apk/debug/app-debug.apk"

echo "Waiting for emulator device..."
adb wait-for-device

echo "Waiting for Android boot completion..."
booted=""
for i in $(seq 1 90); do
  booted="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [ "$booted" = "1" ]; then
    break
  fi
  sleep 2
done
if [ "$booted" != "1" ]; then
  echo "Emulator did not finish booting"
  adb logcat -d -t 500 || true
  exit 1
fi

adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

test -s "$APK"
adb install -r "$APK"
adb logcat -c
adb shell am force-stop "$PACKAGE" || true
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1

echo "Waiting for app process..."
app_started=""
for i in $(seq 1 45); do
  app_started="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
  if [ -n "$app_started" ]; then
    break
  fi
  sleep 2
done

if [ -z "$app_started" ]; then
  echo "App process did not start"
  adb shell dumpsys activity activities || true
  adb logcat -d -v brief > android-emulator-logcat.txt
  exit 1
fi

adb shell dumpsys activity activities | grep -F "$PACKAGE" || true
sleep 10
adb logcat -d -v brief > android-emulator-logcat.txt
if grep -A 30 'FATAL EXCEPTION' android-emulator-logcat.txt | grep -F "$PACKAGE"; then
  echo "App crashed during emulator launch"
  exit 1
fi

adb shell screencap -p /sdcard/chart-smoke.png
adb pull /sdcard/chart-smoke.png chart-smoke.png
test -s chart-smoke.png
echo "Emulator launch smoke test passed"
