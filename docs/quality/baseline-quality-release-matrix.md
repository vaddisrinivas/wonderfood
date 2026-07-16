# WonderFood Baseline Quality And Release Matrix

Status: Wave 0 baseline  
Ticket: WF-D01  
Recorded: 2026-07-16  
Scope: quality and release documentation only

## Purpose

This matrix turns the prototype baseline into measurable release gates. It documents
what exists now, what must be evidenced later, and which severity levels block release.

This file is intentionally evidence-oriented. Ignored baseline artifacts such as APKs,
SQLite databases, and screenshots remain untracked; the tracked references live in:

- `docs/baseline/BASELINE.md`
- `docs/baseline/baseline-sha256.txt`
- `docs/baseline/db/schema-v6.sql`

## Reproducible Baseline

| Item | Baseline |
|---|---|
| Prototype tag | `prototype-baseline-v0` |
| Prototype commit | `ef2d1f2503c9b901651148167fed1d45c8aab2b0` |
| Current preflight commit | `ed910c2` (`Document four-agent worktree setup`) |
| Baseline command | `./gradlew :app:assembleDebug :app:testDebugUnitTest` |
| Baseline result | Passed, per `docs/baseline/BASELINE.md` |
| Prototype package | `com.example.wonderfood` |
| Current release package | `com.wonderfood.app` |
| Prototype DB | `wonderfood.db`, schema version `6` |

To reproduce the baseline in a clean clone, check out `prototype-baseline-v0`, run the
baseline command, and compare generated artifact hashes to
`docs/baseline/baseline-sha256.txt`. Then apply the current preflight commit notes:
`ed910c2` records the four worktrees and merge rules without changing production app
behavior. The untracked artifact paths intentionally stay ignored by `.gitignore`:

- `docs/baseline/apk/`
- `docs/baseline/db/*.db`
- `docs/baseline/screenshots/`

## Existing Test Inventory

| Area | Existing coverage | Evidence | Gaps |
|---|---|---|---|
| Local unit tests | `FoodInterpreterTest` covers inventory, grocery, meal-log, and meal-plan draft interpretation. | `app/src/test/java/com/wonderfood/app/ai/FoodInterpreterTest.kt` | No command engine, repository, migration, serialization, coverage, or property tests yet. |
| Instrumented storage tests | `FoodChatStoreTest` applies inventory, meal-log, and meal-plan drafts into SQLite-backed memory. | `app/src/androidTest/java/com/wonderfood/app/data/FoodChatStoreTest.kt` | No migration matrix, corruption, rollback, foreign-key, or fixture reconciliation tests yet. |
| Instrumented UI tests | `MainScreenTest` smoke-checks the main Compose shell, AI composer, receipt affordance, and More panel. | `app/src/androidTest/java/com/wonderfood/app/ui/main/MainScreenTest.kt` | No full navigation, state restoration, accessibility, screenshot, or end-to-end workflow tests yet. |
| Frameworks present | JUnit4, Kotlin coroutines test, AndroidX Test core/ext/runner, Espresso, Compose UI test JUnit4. | `gradle/libs.versions.toml`, `app/build.gradle.kts` | No DI test framework, mocking framework, Robolectric, screenshot framework, UIAutomator, benchmark, or Jacoco yet. |
| UI stack | Compose-only app shell. | `app/build.gradle.kts`, `MainScreen.kt` | No XML view behavior tests needed at baseline. |

## Screen And Flow Baseline

