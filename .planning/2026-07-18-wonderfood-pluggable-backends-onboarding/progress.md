# Progress Log

## Session: 2026-07-18

### Planning and Architecture

- **Status:** complete
- Confirmed one-active-backend direction for Local SQLite, Google Sheets, Notion, and PostgreSQL-compatible providers.
- Confirmed Google Sheets user flow: paste Sheet URL and authorize with Google; no deployment or Apps Script.
- Inspected canonical worktree, dirty state, current issues/PRs, product capability map, roadmap, privacy, security, Google auth dependencies, and provider-related source paths.
- Reviewed official Google Sheets, Notion, Android security, Supabase, and GitHub reference evidence.
- Created backend-neutral architecture, onboarding state machine, migration strategy, UI contract, acceptance gates, risks, rollback, estimates, and PR sequence.
- Preserved current application code; no app source was edited during this planning turn.

### Files Created

- `.planning/2026-07-18-wonderfood-pluggable-backends-onboarding/task_plan.md`
- `.planning/2026-07-18-wonderfood-pluggable-backends-onboarding/findings.md`
- `.planning/2026-07-18-wonderfood-pluggable-backends-onboarding/progress.md`
- `.planning/.active_plan`

## Tests

No build or test was run because this turn created planning artifacts only.

## Current State

- Planning deliverable complete.
- Implementation has started.
- Phase 1 contract slice is implemented in `core:data`.
- Local SQLite is now represented by a real backend adapter for local connect/bootstrap/export health.
- Google Sheets URL/raw-ID parsing exists for the paste-link onboarding step.
- Active backend selection persists through `SharedPreferencesBackendConfigurationStore`.
- First-run data-home chooser renders in the app and can activate Local SQLite.
- Google Sheets onboarding now uses the Google Identity authorization flow for the Sheets OAuth scope in the Play flavor.
- FOSS flavor has an explicit Google Sheets unavailable implementation.
- Google Sheets onboarding now calls the Sheets REST API to read spreadsheet metadata, create missing WonderFood tabs, and initialize headers before saving the backend config.
- Canonical `WonderFoodSnapshot` can now be exported to stable Sheets rows and decoded from the `_meta` snapshot row.
- `GoogleSheetsGateway` can export snapshot rows to the canonical tabs and read rows back from the Sheet.
- Backend/router/onboarding unit tests exist and pass.
- Play debug app assembly passes after the new `core:data -> core:ai` dependency.
- Play debug APK installed on `emulator-5554` and `MainActivity` launch returned success.
- Existing UI consistency edits remain uncommitted in three app files.
- Next implementation action is the Local SQLite backend adapter/cache seam, then persistent backend configuration storage and first-run UI wiring.

## Error Log

| Error | Attempt | Resolution |
|---|---:|---|
| Broad GitHub scan ranked unrelated repositories | 1 | Used targeted repository searches and primary project READMEs |

## Session: 2026-07-18 Implementation Continuation

### Implemented

- Added backend-neutral contracts in `core/data/src/main/kotlin/com/wonderfood/core/data/backend/FoodBackend.kt`.
- Added `BackendRouter`, `BackendConfigurationStore`, `CredentialVault`, and secret wrapper types.
- Added first-run backend onboarding state primitives.
- Added `LocalSqliteBackend` in the Room data layer.
- Added Room snapshot export queries for pages, foods, aliases, and stock lots.
- Added Room-to-domain mappers for pages and aliases.
- Added `GoogleSheetsUrlParser` and `GoogleSheetReference`.
- Added `SharedPreferencesBackendConfigurationStore`.
- Added first-run backend chooser UI to `MainScreen`.
- Added backend state and actions to `MainScreenViewModel`.
- Added `GoogleSheetsAuthorization` for Play and FOSS.
- Wired the Google Sheets onboarding CTA to Google sign-in plus Sheets authorization before saving the backend config.
- Updated Google sign-in missing-client-id wording from backup-only to all Google features.
- Added `GoogleSheetsGateway` and `GoogleSheetsGatewayTest`.
- Updated `connectGoogleSheetsBackend` so Sheets config persists only after schema bootstrap succeeds.
- Added `WonderFoodSnapshotRow` plus `WonderFoodSnapshotCodec.rows()` and `decodeSnapshotRow()`.
- Added `WonderFoodSnapshotRowsTest`.
- Added `GoogleSheetsGateway.exportSnapshotRows()` and `readSnapshotRows()`.
- Expanded Sheets schema tabs to include typed row tabs emitted by the snapshot exporter: pages, food aliases, nutrition snapshots, relations, and attachments.
- Added router and onboarding unit tests.
- Added local backend tests.
- Added Google Sheets URL parser tests.
- Added persisted backend configuration tests.
- Updated `core:data` to depend on `core:ai` so pushes can carry canonical `CommandEnvelope` values.
- Updated `CoreDataBoundary` upstream module list.

### Checks Run

