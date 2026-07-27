# Platform Day 1 Acceptance

## Verdict

PASS for the Day 1 arbitrary-package proof.

This certifies the narrow platform claim:

> A new private app package can be package-defined, loaded, rendered, upgraded, rolled back, and mutated through the canonical operation boundary without product-specific runtime TypeScript.

## Tested implementation commit

`097fad2` — `test: certify day1 operation-boundary proof`

Base branch:

`codex/lifeos-e2e-implementation`

## Required acceptance

- V3 packages missing base fields are rejected: PASS.
- Mobile and server share package validation fixtures: PASS.
- Arbitrary package ID loads without a `catalog.ts` entry: PASS.
- Route rendering uses `RuntimeContext`: PASS.
- Three reference-package screens render through JSON Render: PASS.
- Generic renderer has no prohibited food/WonderFood runtime vocabulary: PASS.
- Generic operations support create/update/archive: existing kernel path retained; Day 1 record create proof now uses `applyOperation()`.
- `reference-app@1.1.0` activates: PASS.
- Rollback returns to `reference-app@1.0.0`: PASS.
- Record created under `1.0.0` survives activation and rollback: PASS.
- Focused acceptance gate passes: PASS.

## Checks run

```bash
npm run typecheck
npx vitest run tests/db/app-package-activation.test.ts tests/contracts/package-validation.test.ts
npm run check:platform-day1
```

Result:

```text
typecheck: PASS
package activation + validation focused tests: PASS, 14 tests
config:validate: PASS
shared package validation fixtures: PASS, 11 fixtures
server package validation fixtures: PASS, 11 fixtures
package loader/runtime/activation tests: PASS, 5 tests
presentation tests: PASS, 4 tests
platform portability check: PASS
Platform day1 gate: PASS
```

## Important evidence fixes after `e9c9577`

- `tests/db/app-package-activation.test.ts` now creates the reference chore through `applyOperation()` instead of direct `upsertRecord()`.
- `packages/shared/contracts/package.ts` no longer imports from `src/domain/canonical-json`.
- Canonical JSON/hash helpers now live in `packages/shared/contracts/canonical-json.ts`.
- `src/domain/canonical-json.ts` remains as a compatibility re-export.

## Checks not run

- Full product quality suite.
- Full web export.
- Full Android export.
- Signed release checks.
- Live provider mutation proof.

These are outside the locked Day 1 proof.

## Known Day 1 limitations

- One active installation only.
- No workspace or `app_installation_id` isolation yet.
- Package trust is local/structural, not signed remote distribution.
- Authoring-folder compiler is deferred.
- CI contains a non-blocking Day 1 step; local focused gate is the certified proof for this sprint.