| Surface | Current baseline | Evidence to preserve or expand |
|---|---|---|
| Main sections | Today, Kitchen, List, Recipes, More. | Screenshot diff plus Compose UI navigation tests. |
| Detail pages | Inventory, grocery, recipe, meal, plan, calendar day, receipt target. | Navigation tests for open, close, back, and restoration. |
| Today | Meal calendar, calorie/protein/water/shop/cook/ledger metrics, plan and meal-log cards. | Plan-to-actual workflow evidence. |
| Kitchen | Fridge/freezer/pantry inventory, filters, sort, search, item detail, delete. | Inventory behavior and no-data-loss evidence. |
| List | Grocery list, mark bought, delete, grocery-to-inventory movement. | Shopping and put-away evidence. |
| Recipes | Recipe grid, search/filter/sort, recipe detail, cook, add missing groceries, edit, image attach. | Recipe-to-meal evidence and DB reconciliation. |
| More | Taste, AI, Voice, Health, Local Data, Appearance, Stores panels. | Settings persistence, permission denial, and privacy evidence. |
| AI composer | Text prompt, receipt photo attach, voice note, draft accept/reject. | No silent mutation, retry, duplicate, and provider-failure evidence. |
| App Actions/deep links | Open sections, log water, add grocery, log meal, start/done shopping, start/done cooking. | Deep-link parsing, malformed URI, duplicate action, and consent tests. |

Baseline screenshot evidence is referenced by
`docs/baseline/baseline-sha256.txt`. Later screenshot suites should add reports and
diff summaries under `docs/quality/` or CI artifacts, not commit generated PNGs unless a
later ticket explicitly changes the artifact policy.

## Schema Baseline

| Schema item | Baseline |
|---|---|
| Persistence | `SQLiteOpenHelper`, database name `wonderfood.db`, version `6`. |
| Schema dump | `docs/baseline/db/schema-v6.sql` |
| Tables | `chat_messages`, `inventory_items`, `grocery_items`, `recipes`, `meal_logs`, `meal_plans`, `meal_plan_entries`, `receipt_captures`, `user_preferences`, `chat_actions`, `inventory_transactions`, `food_events`. |
| Prototype baseline row counts | `inventory_items`: 42, `recipes`: 11, `meal_plans`: 1, `chat_messages`: 1, `grocery_items`: 0, `food_events`: 0. |
| Missing release evidence | Room schema exports, migration tests, rollback/corruption tests, fixture reconciliation, and personal-data exclusion checks. |

Schema risks to carry into later gates:

- `meal_logs` stores calories, protein, carbs, and fat as non-null values.
- Inventory and grocery nutrition fields are nullable in schema but currently filled by local estimates when not supplied.
- Prototype personal seed data is enabled in source and appears in the baseline DB; release builds must prove personal data is absent.
- Hard deletes exist for inventory, grocery, recipe, and meal logs at baseline; release policy requires safe archive, undo, or explicit acceptance before production.

## Permissions And Platform Baseline

| Area | Current setting | Release evidence required |
|---|---|---|
| Internet | `android.permission.INTERNET` | Network/security contract, cleartext disabled or justified, provider failure handling, no secret logging. |
| Health Connect | `android.permission.health.WRITE_NUTRITION`; app writes `NutritionRecord`. | Health Apps declaration, permission denial/revocation tests, health-data privacy review, export correctness evidence. |
| Activity export | `MainActivity` exported for launcher and deep links. | Manifest/deep-link scan, malformed link tests, no unsafe mutation from external intents. |
| Cleartext traffic | `android:usesCleartextTraffic="true"` | P0/P1 disposition before release; secure config evidence. |
| Backup | `android:allowBackup="true"` plus backup/data extraction XML. | Backup scan proving secrets, personal seed data, and health/AI credentials are excluded or intentionally handled. |
| AI settings | LiteLLM URL/model/API key stored in app SharedPreferences. | Secret-at-rest decision, redaction/log scan, backup exclusion, and privacy disclosure. |
| Shortcuts/App Actions | Static shortcuts and custom capabilities in `res/xml/shortcuts.xml`. | Assistant/App Actions verification and duplicate-action tests. |

## Build And Dependency Baseline

| Item | Value |
|---|---|
| Module graph | Root project plus `:app` only. |
| Android Gradle Plugin | `9.0.1` |
| Kotlin / Compose compiler | `2.3.20` |
| Compose BOM | `2026.03.01` |
| Compile SDK / target SDK / min SDK | `36` / `36` / `26` |
| Namespace / application ID | `com.wonderfood.app` / `com.wonderfood.app` |
| Version | `versionName` `1.0`, `versionCode` `1` |
| Java toolchain | 17 |
| Release minification | Disabled at baseline |
| Disabled build features | AIDL, BuildConfig, shaders |
| Repositories | Google, Maven Central, Gradle Plugin Portal with constrained groups. |

