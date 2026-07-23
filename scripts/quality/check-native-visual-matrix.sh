#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/../.." && pwd)"
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
emulator_bin="$sdk_dir/emulator/emulator"
adb_bin="$sdk_dir/platform-tools/adb"
avd_name="${LIFEOS_EMULATOR_AVD:-Pixel_3a_API_34_extension_level_7_arm64-v8a}"
package_name="com.wonderfood.app"
activity="$package_name/.MainActivity"
apk="$root_dir/android/app/build/outputs/apk/release/app-release.apk"
evidence_dir="$root_dir/app/build/evidence/native-visual-matrix"
json="$evidence_dir/native-visual-matrix.json"
mkdir -p "$evidence_dir"

fail() {
  echo "Native visual matrix: FAIL ($*)" >&2
  exit 1
}

[[ -x "$emulator_bin" ]] || fail "Android emulator not found at $emulator_bin"
[[ -x "$adb_bin" ]] || fail "adb not found at $adb_bin"
[[ -f "$apk" ]] || fail "release APK missing: $apk"

"$adb_bin" start-server >/dev/null
serial="$("$adb_bin" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')"
if [[ -z "$serial" ]]; then
  log_file="$(mktemp -t lifeos-native-visual.XXXXXX)"
  "$emulator_bin" -avd "$avd_name" -no-snapshot -no-boot-anim -no-audio -gpu swiftshader_indirect >"$log_file" 2>&1 &
  for _ in $(seq 1 120); do
    serial="$("$adb_bin" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')"
    [[ -n "$serial" ]] && break
    sleep 1
  done
fi
[[ -n "$serial" ]] || fail "no emulator became ready"

boot=""
for _ in $(seq 1 120); do
  boot="$("$adb_bin" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  [[ "$boot" == "1" ]] && break
  sleep 1
done
[[ "$boot" == "1" ]] || fail "emulator boot did not complete"

"$adb_bin" -s "$serial" install -r "$apk" >/dev/null

routes=(
  "home|wonderfood:///|LIFEOS / HOME|Green dal + rice"
  "food|wonderfood:///food|LIFEOS / FOOD|Green dal + rice"
  "record-green-dal|wonderfood:///record/meal-green-dal|Record|Green dal + rice"
  "chat|wonderfood:///chat|Ask about food records|Send"
  "sources|wonderfood:///sources|LIFEOS / SOURCES|Food authority"
  "settings|wonderfood:///settings|LIFEOS / CONNECTIONS|Control center"
  "capture|wonderfood:///capture|LIFEOS / CAPTURE|Save capture"
)

ui_dump_path="/sdcard/lifeos-native-visual.xml"
passed=0
route_json=""
for entry in "${routes[@]}"; do
  IFS='|' read -r name uri label_one label_two <<<"$entry"
  "$adb_bin" -s "$serial" shell am force-stop "$package_name" >/dev/null 2>&1 || true
  "$adb_bin" -s "$serial" shell am start -a android.intent.action.VIEW -d "$uri" -n "$activity" >/dev/null
  dump=""
  for _ in $(seq 1 25); do
    "$adb_bin" -s "$serial" shell uiautomator dump "$ui_dump_path" >/dev/null 2>&1 || true
    dump="$("$adb_bin" -s "$serial" shell cat "$ui_dump_path" 2>/dev/null | tr -d '\r' || true)"
    if grep -q "$label_one" <<<"$dump" && grep -q "$label_two" <<<"$dump"; then
      break
    fi
    sleep 1
  done
  if grep -Eq "isn.t responding|Application Not Responding|aerr_close|aerr_wait" <<<"$dump"; then
    fail "ANR visible on $name"
  fi
  screenshot="$evidence_dir/$name.png"
  dump_file="$evidence_dir/$name.xml"
  "$adb_bin" -s "$serial" exec-out screencap -p >"$screenshot"
  printf '%s\n' "$dump" >"$dump_file"
  grep -q "$label_one" <<<"$dump" || fail "$name missing label: $label_one"
  grep -q "$label_two" <<<"$dump" || fail "$name missing label: $label_two"
  passed=$((passed + 1))
  route_json="$route_json
    { \"name\": \"$name\", \"uri\": \"$uri\", \"screenshot\": \"app/build/evidence/native-visual-matrix/$name.png\", \"ui_dump\": \"app/build/evidence/native-visual-matrix/$name.xml\", \"labels\": [\"$label_one\", \"$label_two\"] },"
done

git_head="$(git -C "$root_dir" rev-parse --short HEAD 2>/dev/null || echo unknown)"
checked_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
route_json="${route_json%,}"
cat >"$json" <<JSON
{
  "proof": "lifeos_native_visual_matrix",
  "checked_at": "$checked_at",
  "git_head": "$git_head",
  "avd": "$avd_name",
  "serial": "$serial",
  "package": "$package_name",
  "status": "passed",
  "routes_checked": $passed,
  "routes_required": ${#routes[@]},
  "routes": [
$route_json
  ]
}
JSON

echo "Native visual matrix: PASS ($passed/${#routes[@]} routes; evidence: $json)"
