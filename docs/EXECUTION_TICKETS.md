# WonderFood Android 1.0 Execution Tickets

Status: ready for implementation after preflight decisions  
Release boundary: Android app, GitHub repository, Google Play production  
Execution model: four parallel agents plus one coordinating merge process  
Updated: 2026-07-16

## Product Contract

WonderFood 1.0 is a local-first Android food workspace. Users capture food by voice,
photo, receipt, barcode, sharing, or direct UI. AI converts ambiguous input into typed
proposals. A deterministic engine validates and executes commands against local data.

In scope:

- Today, Kitchen, Plan, Recipes, and Shop destinations.
- Page-style food, recipe, meal, plan, shopping, receipt, and calendar-day details.
- Structured recipes, lot-based inventory, nutrition provenance, event history, and undo.
- LiteLLM-compatible AI with user-configurable provider settings.
- Receipt, barcode, voice, Google App Actions, Health Connect, and Android sharing.
- Encrypted export/restore, GitHub release, and Google Play production release.

Out of scope for 1.0:

- iOS, web, cloud accounts, real-time multi-device sync, household collaboration.
- Billing, subscriptions, advertisements, public social feeds, or medical advice.
- Silent AI writes, silent uncertain pantry deductions, or invented nutrition values.

## Definition Of Done

- Unknown nutrition remains unknown; every known value has source and confidence.
- Capture-to-kitchen, recipe-to-meal, and plan-to-actual workflows pass end-to-end tests.
- Every destructive or uncertain mutation is confirmed or undoable.
- Existing personal data migrates or imports without loss.
- App works offline except AI and external nutrition enrichment.
- Portrait, landscape, dark mode, large text, TalkBack, phone, and tablet are verified.
- Release source is tagged on GitHub and matches the signed Play App Bundle.
- Google Play production review is approved and staged rollout completes.

## Preflight: Sequential Coordinator Tickets

These run before the four agents split. They touch global project state and must not run
in parallel.

### WF-PF01 - Preserve Prototype Baseline

- Owner: coordinator
- Depends on: none
- Work: copy the current database/export, record current schema version, build debug APK,
  run existing tests, and retain current screenshots as a visual baseline.
- Acceptance: backup restores on an emulator; build/test outputs and baseline screenshots
  are recorded under `docs/baseline/`; no source or user data is deleted.
- Verify: `./gradlew :app:assembleDebug :app:testDebugUnitTest`

### WF-PF02 - Freeze Product Identity And Scope

- Owner: coordinator with user decision
- Depends on: WF-PF01
- Work: choose final `applicationId`, app name, repository visibility, license, minimum SDK,
  versioning policy, and supported release regions. Record decisions in `docs/adr/`.
- Acceptance: package is no longer `com.example.wonderfood`; identity is approved before
  Play Console app creation; 1.0 scope matches this document.
- Verify: `./gradlew :app:processDebugMainManifest`

### WF-PF03 - Initialize Clean Git Baseline

- Owner: coordinator
- Depends on: WF-PF01, WF-PF02
- Work: harden `.gitignore`, exclude builds, local properties, databases, exports, keys,
  and personal images; initialize Git; scan tracked source for secrets; create baseline tag.
- Acceptance: clean working tree after baseline commit; no secret or personal data is
  tracked; `git status --short` is empty.
- Verify: `git status --short`; `git ls-files` review; source secret scan.

### WF-PF04 - Establish Four-Agent Worktrees

- Owner: coordinator
- Depends on: WF-PF03
- Work: create one worktree/branch per lane, document merge order, and make the coordinator
  the only owner of merges to main.
- Acceptance: all four branches start from the same baseline commit and build independently.
- Verify: run `./gradlew :app:assembleDebug` in every worktree.

## Agent Lanes And File Ownership

### Agent A - Core And Data

Owns:

- `core/model/**`
- `core/engine/**`
- `core/data/**`
- `settings.gradle.kts`, root Gradle files, version catalog, module build files

Does not edit UI, integrations, tests owned by Agent D, or release metadata.

### Agent B - Product UI

Owns:

