#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/../.." && pwd)"
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
emulator_bin="$sdk_dir/emulator/emulator"
adb_bin="$sdk_dir/platform-tools/adb"
avd_name="${LIFEOS_EMULATOR_AVD:-Pixel_3a_API_34_extension_level_7_arm64-v8a}"
package_name="com.wonderfood.app"
apk="$root_dir/android/app/build/outputs/apk/release/app-release.apk"
evidence_dir="$root_dir/app/build/evidence"
mkdir -p "$evidence_dir"

if [[ ! -x "$emulator_bin" || ! -x "$adb_bin" ]]; then
  echo "emulatorx check: Android SDK emulator/adb not found" >&2
  exit 1
fi
if [[ ! -f "$apk" ]]; then
  echo "emulatorx check: release APK missing: $apk" >&2
  exit 1
fi

"$adb_bin" start-server >/dev/null
serial="$("$adb_bin" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')"
if [[ -z "$serial" ]]; then
  log_file="$(mktemp /tmp/lifeos-emulatorx.XXXXXX.log)"
  "$emulator_bin" -avd "$avd_name" -no-snapshot -no-boot-anim -no-audio -gpu swiftshader_indirect >"$log_file" 2>&1 &
  for _ in $(seq 1 120); do
    serial="$("$adb_bin" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')"
    [[ -n "$serial" ]] && break
    sleep 1
  done
fi
if [[ -z "$serial" ]]; then
  echo "emulatorx check: no emulator device became ready" >&2
  exit 1
fi

for _ in $(seq 1 120); do
  boot="$("$adb_bin" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  [[ "$boot" == "1" ]] && break
  sleep 1
done
[[ "$boot" == "1" ]] || { echo "emulatorx check: boot did not complete" >&2; exit 1; }

"$adb_bin" -s "$serial" install -r "$apk" >/dev/null
for permission in READ_NUTRITION READ_HYDRATION READ_STEPS READ_ACTIVE_CALORIES_BURNED READ_WEIGHT; do
  "$adb_bin" -s "$serial" shell pm grant "$package_name" "android.permission.health.$permission"
done

"$adb_bin" -s "$serial" shell monkey -p "$package_name" 1 >/dev/null
sleep 2
"$adb_bin" -s "$serial" shell am start -a android.intent.action.VIEW -d wonderfood://health-connect >/dev/null
sleep 2
"$adb_bin" -s "$serial" exec-out screencap -p >"$evidence_dir/emulatorx-healthconnect-script.png"

grants="$("$adb_bin" -s "$serial" shell dumpsys package "$package_name" | grep -E 'android.permission.health.(READ_NUTRITION|READ_HYDRATION|READ_STEPS|READ_ACTIVE_CALORIES_BURNED|READ_WEIGHT): granted=true' | tr -d '\r' || true)"
for permission in READ_NUTRITION READ_HYDRATION READ_STEPS READ_ACTIVE_CALORIES_BURNED READ_WEIGHT; do
  grep -q "android.permission.health.$permission: granted=true" <<<"$grants" || {
    echo "emulatorx check: missing granted scope $permission" >&2
    exit 1
  }
done

printf '{\n  "avd": "%s",\n  "serial": "%s",\n  "package": "%s",\n  "permissions_granted": true,\n  "deep_link": "wonderfood://health-connect",\n  "screenshot": "%s"\n}\n' \
  "$avd_name" "$serial" "$package_name" "$evidence_dir/emulatorx-healthconnect-script.png" \
  >"$evidence_dir/emulatorx-healthconnect-script.json"

echo "emulatorx Health Connect script: PASS ($serial; API 34 permissions + deep link)"
