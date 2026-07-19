# WonderFood Notion-First Product and Data Architecture

## Goal

Rebuild WonderFood around a small household food model that is pleasant to use directly in Notion, understandable in Google Sheets, and fast in the Android app. Notion or Sheets is the user-owned authoritative database; SQLite is the offline cache/outbox; WonderFood is the AI home-space and purpose-built interaction layer.

## Status

- Planning: complete; expanded standards-backed V3 blueprint added
- Implementation: complete for the V3 shared Notion/Sheets workspace projection, live proof harness, conflict inbox persistence, and Google Sheets/Notion seed round-trip gates
- Canonical checkout: `/Users/srinivasvaddi/Projects/wonderfood`
- Replaces the human-workspace schema section of the 2026-07-18 pluggable-backend plan.
- Existing backend/onboarding code and live Notion/Sheets workspaces are migration inputs, not the target schema.
- Canonical schema specification: `schema_blueprint.md`. Where it conflicts with the earlier seven-record outline below, the blueprint wins.

## Final Architecture Amendment

The initial seven-record model was adequate for the daily interface but too shallow as the complete future domain. The final design keeps seven everyday Notion/Sheets surfaces while adding managed records for product identity, lots, movements, recipe revisions, shopping demand, purchase lines, nutrition, provenance, and audit. Global catalog/community records remain hosted and are never mirrored wholesale into a household workspace.

This gives the product three deliberate layers:

1. `Everyday workspace`: what a household member sees and edits.
2. `Managed household model`: correctness, automation, history, and interoperability.
3. `Hosted product model`: 180k+ recipes, discovery, publishing, and communities.

See `schema_blueprint.md` for the authoritative field-level plan and provider mappings.

## Product Contract

1. A household chooses one authority: Local, Notion, Google Sheets, or Postgres-compatible.
2. Notion and Sheets are real, editable household workspaces, not export reports.
3. The Android app is the quickest way to capture, plan, cook, shop, and ask AI.
4. Human edits made in the selected database must return to the app.
5. AI proposes typed food actions; the sync layer validates and applies them.
6. The global recipe catalog and communities are not copied into a household database. Only saved/personalized recipes enter it.
7. Provider presentation differs, but entity meaning, IDs, validation, and lifecycle are uniform.

## Architecture Decision

```text
Global services (future)
  Recipe catalog (180k+) | community graph | import/extraction
                  read/search/share
                         |
Android: WonderFood AI Home Spaces
  Home | Explore | Plan | Kitchen | Cook mode | Cart drawer | AI command bar
                         |
              Canonical domain + commands
                         |
        SQLite cache + outbox + conflict inbox
                         |
                one active authority
         Local | Notion | Sheets | Postgres API
```

The canonical schema is a field registry, not a shared flat table definition. Each field has a stable machine key, domain type, validation, ownership, and provider presentation:

```text
food.quantity_on_hand
  domain: decimal quantity
  Notion: Number property labeled "On hand"
  Sheets: DOUBLE column labeled "On hand"
  SQLite/Postgres: quantity_on_hand decimal
  app: quantity stepper and natural-language capture
```

Schema.org terms are interoperability annotations (`sameAs`/export mapping), not user-facing column names. Users should see `Servings`, not `recipeYield`, and never `@type`.

## Canonical Household Model

### Human-facing records

| Entity | One record means | Why it exists |
|---|---|---|
| `Food` | One household ingredient/product | Catalog identity and current pantry state in one place |
| `Recipe` | One saved or household recipe | Human recipe page plus structured metadata |
| `RecipeIngredient` | One ingredient line in one recipe | Required junction for quantity, substitutions, pantry matching, and shopping generation |
| `Meal` | One planned, cooking, eaten, or skipped meal | Planning and logging are one lifecycle, not duplicate objects |
| `ShoppingItem` | One needed/in-cart/bought line | Active cart and purchase-line history are one lifecycle |
| `Purchase` | One receipt/store transaction | Spending, merchant, date, total, and receipt image |
| `Workspace` | One household configuration record | Currency, timezone, week start, schema version, preferences, and sync health |

### Deferred extension records

Add only when the feature ships:

| Entity | Trigger |
|---|---|
| `MealPlanTemplate` + `MealTemplateEntry` | Saved/shareable reusable meal plans |
| `Profile` + `NutritionGoal` | More than one person needs separate nutrition targets |
| `FoodLot` | Batch-level expiry/price tracking becomes more valuable than one-row pantry simplicity |
| `Collection` | Users need curated recipe packs beyond tags/favorites |

