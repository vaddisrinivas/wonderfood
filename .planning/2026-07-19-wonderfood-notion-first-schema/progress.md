# Progress

## 2026-07-19

- Inspected the canonical checkout, dirty worktree, existing pluggable-backend plan, current Android domain, Room entities, and V1 Notion/Sheets workspace schema.
- Researched current official Notion view, data-source property, template, and automation capabilities.
- Researched current official Google Sheets Tables, typed columns, filter views, developer metadata, and protected ranges.
- Reviewed schema.org food/recipe vocabularies as interoperability mappings.
- Reviewed current recipe/pantry/meal-planning product loops and user feedback.
- Reframed the product: selected backend is human-owned authority; Android is the provider-neutral AI home-space; hosted catalog/community data remains separate.
- Defined the V2 household model, exact Notion hierarchy/properties/views/templates, Sheets tabs/tables/views, app information architecture, sync/conflict policy, migration, phases, and validation matrix.
- No production code or live workspace data changed during this planning pass.

## Next

- Obtain product approval for the V2 contract.
- Implement Phase 1 schema registry and migration fixtures.
- Provision a disposable Notion V2 workspace first for visual/workflow approval.

## 2026-07-19 Complete Standards Pass

- Rechecked schema.org Recipe, HowTo steps/sections/supplies, quantitative values, nutrition, product, order, order item, invoice, price, review, collection, interaction, and action vocabularies.
- Added GS1 product/GTIN/package/lot/date-mark distinctions.
- Added USDA FoodData Central and Open Food Facts identity, serving-basis, nutrition, and provenance considerations.
- Added UCUM quantity/unit representation and contextual culinary conversion rules.
- Added Android Health Connect nutrition compatibility.
- Added RFC 5545 meal scheduling, RFC 6902 field patching, and ActivityStreams hosted-community mappings.
- Checked current Sheets size, payload, quota, atomic batch, and retry constraints.
- Replaced the shallow complete-domain assumption with a three-layer architecture: seven everyday surfaces, managed household records, and hosted catalog/community records.
- Added `schema_blueprint.md` as the authoritative complete schema, provider projections, sync protocol, invariants, migration, implementation order, and acceptance gates.
- No production code, live Notion data, or live Google Sheet data changed.

## Updated Next

- Implement the canonical registry/value objects and fixture first.
- Build a disposable Notion V3 workspace for visual and workflow approval before touching the live workspace.

## 2026-07-19 Implementation Slice 1

- Replaced the old six-tab human workspace projection with the V3 shared Notion/Sheets projection.
- Added everyday tables: Home, Kitchen, Recipes, Meals, Plans, Shopping, Purchases, and Goals.
- Added managed tables: Foods, Products, Recipe Ingredients, Recipe Revisions, Inventory Activity, Shopping Demand, Purchase Lines, Nutrition Facts, Members, Activity, and Workspace.
- Mapped the current `WonderFoodSnapshot` model into the new projection without removing the hidden typed sync tabs.
- Updated Notion property support for URL and checkbox fields.
- Updated Google Sheets onboarding status copy and focused schema tests.
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.GoogleSheetsGatewayTest' --tests 'com.wonderfood.app.sync.NotionGatewayTest'`.
- Broader compile gate passed: `./gradlew :app:assemblePlayDebug`.

## Current Implementation Gaps

- The projection is still export-oriented; provider read/import for the new friendly columns is not implemented yet.
- Notion views/templates and Sheets typed tables/filter views are not provisioned yet.
- Products, Members, and Goals are present as schema surfaces but do not yet map from first-class domain records.
- Disposable V3 Notion/Sheets workspaces have not been created or visually approved in this slice.

## Next After Slice 1

- Add canonical workspace schema metadata/version rows and migration tests for old tab names.
- Add Notion view/template provisioning for everyday and managed databases.
- Add Sheets typed table/filter/developer-metadata provisioning.
- Add seed fixture export for visual review before touching live workspaces.

## 2026-07-19 Implementation Slice 2

- Added Google Sheets presentation provisioning for workspace tabs:
  - raw typed sync tabs are hidden
  - headers are frozen
  - basic filters are applied
  - columns are auto-resized
  - newly created human workspace tabs receive native Sheets table requests
