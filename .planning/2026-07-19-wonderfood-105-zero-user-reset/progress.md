# Progress

## 2026-07-19

- Re-reviewed the previous household-sync plan after the user clarified WonderFood has zero production users.
- Replaced migration-first assumptions with an intentional destructive architecture reset.
- Defined one canonical household model for items, inventory, shopping, recipes, meals, purchases, nutrition, proposals, audit, and sync.
- Defined SQLite as the always-present offline replica and exactly one selected durable data home.
- Defined a shared three-way sync protocol and provider-specific Notion, Sheets, and Postgres projections.
- Added implementation phases, acceptance gates, stop conditions, issue mapping, rollback, and future-feature boundaries.
- Activated this plan as `.planning/.active_plan`.
- Reframed Notion and Google Sheets as independent household products rather than provider projections.
- Reduced conflict scope to a small data-loss safety mechanism for overlapping high-risk fields.
- Added a provider-native workspace contract for Notion dashboards/views and a formula-driven Sheets workbook with no deployment or Apps Script dependency.
- Refreshed official Notion and Google Sheets API constraints and capabilities as of 2026-07-19.
- Created release branch `codex/wonderfood-105-zero-user-reset`.

## Current state

Planning amended and complete. No production code has been changed. Phase 0 is the next implementation action.

## Phase 0 implementation start

- Added a pure Kotlin canonical household domain under `core:model` for food and non-food items, inventory lots/events, shopping, recipes, meals, purchases, spending, nutrition, proposals, and audit.
- Added one provider-neutral workspace field registry for Home, Kitchen, Shopping, Meals, Recipes, Spending, and hidden support tables.
- Added the deliberately narrow conflict policy: automatic one-sided sync, disjoint merge, provider-home precedence for low-risk overlaps, and review for high-risk or archive overlaps.
- Added focused domain and conflict contract tests.
- Verified `./gradlew :core:model:test --tests com.wonderfood.core.model.household.HouseholdContractTest`: BUILD SUCCESSFUL.

## 2026-07-20 Phase 0 continuation

- Re-read the active v1.0.5 zero-user reset plan, architecture, schema, findings, and progress before coding.
- Confirmed current branch `codex/wonderfood-105-zero-user-reset` already contains Phase 0 `core:model` household files and no production runtime migration yet.
- Added `HouseholdRuntimeContract` to inventory affected command, storage, UI, provider, backup, AI, and test surfaces and mark legacy runtime surfaces for replacement or deletion after callers migrate.
- Added `HouseholdSyncContract` with provider lifecycle operations, sync envelopes, remote bindings, sync bases, outbox records, tombstones, conflict records, and `latest-safety` snapshot contracts.
- Extended `HouseholdContractTest` with Phase 0 acceptance checks for runtime surface coverage, canonical mutation boundary, adapter lifecycle, schema-drift rejection, outbox operation limits, and conflict evidence retention.
- First focused test run failed because new files referenced `HouseholdDomain.kt`'s private `requireText` helper; fixed by using file-local validation helpers in the new contract files.
- Verified `./gradlew :core:model:test --tests com.wonderfood.core.model.household.HouseholdContractTest`: BUILD SUCCESSFUL.
- Marked Phase 0 complete in `task_plan.md`; Phase 1 remains the next implementation phase.

## 2026-07-20 Phase 1 foundation

- Started Phase 1 at the core runtime boundary rather than UI/provider migration, because the app still has two architectures: legacy `FoodChatStore` for live app state and typed core modules that were mostly unwired.
- Added `HouseholdRepository`, `HouseholdRepositoryCommand`, `HouseholdRepositoryResult`, and `HouseholdRepositories.room(...)` as the canonical mutation/repository entry point in `core:data`.
- Added canonical Room v3 tables and DAO coverage for households, household items, inventory lots, shopping lines, change proposals, command records, and sync outbox.
- Bumped `WonderFoodDatabase` to version 3, exposed `householdDao()`, added the new entities, and added `MIGRATION_2_3` plus generated schema `core/data/schemas/com.wonderfood.core.data.room.WonderFoodDatabase/3.json`.
- Added `RoomHouseholdRepository` mapping between `core:model` household domain types and the new Room tables.
- Added `RoomHouseholdRepositoryTest` covering idempotent command application, restart persistence for a non-food household item, unknown quantity remaining unknown/not zero, pending proposal storage, and sync outbox staging without mutating item/cart tables.
- First focused repository test failed because the restart case wrote to an in-memory DB and reopened a file DB; fixed the test to use the file-backed database for that scenario.
- Refreshed the official Android Room migration guidance and kept schema export/current-version verification while aligning this branch with the user's no-backcompat direction.
- Renamed the default Room database to `wonderfood-v105-household.db` and enabled explicit destructive fallback for this reset branch.
- Verified `./gradlew :core:data:testDebugUnitTest --tests com.wonderfood.core.data.room.RoomHouseholdRepositoryTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :core:data:testDebugUnitTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :core:model:test --tests com.wonderfood.core.model.household.HouseholdContractTest`: BUILD SUCCESSFUL.
- Phase 1 remains incomplete: app launch/state, commands, backup, search, providers, importers, and legacy `FoodMemory` removal still need migration to the canonical repository.

## 2026-07-20 Phase 1 command boundary

- Added `core:engine` household commands and `HouseholdCommandExecutor` so canonical mutations route through engine validation before persistence.
- Moved the repository command/result surface from `core:data` to `core:engine` and made `HouseholdRepository` extend `HouseholdCommandRepository`.
- Updated `RoomHouseholdRepository` to implement `HouseholdCommand` application and preserve duplicate-command behavior by command ID.
- Added `HouseholdCommandExecutorTest` covering valid command application and rejection before repository mutation when a command household does not match the target entity household.
- Verified `./gradlew :core:engine:test --tests com.wonderfood.core.engine.HouseholdCommandExecutorTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :core:data:testDebugUnitTest --tests com.wonderfood.core.data.room.RoomHouseholdRepositoryTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- UI/emulator proof has not been run in this slice because no UI surface was migrated yet; keep emulator proof mandatory once Phase 1 reaches app state or Phase 2 UI work.

## 2026-07-20 Phase 1 canonical search

- Added `HouseholdRepository.searchItems(householdId, query)` and Room-backed search across canonical item name, category, brand, and notes.
- Added repository test coverage proving search returns both food and non-food canonical items and ignores blank queries.
- Focused repository test passed: `./gradlew :core:data:testDebugUnitTest --tests com.wonderfood.core.data.room.RoomHouseholdRepositoryTest`.
- A parallel app assemble initially failed with generated KSP cache corruption at `core/data/build/kspCaches/debug/symbolLookups/lookups.tab_i`; cleared only generated files under `core/data/build/kspCaches/debug` and reran.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL after clearing the generated KSP cache.

## 2026-07-20 Phase 1 app draft mirror

- Added `HouseholdDraftCommandMapper` in `app:data` to map accepted app drafts into canonical household commands for inventory items/lots, shopping lines, and receipt inventory/household lines.
- Mapper preserves unknown quantity as unknown, maps non-food receipt household lines to `ItemKind.HOUSEHOLD`, and leaves unsupported draft types empty so AI/imports do not mutate unsupported domain tables directly.
- Added `HouseholdDraftCommandMapperTest` covering inventory, grocery, non-food receipt, and unsupported recipe draft behavior.
- Wired `MainScreenViewModel.executeDraftCommand(...)` to mirror supported accepted drafts into `HouseholdRepository` through `HouseholdCommandExecutor`; failures update sync status and do not block local use.
- This is a transitional Phase 1 foothold, not final completion: legacy `FoodChatStore` remains the visible read/write authority until UI state, backup, providers, importers, and remaining commands migrate.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest --tests com.wonderfood.app.data.FoodDraftCommandExecutorTest`: BUILD SUCCESSFUL. The first compile attempt hit a generated Kotlin incremental class backup/file-missing issue, then completed successfully using the fallback non-daemon strategy.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `./gradlew :core:data:testDebugUnitTest --tests com.wonderfood.core.data.room.RoomHouseholdRepositoryTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :core:engine:test --tests com.wonderfood.core.engine.HouseholdCommandExecutorTest`: BUILD SUCCESSFUL.
- UI/emulator proof still not run because this slice did not change user-visible UI behavior; run emulator proof once UI reads or main navigation migrate.

## 2026-07-20 Phase 1 canonical read summary

- Added `CanonicalHouseholdUiSummary` to turn a canonical household snapshot into a compact UI status label.
- Wired `MainScreenViewModel` to create the v1.0.5 household database/repository, seed the default canonical household on startup, refresh a canonical summary, and refresh it after mirrored draft commands.
- Added a Settings home row for "Canonical household store" so the app exposes the canonical repository readiness/counts while the rest of Phase 1 migrates.
- Added focused unit coverage for empty and populated canonical summary labels.
- Extended `MainScreenTest.aMainScreenShowsFiveDestinationShell` to assert the Settings row and empty canonical repository label on the emulator.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.CanonicalHouseholdUiSummaryTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wonderfood.app.ui.main.MainScreenTest#aMainScreenShowsFiveDestinationShell`: BUILD SUCCESSFUL, 1/1 tests passed on Pixel_3a_API_34_extension_level_7_arm64-v8a API 34 emulator.
- Post-test emulator state returned to the launcher. Screenshot probe saved `/tmp/wonderfood-ui-proof.png`, but the connected Compose assertion is the reliable UI proof.
- Phase 1 remains incomplete: visible app state, backup/import/export, provider sync callers, and legacy runtime deletion still need migration to `HouseholdRepository`.

## 2026-07-20 Phase 1 canonical search read path

- Re-read the pasted orchestration brief and active planning files before continuing.
- Confirmed the global Search pane still built kitchen results from `FoodMemory.globalSearchResults()` while `HouseholdRepository.searchItems(...)` already existed in `core:data`.
- Added `CanonicalHouseholdSearchItem` as an app-facing presentation model for canonical item search rows, including non-food labels without food-only details.
- Added `MainScreenViewModel.updateSearchQuery(...)` with a cancellable background job that clears blank queries and queries `HouseholdRepository.searchItems(...)` for typed search terms.
- Threaded canonical search items into `MainScreen` and `SearchContent`; typed search now renders canonical item matches first and excludes legacy `kitchen` results from the filtered legacy list. Recipes, meals, plans, receipts, and shopping remain legacy read paths until their canonical tables/screens migrate.
- Made `SearchResult.target` nullable so canonical rows do not pretend their UUIDs are legacy detail IDs before canonical detail screens exist.
- Added a stable "WonderFood search text" content description for the search field.
- Added `CanonicalHouseholdSearchItemTest` covering a non-food cleaning item label.
- Extended `MainScreenTest` with `gSearchUsesCanonicalHouseholdRepository`, which seeds a canonical cleaning item through `HouseholdCommandExecutor`, opens Search, types "canonical proof", and asserts the canonical result row.
- First unit compile after wiring failed because `onSearchQueryChange` was missing from `MainWorkspace` and the preview call; fixed both thread-through gaps.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.CanonicalHouseholdSearchItemTest --tests com.wonderfood.app.data.CanonicalHouseholdUiSummaryTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL. Gradle first hit a Kotlin daemon/incremental missing-class backup issue, then completed successfully using fallback no-daemon compilation.
- Verified `./gradlew :app:assembleFossDebugAndroidTest`: BUILD SUCCESSFUL.
- Attempted `./gradlew :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wonderfood.app.ui.main.MainScreenTest#gSearchUsesCanonicalHouseholdRepository`; Gradle built the app and instrumentation APKs, then failed before execution with `DeviceException: No connected devices!`.
- `adb devices -l` showed no devices. Starting `Pixel_3a_API_34_extension_level_7_arm64-v8a` initially failed with `unknown skin name 'pixel_3a'`. Retrying with `-skin 1080x2220` crashed during graphics init: `Failed to allocate ColorBuffer with Vulkan backing` / `Failed to find memory type for ColorBuffers`. Retrying with `-gpu swiftshader_indirect` still produced no ADB device within the short wait. Emulator proof for the new search test remains pending on a working AVD/device.
- Phase 1 remains incomplete: more visible read state, backup/import/export, provider sync callers, and legacy runtime deletion still need migration to `HouseholdRepository`.

## 2026-07-20 Phase 1 canonical encrypted backup

- Inspected `WonderFoodBackupGateway` and confirmed encrypted backup, Google Drive backup, restore safety backup, and backend-switch safety backup still package the legacy `wonderfood.db` and count `FoodMemory`.
- Added a canonical `WonderFoodBackupGateway.createEncryptedBackup(passphrase, HouseholdSnapshot)` overload that packages `wonderfood-v105-household.db`, writes a `wonderfood.household-backup.v105` manifest, records household ID, active data home, schema version, canonical object counts, and excludes secrets.
- Updated encrypted restore parsing to accept either the legacy `wonderfood.db` entry or the canonical `wonderfood-v105-household.db` entry from the manifest.
- Updated `BackupSnapshot` to report the restored/created database name while preserving the previous default for existing legacy callers.
- Switched `MainScreenViewModel.createEncryptedBackup(...)` from `store.readMemory()` to `ensureCanonicalHousehold()`, `HouseholdRepository.snapshot(...)`, a Room WAL checkpoint, and the canonical backup overload.
- Updated encrypted restore in `MainScreenViewModel` to close and reopen the canonical Room database/repository/executor around restore, then refresh the canonical summary.
- Added `WonderFoodCanonicalBackupGatewayTest` proving encrypted backup and restore use the canonical DB filename, canonical household object count, and canonical DB bytes without using `FoodMemory`.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodCanonicalBackupGatewayTest --tests com.wonderfood.app.sync.WonderFoodBackendSwitchSafetyBackupTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Remaining backup work: Google Drive backup, restore safety backup, and backend-switch safety backup still use legacy `FoodMemory`; those must move to canonical snapshots before Phase 1 backup acceptance is complete.

## 2026-07-20 Phase 1 canonical Drive and safety backups

- Continued from the canonical encrypted backup slice and inspected the remaining backup call sites.
- Added canonical `WonderFoodBackupGateway.createGoogleDriveBackup(HouseholdSnapshot)`, `createRestoreSafetyBackup(HouseholdSnapshot)`, and `createBackendSwitchSafetyBackup(HouseholdSnapshot, ...)` overloads.
- The canonical Google Drive payload now packages `wonderfood-v105-household.db`, writes a `wonderfood.household-cloud-backup.v105` manifest, caches the latest cloud backup, and returns `BackupSnapshot.databaseName` as the canonical DB filename.
- Backend-switch safety manifests now record the canonical database filename alongside size/count metadata.
- Added `MainScreenViewModel.canonicalHouseholdSnapshotForBackup()` as the single canonical backup snapshot path: it ensures the default household, checkpoints the Room WAL, and reads `HouseholdRepository.snapshot(...)`.
- Switched production `backupToGoogleDrive(...)`, Google restore safety backup creation, and backend-switch safety backup creation from `store.readMemory()` to canonical snapshots.
- Updated Google backup/restore user-facing status text from "food objects" to "household objects".
- Google Drive restore now closes/reopens the canonical Room database around DB replacement and refreshes the canonical summary after restore.
- Extended `WonderFoodCanonicalBackupGatewayTest` to cover canonical Google Drive payloads, restore preview, cloud restore, restore safety backup, and backend-switch safety backup.
- Verified production backup call sites in `MainScreenViewModel` now use canonical snapshot overloads; legacy gateway overloads remain for old tests and compatibility inside the gateway surface until legacy tests are removed.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodCanonicalBackupGatewayTest --tests com.wonderfood.app.sync.WonderFoodBackendSwitchSafetyBackupTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Remaining Phase 1 work: visible app read state, importers, provider sync callers, and eventual legacy runtime/test deletion still need migration to `HouseholdRepository`.

## 2026-07-20 Phase 1 canonical CSV export

- Inspected import/export/provider legacy callers and selected CSV export as the next bounded migration because `MainScreenViewModel.exportCsvTo(...)` directly read `store.readMemory()`.
- Added `WonderFoodCsvGateway.export(HouseholdSnapshot)` to export implemented canonical household data in the existing CSV shape:
  - canonical `Item` plus `InventoryLot` rows export as `inventory`;
  - canonical `ShoppingLine` rows export as `grocery`;
  - canonical IDs remain UUID strings in `id`/`parent_id`;
  - non-food category/kind and source labels are preserved without nutrition-only fields.
