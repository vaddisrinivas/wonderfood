#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

STAMP="$(date +"%Y%m%d-%H%M%S")"
OUT_DIR="${1:-build/evidence/release-device-triage-$STAMP}"
MATRIX="${MATRIX:-.planning/2026-07-20-wonderfood-105-accelerated-release-campa/acceptance_matrix.md}"
mkdir -p "$OUT_DIR"

report="$OUT_DIR/release-device-row-triage.md"
gradle_file="android/app/build.gradle"
if [[ ! -f "$gradle_file" && -f "app/build.gradle.kts" ]]; then
  gradle_file="app/build.gradle.kts"
fi

row_status() {
  local id="$1"
  awk -F'|' -v id="$id" '
    $2 ~ (" " id " ") {
      gsub(/^[ \t]+|[ \t]+$/, "", $6)
      print $6
      found=1
      exit
    }
    END { if (!found) print "MISSING" }
  ' "$MATRIX"
}

record_command() {
  local name="$1"
  shift
  {
    echo "## $name"
    echo '```'
    "$@" 2>&1 || true
    echo '```'
    echo
  } >> "$report"
}

{
  echo "# WonderFood Release/Device Row Triage"
  echo
  echo "- Generated: $STAMP"
  echo "- Scope: E01, E07, E09, E12, E13, E14, E15, E16"
  echo "- Mode: read-only, no signing secrets, no install, no PR/tag/release creation"
  echo "- Matrix: $MATRIX"
  echo
  echo "## Row Status From Matrix"
  echo
  echo "| Row | Matrix status | Triage meaning |"
  echo "|---|---|---|"
  for row in E01 E07 E09 E12 E13 E14 E15 E16; do
    status="$(row_status "$row")"
    case "$row:$status" in
      E01:PASS) meaning="matrix says complete; re-run full stop-condition scan before final claim" ;;
      E01:*) meaning="remaining matrix TODO/BLOCKED rows prevent final acceptance" ;;
      E07:PASS) meaning="connected low/current API proof recorded in matrix" ;;
      E07:*) meaning="needs connected supported low and current API runs" ;;
      E09:PASS) meaning="physical install/launch/restart/preserve proof recorded in matrix" ;;
      E09:*) meaning="needs physical phone release-candidate proof" ;;
      E12:PASS) meaning="CI pass recorded in matrix" ;;
      E12:*) meaning="needs final commit CI pass, read-only status below if available" ;;
      E13:PASS) meaning="signed APKs and checksums recorded in matrix" ;;
      E13:*) meaning="needs signing-owner build outside this no-secret triage" ;;
      E14:PASS) meaning="final PR reviewed/merged recorded in matrix" ;;
      E14:*) meaning="needs PR creation/review/merge after proof; this script does not create it" ;;
      E15:PASS) meaning="tag/release/APKs/checksums/notes recorded in matrix" ;;
      E15:*) meaning="needs tag/release publication after signed artifacts; this script does not create it" ;;
      E16:PASS) meaning="install URL/artifact verification recorded in matrix" ;;
      E16:*) meaning="needs downloadable artifact/install URL verification" ;;
    esac
    echo "| $row | $status | $meaning |"
  done
  echo
  echo "## Local Signals"
  echo
  echo "- Git head: $(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
  echo "- Branch: $(git branch --show-current 2>/dev/null || echo unknown)"
  echo "- Dirty files: $(git status --short 2>/dev/null | wc -l | tr -d ' ')"
  echo "- Version file: $gradle_file"
  echo "- Version name: $(sed -n 's/^[[:space:]]*versionName[[:space:]=]*(\{0,1\}"\([^"]*\)".*/\1/p' "$gradle_file" | head -n 1)"
  echo "- Version code: $(sed -n 's/^[[:space:]]*versionCode[[:space:]=]*(\{0,1\}\([0-9][0-9]*\).*/\1/p' "$gradle_file" | head -n 1)"
  echo
} > "$report"

record_command "Scoped Git Status" git status --short -- scripts/quality docs/release .planning/2026-07-20-wonderfood-105-accelerated-release-campa

if command -v adb >/dev/null 2>&1; then
  record_command "ADB Devices" adb devices -l
else
  {
    echo "## ADB Devices"
    echo
    echo "adb not found on PATH."
    echo
  } >> "$report"
fi

record_command "Connected Proof Tasks" printf '%s\n' \
  './gradlew :app:connectedFossDebugAndroidTest' \
  './gradlew :app:connectedPlayDebugAndroidTest' \
  './gradlew :core:data:connectedDebugAndroidTest'

apk_roots=()
for dir in android/app/build/outputs/apk app/build/outputs/apk; do
  [[ -d "$dir" ]] && apk_roots+=("$dir")
done
if [[ "${#apk_roots[@]}" -gt 0 ]]; then
  record_command "Release APK Candidates" find "${apk_roots[@]}" -maxdepth 6 -path '*/release/*.apk' -type f
else
  record_command "Release APK Candidates" printf '%s\n' "none"
fi
record_command "Existing Checksum Files" find build app/build \( -name 'SHA256SUMS.txt' -o -name '*.sha256' \)

if command -v gh >/dev/null 2>&1 && git remote get-url origin >/dev/null 2>&1; then
  record_command "Read-only GitHub PR State" gh pr view --json number,state,isDraft,mergeStateStatus,reviewDecision,url,headRefName,baseRefName
  record_command "Read-only GitHub Workflow State" gh run list --limit 5 --json databaseId,headBranch,headSha,status,conclusion,displayTitle,workflowName,createdAt
  record_command "Read-only v1.0.5 Release State" gh release view v1.0.5 --json tagName,isDraft,isPrerelease,publishedAt,url
else
  {
    echo "## Read-only GitHub State"
    echo
    echo "gh or origin remote unavailable; skipped PR/CI/release status lookup."
    echo
  } >> "$report"
fi

cat >> "$report" <<'EOF'
## Non-Secret Acceleration Commands

- `./scripts/quality/triage-release-device-rows.sh`
- `./scripts/quality/android-harness.sh connected`
- `./scripts/quality/collect-device-evidence.sh "$ANDROID_SERIAL" build/evidence/<run>/device`
- `./scripts/quality/verify-release-assetlinks.sh` with `ASSETLINKS_URL=` for local-only file validation, or default URL for published-domain validation.
- `./scripts/quality/collect-release-evidence.sh build/evidence/<run>/release` only when the signing owner has loaded release signing env; otherwise it records missing signing env and skips release builds.

## Overclaim Guard

This triage is not release proof. Rows E01/E07/E09/E12-E16 remain TODO until the acceptance matrix contains current command/device/CI/signing/PR/release/install evidence for each row.
EOF

echo "$report"
