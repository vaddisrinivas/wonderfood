# WonderFood Pluggable Backends and Onboarding Plan

## Goal

Ship a backend-neutral WonderFood with a calm first-run chooser, one active authoritative backend per household, offline-capable Android behavior, and a new friction-free daily food UI.

## Planning Status

- Plan created: 2026-07-18
- Implementation: started
- Current implementation entry point: Phase 1 contract slice complete; next action is Local adapter/cache seam
- Canonical checkout: `/Users/srinivasvaddi/Projects/wonderfood`
- GitHub repository: `vaddisrinivas/wonderfood`
- Existing uncommitted UI consistency changes must be preserved.

## Product Decision

WonderFood supports four selectable data homes:

1. Local SQLite
2. Google Sheets
3. Notion
4. PostgreSQL-compatible backend

Exactly one data home is authoritative at a time. When a remote backend is selected, SQLite remains an implementation-level offline cache and outbox, not a second user-visible source of truth. WonderFood does not perform simultaneous Notion/Sheets/Postgres mirroring.

## Architecture Decision

```text
Compose UI
  -> FoodRepository
      -> LocalFoodCache (SQLite, always present)
      -> BackendRouter (one active adapter)
          -> LocalSqliteBackend
          -> GoogleSheetsBackend
          -> NotionBackend
          -> PostgresBackend
```

App-facing code uses canonical domain models and command envelopes. Provider SDKs remain inside adapter modules. All adapters use stable UUIDs, schema versions, expected versions, idempotency keys, soft archive, nullable unknown values, and exportable snapshots.

## Backend Contract

```kotlin
interface FoodBackend {
    val descriptor: BackendDescriptor
    val capabilities: Set<BackendCapability>

    suspend fun connect(config: BackendConfig): ConnectionResult
    suspend fun healthCheck(): BackendHealth
    suspend fun bootstrap(): WonderFoodSnapshot
    suspend fun pull(after: ChangeCursor?): ChangePage
    suspend fun push(commands: List<CommandEnvelope>): PushResult
    suspend fun exportSnapshot(): WonderFoodSnapshot
    suspend fun disconnect()
}
```

Required supporting boundaries:

- `BackendRouter`: resolves exactly one active adapter.
- `BackendConfigurationStore`: stores non-secret provider selection/configuration.
- `CredentialVault`: stores OAuth/token/DSN secrets with Android Keystore protection.
- `LocalFoodCache`: stores rendered state, sync cursor, outbox, conflicts, and errors.
- `SyncCoordinator`: pushes outbox and pulls remote changes only for remote adapters.
- `BackendContractTest`: runs the same behavioral suite against every adapter.

### Phase 1 Implementation Status

- Added `FoodBackend`, provider descriptors, capabilities, configs, credential references, health, change, push, and snapshot summary types under `core:data`.
- Added `BackendRouter`, `BackendConfigurationStore`, `CredentialVault`, and onboarding state primitives.
- Added `LocalSqliteBackend` backed by Room for local connect, health, bootstrap, pull, snapshot export, and disconnect.
- Added Room snapshot read queries and domain mappers for pages, foods, aliases, and stock lots.
- Added pure Kotlin Google Sheets link/raw-ID parser for the no-deployment onboarding flow.
- Added `SharedPreferencesBackendConfigurationStore` for persisted active backend selection and first-run dismissal state.
- Wired first-run Compose data-home chooser into `MainScreen`: Local activates immediately, Google Sheets validates and stores the pasted Sheet reference, Notion/Postgres show setup-next status.
- Added Play/FOSS `GoogleSheetsAuthorization` wrappers using Google Identity authorization for the Sheets OAuth scope in Play and an explicit unavailable path in FOSS.
- Wired Google Sheets onboarding button to Google sign-in plus Sheets scope request before persisting the Sheets backend config.
- Updated shared Google sign-in wording so missing OAuth config applies to all Google features, not just Drive backup.
- Added `GoogleSheetsGateway` for spreadsheet metadata fetch, WonderFood tab creation, and header initialization using the Sheets REST API.
- Google Sheets config is now saved only after OAuth and schema bootstrap succeed.
- Added typed `WonderFoodSnapshotRow` export/decode helpers in `core:model`.
- Added Google Sheets snapshot row export and read primitives for the canonical tab/header protocol.
- Added focused unit tests proving local default behavior, successful activation, failed activation preservation, duplicate adapter rejection, and onboarding step mapping.
- Added focused local backend tests proving local health and kitchen snapshot bootstrap/export.
- Added focused Google Sheets URL parsing tests.
- Added focused persisted backend configuration tests.
- `core:data` now depends on `core:ai` so provider pushes use the canonical `CommandEnvelope` mutation object.
- Verified with `./gradlew :core:model:test`, `./gradlew :core:data:testDebugUnitTest`, targeted `GoogleSheetsGatewayTest`, `./gradlew :app:assemblePlayDebug`, `./gradlew :app:assembleFossDebug`, emulator install, fresh-launch onboarding screenshot, Local selection tap, Sheets placeholder-OAuth click-path, and `MainActivity` resumed activity checks.