- Switched production `exportCsvTo(...)` from `WonderFoodCsvGateway.export(memory)` to `WonderFoodCsvGateway.export(snapshot)` via `canonicalHouseholdSnapshotForBackup()`.
- Updated CSV export status text to report canonical item, lot, and shopping-line counts.
- Added `WonderFoodCsvGatewayTest.exportsCanonicalHouseholdItemsAndShoppingRows` proving canonical inventory and shopping rows export and parse back through the existing CSV parser.
- First parallel Gradle verification attempt caused Kotlin incremental storage/cache errors (`source-to-classes.tab already registered`) and a noisy compile with bogus unresolved symbols. Stopped Gradle daemons and reran serially.
- First serial test run failed because the assertion forgot the CSV `title` column between `name` and `quantity`; fixed the expected row substrings.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodCsvGatewayTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Confirmed `exportCsvTo(...)` now calls `WonderFoodCsvGateway.export(snapshot)`; remaining import/provider paths still have `FoodMemory` reads.

## 2026-07-20 Phase 1 canonical CSV import for supported rows

- Added `WonderFoodCsvImport.canImportDirectlyToCanonicalHousehold()` to identify CSV files that contain only inventory and/or grocery rows.
- Changed `MainScreenViewModel.applyCsvImport(...)` to route inventory/grocery-only imports through `HouseholdDraftCommandMapper` and `HouseholdCommandExecutor` instead of the legacy draft executor.
- The canonical CSV import path validates the generated draft, ensures the default household exists, executes canonical household commands, refreshes the canonical Settings summary, and records only an assistant chat/status message in `FoodChatStore`.
- Mixed CSV files containing recipes, meal logs, meal plans, or preferences still use the legacy import path until those canonical tables and screens migrate.
- Added `WonderFoodCsvGatewayTest.classifiesInventoryAndShoppingCsvAsCanonicalDirectImport` for the new import classifier.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodCsvGatewayTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable for this slice.

## 2026-07-20 Phase 1 canonical provider export source

- Re-read the pasted orchestration brief, active plan, architecture, schema, progress, and relevant memory registry before continuing.
- Confirmed production provider export paths still created `WonderFoodSnapshot` payloads with `LegacyFoodMemorySnapshotExporter.toSnapshot(store.readMemory())` in Google Sheets connect, Notion connect, hosted Postgres connect, and debounced backend snapshot sync.
- Added `CanonicalHouseholdSnapshotExporter` to project the currently implemented canonical `HouseholdSnapshot` data into the provider-facing `WonderFoodSnapshot` payload shape:
  - canonical `Item` rows project to provider food/page rows;
  - canonical `InventoryLot` rows project to provider stock-lot rows;
  - canonical `ShoppingLine` rows project to provider shopping rows;
  - canonical `Purchase` rows project to provider receipt rows;
  - recipes, meal plans, meal logs, nutrition, and events remain empty in this projection until canonical persistence for those domains lands.
- Added `MainScreenViewModel.canonicalWorkspaceSnapshotForProviders()` so provider snapshot exports share the same canonical repository snapshot and Room WAL checkpoint path used by canonical backups.
- Switched Google Sheets connect/export, Notion connect/export, hosted Postgres connect/export, and `exportSnapshotToActiveBackend(...)` from legacy `FoodMemory` reads to `canonicalWorkspaceSnapshotForProviders()`.
- Added `CanonicalHouseholdSnapshotExporterTest.projectsCanonicalHouseholdInventoryAndShoppingForProviderSnapshots` for non-food inventory plus shopping projection IDs, quantities, statuses, and the deliberate empty recipe surface.
- First focused Gradle run failed because `Household` has direct timestamps rather than entity `metadata`; fixed the exporter timestamp source.
- Second focused Gradle run failed because the new test used `kotlin.test` imports while this source set uses JUnit; switched to `org.junit` assertions.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.CanonicalHouseholdSnapshotExporterTest --tests com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest --tests com.wonderfood.app.sync.NotionGatewayTest --tests com.wonderfood.app.sync.PostgresGatewayTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "LegacyFoodMemorySnapshotExporter.toSnapshot\\(store.readMemory\\(\\)\\)|canonicalWorkspaceSnapshotForProviders|CanonicalHouseholdSnapshotExporter" app/src/main/java app/src/test/java`: no production provider export site still uses the direct legacy `FoodMemory` snapshot pattern.
- Checked `adb devices -l`: no connected devices, so no emulator/device UI proof was available for this backend-only slice.

## 2026-07-20 Phase 1 canonical provider import review for supported rows

- Continued from the canonical provider export checkpoint and selected provider import-review confirmation as the next bounded caller migration.
- Confirmed `confirmSheetsImportPreview()` still converted remote `WonderFoodSnapshot` payloads into legacy drafts and always executed them through `executeDraftCommand(...)`, which writes legacy `FoodMemory` before mirroring supported rows.
- Added `LegacySnapshotDraftImporter.canImportDirectlyToCanonicalHousehold(...)` for remote snapshots that contain only implemented canonical import surfaces: foods, stock lots, and shopping items.
- Unsupported provider snapshot surfaces still force the existing legacy path: recipes, meal plans, meal logs, receipts, nutrition snapshots, food events, relations, and attachments.
- Extracted `MainScreenViewModel.applyCanonicalDraftImport(...)` and reused it for canonical CSV imports and supported provider import-review snapshots.
- Updated `confirmSheetsImportPreview()` so supported provider imports execute canonical household commands directly, refresh the canonical summary, avoid legacy `FoodMemory` writes, and preserve the conflict-inbox decision record.
- Fixed a Kotlin control-flow bug caught during verification: an Elvis expression would have executed the legacy fallback even after a canonical import message. Replaced it with an explicit `if (canonicalImportMessage == null)` branch.
- Added `LegacySnapshotDraftImporterTest.classifiesProviderSnapshotsForDirectCanonicalImport` to prove simple inventory/shopping snapshots can import canonically and mixed snapshots with recipe/nutrition data cannot.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest --tests com.wonderfood.app.sync.CanonicalHouseholdSnapshotExporterTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "canImportDirectlyToCanonicalHousehold|applyCanonicalDraftImport|confirmSheetsImportPreview|executeDraftCommand\\(" app/src/main/java/com/wonderfood/app/ui/main/MainScreenViewModel.kt app/src/main/java/com/wonderfood/app/sync/LegacySnapshotDraftImporter.kt app/src/test/java/com/wonderfood/app/sync/LegacySnapshotDraftImporterTest.kt`: supported provider import branch is present; legacy execution remains only for mixed/unsupported imports and other app actions.
- Checked `adb devices -l`: no connected devices, so no emulator/device UI proof was available for this backend/import slice.

## 2026-07-20 Phase 1 canonical external proposal acceptance for supported rows

- Re-read the pasted orchestration brief and active plan before continuing.
- Inspected `CommandEnvelopeDraftMapper`, `WonderFoodDeepLink`, `MainActivity`, and `MainScreenViewModel` external proposal staging.
- Confirmed external WonderFood proposal packages are staged as pending drafts and only write when `acceptDraft()` runs with `pendingDraftOrigin == EXTERNAL_PROPOSAL`.
- Updated `acceptDraft()` so external proposals composed entirely of canonical-supported draft types write directly through `applyCanonicalDraftImport(...)` instead of `executeDraftCommand(...)`.
- The direct canonical external proposal path supports `InventoryDraft`, `GroceryDraft`, `ReceiptDraft`, and composites made only from those types. It rejects mixed composites containing recipes, meal logs, meal plans, or link actions back to the existing legacy review path so no unsupported draft is silently dropped.
- `applyCanonicalDraftImport(...)` now also queues backend snapshot sync with the accepted origin label, preserving the old "accepted draft triggers backend sync" behavior for canonical-only writes.
- Added `HouseholdDraftCommandMapperTest.externalProposalSupportedDraftsCreateCanonicalCommands` to prove supported external proposal composites generate canonical commands with `external_proposal` source.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest --tests com.wonderfood.app.ai.CommandEnvelopeDraftMapperTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "canApplyDirectlyToCanonicalHousehold|External proposal saved to canonical|queueBackendSnapshotSync\\(origin.writeSource\\)|externalProposalSupportedDraftsCreateCanonicalCommands" app/src/main/java/com/wonderfood/app/ui/main/MainScreenViewModel.kt app/src/test/java/com/wonderfood/app/data/HouseholdDraftCommandMapperTest`: external canonical accept branch, backend sync queue, and test are present.
- Checked `adb devices -l`: no connected devices, so no emulator/device UI proof was available for this backend/proposal acceptance slice.

## 2026-07-20 Phase 1 Now dashboard canonical read metric

- Re-read the pasted orchestration brief, active plan, and latest progress before continuing.
- Inspected `MainScreen` and confirmed the Food/Cart item bodies still depend heavily on legacy `InventoryItem` and `GroceryItem` models, making a full visible list swap too broad for this bounded continuation.
- Selected the Now dashboard as a safe primary-surface read migration because it can display canonical repository state without pretending canonical detail screens exist.
- Added `CanonicalHouseholdUiSummary.dashboardLabel()` for compact non-empty household/cart counts.
- Threaded `state.canonicalSummary` through `TodayContent(...)` into `TodayDashboard(...)`.
- Added a canonical household metric pill to the Now dashboard whenever canonical items/cart lines exist. Settings remains the detailed canonical status surface.
- Added `CanonicalHouseholdUiSummaryTest.dashboardLabelOnlyAppearsForPopulatedCanonicalState`.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.CanonicalHouseholdUiSummaryTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "dashboardLabel|canonicalDashboardLabel|TodayContent\\(|TodayDashboard\\(" app/src/main/java/com/wonderfood/app/data/CanonicalHouseholdUiSummary.kt app/src/main/java/com/wonderfood/app/ui/main/MainScreen.kt app/src/test/java/com/wonderfood/app/data/CanonicalHouseholdUiSummaryTest`: Now dashboard receives and renders the canonical summary metric.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This is a visible UI change with compile/unit proof only until a device/AVD is available.

## 2026-07-20 Phase 1 canonical Cart preview read path

- Continued from the Now dashboard canonical metric checkpoint and selected a Cart read path that would not disturb legacy buy/delete actions.
- Added `CanonicalCartPreviewItem` to derive read-only cart rows from canonical `HouseholdSnapshot.shoppingLines`.
- The preview filters archived, purchased, and skipped canonical shopping lines, keeps `NEEDED` and `IN_CART`, and displays compact quantity/category/reason text.
- `refreshCanonicalSummary()` now reads one canonical snapshot and updates both `canonicalSummary` and `canonicalCartPreview` in `WonderFoodUiState`.
- Threaded `state.canonicalCartPreview` into `GroceryContent(...)`.
- Added a read-only `Canonical cart` section above the legacy Cart `To buy` list when canonical shopping lines exist. Existing legacy cart actions remain unchanged until canonical detail/action handlers migrate.
- Added `CanonicalCartPreviewItemTest.filtersArchivedAndPurchasedLinesForCartPreview`.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.CanonicalCartPreviewItemTest --tests com.wonderfood.app.data.CanonicalHouseholdUiSummaryTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "CanonicalCartPreviewItem|canonicalCartPreview|CanonicalCartPreviewSection" app/src/main/java app/src/test/java`: preview model, ViewModel state, UI section, and tests are present.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This visible Cart read-path change has compile/unit proof only until a device/AVD is available.

## 2026-07-20 Phase 1 canonical Kitchen preview read path

- Re-read the pasted orchestration brief, active plan, latest progress, and goal budget before continuing.
- Ran the planning-with-files catchup script; it produced no unsynced-context report.
- Added `CanonicalKitchenPreviewItem` to derive read-only kitchen rows from canonical `HouseholdSnapshot.items` plus active inventory lots.
- The preview filters archived items/lots and only includes active `AVAILABLE`, `OPENED`, or `RESERVED` lots. It displays compact quantity, item kind, and category text while preserving unknown quantity as unknown instead of zero.
- `refreshCanonicalSummary()` now reads one canonical snapshot and updates `canonicalSummary`, `canonicalCartPreview`, and `canonicalKitchenPreview` together.
- Threaded `state.canonicalKitchenPreview` into `FoodHubContent(...)`.
- Added a read-only `Household kitchen` section above the legacy Kitchen list when canonical inventory exists. Existing legacy Kitchen actions remain unchanged until canonical detail/action handlers migrate.
- Added `CanonicalKitchenPreviewItemTest.showsOnlyActiveUnarchivedInventoryItems`.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.CanonicalKitchenPreviewItemTest --tests com.wonderfood.app.data.CanonicalCartPreviewItemTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "CanonicalKitchenPreviewItem|canonicalKitchenPreview|CanonicalKitchenPreviewSection|Household kitchen" app/src/main/java app/src/test/java`: preview model, ViewModel state, UI section, and tests are present.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This visible Kitchen read-path change has compile/unit proof only until a device/AVD is available.

## 2026-07-20 Phase 1 supported reviewed drafts canonical-only

- Re-read the pasted orchestration brief, active plan, latest progress, git state, and goal budget before continuing.
- Did a targeted memory quick-pass for WonderFood/FoodChatStore context; used current repo/planning files as authoritative.
- Inspected `acceptDraft()`, `createManual(...)`, `submitToAi(...)`, `executeDraftCommand(...)`, `applyCanonicalDraftImport(...)`, and `HouseholdDraftCommandMapper`.
- Confirmed ordinary reviewed AI/receipt/manual/voice supported drafts were still writing through `FoodDraftCommandExecutor`/`FoodChatStore` first and then mirroring to the canonical repository.
- Added `executeCanonicalDraftCommand(...)`, which normalizes and validates with `FoodDraftValidator` plus `FoodDraftCommandPolicy`, executes canonical `HouseholdCommand`s, refreshes canonical UI state, and queues backend snapshot sync.
- Changed accepted supported drafts (`InventoryDraft`, `GroceryDraft`, `ReceiptDraft`, and composites made only of those types) to route directly through canonical commands for all origins, not just external proposals.
- Changed manual inventory/grocery saves and supported voice auto-accept drafts to use the same canonical-only route. Unsupported recipe/meal/link drafts still use the legacy executor until their canonical models and screens are implemented.
- Added `HouseholdDraftCommandMapperTest.supportedReviewedDraftOriginsCarryCanonicalSourceLabels` to prove manual and voice auto-accept canonical commands preserve source labels.
- First compile attempt failed because `FoodDraftCommandPolicy` was missing from imports and `submitToAi(...)` was not suspend-capable. Fixed by importing the policy and making `submitToAi(...)` suspend; its callers already run in `viewModelScope.launch(Dispatchers.IO)`.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest --tests com.wonderfood.app.data.FoodDraftCommandExecutorTest`: BUILD SUCCESSFUL after the fix.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "executeCanonicalDraftCommand|canApplyDirectlyToCanonicalHousehold\\(|FoodDraftCommandPolicy|VOICE_AUTO_ACCEPT|MANUAL_SAVE|Saved .* to household" app/src/main/java/com/wonderfood/app/ui/main/MainScreenViewModel.kt app/src/test/java/com/wonderfood/app/data/HouseholdDraftCommandMapperTest`: canonical-only routes, validation policy, and source-label test are present.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable for this routing change.

## 2026-07-20 Phase 1 canonical Cart preview actions