## Known False Or Unsafe Data

These are not release waivers. They are known baseline facts that later tickets must
close or explicitly gate.

| Risk | Current baseline | Severity if present in release |
|---|---|---|
| False nutrition | Local interpreter and LiteLLM parser default meal logs to estimated calories/macros. Some code paths default to `420 kcal`, `18g` protein, `42g` carbs, `16g` fat. | P1 |
| Generic item nutrition | Unknown inventory/grocery items can get `"1 serving"`, `100 kcal`, and generic macros with source `ai_estimate_local`. | P1 |
| Health export of estimates | Health Connect export can write meal nutrition that may be estimated. | P1 if not clearly sourced/confirmed |
| Personal seed data | `PERSONAL_SEED_ENABLED = true` seeds personal pantry, recipes, preferences, and plan data. | P0/P1 depending on tracked/released exposure |
| Direct mutation paths | Voice/App Action paths can log water, meals, shopping, groceries, and cooking without the future command engine. | P1 for wrong mutation or duplicate actions |
| Hard deletion | Baseline delete paths remove rows rather than archive with undo. | P0/P1 for unsafe deletion or data loss |
| Cleartext and API key storage | Cleartext traffic is enabled; LiteLLM key is stored in SharedPreferences. | P0 for leak, P1 for release/privacy blocker |

## Supported Device And Configuration Matrix

Baseline support starts from Android API 26 or newer. Release support is not complete
until the following matrix has evidence:

| Dimension | Required coverage |
|---|---|
| Device classes | Phone compact width, tablet/expanded width, foldable or large-screen emulator where available. |
| Named Play/internal devices | Samsung phone and emulator-compatible device for internal release verification. |
| Orientation | Portrait and landscape. |
| Theme | Light, dark, and system modes. |
| Font scale | 1.0x, 1.5x, and 2.0x. |
| Accessibility | TalkBack reachability, semantic labels, contrast, keyboard navigation, touch targets. |
| Connectivity | Offline baseline, failed AI provider, no Health Connect, permission denied/revoked. |
| Region | United States first, per ADR 0001. |

Screenshot testing should follow the later D-lane plan: compact, medium, and expanded
widths with compact, medium, and expanded heights, plus mobile variations for theme and
font scale.

## Required Google Play Forms And Release Assets

| Play requirement | Owner | Evidence required |
|---|---|---|
| Play app creation and package identity | Coordinator | Play Console package `com.wonderfood.app`, signing setup, source tag. |
| App signing and upload key | Coordinator | Upload key custody note, Play App Signing enrollment, signed AAB checksum. |
| Store listing | Agent D with user review | Title, short/full description, icon, feature graphic, phone/tablet screenshots, release notes. |
| Privacy policy | Agent D with user approval | Public URL matching Data safety and in-app behavior. |
| Data safety | Agent D and Agent C | Final data inventory: food data, optional health nutrition write, optional AI provider network calls, local storage, no ads/billing. |
| Health Apps declaration | Agent D and Agent C | Nutrition write permission purpose, user consent flow, screenshots, denial behavior. |
| Content rating | Agent D | Completed questionnaire and rating certificate. |
| Target audience | Agent D | Audience declaration; no child-directed claims unless product scope changes. |
| Ads declaration | Agent D | No ads, matching product scope. |
| App access | Agent D | Instructions showing no account is required, or test credentials if this changes. |
| Permissions declaration | Agent D and Agent C | INTERNET and Health Connect permission justification; pre-launch permission report. |
| Countries/regions | Coordinator and user | United States first, expansion only after vitals/review are clean. |
| Internal/closed testing | Agent D and coordinator | Tester track evidence, install/upgrade evidence, feedback/vitals triage. |
| Production rollout | Coordinator and Agent D | Review approval, staged rollout status, Android Vitals monitoring, GitHub tag and Play AAB reconciliation. |

## Severity Policy