- `app/src/main/java/<basePackage>/ui/**`
- `app/src/main/java/<basePackage>/feature/**`
- `app/src/main/java/<basePackage>/theme/**`
- UI drawables, strings, previews, and store screenshot compositions

Does not edit database schemas, AI provider code, manifest capabilities, or CI.

### Agent C - AI And Platform Integrations

Owns:

- `core/ai/**`
- `app/src/main/java/<basePackage>/integration/**`
- `AndroidManifest.xml`, Health Connect, App Actions, share/camera adapters
- Integration-specific `res/xml/**`

Does not edit feature UI except integration contracts explicitly assigned by Agent B.

### Agent D - Quality And Release

Owns:

- `**/src/test/**`, `**/src/androidTest/**`
- `docs/**`, `.github/**`, `fastlane/**`, release checklists and Play metadata
- Test fixtures, fakes, benchmark, screenshot, accessibility, and security checks

Does not change production behavior to make a failing test pass without a separate ticket.

## Parallel Execution Rules

1. Start a wave only after all required tickets from earlier waves are merged and green.
2. One agent runs one ticket at a time. One ticket produces one focused commit.
3. Rebase each lane on the wave baseline before starting its next ticket.
4. Only Agent A edits shared Gradle/version-catalog files. Other agents request dependencies.
5. Public contracts are frozen at each wave boundary; changes require a small ADR.
6. Every ticket includes tests or a written reason tests do not apply.
7. Failed verification blocks merge. Agent fixes its own ticket before taking another.
8. Coordinator merges A, then C, then B, then D unless a ticket states another order.
9. No agent deletes or rewrites existing user data, migrations, or another lane's work.
10. Release tickets cannot waive trust, privacy, data-loss, or accessibility failures.
11. Tickets in the same wave never depend on one another. They build against contracts and
    fakes frozen by the preceding gate; the coordinator verifies integration after merging.

## Wave Matrix

| Wave | Agent A | Agent B | Agent C | Agent D | Gate |
|---|---|---|---|---|---|
| 0 | WF-A01 | WF-B01 | WF-C01 | WF-D01 | Architecture and contracts documented |
| 1 | WF-A02 | WF-B02 | WF-C02 | WF-D02 | Modules compile; test harness green |
| 2 | WF-A03 | WF-B03 | WF-C03 | WF-D03 | Domain, Room, UI kit, nutrition contracts stable |
| 3 | WF-A04 | WF-B04 | WF-C04 | WF-D04 | Command, Kitchen, capture, migration foundations green |
| 4 | WF-A05 | WF-B05 | WF-C05 | WF-D05 | Inventory and direct-action behavior verified |
| 5 | WF-A06 | WF-B06 | WF-C06 | WF-D06 | Capture-to-kitchen workflow passes |
| 6 | WF-A07 | WF-B07 | WF-C07 | WF-D07 | Recipe-to-meal workflow passes |
| 7 | WF-A08 | WF-B08 | WF-C08 | WF-D08 | Health, secure config, plan/shop views integrated |
| 8 | WF-A09 | WF-B09 | WF-C09 | WF-D09 | Personal migration and plan-to-actual workflow pass |
| 9 | WF-A10 | WF-B10 | WF-C10 | WF-D10 | Release candidate and GitHub release ready |
| 10 | release fixes | release fixes | release fixes | WF-D11 | Play internal release accepted |
| 11 | test fixes | test fixes | test fixes | WF-D12 | Production rollout complete |

## Agent A Tickets - Core And Data

### WF-A01 - Create Layered Module Skeleton

- Depends on: WF-PF04
- Goal: separate schemas, deterministic behavior, persistence, AI, and UI.
- Work: create `core:model`, `core:engine`, `core:data`, and `core:ai`; move only code needed
  to establish dependencies; enforce `app -> core:*`, `data/ai -> model/engine`, and pure Kotlin
  model/engine modules.
- Acceptance: no Android imports in model/engine; no cyclic dependencies; current app builds.
- Verify: `./gradlew projects`; `./gradlew assembleDebug test`

### WF-A02 - Define Canonical Food Domain And Truth States