- Re-read the pasted orchestration brief, active plan, latest progress, git state, and goal budget before continuing.
- Inspected canonical `HouseholdCommand`, `RoomHouseholdRepository`, `HouseholdDao`, legacy mutation handlers, and existing Cart preview UI.
- Confirmed the canonical command layer currently supports upsert-style updates but not a separate archive command, so the minimal aligned path was to copy and upsert canonical `ShoppingLine` rows with updated status/metadata.
- Added `CanonicalCartMutationCommandFactory` for `MarkShoppingLinePurchased` and `ArchiveShoppingLine` commands. The factory preserves the canonical shopping line id, increments revision, updates metadata timestamps/source, and records affected entity ids.
- Added `markCanonicalCartLinePurchased(...)` and `archiveCanonicalCartLine(...)` ViewModel handlers. They load the canonical household snapshot, find the canonical shopping line by `EntityId`, execute the generated household command, refresh canonical UI state, and queue backend snapshot sync with `canonical_cart` source.
- Threaded canonical Cart handlers through `WonderFoodScreen`, `MainWorkspace`, and `GroceryContent`.
- Added compact icon actions to canonical Cart preview rows: check marks the canonical line purchased; delete archives the canonical line. Legacy Cart rows keep their existing legacy handlers.
- Added `CanonicalCartMutationCommandFactoryTest` for purchased/archive command shape.
- First focused test attempt compiled app code but failed a test assertion comparing an `Int` to a `Long` timestamp. Fixed the test literal and removed unnecessary casts.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.CanonicalCartMutationCommandFactoryTest --tests com.wonderfood.app.data.CanonicalCartPreviewItemTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "CanonicalCartMutationCommandFactory|markCanonicalCartLinePurchased|archiveCanonicalCartLine|onCanonicalBought|onCanonicalArchive|ArchiveShoppingLine|MarkShoppingLinePurchased" app/src/main/java app/src/test/java`: command factory, ViewModel handlers, UI wiring, and tests are present.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable for this visible canonical Cart action slice.

## 2026-07-20 Phase 1 canonical Kitchen preview actions

- Re-read the pasted orchestration brief, active plan, latest progress, git state, and goal budget before continuing.
- Inspected `CanonicalKitchenPreviewItem`, its tests, the `FoodHubContent`/`CanonicalKitchenPreviewSection` UI path, and the canonical domain command/model shapes.
- Added `CanonicalKitchenMutationCommandFactory` for `ArchiveKitchenItem`, `ArchiveInventoryLot`, and `AddKitchenItemToCart` commands.
- `ArchiveKitchenItem` archives the canonical item and active inventory lots (`AVAILABLE`, `OPENED`, `RESERVED`) with `canonical_kitchen` source metadata and revision bumps.
- `AddKitchenItemToCart` creates a canonical `ShoppingLine` linked to the source item, keeps quantity unknown rather than zero, preserves default unit/category/store, and records `canonical_kitchen` provenance.
- Added ViewModel handlers `addCanonicalKitchenItemToCart(...)` and `archiveCanonicalKitchenItem(...)`. They load the canonical household snapshot, generate commands, execute them through `HouseholdCommandExecutor`, refresh canonical UI state, and queue backend snapshot sync with `canonical_kitchen` source.
- Threaded canonical Kitchen callbacks through `WonderFoodScreen`, `MainWorkspace`, and `FoodHubContent`.
- Added compact Cart and Archive icon actions to canonical Kitchen preview rows. Legacy Kitchen rows keep their existing legacy handlers.
- Added `CanonicalKitchenMutationCommandFactoryTest` for archive and add-to-cart command shape.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.CanonicalKitchenMutationCommandFactoryTest --tests com.wonderfood.app.data.CanonicalKitchenPreviewItemTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "CanonicalKitchenMutationCommandFactory|addCanonicalKitchenItemToCart|archiveCanonicalKitchenItem|onCanonicalAddToCart|ArchiveKitchenItem|AddKitchenItemToCart|canonical_kitchen" app/src/main/java app/src/test/java`: command factory, ViewModel handlers, UI wiring, and tests are present.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable for this visible canonical Kitchen action slice.

## 2026-07-20 Phase 1 canonical recipe root persistence

- Re-read the pasted orchestration brief, active plan, latest progress, git state, and goal budget before continuing.
- Inspected `HouseholdRoomEntities`, `WonderFoodDatabase`, `WonderFoodMigrations`, `HouseholdDomain.Recipe`, `RoomHouseholdRepository`, `WonderFoodRoomConverters`, and existing repository/engine tests.
- Confirmed canonical recipe models exist in `core:model`, but the canonical Room v3 household schema and command layer did not yet persist recipe roots.
- Added `HouseholdCommand.UpsertRecipe` plus executor validation that command household id matches the recipe household id.
- Added `HouseholdRecipeEntity`, `HouseholdDao.upsertRecipe/getRecipes/upsertRecipeAndRecord`, and `RoomHouseholdRepository` recipe entity/domain mapping.
- Added `household_recipes` to the Room database entity list and migration v2->v3 table/index creation. The table stores root recipe metadata, source URL/provider, author/cuisine/category/tags, yield quantity with unknown support, timing, difficulty, status, related ids, provenance, confidence, archive timestamp, and revision.
- Included canonical recipes in `HouseholdSnapshot` reads from `RoomHouseholdRepository`.
- Added `RoomHouseholdRepositoryTest.recipeRootPersistsAcrossRestart` proving a canonical active recipe root, tags, yield, and related ids survive database restart.
- Added `HouseholdCommandExecutorTest.executorRejectsMismatchedRecipeHouseholdBeforeRepositoryMutation`.
- Verified `./gradlew :core:engine:test --tests com.wonderfood.core.engine.HouseholdCommandExecutorTest :core:data:testDebugUnitTest --tests com.wonderfood.core.data.room.RoomHouseholdRepositoryTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "UpsertRecipe|household_recipes|HouseholdRecipeEntity|getRecipes|recipeRootPersistsAcrossRestart|executorRejectsMismatchedRecipe" core/engine core/data/src/main core/data/src/test`: recipe command, Room schema, repository mapping, and tests are present.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This recipe-root persistence slice is backend/model work and has engine/data/app compile proof.

## 2026-07-20 Phase 1 recipe draft canonical root routing

- Re-read the pasted orchestration brief, active plan, latest progress, git state, and goal budget before continuing.
- Inspected `RecipeDraft`, current recipe draft producers, `HouseholdDraftCommandMapper`, mapper tests, and `MainScreenViewModel.canApplyDirectlyToCanonicalHousehold(...)`.
- Confirmed recipe drafts were still excluded from canonical command mapping even after canonical recipe-root persistence landed.
- Added `HouseholdDraftCommandMapper.recipeCommand(...)` to map `RecipeDraft` to canonical `HouseholdCommand.UpsertRecipe`.
- The mapper stores the canonical recipe root title, ingredient/step text as a temporary description, normalized tags, serving yield with unknown support, prep minutes, active status, and source label/provenance from the accepted origin. Ingredient and step entities remain future work until canonical ingredient/step tables land.
- Enabled `RecipeDraft` in `canApplyDirectlyToCanonicalHousehold(...)`, so accepted recipe proposals, manual recipe saves, and voice/external recipe drafts can use the canonical command path when they contain a mappable recipe root.
- Updated `HouseholdDraftCommandMapperTest`: recipe drafts now prove `UpsertRecipe` root command shape, and unsupported direct canonical mapping coverage moved to `MealLogDraft`.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest :core:engine:test --tests com.wonderfood.core.engine.HouseholdCommandExecutorTest :core:data:testDebugUnitTest --tests com.wonderfood.core.data.room.RoomHouseholdRepositoryTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "recipeDraftCreatesCanonicalRecipeRootCommand|RecipeDraft -> listOf\\(recipeCommand|UpsertRecipe|RecipeStatus.ACTIVE|canApplyDirectlyToCanonicalHousehold" app/src/main/java/com/wonderfood/app/data/HouseholdDraftCommandMapper.kt app/src/main/java/com/wonderfood/app/ui/main/MainScreenViewModel.kt app/src/test/java/com/wonderfood/app/data/HouseholdDraftCommandMapperTest`: recipe mapping, canonical acceptance, and tests are present.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This recipe draft routing slice has mapper/engine/data/app compile proof.

## 2026-07-20 Phase 1 recipe CSV/provider direct canonical imports

- Re-read the pasted orchestration brief, active plan, latest progress, git state, and goal budget before continuing.
- Inspected CSV import parsing/classification, provider snapshot draft import classification, `applyCanonicalCsvImport(...)`, and related sync/mapper tests.
- Confirmed recipe drafts now map to canonical `UpsertRecipe`, but CSV and provider import guards still rejected recipes from the direct canonical path.
- Updated `WonderFoodCsvImport.canImportDirectlyToCanonicalHousehold()` so inventory, shopping, and recipe-root imports can route directly to canonical commands when meals, meal plans, and preferences are absent.
- Updated `LegacySnapshotDraftImporter.canImportDirectlyToCanonicalHousehold(...)` so provider snapshots containing foods/stock/shopping and/or recipe roots can route directly to canonical commands when meals, plans, receipts, nutrition, events, relations, and attachments are absent.
- Updated `applyCanonicalCsvImport(...)` to include parsed `RecipeDraft`s in the canonical composite import.
- Updated CSV/provider classification tests to treat recipes as direct-canonical supported and meal logs as mixed/legacy.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodCsvGatewayTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "classifiesInventoryShoppingAndRecipeCsvAsCanonicalDirectImport|canImportDirectlyToCanonicalHousehold|addAll\\(imported.recipes\\)|recipes.isNotEmpty\\(\\)|recipeDraftCreatesCanonicalRecipeRootCommand" app/src/main/java/com/wonderfood/app/sync/WonderFoodCsvGateway.kt app/src/main/java/com/wonderfood/app/sync/LegacySnapshotDraftImporter.kt app/src/main/java/com/wonderfood/app/ui/main/MainScreenViewModel.kt app/src/test/java/com/wonderfood/app/sync app/src/test/java/com/wonderfood/app/data/HouseholdDraftCommandMapperTest`: recipe direct-import guards, canonical importer assembly, and tests are present.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This import-routing slice has sync/mapper/app compile proof.

## 2026-07-20 Phase 1 canonical recipe export projection

- Re-read the pasted orchestration brief, active plan, latest progress, git state, and goal budget before continuing.
- Inspected canonical provider snapshot projection, canonical CSV export, legacy recipe snapshot mapping, and sync tests.
- Confirmed canonical recipe roots now persist and import directly, but `CanonicalHouseholdSnapshotExporter` still emitted `recipes = emptyList()` and `WonderFoodCsvGateway.export(HouseholdSnapshot)` only emitted inventory/shopping rows.
- Updated `CanonicalHouseholdSnapshotExporter` to project canonical recipe roots into legacy `Recipe` records plus `PageKind.RECIPE` pages for provider payloads.
- The provider projection keeps the root title, description, active/draft/archive status, serving quantity, prep/cook minutes, tags as page aliases, source/confidence, and leaves ingredients/steps empty until canonical ingredient/step tables are implemented.
- Updated canonical CSV export to include `recipe` rows with root title, serving count, prep minutes, category/cuisine/tags, notes, and source.
- Updated `CanonicalHouseholdSnapshotExporterTest` to prove canonical recipe roots survive provider snapshot export.
- Updated `WonderFoodCsvGatewayTest` to prove canonical recipe roots appear in CSV export and parse back as recipe drafts.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.CanonicalHouseholdSnapshotExporterTest --tests com.wonderfood.app.sync.WonderFoodCsvGatewayTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This export-projection slice has sync/app compile proof, but no connected UI run.

## 2026-07-20 Phase 1 canonical receipt purchase roots

- Re-read the pasted orchestration brief, active plan, latest progress, git state, and goal budget before continuing.
- Inspected remaining `FoodChatStore`/`FoodMemory` hotspots, current canonical command types, receipt draft mapping, Room household entities/DAO/repository, and repository/mapper tests.
- Confirmed receipt drafts were creating canonical item/inventory commands but were not preserving the receipt purchase root or line-level spending records in the canonical store.
- Added `HouseholdCommand.UpsertPurchase` and `HouseholdCommand.UpsertPurchaseLine`, with executor household-id validation before repository mutation.
- Added canonical Room tables `household_purchases` and `household_purchase_lines`, DAO upsert/query/record-once transactions, v3 schema reset SQL, schema JSON export, and Room repository domain/entity mapping.
- Included canonical purchases and purchase lines in `HouseholdSnapshot` reads.
- Updated `HouseholdDraftCommandMapper` so `ReceiptDraft` emits one canonical `Purchase`, one `PurchaseLine` per receipt item, and inventory item/lot commands for inventory/household dispositions.
- Purchase money stays in minor units; receipt totals sum non-ignored line prices. Purchase lines link to created canonical items and inventory lots. Ignored receipt rows become reviewed ignored purchase lines without creating inventory.
- Added `HouseholdCommandExecutorTest.executorRejectsMismatchedPurchaseHouseholdBeforeRepositoryMutation`.
- Added `RoomHouseholdRepositoryTest.purchaseAndLinePersistAcrossRestartWithMinorUnitMoney`.
- Updated `HouseholdDraftCommandMapperTest` to prove receipt purchase/line command shape, line-money minor units, inventory-lot linkage, and ignored-line behavior.
- Verified `./gradlew :core:engine:test --tests com.wonderfood.core.engine.HouseholdCommandExecutorTest :core:data:testDebugUnitTest --tests com.wonderfood.core.data.room.RoomHouseholdRepositoryTest :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "household_purchases|household_purchase_lines|UpsertPurchase|purchaseAndLinePersist|ignoredReceiptLine" core/data/schemas core/engine/src core/data/src app/src/main app/src/test core/data/src/test`: schema, commands, repository, mapper, and tests are present.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This canonical receipt purchase slice has engine/data/app compile proof, but no connected UI run.

## 2026-07-20 Phase 1 canonical receipt CSV/provider import-export

- Re-read the pasted orchestration brief, current progress, git state, memory guardrails, and goal budget before continuing.
- Inspected CSV import/export, provider snapshot draft import, receipt draft shape, and canonical purchase export state.
- Confirmed canonical purchases now persist, but CSV export did not emit purchase/receipt rows, CSV parsing ignored receipt rows, CSV import assembly dropped parsed receipt drafts, and provider import guards still rejected all receipts.
- Extended CSV headers with merchant, purchase timestamp, currency, amount cents, and receipt disposition fields.
- Updated canonical household CSV export to emit a `receipt` row for each canonical purchase and `receipt_line` rows for each purchase line.
- Updated CSV parsing to reconstruct grouped `ReceiptDraft`s from `receipt` and `receipt_line` rows.
- Updated `WonderFoodCsvImport` summary/count/direct-canonical classification so receipt imports can route through canonical commands when no meal plan/log/preferences payload is mixed in.
- Updated CSV import handling in `MainScreenViewModel` so parsed receipt drafts are included in both canonical direct imports and fallback draft execution.
- Updated provider snapshot import rules so linked receipt snapshots can route directly to canonical commands, while empty receipt captures without item lines remain excluded.
- Updated `LegacySnapshotDraftImporter.toDraft(...)` to map linked provider receipts to `ReceiptDraft`s by resolving receipt item IDs through shopping rows and food/page names.
- Updated `WonderFoodCsvGatewayTest` to prove canonical receipt rows export, parse back as receipt drafts, and qualify for direct canonical import.
- Updated `LegacySnapshotDraftImporterTest.mapsLinkedProviderReceiptToCanonicalReceiptDraft` to prove linked provider receipts become direct-canonical receipt drafts.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodCsvGatewayTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "receipt_line|receipts = receipts|addAll\\(imported\\.receipts\\)|mapsLinkedProviderReceipt|toReceiptDisposition|canonicalReceiptRow|canImportDirectlyToCanonicalHousehold" app/src/main/java app/src/test/java`: receipt CSV/provider import-export wiring and tests are present.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This canonical receipt import-export slice has sync/app compile proof, but no connected UI run.

## 2026-07-20 Phase 1 canonical spending summary surface

- Re-read the pasted orchestration brief, latest progress, git state, and goal budget before continuing.
- Inspected canonical summary state, Now dashboard metric pills, Settings canonical store row, receipt money formatting, and canonical purchase models.
- Confirmed canonical purchases and purchase lines now persist/import/export, but the app's canonical summary still hid purchase counts and spending amounts.
- Extended `CanonicalHouseholdUiSummary` with purchase count, purchase-line count, this-month spending, last-month spending, and currency.
- Spending math uses canonical purchase totals first, then falls back to grouped purchase-line final/subtotal amounts for purchases without totals. It respects the household default currency and the household timezone.
- Updated the canonical summary label shown in Settings to include purchase count and this-month spend when present.
- Added a Now-dashboard spending metric pill showing this-month and last-month canonical spending when purchase amounts exist.
- Added `CanonicalHouseholdUiSummaryTest.spendingLabelsUseCanonicalPurchasesForCurrentAndPreviousMonth` with fixed dates to prove current-month total, previous-month line fallback, and UI labels.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.CanonicalHouseholdUiSummaryTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "spendingDashboardLabel|thisMonthSpentMinorUnits|lastMonthSpentMinorUnits|purchases = snapshot\\.purchases|Canonical household store|MetricPill\\(\\\"💵\\\"|spendingLabelsUseCanonicalPurchases" app/src/main/java app/src/test/java`: summary spending state, UI pill, and tests are present.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This canonical spending summary slice has app unit/build proof, but no connected UI run.

