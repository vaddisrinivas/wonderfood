# Accelerated Campaign Progress

## 2026-07-20 Planning

- Created isolated plan `2026-07-20-wonderfood-105-accelerated-release-campa` in autonomous mode.
- Re-read the original task plan, progress, findings, orchestration brief, current branch, dirty state, and repository quality/release entry points.
- Preserved the original Phase 0-9 plan as the normative requirements source.
- Reorganized execution into four parallel lanes and two bounded campaigns of at most six hours each.
- Added exclusive file ownership, dependency gates, test cadence, token/credit caps, failure stop rules, and an exhaustive acceptance matrix.
- No production code, tests, builds, credentials, GitHub state, or release state changed during planning.

## Current Status

- Stage: Campaign 1 in-progress
- Execution: started
- Current rollup: `60 PASS / 0 BLOCKED / 16 TODO`
- Next action: continue remaining gesture/visual/live-provider/device/release proof rows, avoiding parallel Gradle unit-test tasks that write the same XML result files.

- 2026-07-23 LifeOS control-plane addendum: added the v5 portable control-plane track to `docs/lifeos/implementation-ledger.md` and implemented hard C0 first. `src/config/types.ts` now defines config-source/snapshot/conflict/control-plane contracts. SQLite `DATABASE_VERSION` is now 5 and creates `config_sources`, `config_snapshots`, and `config_conflicts` separately from household `records`. Verified `npm run check:control-plane-c0`, `npm run typecheck`, `npm run config:validate`, and `git diff --check`. Next action: C1 config fetcher registry for local/GitHub/URL/Notion/Sheets with no data-plane writes.
- 2026-07-23 LifeOS control-plane C1: added `src/config/fetchers.ts` and `tests/config/fetchers.test.ts`. Local, GitHub, URL, Notion and Sheets config sources now fetch into unvalidated snapshots without a DB handle or record-writer path. Verified `npm run check:control-plane-c1`, `npm run typecheck`, `npm test`, and `git diff --check`. Next action: C2 validation/merge/apply/undo for config proposals.
- 2026-07-23 LifeOS control-plane C2: added `src/config/runtime.ts` and `tests/config/runtime.test.ts`. Config snapshots now validate JSON/YAML-lite, merge additively by precedence, block scalar/destructive conflicts for review, apply only clean proposals, and undo to the prior control-plane state. Verified `npm run check:control-plane-c2`, `npm run typecheck`, `npm test`, and `git diff --check`. Next action: C3 persistence/sync guard so control-plane writes cannot mutate household records.
- 2026-07-23 LifeOS control-plane C3: added `src/config/sync.ts`, `src/db/config.ts`, and `tests/config/sync.test.ts`. Config sync now saves source/snapshot/conflict rows only, skips disabled-source fetches, stores conflicts without applying disputed config, and has a SQLite guard proving no `records` writes. Verified `npm run check:control-plane-c3`, `npm run typecheck`, `npm test`, and `git diff --check`. Next action: C4 Config Sources UX in app Settings/Config Studio.
- 2026-07-23 LifeOS control-plane C5 core: added `src/config/ai.ts` and `tests/config/ai.test.ts`. AI-authored config now previews added/changed keys, accepts only clean proposals through the same config runtime, rolls back with the undo receipt, and rejects household-record mutations disguised as config. Verified `npm run check:control-plane-c5`, `npm run typecheck`, `npm test`, and `git diff --check`. C4 UI remains pending because the repo instructions restrict Expo UI edits unless explicitly reopened.
- 2026-07-23 LifeOS control-plane gate integration: added umbrella `npm run check:control-plane` and wired it into `check:product` and `quality`. Verified `npm run check:control-plane`, `npm run typecheck`, `npm test`, `npm run config:validate`, and `git diff --check`.
- 2026-07-23 live provider refresh: patched live proof wrappers to set `SSL_CERT_FILE` from certifi when unset, patched quality `tsx` runners to pass root `tsconfig.json`, aligned direct Notion app writeback with the passing data-source parent shape, and made Notion parent discovery climb to the shallowest accessible parent page. Verified `scripts/quality/run-provider-live-proofs.sh notion sheets` with private env: fresh evidence `notion_scenarios-1784830715.json` and `google_sheets_scenarios-1784830731.json`. Verified `npm run check:live-provider-writeback` with Homebrew Node first in PATH and temp npm cache: fresh evidence `direct_provider_writeback-1784831123.json`. Notion row cleanup passed, but temporary proof database cleanup remains manual because Notion rejected archive for that database block path.
- 2026-07-23 product/native gate refresh: `check:product` initially failed because recovery import rejected new `config_sources`; fixed `src/db/recovery.ts` and seeded/restored config rows in `scripts/quality/check-roundtrip.ts`. Verified `npm run check:roundtrip`, `npm run typecheck`, and full `NPM_CONFIG_CACHE=/tmp/wonderfood-npm-cache npm run check:product` PASS. Native gates: `npm run phase8:check:health-connect` PASS, `npm run phase8:check:android-release-artifacts` PASS, and `npm run check:native-emulator` PASS after the script opened the Health Connect permission manager directly instead of relying on a flaky deep-link handoff for the system permission screen.
- 2026-07-23 release signing audit: private env has no Android keystore/password/alias/key password and no Expo/App Store Connect release variables. `npm run phase8:check:android-release-signed` fails with `APK is debug-signed`. Signed Play release and iOS/TestFlight remain real external blockers.
- 2026-07-23 Notion proof cleanup: added `scripts/quality/cleanup-notion-proof-artifacts.mjs`, patched Notion scenario proof to trash its scenario page, and patched direct provider writeback proof to archive its temporary Notion database through `/databases/{id}`. Ran cleanup against exact `WonderFood ... Proof` titles only; final evidence `notion-proof-cleanup-1784831987.json` passed with `archived_count=8`, `failed_count=0`. Fresh live proof evidence: `direct_provider_writeback-1784832006.json` passed with Notion `cleanup_database_archived=true`; `notion_scenarios-1784832028.json` passed with `cleanup_scenario_page_trashed=true`; `google_sheets_scenarios-1784832041.json` passed.

- Latest acceleration: added non-secret release/device row triage for `E01`, `E07`, `E09`, and `E12`-`E16`. The helper is read-only and records matrix status, scoped dirty state, ADB visibility, connected proof tasks, existing release APK/checksum candidates, and read-only GitHub PR/CI/release state when `gh` is available. It explicitly does not use signing secrets, install builds, create PRs, publish tags/releases, or mark rows PASS.

- Latest milestone: AppFunctionService now has focused canonical write evidence tests.

- Latest milestone: `MainScreenViewModel` now builds canonical `FoodMemory` from `HouseholdSnapshot` and no longer calls `store.readMemory()`; proposal accept/reject now runs through canonical draft command execution.

- Latest blocker: many mutation/action paths in `MainScreenViewModel` still call `store.*` mutation/event methods (`deleteInventory`, `updateRecipe`, `updateMealLog`, etc.), so A05 remains blocked; no emulator was available for `MainScreenTest` UI proof in this run.

- Latest milestone: E10 docs claim proof passed. Stale docs that named `FoodChatStore` as the app runtime or transaction proof now describe the canonical `HouseholdRepository`/`HouseholdCommandExecutor` path. Claim scan across README, FEATURES, CHANGELOG, docs, and production app sources has no active release overclaims; remaining hits are warning copy, historical changelog text, generic design wording, or Compose imports.

- Latest milestone: B08 meal-planning proof passed. Week projection now shows servings, leftovers, inventory coverage/subtraction, reviewed recipe gaps, nutrition, and provenance from canonical household state. Verified focused unit test and single connected emulator Week test: BUILD SUCCESSFUL.

- Latest milestone: C09 Notion visible product proof passed. Notion now exposes only WonderFood Home, Kitchen, Shopping, Meals, Recipes, Spending, and Help while keeping advanced/system support sources hidden from visible product export. Focused Notion/schema seed tests passed.

- Latest milestone: B09 mixed receipt proof passed. Receipt mapping now persists food lots, non-food household stock, ignored lines, reviewed purchase expenses, receipt attachment, and bidirectional lot/purchase-line links through canonical commands into Room. Focused and full mapper tests passed.

- Latest milestone: A02 command-boundary proof passed. Removed unused production `FoodMutationCommandExecutor`/`FoodMutationCommand` wrapper and routed the remaining event/receipt/image completion hooks through canonical command completion plus backend sync queueing. Scan for the old mutation wrapper is clean; app compile, androidTest compile, and focused draft/mapper tests passed.

- Latest milestone: Parallel-agent slice integrated and validated. B10 purchase/refund/reconciliation proof passed; C10-C13 Notion standalone contract proof passed; C15 Google Sheets create/select/paste onboarding proof passed with focused unit, compile, and emulator evidence. Current matrix is `59 PASS / 0 BLOCKED / 17 TODO`.

- Latest milestone: C24 provider sync proof passed. `DataHomeSyncCoordinator` and focused adapter tests prove Google Sheets, Notion, and Postgres sync cycles are pull/merge/push rather than export-only, import-only, append-only, or snapshot-dump based. Current matrix is `60 PASS / 0 BLOCKED / 16 TODO`.

## Evidence

| Check | Result |
|---|---|
| Original deliverables retained | Mapped in `acceptance_matrix.md` |
| Worker overlap controlled | Defined in `worker_contracts.md` |
| Canonical memory read migration | `app/src/main/java/com/wonderfood/app/ui/main/MainScreenViewModel.kt` |
| Focused local test pass | `./gradlew :app:compileFossDebugUnitTestKotlin`; `./gradlew :app:testFossDebugUnitTest --tests "com.wonderfood.app.FoodWorkspaceAppFunctionServiceTest"` |
| Resource limits encoded | 6h continuous, 500k target, 700k hard token stop, 8% credit stop |

## Errors

| Error | Resolution |
|---|---|
| None | N/A |

## Template Spike

- Downloaded and inspected public Smartsheet Restaurant Inventory and Simple Inventory workbooks as `.xlsx` files in `/tmp`.
- Could not duplicate the Notion template or LL Home workbook without logged-in/form-gated workspace access; mapped from public template pages/articles instead.
- Added `template_spike.md` with source links, workbook tabs/headers/formulas, mapping decisions, and implementation recommendations.
- Updated `task_plan.md` with the decision to borrow external template UX patterns while generating WonderFood-owned Notion/Sheets schemas.
- Chrome follow-up duplicated Tim Rawling's Notion template into the user's `manasa-srinivas` workspace under Private and updated `template_spike.md` with the live duplicated URL and observed database/property structure.

## 2026-07-20 Campaign 1 continuation

- Used `architecture-preflight`, `planning-with-files`, and compressed/caveman status mode for the resumed implementation run.
- Spawned three Spark workers with disjoint scopes:
  - Worker B updated canonical local-flow UI semantics/content descriptions and compiled `:app:compileFossAndroidTestKotlin`; connected emulator proof remains pending.
  - Worker C added `DataHomeAdapter`/retry/secret-redaction focused coverage; `DataHomeAdapterTest` passes.
  - Worker D added AI skill proposal intent, receipt parse, nutrition estimate contracts, and focused AI contract tests; remaining D11 golden matrix/docs cleanup still pending.
- Coordinator changed `MainScreenViewModel` legacy kitchen/cart mutation paths:
  - `deleteInventory`, `deleteGrocery`, and `markGroceryBought` now map legacy UI IDs back to canonical IDs and execute canonical archive/purchase commands.
  - `updateInventory` and `updateGrocery` now execute canonical update commands instead of `FoodChatStore` writes.