- Depends on: WF-A01, WF-C01
- Goal: establish stable nouns shared by UI, commands, AI, and persistence.
- Work: define UUID-based Page, Food, FoodAlias, StockLot, NutritionSnapshot, Recipe,
  RecipeIngredient, RecipeStep, MealPlan, PlanEntry, MealLog, ShoppingItem, Receipt,
  FoodEvent, Relation, Attachment, Source, Confidence, and TruthState models.
- Acceptance: nutrition values are nullable; Unknown is first-class; no default macro values;
  domain serialization round-trips; models contain no framework annotations.
- Verify: `./gradlew :core:model:test`

### WF-A03 - Implement Room V2 Schema And DAOs

- Depends on: WF-A02
- Goal: persist the canonical domain with explicit relations and history.
- Work: create entities, DAOs, indexes, foreign keys, converters, database factory, schema
  export, transaction boundaries, and append-only event/action tables.
- Acceptance: all domain objects persist and observe through Flow; deletes use archive/tombstone;
  foreign-key and uniqueness constraints reject invalid rows.
- Verify: `./gradlew :core:data:test`; exported Room schema inspection.

### WF-A04 - Implement Repository And FoodCommand Engine

- Depends on: WF-A03, WF-C02
- Goal: create the only path that changes product data.
- Work: define typed FoodCommand hierarchy, validators, command executor, repository interfaces,
  risk/confirmation policy, idempotency store, before/after audit record, and undo command.
- Acceptance: AI and UI cannot call DAOs directly; duplicate idempotency keys are no-ops;
  rejected commands make no changes; undo restores prior state through a new event.
- Verify: `./gradlew :core:engine:test :core:data:test`

### WF-A05 - Implement Inventory Lots And Transactions

- Depends on: WF-A04
- Goal: represent actual purchased food without false precision.
- Work: add commands/use cases for add, merge, move, open, consume, discard, correct, archive,
  mark low/out, and put away; support exact quantities and qualitative present/low/out states.
- Acceptance: separate lots retain purchase/expiry/source; zero quantity remains history;
  uncertain consumption proposes usage without decrementing stock.
- Verify: inventory command tests and DAO transaction tests.

### WF-A06 - Implement Structured Recipes And Have/Need Matching

- Depends on: WF-A05
- Goal: make recipes computable instead of free-text notes.
- Work: implement recipe versions, ingredient sections, quantities/units, optional ingredients,
  steps, serving scaling, food alias matching, availability status, missing-item generation,
  cook sessions, proposed deductions, and leftovers.
- Acceptance: original imported text remains attached; scaling is deterministic; have/need
  explains every match; ambiguous units require confirmation.
- Verify: recipe parser fixtures, scaling tests, have/need tests, cook transaction tests.

### WF-A07 - Implement Plans, Meals, Shopping, And Plan-Versus-Actual

- Depends on: WF-A04, WF-A06
- Goal: link intentions, purchases, preparation, and actual consumption.
- Work: implement draft/accepted plans, meal slots, move/skip/replace/eat states, meal logs,
  shopping origins, shopping trips, put-away staging, plan gaps, and actual-meal relations.
- Acceptance: status transitions reject invalid moves; restaurant meals do not touch inventory;
  shopping completion never deletes history; plan adherence uses IDs, not title matching.
- Verify: state-machine and relation tests.

### WF-A08 - Implement Relations, Search, Export, And Restore

- Depends on: WF-A03, WF-A07
- Goal: provide Notion-like linking and recoverable local data.
- Work: add relation queries/rollups, FTS search, encrypted versioned export, dry-run import,
  merge/conflict rules, attachment manifest, and restore transaction.
- Acceptance: search covers foods, aliases, recipes, ingredients, notes, brands, and tags;
  restore is atomic; incompatible versions fail safely; secrets are excluded.
- Verify: export/import round-trip and corruption tests.

### WF-A09 - Migrate Prototype And Personal Seed Data

- Depends on: WF-A03, WF-A08
- Goal: preserve current useful data without shipping it to other users.
- Work: map V1 inventory, groceries, recipes, plans, logs, actions, events, receipts, and
  preferences into V2; convert string recipes through review staging; isolate personal seed
  data behind a private/debug import path.
- Acceptance: migration count report reconciles all source rows; no personal content is in
  release assets; failed conversion remains reviewable rather than dropped.