- Added a readable, idempotent Notion Home scaffold marker so a provisioned workspace root is not just a pile of child databases.
- Updated focused tests to cover Sheets presentation requests and Notion scaffold blocks.
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.GoogleSheetsGatewayTest' --tests 'com.wonderfood.app.sync.NotionGatewayTest'`.
- Broader compile gate passed: `./gradlew :app:assemblePlayDebug`.

## Current Implementation Gaps After Slice 2

- Notion true linked database views/templates are still not implemented; current scaffold is readable block structure plus child databases.
- Sheets developer metadata and warning-only protected system columns are not implemented yet.
- Friendly-column import/merge from Notion/Sheets back into `WonderFoodSnapshot` remains incomplete.
- Seed fixture export and disposable workspace visual proof are still pending.

## 2026-07-19 Implementation Slice 3

- Added workspace schema version `3` as an explicit shared projection version.
- Added Google Sheets developer metadata requests for newly provisioned tabs with:
  - semantic table title
  - table kind (`raw_sync` or `workspace`)
  - workspace schema version
  - header list
- Added warning-only protected ranges for system columns:
  - raw sync tabs protect `id`, `version`, `updated_at`, `archived_at`, and `payload_json`
  - human workspace tabs protect `identifier`
- Kept metadata/protection creation scoped to newly created tabs to avoid duplicate protected ranges or duplicate metadata on every sync.
- Updated focused tests for metadata and protected-range requests.
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.GoogleSheetsGatewayTest' --tests 'com.wonderfood.app.sync.NotionGatewayTest'`.
- Broader compile gate passed: `./gradlew :app:assemblePlayDebug`.

## Current Implementation Gaps After Slice 3

- Notion true linked database views/templates are still not implemented; current scaffold is readable block structure plus child databases.
- Friendly-column import/merge from Notion/Sheets back into `WonderFoodSnapshot` remains incomplete.
- Seed fixture export and disposable workspace visual proof are still pending.

## 2026-07-19 Implementation Slice 4

- Added `GoogleSheetsWorkspaceRow` and `readWorkspaceRows()` to read visible human workspace tabs with their header rows.
- Added `GoogleSheetsWorkspaceDraftImporter` to convert friendly workspace edits into reviewable app drafts:
  - Kitchen rows -> `InventoryDraft`
  - Shopping rows -> `GroceryDraft`
  - Recipes rows -> `RecipeDraft`
  - eaten/confirmed Meals rows -> `MealLogDraft`
  - planned Meals rows -> `MealPlanDraft`
- Added `GoogleSheetsSnapshotSyncCoordinator.readRemoteWorkspaceDraft()` so app/onboarding code can import visible Sheets edits separately from hidden raw snapshot import.
- Added focused tests for friendly workspace row import and coordinator integration.
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.GoogleSheetsGatewayTest' --tests 'com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest' --tests 'com.wonderfood.app.sync.GoogleSheetsWorkspaceDraftImporterTest'`.
- Broader compile gate passed: `./gradlew :app:assemblePlayDebug`.

## Current Implementation Gaps After Slice 4

- Friendly Sheets import now creates reviewable drafts, but it does not yet perform field-version merge back into canonical `WonderFoodSnapshot` records.
- Notion true linked database views/templates are still not implemented; current scaffold is readable block structure plus child databases.
- Notion friendly-column import is not implemented yet.
- Seed fixture export and disposable workspace visual proof are still pending.

## 2026-07-19 Implementation Slice 5

- Added Notion friendly workspace import:
  - discovers provisioned WonderFood databases under the Notion root page
  - queries each workspace database with pagination
  - parses Notion `title`, `rich_text`, `select`, `number`, `date`, `url`, and `checkbox` property values
  - converts Notion pages into the same friendly workspace row model used by Sheets imports
  - exposes `readRemoteWorkspaceDraft()` for reviewable app draft import
- Reused the friendly workspace draft importer so Notion and Sheets interpret Kitchen, Shopping, Recipes, and Meals consistently.
- Added focused Notion property parsing tests.
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.NotionGatewayTest' --tests 'com.wonderfood.app.sync.GoogleSheetsWorkspaceDraftImporterTest'`.
- Broader compile gate passed: `./gradlew :app:assemblePlayDebug`.