## First-Run Onboarding

### Screen 1: Value

Show one short promise, not a feature tour:

```text
Your kitchen, plans and shopping in one place.
Choose where your data should live. You can change this later.

[Continue]
```

Do not request camera, microphone, notification, Health Connect, Google, or AI permissions here.

### Screen 2: Choose Data Home

```text
Where should WonderFood keep your food?

On this phone
Private, offline, no setup

Google Sheets
Shared and editable from a spreadsheet

Notion
Use a Notion page as your food workspace

Postgres / Supabase
Connect a hosted or self-managed database
```

Local SQLite is visually recommended for users who do not know what to choose. Do not use database jargon in primary card labels.

### Screen 3A: Local SQLite

- Explain that data stays on this device.
- Offer optional import from WonderFood backup/CSV.
- Primary action: `Use this phone`.
- No account, token, permission, or network required.

### Screen 3B: Google Sheets

User input:

```text
Google Sheet URL
[Paste link]

[Continue with Google]
```

Flow:

1. Validate and extract spreadsheet ID from pasted URL.
2. Start Google sign-in with existing Credential Manager integration.
3. Request the Sheets write scope through Google Identity authorization.
4. Fetch workbook metadata and confirm edit access.
5. Detect WonderFood schema.
6. If empty/new, create required tabs and headers.
7. If populated but unknown, show import/schema preview instead of mutating.
8. Write a reversible connection-test record or metadata field.
9. Bootstrap local cache.
10. Save active backend only after all checks pass.

No user deployment, Apps Script, connector URL, password, or API key. The app owns OAuth configuration once; each user only pastes a Sheet URL and signs in.

Initial flavor scope:

- `play`: implement using current Credential Manager, Google ID, and Play authorization dependencies.
- `foss`: keep Local/Notion/Postgres available; show Google Sheets as unavailable until a non-Play browser OAuth path is deliberately added.

Google Sheets schema:

```text
_meta
_changes
_mutations
foods
stock_lots
recipes
recipe_ingredients
recipe_steps
meal_plans
plan_entries
meal_logs
shopping_items
receipts
receipt_lines
preferences
events
```

System columns are protected where Google permissions allow. Human-editable columns remain obvious. Record identity never depends on row number, title, or sort order.

### Screen 3C: Notion

Initial no-deployment setup:

```text
Notion page URL
Integration or personal token

[Test connection]
```

Do not collect Notion username/password. Initial implementation uses internal integration token or personal access token because public Notion OAuth requires a protected client-secret exchange service. A later hosted OAuth option may be added without changing the adapter contract.

Flow:

1. Validate token without logging it.
2. Parse root page ID.
3. Verify the page is shared with the integration/token.
4. Detect or create WonderFood data sources.
5. Confirm create/update/archive access.
6. Bootstrap local cache.
7. Save active backend only after successful verification.