- Verify: migration test using a copied V1 database and row-by-row reconciliation report.

### WF-A10 - Freeze Release Data Layer

- Depends on: WF-A05 through WF-A09, WF-D04, WF-D05, WF-D09
- Goal: make release upgrades and recovery safe.
- Work: finalize schema version, indexes, query performance, backup rules, retention rules,
  database integrity check, release migration path, and documented recovery procedure.
- Acceptance: debug-to-release and previous-version upgrade preserve data; integrity check
  passes; no destructive migration fallback exists; release DB operations avoid main thread.
- Verify: full core test suite, migration matrix, restore drill, query plan review.

## Agent B Tickets - Product UI

### WF-B01 - Freeze V2 UX And Design System

- Depends on: WF-PF04
- Goal: replace prototype chrome with one coherent visual hierarchy.
- Work: document navigation, page anatomy, density, color semantics, typography, spacing,
  imagery, empty/loading/error/success states, and phone/tablet behavior; create Compose previews.
- Acceptance: five destinations only; no More/AI tab or phone drawer; health is secondary;
  no repeated dashboard counters; destructive actions live in overflow/confirmation.
- Verify: phone, dark, large-font, and tablet preview review.

### WF-B02 - Build App Shell And Navigation

- Depends on: WF-A01, WF-B01
- Goal: establish production navigation before feature screens.
- Work: implement Today, Kitchen, Plan, Recipes, Shop destinations; profile/settings route;
  global search; compact AI action; detail routes; back-stack restoration; adaptive nav rail.
- Acceptance: portrait/landscape work; selected destination restores after process recreation;
  AI is an action, not destination; detail pages deep-link correctly.
- Verify: Compose navigation tests and screenshots at compact/medium/expanded widths.

### WF-B03 - Build Reusable Page And Database Components

- Depends on: WF-A02, WF-B01, WF-B02
- Goal: make every object feel like a linked food page.
- Work: implement PageHeader, PropertyRow, RelationRow, SourceBadge, ConfidenceBadge,
  FoodImage, DatabaseToolbar, Gallery/List toggle, FilterSheet, ActivityTimeline,
  MutationReviewSheet, UndoSnackbar, EmptyState, and confirmation components.
- Acceptance: components use 48dp targets, semantic colors, accessible labels, stable sizing,
  and no nested decorative cards.
- Verify: component previews and screenshot tests.

### WF-B04 - Build Kitchen Views

- Depends on: WF-A03, WF-B03
- Goal: make inventory visual, dense, and actionable.
- Work: add actionable summary, search, saved gallery/list mode, zone/freshness/category filters,
  sort sheet, food/lot cards, use-soon/low/review states, and multi-select non-destructive actions.
- Acceptance: first viewport shows food, not controls; no visible Remove action; images have
  useful fallbacks; filters never wrap into a large panel.
- Verify: populated, empty, loading, 200-item, landscape, and large-text screenshots.

### WF-B05 - Build Food And Stock-Lot Pages

- Depends on: WF-A04, WF-B03
- Goal: expose properties, lots, relations, and safe correction.
- Work: create food page, lot page, nutrition/provenance section, purchase/expiry/opened fields,
  related recipes, transaction history, add/use/move/buy-again actions, edit, archive, and delete.
- Acceptance: every mutation opens review when required; archive/delete are difficult to trigger;
  quantity zero remains visible in history; source/confidence are inspectable.
- Verify: interaction tests for exact, qualitative, unknown, archived, and multi-lot foods.

### WF-B06 - Build Recipes And Cooking Mode

- Depends on: WF-A04, WF-B03, WF-A02
- Goal: make recipe availability and cooking the primary experience.
- Work: create image gallery/list, search/collections, recipe page, structured ingredient editor,
  scaling, have/need, substitutions, steps, timers, notes, versions, last cooked, Cook/Plan/Add
  missing actions, cooking mode, finish summary, deduction review, and leftovers review.
- Acceptance: no recipe data is edited as one text blob; missing items explain source recipe;
  cooking mode stays readable with screen awake and large text.
- Verify: recipe UI tests and cooking-flow screenshots.

### WF-B07 - Build Today