## Current Implementation Gaps After Slice 5

- Friendly Notion/Sheets import creates reviewable drafts, but still does not perform canonical field-version merge into `WonderFoodSnapshot` records.
- Notion true linked database views/templates are still not implemented; current scaffold is readable block structure plus child databases.
- Seed fixture export and disposable workspace visual proof are still pending.

## 2026-07-19 Implementation Slice 6

- Added production `WonderFoodWorkspaceSeedFixture` for realistic disposable workspace data:
  - Kitchen inventory across pantry/fridge/freezer
  - nutrition labels
  - shopping needs and bought items
  - two recipes with structured ingredients and steps
  - weekly plan and planned meals
  - meal log
  - pantry-use and grocery-purchase activity
  - receipt capture
- Added seed projection tests proving the fixture populates everyday and managed workspace tables.
- Fixed two workspace readability bugs exposed by the fixture:
  - Shopping rows now use the source page title before falling back to reason/category when no Food relation exists.
  - Purchases rows now use the receipt page title before falling back to timestamp/id when merchant is unknown.
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.GoogleSheetsGatewayTest' --tests 'com.wonderfood.app.sync.NotionGatewayTest' --tests 'com.wonderfood.app.sync.GoogleSheetsWorkspaceDraftImporterTest' --tests 'com.wonderfood.app.sync.WonderFoodWorkspaceSeedFixtureTest'`.
- Broader compile gate passed: `./gradlew :app:assemblePlayDebug`.

## Current Implementation Gaps After Slice 6

- Friendly Notion/Sheets import creates reviewable drafts, but still does not perform canonical field-version merge into `WonderFoodSnapshot` records.
- Notion true linked database views/templates are still not implemented; current scaffold is readable block structure plus child databases.
- Disposable live Notion/Sheets workspace visual proof has not been run in this slice.

## 2026-07-19 Implementation Slice 7

- Added `WonderFoodWorkspaceSnapshotMerger`, a pure canonical merge layer for friendly Notion/Sheets workspace rows.
- Current merge scope updates existing records by stable `identifier`:
  - Kitchen rows update Food name plus StockLot quantity, unit, status, location, and expiry.
  - Shopping rows update page title, quantity, unit, cart state, and reason.
  - Recipes rows update title, state, servings, prep/cook minutes, ingredient lines, and direction steps.
  - Meals rows update plan-entry date/slot/state/servings and meal-log page title/time/slot/state.
  - Purchases rows update receipt page title, merchant, purchased time, subtotal, total, currency, and state.
- Added merge result objects with changes/conflicts lists. Conflict reporting is currently structural only; no field-clock conflict resolution yet.
- Added focused merge test against the realistic seed fixture.
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.GoogleSheetsGatewayTest' --tests 'com.wonderfood.app.sync.NotionGatewayTest' --tests 'com.wonderfood.app.sync.GoogleSheetsWorkspaceDraftImporterTest' --tests 'com.wonderfood.app.sync.WonderFoodWorkspaceSeedFixtureTest' --tests 'com.wonderfood.app.sync.WonderFoodWorkspaceSnapshotMergerTest'`.
- Broader compile gate passed: `./gradlew :app:assemblePlayDebug`.

## Current Implementation Gaps After Slice 7

- Merge only updates existing canonical records; it does not create new Food/Recipe/Meal/Shopping/Purchase records from unmatched provider rows yet.
- Merge does not yet persist field-version clocks or perform conflict-resolution UI flows.
- Notion true linked database views/templates are still not implemented; current scaffold is readable block structure plus child databases.
- Disposable live Notion/Sheets workspace visual proof has not been run in this slice.

## 2026-07-19 Implementation Slice 8

- Extended `WonderFoodWorkspaceSnapshotMerger` to create new canonical records from unmatched friendly workspace rows.
- Creation support:
  - Kitchen rows create Food page, Food, and StockLot.
  - Shopping rows create Shopping page and ShoppingItem.
  - Recipe rows create Recipe page, Recipe, RecipeIngredient rows, and RecipeStep rows.
  - Planned Meal rows create or append to an imported workspace MealPlan.
  - Eaten/confirmed Meal rows create MealLog page and MealLog.
  - Purchase rows create Receipt page and Receipt.
