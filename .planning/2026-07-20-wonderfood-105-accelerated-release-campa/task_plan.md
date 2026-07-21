# WonderFood 1.0.5 Accelerated Release Campaign

## Status

V4 completion campaign is in progress. The old 65/76 rollup is being rebaselined because V3 provider evidence cannot prove the replacement V4 workspace contract.

## Mission

Deliver every requirement and completion gate from the existing WonderFood 1.0.5 zero-user reset, but execute as one parallel release campaign instead of nine sequential implementation phases.

This plan changes sequencing and coordination and removes Supabase from scope. Postgres remains, reached through HTTPS/API or a user-owned service endpoint, not a raw Android database socket.

## Normative Sources

These remain the source of truth. If this execution overlay is ambiguous, the original requirement wins.

- `../2026-07-19-wonderfood-105-zero-user-reset/task_plan.md`
- `../2026-07-19-wonderfood-105-zero-user-reset/architecture.md`
- `../2026-07-19-wonderfood-105-zero-user-reset/schema.md`
- `../2026-07-19-wonderfood-105-zero-user-reset/workspace_product.md`
- `acceptance_matrix.md` in this directory

## Non-negotiables

- No backward compatibility. Delete legacy runtime, dual-write, and bridge paths after callers move.
- SQLite remains fully usable offline in every mode.
- Exactly one data home is active per household.
- Notion and Google Sheets are standalone household products, not dumps or backup views.
- Postgres uses HTTPS/API or a user-owned service endpoint, authenticated users, household membership checks, and no direct DB password/DSN ships in Android.
- AI and imports produce typed proposals; accepted changes use canonical commands.
- One release branch, one final PR, one v1.0.5 release.
- Preserve unrelated dirty-worktree changes and user data.
- No completion claim without all rows in `acceptance_matrix.md` carrying current evidence.

## Hard Resource Guardrails

- Maximum continuous campaign runtime: 6 hours. Stop at the cap and checkpoint; never loop.
- Planned delivery: at most two bounded campaigns of up to 6 hours each.
- Total model-token target: 500,000. Hard stop: 700,000 across coordinator and workers.
- Worker budgets: A 120k, B 120k, C 150k, D 100k; coordinator/integration reserve 210k.
- Credit target: 5%; hard stop at 8% to retain margin below the user's 10% ceiling.
- Check budget at campaign start, 2h, 4h, 5h30m, and before release actions. Do not poll repeatedly.
- A worker with no patch, test result, or concrete blocker after 15 minutes stops and reports once.
- Three attempts maximum per distinct failure; each attempt must change the approach.
- Worker reports are capped at 40 lines: outcome, changed files, tests, blocker, next action.

Credit percentage is not reliably derivable from token count. The coordinator must stop when either the product-reported credit threshold or numeric token threshold is reached.

## Operating Model

Four workers execute concurrently after a single baseline checkpoint. The coordinator owns contracts, shared integration files, merges, budget, acceptance evidence, GitHub, and release actions.

Workers use temporary local branches/worktrees based on the verified checkpoint. They do not open separate PRs. Each worker produces one or more reviewable commits that the coordinator integrates into `codex/wonderfood-105-zero-user-reset`.

### Ownership

| Owner | Exclusive write scope | Primary outcome |
|---|---|---|
| Coordinator | `MainScreenViewModel.kt`, build files, `.planning`, cross-lane wiring, Git/GitHub/release | Stable contracts, merges, final proof |
| Worker A: Canonical Runtime | `core/model`, `core/data`, `core/engine`, `app/data`, legacy runtime deletion, AppFunctions canonical executor | One canonical runtime and mutation boundary |
| Worker B: Android Product | `app/ui` except `MainScreenViewModel.kt`, `app/src/androidTest` | Complete mobile household workflows and rendered proof |
| Worker C: Data Homes | `app/sync`, provider-specific sources, provider proof scripts, Postgres schema/API proof | Shared sync kernel and three complete data homes |
| Worker D: Skills and Release Assets | `core/ai`, AI assets/fixtures/tests, docs except planning, release-note drafts | Seven skills, deterministic fixtures, documentation |

No worker edits another owner's files. Cross-scope changes are requested from the coordinator with an exact API or patch description. See `worker_contracts.md`.

## Contract Freeze

Before parallel edits, the coordinator freezes these APIs for the campaign:

- `HouseholdRepository` snapshot/state and mutation surface.
- `HouseholdCommand` and result/error contract.
- Canonical IDs, quantity, money, archive, provenance, and nutrition rules.
- `DataHomeAdapter` lifecycle and sync result types.
- UI-facing canonical projections used by `MainScreen`.
- Proposal/skill input-output envelope.

Workers may extend these only through a coordinator-owned contract commit. This prevents four divergent models.

## Four Proof Bundles

## V4 Completion Campaign

This is the final integrated campaign; it does not create another phase hierarchy.

| Lane | Owner | Scope | Exit gate |
|---|---|---|---|
| Sheets V4 | Worker S | `GoogleSheetsGateway.kt` and its focused tests only | Native tables, relation dropdowns, metadata/protection repair, and focused tests pass |
| Android proof | Worker U | `MainScreenTest.kt` only plus read-only emulator/device evidence | Connected failure is fixed or reduced to one concrete external blocker; required UI states are reproducible |
| Release audit | Worker R | Read-only repo/GitHub/signing/acceptance audit | Fresh row-by-row list of PASS/TODO with exact commands and blockers |
| Integration and providers | Coordinator | Planning, cross-lane wiring, live Notion/Sheets, full tests, evidence, Git/GitHub/release | V4 provider round trips and final release gates pass; no stale V3 evidence remains |

Execution order:

1. Run all three worker lanes concurrently while the coordinator verifies the integrated baseline and live credentials.
2. Review and integrate worker patches; run focused tests serially to avoid Gradle cache collisions.
3. Create a fresh V4 Sheets workbook and directly inspect Notion and Sheets in Chrome.
4. Complete emulator/S23 proof and rebaseline all 76 rows from current evidence.
5. Run final build/test/secret/doc gates, then PR, CI, signing, tag, release, checksum, and install proof.

Completion rule: implementation can finish autonomously, but signing, CI publication, and release rows remain explicitly blocked if required credentials, device connectivity, or user-owned GitHub actions are unavailable. Do not convert those external blockers into PASS.

## Template Spike Decision

See `template_spike.md`.

Provider implementation target: `workspace_template_mapping.md`.

External Notion and Sheets templates are design references only, not source-of-truth schemas.

- Notion reference: Tim Rawling's recipe/shopping/meal-plan template. Borrow the recipe -> ingredient -> meal plan -> shopping-list mental model, but generate WonderFood-owned databases/properties because public reviews suggest relation fragility.
- Sheets reference: Smartsheet Restaurant Inventory template. Borrow the Kitchen grouping and low-stock formula pattern for pantry/freezer/fridge/paper-goods/cleaning-supplies style household stock.
- Sheets formula reference: Smartsheet Simple Inventory. Borrow reorder/value formulas only; avoid business SKU language.
- Meal-plan reference: LL Home public article. Borrow weekly plan -> grocery list pattern only.

Implementation rule: provider proof must use WonderFood-generated Notion/Sheets workspaces, not copied third-party templates.

### Bundle 1: Canonical App Runtime

Goal: app runs only on canonical SQLite/Postgres-ready model.

- Finish `MainScreenViewModel` cutover from `FoodChatStore`.
- Keep `FoodMemory` only as temporary UI projection if needed.
- Route all writes through `HouseholdCommandExecutor`.
- Persist full canonical household state in Room.
- Keep AppFunctions canonical-command only.
- Delete legacy runtime after callers are gone.

Proof:

- `rg "FoodChatStore|store\\.|readMemory"` has no production authority paths.
- Model, repository, command, AppFunctions tests pass.
- App compile passes.

Covers: A01-A10 and parts of E02/E05/E17.

### Bundle 2: Android Product

Goal: local-first app UX works without provider/account/network.

- First boot offers Local, Notion, Sheets, Postgres; Local is fastest.
- Main tabs are Now, Food, Week, Cart.
- Quick add supports item, recipe, meal, cart line, receipt.
- Kitchen/cart/recipes/meals/receipt/spending flows are backed by canonical state.
- Gesture alternatives, undo, empty/error/conflict states work.
- User-facing terminology is product language, not architecture language: no visible `canonical`, `repository`, `adapter`, `outbox`, `snapshot`, `schema`, or similarly internal terms unless inside diagnostics/dev-only evidence.
- App copy is ready for i18n: visible strings that ship in the app are reviewed for extraction to Android string resources, concatenation/plural pitfalls, and locale-sensitive date/money/quantity wording.
- Every changed user workflow has deterministic behavior tests at the lowest reliable layer: Compose behavior tests for UI state/actions, ViewModel/repository tests for command wiring, Room tests for persistence/restart, and focused journey tests only for cross-screen flows.