- Depends on: WF-A02, WF-A06, WF-B03
- Goal: answer planned, eaten, and next action without dashboard overload.
- Work: build against the frozen meal-plan and meal-log interfaces while Agent A implements
  persistence; add date/week strip, enabled nutrition summary, meal-slot timeline, next-meal card,
  use-soon rail, pending-review indicator, compact activity context, and contextual quick actions.
- Acceptance: empty sections collapse; Health Connect status never occupies hero space; date is
  prominent only here; planned and actual meals are visibly distinct.
- Verify: empty day, planned day, active cooking, completed day, and large-text screenshots.

### WF-B08 - Build Plan, Calendar-Day, And Shop Views

- Depends on: WF-A07, WF-B03
- Goal: make plans and purchasing one connected workflow.
- Work: implement week/month navigation, meal queue, day slots, draft/accepted controls,
  move/skip/swap/eat actions, day page, grouped shopping checklist, origin/reason labels,
  shopping mode, receipt entry, cart total, bought state, and put-away queue.
- Acceptance: plan gaps generate reviewable shopping items; purchased items do not disappear;
  day page shows plan versus actual and related kitchen/shopping events.
- Verify: plan/shop interaction tests and narrow-phone screenshots.

### WF-B09 - Build AI Sheet, Search, And Settings

- Depends on: WF-B02, WF-B03, WF-C02, WF-C08
- Goal: expose AI and configuration without another dashboard.
- Work: implement collapsed AI action and modal sheet with speak/photo/barcode/receipt/text/share
  actions, contextual shortcuts, proposal review, per-row edits, confidence warnings, history,
  global search UI, and nested settings for taste, allergies, goals, AI, health, data, and theme.
- Acceptance: raw provider fields live under AI Advanced; settings use compact rows and detail
  pages; no More grid; AI sheet never permanently covers content.
- Verify: keyboard/IME, TalkBack, proposal-review, and settings persistence tests.

### WF-B10 - Final Visual, Adaptive, And Store Asset Polish

- Depends on: WF-B04 through WF-B09, WF-D09
- Goal: produce release-quality UI on supported devices.
- Work: replace placeholder imagery, finish light/dark/system themes, motion/haptics hooks,
  edge-to-edge, large-screen list-detail layouts, scroll/app-bar behavior, app icon, feature
  graphic, and final phone/tablet screenshot scenes.
- Acceptance: no clipped controls, overlapping text, blank images, one-note palette, or forced
  orientation; all store screenshots use real app state and no personal secrets.
- Verify: screenshot matrix plus manual review on emulator and Samsung phone.

## Agent C Tickets - AI And Integrations

### WF-C01 - Define AI Skill And Tool Catalog

- Depends on: WF-PF04
- Goal: separate AI interpretation from product logic.
- Work: define versioned skills for inventory, shopping, recipes, meals, planning, preferences,
  receipt parsing, nutrition correction, and navigation; specify input evidence, output command,
  examples, warnings, confidence, confirmation, and unsupported behavior.
- Acceptance: skills emit typed commands only; no skill emits SQL or generic CRUD; destructive
  commands always require confirmation; catalog is editable/versioned without UI code changes.
- Verify: schema validation and golden example review.

### WF-C02 - Implement LiteLLM Structured Proposal Gateway

- Depends on: WF-A01, WF-C01
- Goal: make AI output predictable and provider-independent.
- Work: build against the command-envelope contract frozen by WF-C01 while Agent A implements
  domain types; implement request builder, tool/JSON schema output, provider adapter, timeout, retry,
  cancellation, malformed-output handling, idempotency key, evidence attachment, and local fake.
- Acceptance: malformed or partial output cannot mutate data; provider errors preserve input for
  retry; secrets never enter logs; fake gateway reproduces all skill fixtures.
- Verify: `./gradlew :core:ai:test`

### WF-C03 - Implement Nutrition Provider Chain

- Depends on: WF-A02, WF-C02
- Goal: enrich food without confident fabrication.
- Work: implement local corrected lookup, package label input contract, Open Food Facts barcode,
  USDA search/details, recipe calculation hook, AI estimate fallback, cache, rate limits, and
  provenance mapping.