- Blank provider identifiers now get stable deterministic IDs from table plus human title, reducing repeat-import duplicates.
- Added focused creation test covering all supported everyday surfaces from an empty snapshot.
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.GoogleSheetsGatewayTest' --tests 'com.wonderfood.app.sync.NotionGatewayTest' --tests 'com.wonderfood.app.sync.GoogleSheetsWorkspaceDraftImporterTest' --tests 'com.wonderfood.app.sync.WonderFoodWorkspaceSeedFixtureTest' --tests 'com.wonderfood.app.sync.WonderFoodWorkspaceSnapshotMergerTest'`.
- Broader compile gate passed: `./gradlew :app:assemblePlayDebug`.

## Current Implementation Gaps After Slice 8

- Merge does not yet persist field-version clocks or perform conflict-resolution UI flows.
- Notion true linked database views/templates are still not implemented; current scaffold is readable block structure plus child databases.
- Disposable live Notion/Sheets workspace visual proof has not been run in this slice.

## 2026-07-19 Implementation Slice 9

- Hardened `WonderFoodWorkspaceSnapshotMerger` for editable Notion/Sheets workspaces.
- Added deterministic duplicate-row handling:
  - Rows are normalized to stable identifiers before merge.
  - Duplicate `(table, identifier)` rows now emit a `WorkspaceMergeConflict`.
  - The last visible duplicate row is applied deterministically.
- Added typed friendly-row validation before merge:
  - Kitchen: quantity, pantry state, best-by date.
  - Shopping: needed quantity and cart state.
  - Recipes: recipe state, servings, prep minutes, cook minutes.
  - Meals: date/timestamp, slot, meal state, servings.
  - Purchases: purchased timestamp/date, subtotal, total, purchase state.
- Tightened date application so human text like `soon` is reported as a conflict and does not overwrite canonical `IsoDate` fields.
- Added merge clocks:
  - `mergeClock` records the provider merge timestamp.
  - `fieldClocks` records deterministic per-field versions from applied changes.
- Added focused tests proving duplicate-row conflict behavior, deterministic last-row application, field-clock emission, invalid-value conflict reporting, and canonical-field preservation.
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.WonderFoodWorkspaceSnapshotMergerTest'`.
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.GoogleSheetsGatewayTest' --tests 'com.wonderfood.app.sync.NotionGatewayTest' --tests 'com.wonderfood.app.sync.GoogleSheetsWorkspaceDraftImporterTest' --tests 'com.wonderfood.app.sync.WonderFoodWorkspaceSeedFixtureTest' --tests 'com.wonderfood.app.sync.WonderFoodWorkspaceSnapshotMergerTest'`.
- Broader compile gate passed: `./gradlew :app:assemblePlayDebug`.

## Current Implementation Gaps After Slice 9

- Full conflict-resolution UI is still not built; the merge layer now exposes conflicts/clocks for that UI.
- Disposable live Notion/Sheets workspace visual proof has not been run in this slice.

## 2026-07-19 Implementation Slice 10

- Wired editable Google Sheets workspace rows into the app connection/import flow.
- Added `GoogleSheetsSnapshotSyncCoordinator.readRemoteWorkspaceMerge()`:
  - reads human workspace tabs,
  - merges them into the current local `WonderFoodSnapshot`,
  - returns `RemoteWorkspaceMerge` when there are applied changes or conflicts,
  - falls back to no-workspace-draft when the friendly tabs have nothing actionable.
- Updated Google Sheets backend connection to prefer friendly workspace merge preview before raw hidden snapshot import.
- Reused the existing explicit review/confirm flow so WonderFood does not silently overwrite or duplicate household data.
- Expanded `SheetsImportPreview` with:
  - source label,
  - workspace row count,
  - merge change count,
  - conflict count,
  - field-clock count,
  - merge clock,
  - top conflict summaries.
