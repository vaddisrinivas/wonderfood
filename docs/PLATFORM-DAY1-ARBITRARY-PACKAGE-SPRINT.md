# Platform Day 1 Sprint — Arbitrary Package Proof

Status: approved and locked  
Base branch: `codex/lifeos-e2e-implementation`  
Primary objective: prove the platform can run an arbitrary compiled JSON app package without product-specific TypeScript.

## Objective

Prove this scenario end to end:

1. Load a neutral reference app package `reference-app@1.0.0` from JSON.
2. Confirm the loaded package ID is not named or imported in runtime source.
3. Render three JSON-defined screens through JSON Render.
4. Create a record through existing generic operations.
5. Activate `reference-app@1.1.0`.
6. Roll back to `reference-app@1.0.0`.
7. Confirm the record still exists and is readable.
8. Pass:

```bash
npm run check:platform-day1
```

This sprint does not complete the full commercial platform. It proves the decisive platform claim: a new private app can be package-defined, loaded, rendered, upgraded, and rolled back without a runtime code fork.

## Scope freeze

Do not work on:

- Workspaces or `app_installation_id`
- Sync
- Authentication
- Billing
- Marketplace
- Package signing
- Multi-installation support
- Native build generation
- Remote package registry
- Full authoring-folder compiler
- Destructive migrations

## End-of-day pass criteria

Required:

- V3 packages missing base fields are rejected.
- Mobile and server run the shared package fixture corpus.
- Remaining validator parity gaps are fixed or documented.
- The reference package ID is not imported or named in platform TypeScript.
- Three reference-package screens render through JSON Render.
- Generic renderer contains no prohibited food/WonderFood vocabulary.
- Existing generic operations support create, update, and archive.
- `reference-app@1.1.0` activates.
- Rollback returns to `reference-app@1.0.0`.
- Records created under `1.0.0` survive activation and rollback.
- Focused acceptance gate passes.

Optional:

- Restore operation.
- Undo verification.
- Full web export.
- Full Android export.
- Exact validator error-message parity.
- CI workflow update.

## Operating model

Use four lanes, not eight. Keep ownership tight. No broad formatting. No dependency upgrades. No generated native project changes.

| Lane | Responsibility | Priority |
|---|---|---:|
| A | Package validation parity | High |
| B | Dynamic package loader and `RuntimeContext` | Highest |
| C | Generic renderer and reference package | High |
| D | Focused acceptance gate and docs | Medium |

## Worktree setup

```bash
BASE="codex/lifeos-e2e-implementation"
ROOT="../wonderfood-platform-day1"

mkdir -p "$ROOT"

git worktree add -b day1/package-validation "$ROOT/package-validation" "$BASE"
git worktree add -b day1/package-runtime "$ROOT/package-runtime" "$BASE"
git worktree add -b day1/reference-package "/reference-package" "$BASE"
git worktree add -b day1/acceptance "$ROOT/acceptance" "$BASE"
```

## Cross-lane rules

1. Each lane edits only owned files.
2. No repository-wide formatting.
3. No dependency upgrades.
4. No generated `android/` changes.
5. Interface requests go into lane `handoff.md`.
6. Shared validation fixtures belong to Lane A.
7. Compiled reference package belongs to Lane C.
8. Only integration lead resolves cross-lane incompatibilities.
9. Cross-lane fixes get separate commits.
10. Existing unrelated work is preserved.

## Lane A — Package validation parity

### Goal

Ensure V2 and V3 packages receive complete base validation and establish shared fixture behavior.

### First deliverable

Create a small shared validation contract/helper before patching mobile/server validators.

Order:

1. Define base-package invariants.
2. Define stable error categories.
3. Add shared fixtures.
4. Patch mobile validation.
5. Patch server validation.
6. Record remaining parity gaps.

### Owned paths

```text
packages/shared/contracts/package.ts
packages/shared/contracts/package-change.ts
packages/shared/contracts/native-capabilities.ts
packages/shared/contracts/native-capability-kinds.ts
src/db/app-package-registry.ts
server/src/kernel/package-schema.ts
server/src/kernel/package.ts
tests/fixtures/package-validation/**
tests/contracts/package-validation.test.ts
server/test/fixtures/package-validation/**
server/test/package-validation.ts
```

### `src/db/app-package-registry.ts` split

Lane A owns only validation symbols:

- `assertAppPackageShape*`
- validation helpers
- reference checks
- V2/V3 invariant checks

Lane B owns:

- bootstrap
- activation
- lookup
- rollback
- package loading flow

### Required implementation

Base validation must cover:

- `id`
- `version`
- `collections`
- `queries`
- `views`
- `rules`
- `capabilities`
- `acceptanceTests`

V3 validation must retain:

- dependency pins
- native capabilities
- contract lock

Reference validation must include:

- view references existing query
- package UI references existing collection
- IDs match map keys

### Minimum fixtures

```text
valid-v2.json
valid-v3.json
v3-missing-collections.json
v3-missing-queries.json
v3-missing-views.json
view-unknown-query.json
ui-unknown-collection.json
invalid-contract-checksum.json
invalid-native-capability.json
```