- Acceptance: resolution order is deterministic; Unknown survives provider failure; user
  correction wins; every snapshot records source ID, serving basis, confidence, and timestamp.
- Verify: provider contract tests using recorded fixtures; offline and rate-limit tests.

### WF-C04 - Implement Receipt, Barcode, Label, And Meal Capture

- Depends on: WF-A03, WF-C02, WF-C03
- Goal: turn images into reviewable evidence and proposals.
- Work: implement Photo Picker/camera contracts, private image storage, receipt/label OCR input,
  barcode scanning, meal-photo attachment, metadata stripping policy, proposal staging, and retry.
- Acceptance: capture never writes inventory directly; user can remove rows or correct fields;
  missing OCR/AI/network leaves a recoverable capture; images follow retention/export policy.
- Verify: fixture captures, permission denial, rotation, process-death, and offline tests.

### WF-C05 - Implement Voice Notes And Google Direct Actions

- Depends on: WF-A04, WF-C02
- Goal: support hands-free direct and AI-assisted operations.
- Work: implement speech capture, deep links, shortcuts/App Actions for open, water, add grocery,
  log meal, start cooking, and shopping list; map low-risk commands directly and ambiguous notes
  through AI; provide confirmation/undo according to command policy.
- Acceptance: no separate chat screen; repeated intents are idempotent; unsupported utterances
  open AI review; direct actions work with app cold, warm, and backgrounded.
- Verify: ADB deep-link suite and instrumented intent tests.

### WF-C06 - Implement Inventory-Aware Recommendation Service

- Depends on: WF-A02, WF-A05, WF-C02
- Goal: rank useful meals instead of returning generic prose.
- Work: build against the frozen recipe-availability port while Agent A implements matching;
  combine availability, expiry, missing count, prep time, nutrition goals, allergies,
  dislikes, repetition, ratings, leftovers, meal slot, and optional activity context; return
  structured candidates with reason codes and follow-up commands.
- Acceptance: allergy exclusions are hard filters; ranking is deterministic before AI wording;
  every suggestion explains why, missing items, time, and nutrition truth state.
- Verify: fixed-scenario ranking tests and adversarial allergy fixtures.

### WF-C07 - Complete Health Connect Integration

- Depends on: WF-A02, WF-C03
- Goal: treat health as optional context and confirmed output.
- Work: build against the frozen meal-log and nutrition interfaces while Agent A implements
  persistence; minimize permissions; write confirmed hydration/nutrition; read only enabled activity,
  energy, steps, or weight context; add client IDs, sync state, retry, unlink, and deletion handling.
- Acceptance: no health data is sent to AI without explicit opt-in; estimated/unconfirmed meals
  are not written; app functions fully without Health Connect.
- Verify: permission denial/revocation, duplicate write, retry, and disconnect tests.

### WF-C08 - Secure Provider Configuration And Production Networking

- Depends on: WF-C02, WF-C03
- Goal: protect credentials and network traffic.
- Work: store provider token with Keystore-backed encryption, redact logs, validate provider URL,
  split debug/release network security, disable unrestricted production cleartext, add certificate
  and HTTP error handling, and exclude credentials from backup/export.
- Acceptance: release manifest disallows cleartext except an explicitly approved configuration;
  token never appears in DB, logs, screenshots, backup, source, or crash output.
- Verify: release manifest inspection, backup inspection, log scan, and network failure tests.

### WF-C09 - Implement Background Enrichment And Android Sharing

- Depends on: WF-C03, WF-C04, WF-C08
- Goal: finish enrichment reliably without blocking normal use.
- Work: add WorkManager jobs for receipt/nutrition enrichment with constraints and backoff;
  implement Android share target for recipe URLs/text/images; store pending work and surface state.
- Acceptance: jobs are unique/idempotent; cancellation works; battery/network constraints are
  respected; shared content becomes a reviewable proposal and survives process death.
- Verify: WorkManager tests and share-intent instrumentation tests.

### WF-C10 - Harden Integrations For Release

- Depends on: WF-C04 through WF-C09, WF-D08, WF-D09
- Goal: close integration failures before release.
- Work: audit permissions, exported components, deep-link validation, provider privacy behavior,
  retries, timeouts, lifecycle cleanup, background limits, offline fallbacks, and release logging.
