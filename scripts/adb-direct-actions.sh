#!/usr/bin/env bash
set -euo pipefail

ADB="${ADB:-adb}"
PKG="${1:-com.wonderfood.app}"
SERIAL_ARGS=()
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  SERIAL_ARGS=(-s "$ANDROID_SERIAL")
fi

start_link() {
  local uri="$1"
  echo "Launching $uri"
  "$ADB" "${SERIAL_ARGS[@]}" shell \
    "am start -W -a android.intent.action.VIEW -d '$uri' -p '$PKG'" >/dev/null
}

start_link "wonderfood://open/today"
start_link "wonderfood://open/numbers"
start_link "wonderfood://open/kitchen"
start_link "wonderfood://voice/water?ml=250&requestId=adb-water-250"
start_link "wonderfood://voice/grocery/add?item=oats&quantity=1%20bag&requestId=adb-grocery-oats"
start_link "wonderfood://voice/meal/log?meal=chicken%20rice&calories=520&requestId=adb-meal-chicken-rice"
start_link "wonderfood://voice/shopping/start?requestId=adb-shopping-start"
start_link "wonderfood://quick?text=need%20Greek%20yogurt%20this%20week&requestId=adb-ai-note-yogurt"

echo "WonderFood direct-action deep links launched."
