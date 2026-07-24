#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/../.." && pwd)"
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
emulator_bin="$sdk_dir/emulator/emulator"
adb_bin="$sdk_dir/platform-tools/adb"
avd_name="${LIFEOS_EMULATOR_AVD:-Pixel_3a_API_34_extension_level_7_arm64-v8a}"
requested_serial="${LIFEOS_ANDROID_SERIAL:-${ANDROID_SERIAL:-}}"
package_name="com.wonderfood.app"
activity="$package_name/.MainActivity"
signed_apk="$root_dir/android/app/build/outputs/apk/release/app-release.apk"
unsigned_apk="$root_dir/android/app/build/outputs/apk/release/app-release-unsigned.apk"
apk="$signed_apk"
evidence_dir="$root_dir/app/build/evidence/native-visual-matrix"
json="$evidence_dir/native-visual-matrix.json"
mkdir -p "$evidence_dir"

fail() {
  echo "Native visual matrix: FAIL ($*)" >&2
  exit 1
}

[[ -x "$emulator_bin" ]] || fail "Android emulator not found at $emulator_bin"
[[ -x "$adb_bin" ]] || fail "adb not found at $adb_bin"
if [[ ! -f "$apk" && -f "$unsigned_apk" ]]; then
  apk="$unsigned_apk"
fi
[[ -f "$apk" ]] || fail "release APK missing: $apk"

"$adb_bin" start-server >/dev/null
serial=""
if [[ -n "$requested_serial" ]]; then
  state="$("$adb_bin" -s "$requested_serial" get-state 2>/dev/null || true)"
  [[ "$state" == "device" ]] || fail "requested Android device is not connected and ready: $requested_serial"
  serial="$requested_serial"
else
  serial="$("$adb_bin" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')"
fi
if [[ -z "$serial" && -z "$requested_serial" ]]; then
  echo "Native visual matrix: starting emulator $avd_name"
  log_file="$(mktemp -t lifeos-native-visual.XXXXXX)"
  wipe_arg=()
  if [[ "${LIFEOS_EMULATOR_WIPE_DATA:-0}" == "1" ]]; then
    wipe_arg=(-wipe-data)
  fi
  "$emulator_bin" -avd "$avd_name" "${wipe_arg[@]}" -no-snapshot -no-boot-anim -no-audio -gpu swiftshader_indirect >"$log_file" 2>&1 &
  for _ in $(seq 1 240); do
    serial="$("$adb_bin" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')"
    [[ -n "$serial" ]] && break
    sleep 1
  done
fi
[[ -n "$serial" ]] || fail "no emulator became ready"
echo "Native visual matrix: using device $serial"

boot=""
for _ in $(seq 1 300); do
  boot="$("$adb_bin" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  [[ "$boot" == "1" ]] && break
  sleep 1
done
[[ "$boot" == "1" ]] || fail "emulator boot did not complete"
echo "Native visual matrix: device booted"

if [[ "$serial" == emulator-* ]]; then
  "$adb_bin" -s "$serial" uninstall "$package_name" >/dev/null 2>&1 || true
fi
echo "Native visual matrix: installing $(basename "$apk")"
install_log="$(mktemp -t lifeos-native-install.XXXXXX)"
if ! timeout 420 "$adb_bin" -s "$serial" install --no-incremental -r "$apk" >"$install_log" 2>&1; then
  fail "APK install timed out or failed on $serial: $(tail -n 8 "$install_log" | tr '\n' ' ')"
fi
echo "Native visual matrix: install complete"

routes=(
  "home|wonderfood:///|LIFEOS / HOME|Green dal + rice"
  "food|wonderfood:///food|Meal timeline|Review queue"
  "record-green-dal|wonderfood:///record/meal-green-dal|Record|Green dal + rice"
  "chat|wonderfood:///chat|Chat|Settings"
  "sources|wonderfood:///sources|LIFEOS / SOURCES|Food authority"
  "settings|wonderfood:///settings|LIFEOS / CONNECTIONS|Control center"
  "capture|wonderfood:///capture|LIFEOS / CAPTURE|Save capture"
)

ui_dump_path="/sdcard/lifeos-native-visual.xml"
passed=0
route_json=""
for entry in "${routes[@]}"; do
  IFS='|' read -r name uri label_one label_two <<<"$entry"
  echo "Native visual matrix: checking $name"
  "$adb_bin" -s "$serial" shell am force-stop "$package_name" >/dev/null 2>&1 || true
  "$adb_bin" -s "$serial" shell am start -a android.intent.action.VIEW -d "$uri" -n "$activity" >/dev/null
  if [[ "$name" == "chat" ]]; then
    sleep 2
    for _ in 1 2 3 4; do
      "$adb_bin" -s "$serial" shell input swipe 540 1750 540 350 500 >/dev/null 2>&1 || true
    done
  fi
  dump=""
  for attempt in $(seq 1 10); do
    timeout 8 "$adb_bin" -s "$serial" shell uiautomator dump --compressed "$ui_dump_path" >/dev/null 2>&1 || true
    dump="$(timeout 8 "$adb_bin" -s "$serial" shell cat "$ui_dump_path" 2>/dev/null | tr -d '\r' || true)"
    if grep -q "$label_one" <<<"$dump" && grep -q "$label_two" <<<"$dump"; then
      break
    fi
    echo "Native visual matrix: waiting for $name labels ($attempt/10)"
    sleep 1
  done
  if grep -Eq "isn.t responding|Application Not Responding|aerr_close|aerr_wait" <<<"$dump"; then
    fail "ANR visible on $name"
  fi
  screenshot="$evidence_dir/$name.png"
  dump_file="$evidence_dir/$name.xml"
  timeout 20 "$adb_bin" -s "$serial" exec-out screencap -p >"$screenshot" || fail "$name screenshot timed out"
  printf '%s\n' "$dump" >"$dump_file"
  grep -q "$label_one" <<<"$dump" || fail "$name missing label: $label_one"
  grep -q "$label_two" <<<"$dump" || fail "$name missing label: $label_two"
  passed=$((passed + 1))
  route_json="$route_json
    { \"name\": \"$name\", \"uri\": \"$uri\", \"screenshot\": \"app/build/evidence/native-visual-matrix/$name.png\", \"ui_dump\": \"app/build/evidence/native-visual-matrix/$name.xml\", \"labels\": [\"$label_one\", \"$label_two\"] },"
done

git_head="$(git -C "$root_dir" rev-parse --short HEAD 2>/dev/null || echo unknown)"
git_tree="$(git -C "$root_dir" rev-parse HEAD^{tree} 2>/dev/null || echo unknown)"
git_branch="$(git -C "$root_dir" branch --show-current 2>/dev/null || echo unknown)"
dirty_status="$(git -C "$root_dir" status --porcelain=v1 2>/dev/null || true)"
dirty="false"; [[ -n "$dirty_status" ]] && dirty="true"
dirty_diff_hash="$(node --input-type=module -e "import { currentDirtyDiffHash } from './scripts/quality/evidence-provenance.mjs'; process.stdout.write(currentDirtyDiffHash(process.cwd()));")"
checked_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
route_json="${route_json%,}"
cat >"$json" <<JSON
{
  "proof": "lifeos_native_visual_matrix",
  "checked_at": "$checked_at",
  "git": { "branch": "$git_branch", "head": "$git_head", "tree": "$git_tree", "dirty": $dirty, "dirty_diff_hash": "$dirty_diff_hash" },
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