- Acceptance: no high-severity security finding; every integration has disabled/error/retry state;
  app remains useful when every external service is unavailable.
- Verify: release integration suite, manifest audit, and offline device run.

## Agent D Tickets - Quality And Release

### WF-D01 - Record Baseline Quality And Release Matrix

- Depends on: WF-PF04
- Goal: make regressions and release gates measurable.
- Work: document existing tests, screens, schemas, permissions, build settings, known false data,
  supported devices, required Play forms, and severity policy; capture baseline screenshots.
- Acceptance: every release gate has owner/evidence; P0/P1 definitions are explicit; baseline is
  reproducible from the tagged prototype.
- Verify: independent coordinator review of `docs/quality/`.

### WF-D02 - Build Test Harness, Fakes, And Fixtures

- Depends on: WF-A01, WF-C01, WF-D01
- Goal: let four agents test without real providers or personal data.
- Work: add deterministic clock/UUID, in-memory DB factory, fake repositories, fake AI gateway,
  nutrition/receipt fixtures, sample images, seed builders, coroutine rules, and test data policy.
- Acceptance: fixtures contain no personal or secret data; tests run offline and repeatably;
  production modules can inject all external dependencies.
- Verify: `./gradlew test`; run suite twice and compare results.

### WF-D03 - Test Domain Schemas And Truth Rules

- Depends on: WF-A02, WF-D02
- Goal: lock the product's truth contract before persistence and UI expand.
- Work: test serialization, nullable nutrition, source/confidence, units, statuses, relations,
  archive semantics, invalid states, equality, and schema-version compatibility.
- Acceptance: no unknown food gains default nutrition; invalid state construction fails or is
  rejected; golden serialized commands remain stable.
- Verify: `./gradlew :core:model:test :core:engine:test`

### WF-D04 - Test Room Schema And Every Migration

- Depends on: WF-A03, WF-D02
- Goal: prevent data loss during upgrades.
- Work: add DAO, constraint, index, transaction, migration, rollback-failure, corruption, and
  V1 fixture tests; archive Room schemas as CI artifacts.
- Acceptance: every version-to-latest path passes; no destructive migration; row counts and
  relations reconcile; corrupted/unsupported data fails safely.
- Verify: Room migration test task on emulator and CI.

### WF-D05 - Test FoodCommand Validation, Idempotency, And Undo

- Depends on: WF-A04, WF-D02, WF-D03
- Goal: prove all mutations are safe without UI or AI.
- Work: table-test every command, permission/confirmation level, duplicate key, partial failure,
  transaction rollback, audit entry, and undo/correction behavior.
- Acceptance: direct DAO writes are absent outside data module; rejected/duplicate commands leave
  identical DB state; undo creates history rather than erasing it.
- Verify: `./gradlew :core:engine:test :core:data:test`

### WF-D06 - Automate Capture-To-Kitchen Workflow

- Depends on: WF-A05, WF-B04, WF-B05, WF-C04, WF-D05
- Goal: prove the first magical loop.
- Work: automate receipt/photo/barcode/voice proposal, row correction, confirmation, stock-lot
  creation, shopping origin, put-away, nutrition provenance, duplicate retry, and undo.
- Acceptance: workflow passes offline with fakes and connected with approved test providers;
  no mutation occurs before confirmation; rerun creates no duplicates.
- Verify: connected Android test plus recorded result screenshots.

### WF-D07 - Automate Recipe-To-Meal Workflow

- Depends on: WF-A06, WF-B06, WF-C06, WF-D05
- Goal: prove availability, cooking, consumption, and leftovers.
- Work: automate recipe creation/edit, scaling, have/need, add missing, cook, timers, proposed
  deductions, uncertain correction, leftovers, meal log, nutrition source, and undo.
- Acceptance: exact quantities reconcile; ambiguous quantities wait for confirmation; outside
  meals do not alter inventory; history remains intact.
- Verify: connected Android test and DB reconciliation assertion.

### WF-D08 - Test Health, Voice, Capture, Security, And Offline States