- Added canonical update helpers in `CanonicalKitchenMutationCommandFactory` and `CanonicalCartMutationCommandFactory`.
- Reduced `MainScreenViewModel.kt` direct `store.*` references from 45 to 40; remaining legacy authority is undo, preferences, voice/event/receipt capture, recipe/meal/plan actions, receipt status, water logs, reopen/close.
- Verified `./gradlew :app:compileFossDebugKotlin`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:testFossDebugUnitTest --tests "com.wonderfood.app.data.CanonicalKitchenMutationCommandFactoryTest" --tests "com.wonderfood.app.data.CanonicalCartMutationCommandFactoryTest" --tests "com.wonderfood.app.sync.DataHomeAdapterTest" --tests "com.wonderfood.app.ai.WonderFoodAiSkillContractsTest"`: BUILD SUCCESSFUL.
- Acceptance matrix remains `0 PASS / 2 BLOCKED / 74 TODO`; broad rows are not marked PASS because full row evidence is still missing.

## 2026-07-20 B05 connected proof

- Added connected `MainScreenTest.baKitchenRowAlternativesAddArchiveAndUndo`.
- The test restores local backend state, opens Food > In kitchen, verifies the seeded canonical kitchen row `Basmati rice can-make proof`, triggers add-to-cart and archive non-gesture alternatives, waits for `Undo`, and verifies undo restores the row after each action.
- Source still covers swipe gestures through `CanonicalKitchenPreviewSection` `SwipeToDismissBox`: `StartToEnd` adds to cart and `EndToStart` archives.
- Verified `./gradlew :app:compileFossDebugAndroidTestKotlin`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wonderfood.app.ui.main.MainScreenTest#baKitchenRowAlternativesAddArchiveAndUndo`: BUILD SUCCESSFUL on `Pixel_3a_API_34_extension_level_7_arm64-v8a(AVD) - 14`.
- Acceptance matrix now `65 PASS / 0 BLOCKED / 11 TODO`; `B05` is PASS.

## 2026-07-20 E07 connected harness attempt

- Checked device/SDK state: only `emulator-5554` / API 34 is connected; installed system image list only shows `system-images;android-34;google_apis;arm64-v8a`, so no supported low/current API pair is currently available.
- Ran broad `scripts/quality/android-harness.sh connected` on the available API 34 emulator.
- Interrupted after clear current-API failures to avoid looping: Compose lifecycle/runtime failures in `aMainScreenShowsFiveDestinationShell`, `aaFirstBootShowsBackendChoicesWithLocalFastest`, and `baKitchenRowAlternativesAddArchiveAndUndo`; timeout in `abConflictInboxRendersWithDismissSemantics`; display assertion failure in `acQuickAddCoversItemRecipeMealCartLineAndReceipt`.
- `E07` remains TODO; focused B05 passes in isolation, but the broad connected suite is not release-green.

## 2026-07-20 C25 direct-provider UI attempt

- Used agent-env to create a local non-printed URL handoff for the latest live Notion standalone proof page at `build/evidence/live-workspace/c25-direct-provider-urls.json`.
- Opened the live Notion page in the in-app browser; direct provider UI is blocked by Notion sign-in. Screenshot saved at `app/build/evidence/live-workspace/c25-direct-notion-ui.png`.
- Google Drive/Sheets lookup for the latest standalone proof sheet returned HTTP 401 from the stored token, so no direct Sheets UI URL was verified.
- `C25` remains TODO; API-derived visual report remains useful partial evidence, but direct Notion/Sheets UI inspection is still missing.

## 2026-07-20 GitHub/release and S23 continuation

- GitHub subagent checked PR/CI/tag/release state read-only: current branch `codex/wonderfood-105-zero-user-reset` is not pushed, has no PR, has no branch workflow runs, and `v1.0.5` tag/release is absent. Existing run on current HEAD is old `main` proof, not current dirty v1.0.5 release state. `E12`, `E14`, `E15`, and `E16` remain TODO.
- S23 is connected as USB serial `R3CW10MSVRT`, Android 16 / API 36 / `SM-S918U1`.
- Collected S23 before/current evidence under `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/physical-device-e09-20260720-162239/`.
- `adb install -r app/build/outputs/apk/foss/debug/app-foss-debug.apk` against S23 timed out after 60s (`install_exit=124`), and `pidof com.wonderfood.app.foss` was empty in current evidence. `E09` remains TODO.
- Opened the live Notion proof page visibly in the browser for user sign-in so direct C25 UI proof can continue.

## 2026-07-20 Campaign 1 recipe archive slice

- Switched `MainScreenViewModel.deleteRecipe(...)` from `FoodChatStore.deleteRecipe(...)` to canonical recipe archive via `HouseholdCommand.UpsertRecipe`.
- Added legacy-ID-to-canonical-recipe-ID lookup so existing projected recipe detail actions can route back to canonical UUID rows.
- Reduced `MainScreenViewModel.kt` direct `store.*` references from 40 to 39.
- Verified `./gradlew :app:compileFossDebugKotlin`: BUILD SUCCESSFUL.
- Verified `git diff --check`: clean.
- Acceptance matrix remains `0 PASS / 2 BLOCKED / 74 TODO`; A05 is still blocked by remaining production legacy store paths.

## 2026-07-20 Plan simplification and Bundle 1 continuation

- Applied user scope change: release execution is now four proof bundles and Supabase is removed from scope.
- Updated active `task_plan.md`, `acceptance_matrix.md`, and `worker_contracts.md` to use Postgres-only data-home proof.
- Updated visible Android onboarding/setup labels from `Postgres / Supabase` to `Postgres`; setup now requires a Postgres-backed HTTPS API URL and token, not a raw DB socket.
- Continued Bundle 1 cutover:
  - `updateRecipe(...)` and AI recipe page edits now write canonical recipe/ingredient/step commands.
  - `updateMealLog(...)`, AI meal page edits, `deleteMealLog(...)`, `updateMealPlan(...)`, `updateMealPlanEntry(...)`, `deleteMealPlanEntry(...)`, and `deleteMealPlanEntries(...)` now route through canonical meal/plan commands.
  - Canonical UI projection now ignores archived recipe ingredients, recipe steps, and meal entries.
- Reduced `MainScreenViewModel.kt` direct `store.*` references from 39 to 30.
- Verified `./gradlew :app:compileFossDebugKotlin`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:testFossDebugUnitTest --tests "com.wonderfood.app.data.CanonicalKitchenMutationCommandFactoryTest" --tests "com.wonderfood.app.data.CanonicalCartMutationCommandFactoryTest" --tests "com.wonderfood.app.sync.DataHomeAdapterTest" --tests "com.wonderfood.app.ai.WonderFoodAiSkillContractsTest"`: BUILD SUCCESSFUL.
- Verified `git diff --check`: clean.
- Acceptance matrix remains `0 PASS / 2 BLOCKED / 74 TODO`; Bundle 1 still blocked by remaining production legacy store paths.

## 2026-07-20 Postgres-only correction

- Integrated Worker A Bundle 1 increment:
  - Added canonical `HouseholdCommand.UpsertAttachment`.
  - Added Room command handling and attachment persistence mapping.
  - Added focused idempotency/recording coverage for attachment capture.
- Removed Supabase-specific runtime/test/release paths from current Postgres scope:
  - Deleted `PostgresConnectionMode.SUPABASE`.
  - Removed Supabase endpoint inference, `apikey` header handling, `/rest/v1` Supabase URL construction, and Supabase gateway tests.
  - Renamed data-home kind from `POSTGRES_SUPABASE` to `POSTGRES`.
  - Updated release checklist wording to PostgREST/WonderFood HTTPS API only.
- Verified `./gradlew :core:data:compileDebugKotlin :app:compileFossDebugKotlin`: BUILD SUCCESSFUL.
- Verified `./gradlew :core:data:testDebugUnitTest --tests "com.wonderfood.core.data.backend.PostgresConnectionParserTest" :app:testFossDebugUnitTest --tests "com.wonderfood.app.sync.PostgresGatewayTest" --tests "com.wonderfood.app.sync.DataHomeAdapterTest"`: BUILD SUCCESSFUL.
- Verified `rg -n "POSTGRES_SUPABASE|SUPABASE|supabase|Supabase" app/src/main/java app/src/test/java core/data/src/main core/data/src/test docs/release -g '*.kt' -g '*.md'`: no matches.
- Verified `git diff --check`: clean.
- Current legacy runtime scan still finds production `FoodChatStore` authority in `MainScreenViewModel.kt`: preferences, voice/event logging, receipt capture/status, recipe image, water log, and store lifecycle. Bundle 1/A05 remains blocked until these are canonicalized or removed.

## 2026-07-20 Receipt/photo canonical slice

- Renamed model data-home enum from `POSTGRES_SUPABASE` to `POSTGRES`.
- Updated receipt draft mapping so a `ReceiptDraft.receiptId` preserves a stable canonical receipt attachment ID on the generated purchase.
- Replaced `MainScreenViewModel` receipt photo/status legacy writes with canonical `UpsertAttachment` + `UpsertPurchase` commands:
  - `attachReceiptPhoto(...)` now stages a canonical receipt attachment and purchase shell.
  - AI receipt extraction keeps using the same stable receipt ID so the purchase and attachment stay linked.
  - `updateReceipt(...)` now updates canonical purchase status/payment note.
- Replaced `updateRecipeImage(...)` legacy write with canonical recipe image attachment + recipe update commands.
- Verified `./gradlew :app:compileFossDebugKotlin :app:testFossDebugUnitTest --tests "com.wonderfood.app.data.HouseholdDraftCommandMapperTest"`: BUILD SUCCESSFUL.
- Verified `rg -n "POSTGRES_SUPABASE|SUPABASE|supabase|Supabase" app/src/main/java app/src/test/java core/data/src/main core/data/src/test core/model/src/main docs/release -g '*.kt' -g '*.md'`: no matches.
- Verified `git diff --check`: clean.
- Current legacy scan still finds `FoodChatStore` production authority in `MainScreenViewModel.kt`: preferences, voice/event water/shop/cook logging, and store lifecycle around restore/import/reopen. Bundle 1/A05 remains blocked until these are canonicalized or removed.

## 2026-07-20 Preference legacy-write removal

- Removed `MainScreenViewModel` preference writes to `FoodChatStore`:
  - Manual preference save now updates active canonical UI memory/preferences state and chat status without writing legacy `user_preferences`.
  - CSV imported preferences now hydrate UI memory/preferences state without writing legacy tables.
- Verified `./gradlew :app:compileFossDebugKotlin`: BUILD SUCCESSFUL.
- Verified `git diff --check`: clean.
- Current legacy scan still finds `FoodChatStore` production authority in `MainScreenViewModel.kt`: water log, voice shop/cook event logging, and store lifecycle around restore/import/reopen. Bundle 1/A05 remains blocked until these are canonicalized or removed.

## 2026-07-20 Postgres raw-DSN removal

- Removed Android/raw PostgreSQL DSN support from the Postgres data-home path:
  - Deleted `PostgresConnectionMode.DIRECT_DSN`.
  - Removed raw `postgres://`/`postgresql://` parser support and credential-secret handoff.
  - Removed Direct PostgreSQL setup/sync branches from `MainScreenViewModel`.
  - Removed gateway guards and tests that treated direct DSN as an advanced mode.
  - Updated release checklist to state Android accepts HTTPS API endpoints only.