## 2026-07-20 Phase 1 canonical receipt provider line projection

- Re-read the pasted orchestration brief, latest progress, git state, and goal budget before continuing.
- Inspected canonical provider snapshot export and exporter tests around shopping rows, receipt rows, and purchase-line item IDs.
- Confirmed canonical receipts exported `itemIds` derived from purchase-line IDs, but those IDs did not have corresponding legacy `ShoppingItem` rows in the provider snapshot.
- Updated `CanonicalHouseholdSnapshotExporter` to project each canonical `PurchaseLine` as a legacy `ShoppingItem` row with a `PageKind.SHOPPING_ITEM` page.
- Receipt-line projections preserve display name, quantity, linked canonical item/food ID, spend category/disposition as reason, and purchased/skipped status based on purchase-line disposition.
- Updated provider snapshot assembly so exported receipts now reference actual shopping rows rather than dangling IDs.
- Updated `CanonicalHouseholdSnapshotExporterTest` with a canonical purchase and purchase line, proving the exported receipt references a purchased shopping row with the expected page/title, quantity, reason, and total.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.CanonicalHouseholdSnapshotExporterTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "receiptLineShoppingItems|toLegacyShoppingStatus|PurchaseLineDisposition|receiptLineItem|canonical:shopping_line:00000000-0000-0000-0000-000000000782" app/src/main/java/com/wonderfood/app/sync/CanonicalHouseholdSnapshotExporter.kt app/src/test/java/com/wonderfood/app/sync/CanonicalHouseholdSnapshotExporterTest`: provider receipt line projection and tests are present.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This canonical provider receipt-line projection slice has sync/app compile proof, but no connected UI run.

## 2026-07-20 Phase 1 workspace purchase-line totals

- Re-read the pasted orchestration brief, latest progress, git state, and goal budget before continuing.
- Inspected `WonderFoodWorkspaceSchema` purchase/purchase-line rows and workspace seed tests.
- Confirmed the workspace `Purchase Lines` support table existed, but `Line total` was always blank even when the linked receipt had a parsed total and exactly one line.
- Updated `WonderFoodWorkspaceSchema` so single-line receipts carry receipt `total` or `subtotal` into the `Purchase Lines` row amount while preserving existing blank behavior for multi-line receipts where line allocation is unknown.
- Added `WonderFoodWorkspaceSeedFixtureTest.singleLinePurchaseCarriesReceiptTotalIntoPurchaseLinesTable` by linking the seed receipt to one shopping row and setting a parsed total.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodWorkspaceSeedFixtureTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "singleLineAmount|Line total|singleLinePurchaseCarriesReceiptTotal" app/src/main/java/com/wonderfood/app/sync/WonderFoodWorkspaceSchema.kt app/src/test/java/com/wonderfood/app/sync/WonderFoodWorkspaceSeedFixtureTest`: workspace purchase-line total projection and test are present.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This workspace spending row slice has sync/app compile proof, but no connected UI run.

## 2026-07-20 Phase 1 workspace purchase import drafts

- Re-read the pasted orchestration brief, latest progress, git state, emulator availability, and goal budget before continuing.
- Inspected `GoogleSheetsWorkspaceDraftImporter`, workspace row schemas, purchase/purchase-line support tables, and the importer test.
- Confirmed Sheets/Notion workspace imports converted Kitchen, Shopping, Recipes, and Meals into reviewable drafts, but ignored standalone Purchases and Purchase Lines rows.
- Updated `GoogleSheetsWorkspaceDraftImporter` so `Purchases` rows with linked `Purchase Lines` produce `ReceiptDraft`s with merchant, purchase date, currency, subtotal/total minor units, line quantity, receipt line text, line amount, and evidence.
- Kept import behavior conservative: archived purchases are skipped, blank purchase lines are skipped, and purchase rows without item lines are not imported as empty receipts.
- Extended `GoogleSheetsWorkspaceDraftImporterTest.friendlyWorkspaceRowsBecomeReviewableDrafts` to prove workspace purchase rows become a reviewable receipt draft alongside inventory, cart, recipe, meal-log, and meal-plan drafts.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.GoogleSheetsWorkspaceDraftImporterTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "receiptDrafts\\(|PURCHASE_LINES|Google Sheets workspace purchase line|moneyCentsOrNull|ReceiptDraft|linePriceCents" app/src/main/java/com/wonderfood/app/sync/GoogleSheetsWorkspaceDraftImporter.kt app/src/test/java/com/wonderfood/app/sync/GoogleSheetsWorkspaceDraftImporterTest.kt`: importer wiring and assertions are present.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This standalone workspace purchase-import slice has sync/app compile proof, but no connected UI run.

## 2026-07-20 Phase 1 canonical meal entry root

- Re-read the pasted orchestration brief, latest progress, git state, emulator availability, and goal budget before continuing.
- Inspected the canonical household model, command layer, Room household repository, migration SQL, mapper, and tests.
- Confirmed canonical `MealEntry` already existed in the model snapshot, but there was no command, Room table, repository mapping, or direct accepted-draft path for `MealLogDraft`.
- Added `HouseholdCommand.UpsertMealEntry` with executor household-id validation.
- Added Room `household_meal_entries`, DAO upsert/query/idempotent record transaction, v3 migration SQL, database entity registration, repository domain/entity mapping, and snapshot inclusion.
- Updated `HouseholdDraftCommandMapper` so accepted `MealLogDraft`s create canonical `MealEntry` rows with scheduled date, slot, title, eaten status, unknown serving quantity, and nutrition/source notes.
- Updated the direct canonical acceptance guard so `MealLogDraft` can use the canonical command path; `MealPlanDraft` remains unsupported until canonical meal-plan persistence is wired.
- Added `HouseholdCommandExecutorTest.executorRejectsMismatchedMealEntryHouseholdBeforeRepositoryMutation`.
- Added `RoomHouseholdRepositoryTest.mealEntryPersistsAcrossRestart`.
- Updated `HouseholdDraftCommandMapperTest.mealLogDraftCreatesCanonicalMealEntryCommand` and moved unsupported coverage to `MealPlanDraft`.
- Verified `./gradlew :core:engine:test --tests com.wonderfood.core.engine.HouseholdCommandExecutorTest :core:data:testDebugUnitTest --tests com.wonderfood.core.data.room.RoomHouseholdRepositoryTest :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "UpsertMealEntry|household_meal_entries|mealEntryPersistsAcrossRestart|mealLogDraftCreatesCanonicalMealEntryCommand|MealEntry" core/engine/src core/data/src core/data/schemas app/src/main app/src/test core/data/src/test -g "*.*"`: command, Room schema, repository, direct acceptance, and tests are present.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This canonical meal-entry slice has engine/data/app compile proof, but no connected UI run.

## 2026-07-20 Phase 1 canonical meal plan root

- Re-read the pasted orchestration brief, latest progress, git state, emulator availability, and goal budget before continuing.
- Inspected the canonical `MealPlan` model, `MealEntry` persistence added in the previous slice, command layer, Room household repository, migration SQL, mapper, direct-accept guard, and tests.
- Confirmed canonical `MealPlan` existed in the model snapshot, but there was no command, Room table, repository mapping, or direct accepted-draft path for `MealPlanDraft`.
- Added `HouseholdCommand.UpsertMealPlan` with executor household-id validation.
- Added Room `household_meal_plans`, DAO upsert/query/idempotent record transaction, v3 migration SQL, database entity registration, repository domain/entity mapping, and snapshot inclusion.
- Updated `HouseholdDraftCommandMapper` so accepted `MealPlanDraft`s create canonical `MealPlan` roots and structured `MealPlanEntryDraft`s become linked planned `MealEntry` rows.
- Updated the direct canonical acceptance guard so `MealPlanDraft` can use the canonical command path.
- Added `HouseholdCommandExecutorTest.executorRejectsMismatchedMealPlanHouseholdBeforeRepositoryMutation`.
- Added `RoomHouseholdRepositoryTest.mealPlanAndLinkedEntryPersistAcrossRestart`.
- Added `HouseholdDraftCommandMapperTest.mealPlanDraftCreatesCanonicalPlanAndLinkedEntries`; unsupported canonical mapping coverage now uses `LinkActionDraft`.
- Verified `./gradlew :core:engine:test --tests com.wonderfood.core.engine.HouseholdCommandExecutorTest :core:data:testDebugUnitTest --tests com.wonderfood.core.data.room.RoomHouseholdRepositoryTest :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "UpsertMealPlan|household_meal_plans|mealPlanAndLinkedEntryPersistAcrossRestart|mealPlanDraftCreatesCanonicalPlanAndLinkedEntries|MealPlanStatus|canApplyDirectlyToCanonicalHousehold" core/engine/src core/data/src core/data/schemas app/src/main app/src/test core/data/src/test -g "*.*"`: command, Room schema, repository, direct acceptance, and tests are present.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This canonical meal-plan slice has engine/data/app compile proof, but no connected UI run.

## 2026-07-20 Phase 1 canonical meal provider projection

- Re-read the pasted orchestration brief, latest progress, git state, emulator availability, and goal budget before continuing.
- Inspected `CanonicalHouseholdSnapshotExporter`, its test fixture, legacy `MealPlan`/`PlanEntry`/`MealLog` domain shapes, and workspace meal/plan row generation.
- Confirmed canonical meal plans and entries now persist, but provider snapshot projection still emitted `mealPlans = emptyList()` and `mealLogs = emptyList()`.
- Updated `CanonicalHouseholdSnapshotExporter` to project canonical `MealPlan` roots into legacy provider `MealPlan` records with `PageKind.MEAL_PLAN` pages.
- Updated canonical `MealEntry` projection so entries linked to a plan become legacy `PlanEntry` records with date, slot, recipe link when present, quantity, and planned/eaten/skipped status.
- Updated canonical eaten/unplanned meal entries to project as legacy `MealLog` records with `PageKind.MEAL_LOG` pages, timestamp, slot, recipe link when present, and confirmed/estimated/archive status.
- Noted limitation: the legacy `PlanEntry` type does not carry free-text meal titles, so title-only planned entries preserve date/slot/status but need recipe/food links to show a rich meal name in existing workspace rows.
- Extended `CanonicalHouseholdSnapshotExporterTest.projectsCanonicalHouseholdInventoryAndShoppingForProviderSnapshots` with a canonical meal plan, linked planned meal entry, and unplanned eaten meal log.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.CanonicalHouseholdSnapshotExporterTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "mealPlans = mealPlans|mealLogs = mealLogs|toLegacyPlanEntry|toLegacyMealLogStatus|toMealPlanId|MealLog\\(|MealPlan\\(|mealPlan\\.entries|mealLog\\.status" app/src/main/java/com/wonderfood/app/sync/CanonicalHouseholdSnapshotExporter.kt app/src/test/java/com/wonderfood/app/sync/CanonicalHouseholdSnapshotExporterTest.kt`: provider projection and assertions are present.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This canonical meal provider-projection slice has sync/app compile proof, but no connected UI run.

## 2026-07-20 Phase 1 canonical meal CSV import-export

- Re-read the pasted orchestration brief, latest progress, git state, emulator availability, and goal budget before continuing.
- Inspected `WonderFoodCsvGateway`, CSV import application in `MainScreenViewModel`, canonical meal-plan/meal-entry mapper support, and CSV tests.
- Confirmed CSV parsing already understood `meal_log`, `meal_plan`, and `meal_plan_entry` rows, but canonical `HouseholdSnapshot` CSV export emitted no meal rows and direct-canonical CSV classification still rejected meal logs/plans.
- Updated canonical CSV export to emit `meal_plan` rows from canonical `MealPlan` roots.
- Updated canonical CSV export to emit linked `meal_plan_entry` rows from canonical planned `MealEntry` records.
- Updated canonical CSV export to emit `meal_log` rows for eaten or unplanned canonical `MealEntry` records.
- Updated CSV direct-canonical classification so inventory, cart, recipe, receipt, meal log, and meal plan rows can all route directly to canonical commands when no preferences are mixed in.
- Updated `MainScreenViewModel.applyCanonicalCsvImport(...)` to include parsed meal logs and meal plans in the canonical draft import.
- Extended `WonderFoodCsvGatewayTest` canonical fixture with a meal plan, linked planned meal, and eaten meal log; assertions now prove canonical CSV meal rows parse back to meal drafts and direct-import classification accepts supported meal rows.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodCsvGatewayTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `rg -n "canonicalMealPlanRow|canonicalMealPlanEntryRow|canonicalMealLogRow|mealLogs\\.isNotEmpty|mealPlans\\.isNotEmpty|addAll\\(imported\\.mealLogs\\)|addAll\\(imported\\.mealPlans\\)|classifiesSupportedCsvRowsAsCanonicalDirectImport|meal_log" app/src/main/java/com/wonderfood/app/sync/WonderFoodCsvGateway.kt app/src/main/java/com/wonderfood/app/ui/main/MainScreenViewModel.kt app/src/test/java/com/wonderfood/app/sync/WonderFoodCsvGatewayTest.kt`: CSV export, direct import, and tests are present.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This canonical meal CSV slice has sync/app compile proof, but no connected UI run.

## 2026-07-20 Phase 1 canonical meal provider import classification

- Re-read the pasted orchestration brief, latest progress, git state, emulator availability, and goal budget before continuing.
- Inspected `LegacySnapshotDraftImporter`, `LegacySnapshotDraftImporterTest`, provider `MealPlan`/`PlanEntry`/`MealLog` shapes, and `HouseholdDraftCommandMapper`.
- Confirmed provider snapshots already mapped meal logs and meal plans into `MealLogDraft`/`MealPlanDraft`, and the draft mapper can turn those drafts into canonical meal commands, but direct-canonical provider classification still rejected any snapshot containing meals.
- Updated `LegacySnapshotDraftImporter.canImportDirectlyToCanonicalHousehold(...)` so supported provider snapshots with inventory, shopping, recipes, linked receipts, meal logs, and meal plans can route directly to canonical commands.
- Kept the classification conservative for unsupported fields: nutrition snapshots, events, relations, attachments, and empty receipt shells still stay out of direct canonical import.
- Updated meal-plan import to preserve provider entry dates as `MealPlanEntryDraft.dayOffset` relative to the provider plan start date instead of flattening every entry to day zero.
- Extended `LegacySnapshotDraftImporterTest.classifiesProviderSnapshotsForDirectCanonicalImport` to prove meal logs/plans are direct-canonical supported and nutrition-rich snapshots are not.
- Extended `LegacySnapshotDraftImporterTest.mapsCanonicalSnapshotToAdditiveLegacyDrafts` with a linked meal-plan entry and day-offset assertion.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This provider meal-import classification slice has sync/app compile proof, but no connected UI run.

## 2026-07-20 Phase 1 canonical receipt purchase facts

- Re-read the pasted orchestration brief, latest progress, git state, emulator availability, and goal budget before continuing.
- Inspected `HouseholdDraftCommandMapper`, canonical `Purchase`, receipt draft fields, and provider importer tests.
- Confirmed canonical receipt commands created `Purchase` roots, but used the current clock for `occurredAt` and only derived purchase totals from line prices, ignoring receipt-level purchased date, subtotal, tax, total, merchant, and location facts.
- Updated `HouseholdDraftCommandMapper.receiptCommands(...)` so canonical purchases preserve `purchasedAtMillis`, `subtotalCents`, `taxCents`, `totalCents`, and merchant/location/source notes from `ReceiptDraft`.
- Kept line-price summing as a fallback only when the receipt draft has no explicit total.
- Extended `HouseholdDraftCommandMapperTest.receiptHouseholdLineCreatesNonFoodInventoryWithoutFoodDetails` to assert canonical purchase occurred time, subtotal, tax, total, merchant note, and location note.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest --tests com.wonderfood.app.sync.GoogleSheetsWorkspaceDraftImporterTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This canonical receipt-spending slice has mapper/importer/app compile proof, but no connected UI run.

