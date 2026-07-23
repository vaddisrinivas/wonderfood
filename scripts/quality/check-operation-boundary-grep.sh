#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

if rg -n "INSERT INTO records|UPDATE records SET|INSERT INTO record_relations|DELETE FROM record_relations WHERE from_id = \\?" src app --glob '!src/ops/apply.ts' --glob '!src/db/migrations.ts'; then
  echo "Operation boundary violation: record writes must go through src/ops/apply.ts" >&2
  exit 1
fi

echo "Operation boundary grep passed"