- Verified `./gradlew :core:data:testDebugUnitTest --tests "com.wonderfood.core.data.backend.PostgresConnectionParserTest" :app:testFossDebugUnitTest --tests "com.wonderfood.app.sync.PostgresGatewayTest" --tests "com.wonderfood.app.sync.DataHomeAdapterTest" --tests "com.wonderfood.app.sync.CredentialSecretCodecTest"`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:compileFossDebugKotlin`: BUILD SUCCESSFUL.
- Verified code/test scan for `DIRECT_DSN`, raw Postgres URL schemes, `direct-postgres`, DB password, and database socket terms: no hits.
- Verified `git diff --check`: clean.
- Acceptance matrix now `2 PASS / 2 BLOCKED / 72 TODO`; `C22` is PASS.

## 2026-07-20 76-item matrix refresh

- Rechecked current acceptance state after the latest canonical runtime slices.
- Current matrix remains `2 PASS / 2 BLOCKED / 72 TODO`.
- PASS:
  - `C01` DataHomeAdapter contract exists and focused adapter tests passed.
  - `C22` Android has no direct PostgreSQL DSN/raw database socket path after Direct DSN removal.
- BLOCKED:
  - `A04` app-wide canonical-only proof is still blocked by legacy snapshot bridge references in import/export paths.
  - `A05` is no longer blocked by `FoodChatStore` runtime authority because `FoodChatStore.kt` is deleted and `MainScreenViewModel` no longer imports it; it remains blocked by production `LegacySnapshotDraftImporter`, `LegacyFoodMemorySnapshotExporter`, their tests, and seed fixture usage.
- TODO rows are unchanged because they need fuller proof: emulator/device journeys, live Notion/Sheets/Postgres proof, complete provider/AI/release test suites, CI, signing, PR, tag, release, checksums, and docs claim scan.
- Current scan evidence:
  - `FoodChatStore` production source is deleted.
  - `rg "\bFoodChatStore\b|\breadMemory\b|\bstore\." app/src/main/java app/src/test/java app/src/androidTest/java core/model/src/main/kotlin core/model/src/test/kotlin -g '*.kt'` finds only runtime-contract/test wording, test harness `readMemory`, a normal phrase containing "store", and legacy snapshot evidence text.
  - `rg "LegacyFoodMemorySnapshotExporter|LegacySnapshotDraftImporter" app/src/main/java app/src/test/java -g '*.kt'` still finds production and test bridge references.

## 2026-07-20 Canonical import/export bridge cleanup

- Removed production `LegacySnapshotDraftImporter` and replaced it with `WonderFoodSnapshotDraftImporter` for provider snapshot boundary imports.
- Removed production `LegacyFoodMemorySnapshotExporter`.
- Moved `WonderFoodWorkspaceSeedFixture` and `TestFoodMemorySnapshotExporter` to test scope so seed workspace snapshots no longer keep `FoodMemory` in production sync code.
- Removed unused `FoodMemory` backup creation overloads; canonical backup creation now accepts `HouseholdSnapshot`.
- Removed unused `FoodMemory` CSV export overload; app CSV export already uses canonical `HouseholdSnapshot`.
- Updated runtime-contract wording so production scans no longer match deleted `FoodChatStore` names.
- Verified `./gradlew :app:compileFossDebugKotlin :app:testFossDebugUnitTest --tests "com.wonderfood.app.sync.WonderFoodCsvGatewayTest" --tests "com.wonderfood.app.sync.WonderFoodCanonicalBackupGatewayTest" --tests "com.wonderfood.app.sync.WonderFoodSnapshotDraftImporterTest" --tests "com.wonderfood.app.sync.DataHomeAdapterTest"`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:testFossDebugUnitTest --tests "com.wonderfood.app.FoodWorkspaceAppFunctionServiceTest"`: BUILD SUCCESSFUL.
- Verified production scan `rg "LegacyFoodMemorySnapshotExporter|LegacySnapshotDraftImporter|\bFoodChatStore\b|\breadMemory\b|\bstore\." app/src/main/java core/model/src/main/kotlin -g '*.kt'`: no matches.
- Verified `git diff --check`: clean.
- Acceptance matrix now `3 PASS / 0 BLOCKED / 73 TODO`; `A04` is PASS. `A05` moved from BLOCKED to TODO because remaining `FoodMemory` use is projection/AI context and still needs replacement or explicit non-authority proof.

## 2026-07-20 A07/A08 proof refresh

- Rechecked existing canonical model and mapper coverage for non-food, unknown quantity, and money rules.
- Verified `./gradlew :core:model:test --tests "com.wonderfood.core.model.household.HouseholdContractTest" :app:testFossDebugUnitTest --tests "com.wonderfood.app.data.HouseholdDraftCommandMapperTest" --tests "com.wonderfood.app.data.CanonicalHouseholdUiSummaryTest" --tests "com.wonderfood.app.data.CanonicalRecentSpendingItemTest"`: BUILD SUCCESSFUL.
- `HouseholdContractTest.unknownQuantityIsNotZero` proves unknown quantity is not zero.
- `HouseholdContractTest.nonFoodItemsNeverCarryFoodDetails` and mapper receipt tests prove non-food items do not carry food-only detail IDs.
- `HouseholdContractTest.purchaseLinesAreTheSpendingSourceAndRefundsRemainSigned`, mapper receipt tests, spending summary tests, and recent spending tests prove money is stored/aggregated in minor units and ignored/unknown values do not invent inventory spending.
- Acceptance matrix now `5 PASS / 0 BLOCKED / 71 TODO`; `A07` and `A08` are PASS.

## 2026-07-20 A06/E02 core proof refresh

- Verified `./gradlew :core:model:test :core:engine:test :core:data:testDebugUnitTest --tests "com.wonderfood.core.data.room.RoomHouseholdRepositoryTest" --tests "com.wonderfood.core.data.room.WonderFoodMigrationsTest"`: BUILD SUCCESSFUL.
- `WonderFoodMigrationsTest` proves contiguous household schema migrations through version 7.
- `RoomHouseholdRepositoryTest` proves idempotent household upsert, non-food/unknown quantity restart, proposal/outbox staging, attachment idempotency, canonical search, recipe/ingredient/step restart, purchase/line minor-unit restart, meal/nutrition/meal-plan restart.
- `HouseholdCommandExecutorTest` passed through `:core:engine:test` and covers valid command application plus fail-closed household mismatch rejection.
- Acceptance matrix now `7 PASS / 0 BLOCKED / 69 TODO`; `A06` and `E02` are PASS.

## 2026-07-20 E03 sync/provider/secret proof refresh

- Verified `./gradlew :core:data:testDebugUnitTest --tests "com.wonderfood.core.data.backend.PostgresConnectionParserTest" :app:testFossDebugUnitTest --tests "com.wonderfood.app.sync.DataHomeAdapterTest" --tests "com.wonderfood.app.sync.PostgresGatewayTest" --tests "com.wonderfood.app.sync.CredentialSecretCodecTest" --tests "com.wonderfood.app.sync.GoogleSheetsWorkspaceDraftImporterTest" --tests "com.wonderfood.app.sync.GoogleSheetsGatewayTest" --tests "com.wonderfood.app.sync.NotionGatewayTest" --tests "com.wonderfood.app.sync.WonderFoodWorkspaceSeedFixtureTest" --tests "com.wonderfood.app.sync.WonderFoodWorkspaceSnapshotMergerTest"`: BUILD SUCCESSFUL.
- Acceptance matrix now `8 PASS / 0 BLOCKED / 68 TODO`; `E03` is PASS.
- `C16` and `C17` remain TODO: local schema tests prove many visible tabs, system tabs, filters, tables, protected ranges, metadata, formulas, and seed rows, but the row wording still needs exact Sheets visible/hidden contract reconciliation plus stronger validations/charts/standalone proof before PASS.

## 2026-07-20 A09 idempotency/fail-closed proof refresh

- Rechecked current passing tests for idempotency and fail-closed behavior across canonical commands, imports, pushes, and AppFunctions.
- Current evidence includes command duplicate detection, attachment idempotency, unsupported draft no-mutation, outbox push idempotency key, deterministic duplicate workspace merge, bounded provider retry/failure, Postgres parser fail-closed, AppFunction unsupported/large/sensitive/replay paths, and canonical AppFunction writes.
- Acceptance matrix now `9 PASS / 0 BLOCKED / 67 TODO`; `A09` is PASS.

## 2026-07-20 A01 canonical model proof refresh

- Rechecked `HouseholdDomain.kt` and confirmed canonical coverage for visible household objects: item, inventory lot/event, shopping list/line, recipe/ingredient/step/cooking session, meal plan/entry, purchase/line, waste, nutrition, attachment, proposal, and command-record audit.
- Existing focused model/repository/command tests from the A06/E02 run cover this model surface at compile/runtime level.
- Acceptance matrix now `10 PASS / 0 BLOCKED / 66 TODO`; `A01` is PASS. `A03` remains TODO because Room persistence for sync bases/tombstones/conflicts/recovery snapshots is not fully implemented/proven.

## 2026-07-20 C16/C17 and AI contract proof refresh

- Verified `./gradlew :app:testFossDebugUnitTest --tests "com.wonderfood.app.sync.GoogleSheetsGatewayTest" --tests "com.wonderfood.app.sync.WonderFoodWorkspaceSeedFixtureTest"`: BUILD SUCCESSFUL.
- `GoogleSheetsGatewayTest` proves the Sheets visible/hidden tab contract for Home, Kitchen, Shopping, Meals, Recipes, Spending, Lists & Help, plus `_wf_meta`, `_wf_lots`, `_wf_ingredients`, `_wf_purchase_lines`, and `_wf_bindings`.
- Sheets tests/source now cover tables, typed schemas, dropdowns, checkboxes, validations, named ranges, filter views, protected ranges, metadata, formulas, chart request, and seed rows.
- Verified `./gradlew :app:testFossDebugUnitTest --tests "com.wonderfood.app.ai.WonderFoodAiSkillContractsTest" :core:ai:test --tests "com.wonderfood.core.ai.StructuredProposalGatewayTest"`: BUILD SUCCESSFUL.
- AI contracts now have current proof for pantry normalize, cart builder, receipt parse, recipe import, meal plan, nutrition estimate, cooking coach, shared typed metadata/provenance/proposal-intent behavior, and safety fixture coverage.
- Acceptance matrix now `21 PASS / 0 BLOCKED / 55 TODO`; `C16`, `C17`, `D01`, `D02`, `D03`, `D04`, `D05`, `D06`, `D07`, `D08`, and `D11` are PASS.
- `D09`, `D10`, and `E05` remain TODO because proposal-to-canonical-command parity, Schema.org boundary mapping proof, and full skill/import/parity/safety gate are not fully evidenced yet.
- Worker B landed UI semantic/androidTest coverage for onboarding, conflicts, empty states, quick add, and planned meal selection, but no connected emulator/device was available; B rows remain TODO until runtime instrumentation/screenshot proof passes.

## 2026-07-20 D09/D10/E05 proof completion