- `./gradlew :core:data:testDebugUnitTest` - passed.
- `./gradlew :app:assemblePlayDebug` - passed.
- `adb -s emulator-5554 install -r app/build/outputs/apk/play/debug/app-play-debug.apk` - passed.
- `adb -s emulator-5554 shell am start -n com.wonderfood.app/.MainActivity` - passed.
- `adb -s emulator-5554 shell dumpsys activity activities | rg -n "mResumedActivity|topResumedActivity|com.wonderfood|ProcessRecord"` - showed `com.wonderfood.app/.MainActivity` resumed.
- `./gradlew :core:data:testDebugUnitTest :app:assemblePlayDebug` - passed after the Google Sheets parser addition.
- `./gradlew :core:data:testDebugUnitTest :app:assemblePlayDebug` - passed after persisted backend storage and onboarding UI.
- `adb -s emulator-5554 install -r app/build/outputs/apk/play/debug/app-play-debug.apk` - passed after onboarding UI.
- `adb -s emulator-5554 shell pm clear com.wonderfood.app && adb -s emulator-5554 shell am start -n com.wonderfood.app/.MainActivity` - fresh first-run launch passed.
- Screenshot `/tmp/wonderfood-backend-onboarding-ready.png` showed the data-home chooser.
- Screenshot `/tmp/wonderfood-backend-local-selected.png` showed the main Today screen after tapping `Use this phone`.
- `./gradlew :core:data:testDebugUnitTest :app:assemblePlayDebug :app:assembleFossDebug` - passed after Google Sheets OAuth wrapper and wiring.
- Screenshot `/tmp/wonderfood-sheets-oauth-onboarding.png` showed `Connect Google Sheets`.
- Screenshot `/tmp/wonderfood-sheets-oauth-placeholder-error.png` showed the placeholder OAuth client ID error path without crashing.
- Final `adb -s emulator-5554 install -r app/build/outputs/apk/play/debug/app-play-debug.apk` - passed.
- Final `adb -s emulator-5554 shell am start -n com.wonderfood.app/.MainActivity` plus `dumpsys activity` - showed resumed `com.wonderfood.app/.MainActivity`.
- Broad `./gradlew :core:data:testDebugUnitTest :app:testPlayDebugUnitTest :app:assemblePlayDebug :app:assembleFossDebug` built both APKs but failed `:app:testPlayDebugUnitTest` on 7 existing parser/golden tests: `CommandEnvelopeDraftMapperTest`, `DeterministicReceiptParserTest`, and `PantryCrossChannelGoldenTest`.
- Focused `./gradlew :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.GoogleSheetsGatewayTest :core:data:testDebugUnitTest :app:assemblePlayDebug :app:assembleFossDebug` - passed.
- Final post-Sheets-schema `adb -s emulator-5554 install -r app/build/outputs/apk/play/debug/app-play-debug.apk` - passed.
- Final post-Sheets-schema `adb -s emulator-5554 shell am start -n com.wonderfood.app/.MainActivity` plus `dumpsys activity` - showed resumed `com.wonderfood.app/.MainActivity`.
- `./gradlew :core:model:test :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.GoogleSheetsGatewayTest :core:data:testDebugUnitTest :app:assemblePlayDebug :app:assembleFossDebug` - passed after snapshot row export/read primitives.
- Post-row-sync `adb -s emulator-5554 install -r app/build/outputs/apk/play/debug/app-play-debug.apk` - passed.
- Post-row-sync `adb -s emulator-5554 shell am start -n com.wonderfood.app/.MainActivity` plus `dumpsys activity` - showed resumed `com.wonderfood.app/.MainActivity`.
- `./gradlew :core:model:test :core:data:testDebugUnitTest :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.GoogleSheetsGatewayTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest :app:assemblePlayDebug :app:assembleFossDebug` - passed after the Sheets snapshot sync coordinator wiring.

### Notes

- Existing dirty UI edits in `MainActivity.kt`, `Theme.kt`, and `MainScreen.kt` were not touched.
- No provider SDKs were added yet; this slice gives Local, Sheets, Notion, and Postgres adapters a stable shared contract.
- `pidof com.wonderfood.app` returned empty, but `dumpsys activity` showed the WonderFood activity resumed and `pidof com.example.wonderfood` returned a process. No `com.example.wonderfood` source reference was found in this repo; investigate emulator/package residue or legacy process naming in a separate runtime polish pass.
- Google Sheets onboarding now performs sign-in, Sheets OAuth authorization, metadata fetch, missing tab creation, and header initialization before saving config. Snapshot row export/read primitives exist. The next step is wiring live local export/import and conflict/outbox handling.

## Five-Question Reboot Check

| Question | Answer |
|---|---|
| Where am I? | Phase 1 contracts/local/persistence/onboarding slice is implemented |
| Where am I going? | Backend contracts, local seam, onboarding, providers, new UI, migration, release |
| What is the goal? | One selectable authoritative backend with offline Android behavior and friction-free onboarding/UI |
| What have I learned? | See `findings.md` |
| What have I done? | Created contracts, Local backend adapter, persisted backend selection, and first-run chooser |