## 2026-07-20 Phase 1 canonical receipt idempotency scope

- Re-read the pasted orchestration brief, latest progress, git state, emulator availability, and goal budget before continuing.
- Inspected `HouseholdDraftCommandMapper`, its receipt mapper tests, canonical command idempotency behavior, and Room household command application.
- Confirmed canonical receipt commands used a constant `receipt` command scope and line scopes like `receipt:0`, so receipts with the same line names/prices could collide across different trips and produce duplicate command identities.
- Updated receipt mapping to build a normalized receipt identity from receipt id, purchased time, merchant, location, currency, subtotal, tax, total, and line names/quantities/prices/dispositions.
- Updated canonical purchase, purchase-line, item, and lot command scopes for receipt drafts to include the normalized receipt identity, preserving idempotency for the same receipt while separating different same-item trips.
- Added `HouseholdDraftCommandMapperTest.sameReceiptLinesOnDifferentTripsCreateDistinctCanonicalPurchaseIdentities` to prove different merchant/date trips create distinct purchase IDs and command IDs.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This canonical receipt-idempotency slice has mapper/app compile proof, but no connected UI run.

## 2026-07-20 Phase 1 canonical spending ignored-line guard

- Re-read the pasted orchestration brief, latest progress, git state, emulator availability, and goal budget before continuing.
- Inspected `CanonicalHouseholdUiSummary`, canonical purchase-line dispositions, spending summary tests, and the receipt mapper path that creates ignored purchase lines.
- Confirmed spending fallback used all purchase lines when a purchase had no explicit total/subtotal, so ignored receipt rows such as cash back or excluded lines could be counted as household spending.
- Updated `CanonicalHouseholdUiSummary` so line-based spending fallback excludes `PurchaseLineDisposition.IGNORED` lines.
- Added `CanonicalHouseholdUiSummaryTest.spendingFallbackIgnoresIgnoredReceiptLines` to prove an ignored-only receipt without explicit totals does not surface false monthly spending.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.CanonicalHouseholdUiSummaryTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This canonical spending slice has summary/mapper/app compile proof, but no connected UI run.

## 2026-07-20 Phase 1 canonical spending food-vs-household split

- Re-read the pasted orchestration brief, latest progress, git state, emulator availability, and goal budget before continuing.
- Inspected `CanonicalHouseholdUiSummary`, canonical item kinds, purchase lines, and spending summary tests.
- Confirmed canonical spending surfaced current and previous month totals, but did not yet expose the requested food versus non-food household split from canonical purchase lines.
- Added `thisMonthFoodSpentMinorUnits` and `thisMonthNonFoodSpentMinorUnits` to `CanonicalHouseholdUiSummary`.
- Updated spending labels to include current-month food and household subtotals when present.
- Computed the split from non-ignored purchase lines, using linked canonical `Item.kind`; food item lines count as food, and household/unlinked lines count as non-food.
- Added `CanonicalHouseholdUiSummaryTest.spendingLabelsSplitCurrentMonthFoodAndNonFoodLines` to prove linked food and household receipt lines surface distinct spending subtotals.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.CanonicalHouseholdUiSummaryTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This canonical spending split slice has summary/app compile proof, but no connected UI run.

## 2026-07-20 Phase 1 canonical spending category and merchant labels

- Re-read the pasted orchestration brief, latest progress, git state, emulator availability, and goal budget before continuing.
- Checked `adb devices -l` and `emulator -list-avds`: no connected devices and no local AVDs were listed, so emulator UI proof remains unavailable.
- Inspected `CanonicalHouseholdUiSummary` and its spending tests after the current-month food versus household split.
- Confirmed the canonical spending summary still did not surface the requested category or merchant signal.
- Added `thisMonthTopCategoryLabel` and `thisMonthTopMerchantLabel` to `CanonicalHouseholdUiSummary`.
- Computed the current-month top category from non-ignored canonical purchase-line `spendCategory` amounts.
- Computed the current-month top merchant from canonical purchase payment notes written by the receipt mapper, using the purchase amount for the merchant total.
- Updated spending labels to include category and merchant when present.
- Extended `CanonicalHouseholdUiSummaryTest.spendingLabelsSplitCurrentMonthFoodAndNonFoodLines` to prove top category and merchant are surfaced alongside current-month total and food/household subtotals.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.CanonicalHouseholdUiSummaryTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This canonical spending category/merchant slice has summary/app compile proof, but no connected UI run.

## 2026-07-20 Phase 1 canonical receipt merchant projection

- Re-read the pasted orchestration brief, latest progress, git state, emulator availability, and goal budget before continuing.
- Checked `adb devices -l` and `emulator -list-avds`: no connected devices and no local AVDs were listed, so emulator UI proof remains unavailable.
- Inspected `CanonicalHouseholdSnapshotExporter`, receipt projection tests, and the canonical receipt mapper's payment-note merchant storage.
- Confirmed provider snapshot projection only read merchant names through canonical `merchantId`, so receipts created by the current canonical draft mapper could lose their merchant on Sheets/Notion projection.
- Updated `CanonicalHouseholdSnapshotExporter` so legacy/provider receipts fall back to parsing `Merchant:` from canonical purchase `paymentNote` when no canonical merchant entity is linked.
- Extended `CanonicalHouseholdSnapshotExporterTest.projectsCanonicalHouseholdInventoryAndShoppingForProviderSnapshots` to prove a canonical receipt payment-note merchant becomes the provider snapshot `Receipt.merchantName`.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.CanonicalHouseholdSnapshotExporterTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This canonical receipt merchant-projection slice has exporter/app compile proof, but no connected UI run.

## 2026-07-20 Phase 1 canonical receipt CSV merchant parity

- Re-read the pasted orchestration brief, latest progress, git state, emulator availability, and goal budget before continuing.
- Checked `adb devices -l` and `emulator -list-avds`: no connected devices and no local AVDs were listed, so emulator UI proof remains unavailable.
- Inspected `WonderFoodCsvGateway` canonical receipt export/import and the canonical snapshot CSV fixture.
- Confirmed canonical CSV receipt export wrote `merchantId` only, so receipts created by the current canonical receipt mapper could lose their human merchant on CSV export/import even though provider snapshot projection now preserves it.
- Updated canonical CSV receipt export so `merchant` falls back to parsing `Merchant:` from purchase `paymentNote` when no canonical merchant entity is linked.
- Extended `WonderFoodCsvGatewayTest.exportsCanonicalHouseholdItemsAndShoppingRows` so the canonical receipt fixture includes a payment-note merchant, CSV contains that merchant, and CSV parse returns it in `ReceiptDraft.merchant`.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodCsvGatewayTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no connected devices, so emulator UI proof remains unavailable. This canonical receipt CSV-merchant slice has CSV/app compile proof, but no connected UI run.

## 2026-07-20 Phase 1 canonical receipt CSV totals parity

- Re-read the pasted orchestration brief and goal budget before continuing.
- Inspected `WonderFoodCsvGateway` canonical receipt export/import and `WonderFoodCsvGatewayTest.exportsCanonicalHouseholdItemsAndShoppingRows`.
- Confirmed canonical receipt CSV rows preserved merchant/date/currency/total only, so subtotal and tax were lost when canonical receipts were exported and parsed back as `ReceiptDraft`.
- Added explicit CSV columns for `subtotal_cents`, `tax_cents`, and `total_cents`; kept `amount_cents` as the existing total/readable amount column.
- Updated receipt CSV parsing to preserve subtotal, tax, and total, falling back from `total_cents` to legacy `amount_cents` when needed.
- Updated canonical receipt CSV export to choose currency from total, subtotal, or tax and write all available receipt totals.
- Extended the canonical snapshot CSV fixture so subtotal 849, tax 50, and total 899 round-trip into `ReceiptDraft`.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodCsvGatewayTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Emulator proof: initial `medium_phone` AVD failed because its Android 36 system image path is broken; initial Pixel 3a launch failed due to missing named skin; Pixel 3a booted with `-skin 1080x2220 -gpu swiftshader_indirect`.
- Verified connected UI test `./gradlew :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wonderfood.app.ui.main.MainScreenTest` on `emulator-5554` / `Pixel_3a_API_34_extension_level_7_arm64-v8a`: BUILD SUCCESSFUL, 8 tests run, 0 failed.
- Shut down the emulator with `adb emu kill`.

## 2026-07-20 Phase 1 Google Sheets receipt tax parity

- Re-read the pasted orchestration brief, active plan, latest progress, and goal budget before continuing.
- Inspected `GoogleSheetsWorkspaceDraftImporter`, `WonderFoodWorkspaceSchema`, canonical snapshot projection, provider snapshot merge, and Sheets tests around Purchases.
- Confirmed the Sheets workspace preserved `Subtotal` and `Total`, but had no first-class `Tax` column and Google Sheets imports never filled `ReceiptDraft.taxCents`.
- Added `tax` to the provider snapshot `Receipt` model and `WonderFoodSnapshotCodec` so tax can travel through provider snapshots instead of being flattened away.
- Projected canonical `Purchase.tax` into provider receipts in `CanonicalHouseholdSnapshotExporter`.
- Added a visible `Tax` field to the Google Sheets Purchases table and exported receipt tax into that field.
- Updated the Home spending formula from `=SUM(Purchases!E2:E)` to `=SUM(Purchases!F2:F)` because `Total` shifted one column after adding `Tax`.
- Updated Google Sheets draft import so Purchases `Tax` becomes `ReceiptDraft.taxCents`.
- Updated workspace snapshot merge so new purchase rows can create provider receipts with subtotal, tax, and total.
- Updated focused tests for Sheets import, canonical provider projection, workspace merge, Sheets formula output, and snapshot codec round-trip coverage.
- First focused test attempt failed because the canonical exporter test expected subtotal/tax but its fixture only set total; fixed the fixture to include subtotal 849 and tax 50.
- Verified `./gradlew :core:model:test --tests com.wonderfood.core.model.WonderFoodSnapshotCodecTest :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.GoogleSheetsWorkspaceDraftImporterTest --tests com.wonderfood.app.sync.CanonicalHouseholdSnapshotExporterTest --tests com.wonderfood.app.sync.WonderFoodWorkspaceSnapshotMergerTest --tests com.wonderfood.app.sync.GoogleSheetsGatewayTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Emulator proof: Pixel 3a booted with `-skin 1080x2220 -gpu swiftshader_indirect`; `adb shell cmd package list packages com.android.shell` confirmed package manager readiness after an initial early poll failed with `Can't find service: package`.
- Verified connected UI test `./gradlew :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wonderfood.app.ui.main.MainScreenTest` on `emulator-5554` / `Pixel_3a_API_34_extension_level_7_arm64-v8a`: BUILD SUCCESSFUL, 8 tests run, 0 failed.
- Shut down the emulator with `adb emu kill`.

## 2026-07-20 Phase 1 Notion receipt tax parity proof

- Re-read the pasted orchestration brief, latest progress, worktree status, and goal budget before continuing.
- Inspected Notion workspace database/page serialization and parsing after the shared Purchases `Tax` field was added.
- Confirmed Notion uses `WonderFoodWorkspaceSchema` for database properties, structured page export, and remote page parsing, so the new visible `Tax` number field flows through Notion without custom provider code.
- Added `NotionGatewayTest.purchasesDatabaseExportsAndParsesTax` to prove the Notion Purchases database declares `Tax` as a number, structured page export writes a numeric tax value, and remote Notion page parsing returns the `Tax` value for import/merge.
- Used an explicit receipt-tax copy of the seed snapshot in the test instead of changing legacy-derived seed data.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.NotionGatewayTest --tests com.wonderfood.app.sync.GoogleSheetsWorkspaceDraftImporterTest --tests com.wonderfood.app.sync.GoogleSheetsGatewayTest --tests com.wonderfood.app.sync.WonderFoodWorkspaceSnapshotMergerTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no devices attached. Skipped another emulator run for this provider serialization/parsing proof to conserve usage; the previous Google Sheets tax parity slice already verified connected `MainScreenTest` after the shared model/schema change.

## 2026-07-20 Phase 1 provider receipt tax import parity

- Re-read the pasted orchestration brief, latest progress, worktree status, and goal budget before continuing.
- Inspected `LegacySnapshotDraftImporter` after provider receipts gained a first-class `tax` field.
- Confirmed provider snapshots could now export and serialize receipt tax, but generic provider snapshot import still mapped only subtotal and total into `ReceiptDraft`.
- Updated provider receipt import so `ReceiptDraft.taxCents` is populated from `receipt.tax`, and currency fallback considers total, subtotal, then tax.
- Strengthened `LegacySnapshotDraftImporterTest.mapsLinkedProviderReceiptToCanonicalReceiptDraft` with subtotal 849, tax 50, and total 899 assertions.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest --tests com.wonderfood.app.sync.GoogleSheetsWorkspaceDraftImporterTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no devices attached. Skipped emulator for this mapper-only parity slice to conserve usage; no UI behavior changed.

## 2026-07-20 Phase 1 provider purchase-line category/disposition parity

- Re-read the pasted orchestration brief, latest progress, worktree status, and goal budget before continuing.
- Inspected `WonderFoodWorkspaceSchema` Purchase Lines and `GoogleSheetsWorkspaceDraftImporter.receiptDrafts`.
- Confirmed provider purchase-line rows had no visible category/disposition fields, and Google Sheets receipt imports always mapped every purchase line to `ReceiptItemDisposition.INVENTORY`, which could send non-food household purchases into Kitchen.
- Added visible Purchase Lines `Category` and `Disposition` fields to the shared workspace schema used by Sheets and Notion.
- Updated workspace export so purchase-line rows carry category from the linked shopping item reason and a disposition label derived from household spending categories such as cleaning, personal care, medicine, pet, and household.
- Updated Google Sheets workspace import so `Disposition` wins when present, otherwise deterministic non-food inference marks obvious household lines as `HOUSEHOLD`.
- Strengthened `GoogleSheetsWorkspaceDraftImporterTest.friendlyWorkspaceRowsBecomeReviewableDrafts` with a mixed receipt containing Basmati Rice as inventory and Dish soap as household.
- Strengthened `WonderFoodWorkspaceSeedFixtureTest.singleLinePurchaseCarriesReceiptTotalIntoPurchaseLinesTable` to prove exported purchase lines include `Category` and `Disposition`.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.GoogleSheetsWorkspaceDraftImporterTest --tests com.wonderfood.app.sync.WonderFoodWorkspaceSeedFixtureTest --tests com.wonderfood.app.sync.NotionGatewayTest --tests com.wonderfood.app.sync.GoogleSheetsGatewayTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no devices attached. Skipped emulator for this provider schema/import slice to conserve usage; no UI behavior changed.

## 2026-07-20 Phase 1 workspace merge purchase-line rows

- Re-read the pasted orchestration brief, latest progress, worktree status, and goal budget before continuing.
- Inspected canonical purchase-line provider projection and `WonderFoodWorkspaceSnapshotMerger`.
- Confirmed canonical purchase lines already project `spendCategory` into provider shopping item reason, but workspace merge ignored `Purchase Lines` rows entirely, so remote purchase-line/category/disposition edits could not become provider receipt line records.
- Updated workspace merge so new `Purchase Lines` rows create purchased `ShoppingItem` records with line title, quantity, unit, category/reason, optional Food ID, and purchased/skipped status from disposition.
- Updated receipt merge so matching existing or newly-created purchases include the created purchase-line item IDs.
- Added Purchase Lines validation for quantity, line total, and disposition options.
- Updated stable identifier generation so blank Purchase Lines identifiers derive from `Line`.
- Strengthened `WonderFoodWorkspaceSnapshotMergerTest.mergesFriendlyKitchenShoppingRecipeMealAndPurchaseRowsByIdentifier` with a remote Dish soap Purchase Line attached to the receipt.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodWorkspaceSnapshotMergerTest --tests com.wonderfood.app.sync.GoogleSheetsWorkspaceDraftImporterTest --tests com.wonderfood.app.sync.WonderFoodWorkspaceSeedFixtureTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no devices attached. Skipped emulator for this provider merge slice to conserve usage; no UI behavior changed.