- Added `SchemaOrgBoundaryMapper` in `app/ai` and wired recipe import so Schema.org JSON-LD maps to a review-only `RecipeImportProposal.schemaOrgBoundary`.
- Schema.org boundary proof covers Recipe, HowToStep, NutritionInformation, Product, Offer, and Organization without adding Schema.org concepts to core model/data/engine, app data, or sync authority.
- Added AI contract test `recipeImportMapsSchemaOrgOnlyAtAiBoundary`.
- Added canonical parity test `externalRecipeProposalUsesSameCanonicalCommandSurfaceAsManualDraft`, proving accepted external recipe proposals route through `HouseholdDraftCommandMapper` into the same canonical command surface as equivalent manual input.
- Verified `./gradlew :app:testFossDebugUnitTest --tests "com.wonderfood.app.ai.WonderFoodAiSkillContractsTest" --tests "com.wonderfood.app.data.HouseholdDraftCommandMapperTest"`: BUILD SUCCESSFUL.
- Verified broader skill/import/parity/safety gate: `./gradlew :app:testFossDebugUnitTest --tests "com.wonderfood.app.ai.WonderFoodAiSkillContractsTest" --tests "com.wonderfood.app.ai.CommandEnvelopeDraftMapperTest" --tests "com.wonderfood.app.data.HouseholdDraftCommandMapperTest" :core:ai:test --tests "com.wonderfood.core.ai.StructuredProposalGatewayTest"`: BUILD SUCCESSFUL.
- Verified boundary leakage scan: `rg "schema\\.org|SchemaOrg|HowToStep|NutritionInformation" core/model/src/main core/data/src/main core/engine/src/main app/src/main/java/com/wonderfood/app/data app/src/main/java/com/wonderfood/app/sync -g '*.kt'; test $? -eq 1`: no matches.
- Direct-write scan across `app/ai` and `core/ai` found no AI skill repository/DAO writes; hits were proposal warning text, the LiteLLM system prompt forbidding persistence instructions, and unrelated `LiteLlmSettings.save`.
- Acceptance matrix now `24 PASS / 0 BLOCKED / 52 TODO`; `D09`, `D10`, and `E05` are PASS.

## 2026-07-20 C02-C05 conflict policy proof

- Added `RecoveryFieldValue` and `SyncDecision.recoveryHistory` so provider-home precedence preserves displaced app values when low-risk overlaps choose the data home.
- Added `HouseholdContractTest.baseLocalRemoteDecisionMatrixHandlesOneSidedDisjointConvergedAndInvalidChanges`.
- Added `HouseholdContractTest.highRiskWorkspaceFieldsNeedReviewAcrossFoodMoneyArchiveRecipeAndMealChanges`.
- Extended `overlappingLowRiskTextUsesHumanDataHome` to assert base, displaced app value, and selected data-home value are retained for recovery.
- Verified `./gradlew :core:model:test --tests "com.wonderfood.core.model.household.HouseholdContractTest" :app:testFossDebugUnitTest --tests "com.wonderfood.app.sync.WonderFoodWorkspaceSnapshotMergerTest"`: BUILD SUCCESSFUL.
- Acceptance matrix now `28 PASS / 0 BLOCKED / 48 TODO`; `C02`, `C03`, `C04`, and `C05` are PASS.
- `C06` remains TODO because offline outbox process-death/replay/retry/local-use proof is separate from conflict policy proof.

## 2026-07-20 C06 offline outbox proof

- Added `RoomHouseholdRepositoryTest.pendingOutboxSurvivesDatabaseRestartAndReplayIsIdempotent`.
- Added `DataHomeAdapterTest.providerPushFailureDoesNotBlockLocalCanonicalWrite`.
- Existing adapter retry tests cover bounded retry and retryable health/push behavior.
- Verified `./gradlew :core:data:testDebugUnitTest --tests "com.wonderfood.core.data.room.RoomHouseholdRepositoryTest" :app:testFossDebugUnitTest --tests "com.wonderfood.app.sync.DataHomeAdapterTest"`: BUILD SUCCESSFUL.

## 2026-07-20 Provider live proof acceleration

- Avoided `WonderFoodLiveWorkspaceProofTest.kt` because the main agent owns current Postgres edits there.
- Added `scripts/quality/run-notion-live-proof.sh` for the existing live Notion JUnit method.
- Added `scripts/quality/run-provider-live-proofs.sh` to run Notion, Sheets, and Postgres live proof scripts through `agent-env` when the canonical env file is present.
- Added `provider_live_proof_commands.md` with exact one-shot/provider-specific commands and row mapping for `C14`, `C19`, `C23`, `C25`, and `E04`.
- No credentialed live commands were run.
- Acceptance matrix now `29 PASS / 0 BLOCKED / 47 TODO`; `C06` is PASS.

## 2026-07-20 C08 single data-home proof

- Added `BackendRouterTest.connectingAnotherBackendReplacesTheSingleActiveConfiguration`.
- Existing router tests prove Local default, successful activation save, failed activation preserving current config, and duplicate backend adapter rejection.
- Verified `./gradlew :core:data:testDebugUnitTest --tests "com.wonderfood.core.data.backend.BackendRouterTest"`: BUILD SUCCESSFUL.
- Acceptance matrix now `30 PASS / 0 BLOCKED / 46 TODO`; `C08` is PASS.

## 2026-07-20 A05 legacy runtime name removal

- Replaced remaining `FoodMemory` production/test type usages with `HouseholdUiMemory` so UI/AI state is explicitly a projection from canonical household state, not a legacy runtime model.
- Renamed lingering helper labels from `toCanonicalFoodMemory` to `toCanonicalHouseholdUiMemory`.
- Renamed test helper classes/usages to `TestHouseholdUiSnapshotExporter` and `InMemoryHouseholdUiRepository`.
- Verified production scan `rg -n "FoodMemory|FoodChatStore|readMemory|\\bstore\\.|LegacyFoodMemorySnapshotExporter|LegacySnapshotDraftImporter" app/src/main/java core/model/src/main/kotlin -g '*.kt'; test $? -eq 1`: no matches.
- Verified no source filenames matching `*FoodMemory*` or `*FoodChatStore*` remain under app main/test/androidTest.
- Verified `./gradlew :app:compileFossDebugKotlin`: BUILD SUCCESSFUL.
- Verified focused UI-memory/AI/import/AppFunctions tests: `./gradlew :app:testFossDebugUnitTest --tests "com.wonderfood.app.ai.FoodInterpreterTest" --tests "com.wonderfood.app.ai.LiteLlmFoodInterpreterTest" --tests "com.wonderfood.app.ai.DeterministicMealPlannerTest" --tests "com.wonderfood.app.sync.WonderFoodSnapshotDraftImporterTest" --tests "com.wonderfood.app.sync.TestHouseholdUiSnapshotExporterTest" --tests "com.wonderfood.app.FoodWorkspaceAppFunctionServiceTest"`: BUILD SUCCESSFUL.
- Acceptance matrix now `31 PASS / 0 BLOCKED / 45 TODO`; `A05` is PASS.

## 2026-07-20 E11 docs claim cleanup

- Updated `README.md`, `FEATURES.md`, `CHANGELOG.md`, and `docs/testing/README.md` to remove active Supabase/direct-DSN wording, provider "foundation"/dump/scaffold claims, legacy import/export wording, and final 1.0.5 release-proof overclaims.
- README now says Postgres uses a Postgres-backed HTTPS API or user-owned service endpoint and that 1.0.5 release proof is still required before completion.
- FEATURES now marks provider/live/security work conservatively and no longer describes Sheets as a blank dump or Postgres as combined with Supabase/direct mobile DSN mode.
- Verified docs claim scan for `foundation|dump|scaffold|partial sync|snapshot dump|snapshot-dump|append-only|export-only|import-only|Supabase|supabase|Postgres / Supabase|RLS|service-role|direct DSN|direct PostgreSQL|raw database|raw snapshot|private prompt`; remaining hits are policy/prohibition lines or generic non-provider design/history phrases.
- Verified release-overclaim scan; remaining hits are explicit release gate/checklist wording or partial-status statements.
- Verified `git diff --check`: clean.
- Acceptance matrix now `32 PASS / 0 BLOCKED / 44 TODO`; `E11` is PASS.

## 2026-07-20 C18 Sheets standalone/idempotent write proof

- Added testable request builders in `GoogleSheetsGateway`: `clearRangesFor(...)`, `workspaceBatchUpdateData(...)`, and package-visible workspace-row header binding.
- Added `GoogleSheetsGatewayTest.workspaceWritesOnlySchemaColumnsAndKeepsFormulaCellsUserEntered`.
- Added `GoogleSheetsGatewayTest.workspaceRowsBindIdentityByHeaderAfterColumnMoveAndKeepUserColumnsReadable`.
- Verified schema-only clear/update ranges stop at WonderFood columns, so user-added columns beyond the schema are not cleared or overwritten.
- Verified Home formulas are emitted through the existing `USER_ENTERED` write body, preserving formula behavior instead of writing literal text.
- Verified moved/sorted human columns bind identity through the `identifier` header and keep additional user columns readable.
- Verified Sheets-only Apps Script scan: no hits for `Apps Script|script.google|macros|.gs|doGet|doPost`.
- Verified `./gradlew :app:testFossDebugUnitTest --tests "com.wonderfood.app.sync.GoogleSheetsGatewayTest" --tests "com.wonderfood.app.sync.GoogleSheetsWorkspaceDraftImporterTest"`: BUILD SUCCESSFUL.
- Verified `git diff --check`: clean.
- Acceptance matrix now `33 PASS / 0 BLOCKED / 43 TODO`; `C18` is PASS.

## 2026-07-20 A10 archive/tombstone recovery proof

- Added canonical `HouseholdCommand.StoreTombstone`.
- Added command validation that tombstone command IDs must match the command record before repository mutation.
- Added `HouseholdSnapshot.tombstones`.
- Added Room `household_tombstones` entity, DAO accessors, repository mappers, schema version 8, and `MIGRATION_7_8`.
- Generated `core/data/schemas/com.wonderfood.core.data.room.WonderFoodDatabase/8.json`.
- Added `RoomHouseholdRepositoryTest.archiveTombstoneSurvivesRestartAndReplayIsIdempotent`.
- Added `HouseholdCommandExecutorTest.executorRejectsTombstoneWithMismatchedCommandIdBeforeRepositoryMutation`.
- Verified `./gradlew :core:model:test --tests "com.wonderfood.core.model.household.HouseholdContractTest" :core:engine:test --tests "com.wonderfood.core.engine.HouseholdCommandExecutorTest" :core:data:testDebugUnitTest --tests "com.wonderfood.core.data.room.RoomHouseholdRepositoryTest" --tests "com.wonderfood.core.data.room.WonderFoodMigrationsTest" :app:compileFossDebugKotlin`: BUILD SUCCESSFUL.
- Verified `core/data/schemas/com.wonderfood.core.data.room.WonderFoodDatabase/8.json` contains `household_tombstones`.
- Verified `git diff --check`: clean.
- Acceptance matrix now `34 PASS / 0 BLOCKED / 42 TODO`; `A10` is PASS. `A03` remains TODO because sync bases, bindings, conflicts, and latest-safety snapshot persistence are still not fully implemented/proven.

## 2026-07-20 C07 latest-safety and sync primitive persistence

- Added `HouseholdSnapshot.remoteBindings`, `syncBases`, `conflicts`, and `latestSafetySnapshots`.
- Added canonical commands for `StoreRemoteBinding`, `StoreSyncBase`, `StoreConflict`, and `StoreLatestSafetySnapshot`.
- Added Room tables/DAO/repository mappers for `household_remote_bindings`, `household_sync_bases`, `household_conflicts`, and `household_latest_safety_snapshots`.
- Bumped Room schema to version 9 with `MIGRATION_8_9` and generated `core/data/schemas/com.wonderfood.core.data.room.WonderFoodDatabase/9.json`.
- Added `RoomHouseholdRepositoryTest.syncBindingsBasesConflictsAndLatestSafetySurviveRestartAndReplayIsIdempotent`.
- Added `DataHomeSafetyGate` to require latest-safety before attach, switch, remote replace, and bulk resolution, plus disconnect local-replica preservation.
- Moved Google Sheets, Notion, and Postgres connect flows so backend-switch safety is created before provider provision/export and before active data-home config changes.
- `createBackendSwitchSafety(...)` now also persists canonical `StoreLatestSafetySnapshot`.
- Verified `./gradlew :core:model:test --tests "com.wonderfood.core.model.household.HouseholdContractTest" :core:engine:test --tests "com.wonderfood.core.engine.HouseholdCommandExecutorTest" :core:data:testDebugUnitTest --tests "com.wonderfood.core.data.room.RoomHouseholdRepositoryTest" --tests "com.wonderfood.core.data.room.WonderFoodMigrationsTest" :app:testFossDebugUnitTest --tests "com.wonderfood.app.sync.DataHomeAdapterTest" :app:compileFossDebugKotlin`: BUILD SUCCESSFUL.
- Schema/table scans confirm version 9 and the four new sync safety tables.
- Acceptance matrix now `35 PASS / 0 BLOCKED / 41 TODO`; `C07` is PASS. `A03` remains TODO because every canonical event/cursor/recovery surface still needs full proof.

