# WonderFood capability map

This file is the product source of truth. It describes user-visible capabilities,
not implementation tickets. Update it in the same change that ships or removes a
capability.

## Latest release: 1.0.4

WonderFood 1.0.4 ships the first serious version of the AI home-space
foundation:

| Area | What changed | How to use it |
|---|---|---|
| Android shell | The primary app destinations are now `Now`, `Food`, `Week`, `Saved`, and `Cart`. | Use `Now` for today's meals, `Food` for kitchen and recipe context, `Week` for planning, `Saved` for saved recipes, and `Cart` for shopping/receipts/put-away. |
| Local-first data | SQLite remains the default data home and needs no setup. | Choose local/on-device during onboarding or keep using the app without connecting another backend. |
| Google Sheets | Added Sheet URL parsing, Google authorization foundations, readable workspace schema tabs, raw hidden `_wf_*` sync tabs, snapshot export/import scaffolding, and seeded proof data. | Use the Play build, connect a Sheet URL with Google authorization, then review exported workspace tabs instead of treating Sheets as a blank dump. |
| Notion | Added Notion page URL parsing, token-protected gateway foundations, human-readable workspace schema, managed block export, snapshot merge support, and seed fixture coverage. | Connect a Notion page/integration token and use Notion as a household-readable mirror/workspace, while the app remains the custom AI view. |
| Postgres/Supabase | Added Postgres/Supabase-style connection parsing and gateway contracts. | Use HTTPS endpoint/API-token style configuration for advanced or self-hosted setups; direct mobile DSN mode is config-only until a safe adapter exists. |
| Backend safety | Added backend router and rollback snapshot contract before switching data homes. | Before committing a new data home, WonderFood stages a local safety snapshot so the household can recover. |
| Workspace schema | Added uniform app/Notion/Sheets concepts for food, recipes, meals, purchases, cart, and planning data. | Use the same object identities and fields across the app and external workspaces to support future automation. |
| AI contracts | Added typed skill contracts for recipe import, pantry normalization, can-cook ranking, meal planning, cart building, personalization, cooking coach, receipt parsing, and nutrition estimation. | AI output stays as reviewable proposals; app validation owns persistence and safety. |
| External food providers | Added provider contracts and mappings for TheMealDB recipe lookup and Open Food Facts package lookup. | Provider responses map into WonderFood proposals with attribution, warnings, cache policy, and confidence instead of silent writes. |
| Receipts/import | Improved deterministic receipt parsing, food draft normalization, legacy memory export, legacy snapshot import, and snapshot merging. | Receipt and import paths now produce more consistent editable drafts across AI and non-AI flows. |
| Manual workflows | Manual food entry, cart item creation, recipe creation, and meal logging remain available without AI. | Use the visible create actions in each destination when you do not want AI involved. |
| Quality/release | Added release evidence scripts, Google Sheets proof helper, serial connected harness, and CI green lanes for local quality plus API 26/API 35 device quality. | A release should now prove local unit/build checks, connected device checks, and signed APK publication. |

Status meanings:

- **Shipped** — available in the Android app and covered by an automated or device check.
- **Partial** — useful path exists, but a provider, workflow, or validation path remains.
- **Planned** — accepted product direction; not yet dependable for users.
- **External** — requires configuration or infrastructure outside this repository.

## Daily workspace

| Capability | Status | User contract |
|---|---|---|
| Now/Food/Week/Cart shell | Shipped | Primary navigation is Now, Food, Week, and Cart; recipes live inside Food instead of a separate top-level destination. |
| Today meal timeline | Shipped | Planned and logged meals share one daily timeline. Unknown nutrition is not rendered as zero. |
| Global search | Shipped | Search across food, groceries, recipes, meals, plans, and receipts. |
| Object detail pages | Shipped | Food objects can be inspected and edited without AI. |
| Undo and activity history | Partial | Core food mutations expose feedback/history; every multi-object workflow still needs parity coverage. |
| Archived catalog activation | Planned | Imported items can remain inactive until explicitly restored to the working kitchen. |

## Kitchen

| Capability | Status | User contract |
|---|---|---|
| Manual food entry and editing | Shipped | Name, quantity, zone, category, nutrition, notes, image, expiry, store, and price remain user-correctable. |
| Fridge, freezer, and pantry zones | Shipped | Storage is explicit and editable; receipt intake does not become a shopping item. |
| Use-first prioritization | Shipped | Expiry, low stock, and missing details surface before passive inventory. |
| Gallery/list views and filters | Shipped | Search, zone, category, sort, and selection state survive recomposition. |
| Stable food identity and emoji | Partial | Stored/user-selected image wins; deterministic fallback exists, but full cross-surface golden coverage remains. |
| Nutrition enrichment | Partial | Trusted lookup and provenance exist; real label/barcode provider coverage remains incomplete. |
| Inventory lots and provenance | Partial | Purchase source, date, cost, storage, and transactions exist; multi-lot UI needs more device coverage. |
| Barcode capture | Partial | Intake contract and test snapshots exist; production catalog/provider and serving picker remain. |

