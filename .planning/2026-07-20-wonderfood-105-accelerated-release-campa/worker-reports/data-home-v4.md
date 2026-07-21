# DataHome V4 Worker Report

## Scope

Owned files only:

- `app/src/main/java/com/wonderfood/app/sync/DataHomeAdapter.kt`
- `app/src/test/java/com/wonderfood/app/sync/DataHomeAdapterTest.kt`
- `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/worker-reports/data-home-v4.md`

## Changes

- Rewrote `DataHomeAdapter.pull` and `DataHomeAdapter.push` to accept canonical `HouseholdSnapshot` instead of `WonderFoodSnapshot`.
- Updated `DataHomeSyncCoordinator` so pull-before-push remains intact and the pushed object is canonical.
- Changed Sheets and Notion pulls to read V4 workspace rows and expose `workspaceRows` plus `workspaceDraft`; they no longer route through legacy snapshot-row dumps or `WonderFoodWorkspaceSnapshotMerger`.
- Changed Sheets pushes to export a canonical workspace graph through `GoogleSheetsGateway.exportGraph` or an adapter-level canonical test gateway.
- Changed Notion pushes to export canonical V4 workspace data through `NotionGateway.exportWorkspace(HouseholdSnapshot, ...)` or an adapter-level canonical test gateway.
- Kept Postgres as the only legacy wire boundary: outbound canonical snapshots are converted with `CanonicalHouseholdSnapshotExporter.toSnapshot(...)`.
- Preserved lifecycle methods, retry behavior, health/probe/provision/repair/disconnect, local-replica safety, secret redaction, and provider-failure behavior.
- Added focused tests that fail if Sheets/Notion adapter pushes use legacy snapshot exports.

## Checks

- PASS: `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.DataHomeAdapterTest`
- PASS: `./gradlew :app:compileFossDebugKotlin`
- PASS: `git diff --check -- app/src/main/java/com/wonderfood/app/sync/DataHomeAdapter.kt app/src/test/java/com/wonderfood/app/sync/DataHomeAdapterTest.kt .planning/2026-07-20-wonderfood-105-accelerated-release-campa/worker-reports/data-home-v4.md`

## Unresolved Conformance Patch

Postgres inbound still returns `PostgresRemoteSnapshotResult.snapshot: WonderFoodSnapshot?`. This worker scope has only `CanonicalHouseholdSnapshotExporter.toSnapshot(HouseholdSnapshot)`, not a reverse importer. Exact follow-up patch:

- Add a canonical Postgres read result or importer outside this worker scope.
- Change `PostgresHostedGateway.readRemoteSnapshot(...)` to return `HouseholdSnapshot?` or a typed canonical DTO.
- Then set `DataHomePullResult.canonicalSnapshot` for Postgres and remove the legacy `snapshot` field from `DataHomePullResult`.

Provider interface cleanup remains outside this worker scope:

- `GoogleSheetsSnapshotGateway` still declares legacy snapshot row methods in `GoogleSheetsGateway.kt`.
- `NotionWorkspaceGateway` still declares legacy snapshot merge/export methods so `NotionGateway.kt` keeps compiling without an out-of-scope edit.
- Adapter production code no longer calls those methods for Sheets or Notion; test fakes fail loudly if legacy export is used.

No provider gateway files were edited.