Managed Notion data sources:

```text
Kitchen
Recipes
Recipe Ingredients
Week
Shopping
Preferences
Receipts
Activity
```

Structured properties sync. Recipe notes may remain free-form Notion content; managed recipe steps and ingredients require explicit blocks/data-source relations rather than parsing arbitrary page content.

### Screen 3D: Postgres / Supabase

Recommended setup:

```text
Connection type
  Supabase
  PostgREST
  WonderFood server

Server URL
Publishable/API token
Household ID

[Test connection]
```

Advanced setup:

```text
Direct PostgreSQL connection string
```

Direct DSN mode is internal/sideload-oriented, TLS-only, Keystore-protected, and requires a restricted database role. Production recommendation remains PostgREST/Supabase/WonderFood HTTP API with row-level authorization; raw database credentials should not be embedded in a broadly distributed app.

### Screen 4: Existing Data

When source contains data:

```text
We found 498 kitchen items, 12 recipes and 7 plans.

Use this data
Import into an empty workspace
Review details
```

Never merge unknown schemas or overwrite destination records without preview.

### Screen 5: Ready

```text
Your food home is ready.

Google Sheets
498 kitchen items
Last checked just now

[Enter WonderFood]
```

### Onboarding Failure States

- Invalid URL
- Authentication cancelled
- Missing edit permission
- Shared Sheet is view-only
- Notion page not shared with integration
- Token expired/revoked
- Postgres TLS/certificate failure
- Unsupported schema version
- Destination contains unrelated data
- Network unavailable
- Partial bootstrap
- OAuth configuration missing in release build

Every error explains what remains safe, offers retry, and offers `Choose another data home`. No failed connection becomes active.

## Backend Switching

Settings exposes `Data home` with status, provider link, export, connection test, and switch action.

Safe switch sequence:

1. Connect and validate destination.
2. Export current canonical snapshot locally.
3. Preview entity counts and conflicts.
4. Import into destination using stable IDs.
5. Compare counts and deterministic hashes.
6. Flip active backend atomically.
7. Keep source unchanged for rollback.
8. Retain encrypted migration snapshot until user explicitly removes it.

No live dual-write between old and new backends after switch.

## New Daily UI Contract

Navigation:

```text
Now       Food       [+]       Week
                         Cart available globally
```

### Now

- Next meal with `Cook`, `Ate it`, and `Swap`.
- Universal `Tell WonderFood...` capture row.
- Use-first food.
- Pending review only when needed.
- Compact spending summary when receipt prices exist.

### Food

Segmented views:

```text
Can make | In kitchen | Saved
```

- `Can make` ranks recipes by available ingredients, expiry, missing cost, preferences, and leftovers.
- `In kitchen` supports swipe-left archive and swipe-right cart with Undo plus accessible action alternatives.
- `Saved` is the recipe library and import surface.

### Week

- Seven-day strip with meal slots.
- `Make my week` creates a reviewable plan draft.
- Plan cards show kitchen match, missing items, leftovers, and estimated extra cost.
- Plan-to-cart changes remain reviewable.

### Universal Capture

One composer accepts text, voice, barcode, receipt, photo, share, and pasted recipe URL. User describes reality; WonderFood infers object type and shows a compact review draft. Manual deterministic actions remain available without AI.

### Cart

Cart is a global bottom sheet. Full-screen mode appears only during active shopping, receipt review, or put-away.

### Backend Visibility

Daily UI does not change by backend. Provider appears only in Settings and relevant external actions:

- `Open Google Sheet`
- `Open in Notion`
- `Open server dashboard`
- `Stored on this phone`

## Product Taste Brief