## 2026-07-20 Phase 1 existing purchase tax merge

- Re-read the pasted orchestration brief, latest progress, worktree status, and goal budget before continuing.
- Inspected `WonderFoodWorkspaceSnapshotMerger` after new purchases gained tax support.
- Confirmed newly-created Purchases rows could set `tax`, but edits to existing Purchases rows still ignored the visible `Tax` field.
- Updated existing receipt merge so `Tax` edits produce `Money` on `Receipt.tax`, and currency fallback now considers total, subtotal, then tax.
- Strengthened `WonderFoodWorkspaceSnapshotMergerTest.mergesFriendlyKitchenShoppingRecipeMealAndPurchaseRowsByIdentifier` to assert existing receipt subtotal 15.99, tax 0.98, and total 16.97.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodWorkspaceSnapshotMergerTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Checked `adb devices -l`: no devices attached. Skipped emulator for this provider merge mapper slice to conserve usage; no UI behavior changed.

## 2026-07-20 Phase 1 existing purchase-line merge

- Re-read the pasted orchestration brief, latest progress, worktree status, and goal budget before continuing.
- Inspected `WonderFoodWorkspaceSnapshotMerger` and the focused merger tests around Purchases and Purchase Lines.
- Confirmed new Purchase Lines rows could create receipt line `ShoppingItem` records, but edits to existing Purchase Lines rows were ignored once their shopping item ID was known.
- Updated workspace merge so existing Purchase Lines rows update linked shopping item page title, quantity/unit, category/reason, optional Food ID, and disposition-derived purchased/skipped status.
- Updated receipt-link repair so Purchase Lines rows using `Shopping item ID` keep the linked receipt attached without duplicating line items.
- Added `WonderFoodWorkspaceSnapshotMergerTest.updatesExistingPurchaseLineRowsAndKeepsReceiptLinked`, proving a second workspace merge edits an existing line via `Shopping item ID`, avoids duplicates, and preserves receipt linkage.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodWorkspaceSnapshotMergerTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Goal budget check after this slice: about 3h17m elapsed, still under the 6h cap. Skipped emulator for this non-UI provider merge slice to conserve usage; no UI behavior changed.

## 2026-07-20 Phase 1 purchase-line orphan guard

- Continued from the existing purchase-line merge slice with the same provider/workspace scope.
- Confirmed a friendly Purchase Lines row with a visible `Line` but no `Purchase ID` could otherwise create an unattached shopping item, leaving a receipt-line orphan outside any purchase.
- Added validation that reports missing `Purchase ID` for visible Purchase Lines rows.
- Updated new purchase-line creation so rows without `Purchase ID` are not materialized as orphan line items.
- Added `WonderFoodWorkspaceSnapshotMergerTest.reportsPurchaseLineWithoutPurchaseIdAndDoesNotCreateOrphanLine`.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodWorkspaceSnapshotMergerTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Goal budget check after this slice: about 3h18m elapsed, still under the 6h cap. Skipped emulator for this non-UI provider validation slice to conserve usage; no UI behavior changed.

## 2026-07-20 Phase 1 Notion purchase-line category/disposition proof

- Continued with a compact provider-proof slice for Notion, using the shared workspace schema already exercised through Google Sheets.
- Added `NotionGatewayTest.purchaseLinesDatabaseExportsAndParsesCategoryAndDisposition`.
- The test proves the Notion Purchase Lines database declares `Category` and `Disposition` as select properties, structured page export writes the values from a receipt line, and Notion property parsing returns those values for merge/import.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.NotionGatewayTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Goal budget check after this slice: about 3h19m elapsed, still under the 6h cap.

## 2026-07-20 Connected UI proof after provider merge slices

- Started the known-good Pixel 3a AVD headless with Android SDK roots set and `-gpu swiftshader_indirect`.
- Waited for `sys.boot_completed=1` and package manager readiness before test execution.
- Verified `./gradlew :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wonderfood.app.ui.main.MainScreenTest`: BUILD SUCCESSFUL.
- Connected result: 8 tests finished on `Pixel_3a_API_34_extension_level_7_arm64-v8a(AVD) - 14`, 0 skipped, 0 failed.
- Shut down `emulator-5554`, confirmed the emulator process exited, and confirmed `adb devices -l` has no attached devices.
- Goal budget check after UI proof: about 3h22m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 DataHomeAdapter contract slice

- Re-read the pasted orchestration brief, latest progress, worktree status, and goal budget before continuing.
- Inspected existing Google Sheets, Notion, Postgres gateway, and Google Sheets sync coordinator APIs.
- Added `DataHomeAdapter`, `DataHomeConnection`, shared health/provision/pull/push/repair/disconnect result types, and concrete Google Sheets and Notion adapter wrappers.
- Added `NotionWorkspaceGateway` so Notion adapter tests can use a fake gateway without live network credentials.
- Updated `NotionGateway` to implement `NotionWorkspaceGateway`.
- Added `DataHomeAdapterTest` covering Google Sheets and Notion provision, push, pull/merge, health/probe, repair, and disconnect behavior through fake gateways.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.DataHomeAdapterTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Goal budget check after this slice: about 3h26m elapsed, still under the 6h cap. Skipped emulator for this non-UI adapter-contract slice; connected `MainScreenTest` was already verified after the prior provider slices.

## 2026-07-20 Phase 1 Postgres DataHomeAdapter slice

- Re-read the pasted orchestration brief, latest progress, worktree status, and goal budget before continuing.
- Inspected `PostgresGateway`, `PostgresGatewayTest`, `PostgresConnectionMode`, and the existing `DataHomeAdapter` contract.
- Extended `DataHomeConnection` with hosted Postgres/Supabase credentials and added `DataHomeKind.POSTGRES_SUPABASE`.
- Extended `DataHomePullResult` so providers that pull canonical snapshots, not editable workspace merges, can return a remote `WonderFoodSnapshot`.
- Added `PostgresHostedGateway` and `PostgresDataHomeAdapter` for hosted probe/provision, snapshot pull, snapshot push, repair, and disconnect behavior.
- Updated `PostgresGateway` to implement `PostgresHostedGateway`.
- Strengthened `DataHomeAdapterTest` with hosted Postgres adapter proof and direct-DSN rejection proof, preserving the server-side-adapter boundary for Android remote sync.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.DataHomeAdapterTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Goal budget check after this slice: about 3h28m elapsed, still under the 6h cap. Skipped emulator for this non-UI adapter-contract slice; connected `MainScreenTest` was already verified after the provider merge slices.

## 2026-07-20 Phase 1 DataHomeAdapter initial scan slice

- Re-read the pasted orchestration brief, latest progress, worktree status, and goal budget before continuing.
- Inspected current `DataHomeAdapter` and `DataHomeAdapterTest`.
- Added explicit `initialScan(connection)` to the shared provider contract so onboarding/sync can ask what already exists in a selected data home before pull or push.
- Added `DataHomeInitialScanResult` with provider kind, remote ID, row count, surface count, remote snapshot presence, and summary.
- Implemented initial scan for Google Sheets by reading editable workspace rows and counting visible tabs.
- Implemented initial scan for Notion by reading editable workspace rows and counting databases represented by rows.
- Implemented initial scan for hosted Postgres/Supabase by checking for the current household snapshot.
- Strengthened `DataHomeAdapterTest` to assert initial scan behavior for Google Sheets, Notion, and hosted Postgres.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.DataHomeAdapterTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Goal budget check after this slice: about 3h30m elapsed, still under the 6h cap. Skipped emulator for this non-UI adapter-contract slice; connected `MainScreenTest` was already verified after provider merge slices.

## 2026-07-20 Phase 1 DataHomeAdapter retry slice

- Re-read the pasted orchestration brief, latest progress, worktree status, and goal budget before continuing.
- Inspected current `DataHomeAdapter` and `DataHomeAdapterTest`.
- Added `DataHomeRetryPolicy` and `RetryingDataHomeAdapter` as a shared provider retry wrapper.
- Retry behavior covers thrown transient failures from provision, initial scan, pull, push, and repair.
- Retry behavior also retries `probe`/`health` when a provider returns `DataHomeStatus.ERROR`.
- Disconnect remains a simple delegated local operation.
- Strengthened `DataHomeAdapterTest` with retry success and retry exhaustion coverage.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.DataHomeAdapterTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Goal budget check after this slice: about 3h31m elapsed, still under the 6h cap. Skipped emulator for this non-UI adapter-contract slice; connected `MainScreenTest` was already verified after provider merge slices.

## 2026-07-20 Phase 1 workspace conflict Needs review slice

- Re-read the pasted orchestration brief, latest progress, worktree status, and goal budget before continuing.
- Inspected `WonderFoodWorkspaceSnapshotMerger`, its conflict model, and focused merge tests.
- Added `WorkspaceConflictDisposition.NEEDS_REVIEW` and attached it to every `WorkspaceMergeConflict` by default.
- Strengthened invalid friendly-row coverage so quantity, money, meal date, meal servings, and purchase-line amount conflicts explicitly surface as Needs review.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodWorkspaceSnapshotMergerTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Goal budget check after this slice: about 3h33m elapsed, still under the 6h cap. Skipped emulator for this non-UI merge-model slice; connected `MainScreenTest` was already verified after provider merge slices.

## 2026-07-20 Phase 1 DataHomeAdapter needs-review pull count slice

- Re-read the pasted orchestration brief, latest progress, worktree status, and goal budget before continuing.
- Inspected `DataHomeAdapter`, `DataHomeAdapterTest`, and current conflict consumers.
- Added `needsReviewCount` to `DataHomePullResult`.
- Google Sheets and Notion adapters now compute `needsReviewCount` from merge conflicts with `WorkspaceConflictDisposition.NEEDS_REVIEW`.
- Postgres snapshot pulls explicitly report zero needs-review rows because they return canonical snapshots rather than editable workspace merge conflicts.
- Strengthened `DataHomeAdapterTest` with invalid remote rows proving Sheets and Notion pull results expose review counts, while hosted Postgres remains zero.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.DataHomeAdapterTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Goal budget check after this slice: about 3h34m elapsed, still under the 6h cap. Skipped emulator for this non-UI adapter-contract slice; connected `MainScreenTest` was already verified after provider merge slices.

## 2026-07-20 Phase 1 live workspace proof conflict disposition slice

- Re-read the pasted orchestration brief, latest progress, worktree status, and goal budget before continuing.
- Inspected `WonderFoodLiveWorkspaceProofTest`.
- Updated live Notion and Google Sheets evidence JSON so each merge conflict includes its `disposition`, preserving the Needs review signal in proof artifacts.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodLiveWorkspaceProofTest`: BUILD SUCCESSFUL. Live credentials were not provided, so the test class compiled and local assumption-gated live proofs skipped.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Goal budget check after this slice: about 3h35m elapsed, still under the 6h cap. Skipped emulator for this non-UI proof-output slice; connected `MainScreenTest` was already verified after provider merge slices.

## 2026-07-20 Phase 1 DataHomeAdapter secret-redaction slice

- Re-checked the active goal budget before continuing.
- Inspected `DataHomeAdapter` and `DataHomeAdapterTest`.
- Added adapter-boundary redaction for provider token secrets in Google Sheets error summaries, Notion health summaries/errors, and hosted Postgres health/provision summaries/errors.
- Strengthened `DataHomeAdapterTest` with provider fakes that intentionally echo credential tokens through thrown Sheets errors, Notion page summaries, and Postgres hosted summaries; assertions prove the raw token is absent and the redaction marker is present.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.DataHomeAdapterTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Goal budget check after this slice: about 3h36m elapsed, still under the 6h cap. Skipped emulator for this non-UI adapter-hardening slice; connected `MainScreenTest` was already verified after provider merge slices.

## 2026-07-20 Phase 1 canonical Today spending preview slice

- Re-checked the active goal budget and current Phase 1 plan before continuing.
- Inspected `MainScreenViewModel`, `MainScreen`, existing canonical Cart/Kitchen/Search projections, and connected `MainScreenTest`.
- Added `CanonicalRecentSpendingItem` to project recent canonical purchase lines into UI-ready rows with amount, spend category, and merchant.
- Added `CanonicalRecentSpendingItemTest`, proving ignored receipt lines are excluded and recent canonical purchase lines are sorted/displayed with receipt-backed spending details.
- Wired the projection through `WonderFoodUiState.refreshCanonicalSummary()`.
- Added a `Recent spending` section to the `Now`/Today surface so canonical receipt-backed spending is visible in the primary app flow.
- Added connected UI coverage that seeds a canonical purchase line before activity launch and asserts `Recent spending`, `Dish soap spending proof`, and `USD 8.99  Cleaning  Target` render from the household repository.
- First connected assertion attempt failed because the test seeded data after the ViewModel was already created; fixed by moving the seed to `@BeforeClass`, checkpointing the DB, and keeping the old startup shell assertion canonical-state agnostic.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.CanonicalRecentSpendingItemTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebugAndroidTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wonderfood.app.ui.main.MainScreenTest`: BUILD SUCCESSFUL, 9 tests on `Pixel_3a_API_34_extension_level_7_arm64-v8a(AVD) - 14`, 0 skipped, 0 failed.
- Shut down `emulator-5554`, confirmed the emulator process exited, and confirmed `adb devices -l` has no attached devices.
- Verified `git diff --check`: no whitespace errors.
- Goal budget check after this slice: about 3h55m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 canonical Week plan preview slice

- Re-checked the active goal budget, pasted orchestration brief, memory guidance, and current Phase 1 progress before continuing.
- Inspected `PlanContent`, `PlanWeekStrip`, canonical `MealEntry`/`MealPlan` model shape, and existing canonical preview patterns.
- Added `CanonicalWeekPlanItem` to project current-week canonical meal entries into UI-ready rows with day, slot, servings, and status.
- Added `CanonicalWeekPlanItemTest`, proving current-week planned meals render while skipped/out-of-week rows are excluded.
- Wired the projection through `WonderFoodUiState.refreshCanonicalSummary()`.
- Added a `Household week` section to the Week surface so canonical planned meals from `HouseholdRepository` appear alongside the existing legacy plan UI.
- Extended connected `MainScreenTest` setup to seed a canonical `MealEntry` before activity launch, checkpoint Room, and assert `Household week` plus `Tomato rasam week proof` render from the canonical repository.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.CanonicalWeekPlanItemTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug :app:assembleFossDebugAndroidTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wonderfood.app.ui.main.MainScreenTest`: BUILD SUCCESSFUL, 10 tests on `Pixel_3a_API_34_extension_level_7_arm64-v8a(AVD) - 14`, 0 skipped, 0 failed.
- Shut down `emulator-5554`, confirmed the emulator process exited, and confirmed `adb devices -l` has no attached devices.
- Verified `git diff --check`: no whitespace errors.
- Goal budget check after this slice: about 4h04m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 canonical Saved recipe preview slice

- Re-checked the active goal budget, pasted orchestration brief, memory guidance, and current Phase 1 progress before continuing.
- Inspected `RecipesContent`, the canonical `Recipe` model shape, and existing canonical preview wiring patterns.
- Added `CanonicalSavedRecipeItem` to project active canonical household recipes into UI-ready rows with category, cuisine, yield, duration, and status.
- Added `CanonicalSavedRecipeItemTest`, proving active recipes render and archived recipes are excluded.
- Wired the projection through `WonderFoodUiState.refreshCanonicalSummary()`.
- Added a `Household recipes` section to the bottom-nav Saved surface so canonical saved recipes from `HouseholdRepository` appear in the primary app flow.
- Extended connected `MainScreenTest` setup to seed a canonical `Recipe` before activity launch, checkpoint Room, and assert `Household recipes` plus `Vegetable pulao recipe proof` render from the canonical repository.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.CanonicalSavedRecipeItemTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug :app:assembleFossDebugAndroidTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wonderfood.app.ui.main.MainScreenTest`: BUILD SUCCESSFUL, 11 tests on `Pixel_3a_API_34_extension_level_7_arm64-v8a(AVD) - 14`, 0 skipped, 0 failed.
- Shut down `emulator-5554` and confirmed `adb devices -l` has no attached devices.
- Verified `git diff --check`: no whitespace errors.
- Goal budget check after this slice: about 4h06m elapsed before the connected test and about 4h10m elapsed after, still under the 6h cap.

