#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/../.." && pwd)"
manifest="$root_dir/android/app/src/main/AndroidManifest.xml"
apk="$root_dir/android/app/build/outputs/apk/release/app-release.apk"

for permission in READ_NUTRITION READ_HYDRATION READ_STEPS READ_ACTIVE_CALORIES_BURNED READ_WEIGHT; do
  grep -q "android.permission.health.${permission}" "$manifest"
done
grep -q 'com.google.android.apps.healthdata' "$manifest"
grep -q 'com.google.android.healthconnect.controller' "$manifest"
grep -q 'react-native-health-connect' "$root_dir/package.json"
grep -q 'HealthConnectPermissionDelegate.setPermissionDelegate' "$root_dir/android/app/src/main/java/com/wonderfood/app/MainActivity.kt"
test -f "$root_dir/src/health/connect.ts"
grep -q "health/connect/snapshot" "$root_dir/server/src/index.ts"
grep -q "saveHealthSnapshot" "$root_dir/server/src/health/snapshots.ts"

if [[ ! -f "$apk" ]]; then
  echo "Health Connect APK check: FAIL (release APK missing; run npm run android:release)" >&2
  exit 1
fi

aapt_path="${ANDROID_BUILD_TOOLS_AAPT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/build-tools/36.0.0/aapt}"
if [[ ! -x "$aapt_path" ]]; then
  echo "Health Connect APK check: FAIL (aapt not found at $aapt_path)" >&2
  exit 1
fi

permissions="$($aapt_path dump permissions "$apk")"
for permission in READ_NUTRITION READ_HYDRATION READ_STEPS READ_ACTIVE_CALORIES_BURNED READ_WEIGHT; do
  grep -q "android.permission.health.${permission}" <<<"$permissions"
done

echo "Health Connect check: PASS (manifest, JS bridge, permission delegate, snapshot sync, and release APK)"