Each fixture should declare:

- path
- expected valid/invalid result
- expected error category

Exact error text does not need to match between mobile and server on Day 1.

### Required checks

```bash
npm run typecheck
npx vitest run tests/contracts/package-validation.test.ts
./server/node_modules/.bin/tsx server/test/package-validation.ts
```

### Handoff to Lane B

Provide:

- exported validation entry point
- stable valid/invalid categories
- activation-safe error categories

## Lane B — Dynamic package loader and `RuntimeContext`

### Goal

Load and activate an unrecognized package ID without adding it to `src/domain/catalog.ts`, and remove global package mutation from the normal route path.

This is the highest-priority lane.

### Owned paths

```text
src/domain/catalog.ts
src/domain/app-package-bridge.ts
src/domain/package-loader.ts
src/domain/runtime-context.tsx
src/domain/package-runtime.ts
src/db/app-package-registry.ts
src/db/provider.native.tsx
src/db/provider.web.tsx
tests/domain/package-loader.test.ts
tests/domain/runtime-context.test.ts
tests/db/app-package-activation.test.ts
```

### Day-one limits

Required:

- No package mutation during React render.
- Route path receives package through `RuntimeContext`.
- Unknown package ID loads without a `catalog.ts` entry.
- Activation and rollback continue using `src/db/app-package-registry.ts`.
- Existing globals may remain only as deprecated compatibility paths.

Deferred:

- multiple simultaneous installations
- `app_installation_id`
- workspace isolation
- signed remote downloads
- file-picker UI
- marketplace/catalog UX

### Required implementation

1. Introduce package loader abstraction accepting parsed `AppPackage`.
2. Preserve existing bundled WonderFood bootstrap.
3. Allow activation from JSON object.
4. Remove explicit reference package requirement from `loadManifestByPath()`.
5. Replace normal route usage of:
   - `activeDomainOverride`
   - `activePackageOverride`
   - global active-package cache
6. Supply active package through `RuntimeContext`.
7. Preserve activation receipts.
8. Preserve previous package key.
9. Preserve rollback.
10. Confirm records survive package activation and rollback.

### Required tests

- Load unknown package ID from JSON object.
- Activate `reference-app@1.0.0`.
- Activate `reference-app@1.1.0`.
- Roll back to `1.0.0`.
- Record created before upgrade still exists after rollback.
- Two independent runtime contexts do not share active package state.
- Invalid activation leaves previous package active.

### Required checks

```bash
npm run typecheck
npx vitest run \
  tests/domain/package-loader.test.ts \
  tests/domain/runtime-context.test.ts \
  tests/db/app-package-activation.test.ts
```

### Handoff to Lane C

Provide small API:

```ts
loadAppPackage(candidate)
activateAppPackage(db, candidate)
rollbackAppPackage(db)
useAppRuntime()
```

Lane C should not need to know storage internals.

## Lane C — Generic renderer and reference package

### Goal

Remove WonderFood assumptions from the runtime renderer and prove arbitrary-package rendering using compiled JSON.

### Owned paths

```text
src/presentation/json-render-route.tsx
src/presentation/json-render-surface.tsx
src/presentation/json-render-widgets.tsx
src/domain/renderer.tsx
tests/fixtures/app-packages/reference-app/**
tests/presentation/**
tests/fixtures/app-packages/reference-app/**
```

### Package artifacts

```text
tests/fixtures/app-packages/reference-app/
  README.md
  authoring/
    schemas/
    workflows/
    locales/
  compiled/
    reference-app-1.0.0.package.json
    reference-app-1.1.0.package.json
  fixtures/
    records.json
```

The runtime proof consumes files under `compiled/`. The authoring folder only points toward the future compiler.

### reference package scope

Collections:

- `chore`
- `assignment`
- `household_member`
- `completion`

Required screens:

1. Today
   - current assignments
   - due items
2. Chores
   - chore list
   - add/open action
3. Household
   - household members
   - assigned chore summary

Optional screen:

- Review

Versions:

- `1.0.0`: base chore fields and screens
- `1.1.0`: adds optional `estimated_minutes`

No destructive migration is required.

### Renderer purity

Remove runtime renderer references to:

```text
shopping_item
inventory
meal_plan
pantry
shopping
meal
Ask Wonder
WonderFood
```

Grep scope:

```text
src/presentation/
src/domain/renderer.tsx
```

Allowed locations:

```text
packages/domain-config/domains/food.v1.json
tests/fixtures/app-packages/reference-app/**
tests/**
docs/**
```

Package components should supply:

- empty-state text
- icon or emoji
- action route
- button label
- metric label
- record subtitle fields or template

Safe platform defaults may remain, but they must be domain-neutral.

### Required tests

- Three screens select and compose successfully.
- Chore data renders without platform code knowing package collection IDs.
- Package empty-state text is respected.
- Unknown widgets fail closed or show neutral unsupported state.
- Malformed props do not crash entire surface.
- Renderer source grep finds no prohibited vocabulary.

### Required checks

