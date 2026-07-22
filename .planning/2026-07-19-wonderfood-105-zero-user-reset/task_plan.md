# WonderFood 1.0.5: Zero-user household architecture reset

## Status

Phase 0 complete. Phase 1 in progress with a canonical household Room/repository foundation added beside the legacy runtime while callers migrate. Provider/CSV projections now preserve canonical nutrition, recipe ingredients, and recipe steps for the covered fixtures; accepted recipe drafts and provider recipe imports now persist canonical recipe-step rows.

## Goal

Ship WonderFood 1.0.5 as a clean local-first household AI workspace: one canonical model, friction-free Android workflows, universal inventory and shopping, receipt-backed spending, and interchangeable Local, Notion, Google Sheets, and Postgres/Supabase data homes. The Notion and Google Sheets homes must each remain useful as complete household products when the Android app is closed or uninstalled.

## Zero-user rule

There are no production users whose data or integrations require compatibility. Prefer deletion and replacement over adapters, dual writes, legacy migrations, or deprecated fields.

- Replace `FoodMemory` and legacy exporters/importers with the canonical repository.
- Use a destructive Room schema reset during development.
- Provision fresh Notion, Sheets, and Postgres proof workspaces.
- Keep the `v1.0.4` tag and release artifacts as rollback evidence, not as a schema constraint.

## Product contract

- WonderFood is the fast Android and AI interface.
- Notion and Google Sheets are independent household interfaces, not exports, backups, or sync dumps.
- A connected household may operate entirely in its chosen workspace; WonderFood adds mobile speed, offline access, capture, normalization, matching, and AI assistance.
- SQLite always powers the app offline and stores the outbox, sync bases, conflicts, and recovery snapshots.
- Exactly one data home is active per household.
- Local mode uses SQLite as the only durable home.
- Notion, Sheets, or Postgres mode uses that provider as the shared durable home while SQLite remains the offline working replica.
- Remote human edits are first-class. Ordinary edits import automatically; only overlapping high-risk edits require review.
- AI and imports produce typed proposals. They never write directly to SQLite or a provider.
- Food remains the richest domain, but inventory, shopping, purchases, and expenses support any household item.

## Release boundary

Included in 1.0.5:

- Canonical-model reset and one runtime repository.
- Friction-free onboarding and core actions.
- Food and non-food inventory.
- Universal shopping and meal-plan gap generation.
- Receipts, purchase lines, prices, and spending summaries.
- Real two-way Notion, Google Sheets, and Postgres/Supabase adapters.
- Seven WonderFood capability skills.
- Emulator, physical-device, live-provider, CI, signed-release proof.

Not included in 1.0.5:

- Bank-account aggregation or financial advice.
- Public social/community hosting.
- A hosted 180,000-recipe catalog owned by WonderFood.
- Simultaneous fan-out sync to multiple data homes.
- Direct PostgreSQL socket/DSN access from Android.
- Backward-compatible migration from the legacy runtime schema.

## Delivery shape

Use one release branch and one final PR, but keep each phase as a reviewable commit. Do not begin the next phase until the preceding phase's focused acceptance checks pass.

## Phase 0: Freeze invariants and remove compatibility assumptions

Status: complete

Work:

- Approve `schema.md` and `architecture.md` as implementation contracts.
- Approve `workspace_product.md` as the independent Notion/Sheets product contract.
- Define IDs, quantities, money, timestamps, archive behavior, revisions, provenance, and the deliberately narrow conflict rules once.
- Mark legacy `FoodMemory`, snapshot bridges, and raw snapshot exports for deletion.
- Inventory the affected command, storage, UI, provider, backup, and test surfaces.

Acceptance:

- Every visible object maps to exactly one canonical entity or projection.
- Every mutation enters through one command/repository boundary.
- No plan step relies on preserving legacy IDs, tables, or serialized payloads.

Evidence:

- `core:model` now contains canonical household entities, workspace field ownership/risk, conflict policy, runtime surface inventory, and sync/outbox/recovery contracts.
- `HouseholdContractTest` verifies object/projection coverage, command-boundary intent, no non-food food details, unknown quantity semantics, purchase-line spending, data-home conflict rules, adapter lifecycle operations, schema-drift rejection, outbox limits, and conflict evidence retention.
- Verified `./gradlew :core:model:test --tests com.wonderfood.core.model.household.HouseholdContractTest`: BUILD SUCCESSFUL on 2026-07-20.

## Phase 1: Replace the runtime with the canonical household model

Status: in_progress

Work:

- Generalize `Food` into `Item` with an item kind and optional food details.
- Implement the canonical Room entities, DAO, repository, command handlers, and event ledger.
- Replace `FoodChatStore` persistence with repository-backed state flows.
- Remove duplicate legacy models, exporters, importers, and bridge tests after their callers migrate.
- Reset development databases to the new schema.

Acceptance:

- App launch, state, commands, backup, and search use the canonical repository.
- No production code reads or writes legacy `FoodMemory` domain records.
- Nullable food-only fields stay absent for non-food items.
- Restarting offline preserves all canonical entities and pending proposals.

Progress:

- Added a public `HouseholdRepository` boundary for household mutations.
- Added a `core:engine` `HouseholdCommand` / `HouseholdCommandExecutor` boundary and made the Room repository implement it.
- Added Room v3 household tables for households, items, inventory lots, shopping lines, proposals, command records, and sync outbox.
- Added Room v3 canonical recipe-root persistence and an `UpsertRecipe` command.
- Added Room v4 canonical recipe-ingredient persistence and an `UpsertRecipeIngredient` command.
- Added Room v5 canonical shopping-line provenance persistence for meal-plan and recipe-gap source links.
- Added Room v6 canonical nutrition-snapshot persistence and an `UpsertNutritionSnapshot` command.
- Added Room v7 canonical recipe-step persistence and an `UpsertRecipeStep` command.
- Added `RoomHouseholdRepository` with idempotent command recording and snapshot reads for the implemented entities.
- Added canonical repository item search for food and non-food items.
- Added an app draft-to-household command mapper for inventory, grocery, and receipt lines.
- Wired accepted app drafts to mirror supported canonical household commands into `HouseholdRepository`.
- Added app startup seeding for the canonical household and a Settings-visible canonical repository summary.
- Added a Now-dashboard canonical household read metric for the primary app surface.
- Added a read-only canonical Cart preview above the legacy Cart list.
- Added canonical Cart preview actions for marking canonical shopping lines bought or archived.
- Added a read-only canonical Kitchen preview above the legacy Kitchen list.
- Added canonical Kitchen preview actions for adding canonical items to Cart or archiving canonical kitchen items/lots.
- Routed the Food > Can make recipe ranking through canonical recipes, canonical recipe ingredients, and canonical inventory lots when canonical matches exist.
- Routed the global Search pane's typed kitchen/item matches through `HouseholdRepository.searchItems(...)` and renders canonical item rows separately from legacy detail-only results.
- Switched local encrypted backup creation to package the canonical household Room database and a `wonderfood.household-backup.v105` manifest; encrypted restore now recognizes the canonical database filename and the ViewModel closes/reopens Room around restore.
- Switched Google Drive backup payloads, restore safety backups, and backend-switch safety backups to canonical household snapshots and the canonical Room database.
- Switched production CSV export to read the canonical household snapshot and export canonical item, inventory-lot, and shopping-line rows.
- Preserved canonical inventory and meal-log nutrition in CSV export/parse instead of dropping kcal/macros.
- Preserved canonical recipe ingredients in CSV export/parse instead of exporting recipe roots only.
- Routed inventory/grocery-only CSV imports directly through canonical household commands.
- Routed recipe-root-only CSV and provider import-review data directly through canonical household commands.
- Switched production Google Sheets, Notion, hosted Postgres, and debounced backend snapshot exports to derive provider payloads from the canonical household repository.
- Projected canonical item/meal-entry nutrition snapshots into provider export payloads instead of dropping nutrition.
- Linked canonical recipe nutrition snapshots from provider recipe rows instead of exporting them as orphan nutrition records.
- Projected canonical recipe ingredients into provider export payloads instead of exporting recipe roots only.
- Routed supported provider import-review snapshots with only inventory/shopping data directly through canonical household commands.
- Routed accepted reviewed proposal drafts that contain only inventory, shopping, or receipt rows directly through canonical household commands for external, AI review, receipt, manual, local fallback, Google Assistant, and voice auto-accept origins.
- Routed accepted recipe drafts to canonical `UpsertRecipe` root commands for all canonical-supported origins.
- Routed accepted recipe draft ingredient lines to canonical `UpsertRecipeIngredient` commands linked from their canonical recipe roots.
- Routed accepted recipe draft step lines to canonical `UpsertRecipeStep` commands linked from their canonical recipe roots.
- Routed accepted meal-plan grocery hints to canonical `UpsertShoppingLine` recipe-gap commands with meal-plan and meal-entry provenance.
- Scoped accepted meal-log and meal-plan canonical command identities by date/week/content, preventing repeated titles from collapsing into one canonical entity.
- Made canonical draft/import application fail closed when any household command is rejected, instead of reporting success after partial or ignored command failures.
- Routed accepted meal-log nutrients into canonical `NutritionSnapshot` records linked from their canonical meal entries.
- Routed accepted inventory/provider food nutrients into canonical item-linked `NutritionSnapshot` records.
- Rendered linked canonical meal-entry nutrition snapshots in the Week preview and proved the row on emulator.
- Added connected UI proof that canonical meal-plan recipe-gap shopping lines render in Cart from `HouseholdRepository`.
- Added focused repository tests proving command idempotency, non-food item persistence, unknown quantity semantics across restart, pending proposal storage, outbox staging, and canonical item search.
- Verified `./gradlew :core:data:testDebugUnitTest`, `./gradlew :core:engine:test --tests com.wonderfood.core.engine.HouseholdCommandExecutorTest`, `./gradlew :core:model:test --tests com.wonderfood.core.model.household.HouseholdContractTest`, `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.data.CanonicalHouseholdSearchItemTest --tests com.wonderfood.app.data.CanonicalHouseholdUiSummaryTest`, `./gradlew :app:testFossDebugUnitTest --tests com.wonderfood.app.sync.WonderFoodCanonicalBackupGatewayTest --tests com.wonderfood.app.sync.WonderFoodBackendSwitchSafetyBackupTest`, `./gradlew :app:assembleFossDebug`, `./gradlew :app:assembleFossDebugAndroidTest`, and `./gradlew :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wonderfood.app.ui.main.MainScreenTest#aMainScreenShowsFiveDestinationShell`: BUILD SUCCESSFUL on 2026-07-20.