## 2026-07-20 Phase 1 canonical recipe ingredient and Can make slice

- Re-checked the active goal budget, pasted orchestration brief, active planning files, and current worktree before continuing.
- Corrected a harmless plan-restore read attempt that treated `.planning/.active_plan` as a direct path instead of a plan id; the active plan path is `.planning/2026-07-19-wonderfood-105-zero-user-reset`.
- Inspected the legacy `CanMakeRecipesContent`, canonical `Recipe`/`RecipeIngredient` model shape, Room household repository, and connected `MainScreenTest`.
- Added canonical `UpsertRecipeIngredient` to the household command boundary and validation.
- Added the `household_recipe_ingredients` Room entity, DAO methods, repository mapping, snapshot readback, schema v4, and `MIGRATION_3_4`.
- Added `CanonicalRecipeMatchItem` to rank active canonical recipes from canonical recipe ingredients and active canonical inventory lots as `Can make`, `Almost`, or `Need more`.
- Wired canonical recipe matches through `WonderFoodUiState.refreshCanonicalSummary()`.
- Updated the Food > Can make surface to prefer canonical recipe matches when present, reducing that user-facing recipe ranking path's dependence on legacy `FoodMemory`.
- Extended connected `MainScreenTest` setup to seed canonical food inventory, a recipe ingredient, and a recipe linked to that ingredient before activity launch.
- Added connected proof that Food > Can make renders `Vegetable pulao recipe proof` as `Can make  1/1 in Kitchen  35 min` from the canonical repository.
- Verified `./gradlew :core:data:testDebugUnitTest --tests com.wonderfood.core.data.room.RoomHouseholdRepositoryTest.recipeIngredientPersistsAcrossRestart :app:testFossDebugUnitTest --tests com.wonderfood.app.data.CanonicalRecipeMatchItemTest`: BUILD SUCCESSFUL. First attempt failed because the new Room entity lacked metadata/source/confidence mappers; added those mappers and reran successfully.
- Verified `./gradlew :app:assembleFossDebug :app:assembleFossDebugAndroidTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wonderfood.app.ui.main.MainScreenTest`: BUILD SUCCESSFUL, 12 tests on `Pixel_3a_API_34_extension_level_7_arm64-v8a(AVD) - 14`, 0 skipped, 0 failed.
- Shut down `emulator-5554` and confirmed `adb devices -l` has no attached devices.
- Verified `git diff --check`: no whitespace errors.
- Goal budget check after this slice: about 4h22m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 accepted recipe draft ingredient command slice

- Re-checked the active goal budget, pasted orchestration brief, memory guidance, active planning files, and current worktree before continuing.
- Inspected `HouseholdDraftCommandMapper`, `RecipeDraft`, `RecipeIngredientParser`, and focused mapper tests.
- Changed accepted `RecipeDraft` mapping from a recipe-root-only command to a canonical recipe command plus one `UpsertRecipeIngredient` command per parsed ingredient line.
- Linked canonical recipe `ingredientIds` to the generated ingredient command ids so accepted AI/import/manual recipe proposals can later drive canonical Can make matching.
- Scoped recipe command ids by recipe title instead of one generic `recipe` command scope, preventing unrelated accepted recipes from sharing the same idempotency key.
- Added focused mapper assertions for parsed `cup` and `clove` ingredient quantities and command linkage.
- Added a Room-backed mapper integration test proving accepted recipe draft commands persist canonical recipe ingredients through `HouseholdRepository`.
- First integration test attempt failed because `ApplicationProvider` needs a registered instrumentation in a local JVM test; fixed by running the class with Robolectric.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI mapper slice; the previous UI-affecting slice was verified with connected `MainScreenTest` 12/12.

## 2026-07-20 Phase 1 meal-plan shopping gap command slice

- Re-checked the active goal budget, pasted orchestration brief, active planning files, and current worktree before continuing.
- Inspected `MealPlanDraft` mapping, canonical `ShoppingLine` provenance fields, and focused mapper tests.
- Changed accepted meal-plan drafts so `groceryHint` entries create canonical `UpsertShoppingLine` commands instead of remaining notes only.
- Marked generated shopping lines as `ShoppingReason.RECIPE_GAP`, category `Meal plan`, and linked `sourceEntityIds` to the canonical meal plan plus all generated meal entries.
- Added focused test coverage proving a Week plan with `groceryHint = "lentils"` now emits a canonical Cart line with plan/entry provenance.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI mapper slice; no rendered UI changed after the previous connected `MainScreenTest` 12/12 proof.

## 2026-07-20 Phase 1 shopping-line provenance persistence slice

- Re-checked the active goal budget, pasted orchestration brief, active planning files, and current worktree before continuing.
- Inspected `CanonicalCartPreviewItem`, meal-plan mapper integration coverage, and `ShoppingLine.sourceEntityIds`.
- Found that canonical `ShoppingLine` had provenance in the domain model, but Room did not persist `sourceEntityIds`.
- Added `source_entity_ids` to `HouseholdShoppingLineEntity`, repository entity/domain mapping, schema v5, and `MIGRATION_4_5`.
- Added Room-backed mapper proof that accepted meal-plan grocery-gap commands persist source provenance and appear through `CanonicalCartPreviewItem`.
- Verified generated `core/data/schemas/com.wonderfood.core.data.room.WonderFoodDatabase/5.json` includes `source_entity_ids`.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI persistence/projection slice; no rendered UI changed after the previous connected `MainScreenTest` 12/12 proof.

## 2026-07-20 Phase 1 canonical meal-plan Cart UI proof slice

- Re-checked the active goal budget, pasted orchestration brief, active planning files, and current worktree before continuing.
- Extended connected `MainScreenTest` setup to seed a canonical `ShoppingLine` with `ShoppingReason.RECIPE_GAP`, category `Meal plan`, quantity `1 cup`, and meal-plan/meal-entry `sourceEntityIds`.
- Added connected proof that Cart renders `Lentils meal gap proof` and `1 cup  Meal plan  recipe gap` from the canonical household repository.
- Verified `./gradlew :app:assembleFossDebug :app:assembleFossDebugAndroidTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wonderfood.app.ui.main.MainScreenTest`: BUILD SUCCESSFUL, 13 tests on `Pixel_3a_API_34_extension_level_7_arm64-v8a(AVD) - 14`, 0 skipped, 0 failed.
- Shut down `emulator-5554` and confirmed `adb devices -l` has no attached devices.
- Verified `git diff --check`: no whitespace errors.
- Goal budget check after this slice: about 4h38m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 accepted meal identity command slice

- Re-checked the active goal budget, pasted orchestration brief, planning skill, memory guidance, active planning files, and current worktree before continuing.
- Found that accepted `MealLogDraft` commands used the coarse `meal_log` scope and accepted `MealPlanDraft` commands used `meal_plan` plus title-only entity identity, so repeated meal titles or repeated `Week plan` titles could collapse into one canonical entity/command record.
- Scoped canonical meal-log command/entity identity by date or creation time, slot, title, nutrients, used items, and source.
- Scoped canonical meal-plan, meal-entry, and generated recipe-gap shopping-line identities by start week, title, plan text, grocery hints, and entry content.
- Added focused mapper assertions that the same meal title on different dates creates distinct canonical meal entries and command ids.
- Added Room-backed mapper assertions that repeated accepted meal logs persist as separate canonical entries through `HouseholdRepository`.
- Added focused mapper assertions that the same meal-plan title on different weeks creates distinct canonical plans, linked entries, and recipe-gap shopping lines.
- Added Room-backed mapper assertions that repeated accepted meal-plan titles persist as separate canonical weeks through `HouseholdRepository`.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI mapper/idempotency slice; rendered behavior did not change after the previous connected `MainScreenTest` 13/13 proof.
- Goal budget check after this slice: about 4h42m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 canonical command rejection hardening slice

- Re-checked the active goal budget, pasted orchestration brief, planning skill, memory guidance, active planning files, and current worktree before continuing.
- Inspected `executeCanonicalDraftCommand`, canonical CSV import, legacy-to-canonical mirror handling, and the canonical-direct draft gate.
- Confirmed accepted meal logs and meal plans already use the canonical-direct gate when their mapper emits commands; did not duplicate that work.
- Added shared `executeCanonicalHouseholdCommands(...)` handling in `MainScreenViewModel`: `Applied` and `Duplicate` household command results are accepted, while `Rejected` results are converted into explicit command-type error messages.
- Changed canonical CSV import, reviewed draft accept, and legacy fallback mirroring to fail closed when any canonical household command is rejected, instead of silently ignoring the rejection and reporting success.
- Verified `./gradlew :app:compileFossDebugKotlin`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Confirmed `adb devices -l` has no attached devices; skipped emulator because this non-UI command-result hardening slice did not change rendered behavior.
- Goal budget check after this slice: about 4h45m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 canonical meal-log nutrition snapshot slice

- Re-checked the active goal budget, pasted orchestration brief, memory guidance, active planning files, and current worktree before continuing.
- Found accepted `MealLogDraft` nutrients were preserved only in meal-entry notes, even though the canonical model has first-class `NutritionSnapshot` and `NutritionValues`.
- Added `UpsertNutritionSnapshot` to the canonical household command boundary with household validation.
- Added the `household_nutrition_snapshots` Room entity, DAO methods, repository mapping, snapshot readback, schema v6, and `MIGRATION_5_6`.
- Updated accepted meal-log command mapping so calories, protein, carbs, and fat create a canonical `NutritionSnapshot` subject-linked to the generated meal entry; the meal entry now references that snapshot id through `nutritionSnapshotIds`.
- Kept nutrient-free meal logs as meal-entry-only commands.
- Added focused mapper assertions proving a nutrient-bearing `MealLogDraft` emits `UpsertMealEntry` plus `UpsertNutritionSnapshot` with meal-entry subject linkage.
- Added Room-backed mapper assertions proving accepted meal-log nutrition snapshots persist through `HouseholdRepository`.
- Added repository restart coverage proving a standalone canonical nutrition snapshot survives database close/reopen and remains linked from its meal entry.
- Verified `./gradlew :core:data:testDebugUnitTest --tests com.wonderfood.core.data.room.RoomHouseholdRepositoryTest.nutritionSnapshotPersistsAcrossRestartAndLinksToMealEntry :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest`: BUILD SUCCESSFUL.
- Verified generated `core/data/schemas/com.wonderfood.core.data.room.WonderFoodDatabase/6.json` has `version = 6` and includes `household_nutrition_snapshots`.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Confirmed `adb devices -l` has no attached devices; skipped emulator for this non-UI persistence/mapper slice.
- Goal budget check after this slice: about 4h49m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 canonical Week nutrition UI proof slice