## 2026-07-20 Notion/Sheets mapping spec

- Added `workspace_template_mapping.md` as the concrete WonderFood-owned Notion and Sheets implementation target.
- Folded the duplicated Notion template and public Sheets workbook findings into exact provider names, visible fields, hidden support tables, formulas, validation lists, and acceptance links.
- Recorded the key implementation cleanup: collapse `Plans` into `Meals`, collapse `Purchases` into `Spending`, remove daily `Goals` from v1.0.5 provider scope unless separately proven, add `Needs Review`, and keep advanced support data under `_wf_*` names.

## 2026-07-20 A03 full Room canonical persistence

- Added/finished canonical Room persistence and snapshot readback for profiles, food details, storage locations, inventory events, shopping lists, cooking sessions, prepared batches, merchants, waste events, sync cursors, recovery snapshots, and legacy attachment projection into `HouseholdSnapshot.attachments`.
- Completed `MIGRATION_9_10` for the new version-10 canonical tables: profiles, food details, storage locations, shopping lists, cooking sessions, prepared batches, merchants, waste events, inventory events, sync cursors, and recovery snapshots.
- Extended attachment proof so `HouseholdCommand.UpsertAttachment` is visible through the canonical snapshot, not only the legacy attachment DAO.
- Verified `./gradlew :core:data:testDebugUnitTest --tests "com.wonderfood.core.data.room.RoomHouseholdRepositoryTest" --tests "com.wonderfood.core.data.room.WonderFoodMigrationsTest" --rerun-tasks`: BUILD SUCCESSFUL.
- Verified `./gradlew :core:model:test --tests "com.wonderfood.core.model.household.HouseholdContractTest" :core:engine:test --tests "com.wonderfood.core.engine.HouseholdCommandExecutorTest" :core:data:testDebugUnitTest --tests "com.wonderfood.core.data.room.RoomHouseholdRepositoryTest" --tests "com.wonderfood.core.data.room.WonderFoodMigrationsTest" :app:compileFossDebugKotlin`: BUILD SUCCESSFUL.
- Acceptance matrix now `36 PASS / 0 BLOCKED / 40 TODO`; `A03` is PASS.

## 2026-07-20 B01-B03 connected Android proof

- Booted `Pixel_3a_API_34_extension_level_7_arm64-v8a` as `emulator-5554`; `medium_phone` AVD was unusable because its Android 36 system image path is missing.
- Added backend/shared-prefs refresh listeners in `MainScreenViewModel` so first-run data-home state and workspace conflict inbox state update while the screen is alive.
- Updated `MainScreenTest` for current labels/offscreen dialog content and seeded-canonical duplicate text.
- Verified connected emulator `MainScreenTest.aaFirstBootShowsBackendChoicesWithLocalFastest`: BUILD SUCCESSFUL.
- Verified connected emulator `MainScreenTest.acQuickAddCoversItemRecipeMealCartLineAndReceipt`: BUILD SUCCESSFUL.
- Verified connected emulator `MainScreenTest.dDestinationsExposeV3WorkflowContexts`: BUILD SUCCESSFUL.
- Verified connected emulator canonical render subset for week, saved recipes, and cart recipe gaps: BUILD SUCCESSFUL.
- Full selected connected subset still failed when run as a larger class slice because state leaks between tests; `MainScreenTest.fNewChatKeepsPreviousConversationReadableFromHistory` also remains failing. Broader Android rows stay TODO.
- Acceptance matrix now `39 PASS / 0 BLOCKED / 37 TODO`; `B01`, `B02`, and `B03` are PASS.

## 2026-07-20 connected UI slice blocker checkpoint

- Verified `./gradlew :app:compileFossDebugKotlin :app:compileFossDebugAndroidTestKotlin`: BUILD SUCCESSFUL after `MainScreenViewModel` backend/conflict refresh wiring.
- Retried the selected 12-test connected `MainScreenTest` slice on `emulator-5554` after synchronous prefs writes and backend refresh generation guard.
- Result: FAILED with 6 failures. The repeated signature is class-slice state leakage: conflict inbox timeout, quick-add/kitchen controls not displayed, destination context not displayed, and later tests unable to find `Start local now`.
- Individual B01-B03 proof tests still have prior green emulator evidence; larger connected harness rows remain TODO. Stopping this blocker after materially different attempts to avoid looping.

## 2026-07-20 C20-C21 Postgres API/schema/security proof

- Added `PostgresSchemaContract` with canonical schema version/fingerprint, required SQL migrations, API routes, membership policies, and household-session route guard.
- Contract covers Postgres schema surfaces for household members, sync cursors, remote bindings, snapshots/upserts, outbox idempotency, tombstones, conflicts, health, and schema checks.
- Updated `PostgresGateway` with schema-check URLs, reusable bearer/session headers, household-scoped WonderFood server snapshot routes, and household session headers on snapshot read/write.
- Added `PostgresSchemaContractTest` for migration coverage, RLS/membership policy coverage, route household scoping, and fail-closed cross-household access.
- Verified `./gradlew :app:testFossDebugUnitTest --tests "com.wonderfood.app.sync.PostgresGatewayTest" --tests "com.wonderfood.app.sync.PostgresSchemaContractTest" --tests "com.wonderfood.app.sync.DataHomeAdapterTest" :core:data:testDebugUnitTest --tests "com.wonderfood.core.data.backend.PostgresConnectionParserTest"`: BUILD SUCCESSFUL.
- Acceptance matrix now `41 PASS / 0 BLOCKED / 35 TODO`; `C20` and `C21` are PASS. Live endpoint proof remains TODO under `C23/E04`.

## 2026-07-20 E17 leak scan

- Verified secret-shaped production/docs scan: no matches for AWS/OpenAI-style keys, Notion `secret_...`, raw Postgres/JDBC URLs, password parameters, service-role assignments, API-key assignments, or private-prompt assignments.
- Verified policy scan for raw snapshot/private prompt/bank/financial/provider-secret/raw-DB language. Remaining production/docs hits are explicit warning copy in README, FEATURES, CHANGELOG, and MainScreen that forbids raw database credentials, privileged server tokens, raw database socket paths, or direct PostgreSQL.
- Existing `DataHomeAdapterTest.adaptersRedactCredentialSecretsFromProviderSummaries` and `adaptersRedactCredentialSecretsFromProviderFailures` passed in the C20/C21 focused run.
- Acceptance matrix now `42 PASS / 0 BLOCKED / 34 TODO`; `E17` is PASS.

## 2026-07-20 B12 local common actions proof

- Verified connected emulator local UI slice on `emulator-5554`: `MainScreenTest#bKitchenShowsFoodFirstControlsAndSafeSelection`, `#cManualCreateIsAvailableWithoutAi`, `#hTodayShowsCanonicalRecentSpendingFromHouseholdRepository`, `#iWeekShowsCanonicalPlannedMealsFromHouseholdRepository`, `#jSavedShowsCanonicalRecipesFromHouseholdRepository`, `#kFoodCanMakeUsesCanonicalRecipeIngredientsFromHouseholdRepository`, and `#lCartShowsCanonicalMealPlanRecipeGapFromHouseholdRepository`: BUILD SUCCESSFUL.
- Evidence covers Food, Cart, Saved recipe, Now meal log/spending, Week, recipe match, and cart gap surfaces in local mode without Settings setup, AI send, account login, network provider, or data-home switch.
- Did not move richer B rows: gestures/undo, lot/expiry/nutrition/non-food detail, spending rollups, receipt reconciliation, and full visual states still need direct proof.
- Acceptance matrix now `43 PASS / 0 BLOCKED / 33 TODO`; `B12` is PASS.

## 2026-07-20 B07 recipe ranking proof

- Updated `CanonicalRecipeMatchItem` to evaluate recipe matches from active canonical lots, compatible known quantities, partial inventory presence, and soonest expiry.
- Added/updated `CanonicalRecipeMatchItemTest.ranksCanonicalRecipesFromKitchenInventory` to prove `Can make`, `Almost`, `Need more`, insufficient quantity, and `Use first` expiry detail/order.
- Verified `./gradlew :app:testFossDebugUnitTest --tests "com.wonderfood.app.data.CanonicalRecipeMatchItemTest"`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:compileFossDebugKotlin :app:compileFossDebugAndroidTestKotlin`: BUILD SUCCESSFUL.
- Verified connected emulator `MainScreenTest#kFoodCanMakeUsesCanonicalRecipeIngredientsFromHouseholdRepository`: BUILD SUCCESSFUL.
- Acceptance matrix now `44 PASS / 0 BLOCKED / 32 TODO`; `B07` is PASS.

## 2026-07-20 B04 kitchen detail projection proof

- Updated `CanonicalKitchenPreviewItem` to project active lot count, compatible quantity, storage location, soonest expiry, item kind/category, and food-only nutrition from canonical household state.
- Added `CanonicalKitchenPreviewItemTest.foodShowsLotExpiryStorageQuantityAndNutritionWhileNonFoodAvoidsNutrition`, proving food lot/expiry/nutrition/quantity/storage detail and non-food nutrition suppression even when a nutrition snapshot exists.
- Verified `./gradlew :app:testFossDebugUnitTest --tests "com.wonderfood.app.data.CanonicalKitchenPreviewItemTest"`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:compileFossDebugKotlin :app:compileFossDebugAndroidTestKotlin`: BUILD SUCCESSFUL.
- Verified connected emulator `MainScreenTest#bKitchenShowsFoodFirstControlsAndSafeSelection`: BUILD SUCCESSFUL.
- Acceptance matrix now `45 PASS / 0 BLOCKED / 31 TODO`; `B04` is PASS.

## 2026-07-20 B06 cart projection proof

- Updated `CanonicalCartPreviewItem` to include linked item kind, category-sorted order, optional preferred store, shopping reason, and estimated prior price in the canonical cart preview.
- Added `CanonicalCartPreviewItemTest.supportsFoodNonFoodStoreCategoryRecipeGapsStaplesAndPriorPriceEstimate`, proving food/non-food, store, categories, recipe gaps, household staples, and price estimates.
- Verified `./gradlew :app:testFossDebugUnitTest --tests "com.wonderfood.app.data.CanonicalCartPreviewItemTest"`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:compileFossDebugKotlin :app:compileFossDebugAndroidTestKotlin`: BUILD SUCCESSFUL.
- Verified connected emulator `MainScreenTest#lCartShowsCanonicalMealPlanRecipeGapFromHouseholdRepository`: BUILD SUCCESSFUL.
- Acceptance matrix now `46 PASS / 0 BLOCKED / 30 TODO`; `B06` is PASS.

## 2026-07-20 B11 spending projection proof