- Audience: one household using Android, Notion, and spreadsheets during cooking, planning, and shopping.
- Job: capture what happened or decide what happens next in under five seconds.
- Taste: warm kitchen utility; dense but calm; actual food before controls; natural language before object types; reversible gestures.
- First screen: next meal, one capture row, use-first items, cart access.
- Happy path: state intent, review only when needed, commit, Undo.
- Failure path: cached data remains visible; remote/auth failure never hides local state.
- Do not: database-first forms, five disconnected creation flows, mandatory AI, onboarding permission barrage, provider-specific navigation, nested decorative cards.

## Implementation Phases

### Phase 1: Canonical Contracts and ADRs

- [ ] Write V4 backend/onboarding architecture document and supersede incompatible five-destination requirements.
- [ ] Define canonical snapshot, change cursor, command, conflict, health, configuration, and capability contracts.
- [ ] Define schema versioning and provider mapping rules.
- [ ] Decide module/package boundaries around existing app/core split.
- [ ] Add backend contract test specification.
- **Status:** pending

### Phase 2: Local Adapter and Repository Seam

- [ ] Wrap existing `FoodChatStore` behavior behind `LocalSqliteBackend` without data loss.
- [ ] Add `FoodRepository`, `BackendRouter`, local cache, outbox, sync state, and conflict state.
- [ ] Preserve existing manual, AI, share, deep-link, import/export, and receipt paths.
- [ ] Prove local behavior parity before adding remote providers.
- **Status:** pending

### Phase 3: First-Run Onboarding and Credential Vault

- [ ] Implement onboarding state machine and resumable progress.
- [ ] Build backend chooser and provider setup screens.
- [ ] Add DataStore-backed non-secret configuration.
- [ ] Add Keystore-backed credential storage and redacted diagnostics.
- [ ] Add connection test, schema preview, cancellation, retry, and alternate-backend flows.
- [ ] Add Settings `Data home` and safe backend-switch wizard.
- **Status:** pending

### Phase 4: Remote Adapters

- [ ] Google Sheets direct OAuth adapter for `play` flavor.
- [ ] Sheets schema creation, batched CRUD, cursor/change log, conflict detection, and direct Sheet edit reconciliation.
- [ ] Notion token/page adapter with managed data sources and polling/webhook-ready cursor model.
- [ ] PostgREST/Supabase adapter with RLS/household boundaries.
- [ ] Advanced direct PostgreSQL DSN mode behind internal/advanced guardrails.
- [ ] Run shared contract tests against each backend.
- **Status:** pending

### Phase 5: New UI and Daily Flows

- [ ] Add new design tokens, typography, shapes, and adaptive shell.
- [ ] Implement Now, Food, Week, universal capture, and global cart.
- [ ] Implement one-tap meal logging and reviewable inventory deductions.
- [ ] Implement Kitchen gestures and accessible alternatives.
- [ ] Implement matched recipes, plan-to-cart, receipt spending, and food-finance summaries.
- [ ] Keep old UI behind a feature flag until parity is proven.
- **Status:** pending

### Phase 6: Migration, Quality, and Device Proof

- [ ] Migrate current SQLite records to stable canonical IDs.
- [ ] Add import/export and backend-switch rollback tests.
- [ ] Add contract, conflict, idempotency, retry, schema, and security tests.
- [ ] Verify light/dark, large text, offline, auth-cancelled, long-list, conflict, landscape, and tablet UI.
- [ ] Build/install on emulator and physical phone; capture screenshots and interaction evidence.
- [ ] Validate Google OAuth with release client configuration.
- **Status:** pending

### Phase 7: Rollout and Documentation

- [ ] Update `FEATURES.md`, `ROADMAP.md`, `PRIVACY.md`, `SECURITY.md`, and release notes.
- [ ] Publish backend setup and migration guides.
- [ ] Document provider privacy and deletion behavior.
- [ ] Stage rollout: Local -> Sheets beta -> Notion/Postgres beta -> new UI default.
- [ ] Retain rollback flags for one stable release.
- [ ] Run ship-readiness gate before signed release.
- **Status:** pending