Global `CatalogRecipe`, `Community`, `Post`, `User`, and recommendation features live in hosted product services. Saving/importing creates household `Recipe` and `RecipeIngredient` records with provenance.

## Canonical Field Rules

Every mutable record has hidden system fields:

| Key | Type | Rule |
|---|---|---|
| `id` | UUID text | Stable across every provider; never derived from row/page position |
| `version` | integer | Increment on accepted mutation |
| `updated_at` | instant | UTC machine timestamp |
| `archived` | boolean | Soft removal; no normal sync delete |
| `origin` | enum | `app`, `notion`, `sheets`, `postgres`, `import`, `automation`, `ai` |

Rules:

- Human titles are not identity.
- Empty means unknown; zero means known zero.
- Quantities are decimal plus unit; no combined `"2 cups"` source field.
- Dates and instants are typed values.
- Status values are finite enums shared by providers.
- Relations use stable IDs; human names are presentation only.
- Derived counts, totals, coverage, and dashboard values are never authoritative.
- AI confidence/provenance belongs in audit metadata, not in the wife-facing tables.

## Notion Workspace Plan

### Workspace hierarchy

```text
WonderFood Home
  Today
  This week
  Use soon
  Shopping
  Cook from our kitchen
  Spending this month
  Data
    Food
    Recipes
    Meals
    Shopping
    Purchases
    Recipe Ingredients (managed)
    Workspace (managed)
```

`WonderFood Home` is provisioned as a dashboard with linked views. Data sources live under `Data`, keeping the home calm. Managed sources remain editable but are visually de-emphasized.

### 1. Food

Primary properties:

| Label | Key | Notion type | Notes |
|---|---|---|---|
| Food | `name` | Title | Household-normalized name |
| On hand | `quantity_on_hand` | Number | Zero means out |
| Unit | `unit` | Select | `item`, `g`, `kg`, `ml`, `l`, `cup`, `tbsp`, `tsp`, `oz`, `lb` |
| Pantry state | `pantry_state` | Status | `In stock`, `Low`, `Out`, `Archived` |
| Category | `category` | Select | Produce, Dairy, Protein, Grain, Pantry, Frozen, Bakery, Beverage, Other |
| Location | `location` | Select | Pantry, Fridge, Freezer, Counter, Other |
| Best by | `best_by` | Date | Earliest useful household expiry |
| Low at | `low_at` | Number | Reorder threshold |
| Favorite | `favorite` | Checkbox | Fast pinning |
| Tags | `tags` | Multi-select | Dietary/custom organization |

Secondary/hidden properties: barcode, brand, image, calories/protein/carbs/fat/fiber per serving, serving amount/unit, source URL, system fields.

Notion views:

- `Kitchen`: table; active; grouped by Location; sort Category then Food.
- `Use soon`: gallery/list; active with Best by present; soonest first.
- `Low & out`: list; Pantry state is Low or Out.
- `Favorites`: gallery; Favorite checked.
- `Archived`: table; archived only.
- `Quick add`: form exposing Food, On hand, Unit, Location, Best by.

### 2. Recipes

Primary properties:

| Label | Key | Notion type | Notes |
|---|---|---|---|
| Recipe | `name` | Title | Saved title |
| Photo | `image` | Files | Gallery cover |
| Recipe state | `recipe_state` | Status | `Inbox`, `Want to try`, `Favorite`, `Regular`, `Archived` |
| Source | `source_url` | URL | Original URL |
| Tags | `tags` | Multi-select | Household labels |
| Cuisine | `cuisine` | Select | Optional |
| Diet | `diet` | Multi-select | Vegetarian, Vegan, Gluten-free, etc. |
| Servings | `servings` | Number | Base yield |
| Prep | `prep_minutes` | Number | Minutes |
| Cook | `cook_minutes` | Number | Minutes |
| Rating | `rating` | Number | 0-5 |
| Ingredients | `ingredients` | Relation | To Recipe Ingredients |
| Last cooked | `last_cooked_at` | Rollup | Latest related eaten Meal |

Secondary/hidden properties: calories/protein/carbs/fat/fiber per serving, author, source type, imported at, system fields.