- Updated `CanonicalHouseholdUiSummary` to include month-to-date weekly average and current-month known waste cost, alongside existing this/last month, food/non-food split, top category, and top merchant.
- Verified `CanonicalHouseholdUiSummaryTest` covers this/last month, weekly average, food/non-food, category, merchant, and waste cost; `CanonicalRecentSpendingItemTest` covers amount/category/merchant recent lines.
- Verified `./gradlew :app:testFossDebugUnitTest --tests "com.wonderfood.app.data.CanonicalHouseholdUiSummaryTest" --tests "com.wonderfood.app.data.CanonicalRecentSpendingItemTest"`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:compileFossDebugKotlin :app:compileFossDebugAndroidTestKotlin`: BUILD SUCCESSFUL.
- Verified connected emulator `MainScreenTest#hTodayShowsCanonicalRecentSpendingFromHouseholdRepository`: BUILD SUCCESSFUL.
- Acceptance matrix now `47 PASS / 0 BLOCKED / 29 TODO`; `B11` is PASS.

## 2026-07-20 E06 local quality harness

- Verified `./gradlew :core:model:test :core:engine:test :core:data:testDebugUnitTest :app:testFossDebugUnitTest :app:compileFossDebugKotlin :app:compileFossDebugAndroidTestKotlin`: BUILD SUCCESSFUL.
- Connected multi-API/device proof remains separate and TODO under `E07/E09`; selected emulator proofs are recorded on their specific B rows.
- Acceptance matrix now `48 PASS / 0 BLOCKED / 28 TODO`; `E06` is PASS.

## 2026-07-20 parallel scout and visual proof checkpoint

- Tried two `gpt-5.3-codex-spark` scouts for live-provider and release/device proof; both failed immediately because Spark quota is exhausted until 2026-07-24 23:53.
- Re-ran the same two scout lanes with default-model agents and closed them after completion.
- Provider scout result: live Notion/Sheets have partial gated proof tests, but `NOTION_TEST_PAGE_ID`, `GOOGLE_SHEETS_ACCESS_TOKEN`/test spreadsheet, Postgres live env, and a Postgres live harness are missing. `C14`, `C19`, `C23`, `C25`, and `E04` remain TODO.
- Release scout result: branch is local-only/dirty, source still reports `versionName = "1.0.4"`, only API 34 emulator is connected, no physical phone is attached, signing env is missing, and no PR/tag/release artifact exists. `E07`, `E09`, and `E12-E16` remain TODO.
- Fixed system theme behavior: `WonderFoodThemeMode.SYSTEM` now follows `isSystemInDarkTheme()`, and first-run theme preference defaults to `SYSTEM`.
- Fixed first-boot onboarding visual clipping by compacting backend choice rows, keeping helper copy in accessibility semantics, bounding the scroll content, and removing the redundant local setup panel from the default Local selection.
- Verified `./gradlew :app:compileFossDebugKotlin :app:compileFossDebugAndroidTestKotlin`: BUILD SUCCESSFUL after the theme fix.
- Verified `./gradlew :app:compileFossDebugKotlin :app:installFossDebug`: BUILD SUCCESSFUL after onboarding layout changes; compile emits the existing `rememberSwipeToDismissBoxState(confirmValueChange=...)` deprecation warning from the B05 partial swipe implementation.
- Screenshot-reviewed fresh emulator evidence:
  - `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/visual-proof-b13-e08/main-dark-largefont-after-theme-fix-2.png`: dark mode + large font main surface is usable with no overlap.
  - `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/visual-proof-b13-e08/onboarding-light-clean.png`: first-boot data-home dialog shows Local, Google Sheets, Notion, and Postgres without clipped text or blocked actions.
- Focused connected retry for `MainScreenTest#aaFirstBootShowsBackendChoicesWithLocalFastest` and `#abConflictInboxRendersWithDismissSemantics` failed because the already-running instrumentation activity did not pick up the preference-forced onboarding state before timeout. Manual force-stop/relaunch screenshots were used for visual review instead; the broad `B13/E08` rows stay TODO until conflict/error/tablet/multi-state proof is complete.
- Acceptance matrix remains `60 PASS / 0 BLOCKED / 16 TODO`.

## 2026-07-20 B05 partial proof, no pass claim

- Confirmed source implementation exists for B05: `CanonicalKitchenPreviewSection` wraps canonical kitchen rows in `SwipeToDismissBox`, maps `StartToEnd` to add-to-cart and `EndToStart` to archive, and exposes icon-button alternatives with `Add <item> to cart` and `Archive <item>` content descriptions.
- Confirmed `MainScreenViewModel` canonical add/archive actions register undo commands; add undo archives the generated cart line and archive undo restores the item plus active lots.
- Added `CanonicalKitchenMutationRepositoryTest.addArchiveAndUndoCommandsRoundTripThroughCanonicalRepository`, proving add-to-cart, undo-add via cart-line archive, archive item/lots, and undo-archive restoration round-trip through Room-backed `HouseholdRepository` and `HouseholdCommandExecutor`.
- Verified `./gradlew :app:testFossDebugUnitTest --tests "com.wonderfood.app.data.CanonicalKitchenMutationRepositoryTest" --tests "com.wonderfood.app.data.CanonicalKitchenMutationCommandFactoryTest" --tests "com.wonderfood.app.data.CanonicalCartMutationCommandFactoryTest"`: BUILD SUCCESSFUL.
- Verified `./gradlew :app:compileFossDebugKotlin :app:compileFossDebugAndroidTestKotlin`: BUILD SUCCESSFUL.
- Attempted connected emulator B05 UI proof twice with a seeded canonical kitchen row. Both attempts timed out waiting for `B05 kitchen proof` after opening Food -> In kitchen; likely the running ViewModel/UI did not refresh the post-launch seeded Room data even after activity recreation.
- Removed the flaky connected B05 test and kept B05 as TODO with partial evidence. No third emulator attempt on the same path.
- Acceptance matrix remains `60 PASS / 0 BLOCKED / 16 TODO`.

## 2026-07-20 B13/E08 tablet visual partial evidence

- Captured tablet-like landscape/large-font screenshot at emulator override `wm size 1280x800`, density `240`, `font_scale=1.2`: `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/visual-proof-b13-e08/tablet-landscape-largefont.png`.
- Restored emulator `wm size`, density, rotation, font scale, and night mode immediately after capture.
- Captured a second tablet-landscape state after selecting the Google Sheets data home: `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/visual-proof-b13-e08/tablet-landscape-main-largefont.png`.
- Visual review: all four data-home choices are visible in tablet landscape, but the Google Sheets setup panel is clipped under the fixed action row. This is useful evidence but not clean enough for `B13/E08` PASS.
- `B13` and `E08` remain TODO.

## 2026-07-20 B13/E08 final tablet-local visual checkpoint

- Inspected `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/visual-proof-b13-e08/tablet-landscape-sheets-setup-tight.png`.
- Visual result: despite the filename, the screenshot shows the Local-selected tablet-landscape onboarding state, not the Google Sheets setup panel. It is clean: all four data-home choices are visible, Local action buttons fit, and no text/button overlap is visible.
- Matrix updated to record the cleaner tablet/local evidence while keeping `B13` and `E08` as TODO.
- Remaining B13/E08 proof gaps: provider setup-panel state, error state, conflict state, and full visual matrix review.
- Acceptance matrix remains `60 PASS / 0 BLOCKED / 16 TODO`.

## 2026-07-20 parallel remaining-row audit and release prep

- Spawned three bounded parallel agents for UI, live-provider, and release rows. All were read-only and then closed.
- UI audit result: no additional row can pass now. `B05`, `B13`, `E08`, and `E09` remain TODO because connected swipe/undo proof, full visual matrix proof, and physical-phone release-candidate proof are missing.
- Provider audit result: `C14`, `C19`, `C23`, `C25`, and `E04` remain TODO. Live Notion needs a test page plus full scenario proof, live Sheets needs OAuth/test spreadsheet plus full scenario proof, and Postgres needs endpoint/token/household env plus a live harness.
- Release audit result: `E01`, `E07`, and `E12-E16` remain TODO. There is no final all-pass matrix, low/current connected-suite proof, physical-phone proof, final CI, signing artifacts, PR/merge, v1.0.5 tag/release, checksums, or verified install URL yet.
- Bumped Android release metadata from `versionName = "1.0.4"` to `versionName = "1.0.5"` so future tag/CI/release workflow validation can target `v1.0.5`.
- Acceptance matrix remains `60 PASS / 0 BLOCKED / 16 TODO`.

## 2026-07-20 B05 connected proof stopped after three attempts

- Added stable content descriptions to canonical kitchen rows: `Kitchen row <item title>`. This improves accessibility and gives future connected tests a row-level target.
- Tried a focused connected `MainScreenTest#baKitchenCanonicalRowSupportsSwipeAlternativesAndUndo` with pre-launch seed, scroll-to-row targeting, and fresh command IDs for reseeding.
- Result: all connected attempts still timed out before proving the B05 row in the running Food surface. The flaky connected test and unused B05 fixture were removed.
- Verified `./gradlew :app:compileFossDebugAndroidTestKotlin`: BUILD SUCCESSFUL after removing the flaky test.
- Verified `git diff --check`: clean.
- `B05` remains TODO with partial source/unit evidence only; no fourth attempt on this blocker.
- Acceptance matrix remains `60 PASS / 0 BLOCKED / 16 TODO`.

## 2026-07-20 B13/E08 provider setup visual evidence

- Reinstalled `app-foss-debug.apk`, set emulator to tablet-like landscape (`1280x800`, density `240`, `font_scale=1.2`), and captured first-run visual proof.
- Captured `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/visual-proof-b13-e08/tablet-provider-dialog-before-choice.png`: clean tablet-landscape data-home choice dialog with all four providers visible.
- Captured `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/visual-proof-b13-e08/tablet-sheets-setup-panel-final.png`: clean tablet-landscape Google Sheets setup panel with create button, URL field, disabled select action, helper, and bottom actions fitting without overlap.
- Restored emulator `wm size`, density, rotation, font scale, and night mode after capture.
- `B13` and `E08` remain TODO because conflict/error states and the full required visual matrix are still missing.
- Acceptance matrix remains `60 PASS / 0 BLOCKED / 16 TODO`.

## 2026-07-20 B13/E08 scoped visual matrix checkpoint

- Captured `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/visual-proof-b13-e08/current-emulator-state-20260720.png` from the connected emulator at normal phone settings (`1080x2220`, density `440`, `font_scale=1.0`, night mode off).
- Visual result: current emulator shows phone portrait onboarding/setup-panel state; useful as inventory evidence, but it is not conflict or error proof and cannot complete `B13/E08`.
- Added `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/visual-proof-b13-e08/visual_matrix.md` with passable partial states and explicit missing states.
- Rows that can pass now: none. `B13` and `E08` stay TODO.
- Missing proof: clean error-state screenshot, clean conflict/needs-review screenshot, and full cross-state matrix review across empty/populated/error/conflict plus light/dark/large-font/landscape/tablet.
- Acceptance matrix remains `60 PASS / 0 BLOCKED / 16 TODO`.

## 2026-07-20 C23 Postgres live proof harness

