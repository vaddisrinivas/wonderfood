#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/../.." && pwd)"
apk="$root_dir/android/app/build/outputs/apk/release/app-release.apk"
aab="$root_dir/android/app/build/outputs/bundle/release/app-release.aab"
evidence_dir="$root_dir/app/build/evidence"
evidence="$evidence_dir/android-release-artifacts.json"
mkdir -p "$evidence_dir"

fail() {
  echo "Android release artifact check: FAIL ($*)" >&2
  exit 1
}

[[ -f "$apk" ]] || fail "release APK missing: $apk"
[[ -f "$aab" ]] || fail "release AAB missing: $aab"

aapt_path="${ANDROID_BUILD_TOOLS_AAPT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/build-tools/36.0.0/aapt}"
[[ -x "$aapt_path" ]] || fail "aapt not found at $aapt_path"

badging="$("$aapt_path" dump badging "$apk")"
grep -q "package: name='com.wonderfood.app'" <<<"$badging" || fail "APK package mismatch"
grep -q "versionName='1.0.0'" <<<"$badging" || fail "APK versionName mismatch"
grep -q "sdkVersion:'26'" <<<"$badging" || fail "APK minSdk mismatch"
grep -q "targetSdkVersion:'36'" <<<"$badging" || fail "APK targetSdk mismatch"

permissions="$("$aapt_path" dump permissions "$apk")"
for permission in READ_NUTRITION READ_HYDRATION READ_STEPS READ_ACTIVE_CALORIES_BURNED READ_WEIGHT WRITE_HYDRATION; do
  grep -q "android.permission.health.${permission}" <<<"$permissions" || fail "missing Health Connect permission $permission"
done

zip_listing="$(unzip -l "$aab")"
for entry in base/manifest/AndroidManifest.xml base/assets/index.android.bundle base/assets/app.config base/dex/classes.dex BundleConfig.pb; do
  grep -q "$entry" <<<"$zip_listing" || fail "AAB missing $entry"
done

apksigner_path="$(find "${ANDROID_HOME:-$HOME/Library/Android/sdk}/build-tools" -type f -name apksigner 2>/dev/null | sort -V | tail -n 1 || true)"
[[ -x "$apksigner_path" ]] || fail "apksigner not found under Android SDK build-tools"
apk_signing="$("$apksigner_path" verify --verbose --print-certs "$apk" 2>&1 || true)"
grep -q '^Verifies$' <<<"$apk_signing" || fail "APK signature verification failed"
apk_cert_dn="$(sed -n 's/^Signer #1 certificate DN: //p' <<<"$apk_signing" | head -n 1)"
apk_cert_sha256="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<<"$apk_signing" | head -n 1)"
apk_signing_kind="release"
if grep -q 'CN=Android Debug' <<<"$apk_cert_dn"; then
  apk_signing_kind="debug"
fi

aab_signed="true"
aab_signing="$(jarsigner -verify -verbose -certs "$aab" 2>&1 || true)"
if grep -q 'jar is unsigned' <<<"$aab_signing"; then
  aab_signed="false"
fi

if [[ "${REQUIRE_RELEASE_SIGNING:-0}" == "1" ]]; then
  [[ "$apk_signing_kind" == "release" ]] || fail "APK is debug-signed"
  [[ "$aab_signed" == "true" ]] || fail "AAB is unsigned"
fi

apk_sha="$(shasum -a 256 "$apk" | awk '{print $1}')"
aab_sha="$(shasum -a 256 "$aab" | awk '{print $1}')"
apk_size="$(stat -f%z "$apk")"
aab_size="$(stat -f%z "$aab")"
git_head="$(git -C "$root_dir" rev-parse --short HEAD 2>/dev/null || echo unknown)"
checked_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

cat > "$evidence" <<JSON
{
  "status": "passed",
  "checked_at": "$checked_at",
  "git_head": "$git_head",
  "package": "com.wonderfood.app",
  "version_name": "1.0.0",
  "version_code": 1,
  "min_sdk": 26,
  "target_sdk": 36,
  "apk": {
    "path": "android/app/build/outputs/apk/release/app-release.apk",
    "bytes": $apk_size,
    "sha256": "$apk_sha",
    "signing": "$apk_signing_kind",
    "certificate_dn": "$apk_cert_dn",
    "certificate_sha256": "$apk_cert_sha256"
  },
  "aab": {
    "path": "android/app/build/outputs/bundle/release/app-release.aab",
    "bytes": $aab_size,
    "sha256": "$aab_sha",
    "signed": $aab_signed,
    "contains": [
      "base/manifest/AndroidManifest.xml",
      "base/assets/index.android.bundle",
      "base/assets/app.config",
      "base/dex/classes.dex",
      "BundleConfig.pb"
    ]
  },
  "health_connect_permissions": [
    "READ_NUTRITION",
    "READ_HYDRATION",
    "READ_STEPS",
    "READ_ACTIVE_CALORIES_BURNED",
    "READ_WEIGHT",
    "WRITE_HYDRATION"
  ]
}
JSON

echo "Android release artifact check: PASS (APK + AAB + Health Connect permissions; apk_signing=$apk_signing_kind; aab_signed=$aab_signed; evidence: $evidence)"
