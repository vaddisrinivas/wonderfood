#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

SERIAL="${ANDROID_SERIAL:-${1:-}}"
STAMP="$(date +"%Y%m%d-%H%M%S")"
OUT_DIR="${2:-build/evidence/android-$STAMP}"
mkdir -p "$OUT_DIR"

adb_args=()
if [[ -n "$SERIAL" ]]; then
  adb_args=(-s "$SERIAL")
fi

adb "${adb_args[@]}" devices -l > "$OUT_DIR/adb-devices.txt"
adb "${adb_args[@]}" shell pidof com.wonderfood.app > "$OUT_DIR/wonderfood-pid.txt" 2>&1 || true
adb "${adb_args[@]}" exec-out screencap -p > "$OUT_DIR/screen.png" || true
adb "${adb_args[@]}" logcat -d -t 1000 > "$OUT_DIR/logcat.txt" || true
adb "${adb_args[@]}" exec-out run-as com.wonderfood.app cat databases/wonderfood.db > "$OUT_DIR/wonderfood.db" 2> "$OUT_DIR/db-pull.err" || true

echo "$OUT_DIR"