```bash
npm run typecheck
npx vitest run tests/presentation
```

## Lane D — Focused acceptance gate and docs

### Goal

Create one focused command proving the day’s work without making slow native exports part of the first feedback loop.

### Owned paths

```text
package.json
scripts/quality/check-platform-package-portability.mjs
scripts/quality/run-platform-day1.mjs
.github/workflows/expo-quality.yml
docs/adr/0002-package-installation-identity.md
docs/adr/0003-package-loading-and-trust.md
docs/PLATFORM-DAY1-ACCEPTANCE.md
docs/PLATFORM-EXECUTION-PLAN.md
```

### Required command

Add:

```bash
npm run check:platform-day1
```

It must run:

1. Config validation.
2. Typecheck.
3. Shared package validation tests.
4. Server package validation fixtures.
5. Package loader and runtime-context tests.
6. Renderer tests.
7. Package portability check.

### Portability check

Fail if `reference-app` or its package paths appear in:

```text
src/domain/catalog.ts
src/presentation/
app/
```

Allow only:

```text
tests/fixtures/app-packages/reference-app/
tests/
docs/
```

Also require compiled package artifacts:

```text
tests/fixtures/app-packages/reference-app/compiled/reference-app-1.0.0.package.json
tests/fixtures/app-packages/reference-app/compiled/reference-app-1.1.0.package.json
```

### CI rule

The architectural proof is:

```bash
npm run check:platform-day1
```

Updating `.github/workflows/expo-quality.yml` is useful but non-blocking if CI wiring causes unrelated trouble.

### Required docs

Document:

- package vs installation identity
- one-active-installation limitation
- RuntimeContext model
- package trust levels
- current compiled package format
- future authoring-folder compiler
- exact passing commit
- checks run
- checks not run
- known gaps

## Schedule

### 08:00-08:30 — Contract freeze

Agree on:

- existing `AppPackage` contract as compiled artifact
- package fixture location
- validation error categories
- loader API
- package file names and versions
- prohibited hardcoding
- focused acceptance command

No implementation begins until interfaces are written in the coordination note.

### 08:30-11:30 — Parallel block 1

- Lane A: base validation and fixtures
- Lane B: loader abstraction and RuntimeContext
- Lane C: generic renderer cleanup and `1.0.0` package
- Lane D: focused runner, portability check, ADR skeletons

### 11:30-12:00 — Handoff checkpoint

Required outputs:

- Lane A publishes fixture/error-category contract.
- Lane B publishes loader/context signatures.
- Lane C publishes compiled package paths.
- Lane D confirms exact commands invoked by acceptance runner.

Interface changes after this checkpoint require affected lane leads to approve.

### 12:00-14:30 — Parallel block 2

- Lane A: server parity and negative fixtures
- Lane B: activation, upgrade, rollback, record-survival tests
- Lane C: `1.1.0` package and screen tests
- Lane D: focused gate and docs

### 14:30 — Lane freeze

Each lane runs:

```bash
git diff --check
npm run typecheck
```

Then lane-specific tests. Every lane commits before integration.

### 15:00-16:00 — Integration

Merge order:

```bash
git switch codex/lifeos-e2e-implementation
git switch -c day1/platform-integration

git merge --no-ff day1/package-validation
git merge --no-ff day1/package-runtime
git merge --no-ff day1/reference-package
git merge --no-ff day1/acceptance
```

Expected overlap:

- `src/db/app-package-registry.ts` between Lane A and Lane B
- possibly `package.json` only in Lane D

For `app-package-registry.ts`, integrate by symbol ownership. Do not accept one side wholesale.

### 16:00-17:00 — Focused acceptance

```bash
npm ci --no-audit --no-fund
npm ci --prefix server --no-audit --no-fund
npm run check:platform-day1
```

Fix only blockers against the locked criteria.

### 17:00-18:00 — Full checks and evidence

Run as time permits:

```bash
npm test
npm run test:server:direct
npm run export:web
npm run export:android
```

Focused gate is the Day 1 blocker. Full export failures must be recorded, but slow Android export alone does not invalidate the architecture proof unless caused by this sprint.

## Final evidence report template

```text
Final commit:
Base commit:

Required acceptance:
- V3 base validation:
- Shared fixture corpus:
- Dynamic package load:
- No catalog hardcode:
- Three JSON-render screens:
- Generic renderer purity:
- 1.1.0 activation:
- Rollback to 1.0.0:
- Record survival:
- Focused gate:

Additional checks:
- Full client suite:
- Full server suite:
- Web export:
- Android export:

Known parity gaps:
Known runtime limitations:
Deferred work:
```

## Final go/no-go

GO only if:

- `reference-app@1.0.0` loads from JSON.
- It is not named/imported in runtime source.
- Three JSON-defined screens render.
- A record can be created through generic operations.
- `reference-app@1.1.0` activates.
- Rollback returns to `1.0.0`.
- The record still exists and is readable.
- `npm run check:platform-day1` passes.

If this fails, do not start workspaces, sync, auth, billing, marketplace, or package signing. Fix package/runtime boundary first.