- Updated the Sheets import dialog to show merge metadata and conflict warnings before the user applies Sheet data.
- Added focused coordinator coverage proving friendly workspace rows merge into the local base snapshot and emit clocks.
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest'`.
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.GoogleSheetsGatewayTest' --tests 'com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest' --tests 'com.wonderfood.app.sync.NotionGatewayTest' --tests 'com.wonderfood.app.sync.GoogleSheetsWorkspaceDraftImporterTest' --tests 'com.wonderfood.app.sync.WonderFoodWorkspaceSeedFixtureTest' --tests 'com.wonderfood.app.sync.WonderFoodWorkspaceSnapshotMergerTest'`.
- Broader compile gate passed: `./gradlew :app:assemblePlayDebug`.

## Current Implementation Gaps After Slice 10

- Notion friendly workspace merge is not wired into the app connection/import flow yet.
- Full conflict-resolution UI is still a preview/confirm dialog, not a dedicated conflict inbox/editor.
- Disposable live Notion/Sheets workspace visual proof has not been run in this slice.

## 2026-07-19 Implementation Slice 11

- Wired editable Notion workspace rows into the same canonical merge path used by Google Sheets.
- Added `NotionGateway.readRemoteWorkspaceMerge()` and testable `mergeWorkspaceRows()`:
  - reads friendly Notion databases as workspace rows,
  - merges them into the current local `WonderFoodSnapshot`,
  - returns changes, conflicts, merge clock, and field clocks.
- Updated Notion connection behavior to preserve remote Notion data:
  - existing Notion workspace/snapshot data now prepares an explicit import review instead of immediately exporting local data over it,
  - empty Notion pages still receive workspace database export and snapshot export.
- Reused the existing preview/confirm flow for Notion import review.
- Made the workspace import preview provider-aware so the dialog says `Notion` or `Google Sheets` instead of always saying `Sheet`.
- Added focused Notion merge coverage proving friendly Notion rows update the canonical snapshot and emit clocks.
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.NotionGatewayTest'`.
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.GoogleSheetsGatewayTest' --tests 'com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest' --tests 'com.wonderfood.app.sync.NotionGatewayTest' --tests 'com.wonderfood.app.sync.GoogleSheetsWorkspaceDraftImporterTest' --tests 'com.wonderfood.app.sync.WonderFoodWorkspaceSeedFixtureTest' --tests 'com.wonderfood.app.sync.WonderFoodWorkspaceSnapshotMergerTest'`.
- Broader compile gate passed: `./gradlew :app:assemblePlayDebug`.

## Current Implementation Gaps After Slice 11

- Full conflict-resolution UI is still a preview/confirm dialog, not a dedicated conflict inbox/editor.
- Disposable live Notion/Sheets workspace visual proof has not been run in this slice.

## 2026-07-19 Implementation Slice 12

- Added an in-app workspace conflict inbox for Notion/Sheets merge review results.
- `WonderFoodUiState` now carries `WorkspaceConflictInbox` with provider, source, conflict/change counts, merge clock, decision text, and conflict summaries.
- Import review cancel/confirm now preserves conflict summaries after the modal closes:
  - preserving remote data keeps conflicts visible for later review,
  - importing valid changes with conflicts keeps the unresolved conflict summary visible.
- Added `clearWorkspaceConflictInbox()` so reviewed conflicts can be dismissed explicitly.
- Rendered the conflict inbox inside the Data home/backend workspace dialog, above backend choices, so conflicts stay tied to the selected human-owned database.
- Made the inbox provider-aware for both Notion and Google Sheets.
- Validation passed: `./gradlew :app:assemblePlayDebug`.
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest' --tests 'com.wonderfood.app.sync.NotionGatewayTest' --tests 'com.wonderfood.app.sync.WonderFoodWorkspaceSnapshotMergerTest'`.

## Current Implementation Gaps After Slice 12

- Conflict inbox is in-memory UI state; durable conflict history/outbox persistence is not implemented yet.
- Disposable live Notion/Sheets workspace visual proof has not been run in this slice.

## 2026-07-19 Implementation Slice 13