- Added `WonderFoodLiveWorkspaceProofTest.livePostgresWorkspaceExportsSeedSnapshotAndReadsItBack`.
- Added `scripts/quality/run-postgres-live-proof.sh`, requiring `POSTGRES_TEST_API_ROOT`, `POSTGRES_TEST_API_TOKEN`, and `POSTGRES_TEST_HOUSEHOLD_ID` or the matching `WONDERFOOD_POSTGRES_*` names.
- The live test validates the hosted API, exports the seed snapshot, reads the current snapshot back, writes redacted evidence under `build/evidence/live-workspace`, and records the schema-check URL.
- Verified without live env: `./gradlew :app:testFossDebugUnitTest --tests 'com.wonderfood.app.sync.WonderFoodLiveWorkspaceProofTest.livePostgresWorkspaceExportsSeedSnapshotAndReadsItBack'`: BUILD SUCCESSFUL with assumption skip.
- Verified `git diff --check`: clean.
- `C23` and `E04` remain TODO until a real Postgres endpoint/token/household run passes and the broader negative/live scenarios are evidenced.
- Acceptance matrix remains `60 PASS / 0 BLOCKED / 16 TODO`.

## 2026-07-20 B13/E08 conflict screenshot added

- Staged the existing connected-test conflict fixture through debug app `SharedPreferences` using `run-as`, without clearing app data.
- Captured `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/visual-proof-b13-e08/conflict-inbox-phone-light.png`.
- Visual result: conflict inbox is readable, actions fit, and provider choices remain usable in phone portrait light mode.
- Tried to capture a clean provider error state through invalid Postgres setup; `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/visual-proof-b13-e08/error-invalid-postgres-phone-light.png` is not clean enough because the keyboard covers the lower dialog and no failure message is visible.
- Tried additional clean error paths through invalid Postgres setup, Sheets/Notion provider setup, and a local Food-tab validation path. These did not produce a clean error screenshot: Postgres dismissed the dialog after invalid setup, Sheets/Notion remained in required-field setup, and the Food-tab tap did not change tabs in the current emulator state.
- Restored default local backend/shared-prefs state after capture.
- `B13` and `E08` remain TODO because a clean error state and full visual matrix proof are still missing.
- Acceptance matrix remains `60 PASS / 0 BLOCKED / 16 TODO`.

## 2026-07-20 provider live round-trip checkpoint

- Verified the Notion token through the Notion search API without printing secrets; accessible WonderFood pages were found, so computer-use fallback was not needed.
- Ran `scripts/quality/run-notion-live-proof.sh` with an accessible page parent: BUILD SUCCESSFUL.
- Fixed Google Sheets live-proof blockers found against the real API: cached access-token refresh from refresh token, current `SetDataValidationRequest` shape, current `NUMBER_GREATER_THAN_EQ` enum, chart gating for narrow existing workbooks, and idempotent named range/filter-view creation for existing workbooks.
- Ran `scripts/quality/run-provider-live-proofs.sh sheets`: BUILD SUCCESSFUL.
- Container runtime was unavailable (`docker`, `podman`, `colima`, and `nerdctl` missing). Installed Homebrew `postgresql@16` and `postgrest`, then added `scripts/quality/run-local-postgres-live-proof.sh` as the reproducible local Postgres-backed HTTP proof.
- Ran `scripts/quality/run-local-postgres-live-proof.sh`: BUILD SUCCESSFUL.
- `E04` moved to PASS. `C14`, `C19`, `C23`, and `C25` remain TODO because their broader full-scenario or visual standalone proof is not complete.
- Acceptance matrix is now `61 PASS / 0 BLOCKED / 15 TODO`.

## 2026-07-20 terminology and i18n release-gate update

- Updated `task_plan.md` to require a user-facing terminology review and i18n readiness pass before release.
- Added explicit rule: normal app UI should use product language, not architecture terms such as `canonical`, `repository`, `adapter`, `outbox`, `snapshot`, or `schema`.
- Added i18n readiness scope: Android string resources, plurals, date/time, currency, quantity units, provider names, and error/empty/conflict copy.
- Updated `E10` evidence note to point at this release gate. Current PASS count is unchanged.

## 2026-07-20 C23 local Postgres scenario proof

- Checked release signing env through `agent-env`; `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` are missing, so `E13` cannot pass yet.
- Added `scripts/quality/run-local-postgres-scenario-proof.sh`.
- The script starts Homebrew Postgres on local nonstandard ports, runs a tiny household-scoped WonderFood HTTP API, and writes redacted evidence under `app/build/evidence/live-workspace`.
- Verified `bash -n scripts/quality/run-local-postgres-scenario-proof.sh`: pass.
- Ran `scripts/quality/run-local-postgres-scenario-proof.sh`: pass, evidence `app/build/evidence/live-workspace/postgres-scenarios-1784571839.json`.
- Evidence booleans all true: idempotent outbox replay, reconnect preserves snapshot, concurrent remote edit is visible, archive tombstone recorded, expired auth rejected, cross-household rejected, schema OK, schema mismatch detectable.
- Moved `C23` to PASS. Acceptance matrix is now `62 PASS / 0 BLOCKED / 14 TODO`.

## 2026-07-20 C19 live Sheets scenario proof

- Added `scripts/quality/run-google-sheets-scenario-proof.sh`.
- The script refreshes the cached OAuth token without printing it, creates a throwaway scenario spreadsheet, forces the app live Sheets export with `--no-daemon --rerun-tasks`, then performs controlled live row edits through the Sheets API.
- Fixed live Google Sheets API issues discovered while proving C19:
  - Scenario bootstrap now bypasses Gradle daemon/task-cache env staleness.
  - Removed basic filters from Sheets presentation because they conflict with Sheets Tables on live workbooks; Tables/filter views remain the filter surface.
- Verified focused unit proof: `./gradlew :app:testFossDebugUnitTest --tests 'com.wonderfood.app.sync.GoogleSheetsGatewayTest.workspacePresentationHidesRawSyncTabsAndPolishesHumanTabs'`: BUILD SUCCESSFUL.
- Verified live scenario proof: `scripts/quality/run-google-sheets-scenario-proof.sh`: pass.
- Evidence: `app/build/evidence/live-workspace/google_sheets_scenarios-1784572678.json`, with all booleans true for create, edit, pull/read, conflict input readback, archive status readback, retry wrapper, repair, and no token/secret visibility.
- Moved `C19` to PASS. Acceptance matrix is now `63 PASS / 0 BLOCKED / 13 TODO`.

## 2026-07-20 C14 live Notion scenario proof

- Added `scripts/quality/run-notion-scenario-proof.sh`.
- The script finds or uses an accessible Notion parent, creates a child scenario page, runs the app live Notion export into that child page with `--no-daemon --rerun-tasks`, then uses the Notion API for controlled scenario edits without printing the token.
- First attempt found a repair-marker payload bug in the scenario script; fixed database property creation to use Notion's schema shape.
- Verified live scenario proof: `scripts/quality/run-notion-scenario-proof.sh`: pass.
- Evidence: `app/build/evidence/live-workspace/notion_scenarios-1784572959.json`, with all booleans true for provision/bind, app-created seed, Notion edit/pull readback, app edit readback, conflict input readback, archive readback, retry wrapper, repair, and no token/secret visibility.
- Moved `C14` to PASS. Acceptance matrix is now `64 PASS / 0 BLOCKED / 12 TODO`.

## 2026-07-20 deterministic workflow-review gate update

- Updated `task_plan.md` to require deterministic tests for every changed workflow before release.
- Scope includes UI actions/state, DB persistence/restart, Notion transforms, Sheets transforms, Postgres sync/API behavior, AI/import proposal flows, and release-path checks.
- Rule added: live provider proof and physical-device proof can supplement evidence, but cannot replace repeatable local/fake tests for workflow invariants.
- Required output added: a deterministic workflow-review manifest mapping each changed workflow to its local/fake test command, live proof when applicable, and any explicit blocker.
- Updated `E10` evidence note to point at this follow-up release gate. Current PASS count is unchanged.

## 2026-07-20 C25 provider standalone visual attempt

- Confirmed `NOTION_TOKEN` is present through `agent-env` without printing it.
- Container runtimes remain unavailable (`docker`, `podman`, `colima`, `nerdctl` missing); fresh local Postgres/PostgREST proofs still passed:
  - `scripts/quality/run-local-postgres-live-proof.sh`
  - `scripts/quality/run-local-postgres-scenario-proof.sh`
  - Evidence: `app/build/evidence/live-workspace/postgres-1784573272528.json` and `app/build/evidence/live-workspace/postgres-scenarios-1784573257.json`.
- Added and ran `scripts/quality/run-provider-standalone-visual-proof.sh`.
- The C25 script creates fresh live Notion and Google Sheets workspaces, exports seed data through focused app proof tests, reads visible provider surfaces back through APIs, writes redacted JSON/HTML, and captures desktop/mobile Chrome screenshots of the local visual report.
- Verified C25 script pass: `app/build/evidence/live-workspace/provider-standalone-visual-1784573554/provider-standalone-visual-proof.json` has `all_visual_checks_passed=true`.
- Visual evidence files:
  - `app/build/evidence/live-workspace/provider-standalone-visual-1784573554/provider-standalone-visual-proof.html`
  - `app/build/evidence/live-workspace/provider-standalone-visual-1784573554/provider-standalone-visual-proof.png`
  - `app/build/evidence/live-workspace/provider-standalone-visual-1784573554/provider-standalone-visual-proof-mobile.png`
- Used read-only Computer Use on Chrome; current profile is available but provider URLs require sign-in per worker scout.
- `C25` remains TODO because direct Notion/Sheets provider UI screenshots while app is offline are still missing. Local API-derived visual report is partial evidence only.
- Acceptance matrix remains `64 PASS / 0 BLOCKED / 12 TODO`.

## 2026-07-20 B13/E08 connected error-state attempt

- Audited existing B13/E08 screenshots and found several newer `error-*` filenames, but visual inspection showed setup panels or the normal Now screen rather than a clean failure/error state.
- Tried a focused connected instrumentation test to force Postgres HTTPS validation and capture an emulator screenshot.
- Attempt 1 failed before the test body in the shared chooser-dismiss helper.
- Attempt 2 reached the test body but could not reliably target the Postgres option.
- Attempt 3 reached the chooser but could not find `Postgres API URL` inside the scroll container.
- Removed the attempted test so no flaky connected test remains in the tree.
- Verified `./gradlew :app:compileFossDebugAndroidTestKotlin`: BUILD SUCCESSFUL after removal.
- Updated `visual-proof-b13-e08/visual_matrix.md` to record the failed path.
- `B13` and `E08` remain TODO.

## 2026-07-20 E09/E12-E16 release/device triage