### Legacy Production Store Bridge

- Added `LegacyFoodMemorySnapshotExporter` so the production `FoodChatStore.readMemory()` state can be exported as canonical `WonderFoodSnapshot` rows.
- Wired Google Sheets connect/export to use the visible legacy app memory instead of a freshly-created Room database.
- Added a generic `GoogleSheetsSnapshotSyncCoordinator.exportSnapshot(...)` path while preserving `exportLocalSnapshot(...)` for Room-backed tests/adapters.
- Added focused exporter tests for kitchen items, nutrition, groceries, recipes, meal plans, meal logs, and food events.

### Checks Run

- `./gradlew :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.LegacyFoodMemorySnapshotExporterTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest :app:assemblePlayDebug` - passed after tightening the grocery/entity expectation.

### Credential Vault Hardening

- Added `AndroidKeystoreCredentialVault` implementing the shared `CredentialVault` interface with AES/GCM keys in Android Keystore and encrypted payloads in private SharedPreferences.
- Added `CredentialSecretCodec` for OAuth, bearer token, API token, and connection-string secrets using JVM-testable kotlinx serialization JSON.
- Wired Google Sheets connect to save the OAuth access token behind `CredentialRef(BackendType.GOOGLE_SHEETS, "google-sheets-primary")` before saving the active backend config.

### Checks Run

- `./gradlew :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.CredentialSecretCodecTest --tests com.wonderfood.app.sync.LegacyFoodMemorySnapshotExporterTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest :app:assemblePlayDebug :app:assembleFossDebug` - passed.
- `adb -s emulator-5554 install -r app/build/outputs/apk/play/debug/app-play-debug.apk` - passed after legacy snapshot export and credential vault changes.
- `adb -s emulator-5554 shell am start -n com.wonderfood.app/.MainActivity` plus `dumpsys activity` - showed resumed `com.wonderfood.app/.MainActivity` after latest changes.

### Provider Input Parsers

- Added `NotionUrlParser` for Notion page URLs/raw page IDs with canonical dashed page IDs.
- Added `PostgresConnectionParser` for Supabase/PostgREST/WonderFood hosted endpoints and direct PostgreSQL DSNs, including HTTPS/TLS safety checks.
- Added focused parser tests for Notion page links, Supabase inference, direct DSN inference, hosted HTTP rejection, and `sslmode=disable` rejection.

### Checks Run

- `./gradlew :core:data:testDebugUnitTest :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.CredentialSecretCodecTest --tests com.wonderfood.app.sync.LegacyFoodMemorySnapshotExporterTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest :app:assemblePlayDebug :app:assembleFossDebug` - passed.

### All-Four Backend Onboarding Wiring

- Extended first-run onboarding so Notion accepts page URL + integration token and Postgres/Supabase accepts endpoint/household/token or direct PostgreSQL DSN.
- Added `MainScreenViewModel.connectNotionBackend(...)` and `connectPostgresBackend(...)`.
- Notion and Postgres secrets now save through `AndroidKeystoreCredentialVault` and active configs save only credential refs plus non-secret metadata.
- Direct PostgreSQL DSNs are treated as vaulted connection-string secrets; the saved config endpoint is redacted to `direct-postgres`.

### Checks Run

- `./gradlew :core:data:testDebugUnitTest :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.CredentialSecretCodecTest --tests com.wonderfood.app.sync.LegacyFoodMemorySnapshotExporterTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest :app:assemblePlayDebug :app:assembleFossDebug` - passed after all-four-backend onboarding wiring.
- `adb -s emulator-5554 install -r app/build/outputs/apk/play/debug/app-play-debug.apk` - passed.
- `adb -s emulator-5554 shell pm clear com.wonderfood.app` - passed.
- `adb -s emulator-5554 shell am start -n com.wonderfood.app/.MainActivity` plus `dumpsys activity` - showed resumed `com.wonderfood.app/.MainActivity`.
- Screenshot saved to `/tmp/wonderfood-all-backends-onboarding.png`.

### Notion Page Access Validation

- Added `NotionGateway` using the current Notion retrieve-page API shape: bearer token auth plus `Notion-Version: 2026-03-11`.
- Notion onboarding now verifies the supplied page is reachable before vaulting the token and saving `NotionConfig`.
- Postgres remains parser/config/vault-only because generic Supabase/PostgREST probing is schema-dependent and should not rely on root OpenAPI access.

### Checks Run

- `./gradlew :core:data:testDebugUnitTest :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.CredentialSecretCodecTest --tests com.wonderfood.app.sync.LegacyFoodMemorySnapshotExporterTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest :app:assemblePlayDebug :app:assembleFossDebug` - passed after Notion gateway wiring.

### Automatic Sheets Snapshot Sync Hook

- Added a debounced post-mutation snapshot sync hook at the shared draft/mutation execution seam.
- When Google Sheets is the active backend and a vaulted OAuth access token exists, accepted drafts and mutations now export the latest `FoodChatStore.readMemory()` snapshot to the connected spreadsheet.
- Sync failures are surfaced in `backendHome.message` without blocking the local mutation.

