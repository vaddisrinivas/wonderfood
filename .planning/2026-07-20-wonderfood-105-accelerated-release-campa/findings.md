# Accelerated Campaign Findings

## Verified Current State

- Active implementation branch: `codex/wonderfood-105-zero-user-reset`.
- Worktree is dirty with substantial v1.0.5 changes; preserve and verify before parallel branching.
- Tracked diff at plan creation: 3,598 additions and 183 deletions across 27 files, plus many untracked canonical source/test/schema files.
- Phase 0 contracts are reported complete.
- Phase 1 already contains work that belongs conceptually to later phases: canonical purchases/spending projections, adapter contracts, provider import/export, sync conflict slices, UI previews, backups, and connected UI tests.
- Main remaining architectural blocker: production UI/app state and AppFunctions still retain legacy `FoodChatStore`/`FoodMemory` authority.
- Existing local quality entry point: `./scripts/quality/android-harness.sh local`.
- Existing connected quality entry point: `./scripts/quality/android-harness.sh connected`.
- Existing release evidence entry point: `./scripts/quality/collect-release-evidence.sh`.
- Existing live Google Sheets proof helper: `./scripts/quality/run-google-sheets-live-proof.sh`.

## Planning Decision

The old phase status is not a useful execution order because implementation already crosses phase boundaries. Preserve it as the requirements source and run one dependency-aware campaign with four exclusive ownership lanes.

## Efficiency Decisions

- Use autonomous planning mode to avoid per-tool plan recitation.
- Create one verified baseline checkpoint before worker branches; uncommitted state cannot safely fan out to worktrees.
- Freeze shared contracts before parallel edits.
- Keep `MainScreenViewModel.kt` coordinator-owned because runtime, UI, providers, and AI all converge there.
- Run focused tests per lane, then integrated tests and final harnesses once.
- Use one realistic seeded household journey across local, UI, providers, restart, backup, and release evidence.
- Treat live provider, visual workspace, physical device, signing, CI, PR, tag, and release as proof work, not implementation substitutes.

## Known Risks

| Risk | Mitigation |
|---|---|
| Existing uncommitted work cannot be shared safely with worker worktrees | Verify and checkpoint before dispatch |
| Four agents collide in `MainScreenViewModel.kt` | Coordinator owns it exclusively |
| Provider adapters drift into separate models | Freeze canonical snapshot/command and `DataHomeAdapter` contracts |
| Live credentials or consent are unavailable | Check at Campaign 2 start; record blocker once; never expose secrets |
| Emulator graphics failure recurs | Use installed compatible AVD/device; stop after bounded alternatives |
| Repeated full builds consume time and tokens | Focused worker tests; one integration harness per gate |
| Plan says complete while proof is missing | Acceptance matrix requires evidence per row |

## Scope Integrity

No deliverable was removed. The original plan, architecture, schema, workspace product contract, seven skill names, provider semantics, visual/device matrix, GitHub flow, and release publication remain mandatory.

## Template Spike Findings

- Tim Rawling's Notion recipe/shopping/meal-plan template is the closest Notion reference for WonderFood's human workflow, but should not be used as the canonical base because public review evidence reports relation fragility.
- Smartsheet Restaurant Inventory is the best Sheets reference for WonderFood Kitchen because it includes pantry/freezer/fridge plus paper goods and cleaning supplies in one household-stock layout.
- Smartsheet Simple Inventory is useful only for reorder and inventory-value formulas; its SKU/manufacturer/business language does not fit household UX.
- WonderFood should generate its own stable Notion/Sheets databases, tabs, property names, hidden binding tables, formulas, and seed data, while borrowing these templates' human-friendly layout patterns.
- Current provider schema still carries older visible names like `Plans`, `Purchases`, and `Goals`; the mapping spec defines the faster v1.0.5 target as `Meals`, `Spending`, `Needs Review`, and `_wf_*` support tables.

## V4 Workspace Rewrite Findings