## Planning, recipes, and meals

| Capability | Status | User contract |
|---|---|---|
| Manual meal logging | Shipped | Logging works without an LLM and keeps nutrition nullable. |
| Meal plans and day entries | Shipped | Draft/planned/eaten/skipped states are editable and reviewable. |
| Deterministic meal planning | Shipped | No-LLM templates can create the same proposal shape as AI. |
| Pantry-aware recipe matching | Shipped | Recipes show have/need matching against current Kitchen data. |
| Food segmented hub | Shipped | Food offers Can make, In kitchen, and Saved views so recipes start from matched Kitchen items. |
| Cooking and inventory deductions | Partial | Cooking can propose/log changes; complete leftover and multi-lot deduction review remains. |
| Focused cooking mode | Planned | Large-step, wake-lock, timers, progress, and finish summary. |

## Shopping and receipts

| Capability | Status | User contract |
|---|---|---|
| Manual shopping list | Shipped | Add, edit, mark bought, recover, and delete without AI. |
| Receipt image plus user note | Shipped | User can add context before interpretation; local evidence stays attached. |
| Deterministic receipt parsing | Shipped | Staged text can become an editable receipt proposal without an LLM. |
| AI receipt interpretation | Partial | Per-line item/storage/nutrition/expiry/cost proposals exist; real OCR/provider flow needs production validation. |
| Receipt provenance and price | Partial | Merchant/cost fields flow through proposals and inventory; UI/provider parity remains. |
| Put-away workflow | Partial | Receipt items default to Kitchen review; dedicated queue UI exists but complete receipt-to-lot state needs coverage. |

## AI and commands

| Capability | Status | User contract |
|---|---|---|
| Primary then fallback routing | Shipped | Primary is tried once; fallback is tried once after failure. No round robin. |
| Editable chat messages and replies | Shipped | User can correct both sides of saved conversation history. |
| Persistent chat history | Shipped | New chat starts a thread without deleting earlier conversations. |
| Editable proposals | Shipped | AI and external commands remain drafts until the user edits/accepts/rejects them. |
| Visible AI context | Shipped | AI sheet discloses its page context and local data summary. |
| In-app core skill editor | Shipped | Bundled skill can be viewed, overridden, saved, or reset in Settings. |
| Typed command envelopes | Shipped | Provider output maps to versioned known commands; app validation owns all writes. |
| Bulk command links/packages | Shipped | Bounded actions are validated, reviewed, audited, and applied atomically. |
| Destructive/risk policy | Partial | Confirmation and command validation exist; allergy/preference/merge parity needs more adversarial tests. |

## External entry and integrations

| Capability | Status | User contract |
|---|---|---|
| HTTPS/custom-scheme prefill links | Shipped | Links open an editable form/proposal; links do not directly mutate local data. |
| Android share intake | Shipped | Text/images shared from messaging and other apps become reviewable intake. |
| Explicit Android command intent | Shipped | Authorized automation apps can stage bounded commands for review. |
| Google Assistant/App Actions | Partial | Intent/shortcut contracts exist; physical-device Assistant validation remains. |
| Samsung Routines/notification bridge | Partial | Share/intent/deep-link paths are available; packaged Routine/notification adapters remain. |
| Google Drive encrypted backup | Partial | App-data backup flow exists; production OAuth client configuration is external. |
| Google Sheets data home | Partial | Play flavor connects with Sheet URL plus Google authorization, creates schema tabs, exports snapshots, preserves remote data, and supports reviewed additive import. Release OAuth proof remains external. |
| Notion data home | Partial | Page URL plus integration token is validated, token is Keystore-protected, and snapshot export appends managed blocks. Structured Notion readback/import remains. |
| Postgres/Supabase data home | Partial | Supabase/PostgREST/WonderFood server modes validate HTTPS API access and export canonical snapshots. Direct DSN is advanced/config-only until a safe adapter exists. |
| Health Connect | Partial | Optional read/integration flow exists; broader device/provider validation remains. |
| Verified `wonderfood.app` links | External | Requires production `assetlinks.json` for the release signing certificate. |

## Data, quality, and release

| Capability | Status | User contract |
|---|---|---|
| Local-first database | Shipped | Core food workspace remains useful offline and without AI. |
| Backend switch rollback snapshot | Shipped | Switching data homes creates a local rollback snapshot before the new backend is committed. |
| CSV import/export | Shipped | Imports are reviewed and exports remain user-controlled. |
| Encrypted backup/restore | Shipped | Explicit backups are encrypted; Android automatic data backup is disabled. |
| Golden command parity tests | Partial | Pantry/AI paths are covered; plan, meal, shop, recipe, and receipt matrix is growing. |
| Android quality workflow | Shipped | Unit/build checks run in GitHub Actions. |
| Signed reproducible release | Planned | Release keystore policy, versioning, provenance, and F-Droid metadata remain. |

## Maintenance rule

GitHub Issues track discrete work. `ROADMAP.md` groups releases. `CHANGELOG.md`
records user-visible changes. This file alone answers: “What can WonderFood do?”