Proof:

- Focused Compose/androidTest compile.
- Emulator journey: local onboarding -> add food/non-food -> cart -> recipe -> week -> receipt -> spending -> restart.
- Screenshots.

Covers: B01-B13 and E06-E09.

### Bundle 3: Data Homes: Notion, Sheets, Postgres Only

Goal: exactly one active data home. No Supabase.

- One `DataHomeAdapter`: provision/probe/scan/pull/push/health/disconnect/repair.
- Three-way sync: base/local/remote.
- Notion standalone workspace.
- Sheets standalone workbook.
- Postgres schema/migrations plus HTTPS client/API endpoint plus household membership checks.
- Provider failure never blocks local mode.
- Every data-home workflow has deterministic fake-adapter/schema tests before live proof: Notion/Sheets row transforms, idempotent upserts, renamed/moved columns/properties, conflict decisions, archive/tombstone propagation, retry/repair, and disconnect/local fallback.

Proof:

- Fake adapter suite.
- Deterministic workflow review suite for Notion, Sheets, Postgres, DB persistence, and app sync transitions.
- Live Notion proof.
- Live Sheets proof.
- Postgres integration proof against test endpoint.
- Secret scan.

Covers: C01-C25 simplified by deleting Supabase-specific proof.

### Bundle 4: AI and Release

Goal: seven AI skills produce proposals only; docs/release match proof.

- Finish seven skill contracts/fixtures.
- Add malformed/unsafe/allergy/offline/provider-failure tests.
- Docs only claim verified behavior.
- Run a terminology and wording review across app UI, screenshots, README, FEATURES, CHANGELOG, setup, privacy, and release notes so product copy does not leak implementation terms such as "canonical cart".
- Run an i18n readiness review for app-visible copy, including string resources, plurals, date/time, currency, quantity units, provider names, and error/empty/conflict states.
- Run a deterministic workflow-review audit for every UI, DB, Notion, Sheets, Postgres, AI/import, and release-path change. No workflow is considered release-ready on live proof alone; each must have repeatable local/fake tests that prove the invariant without external accounts.
- Build signed FOSS/Play APKs.
- CI, PR, merge, tag `v1.0.5`, release artifact/checksum.

Proof:

- AI fixture tests.
- Docs claim scan.
- Deterministic workflow test manifest mapping each changed workflow to its local/fake test command and live proof, when applicable.
- Release evidence script.
- Signed APK checksums.
- PR merged and release published.

Covers: D01-D11 and E10-E17.

## Execution Order

1. Finish Bundle 1 first. This unlocks everything.
2. Run Bundle 2 and Bundle 4 in parallel.
3. Run Bundle 3 in parallel except live proof.
4. Final pass: emulator/device/live providers/release.

## Campaign 1: Bundle 1 Cutover

Target: 6 hours maximum. Status: in progress.

### 0:00-0:30 - Baseline and dispatch

- Inspect branch, worktrees, dirty state, open PRs/issues, and active plan.
- Classify existing changes; preserve anything unrelated.
- Run `git diff --check`, canonical focused tests, and `:app:compileFossDebugKotlin`.
- Create one reviewable baseline checkpoint commit for verified v1.0.5 work.
- Create four worker branches/worktrees from that commit.
- Freeze contracts and assign acceptance rows and file ownership.
- Record start time and token/credit baseline once.

Exit gate: every worker starts from the same compiling canonical checkpoint with non-overlapping write scope.

### 0:30-3:30 - Four parallel lanes

Worker A:

- Complete canonical entities, Room persistence, commands, validation, event/audit ledger, sync metadata, tombstones, conflicts, and latest-safety snapshots.
- Replace all production `FoodChatStore`/`FoodMemory` reads and writes with repository state and canonical commands.
- Port AppFunctions with idempotency and fail-closed unsupported actions.
- Delete obsolete runtime, bridges, exporters/importers, and bridge tests after caller removal.
- Prove offline restart, unknown quantity, minor-unit money, non-food nullability, idempotent replay, archive/tombstone, and proposal persistence.

Worker B:

- Finish Local/Notion/Sheets/Postgres onboarding with Local as fastest path.
- Keep `Now`, `Food`, `Week`, and `Cart` as primary destinations.
- Complete global quick add for item, recipe, meal, shopping line, and receipt.
- Complete reversible and accessible Kitchen gestures.
- Complete mixed household Cart, grouping, plan/staple suggestions, recipe ranking, meal gaps, receipt review, and spending summaries.
- Cover empty/populated/error/conflict, light/dark, large-font, landscape, and tablet states.
- Add or verify deterministic tests for each changed UI workflow: initial state, action, undo/retry/error, restart/state restoration where applicable, and accessibility alternative.

Worker C:

- Complete `DataHomeAdapter` provision/probe, initial scan, pull, push, health, disconnect, repair.
- Complete base/local/remote merge, narrow high-risk review, recovery history, outbox, cursors, idempotent batches, bounded retry, and safety snapshots.
- Finish provider-native Notion workspace, Sheets workbook, and Postgres adapter exactly as specified in the acceptance matrix.
- Add or complete fake-adapter and live-proof scripts without exposing credentials.
- Add or verify deterministic provider workflow tests before live runs: schema/projection, pull-before-push, safe merge, conflict review, archive/tombstone, retry/repair, credential failure, and local fallback.

Worker D:

- Implement and verify all seven named WonderFood skills.
- Use typed versioned inputs/outputs, provenance, confidence, warnings, proposal intent, deterministic-first processing, and canonical command parity.
- Add normal, ambiguous, malformed, unsafe, offline, and provider-failure golden fixtures.
- Draft README, FEATURES, CHANGELOG, architecture, privacy, setup, and release-note updates from verified behavior only.

Worker lane gate: focused tests pass and one concise handoff is produced. No full harness inside individual lanes.

### 3:30-4:45 - Coordinator integration

- Integrate A first and resolve canonical contract changes.
- Integrate C and D against the canonical boundary.
- Integrate B and wire `MainScreenViewModel` to canonical repository state.
- Remove duplicate implementations and legacy fallbacks.
- Run compile after each integrated lane; run focused tests only for touched boundaries.
- Update acceptance rows with commit and test evidence.

Exit gate: app compiles; no unresolved ownership conflicts; `rg` finds no production legacy runtime authority.

### 4:45-5:40 - Integrated smoke proof

- Run focused canonical, sync, skill, and app unit bundles once.
- Run the deterministic workflow-review manifest once; every changed UI, DB, Notion, Sheets, Postgres, AI/import, and release workflow must map to a passing local/fake test or an explicit remaining blocker.
- Assemble FOSS and Play debug APKs.
- Start a supported emulator and run the critical household journey:
  onboarding -> add food and non-food -> inventory -> recipe -> meal plan -> cart -> receipt -> spending -> restart.
- Capture screenshots for core destinations and failures.
- Run fake-adapter parity and secret-leak checks.

### 5:40-6:00 - Hard checkpoint

- Stop new implementation.
- Record commits, tests, screenshots, failing acceptance rows, token/credit use, and exact next commands.
- Tear down idle workers.
- Stop at 6 hours even if incomplete. Do not auto-continue.

Campaign 1 completion gate: all implementation rows are complete or each remaining row has one exact blocker and owner. Release proof may remain for Campaign 2.

## Campaign 2: Live Proof and Release

Target: 6 hours maximum. Start only with explicit approval and remaining total budget. Status: pending.

### 0:00-0:30 - Reconcile

- Read only the accelerated plan ledger and unresolved acceptance rows.
- Verify branch/commit state and current credentials/device availability.
- Dispatch only unresolved work; do not repeat completed audits or tests.

### 0:30-2:30 - Parallel live proof and gap closure

- Worker A handles only canonical/runtime defects found by integration tests.
- Worker B handles only rendered UI/accessibility defects.
- Worker C runs real Notion, Sheets, and Postgres round trips: provision/bind, create, remote edit, pull, local edit, conflict, archive, retry, repair, disconnect.
- Worker D finishes skills/fixtures/docs from current verified behavior.
- Visually inspect Notion and Sheets as standalone household products with realistic seed data.

### 2:30-4:00 - Quality and device matrix

- Run `./scripts/quality/android-harness.sh local` once.
- Run the deterministic workflow-review manifest before any live-provider or physical-device claim; live proof can add confidence but cannot replace repeatable tests.
- Run connected tests on supported low and current API levels.
- Review onboarding and main states in light/dark, large font, landscape, and tablet.
- Review user-facing terminology and i18n readiness in the same rendered pass; block release on internal architecture terms visible to normal users.
- Install and exercise the release candidate on the physical phone without clearing user data unless explicitly approved.
- Run negative Postgres cross-household/membership tests and secret scans.

