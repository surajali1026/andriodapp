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

# Hosted emulators occasionally drop ADB while applying animation settings.
# These settings are optional; retry briefly, but do not fail a valid APK build
# solely because the emulator settings service is temporarily unavailable.
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  applied=""
  for attempt in 1 2 3; do
    if adb shell settings put global "$setting" 0 >/dev/null 2>&1; then
      applied="yes"
      break
    fi
    adb wait-for-device || true
    sleep 2
  done
  if [ "$applied" != "yes" ]; then
    echo "::warning::Could not set $setting; continuing with emulator defaults."
  fi
done

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

# Screenshot evidence is useful but must not turn a healthy APK launch into a
# false-negative when the hosted emulator's ADB transport drops intermittently.
screenshot_ok=""
for attempt in 1 2 3; do
  if adb get-state >/dev/null 2>&1 &&
     adb exec-out screencap -p > chart-smoke.png 2>/dev/null &&
     [ -s chart-smoke.png ]; then
    screenshot_ok="yes"
    break
  fi
  echo "Screenshot attempt $attempt failed; checking emulator transport..."
  sleep 3
done

if [ "$screenshot_ok" = "yes" ]; then
  echo "Screenshot evidence captured"
else
  echo "::warning::Emulator screenshot unavailable (ADB transport issue); app install, launch and crash checks completed."
  rm -f chart-smoke.png
fi

echo "Emulator app launch smoke test passed"