| Severity | Definition | Release action |
|---|---|---|
| P0 | Data loss, secret/health-data leak, unsafe deletion, release crash, broken migration. | Blocks all release gates. Must be fixed and verified. |
| P1 | False nutrition, wrong inventory mutation, unusable primary flow, accessibility blocker, policy/review blocker, persistent duplicate actions. | Blocks all release gates. Must be fixed and verified. |
| P2 | Degraded secondary flow, visual defect without data impact, recoverable integration failure. | Requires explicit coordinator disposition before release. |
| P3 | Cosmetic improvement or deferred convenience. | May ship if tracked and accepted by coordinator. |

No release proceeds with open P0 or P1 tickets. P2 issues require explicit coordinator
disposition. Trust, privacy, data-loss, and accessibility failures cannot be waived by an
individual lane.

## Release Gate Matrix

| Gate | Owner | Evidence required |
|---|---|---|
| Preflight baseline preserved | Coordinator | `docs/baseline/BASELINE.md`, `baseline-sha256.txt`, schema dump, passed debug build/unit tests, ignored artifact policy. |
| Wave 0: architecture and contracts documented | Coordinator plus Agents A/B/C/D | ADR 0001, worktree plan, this matrix, clean branch status, `./gradlew :app:assembleDebug :app:testDebugUnitTest`. |
| Wave 1: modules compile and test harness green | Agent A for modules, Agent D for harness | Module graph, fake/test-fixture policy, offline repeatable `./gradlew test`, no personal fixture data. |
| Wave 2: domain, Room, UI kit, nutrition contracts stable | Agents A/B/C with Agent D verification | Domain truth-state tests, nullable nutrition evidence, Room schema review, UI kit screenshot/accessibility smoke, AI schema contract tests. |
| Wave 3: command, Kitchen, capture, migration foundations green | Agents A/B/C with Agent D verification | Command validation tests, migration fixture run, Kitchen UI flow, capture fallback, no direct unsafe mutation. |
| Wave 4: inventory and direct-action behavior verified | Agents A/C with Agent D verification | Inventory command table tests, Google/App Action duplicate tests, wrong-mutation checks, undo/archive evidence. |
| Wave 5: capture-to-kitchen workflow passes | Agents A/B/C with Agent D verification | Receipt/photo/barcode/voice proposal flow, confirmation before mutation, stock-lot creation, retry/idempotency, screenshots. |
| Wave 6: recipe-to-meal workflow passes | Agents A/B/C with Agent D verification | Recipe create/edit/scale/cook tests, missing grocery generation, uncertain deduction confirmation, DB reconciliation. |
| Wave 7: Health, secure config, plan/shop views integrated | Agents B/C with Agent D verification | Health permission tests, no-network/provider-failure tests, secret redaction and backup scans, plan/shop UI evidence. |
| Wave 8: personal migration and plan-to-actual workflow pass | Agents A/B/D with coordinator review | V1 migration reconciliation, no personal data in release assets, plan move/skip/replace/eat workflow, shopping put-away evidence. |
| Wave 9: release candidate and GitHub release ready | Agent D and coordinator | Fresh clone build, CI green, secret/personal-data scan, release checklist, signed/tagged `v1.0.0-rc1`, debug APK checksum. |
| Wave 10: Play internal release accepted | Agent D and coordinator | Signed AAB upload, Play forms complete, internal install/upgrade on Samsung phone and emulator-compatible device, no unresolved P0/P1 pre-launch issue. |
| Wave 11: production rollout complete | Coordinator with Agent D monitoring | Closed test evidence if required, production review approval, staged rollout to 100 percent, vitals clean, GitHub `v1.0.0` matches Play AAB. |

## Follow-Up D-Lane Tickets Unblocked

- WF-D02 can build test harnesses, fakes, and fixtures against the known baseline.
- WF-D03 through WF-D05 can enforce truth, schema, command, idempotency, and undo rules.
- WF-D06 through WF-D09 can attach workflow, screenshot, accessibility, security, and
  device-matrix evidence to the gates above.
- WF-D10 through WF-D12 can use the Play/release matrix as the release checklist.