### 4:00-5:00 - Release candidate

- Freeze code after acceptance failures are resolved.
- Finalize docs and release notes from the acceptance matrix.
- Finalize app-visible wording after the terminology/i18n review; internal model terms must stay in source/tests/docs evidence, not the user product surface.
- Attach the deterministic workflow-review manifest to release evidence, including commands, passed/failed status, and any workflows deferred as known blockers.
- Build signed Play and FOSS release APKs.
- Generate checksums and release evidence.
- Run CI-equivalent local gates and push the release branch.

### 5:00-6:00 - PR and release

- Create one final PR with acceptance evidence and known limitations.
- Review CI and fix only release-blocking failures within budget.
- Merge only when every stop condition is cleared.
- Tag and publish v1.0.5; attach APKs and checksums.
- Verify install URL/artifacts and report them.
- If any gate remains, stop with a draft PR/checkpoint. Do not call the release complete.

## Verification Cadence

- Worker level: focused tests once after a coherent patch.
- Integration level: compile after each lane; focused cross-boundary bundle once.
- Product level: one critical emulator journey after integration.
- Release level: one local harness, one connected matrix, one physical-device pass, one live-provider matrix for Notion/Sheets/Postgres, one signed build.
- Do not rerun a passing gate unless code affecting that gate changed.
- Use `rtk` for compressed command output when it preserves failure details.

## GitHub Issue Mapping

- Runtime and Android loop: #40.
- Purchases and spending: #41 and #35.
- Sync and Sheets: #42.
- Sync and Notion: #43.
- Sync and Postgres: #44.
- Skills: #45 plus #26, #27, #29, #31, #33, #34, and #36.
- Android workflows: #11, #13, #18, #28, and #30.
- Release and cross-cutting proof: #5, #6, #7, #15, #37, and #46.

## Completion Rule

The campaign is complete only when:

1. Every row in `acceptance_matrix.md` is marked `PASS` with a current commit plus test, screenshot, provider artifact, CI run, or release artifact.
2. Every original Phase 0-9 requirement maps to at least one acceptance row.
3. Every original stop condition is cleared.
4. The final PR is merged and v1.0.5 release artifacts are published and installable.

## Stop and Block Rules

- Stop immediately at 6 continuous hours, 700k total tokens, or 8% reported credits.
- Stop rather than loop when no material progress occurs for 15 minutes.
- Credentials, account consent, physical-device approval, or provider outage are valid blockers; record exact evidence once.
- Three materially different attempts maximum for one failure.
- Never silently narrow a deliverable, substitute unit evidence for required visual/live proof, or report a planned feature as implemented.

## Errors Encountered

| Error | Attempt | Resolution |
|---|---:|---|
| None during accelerated planning | 0 | N/A |
| V4 graph support-row maps inferred nullable text values | 1 | Added a non-null `requiredText` constructor for required support titles/details; core model tests had already passed. |

## V4 Interlinked Workspace Rewrite

Status: in progress.

- Replace the provider-facing V3 string-row projection with one typed `WorkspaceGraphProjection` built directly from canonical `HouseholdSnapshot`.
- Provision Notion with current data-source APIs in dependency order: sources, properties, rows, relations, rollups/formulas, then verification.
- Provision Sheets as linked native Tables with typed columns, relation dropdowns, hidden stable bindings, developer metadata, protected formula columns, and formula-driven Home summaries.
- Keep daily surfaces free of canonical IDs, revisions, fingerprints, raw source labels, sync timestamps, setup prose, and provider-inappropriate formulas.
- Treat unknown quantity as blank, never zero; keep money canonical in ISO currency minor units and display provider decimals using currency fraction digits.
- Do not migrate or mutate V3 workspaces. Refuse V3 activation with `Workspace upgrade required`, preserve the old workspace, and create a fresh V4 workspace from canonical local state.
- Keep `Recipe Ingredients` and `Purchase Lines` as linked detail sources in Notion and visible detail tabs in Sheets.
- C25 remains TODO until direct Chrome inspection proves live relations, rollups/formulas, clean visible properties, offline standalone workflows, and screenshots for both providers.

Exit gate: focused graph/provider/import tests pass, fresh live Notion and Sheets workspaces pass direct UI review, and no V3 provider projection remains in production Notion/Sheets paths.