The Notion page body is the canonical rich recipe document: short description, numbered instructions, notes, substitutions, and source attribution. Structured ingredients remain related rows. The app maps page blocks to canonical `instructions` and `notes`.

Notion views:

- `Recipe box`: gallery with photo, total time, rating, state.
- `Cook from kitchen`: gallery sorted by app-computed `match_percent` descending.
- `Want to try`: gallery filtered by state.
- `Favorites`: gallery.
- `Inbox`: table for newly imported recipes needing cleanup.
- `Quick save`: form exposing URL, title, tags.

Recipe page template:

```text
At a glance: servings | prep | cook | nutrition
Ingredients: linked view filtered to this recipe, sorted by Order
Directions: numbered blocks
Notes and substitutions
Cook this in WonderFood
```

### 3. Recipe Ingredients (managed)

| Label | Key | Notion type |
|---|---|---|
| Ingredient line | `name` | Title |
| Recipe | `recipe_id` | Relation to Recipes |
| Food | `food_id` | Relation to Food; optional until matched |
| Amount | `quantity` | Number |
| Unit | `unit` | Select |
| Preparation | `preparation` | Rich text |
| Optional | `optional` | Checkbox |
| Section | `section` | Select |
| Order | `sort_order` | Number |
| Have enough | `have_enough` | Checkbox/app-derived |

Users normally edit these inside a Recipe template. A top-level `Needs matching` view shows lines without a Food relation. The app can suggest and confirm mappings. This junction is the only deliberate complexity because it powers pantry matching, recipe search, shopping generation, scaling, substitutions, and nutrition.

### 4. Meals

| Label | Key | Notion type | Notes |
|---|---|---|---|
| Meal | `name` | Title | Usually recipe name or freeform meal |
| When | `scheduled_at` | Date with time | Calendar source |
| Slot | `meal_slot` | Select | Breakfast, Lunch, Dinner, Snack |
| Meal state | `meal_state` | Status | `Idea`, `Planned`, `Cooking`, `Eaten`, `Skipped` |
| Recipe | `recipe_id` | Relation to Recipes | Optional for freeform meals |
| Servings | `servings` | Number | Planned/consumed amount |
| People | `people_count` | Number | Household count |
| Shopping | `shopping_items` | Relation to Shopping | Generated needs |
| Notes | `notes` | Rich text | Leftovers, prep, event notes |

Secondary/hidden properties: eaten_at, nutrition totals, source plan/template ID, system fields.

Notion views:

- `Today`: list/timeline filtered to today.
- `This week`: calendar filtered current week.
- `Dinner board`: board by Meal state, filtered to Dinner.
- `History`: table, Eaten, newest first.
- `Ideas`: gallery/list, Idea state.
- `Add meal`: form.

Planning and logging are transitions on the same row: `Planned -> Cooking -> Eaten`. This removes duplicate plan-entry and meal-log records and makes Notion automation simple.

### 5. Shopping

| Label | Key | Notion type | Notes |
|---|---|---|---|
| Item | `name` | Title | Human shopping label |
| Shopping state | `shopping_state` | Status | `Need`, `In cart`, `Bought`, `Skipped` |
| Food | `food_id` | Relation to Food | Optional for one-off items |
| Amount | `quantity` | Number | Needed/bought amount |
| Unit | `unit` | Select | Shared units |
| Category | `category` | Select | Store grouping |
| For meals | `meal_ids` | Relation to Meals | Why it was added |
| Purchase | `purchase_id` | Relation to Purchases | Set when bought |
| Price | `line_total` | Number/currency | Optional actual cost |
| Store | `store` | Select | Optional |
| Added | `added_at` | Created time/date | Automation-friendly |

Notion views:

- `Shopping list`: list grouped by Category; Need and In cart only.
- `Cart`: board by Shopping state.
- `For this week`: list filtered by related meal dates.
- `Bought`: table newest first.
- `Quick add`: form with Item, Amount, Unit, Category.

Checking off an item transitions it to Bought; it is not deleted. This same row becomes the purchase line and can update Food quantity through the app or an automation.

### 6. Purchases

| Label | Key | Notion type | Notes |
|---|---|---|---|
| Purchase | `name` | Title | Merchant/date display |
| Merchant | `merchant` | Select/text | Store |
| Purchased | `purchased_at` | Date with time | Spending timeline |
| Total | `total` | Number/currency | Receipt total |
| Currency | `currency` | Select | Workspace default |
| Items | `shopping_items` | Relation to Shopping | Bought lines |
| Item total | `calculated_total` | Rollup | Sum of line totals |
| Receipt | `receipt_image` | Files | Capture/import |
| Purchase state | `purchase_state` | Status | `Draft`, `Confirmed`, `Needs review` |