### Validation Blocker

- Attempted `./gradlew :core:data:testDebugUnitTest :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.CredentialSecretCodecTest --tests com.wonderfood.app.sync.LegacyFoodMemorySnapshotExporterTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest :app:assemblePlayDebug :app:assembleFossDebug` after the automatic sync hook.
- Gradle failed before code compilation because `local.properties` points at `/Users/srinivasvaddi/Library/Android/sdk`, but that SDK directory is currently not visible to the shell.
- The running emulator process still references `/Users/srinivasvaddi/Library/Android/sdk/emulator/...`, so this appears to be a local SDK visibility/environment issue, not a source compile error.

### Safer Google Sheets Connect

- Updated Google Sheets connect to read existing WonderFood snapshot rows before exporting local data.
- If an existing remote snapshot has user data, connect now preserves the Sheet and reports that import/review is needed instead of overwriting it.
- Persisted backend sync status to shell preferences so the latest Sheets sync/connect message survives process recreation.
- This patch is not yet rebuilt because the Android SDK path is currently missing from the shell environment.

### Postgres/Supabase Hosted Validation

- Added `PostgresGateway` for hosted backend reachability checks.
- Supabase mode checks `${endpoint}/rest/v1/` with both `Authorization: Bearer` and `apikey` headers.
- PostgREST mode checks the provided API root.
- WonderFood server mode checks `${endpoint}/health`.
- Direct PostgreSQL DSN remains parser/vault/config-only because no Android PostgreSQL driver is part of the app.
- Added `PostgresGatewayTest` for endpoint root construction.
- This patch is not yet rebuilt because the Android SDK path is still missing from the shell environment.

### Settings Data Home Visibility

- Added a Settings home `Data home` row that summarizes the active backend, detail text, and latest backend sync/connect message.
- The row is source-patched but not yet rebuilt because the Android SDK path remains missing from the shell environment.

### SDK Recovery and Final Focused Validation

- Recreated the missing Android SDK at `/Users/srinivasvaddi/Library/Android/sdk` using the official `commandlinetools-mac-14742923_latest.zip` package from Android Developers.
- Installed `platforms;android-36`, `build-tools;36.0.0`, and `platform-tools` with `sdkmanager`.
- `./gradlew :core:data:testDebugUnitTest :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.CredentialSecretCodecTest --tests com.wonderfood.app.sync.LegacyFoodMemorySnapshotExporterTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest --tests com.wonderfood.app.sync.PostgresGatewayTest :app:assemblePlayDebug :app:assembleFossDebug` - passed.
- `adb -s emulator-5554 install -r app/build/outputs/apk/play/debug/app-play-debug.apk` - passed.
- `adb -s emulator-5554 shell pm clear com.wonderfood.app` - passed.
- `adb -s emulator-5554 shell am start -n com.wonderfood.app/.MainActivity` plus `dumpsys activity` - showed resumed `com.wonderfood.app/.MainActivity`.
- Screenshot saved to `/tmp/wonderfood-backends-final-onboarding.png`.

### Sheets Remote Import Preview

- Added `SheetsImportPreview` state and a `SheetsImportPreviewDialog`.
- When Google Sheets connect detects an existing remote WonderFood snapshot with user data, the app now shows counts for foods, stock lots, shopping items, recipes, meal plans, meal logs, and events.
- Existing remote Sheet data is still preserved; explicit import/merge remains a future reviewed action.

### Checks Run

- `./gradlew :core:data:testDebugUnitTest :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.CredentialSecretCodecTest --tests com.wonderfood.app.sync.LegacyFoodMemorySnapshotExporterTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest --tests com.wonderfood.app.sync.PostgresGatewayTest :app:assemblePlayDebug :app:assembleFossDebug` - passed.
- `adb -s emulator-5554 install -r app/build/outputs/apk/play/debug/app-play-debug.apk` - passed.
- `adb -s emulator-5554 shell pm clear com.wonderfood.app` - passed.
- `adb -s emulator-5554 shell am start -n com.wonderfood.app/.MainActivity` plus `dumpsys activity` - showed resumed `com.wonderfood.app/.MainActivity`.
- Screenshot saved to `/tmp/wonderfood-sheets-import-preview-onboarding.png`.

### Sheets Reviewed Additive Import

- Added `LegacySnapshotDraftImporter` to map canonical WonderFood snapshots back into existing reviewed legacy drafts.
- Sheets import preview now offers `Use Sheet data` and applies an additive import for kitchen foods/stock, shopping items, recipes, meal logs, and meal plans.
- The import path intentionally avoids destructive local deletes or blind overwrites; preserving-only remains available from the preview dialog.
- Added focused importer coverage using the existing legacy exporter to prove round-trip daily records become applicable drafts.

### Sheets Import Final Validation