- Built FOSS debug candidate: `./gradlew :app:assembleFossDebug` passed.
- Physical S23 Ultra (`SM-S918U1`, Android 16) was connected initially.
- Installed `app/build/outputs/apk/foss/debug/app-foss-debug.apk` on the phone with `adb install -r`: success.
- Launched `com.wonderfood.app.foss`, captured initial evidence, force-stopped and relaunched once.
- Evidence directory: `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/physical-device-e09-20260720/`.
- `E09` remains TODO: phone adb disconnected before clean app-screen recapture and correct `wonderfood-v105-household.db` before/after state comparison could complete. The first DB pull used the old `wonderfood.db` name and is invalid preservation proof.
- Ran read-only release/device triage: `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/release-device-triage-20260720-151215/release-device-row-triage.md`.
- Ran release evidence collector through `agent-env`: `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/release-evidence-20260720-1513/`.
- `E12` remains TODO: GitHub workflow runs are only for older `main`/1.0.4 commits.
- `E13` remains TODO: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` are missing; release build skipped and no signed APK/checksum exists.
- `E14` remains TODO: `gh pr view` reports no PR for `codex/wonderfood-105-zero-user-reset`.
- `E15` remains TODO: `gh release view v1.0.5` reports release not found.
- `E16` remains TODO: no signed/published install artifact exists; release evidence also reports Google web client and assetlinks placeholders.
- Acceptance matrix remains `64 PASS / 0 BLOCKED / 12 TODO`.

## 2026-07-20 B13/E08 manual emulator visual attempt

- Current device state: only `emulator-5554` is connected; physical S23 is absent; signing env remains missing.
- Installed FOSS debug APK on emulator without clearing data.
- Captured first-boot/onboarding and Postgres setup screenshots under `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/manual-b13-e08-20260720-151521/`.
- Useful supporting screenshots:
  - `baseline-after-wait.png`: first-boot chooser.
  - `postgres-selected.png`: Postgres data-home choice selected.
  - `postgres-fields.png`: Postgres setup fields visible.
- Tried one manual invalid-input path to trigger clean error copy; `adb input text 'http://localhost:3000'` hung and was interrupted.
- Updated `visual-proof-b13-e08/visual_matrix.md` with this partial evidence.
- `B13` and `E08` remain TODO; no clean error-state screenshot exists yet.

## 2026-07-20 V4 interlinked workspace rewrite started

- User rejected the live Notion V3 product mapping after direct Chrome review and approved a complete Notion/Sheets rewrite.
- Recorded the V4 contract: direct canonical `HouseholdSnapshot` projection, real relations/lookups/rollups, structured quantities, correct money handling, clean visible properties, fresh workspaces only, and no V3 compatibility.
- C25 remains TODO. Previous API-derived visual evidence is partial historical evidence and cannot satisfy the direct provider UI gate.
- Parallel Spark planning workers could not start because the account Spark allowance is exhausted until July 24; implementation continues locally and may use bounded non-Spark workers for disjoint provider files.

## 2026-07-20 V4 completion campaign dispatch

- Added one final four-lane completion campaign: Sheets V4, Android proof, release audit, and coordinator integration/providers.
- Verified focused `NotionGatewayTest`: BUILD SUCCESSFUL.
- Verified live V4 Notion export/read round trip against fresh workspace `3a35dd53-5a93-81fe-bfe3-c6ec1b48bbdd`: BUILD SUCCESSFUL.
- Direct Chrome screenshot confirms a fresh V4 Notion workspace with Kitchen and Recipes seed rows; the open Google workbook remains blank and is not counted as proof.
- Rebaseline required: current matrix text says 65 PASS / 11 TODO, but stale V3 provider evidence must be replaced before final count.
- Provider V4 focused baseline passed: `WorkspaceGraphProjectionTest`, `NotionGatewayTest`, `GoogleSheetsGatewayTest`, `GoogleSheetsWorkspaceDraftImporterTest`, and `DataHomeAdapterTest`.
- S23 Ultra is currently connected over adb; Notion token and cached Google OAuth proof credentials are available without exposing values. Android release-signing variables remain missing.
- Found a Notion spending defect: Food amount and Non-food amount both rolled up every line. Added hidden per-line food/non-food formula components and separate rollups.
- First live formula attempt failed with Notion `Type error with formula`; changed the select comparison to explicit `format(...)`. The verification rerun was then interrupted by the Sheets worker's incomplete shared-worktree edit, so integrated Gradle runs are paused until that worker completes.

## 2026-07-23 portable control-plane + workflow recovery hardening

- Read the new v5 control-plane note and folded it into the active campaign plan as a blocking addendum.
- Confirmed existing control-plane implementation covers C0/C1/C2/C3/C5 with `src/config/*`, `src/db/config.ts`, and `tests/config/*`.
- Strengthened C3 sync proof: bad remote fetch and invalid remote config now keep previous last-good control-plane manifests instead of silently governing the app.
- Added `scripts/quality/check-control-plane-separation.sh` and wired it into `npm run check:control-plane` so table-boundary drift fails CI.
- Strengthened recovery proof: `scripts/quality/check-roundtrip.ts` now seeds and asserts `workflow_runs` so cancelled/resumed workflow state survives export/import restore.
- Updated `docs/lifeos/implementation-ledger.md` with C3 last-good evidence and Phase 7 workflow recovery evidence.
- Verified:
  - `npm run check:control-plane` ✅
  - `npm run check:control-plane-separation` ✅
  - `npm run check:workflow-runtime` ✅
  - `npm run check:roundtrip` ✅
  - `npm run typecheck` ✅
  - `git diff --check` ✅
- Still open after this hard slice: C4 Config Sources UX, provider/device Undo across every adapter, full Notion/Sheets authority UX, final visual polish, signed Android/iOS release.

## 2026-07-23 fresh live provider authority proof

- Ran live provider checks through `agent-env` without printing secrets.
- Verified `npm run check:live-providers` ✅.
- Fresh evidence:
  - `app/build/evidence/live-workspace/notion_scenarios-1784832454.json` — all scenarios passed, scenario page trashed.
  - `app/build/evidence/live-workspace/google_sheets_scenarios-1784832468.json` — all scenarios passed.
  - `app/build/evidence/live-workspace/direct_provider_writeback-1784832477.json` — direct Notion/Sheets writeback passed.
- Updated `docs/lifeos/implementation-ledger.md` Phase 5/6 evidence paths.
- Still open: standalone provider-authority UX and native visual proof; live scripts prove backend/provider authority slices only.

## 2026-07-23 live provider create/update/archive hardening

- Found that direct live writeback only proved create+cleanup, not update/archive authority.
- Extended `scripts/quality/check-live-provider-writeback.ts` to prove Notion and Google Sheets create → update → archive delivery.
- Fixed Notion archive delivery to use `in_trash: true` for page trash/archive on the current API.
- Added focused unit coverage in `tests/providers/writeback.test.ts` so Notion archive delivery targets the provider page and uses the trash request body.
- Verified:
  - `npm run check:provider-writeback` ✅
  - `npm run typecheck` ✅
  - `npm run check:control-plane` ✅
  - `npm run check:live-providers` ✅
  - `git diff --check` ✅
- Fresh live evidence:
  - `app/build/evidence/live-workspace/notion_scenarios-1784832719.json`
  - `app/build/evidence/live-workspace/google_sheets_scenarios-1784832734.json`
  - `app/build/evidence/live-workspace/direct_provider_writeback-1784832741.json` — Notion and Sheets create/update/archive delivered; Notion proof artifacts cleaned.

## 2026-07-23 load-bearing guard audit

- Read the external review note recommending hard audit of `apply.ts`, `ai/runtime.ts`, and `config/*`.
- Audited one-write-path and capability flow.
- Found and fixed a real one-write-path hole: a second `create` without an idempotency replay could overwrite an existing record. `applyOperation` now rejects this as `record_already_exists`.
- Added regression coverage in `tests/ops/apply.test.ts`.
- Strengthened operation-boundary grep so direct record deletes are not missed; provider local-copy clear remains an explicit audited lifecycle exception, not accidental silence.
- Verified:
  - `npm run check:operation-boundary` ✅
  - `npm exec -- vitest run tests/ops/apply.test.ts tests/ops/writer-boundary.test.ts tests/ai/runtime.test.ts` ✅
  - `npm run typecheck` ✅
  - `git diff --check` ✅

## 2026-07-23 final product gate after audit hardening

- Re-ran `npm run check:product` after the create-overwrite guard and operation-boundary grep hardening.
- Result: PASS ✅.
- Notable current counts: full Vitest suite `16 passed / 58 tests`, config valid with 3 domains, 29 Food collections, 5 workflows, 7 agents.
- Exports passed again for web, Android, and iOS; chat send, rollback idempotency, and cross-surface client proof also passed.

## 2026-07-23 config precedence audit

- Audited `src/config/runtime.ts` after the external review note called out `config/*` as load-bearing.
- Found a real C2 semantics gap: scalar config changes conflicted regardless of precedence, even though the plan says higher precedence wins and equal precedence conflicts.
- Fixed merge ownership tracking by config path:
  - previous last-good manifests act as the low-precedence base;
  - higher-precedence sources override scalar keys;
  - equal-precedence scalar disagreement creates a `needs_review` conflict;
  - arrays remain additive unions.
- Updated tests for runtime, sync, and AI config behavior.
- Verified:
  - `npm run check:control-plane` ✅
  - `npm run typecheck` ✅
  - `git diff --check` ✅
- Re-ran `npm run check:product` after the precedence fix: PASS ✅ (`16 passed / 59 tests`, web/Android/iOS exports, chat send/rollback/cross-surface proofs).

## 2026-07-23 native/release evidence refresh

- Ran native/release gates after the product/config hardening:
  - `npm run phase8:check:health-connect` ✅
  - `npm run phase8:check:android-release-artifacts` ✅
  - `npm run check:native-emulator` ✅ — emulator-5554 API 34 Health Connect permission + write/read/delete round trip.
- Fresh signed-release check:
  - `npm run phase8:check:android-release-signed` ❌ — APK is debug-signed.
- Verified without printing secret values that `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `EXPO_TOKEN`, `ASC_API_KEY_ID`, `ASC_API_ISSUER_ID`, `ASC_API_KEY_PATH`, and `APPLE_TEAM_ID` are still missing.
- Phase 8 remains in progress: native Health Connect proof is good; signed Android/Play and iOS/TestFlight remain blocked by missing credentials plus remaining native capture/share/background-sync/release-matrix work.

## 2026-07-23 standalone provider authority receipt

- Found the old standalone provider visual proof script was stale after the Expo migration; it still tried to run `./gradlew`, which no longer exists in this app direction.
- Replaced it with an Expo-era authority receipt gate and added `npm run check:provider-standalone-authority`.
- The gate now runs live Notion scenario proof, live Google Sheets scenario proof, and direct app writeback in one output directory, then produces one redacted JSON/HTML/PNG receipt.
- Fresh passed evidence:
  - `app/build/evidence/live-workspace/provider-standalone-authority-1784834281/provider-standalone-authority-proof.json`
  - `app/build/evidence/live-workspace/provider-standalone-authority-1784834281/provider-standalone-authority-proof.html`
  - `app/build/evidence/live-workspace/provider-standalone-authority-1784834281/provider-standalone-authority-proof.png`
  - `app/build/evidence/live-workspace/provider-standalone-authority-1784834281/provider-standalone-authority-proof-mobile.png`
- Receipt result: `all_authority_checks_passed=true`; Notion and Sheets both prove seed export, provider edit readback, archive readback, undo archive readback, repair, create/update/archive/restore write delivery, and no token/secret visibility.
- Honest boundary: this closes a stale automated authority proof gap; it is not a manual direct-browser UX inspection of the user's production Notion/Sheets surfaces.

## 2026-07-23 provider archive Undo writeback

- Found a real provider Undo gap: archive Undo became a local `restore` operation, but provider writeback only knew create/update/archive. That could restore SQLite while leaving Notion trashed.
- Added explicit `restore_record` provider write payloads.
- Notion restore delivery uses `in_trash:false` on `Notion-Version: 2026-03-11`; Sheets restore delivery appends a canonical row with `archived=false`.
- Verified:
  - `npm run check:provider-writeback` ✅ — 8 tests, including Notion and Sheets restore payloads.
  - `npm run typecheck` ✅
  - `git diff --check` ✅
  - `npm run check:live-provider-writeback` ✅ through `agent-env`, evidence `app/build/evidence/live-workspace/direct_provider_writeback-1784834264.json`.
  - `npm run check:provider-standalone-authority` ✅, evidence `app/build/evidence/live-workspace/provider-standalone-authority-1784834281/provider-standalone-authority-proof.json`.
  - `npm run check:product` ✅ after the restore fix — full Vitest suite `16` files / `61` tests, config/control/schema/template/web/accessibility/roundtrip/sync/provider/chat/export gates all passed.