Secondary/hidden properties: subtotal, tax, discount, payment note, system fields.

Notion views:

- `This month`: table, newest first.
- `Monthly spending`: chart grouped by purchased month, sum Total.
- `By store`: chart/table grouped Merchant.
- `Needs review`: receipt capture drafts.

### 7. Workspace (managed, one row)

Properties: Name, schema version, timezone, currency, week starts on, household servings, dietary preferences, excluded foods, calorie/protein/carbs/fat targets, last successful sync, sync state, and system fields.

This avoids repeating household settings on recipes, meals, or purchases. If distinct people later need distinct goals, migrate nutrition targets into `Profile` and `NutritionGoal` without changing food/recipe IDs.

### Notion automation contract

Useful generic triggers/actions:

- Shopping state becomes Bought -> app queues pantry increment and links Purchase.
- Food pantry state becomes Low/Out -> offer/create Shopping item.
- Meal becomes Planned -> generate missing Shopping items after pantry subtraction.
- Meal becomes Eaten -> decrement pantry and update Recipe Last cooked.
- Purchase becomes Confirmed -> finalize spending and pantry changes.
- Recipe created with Source URL -> app imports/extracts into the same page.

Automations must set `origin=automation`, preserve `id`, and use idempotency keys in the app audit log. Automations never directly rewrite derived dashboard values.

## Google Sheets Workbook Plan

### Tabs

```text
Home
Food
Recipes
Recipe Ingredients
Meals
Shopping
Purchases
Settings
```

Each data tab is a first-class Google Sheets Table named `wf_food`, `wf_recipes`, `wf_recipe_ingredients`, `wf_meals`, `wf_shopping`, or `wf_purchases`. `Settings` contains one `wf_workspace` row. `Home` contains only formulas, pivot summaries, charts, and links; it is never imported as source data.

Sheets uses the same canonical fields as Notion with these presentation changes:

- Relations become stable ID columns plus formula/display name columns where useful.
- Recipe instructions and notes use multiline text columns.
- File properties become URLs.
- Status/select fields become typed dropdown chips.
- Dates, date-times, numbers, currency, and booleans use native column types.
- System columns appear at the far right, grouped/hidden and warning-protected.

Provisioned workbook behavior:

- Freeze title/header rows and first identifying column.
- Apply food-oriented tab colors and restrained alternating rows.
- Add typed dropdowns for every finite enum.
- Add named filter views matching the Notion views where Sheets supports them.
- Add developer metadata to bind canonical entity/field keys independent of column position.
- Use table-aware append/update and record-ID lookup; never clear and rewrite whole human tabs.
- Protect headers, formulas, IDs, versions, and dashboard ranges with warning protection.
- Preserve user-added columns. Ignore unknown columns during sync.
- `Home` shows Today, This week count, Use soon, active cart, month spending, pantry value/waste when available.

Named filter views:

| Tab | Views |
|---|---|
| Food | Kitchen, Use soon, Low and out, Favorites, Archived |
| Recipes | Recipe box, Cook from kitchen, Want to try, Favorites, Inbox |
| Meals | Today, This week, Dinner, History, Ideas |
| Shopping | Shopping list, In cart, For this week, Bought |
| Purchases | This month, Needs review, By store |

Sheets formulas may calculate display names, totals, and dashboard summaries. The app reads canonical input columns and ignores derived formula columns, preventing formula locale or user edits from corrupting domain state.

## App Information Architecture: AI Home Spaces

The app is not a mobile spreadsheet viewer. It compresses frequent household loops into one or two actions.

### Primary navigation

| Space | Purpose | First surface |
|---|---|---|
| `Home` | What matters now | Today meals, use-soon food, cook-next suggestions, active cart chip |
| `Explore` | Find and save food ideas | Global 180k+ catalog plus household recipes, pantry-match search |
| `Plan` | Decide the week | Seven-day meal strip, AI plan, saved plan entry point |
| `Kitchen` | Know what we have | Scan/search pantry, quick quantity gestures, expiry/low state |

`Cart` is a top-sheet/drawer available everywhere, not a full destination. `Cook` is an immersive mode launched from a recipe or meal. The AI command bar is persistent on Home and available from every space.