- Fixed canonical-to-legacy import gaps found by the focused unit test: canonical lowercase storage locations now map to legacy zones, and page titles are used for imported shopping and meal-log labels.
- `./gradlew :core:data:testDebugUnitTest :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.CredentialSecretCodecTest --tests com.wonderfood.app.sync.LegacyFoodMemorySnapshotExporterTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest --tests com.wonderfood.app.sync.PostgresGatewayTest :app:assemblePlayDebug :app:assembleFossDebug` - passed.
- `adb -s emulator-5554 install -r app/build/outputs/apk/play/debug/app-play-debug.apk` - passed.
- `adb -s emulator-5554 shell pm clear com.wonderfood.app` - passed.
- `adb -s emulator-5554 shell am start -n com.wonderfood.app/.MainActivity` plus `dumpsys activity` - showed resumed `com.wonderfood.app/.MainActivity`.
- Screenshots saved to `/tmp/wonderfood-backend-import-finished.png` and `/tmp/wonderfood-backend-import-finished-settled.png`.

### Notion Snapshot Export Slice

- Added a no-deployment Notion snapshot writer that appends a timestamped WonderFood snapshot summary and chunked JSON blocks to the selected Notion page.
- Notion onboarding now validates page access, exports the current local snapshot, stores the token only after export succeeds, and reports exported bytes/blocks.
- Post-mutation backend snapshot sync now routes through the active backend generically, supporting both Google Sheets and Notion export paths.
- Added focused Notion payload-shape test coverage for the appended block body.

### Notion Snapshot Export Validation

- Fixed sync helper to be `suspend` because credential vault reads are suspend operations.
- Fixed Notion payload test to run under Robolectric because Android `org.json` is stubbed in plain JVM unit tests.
- `./gradlew :core:data:testDebugUnitTest :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.CredentialSecretCodecTest --tests com.wonderfood.app.sync.LegacyFoodMemorySnapshotExporterTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest --tests com.wonderfood.app.sync.NotionGatewayTest --tests com.wonderfood.app.sync.PostgresGatewayTest :app:assemblePlayDebug :app:assembleFossDebug` - passed.
- `adb -s emulator-5554 install -r app/build/outputs/apk/play/debug/app-play-debug.apk` - passed.
- `adb -s emulator-5554 shell pm clear com.wonderfood.app` - passed.
- `adb -s emulator-5554 shell am start -n com.wonderfood.app/.MainActivity` plus `dumpsys activity` - showed resumed `com.wonderfood.app/.MainActivity`.
- Screenshot saved to `/tmp/wonderfood-notion-snapshot-sync.png`.

### Postgres/Supabase Snapshot Export Slice

- Added hosted Postgres snapshot export support for Supabase, PostgREST, and WonderFood server modes.
- Supabase/PostgREST export writes a canonical current snapshot record to `wonderfood_snapshots`; WonderFood server mode posts to `/snapshots`.
- Postgres onboarding now validates hosted API reachability, exports the current snapshot before saving the backend config, and reports exported bytes.
- Post-mutation active-backend snapshot sync now supports Postgres/Supabase API-token modes; direct PostgreSQL DSN remains connect-string-only until a server-side/mobile-safe adapter exists.
- Added focused Postgres URL/body tests for the snapshot export protocol.

### Postgres/Supabase Snapshot Export Validation

- Fixed hosted API validation call-site after expanding the gateway request helper for write bodies.
- `./gradlew :core:data:testDebugUnitTest :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.CredentialSecretCodecTest --tests com.wonderfood.app.sync.LegacyFoodMemorySnapshotExporterTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest --tests com.wonderfood.app.sync.NotionGatewayTest --tests com.wonderfood.app.sync.PostgresGatewayTest :app:assemblePlayDebug :app:assembleFossDebug` - passed.
- `adb -s emulator-5554 install -r app/build/outputs/apk/play/debug/app-play-debug.apk` - passed.
- `adb -s emulator-5554 shell pm clear com.wonderfood.app` - passed.
- `adb -s emulator-5554 shell am start -n com.wonderfood.app/.MainActivity` plus `dumpsys activity` - showed resumed `com.wonderfood.app/.MainActivity`.
- Screenshot saved to `/tmp/wonderfood-postgres-snapshot-sync.png`.

### Backend Switch Safety Slice

- Added backend-switch safety backups that create a local rollback `.wfcloudbackup` before committing a new active backend.
- Local, Google Sheets, Notion, and Postgres connect/switch flows now create a rollback snapshot before saving the new active backend configuration.
- Settings `Data home` summary now includes the latest backend-switch rollback snapshot label when available.
- Added focused Robolectric coverage proving the backend-switch safety snapshot and label are created.

### Backend Switch Safety Validation