Remaining:

- App launch/read state, CSV/provider/external import for recipes/meals/plans/preferences/nutrition, and remaining provider schema internals still need to move from `FoodChatStore`/`FoodMemory` to `HouseholdRepository`.
- The Settings summary proves the canonical repository is initialized and visible, but the main app still reads legacy `FoodMemory` for most screens.
- The new canonical search connected test compiles into the instrumentation APK, but its live execution still needs a working emulator/device; the local AVD crashed during graphics initialization on this continuation.
- The current draft mirror is transitional; final Phase 1 still requires removing legacy write authority rather than keeping dual writes.
- Legacy runtime files and bridge tests are still present until callers migrate.

## Phase 2: Build the friction-free household loop

Status: planned

Work:

- First boot offers Local, Notion, Google Sheets, and Postgres/Supabase with Local as the fastest path.
- Keep primary destinations `Now`, `Food`, `Week`, and `Cart`.
- Add persistent global quick-add access for item, recipe, meal, shopping line, and receipt.
- Kitchen gestures: swipe left archives/sets on-hand to zero; swipe right adds or restores a shopping line; both expose undo.
- Cart supports any item, optional store, category grouping, and separate recipe-gap versus household-staple suggestions.
- Recipe cards rank `Can make`, `Almost`, and `Need more` from inventory quantities and expiry.
- Meal planning produces reviewed shopping gaps with servings, leftovers, and inventory subtraction.

Acceptance:

- Common actions start in one tap and complete without visiting Settings.
- User can add batteries, soap, medicine, or toilet paper without nutrition UI.
- User can add food with lot, expiry, nutrition, and storage details.
- Mixed household cart works without a merchant.
- Swipe actions are reversible and accessible without gestures.
- Meal-plan shopping quantities retain provenance to meal and recipe ingredients.

## Phase 3: Add purchases, receipts, and useful spending

Status: planned

Work:

- Parse or manually create purchases and purchase lines.
- Reconcile subtotal, tax, discount, and total without inventing missing values.
- Link purchase lines to items, shopping lines, and created inventory lots when applicable.
- Support food, household, cleaning, personal care, medicine, pet, and other spending categories.
- Add manual purchases, corrections, returns/refunds, and uncertain-category review.
- Show this month, last month, weekly average, category, merchant, food versus non-food, and known waste cost.
- Use prior purchase-line prices for cart estimates.

Acceptance:

- A mixed receipt can create food lots, non-food stock, ignored lines, and expenses atomically after review.
- Purchase totals reconcile or visibly explain the difference.
- Spending remains correct across corrections and refunds.
- Unknown price/category stays unknown and reviewable.
- No bank credentials or financial-account data are requested.

## Phase 4: Implement the shared sync kernel

Status: planned

Work:

- Add `DataHomeAdapter` contracts for provision/probe, initial scan, pull, push, health, disconnect, and repair.
- Store remote bindings, sync bases, cursors, outbox records, tombstones, and conflicts locally.
- Use lightweight three-way comparison: last synced base, current local, current remote. Do not build CRDTs, vector clocks, multi-home fan-out, or a general merge editor.
- Pull before push. Auto-import one-sided remote edits, auto-push one-sided local edits, and auto-merge disjoint safe fields.
- Require review only when both sides changed the same quantity, money, archive/delete state, recipe ingredient relation, meal date/servings, or another validated high-risk field.
- Resolve ordinary text, notes, tags, and display metadata with deterministic provider-home precedence while retaining the displaced value in local recovery history.
- Push idempotent batches and retry with bounded backoff.
- Create `latest-safety` before initial attach, provider switch, remote replace, or bulk conflict resolution.
- Allow only one active data home and retain a local snapshot when disconnecting.

Acceptance:

- The same adapter contract passes against a fake provider.
- Offline writes survive process death and push once after reconnection.
- Replayed pushes do not duplicate records.
- Remote archive/delete behavior is deterministic.
- Conflicts retain both values, base value, source, timestamp, and proposed actions.
- The primary app never exposes a general conflict-management destination; unresolved high-risk records appear as a small `Needs review` inbox.
- A failed provider never prevents local use.

## Phase 5: Make Notion a real household workspace

Status: planned

Work:

- Provide a duplicable WonderFood Notion template because API-created views are not sufficient for polished workspace setup.
- Make `WonderFood Home`, `Kitchen`, `Shopping`, `Meals`, `Recipes`, and `Spending` the six household-facing surfaces. Keep advanced lot, ingredient, purchase-line, binding, and sync records out of the daily navigation.
- Ship useful linked views: Today, Use first, Low stock, Buy next, This week, Can make, Almost, This month, Last month, and Needs review.
- Let a household add/edit/archive kitchen items, build a mixed cart, plan meals, save recipes, and record purchases entirely in Notion.
- Keep identifiers and machine metadata unobtrusive; expose household-friendly names, filters, relations, rollups, buttons, templates, charts, and realistic seed data.
- Track user-authored convenience fields such as `Buy next`, free-text recipe ingredients, and quick purchase totals as valid workspace inputs that normalize into canonical commands when the app next connects.
- Track data-source and property IDs so user renames do not break sync.
- Support a property whitelist for human edits and reviewed import.
- Upsert pages by stable WonderFood identifier; never append duplicates on routine sync.
- Use an integration token for this no-deployment release; keep public OAuth/broker work outside 1.0.5.

Acceptance:

- A household member can use the Notion workspace without opening WonderFood.
- The home dashboard and core views continue to work with Notion formulas, relations, rollups, buttons, templates, and charts when WonderFood is offline indefinitely.
- App-only enhancements are clearly limited to AI, receipt/URL capture, inventory matching, normalization, and cross-provider sync; basic household work is never app-gated.
- Live proof covers provision/bind, app create, Notion edit, app pull, app edit, conflict, archive, retry, and repair.
- Relations work because every related data source is shared with the connection.
- Notion renaming a visible property does not sever its mapping.
- No token, sync base, raw snapshot, or private AI prompt appears in human databases.

## Phase 6: Make Google Sheets a real household workbook

Status: planned

Work:

- Use Google OAuth plus create/select/paste-URL onboarding.
- Build visible `Home`, `Kitchen`, `Shopping`, `Meals`, `Recipes`, `Spending`, and `Lists & Help` tabs. Keep technical normalization tables hidden.
- Use Sheets Tables, typed columns, dropdowns, checkboxes, validations, named ranges, saved table views, filters, conditional formatting, protected app-owned columns, developer metadata, charts, and formula-driven summaries.
- Let a household add/edit/archive kitchen items, use a mixed cart, plan meals, save recipes, and record purchases entirely in Sheets with no Apps Script or deployment.
- Put sync internals only in hidden `_wf_meta`, `_wf_lots`, `_wf_ingredients`, `_wf_purchase_lines`, and `_wf_bindings` tabs where necessary.
- Treat formulas and user-added columns as user-owned. App writes use metadata-bound rows and a field whitelist rather than replacing visible ranges.
- Support whitelisted edits, reviewed import, stable row identity, and idempotent batch updates.
- Seed the workbook with realistic mixed household data for visual proof.

Acceptance:

- The workbook is immediately readable and useful after setup.
- Home metrics, low-stock/expiry suggestions, weekly meal views, active cart, and monthly spending continue to work from formulas when WonderFood is offline indefinitely.
- No Apps Script, web app, connector deployment, or always-running process is required for standalone workbook use.
- Sorting, filtering, and moving visible rows does not break identity.
- Live proof covers create, edit, pull, conflict, archive, retry, and repair.
- Formulas and unsupported user columns survive routine app sync.
- No OAuth token, provider secret, or private AI prompt is written to the workbook.

## Phase 7: Ship the Postgres/Supabase adapter

Status: planned

Work:

- Provide versioned SQL migrations matching the canonical model and sync envelope.
- Connect Android through HTTPS APIs with a publishable client key and authenticated user session.
- Require `household_id` ownership and membership on every household row.
- Enable and test RLS on every exposed table; never ship a service-role token.
- Implement cursor pull, idempotent upsert, tombstones, conflicts, health, and schema-version checks.
- Use Realtime only as an optimization after pull/push correctness; polling/cursors remain sufficient.
- Treat arbitrary PostgreSQL DSNs as server-side deployment configuration, not Android credentials.

Acceptance:

- Cross-household reads and writes fail in negative tests.
- A client cannot change row ownership or household identity.
- Live proof covers offline queue, reconnect, concurrent edit, archive, expired auth, and schema mismatch.
- Disconnect leaves the full local replica usable.

## Phase 8: Implement the WonderFood capability skill pack

Status: planned

Implementation order:

1. `wonderfood-pantry-normalize`
2. `wonderfood-cart-builder`
3. `wonderfood-receipt-parse`
4. `wonderfood-recipe-import`
5. `wonderfood-meal-plan`
6. `wonderfood-nutrition-estimate`
7. `wonderfood-cooking-coach`

Work:

- Give every skill versioned typed input/output, provenance, confidence, warnings, and proposal intent.
- Run deterministic parsing, lookup, arithmetic, dedupe, and policy checks before AI.
- Route accepted proposals through the same canonical commands as manual UI.
- Map Schema.org `Recipe`, `HowToStep`, `NutritionInformation`, `Product`, `Offer`, and `Organization` at import/export boundaries only.
- Add golden fixtures for normal, ambiguous, malformed, unsafe, offline, and provider-failure cases.
- Record provider/license/privacy notes for any reused library or API.

Acceptance:

- No skill persists directly or bypasses validation.
- Equivalent manual, deterministic, shared, and AI input produces equivalent canonical commands.
- Allergy and hard dietary exclusions fail closed.
- Quantity, nutrition, and cost uncertainty remains visible.
- Skills work with every active data home because they depend only on the canonical repository.

## Phase 9: Product and release proof

Status: planned

Work:

- Run focused model/repository/command/sync/skill tests after each phase.
- Run the full local Android quality harness.
- Run connected tests on supported low and current API levels.
- Render and review onboarding, populated/empty/error/conflict, light/dark, large-font, landscape, and tablet states.
- Complete live round trips with real Notion, Sheets, and Supabase workspaces.
- Install and exercise the release candidate on the physical phone.
- Update README, FEATURES, CHANGELOG, architecture docs, privacy disclosures, setup guides, and release notes.
- Produce signed Play and FOSS APKs with checksums and workflow evidence.

Acceptance:

- The acceptance matrix in `architecture.md` is fully evidenced.
- No release note calls Notion, Sheets, or Postgres a foundation, dump, scaffold, or partial sync.
- Local mode remains fully useful without network, AI, account, or provider.
- The signed release installs, launches, restarts, and preserves its new canonical state.

## Stop conditions

Do not call 1.0.5 complete while any of these remain:

- A production path still uses the legacy runtime model.
- A provider is export-only, import-only, append-only, or snapshot-dump based.
- Notion or Sheets proof is only API payload/unit-test evidence without visual inspection.
- Supabase can expose another household or requires a service-role token on Android.
- AI can mutate without an editable proposal.
- Local mode depends on network or credentials.
- Required emulator, physical-device, live-provider, CI, signing, or documentation evidence is missing.

## GitHub issue mapping

- #40: Phases 1-2.
- #41 and #35: Phase 3.
- #42: Phases 4 and 6.
- #43: Phases 4 and 5.
- #44: Phases 4 and 7.
- #45 plus #26, #27, #29, #31, #33, #34, #36: Phase 8.
- #11, #13, #18, #28, #30: Phase 2.
- #5, #6, #7, #15, #37, #46: Phase 9 and cross-cutting acceptance.

## Errors encountered

None during planning.