### Friction-free actions

- Add food: camera/barcode/receipt/voice/text from global `+`; confirmation sheet, not a multi-page form.
- Save recipe: Android share target accepts URL, image, or text; AI extracts into Inbox.
- Plan meal: drag/tap a recipe onto a day; AI can fill gaps based on pantry and preferences.
- Build cart: one magic action computes required ingredients minus pantry and merges duplicates.
- Log meal: mark planned meal Eaten; unplanned quick log accepts photo/text/recipe.
- Cook: step-by-step mode with timers, scaling, substitutions, and pantry-aware missing items.

### AI responsibility

AI may:

- Normalize food and recipe names.
- Extract recipes from URL/image/text.
- Match recipe ingredients to Food records.
- Rank recipes by pantry coverage, expiry urgency, time, nutrition, and preference.
- Propose a tailored seven-day plan.
- Merge shopping quantities and suggest substitutions.
- Guide cooking and personalize a saved recipe as a fork/version.
- Explain nutrition/spending/waste patterns.

AI may not mutate the authority directly. It emits typed proposals that pass validation, preview/confirmation policy, command execution, audit, sync, and undo.

## Future Feature Fit

| Future capability | Data/architecture fit |
|---|---|
| Browse 180,000+ recipes | Hosted catalog queried by Explore; no bulk Notion/Sheets sync |
| Join cooking communities | Hosted identity/community service; sharing references catalog or published recipe versions |
| Save recipes from anywhere | Share target/import service -> household Recipe + Recipe Ingredients |
| Make and share meal plans | Meals now; add Plan Templates only for reusable/shareable plans |
| Create shopping lists | Shopping generated from Meals and Recipe Ingredients minus Food |
| AI-guided recipes | Cook mode uses canonical instructions/ingredients |
| Saved meal plans | Deferred Plan Template extension with stable recipe references |
| Personalize recipes with AI | Create a household recipe fork; retain source/provenance |
| Tailored 7-day meal plans | AI proposal over Recipes, Food, history, goals, and constraints |
| Search recipes by food list | Structured Recipe Ingredients + Food relation enables exact coverage |
| Automated pantry food list | Receipt/photo/barcode capture updates Food; optional FoodLot later |
| Track nutrition goal | Recipe/Food nutrition now; Profile/Goal extension when per-person tracking ships |

## Sync and Conflict Model

1. Pull remote changes since cursor into a provider-neutral change set.
2. Validate types, enums, relations, and stable IDs.
3. Apply accepted changes to SQLite cache.
4. Present invalid or ambiguous edits in a small conflict/review inbox.
5. Execute local/app/AI commands into outbox with idempotency key and expected version.
6. Push record-level patches, not full snapshots.
7. Confirm provider version/last-edited value, then clear outbox item.

Conflict policy:

- No local pending write: remote edit wins.
- Different fields changed: merge.
- Same field changed: newest accepted version wins only when safe; otherwise ask.
- Delete from Notion/Sheets: import as archive after confirmation/grace window, never immediate hard delete.
- Broken relation/name-only row: preserve and mark Needs review rather than dropping it.

Provider capabilities are explicit. Notion supports relations, rollups, page bodies, files, and rich views. Sheets supports tables, filter views, formulas, and arbitrary user columns. The domain cannot depend on a provider-only feature.

## Non-Destructive V1 to V2 Migration

Current source mapping:

| Current workspace | V2 target |
|---|---|
| Kitchen stock-lot rows | Aggregate by Food into Food; retain earliest expiry and summed compatible quantity |
| Recipes flattened ingredients/instructions | Recipe plus structured Recipe Ingredients and page/body instructions |
| Meal Plan rows | Meals with state Planned |
| Shopping rows | Shopping |
| Spending rows | Purchases |
| Dashboard rows | Discard as source; regenerate linked views/formulas/charts |

Migration flow:

1. Export immutable V1 backup.
2. Create a sibling `WonderFood V2` page/workbook tabs; do not rename or delete V1.
3. Build V2 schema, relations, views, validation, and presentation.
4. Transform and import records with a migration report: imported, merged, skipped, needs review.
5. Run relation and total checks.
6. Show side-by-side preview in app.
7. User confirms V2 as authority.
8. Mark V1 `Legacy - read only`; keep rollback reference.