- Re-checked the active goal budget, pasted orchestration brief, memory guidance, and current worktree before continuing; remained under the 6h cap and did not broaden scope while the user was asleep.
- Extended `CanonicalWeekPlanItem` so Week preview rows include the first linked canonical `NutritionSnapshot` for a meal entry, currently rendering kcal and protein when present.
- Extended `CanonicalWeekPlanItemTest` with a linked nutrition snapshot fixture proving a planned meal row includes `520 kcal  24g protein`.
- Extended connected `MainScreenTest` setup to seed a canonical `NutritionSnapshot` linked to the canonical Week meal-entry proof before activity launch.
- Added connected UI assertion that Week renders the linked nutrition text `520 kcal  24g protein` from `HouseholdRepository`.
- First focused unit-test attempt failed because a nullable subtitle component used `filter` before null removal; fixed the projection to use `listOfNotNull`.
- A parallel assemble attempt exposed generated KSP cache corruption and then a KSP registration collision; moved only generated KSP dirs aside to `/tmp/wonderfood-ksp-cache-backup-20260720064127`, stopped the Gradle daemon, and reran the build serially.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.CanonicalWeekPlanItemTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug :app:assembleFossDebugAndroidTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wonderfood.app.ui.main.MainScreenTest`: BUILD SUCCESSFUL, 13 tests on `Pixel_3a_API_34_extension_level_7_arm64-v8a(AVD) - 14`, 0 skipped, 0 failed.
- Shut down `emulator-5554`, confirmed the emulator process exited, and confirmed `adb devices -l` has no attached devices.
- Verified `git diff --check`: no whitespace errors.
- Goal budget check after this slice: about 5h01m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 canonical inventory nutrition import slice

- Re-checked the active goal budget, pasted orchestration brief, planning files, and memory guidance before continuing; selected a bounded non-UI Phase 1 import migration with under an hour left before the 6h cap.
- Found that provider/CSV direct canonical import still rejected any `nutritionSnapshots`, and inventory draft mapping dropped `FoodCandidate` calories/macros even though canonical `NutritionSnapshot` persistence now exists.
- Updated `HouseholdDraftCommandMapper` so nutrient-bearing inventory candidates emit an item-linked `UpsertNutritionSnapshot` after the canonical `UpsertItem` and `UpsertInventoryLot` commands.
- Fixed quantity unit parsing so `1 serving` maps to `QuantityUnit.SERVING` instead of being caught by the gram check.
- Relaxed `LegacySnapshotDraftImporter.canImportDirectlyToCanonicalHousehold(...)` to allow nutrition snapshots attached to imported provider foods or meal logs, while still rejecting orphan/unsupported nutrition subjects.
- Added focused mapper coverage proving inventory nutrition imports as an item-linked canonical nutrition snapshot with kcal/protein/carbs/fat and provider label.
- Added provider importer coverage proving supported food nutrition can take the direct canonical path and orphan nutrition remains unsupported.
- First focused test run failed on the `1 serving` unit assertion; fixed the parser ordering and reran successfully.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI importer/mapper slice; the previous UI-affecting slice has connected `MainScreenTest` 13/13 proof.
- Goal budget check after this slice: about 5h06m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 canonical provider nutrition export slice

- Continued within the same budget window and checked the adjacent provider export path.
- Found that `CanonicalHouseholdSnapshotExporter` still exported `nutritionSnapshots = emptyList()` and left exported food/meal-log `nutritionSnapshotIds` empty, so canonical nutrition could persist locally but disappear from Notion/Sheets/Postgres snapshot payloads.
- Updated provider snapshot projection to export canonical `NutritionSnapshot` rows for item, meal-entry, and recipe subjects that have provider equivalents.
- Linked exported item nutrition snapshots back to provider `Food.nutritionSnapshotIds`.
- Linked exported meal-entry nutrition snapshots back to provider `MealLog.nutritionSnapshotIds`.
- Added focused provider exporter assertions for item-linked `120 kcal / 18g protein` nutrition and meal-log-linked `520 kcal / 24g protein` nutrition.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.CanonicalHouseholdSnapshotExporterTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI provider projection slice; no rendered UI changed after the previous connected `MainScreenTest` 13/13 proof.
- Goal budget check after this slice: about 5h08m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 canonical CSV nutrition export slice

- Re-checked the active goal budget, pasted orchestration brief, memory guidance, planning files, and current worktree before continuing.
- Found that canonical CSV import already parses `calories`, `protein_g`, `carbs_g`, and `fat_g`, but canonical CSV export dropped linked nutrition for inventory items and meal logs.
- Updated canonical CSV export to include item-linked nutrition snapshot serving text, kcal, protein, carbs, fat, and provider label on inventory rows.
- Updated canonical CSV export to include the first linked meal-entry nutrition snapshot kcal, protein, carbs, fat, and provider label on meal-log rows.
- Added canonical CSV fixture nutrition for one inventory item and one eaten meal entry, then asserted export/parse round-trips those values into `FoodCandidate` and `MealLogDraft`.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodCsvGatewayTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI CSV projection slice; no rendered UI changed after the previous connected `MainScreenTest` 13/13 proof.
- Goal budget check after this slice: about 5h11m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 canonical CSV recipe ingredient export slice

- Continued from the CSV nutrition slice after another budget check showed about 5h11m elapsed.
- Found that canonical recipe ingredients are persisted in `HouseholdSnapshot.recipeIngredients`, but canonical CSV export still emitted recipe roots without `ingredients`, so CSV round-trip would lose ingredient text.
- Updated canonical CSV export to group `RecipeIngredient` rows by recipe id and write sorted `originalText` lines into each recipe row's `ingredients` column.
- Extended the canonical CSV round-trip fixture with two recipe ingredients and asserted the parsed `RecipeDraft.ingredientsText` preserves both lines.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodCsvGatewayTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI CSV projection slice; no rendered UI changed after the previous connected `MainScreenTest` 13/13 proof.
- Goal budget check after this slice: about 5h12m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 canonical provider recipe ingredient export slice

- Continued after checking the clock again; selected the matching provider projection gap to avoid preserving recipe-root-only exports.
- Found that `CanonicalHouseholdSnapshotExporter` still emitted provider `Recipe.ingredients = emptyList()` even though canonical `RecipeIngredient` rows now persist in Room and drive Can make ranking.
- Updated provider snapshot export to group canonical `RecipeIngredient` rows by recipe id and project them into provider `RecipeIngredient` objects with stable canonical ids, quantity, preparation, optional flag, and substituted item ids.
- Extended provider exporter coverage so the Sambar fixture exports `1 cup toor dal` and `2 cups bhindi` as provider recipe ingredients.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.CanonicalHouseholdSnapshotExporterTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI provider projection slice; no rendered UI changed after the previous connected `MainScreenTest` 13/13 proof.
- Goal budget check after this slice: about 5h13m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 canonical provider recipe nutrition linkage slice

- Re-checked the goal budget, pasted orchestration brief, current worktree, and memory guidance before continuing.
- Found that recipe-subject canonical nutrition snapshots were projected into provider `nutritionSnapshots`, but provider `Recipe.nutritionSnapshotIds` stayed empty, leaving recipe nutrition disconnected in Notion/Sheets/Postgres payloads.
- Updated `CanonicalHouseholdSnapshotExporter` so provider recipe rows link nutrition snapshot ids for canonical `NutritionSnapshot` records whose subject is the canonical recipe.
- Extended provider exporter coverage with recipe-scoped nutrition for the Sambar fixture and asserted the provider recipe links that nutrition id.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.CanonicalHouseholdSnapshotExporterTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI provider projection slice; no rendered UI changed after the previous connected `MainScreenTest` 13/13 proof.
- Goal budget check after this slice: about 5h15m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 canonical recipe step export slice

- Re-checked the active goal budget, pasted orchestration brief, current worktree, and recent progress before continuing.
- Found that canonical `RecipeStep` rows exist in the household model/schema, but `CanonicalHouseholdSnapshotExporter` still emitted provider `Recipe.steps = emptyList()` and canonical CSV recipe rows omitted `steps`, so cooking directions could be lost when projecting to Notion/Sheets/Postgres or CSV.
- Updated provider snapshot export to group canonical recipe steps by recipe id and project them into legacy/provider `RecipeStep` objects with stable canonical ids, order, instruction, duration, source, confidence, and truth state.
- Updated canonical CSV export to group recipe steps by recipe id and write sorted instructions into the recipe row `steps` column.
- Extended provider exporter coverage to assert Sambar exports `Boil dal until soft.` and `Simmer with bhindi.` plus the first step duration.
- Extended canonical CSV round-trip coverage to assert parsed `RecipeDraft.stepsText` preserves both exported step lines.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.CanonicalHouseholdSnapshotExporterTest --tests com.wonderfood.app.sync.WonderFoodCsvGatewayTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI provider/CSV projection slice; no rendered UI changed after the previous connected `MainScreenTest` 13/13 proof.
- Goal budget check before broader gates: about 5h19m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 canonical recipe step persistence slice

- Re-checked the active goal budget, pasted orchestration brief, task plan, recent progress, and current worktree before continuing.
- Found that recipe step text was now preserved on provider/CSV export, but accepted `RecipeDraft` inputs still only stored steps in recipe description because the canonical household command path had no `UpsertRecipeStep` or `household_recipe_steps` Room table.
- Added `HouseholdCommand.UpsertRecipeStep` and command validation requiring the command household to match the recipe step household.
- Added Room v7 `household_recipe_steps`, DAO upsert/query methods, recorded `upsertRecipeStepAndRecord`, migration `MIGRATION_6_7`, and generated schema `core/data/schemas/com.wonderfood.core.data.room.WonderFoodDatabase/7.json`.
- Updated `RoomHouseholdRepository` to apply recipe-step commands and include canonical `recipeSteps` in `HouseholdSnapshot`.
- Updated `HouseholdDraftCommandMapper` so accepted recipe draft step lines create canonical `RecipeStep` rows and recipe `stepIds`, instead of only preserving directions in description text.
- Extended mapper tests to assert `UpsertRecipeStep` command identity, recipe linkage, order, instruction, and repository snapshot persistence.
- First focused test run failed on missing Room metadata/source/confidence helper overloads for `HouseholdRecipeStepEntity`; added the helper overloads and reran successfully.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified generated Room schema `7.json` has `version = 7` and includes `household_recipe_steps`.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI persistence/mapper slice; no rendered UI changed after the previous connected `MainScreenTest` 13/13 proof.
- Goal budget check before broader gates: about 5h23m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 provider recipe step direct-import proof slice

- Re-checked the active goal budget, pasted orchestration brief, current progress, and the provider import path before continuing.
- Confirmed `LegacySnapshotDraftImporter` already maps provider `Recipe.steps` into `RecipeDraft.stepsText`, and the ViewModel direct-import path applies `HouseholdDraftCommandMapper` commands fail-closed.
- Added importer coverage asserting provider recipe steps round-trip into `RecipeDraft.stepsText` instead of silently falling back to description text.
- Added importer/mapper boundary coverage proving provider recipe step text creates canonical `UpsertRecipeStep` commands and links their ids from the canonical recipe root `stepIds`.
- First focused test run failed because the test assumed all imports return `CompositeDraft`; fixed the assertion to handle the single-`RecipeDraft` shape.
- Verified `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI importer/mapper proof slice; no rendered UI changed after the previous connected `MainScreenTest` 13/13 proof.
- Goal budget check before broader gates: about 5h25m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 recipe step command validation guard slice

- Re-checked the active goal budget, pasted orchestration brief, current progress, and the command executor tests before continuing.
- Found the newly added `UpsertRecipeStep` validation code had no engine-level regression test proving mismatched-household commands are rejected before repository mutation.
- Added `HouseholdCommandExecutorTest.executorRejectsMismatchedRecipeStepHouseholdBeforeRepositoryMutation`.
- Verified `./gradlew :core:engine:test --tests com.wonderfood.core.engine.HouseholdCommandExecutorTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI command validation test slice; no rendered UI changed after the previous connected `MainScreenTest` 13/13 proof.
- Goal budget check before broader gates: about 5h27m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 recipe step repository restart proof slice

- Re-checked the active goal budget, pasted orchestration brief, current worktree, and Room repository tests before continuing.
- Found `RoomHouseholdRepositoryTest` covered recipe root `stepIds`, but not the new `UpsertRecipeStep` row itself surviving close/reopen in `HouseholdSnapshot.recipeSteps`.
- Added `RoomHouseholdRepositoryTest.recipeStepPersistsAcrossRestart` to apply `UpsertRecipeStep`, close/reopen the file-backed Room database, and assert recipe `stepIds`, recipe id, instruction, duration, and timer label survive.
- Verified `./gradlew :core:data:testDebugUnitTest --tests com.wonderfood.core.data.room.RoomHouseholdRepositoryTest.recipeStepPersistsAcrossRestart`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI persistence test slice; no rendered UI changed after the previous connected `MainScreenTest` 13/13 proof.
- Goal budget check before broader gates: about 5h28m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 migration registration guard slice

- Re-checked the active goal budget, pasted orchestration brief, current progress, and migration test coverage before continuing.
- Found no existing migration tests and added a lightweight guard so the new Room v7 recipe-step migration cannot be accidentally dropped from `WonderFoodMigrations.ALL`.
- Added `WonderFoodMigrationsTest.registersContiguousHouseholdSchemaMigrationsThroughVersionSeven`, asserting the migration chain includes `1->2`, `2->3`, `3->4`, `4->5`, `5->6`, and `6->7`.
- Verified `./gradlew :core:data:testDebugUnitTest --tests com.wonderfood.core.data.room.WonderFoodMigrationsTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI migration registration test slice; no rendered UI changed after the previous connected `MainScreenTest` 13/13 proof.
- Goal budget check before broader gates: about 5h29m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 recipe step repository verification pass

- Re-checked the active goal budget, pasted orchestration brief, task status, and recent recipe-step persistence evidence before continuing.
- Ran the broader focused Room repository and migration guard suite together after the v7 recipe-step persistence changes.
- Verified `./gradlew :core:data:testDebugUnitTest --tests com.wonderfood.core.data.room.RoomHouseholdRepositoryTest --tests com.wonderfood.core.data.room.WonderFoodMigrationsTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI repository verification pass; no rendered UI changed after the previous connected `MainScreenTest` 13/13 proof.
- Goal budget check before broader gates: about 5h30m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 recipe step path combined verification pass

- Re-checked the active goal budget, pasted orchestration brief, current worktree, and recipe-step test coverage before continuing.
- Ran the combined focused recipe-step path across command validation, Room persistence/migration registration, draft mapping, provider import, provider export, and CSV export.
- Verified `./gradlew :core:engine:test --tests com.wonderfood.core.engine.HouseholdCommandExecutorTest :core:data:testDebugUnitTest --tests com.wonderfood.core.data.room.RoomHouseholdRepositoryTest --tests com.wonderfood.core.data.room.WonderFoodMigrationsTest :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest --tests com.wonderfood.app.sync.CanonicalHouseholdSnapshotExporterTest --tests com.wonderfood.app.sync.WonderFoodCsvGatewayTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI recipe-step verification pass; no rendered UI changed after the previous connected `MainScreenTest` 13/13 proof.
- Goal budget check before broader gates: about 5h31m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 canonical model contract regression pass

- Re-checked the active goal budget, pasted orchestration brief, current worktree, and recent recipe-step path verification before continuing.
- Re-ran the canonical household model contract after the v7 recipe-step persistence and projection work.
- Verified `./gradlew :core:model:test --tests com.wonderfood.core.model.household.HouseholdContractTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI model contract regression pass; no rendered UI changed after the previous connected `MainScreenTest` 13/13 proof.
- Goal budget check before broader gates: about 5h32m elapsed, still under the 6h cap.

## 2026-07-20 Phase 1 canonical runtime focused regression pass

- Re-checked the active goal budget, pasted orchestration brief, current worktree, and latest Phase 1 evidence before continuing.
- Ran a broader focused Phase 1 regression bundle covering canonical model contract, command validation, Room repository/migration, draft mapping, provider import/export, and CSV projection.
- Verified `./gradlew :core:model:test --tests com.wonderfood.core.model.household.HouseholdContractTest :core:engine:test --tests com.wonderfood.core.engine.HouseholdCommandExecutorTest :core:data:testDebugUnitTest --tests com.wonderfood.core.data.room.RoomHouseholdRepositoryTest --tests com.wonderfood.core.data.room.WonderFoodMigrationsTest :app:testFossDebugUnitTest --tests com.wonderfood.app.data.HouseholdDraftCommandMapperTest --tests com.wonderfood.app.sync.LegacySnapshotDraftImporterTest --tests com.wonderfood.app.sync.CanonicalHouseholdSnapshotExporterTest --tests com.wonderfood.app.sync.WonderFoodCsvGatewayTest`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:assembleFossDebug`: BUILD SUCCESSFUL.
- Verified `git diff --check`: no whitespace errors.
- Skipped emulator for this non-UI focused regression pass; no rendered UI changed after the previous connected `MainScreenTest` 13/13 proof.
- Goal budget check before broader gates: about 5h33m elapsed, still under the 6h cap.

## 2026-07-20 stop checkpoint near 6h cap

- Re-read the pasted orchestration brief and checked goal budget before continuing.
- Active goal was at about 5h35m elapsed, leaving roughly 24m before the user-specified 6h hard stop.
- Re-checked `git status --short --branch`; work remains on `codex/wonderfood-105-zero-user-reset` with the Phase 0/Phase 1 implementation changes still uncommitted.
- Verified `git diff --check`: no whitespace errors.
- Stopped intentionally instead of opening another implementation slice or emulator run because the full 1.0.5 completion gates still include broader work: removing remaining legacy production paths, live provider proofs, physical install, PR/merge, release, and artifact reporting.

## 2026-07-20 legacy runtime production-reference audit

- Re-read the pasted orchestration brief and checked goal budget before continuing; active elapsed time was about 5h37m, leaving roughly 22m before the user-specified 6h hard stop.
- Audited production references to `FoodChatStore`, `FoodMemory`, `readMemory()`, and legacy snapshot bridges with `rg`.
- Confirmed Phase 1 is still incomplete: `MainScreenViewModel` still owns `FoodChatStore(appContext)` and reads `store.readMemory()` for app state, voice recipe lookup, import fallback state, reset/reload flows, AI context, and working UI state.
- Confirmed `MainScreen` still renders many screens from `FoodMemory`, and production AI/capture/sync helpers still accept or project `FoodMemory` while callers migrate.
- Did not start a code migration in the final cap window because replacing app launch/read state and AI/UI memory context is a broad Phase 1 slice requiring focused implementation plus emulator proof.
- Next action: migrate `WonderFoodUiState.memory` and `MainScreenViewModel` launch/reload/read paths to a canonical `HouseholdSnapshot`-backed UI state, then remove `FoodChatStore` as the production state owner before deleting legacy bridge code.

## 2026-07-20 Settings diagnostics canonical schema reference

- Re-read the pasted orchestration brief and checked goal budget before continuing; active elapsed time was about 5h38m, leaving roughly 22m before the user-specified 6h hard stop.
- Removed a small production UI dependency on legacy `FoodChatStore`: Settings diagnostics now reports `WonderFoodDatabase.SCHEMA_VERSION` instead of `FoodChatStore.SCHEMA_VERSION`.
- Added `WonderFoodDatabase.SCHEMA_VERSION = 7` beside the canonical Room database declaration.
- Verified `./gradlew :app:compileFossDebugKotlin`: BUILD SUCCESSFUL.
- Skipped emulator because this slice only changes Settings diagnostic text and does not alter an interactive UI flow; emulator proof remains required for the broader `MainScreenViewModel`/`WonderFoodUiState` canonical state migration.

## 2026-07-20 backup manifest canonical schema reference

- Re-read the pasted orchestration brief and checked goal budget before continuing; active elapsed time was about 5h40m, leaving roughly 20m before the user-specified 6h hard stop.
- Removed another small production metadata dependency on legacy `FoodChatStore`: backup manifests now record `WonderFoodDatabase.SCHEMA_VERSION` instead of `FoodChatStore.SCHEMA_VERSION`.
- Verified `./gradlew :app:compileFossDebugKotlin`: BUILD SUCCESSFUL.
- Re-audited production `FoodChatStore` references; remaining app references are the real migration surface: `FoodWorkspaceAppFunctionService`, `MainScreenViewModel`, and `FoodChatStore` itself.

## 2026-07-20 AppFunctions legacy write-path audit

- Re-read the pasted orchestration brief and checked goal budget before continuing; active elapsed time was about 5h41m, leaving roughly 19m before the user-specified 6h hard stop.
- Inspected `FoodWorkspaceAppFunctionService`: direct AppFunction writes still use `FoodDraftCommandExecutor { FoodChatStore(applicationContext) }`.
- Confirmed AppFunctions normalize each action to `LinkActionDraft`, apply legacy `FoodDraftCommandPolicy`, run the legacy draft executor, and mark idempotency keys in shared preferences after success.
- Did not start this migration in the final cap window because a correct port must preserve request/action idempotency while routing supported non-destructive actions through `HouseholdDraftCommandMapper` plus `HouseholdRepository`, and must reject or review unsupported `LinkActionDraft` mutations without silently claiming success.
- Next action: add a canonical AppFunction action executor that maps supported external actions to household commands, records canonical command ids with the existing idempotency key, and returns `REJECTED`/`REVIEW_REQUIRED` for any action not yet representable by canonical commands.
