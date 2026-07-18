#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

ADB="${ADB:-adb}"
SERIAL="${ANDROID_SERIAL:-${1:-}}"
STAMP="$(date +"%Y%m%d-%H%M%S")"
OUT_DIR="${2:-build/evidence/android-$STAMP}"
mkdir -p "$OUT_DIR"

serial_adb() {
  if [[ -n "$SERIAL" ]]; then
    "$ADB" -s "$SERIAL" "$@"
  else
    "$ADB" "$@"
  fi
}

serial_adb devices -l > "$OUT_DIR/adb-devices.txt"
serial_adb shell pidof com.wonderfood.app > "$OUT_DIR/wonderfood-pid.txt" 2>&1 || true
serial_adb exec-out screencap -p > "$OUT_DIR/screen.png" || true
serial_adb logcat -d -t 1000 > "$OUT_DIR/logcat.txt" || true
serial_adb exec-out run-as com.wonderfood.app cat databases/wonderfood.db > "$OUT_DIR/wonderfood.db" 2> "$OUT_DIR/db-pull.err" || true

echo "$OUT_DIR"