Rollback switches the active workspace reference back to V1 and restores the pre-migration local snapshot. No provider content is deleted automatically.

## Implementation Phases

### Phase 0: Contract approval

- Approve entity count, Notion hierarchy, app navigation, and deferred extensions.
- Capture screenshots/wireframes for Notion Home, Sheets Home, and four app spaces.

Acceptance: user agrees the workspace is understandable without knowing WonderFood internals.

### Phase 1: Canonical schema registry

- Replace `WonderFoodWorkspaceSchema` flat export definitions with provider-neutral entity/field descriptors.
- Add enum, unit, relation, ownership, visibility, validation, and schema.org mapping metadata.
- Map current domain/Room models to the V2 contract.
- Add migration fixtures and provider contract tests.

Acceptance: the same fixture produces semantically equivalent SQLite, Notion, Sheets, and Postgres records without requiring identical physical columns.

### Phase 2: Notion-first adapter and workspace builder

- Upgrade gateway fully to Notion API `2026-03-11` database/data-source/view APIs.
- Create Data page, seven data sources, relations, rollups, forms, views, dashboard, and recipe template.
- Implement page-body recipe instruction read/write.
- Implement row-level pull/push, archive, cursor, conflict, and user-added-property preservation.
- Seed realistic household data.

Acceptance: wife can plan a meal, edit pantry quantity, add a shopping item, open a readable recipe, and inspect monthly spending entirely in Notion; app reflects each edit.

### Phase 3: Sheets-first adapter and workbook builder

- Create tables, column types, dropdowns, metadata, filter views, protected system columns, dashboard formulas, and charts.
- Replace clear-and-rewrite behavior with stable-ID row patches.
- Preserve unknown user columns and formulas.
- Seed the same fixture as Notion.

Acceptance: the same five workflows work in Sheets and return to the app; sorting and inserted columns do not break sync.

### Phase 4: Sync correctness and migration

- Implement bidirectional cursors, field-level patches, outbox retries, conflict inbox, soft archive, and V1-to-V2 migrator.
- Run contract tests against local fixtures and live disposable Notion/Sheets workspaces.
- Test offline edits, concurrent edits, malformed human rows, relation repair, and rollback.

Acceptance: no silent loss, duplicate record, full-table overwrite, or authority ambiguity.

### Phase 5: AI Home Spaces UI

- Build Home, Explore, Plan, Kitchen; cart drawer; Cook mode; global capture; AI proposal surfaces.
- Keep provider setup/status under Settings; daily UI is provider-neutral.
- Add deep links from app records to their Notion page or Sheets row.

Acceptance: add food, save recipe, plan meal, create cart, and log meal each require at most two intentional actions after capture.

### Phase 6: Future product services

- Catalog/search/import pipeline.
- Community and sharing service.
- Plan templates and sharing.
- Per-profile nutrition goals.
- Personalization/recommendation evaluation and safety.

Acceptance: global scale and social data do not pollute or destabilize household databases.

## Validation Matrix

| Layer | Required proof |
|---|---|
| Schema | Golden fixtures and cross-provider semantic parity tests |
| Notion | Live provisioned workspace screenshots plus API round-trip tests |
| Sheets | Live formatted workbook screenshots plus API round-trip/sort/column-insert tests |
| Local/Postgres | Backend contract suite and migration/rollback tests |
| Android | Focused unit/UI tests, build, emulator install/launch, rendered screenshots |
| Sync | Offline, retry, conflict, archive, automation-origin, and idempotency tests |
| Product | Wife completes five core workflows in Notion without app assistance |

## Explicit Rejections

- One giant universal table: simple initially, poor relations, views, validation, and automation.
- Exact identical visible columns across providers: leaks machine/schema.org jargon and discards provider strengths.
- Flattened recipe ingredients/instructions: blocks pantry match, scaling, cart generation, substitutions, and nutrition.
- Separate plan entry and meal log objects for the same meal: needless duplication for household use.
- Dashboard as an authoritative database: derived values become stale and conflict-prone.
- Mirroring all 180k recipes or community data into Notion/Sheets: slow, noisy, costly, and not household-owned data.
- Full-tab clear/rewrite sync: destroys user formulas, columns, edits, and trust.

## Immediate Next Action

Do not modify the live Notion page or Sheet again using the V1 schema. First implement Phase 1 and a disposable V2 fixture, then provision a sibling live V2 workspace for visual approval before migration.
