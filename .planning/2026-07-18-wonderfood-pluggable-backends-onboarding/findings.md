# Findings and Decisions

## User Requirements

- First boot must let users choose their data home.
- Supported choices: Local SQLite, Google Sheets, Notion, and PostgreSQL-compatible backend.
- One backend is authoritative at a time.
- Local SQLite requires no setup.
- Google Sheets must require no user deployment; accepted flow is Sheet URL plus Google OAuth.
- Notion should be usable by a household member who prefers Notion.
- Notion must not collect username/password; use a page URL and token initially.
- Postgres support should accept hosted API credentials and include an advanced connection-string path.
- Daily UI/UX must be rebuilt around low-friction food capture, meal logging, planning, recipes, and cart access.
- Onboarding, migration, failure states, privacy, security, tests, rollout, and rollback must be planned before coding.

## Verified Current Repository State

- Canonical checkout is `/Users/srinivasvaddi/Projects/wonderfood` on `main` with origin `vaddisrinivas/wonderfood`.
- Existing uncommitted changes are present in `MainActivity.kt`, `Theme.kt`, and `MainScreen.kt`; they belong to the UI consistency work and must not be overwritten.
- `FEATURES.md` is the product capability source of truth.
- Current product contract says the five-destination shell is shipped and local-first database behavior is shipped.
- `ROADMAP.md` already requires release Google OAuth configuration without committed credentials.
- `SECURITY.md` requires provider credentials to be Keystore-protected and excluded from backup.
- `PRIVACY.md` currently promises local-first storage and must be updated when selectable remote authorities ship.
- `play` flavor already includes Credential Manager, Google ID, Play Services Auth, and Google Drive authorization code.
- `foss` flavor intentionally stubs Google integrations.
- Runtime UI still relies heavily on `FoodChatStore`; typed core repository/engine modules coexist but are not fully wired into the production loop.
- No open PR currently exists. Open issue #18 is the closest existing UX/data epic but does not yet contain this full provider/onboarding architecture.

## Official Platform Findings

- Google Sheets write operations require OAuth scopes. A public browser-edit link or API key alone does not authorize API writes.
- Existing Google Identity dependencies create a repo-native path for direct Sheet URL plus Google authorization in the `play` flavor.
- Notion internal integrations and personal tokens use bearer tokens and explicit page sharing.
- Notion public OAuth requires protected client credentials/token exchange; it conflicts with the no-deployment requirement for the first implementation.
- Android secrets embedded in an APK can be extracted. Tokens and database credentials require Keystore-backed protection and should not be hard-coded.
- Direct PostgreSQL from Android exposes credentials and network surface; PostgREST/Supabase is the safer production path.

## GitHub Research Findings

- `android/nowinandroid`: production data interfaces are replaced by flavor/test implementations; supports the `FoodBackend` plus adapter design.
- `ozgliderpilot/plant-scanner`: Room offline data, a sync queue, Apps Script/Sheets backend, deduplication, and an independently tested core demonstrate a close domain pattern. WonderFood does not need its deployment model after choosing OAuth, but its outbox/dedupe split remains useful.
- `seratch/notion-sdk-jvm`: Kotlin/JVM Notion client works on Android and supports OAuth plus pluggable OkHttp clients.
- `PostgREST/postgrest`: exposes PostgreSQL as REST with JWT roles, constraints, schema versioning, and OpenAPI.
- `supabase-community/supabase-kt`: Android client supports PostgREST, Auth, Storage, and Realtime with publishable keys and RLS.
- `sqldelight/sqldelight`: typed SQLite/PostgreSQL schemas are useful references, but SQLDelight is not itself a provider synchronization abstraction.
- `Arsenoal/syncforge` and newer offline-first samples show outbox/push/pull/conflict concepts, but established Android and provider repos are stronger architectural references.
- Broad GitHub scan artifact: `/Users/srinivasvaddi/projects/repos-exploration/wonderfood-pluggable-backends`.

## Technical Decisions

| Decision | Rationale |
|---|---|
| One active authoritative backend | Prevents conflict loops and keeps user mental model clear |
| SQLite always exists as local cache | Preserves instant and offline Android behavior |
| Local mode treats SQLite as authority | No unnecessary sync abstraction for privacy-first users |
| Direct Sheets API with Google OAuth | Meets no-deployment requirement and supports full CRUD |
| Sheets onboarding uses URL plus Google authorization | Simplest feasible user setup |
| Notion initial auth uses page URL plus token | No username/password and no hosted OAuth exchange required |
| Postgres production path uses PostgREST/Supabase/server API | Keeps raw database credentials off normal mobile clients |
| Direct DSN is advanced/internal | User requested connection-string support, but it has material security risk |
| Canonical command/snapshot contract | Prevents provider SDKs from shaping product behavior |
| Stable UUID/version/idempotency/soft archive | Required for switching, retries, conflicts, and safe migration |
| New UI remains backend-neutral | Storage choice should not fragment daily experience |
| Separate backend and UI PRs | Reduces blast radius and preserves rollback |

## Product Taste Brief

- Audience: household members using Android, Notion, or Sheets with different technical comfort.
- Core job: record food reality or decide the next meal quickly.
- First-run success: choose a data home, connect it, see existing data, enter the app without irrelevant permissions.
- Daily success: add food, log a meal, plan, find a recipe, or update cart within one or two actions.
- Visual direction: warm, food-first, compact, calm, trustworthy, and light-first with a deliberate warm dark theme.
- Avoid: database terminology, long setup forms, mandatory account for Local, permission barrage, provider-specific navigation, AI as a gate, and silent remote mutations.

## Resources

- https://github.com/android/nowinandroid
- https://github.com/ozgliderpilot/plant-scanner
- https://github.com/seratch/notion-sdk-jvm
- https://github.com/PostgREST/postgrest
- https://github.com/supabase-community/supabase-kt
- https://github.com/sqldelight/sqldelight
- https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets.values/update
- https://developers.notion.com/guides/get-started/authorization
- https://developer.android.com/privacy-and-security/security-tips

## Open Questions Deferred to Implementation

- Exact canonical package/module placement after inspecting current core repository APIs during PR 1.
- Whether Google Sheets direct human edits use a dedicated `_changes` formula/script-free reconciliation scan or provider metadata diff; prototype both during adapter phase.
- Whether FOSS should eventually add browser OAuth for Sheets or intentionally remain Google-free.
- Which PostgreSQL connection-string driver can meet Android/TLS requirements without harming release security; direct mode may remain internal-only.

## Issues Encountered

| Issue | Resolution |
|---|---|
| Broad GitHub keyword search was noisy | Used targeted repository searches and primary READMEs |
| Public Sheet link was initially conflated with API write access | Verified official Sheets write endpoint requires OAuth and selected Google authorization |