- `./gradlew :core:data:testDebugUnitTest :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.CredentialSecretCodecTest --tests com.wonderfood.app.sync.LegacyFoodMemorySnapshotExporterTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest --tests com.wonderfood.app.sync.NotionGatewayTest --tests com.wonderfood.app.sync.PostgresGatewayTest --tests com.wonderfood.app.sync.WonderFoodBackendSwitchSafetyBackupTest :app:assemblePlayDebug :app:assembleFossDebug` - passed.
- `adb -s emulator-5554 install -r app/build/outputs/apk/play/debug/app-play-debug.apk` - passed.
- `adb -s emulator-5554 shell pm clear com.wonderfood.app` - passed.
- `adb -s emulator-5554 shell am start -n com.wonderfood.app/.MainActivity` plus `dumpsys activity` - showed resumed `com.wonderfood.app/.MainActivity`.
- Screenshot saved to `/tmp/wonderfood-backend-switch-safety.png`.

### Backend Onboarding UX Polish Slice

- Reworked the backend onboarding dialog from an all-fields-at-once form into a guided picker plus one active setup panel.
- Added safety/rollback copy explaining that WonderFood validates, exports a snapshot, and only then saves the backend choice.
- Added clearer provider copy for Local, Google Sheets, Notion, and Postgres/Supabase.
- Masked Notion and Postgres secret fields with password keyboard/visual transformation.

### Backend Onboarding UX Polish Validation

- `./gradlew :core:data:testDebugUnitTest :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.CredentialSecretCodecTest --tests com.wonderfood.app.sync.LegacyFoodMemorySnapshotExporterTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest --tests com.wonderfood.app.sync.NotionGatewayTest --tests com.wonderfood.app.sync.PostgresGatewayTest --tests com.wonderfood.app.sync.WonderFoodBackendSwitchSafetyBackupTest :app:assemblePlayDebug :app:assembleFossDebug` - passed.
- `adb -s emulator-5554 install -r app/build/outputs/apk/play/debug/app-play-debug.apk` - passed.
- `adb -s emulator-5554 shell pm clear com.wonderfood.app` - passed.
- `adb -s emulator-5554 shell am start -n com.wonderfood.app/.MainActivity` plus `dumpsys activity` - showed resumed `com.wonderfood.app/.MainActivity`.
- Screenshot saved to `/tmp/wonderfood-onboarding-polished.png`.

### Now/Food/Week/Cart Shell Slice

- Updated the bottom navigation shell to the plan-facing `Now`, `Food`, `Week`, and `Cart` destinations while preserving existing internal section routing.
- Folded recipes into the Food destination with a segmented hub: `Can make`, `In kitchen`, and `Saved`.
- Added `Can make` recipe ranking based on kitchen ingredient matches so meal planning starts from available food instead of a separate recipe library page.
- Kept the legacy Recipes section internally available for existing voice/detail routing, but removed it from the primary bottom navigation.

### Now/Food/Week/Cart Shell Validation

- `./gradlew :core:data:testDebugUnitTest :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.CredentialSecretCodecTest --tests com.wonderfood.app.sync.LegacyFoodMemorySnapshotExporterTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest --tests com.wonderfood.app.sync.NotionGatewayTest --tests com.wonderfood.app.sync.PostgresGatewayTest --tests com.wonderfood.app.sync.WonderFoodBackendSwitchSafetyBackupTest :app:assemblePlayDebug :app:assembleFossDebug` - passed.
- `adb -s emulator-5554 install -r app/build/outputs/apk/play/debug/app-play-debug.apk` - passed.
- `adb -s emulator-5554 shell pm clear com.wonderfood.app` - passed.
- `adb -s emulator-5554 shell am start -n com.wonderfood.app/.MainActivity` plus `dumpsys activity` - showed resumed `com.wonderfood.app/.MainActivity`.
- Screenshot saved to `/tmp/wonderfood-now-food-week-cart-shell.png`.

### Docs and Release Checklist Slice

- Updated README, privacy, security, roadmap, and feature docs for selectable data homes instead of local-only/five-destination claims.
- Documented Google Sheets, Notion, Postgres/Supabase, direct DSN, and backend-switch rollback behavior.
- Added `docs/release/RELEASE_CHECKLIST.md` covering signing, release OAuth, app links, provider proof, privacy/security, and physical-device proof gates.

### Docs and Release Checklist Validation

- `./gradlew :core:data:testDebugUnitTest :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.CredentialSecretCodecTest --tests com.wonderfood.app.sync.LegacyFoodMemorySnapshotExporterTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest --tests com.wonderfood.app.sync.NotionGatewayTest --tests com.wonderfood.app.sync.PostgresGatewayTest --tests com.wonderfood.app.sync.WonderFoodBackendSwitchSafetyBackupTest :app:assemblePlayDebug :app:assembleFossDebug` - passed.
- `adb -s emulator-5554 install -r app/build/outputs/apk/play/debug/app-play-debug.apk` - passed.
- `adb -s emulator-5554 shell pm clear com.wonderfood.app` - passed.
- `adb -s emulator-5554 shell am start -n com.wonderfood.app/.MainActivity` plus `dumpsys activity` - showed resumed `com.wonderfood.app/.MainActivity`.
- Screenshot saved to `/tmp/wonderfood-docs-release-checklist-current.png`.