- Depends on: WF-C04, WF-C05, WF-C07, WF-D07
- Goal: verify platform boundaries independently of feature polish.
- Work: create black-box checks against the networking/security contract frozen in Wave 6 while
  Agent C hardens its implementation; test permissions denied/revoked, malformed deep links, duplicate voice actions, provider
  failure, cleartext blocking, token redaction, process death, background retry, and no-network use.
- Acceptance: no crash or leaked secret; app remains navigable and editable offline; sensitive
  actions require expected consent.
- Verify: instrumentation suite, manifest scan, log scan, backup scan.

### WF-D09 - Automate Plan-To-Actual And Run UI Quality Matrix

- Depends on: WF-A07, WF-B07, WF-B08, WF-C07, WF-D07, WF-D08
- Goal: prove daily use and visual/accessibility quality.
- Work: automate draft/accept/move/skip/replace/eat, grocery gaps, shopping, put-away, day history,
  adherence; run screenshots on phone/tablet, portrait/landscape, light/dark, 1.0x/1.5x/2.0x font;
  run TalkBack semantics, contrast, touch-target, startup, scrolling, and jank checks.
- Acceptance: zero P0/P1 findings; no overlap/clipping; primary flows keyboard/TalkBack reachable;
  startup and scroll performance meet documented budgets.
- Verify: full connected test, screenshot diff, accessibility and benchmark reports.

### WF-D10 - Publish Hardened GitHub Repository And Release Candidate

- Depends on: WF-D09
- Goal: make source and artifacts reproducible before Play upload.
- Work: prepare the repository and release pipeline while the other lanes freeze production code:
  secret/personal-data scan, README, architecture, privacy model, build/test guide,
  screenshots, issue/PR templates, security policy, license, Actions build/test/lint, dependency
  review, branch protection, changelog, and release checklist. After the coordinator merges A, C,
  and B, rebase D, run the final suite, and create signed/tagged `v1.0.0-rc1` plus the debug APK.
- Acceptance: clean clone builds offline after dependency cache warm-up; CI green; no key/token/DB
  or personal image tracked; tag points to reviewed commit.
- Verify: fresh-clone build and GitHub Actions run.

### WF-D11 - Prepare Play Listing And Internal Release

- Depends on: WF-D10
- Goal: pass Play Console setup and internal testing.
- Work: create Play app with final package, generate and secure upload key, enroll in Play App
  Signing, build signed AAB, upload internal release, complete listing, icon, feature graphic,
  screenshots, privacy policy, Data safety, Health Apps declaration, content rating, target
  audience, ads, app access, permissions, countries, and release notes.
- Acceptance: internal track installs and upgrades; bundle explorer reports expected permissions;
  all App content forms complete; pre-launch report has no unresolved P0/P1 issue.
- Verify: Play internal install on Samsung phone and emulator-compatible device.

### WF-D12 - Complete Closed Testing And Production Rollout

- Depends on: WF-D11
- Goal: finish public distribution, not merely upload a bundle.
- Work: run required closed test when applicable, collect structured feedback and vitals, file
  defects into owning lanes, verify fixes through new version codes, apply for production access,
  submit review, resolve policy findings, stage rollout, monitor Android Vitals, and archive release
  evidence.
- Acceptance: required tester count/duration is satisfied when applicable; production review is
  approved; staged rollout reaches 100% without P0/P1 regression; GitHub `v1.0.0` matches Play AAB.
- Verify: Play production listing URL, installed production build, signing certificate, version,
  and source-tag reconciliation.

## Ticket Handoff Template

Every agent reports this block when finishing a ticket:

```text
Ticket:
Commit:
Changed paths:
Behavior delivered:
Verification commands and results:
Screenshots/artifacts:
Migration or compatibility impact:
Known limitations:
Follow-up tickets unblocked:
```

## Release Blocker Policy

- P0: data loss, secret/health-data leak, unsafe deletion, release crash, broken migration.
- P1: false nutrition, wrong inventory mutation, unusable primary flow, accessibility blocker,
  policy/review blocker, persistent duplicate actions.
- P2: degraded secondary flow, visual defect without data impact, recoverable integration failure.
- P3: cosmetic improvement or deferred convenience.

No release proceeds with open P0 or P1 tickets. P2 requires explicit coordinator disposition.