- Made the workspace conflict inbox durable across app process restarts.
- Conflict inbox state now restores when `MainScreenViewModel` starts.
- Unresolved Notion/Sheets conflicts are saved in `wonderfood_shell` preferences as JSON.
- Preserving remote data and importing valid changes with conflicts both persist the conflict inbox.
- `clearWorkspaceConflictInbox()` now removes the saved inbox and clears UI state only after explicit dismissal.
- Validation passed: `./gradlew :app:assemblePlayDebug`.
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest' --tests 'com.wonderfood.app.sync.NotionGatewayTest' --tests 'com.wonderfood.app.sync.WonderFoodWorkspaceSnapshotMergerTest'`.

## Current Implementation Gaps After Slice 13

- Disposable live Notion/Sheets workspace visual proof has not been run in this slice.
- Full field-level conflict editing remains future hardening; current UX preserves and surfaces conflicts, while valid changes flow through the import review path.

## 2026-07-19 Implementation Slice 14

- Added `WonderFoodLiveWorkspaceProofTest`, a gated live-provider proof harness for disposable Notion and Google Sheets workspaces.
- The live proof harness:
  - runs only when provider-specific environment variables are present,
  - exports the realistic `WonderFoodWorkspaceSeedFixture`,
  - reads provider workspace rows back,
  - runs canonical merge over the returned rows,
  - writes redacted evidence JSON under `app/build/evidence/live-workspace/`.
- Fixed live Notion JVM proof compatibility:
  - `NotionGateway` now falls back to `curl` for PATCH only when the desktop JVM rejects PATCH through `HttpURLConnection`.
  - Android/native PATCH behavior remains the primary path.
- Fixed Notion API compatibility for the current implementation:
  - pinned `Notion-Version` to `2022-06-28`, which matches the gateway's implemented database endpoints while the 2025+ data-source migration remains future hardening.
  - repaired schema migration for existing Notion databases by renaming the existing title property to the current table title field instead of trying to create a second title property.
  - repaired existing non-title property type mismatches where Notion allows conversion, e.g. old rich-text `Unit`/`Location` columns to current select columns.
- Live Notion proof passed using the disposable page from earlier provider proof notes.
- Notion evidence written to `app/build/evidence/live-workspace/notion-1784483391758.json`:
  - redacted page id: `3a25...e83a`
  - upserted rows: `46`
  - read rows: `59`
  - tables read: `Home`, `Kitchen`, `Recipes`, `Meals`, `Plans`, `Shopping`, `Purchases`, `Foods`, `Recipe Ingredients`, `Recipe Revisions`, `Inventory Activity`, `Shopping Demand`, `Nutrition Facts`, `Activity`, `Workspace`
  - merge changes: `18`
  - merge conflicts: `0`
- Google Sheets live proof harness is ready but did not run because `GOOGLE_SHEETS_ACCESS_TOKEN` and `GOOGLE_SHEETS_TEST_SPREADSHEET_ID` are not present in the local agent environment.
- Validation passed: `NOTION_TEST_PAGE_ID=... ./gradlew :app:testPlayDebugUnitTest --tests "com.wonderfood.app.sync.WonderFoodLiveWorkspaceProofTest.liveNotionWorkspaceExportsSeedRowsAndReadsThemBack"` through the agent-env wrapper.
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.NotionGatewayTest' --tests 'com.wonderfood.app.sync.WonderFoodLiveWorkspaceProofTest'`.
- Broader compile gate passed: `./gradlew :app:assemblePlayDebug`.

## Current Implementation Gaps After Slice 14

- Disposable Google Sheets live proof is still pending OAuth access token plus a disposable spreadsheet id.
- Full Notion 2025+ data-source API migration remains future hardening; current gateway is deliberately pinned to the stable database API version it implements.

## 2026-07-19 Implementation Slice 15

- Added `scripts/quality/run-google-sheets-live-proof.sh`, a safe OAuth bootstrap wrapper for the Google Sheets live proof.
- The helper:
  - uses `GOOGLE_SHEETS_ACCESS_TOKEN` directly when already available,
  - otherwise starts a local `127.0.0.1` OAuth callback flow using `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`,
  - opens the browser for user approval without printing token values,
  - stores the temporary token JSON under `build/evidence/live-workspace/google-sheets-token.json`,
  - defaults to the prior disposable proof Sheet id `1-cu0kk39SBUeKS326Sc5GHCEFGF5L3305fr0Pkpf6H4` unless `GOOGLE_SHEETS_TEST_SPREADSHEET_ID` is supplied,
  - runs `WonderFoodLiveWorkspaceProofTest.liveGoogleSheetsWorkspaceExportsSeedRowsAndReadsThemBack` with the acquired token.