- Direct Chrome review invalidated the prior C25 API-derived visual claim: the live Notion workspace exposed `legacy:*` IDs, plain-text relationship hints, duplicated quantity text, a Google Sheets formula string, and internal setup prose.
- `CanonicalHouseholdSnapshotExporter` currently converts `HouseholdSnapshot` into the older `WonderFoodSnapshot`; this loses provider-ready canonical relationships and creates prefixed legacy-style IDs before Notion/Sheets projection.
- `NotionGateway` still targets Notion API `2022-06-28` and creates only simple database properties. Its relation/rollup/template descriptions are prose, not implemented Notion properties.
- The correct insertion point is a typed provider-neutral graph emitted directly from `HouseholdSnapshot`, with provider renderers responsible only for native property/table mechanics.
- Notion visible rows should bind through provider page IDs plus a support binding source; internal canonical IDs must not be visible properties.
- Sheets can keep hidden/protected ID columns because developer metadata and table structure preserve identity through row moves while relation labels remain human-readable.
- Fresh V4 Notion provisioning and export/readback now pass against workspace `3a35dd53-5a93-81fe-bfe3-c6ec1b48bbdd`; focused `NotionGatewayTest` also passes.
- The currently open Google Sheet is blank and is not V4 integration proof. No connected Sheets document-control session is available, and the Google Drive native-import plugin is not installed.
- GitHub reference review identified concrete Sheets gaps: relation fields lack `ONE_OF_RANGE` validation, table ranges/column metadata need reconciliation, spreadsheet reads omit some tables/metadata/protected-range state, and repeated bootstrap can duplicate protections.
- The acceptance matrix still reports 65 PASS / 11 TODO, but C09-C19 evidence must be audited because several notes describe the rejected V3 projection.

## Portable Control Plane Findings

- The missing v5 pillar is not a visual/dashboard issue; it is the control-plane/data-plane split. Config is data, but not household records.
- Existing code already had most hard contracts: typed config sources, fetchers for local/GitHub/URL/Notion/Sheets, additive merge, conflict gating, AI preview/accept/rollback, and SQLite `config_*` tables.
- The main hard gaps found today were fail-safe behavior and recovery proof, not fetcher shape.
- Added evidence now proves invalid/failed remote config keeps the last-good control plane and that workflow run checkpoints survive recovery along with records/config.
- Static separation now has its own gate: config modules cannot write record tables, and provider/data-sync modules cannot write config tables.
- Remaining C4 UI is product-critical but should sit on these contracts: add/reorder/disable source, preview diff, accept/undo, show conflicts/errors/freshness, no token display.

## Provider Writeback Findings

- Direct live provider writeback must prove more than create. It now proves create, update, and archive for Notion and Sheets.
- Notion page archive/trash delivery uses `in_trash: true`; using the older archive field failed live delivery/readback.
- Sheets direct archive is represented as an appended provider write row with archived=true, then proof cleanup clears the appended proof rows.

## Load-bearing Guard Audit Findings

- The AI capability gate is real for current manifests: `DomainManifest.collections` is a string array, so collection scope checks are not a type illusion.
- One-write-path had a create-overwrite hole: `create` on an existing record could mutate without an expected revision if the idempotency key did not catch it. This is now rejected.
- Provider local-copy clear/disconnect directly deletes local provider-owned rows by design. The operation-boundary gate now explicitly allowlists that lifecycle exception so future broad direct deletes are visible.
- Config merge had a precedence semantics bug: scalar disagreements always conflicted. Runtime now tracks path ownership so higher-precedence sources win and equal-precedence disagreements go to review.

## Standalone Provider Authority Findings

- The old `run-provider-standalone-visual-proof.sh` was invalid after the Expo migration because it depended on Gradle test targets from the previous Android app.
- The replacement should be treated as an authority receipt: it proves live Notion and Sheets seed/read/edit/archive/undo/repair plus direct app create/update/archive write delivery under one redacted receipt.
- It should not be used to claim direct manual UX inspection of the user's final Notion/Sheets dashboards; that remains separate product visual proof.
- `check-live-provider-writeback.ts` now supports `PROVIDER_WRITEBACK_OUT`, so composite live receipts can keep all evidence in one directory instead of scattering proof files.
- Provider writeback needs an explicit restore operation. Treating archive Undo as generic update is insufficient because Notion keeps the page in trash unless the write payload sends `in_trash:false`.

## Release Readiness Findings

- Local Android and iOS build/export evidence can be refreshed without release credentials, but a real release remains externally blocked until signing/App Store env exists.
- The release readiness gate should stay non-secret: record env names and boolean readiness, never env values or keystore paths.