### Release Evidence Collector Slice

- Added `scripts/quality/collect-release-evidence.sh` to gather local release proof into `build/evidence/release-*`.
- The collector records version, git head, signing env presence, signing fingerprints when available, release APK checksums/apksigner output when available, Google OAuth placeholder/configured status, assetlinks status, and connected-device evidence.
- Updated release docs/checklist to require this collector for signed-release evidence archival.

### Release Evidence Collector Validation

- `scripts/quality/collect-release-evidence.sh build/evidence/release-current-nosigning-2` - passed and produced evidence manifest.
- Evidence manifest correctly reports missing release signing environment, `google_web_client_id` placeholder, assetlinks placeholder, and zero signed release APKs.
- Fixed multi-device ADB handling in the collector so it reports `none_multiple_devices_set_ANDROID_SERIAL` instead of blocking when more than one device/emulator is connected.
- `./gradlew :core:data:testDebugUnitTest :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.CredentialSecretCodecTest --tests com.wonderfood.app.sync.LegacyFoodMemorySnapshotExporterTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest --tests com.wonderfood.app.sync.NotionGatewayTest --tests com.wonderfood.app.sync.PostgresGatewayTest --tests com.wonderfood.app.sync.WonderFoodBackendSwitchSafetyBackupTest :app:assemblePlayDebug :app:assembleFossDebug` - passed.
- `adb -s emulator-5554 install -r app/build/outputs/apk/play/debug/app-play-debug.apk` - passed.
- `adb -s emulator-5554 shell pm clear com.wonderfood.app` - passed.
- `adb -s emulator-5554 shell am start -n com.wonderfood.app/.MainActivity` plus `dumpsys activity` - showed resumed `com.wonderfood.app/.MainActivity`.
- Screenshot saved to `/tmp/wonderfood-release-evidence-collector-current.png`.

### Live Provider Proof and Settings Data Home Fix

- Fixed Settings `Data home` routing so returning users open the backend chooser instead of the import/export/privacy page.
- Added a Google Sheets readiness guard before app-side Google sign-in so missing OAuth client configuration reports a user-readable error instead of entering a fragile Google framework path.
- Created a dedicated Notion test page via the Notion API using `agent-env` credentials and connected WonderFood to it from the Android backend chooser.
- Verified in Chrome/Notion that the page contains a WonderFood snapshot heading and JSON code block written by the app.
- Verified in the Android app Settings summary that Notion became the active data home with `Notion exported 1 snapshot block` and a rollback snapshot label.
- Verified direct PostgreSQL DSN mode from the Android backend chooser using `agent-env` DSN inputs; Settings summary showed `Direct PostgreSQL connected` and rollback snapshot `Notion -> Direct PostgreSQL`.
- Created a real Google Sheet in Chrome and verified Google Sheets API write access using the user-approved OAuth browser flow.
- The Sheet was renamed `WonderFood Sync Test`, and a `wf_verification` tab was created with a timestamped `wonderfood_oauth=ok` check.
- Google Sheet URL: `https://docs.google.com/spreadsheets/d/1-cu0kk39SBUeKS326Sc5GHCEFGF5L3305fr0Pkpf6H4/edit`.

### Live Provider Proof Validation

- `./gradlew :core:data:testDebugUnitTest :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.CredentialSecretCodecTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest --tests com.wonderfood.app.sync.NotionGatewayTest --tests com.wonderfood.app.sync.PostgresGatewayTest --tests com.wonderfood.app.sync.WonderFoodBackendSwitchSafetyBackupTest :app:assemblePlayDebug` - passed.
- `adb -s emulator-5554` UI proof captured for Notion connect: `/tmp/wf-settings-datahome-notion-connect-final.png` and `/tmp/wf-notion-active-settings-check.png`.
- `adb -s emulator-5554` UI proof captured for Postgres direct DSN connect: `/tmp/wf-postgres-direct-connect-final2.png`.
- Chrome computer-use proof confirmed the Notion page content includes the WonderFood snapshot blocks.
- Google Sheets API proof returned `sheets_api_write=ok`, `spreadsheet_title=WonderFood Sync Test`, and tabs `Sheet1,wf_verification`.

### Remaining Release-Grade Gaps

- Android app-side Google Sheets OAuth on emulator/device is still not equivalent to signed-release OAuth proof; current proof covers browser OAuth plus real Sheets API write access.
- Release signing environment and release OAuth/app-links proof remain blocked by missing signing/app-link production configuration from the release evidence collector.
- Production-grade bidirectional Notion/Postgres sync is still snapshot-export oriented; full remote-to-local/live conflict handling remains beyond the verified slice.

### Backend Switch Rollback Checkpoint UI Slice

- Added an explicit switch checkpoint inside the backend chooser when the selected backend differs from the current active backend.
- The checkpoint names the source and target data homes and explains that WonderFood creates a rollback snapshot, verifies the new data home, exports a snapshot, and only then saves the switch.
- Added a required acknowledgment checkbox before switch connect buttons are enabled.
- Gated `Use this phone`, Google Sheets, Notion, and Postgres/Supabase connect actions behind the checkpoint when switching from an already-active backend.
- Fixed Settings `Data home` returning-user route to open the backend chooser, making the rollback checkpoint reachable after onboarding.

