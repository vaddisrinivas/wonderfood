#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

MODE="${1:-local}"

local_tasks=(
  ":app:lintDebug"
  ":core:data:lintDebug"
  ":app:testDebugUnitTest"
  ":core:model:test"
  ":core:engine:test"
  ":core:ai:test"
  ":core:data:testDebugUnitTest"
  ":app:assembleDebug"
  ":app:assembleDebugAndroidTest"
  ":core:data:assembleDebugAndroidTest"
)

connected_tasks=(
  ":app:connectedDebugAndroidTest"
  ":core:data:connectedDebugAndroidTest"
)

case "$MODE" in
  local)
    ./gradlew "${local_tasks[@]}"
    ;;
  connected)
    ./gradlew "${connected_tasks[@]}"
    ;;
  all)
    "$0" local
    "$0" connected
    ;;
  ai)
    scripts/quality/smoke-ai-providers.py --write-config /tmp/wonderfood-working-ai-configs.json
    ;;
  ai-all)
    scripts/quality/smoke-ai-providers.py --write-config /tmp/wonderfood-all-ai-configs.json --write-config-mode available
    ;;
  *)
    echo "Usage: $0 [local|connected|all|ai|ai-all]" >&2
    exit 64
    ;;
esac