## Proposed PR Sequence

1. `Backend contracts, ADRs, and contract-test harness`
2. `Local SQLite adapter and repository seam`
3. `First-run data-home chooser and credential vault`
4. `Google Sheets OAuth backend`
5. `Notion token backend`
6. `Postgres/PostgREST backend`
7. `WonderFood V4 Now/Food/Week shell`
8. `Universal capture, cart, matched recipes, and plan loop`
9. `Migration, provider switching, device proof, and docs`

Do not combine all phases into one PR. Each backend must remain independently reviewable and removable.

## Acceptance Gates

### Backend Contract

- Same create/update/archive/export fixtures pass for every provider.
- Retried mutation cannot duplicate data.
- Stale expected version creates conflict instead of overwrite.
- Unknown values survive round trips as null.
- Backend selection does not alter domain behavior.

### Onboarding

- Local setup completes in one action.
- Sheets setup requires only Sheet URL plus Google authorization.
- Notion setup requires only page URL plus token.
- Postgres setup accepts supported endpoint credentials; advanced DSN is clearly separated.
- Failed/cancelled setup never becomes active.
- Onboarding resumes safely after process death.
- No unnecessary permission is requested before its feature is used.

### Daily UX

- Add food: two taps plus input.
- Log planned meal: one tap.
- Add Kitchen item to cart: one gesture or accessible action.
- Cart reachable from every main screen in one tap.
- Plan draft generated in two taps before review.
- No common action requires AI.

### Security and Privacy

- OAuth/token/DSN values never appear in logs, backups, screenshots, analytics, or Git.
- Secrets use Android Keystore-backed protection.
- Sheets permission scope is disclosed before authorization.
- Notion username/password is never collected.
- Direct PostgreSQL mode uses TLS and restricted roles.
- Provider disconnect/export/delete behavior is documented.

### Release Proof

- Build and tests pass for `play` and `foss` supported paths.
- Google OAuth works with release configuration.
- Emulator and physical-device onboarding evidence exists.
- Existing user migration and rollback are verified.

## Risks

| Risk | Mitigation |
|---|---|
| Current live `FoodChatStore` and typed core architecture diverge | Introduce adapter seam first; avoid broad simultaneous engine rewrite |
| Sheets row edits break identity or schema | Stable hidden IDs, protected system columns, schema validation, repair preview |
| Google OAuth verification delays release | Start consent-screen/scope review during Sheets adapter phase |
| Notion arbitrary blocks cannot map safely | Sync structured properties and managed regions only |
| Direct Postgres credentials leak from phone | Prefer PostgREST/Supabase; gate DSN as advanced/internal and Keystore-protect it |
| New UI and backend migration create oversized blast radius | Separate PRs, feature flags, local parity gate, reversible migration |
| Existing dirty UI changes are overwritten | Preserve and land them separately before backend refactor touches same files |

## Rollback

- Keep current local store and old UI behind flags until parity and migration proof pass.
- Never delete source backend during switch.
- Create encrypted snapshot before migration or provider change.
- On remote failure, continue showing cached SQLite data and queue writes.
- Allow user to return to previous backend using preserved configuration and snapshot.

## Estimated Scope

- Architecture/contracts: 3-4 days
- Local seam: 5-7 days
- Onboarding/security: 5-7 days
- Sheets adapter: 6-9 days
- Notion adapter: 5-8 days
- Postgres adapter: 5-8 days
- New UI and flows: 10-15 days
- Migration/quality/release: 7-10 days
- Total: approximately 8-12 focused weeks for production-grade delivery

## Next Action

Start PR 1 with architecture documents and canonical backend contracts. Before touching `MainScreen.kt`, land or isolate the existing uncommitted UI consistency changes.

## Errors Encountered

| Error | Attempt | Resolution |
|---|---:|---|
| Initial broad GitHub keyword scan ranked unrelated popular Android repositories | 1 | Replaced broad ranking with targeted repo and code searches |