### Backend Switch Rollback Checkpoint UI Validation

- `./gradlew :app:assemblePlayDebug` - passed after the checkpoint UI changes.
- Installed the updated Play debug build on `emulator-5554` and opened Settings > Data home from an active Direct PostgreSQL state.
- Selected Notion and verified the chooser shows `Switch checkpoint` with copy for switching from `Postgres / Supabase` to `Notion`.
- Screenshot saved to `/tmp/wf-backend-switch-checkpoint-ui-notion.png`.

### Notion/Postgres Remote Snapshot Read Foundation Slice

- Added Notion remote snapshot read support by paging page children, locating the latest `WonderFood snapshot` heading group, assembling captioned code-block chunks, and decoding the canonical `WonderFoodSnapshot` JSON.
- Added Postgres/Supabase/PostgREST remote snapshot read support for hosted modes via `wonderfood_snapshots` current-household query URLs and WonderFood server `/snapshots/{household}/current` URLs.
- Added Postgres response parsing for PostgREST/Supabase row arrays and WonderFood server object responses, decoding `snapshot_json` or nested `snapshot` payloads.
- Updated Notion connect flow to read remote snapshot before export and surface existing remote WonderFood data in the Settings/detail/status messages.
- Updated Postgres hosted connect flow to read remote snapshot before export and surface existing remote WonderFood data in the Settings/detail/status messages.
- Direct PostgreSQL DSN remains config-only because mobile direct DB access still requires a server-side or driver-backed sync adapter.

### Notion/Postgres Remote Snapshot Read Foundation Validation

- `./gradlew :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.NotionGatewayTest --tests com.wonderfood.app.sync.PostgresGatewayTest :app:assemblePlayDebug` - passed.
- Added focused tests for latest complete Notion snapshot group parsing, incomplete Notion chunk handling, Postgres read URL generation, PostgREST row decoding, and empty Supabase row handling.

## 2026-07-19 real Notion and Sheets workspace implementation
- Built real Notion workspace export on top of the existing snapshot backup: `WonderFood Kitchen`, `WonderFood Shopping`, `WonderFood Meal Plan`, `WonderFood Recipes`, and `WonderFood Spending` databases are provisioned under the selected Notion page and rows are upserted by `External ID`.
- Wired Notion onboarding and active backend sync to export both structured workspace databases and chunked JSON snapshot backup using the same app snapshot/update timestamp.
- Built readable Google Sheets tabs alongside machine sync tabs: `Kitchen`, `Shopping`, `Meal Plan`, `Recipes`, `Spending`, and `Dashboard` with workflow headers and regenerated rows.
- Google Sheets exports now refresh machine rows and human workspace rows; Dashboard includes family-level metrics and a spending total formula.
- Added focused tests for readable Sheets schema/dashboard rows and Notion database body shape.
- Verification: `./gradlew :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.GoogleSheetsGatewayTest --tests com.wonderfood.app.sync.NotionGatewayTest :app:assemblePlayDebug` passed on 2026-07-19.
- Remaining proof gap: live OAuth/Notion API visual verification has not been re-run after this workspace upgrade in this pass.

## 2026-07-19 schema.org alignment pass
- Added a single `WonderFoodWorkspaceSchema` adapter for human backends so Sheets and Notion share one schema.org-shaped contract instead of duplicate hand-written columns.
- Canonical workspace fields now use schema.org-style names including `@type`, `identifier`, `name`, `description`, `status`, `requiredQuantity`, `unitText`, `startDate`, `recipeYield`, `prepTime`, `cookTime`, `recipeIngredient`, `recipeInstructions`, `seller`, `value`, `currency`, and `dateModified`.
- Google Sheets human tabs are generated from the shared schema: `Kitchen`, `Shopping`, `Meal Plan`, `Recipes`, `Spending`, and `Dashboard`.
- Notion databases are generated from the shared schema with `WonderFood <tab>` database names, and existing legacy Notion title fields are repaired from `Item`/`Meal`/`Recipe`/`Purchase` to canonical `name`.
- Notion upsert key changed from legacy `External ID` to canonical `identifier`.
- Verification: `./gradlew :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.GoogleSheetsGatewayTest --tests com.wonderfood.app.sync.NotionGatewayTest :app:assemblePlayDebug` passed twice after the schema changes.
- Live proof: existing Notion test page `3a25dd53-5a93-81fc-9012-ef6def97e83a` was migrated/seeded with schema.org-aligned properties; `WonderFood Dashboard` database was created; 14 rows upserted. Existing Google Sheet `1-cu0kk39SBUeKS326Sc5GHCEFGF5L3305fr0Pkpf6H4` was reseeded with canonical headers across six tabs and 15 rows/formulas.