- Confirmed no local Google ADC/gcloud credentials or Sheets access token are currently available.
- Confirmed local env has Google client id/secret but not `GOOGLE_SHEETS_ACCESS_TOKEN` or `GOOGLE_SHEETS_TEST_SPREADSHEET_ID`.
- Did not auto-open the Google OAuth browser in this slice; the helper is ready for the explicit interactive proof run.
- Validation passed: `bash -n scripts/quality/run-google-sheets-live-proof.sh`.
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.WonderFoodLiveWorkspaceProofTest'`.
- Broader compile gate passed: `./gradlew :app:assemblePlayDebug`.

## Current Implementation Gaps After Slice 15

- Disposable Google Sheets live proof still needs one interactive OAuth approval run via `scripts/quality/run-google-sheets-live-proof.sh` or pre-supplied `GOOGLE_SHEETS_ACCESS_TOKEN`.

## 2026-07-19 Implementation Slice 16

- Finished Google Sheets live-proof hardening for the V3 human workspace.
- Fixed Google Sheets raw snapshot tab collisions by using hidden `_wf_*` physical tab names while preserving canonical logical snapshot names such as `foods`, `recipes`, and `shopping_items`.
- Updated Google Sheets read/write paths so hidden physical tabs round-trip back into the canonical `WonderFoodSnapshotCodec` row model.
- Updated Google Sheets provisioning to repair mismatched existing headers, not only missing tabs/undersized grids. This migrated the disposable scratch Sheet away from old schema.org-style headers that were being misread as identifiers.
- Updated sheet presentation provisioning to grow tiny newly-created tabs before freezing row 1, avoiding the Sheets API `You can't freeze all visible rows` failure.
- Added cached-token reuse to `scripts/quality/run-google-sheets-live-proof.sh` so repeated live proofs do not force browser OAuth when a temporary token file is still valid.
- Added redacted conflict details to live-provider evidence JSON for future troubleshooting.
- Google Sheets live proof passed through the agent-env wrapper against the disposable `WonderFood Sync Test` spreadsheet.
- Google Sheets evidence written to `app/build/evidence/live-workspace/google_sheets-1784484035998.json`:
  - redacted spreadsheet id: `1-cu...f6H4`
  - initialized tabs: `39`
  - exported raw rows: `34`
  - read workspace rows: `46`
  - tables read: `Home`, `Kitchen`, `Recipes`, `Meals`, `Plans`, `Shopping`, `Purchases`, `Foods`, `Recipe Ingredients`, `Recipe Revisions`, `Inventory Activity`, `Shopping Demand`, `Nutrition Facts`, `Activity`, `Workspace`
  - merge changes: `3`
  - merge conflicts: `0`
- Validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.GoogleSheetsGatewayTest' --tests 'com.wonderfood.app.sync.WonderFoodLiveWorkspaceProofTest'`.
- Validation passed: `/Users/srinivasvaddi/.codex/skills/agent-env/scripts/run-with-agent-env.sh scripts/quality/run-google-sheets-live-proof.sh`.
- Final validation passed: `./gradlew :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.GoogleSheetsGatewayTest' --tests 'com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinatorTest' --tests 'com.wonderfood.app.sync.NotionGatewayTest' --tests 'com.wonderfood.app.sync.WonderFoodWorkspaceSnapshotMergerTest' --tests 'com.wonderfood.app.sync.WonderFoodLiveWorkspaceProofTest'`.
- Final validation passed: `./gradlew :app:assemblePlayDebug`.

## Current Implementation Gaps After Slice 16

- Full Notion 2025+ data-source API migration remains future hardening; the current gateway is intentionally pinned to the stable database API version it implements.
- Field-level conflict editing remains future hardening; the current implementation preserves remote data, imports valid changes, persists the conflict inbox, and records conflict details in live proof evidence.
