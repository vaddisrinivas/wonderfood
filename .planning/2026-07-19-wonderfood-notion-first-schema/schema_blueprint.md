# WonderFood Complete Schema Blueprint

## 1. Design rule

WonderFood has one semantic model with four storage projections. It does not force Notion, Sheets, SQLite, and Postgres to look physically identical.

```text
Canonical entities and commands
  -> Notion: relations, pages, linked views, templates, rollups
  -> Sheets: typed tables, ID relations, filter views, formulas
  -> SQLite: normalized offline cache, outbox, indexes
  -> Postgres: normalized authority, constraints, RLS/change feed
```

Uniform means the same entity IDs, fields, enum meanings, units, lifecycle rules, and relationships. Presentation labels and derived columns may differ by provider.

## 2. Standards profile

WonderFood uses standards as mappings, not as its internal object model.

| Concern | Standard/reference | Use |
|---|---|---|
| Recipes | schema.org `Recipe`, `HowToStep`, `HowToSection`, `HowToSupply` | Import/export and published structured data |
| Product identity | schema.org `Product`; GS1 Web Vocabulary/Digital Link | GTIN, brand, package quantity, lot and date marks |
| Quantities | schema.org `QuantitativeValue`; UCUM | Numeric amount plus unambiguous unit code |
| Nutrition | schema.org `NutritionInformation`; USDA FoodData Central; Android Health Connect `NutritionRecord` | Nutrient provenance, basis, values, meal export |
| Meals/calendar | schema.org `FoodEvent`; RFC 5545 concepts | Timezone, recurrence, scheduled meal instances |
| Shopping/finance | schema.org `ItemList`, `Demand`, `Order`, `OrderItem`, `Invoice`, `MonetaryAmount`, `PriceSpecification` | Lists, purchases, line prices, totals |
| Community | ActivityStreams 2.0; schema.org `Review` and `InteractionCounter` | Hosted publishing, comments, reactions, collections |
| Change patches | RFC 6902 JSON Patch semantics | Typed field-level commands and audit representation |
| Currency/time/language | ISO 4217, ISO 8601/RFC 3339, IANA timezone, BCP 47 | Portable values |

Schema.org names are never exposed as awkward household labels. Example: canonical `recipe.servings` maps to schema.org `recipeYield`, Notion `Servings`, Sheets `Servings`, and a numeric SQL column.

## 3. Shared value objects

### Record envelope

Every authoritative mutable record carries:

| Key | Type | Rule |
|---|---|---|
| `id` | UUID | Stable globally; never row number or Notion page ID |
| `household_id` | UUID | Tenant boundary |
| `version` | integer | Monotonic accepted-record version |
| `created_at` | instant | UTC |
| `updated_at` | instant | UTC |
| `archived_at` | instant nullable | Soft removal |
| `origin` | enum | `app`, `notion`, `sheets`, `postgres`, `import`, `automation`, `ai` |
| `field_versions` | map | Hidden per-field change clock for safe merges |

Provider IDs live in `RemoteBinding`; they never replace canonical IDs.

### Quantity

| Key | Purpose |
|---|---|
| `value` | Decimal amount; nullable for `to taste`/unknown |
| `unit_code` | UCUM where possible; WonderFood extension for `item`, `pinch`, `can`, `package` |
| `display_text` | Original human expression, such as `1 heaped cup` |
| `base_value` | Optional normalized mass/volume/count |
| `base_unit_code` | `g`, `mL`, or `{item}` |
| `conversion_source` | Label, USDA, product package, household calibration, estimated |
| `confidence` | Confirmed, estimated, inferred |

Volume-to-mass conversion is food-specific and is never applied without density/conversion provenance.

### Money

`amount` is decimal and `currency` is ISO 4217. Subtotal, tax, discount, tip, fees, and total remain distinct.

### Time

Instants are UTC plus original timezone. Meal plans also retain local date and IANA timezone so travel/DST does not move dinner to another day.

### Provenance

Imported/derived data records source URL, provider, external ID/version, author/publisher, license URL, capture time, content hash, confidence, and whether a human confirmed it.

## 4. Complete household domain

### A. Household and people

#### `Household`

One selected data home and shared defaults.

Core fields: name, timezone, locale, currency, week start, default servings, unit system, dietary preferences, excluded foods/allergens, schema version, active backend, last sync state.

#### `Member`

Optional until personal nutrition/community features are enabled.

Core fields: display name, role, locale, timezone, active, avatar, Health Connect link state. Do not store unnecessary personal or medical data.

#### `Preference`

Scope may be household or member. Fields: kind (`diet`, `allergen`, `dislike`, `favorite_cuisine`, `budget`, `prep_time`, `store`), target food/term, value, strength, source, valid dates.

#### `NutritionGoal`

Fields: member, nutrient code, period (`day`, `week`), minimum, target, maximum, unit, effective dates, provenance. Goals are not diagnoses and do not silently drive restrictive recommendations.

### B. Food, products, and inventory

#### `Food`

Household food concept, such as `basmati rice`, `egg`, or `sambar`.

Core fields: name, aliases, category, form, default unit, dietary/allergen tags, FoodOn URI optional, USDA FDC/NDB references optional, edible fraction, favorite, notes.

`Food` is the stable relation target for recipes. It is not a specific branded package or pantry batch.

#### `Product`

Specific packaged/commercial expression of a Food.

Core fields: food relation, product name, brand, GTIN/barcode, package quantity/unit, ingredients label, product image, label nutrition relation, Open Food Facts ID, GS1 Digital Link, country/market, source and last refresh.

#### `InventoryLot`

One quantity acquired together with shared location/date/cost facts. Most simple foods have one active lot; multiple lots appear only when expiry or cost differs.

Core fields: food, optional product, location, quantity, normalized quantity, acquired/opened/frozen dates, best-before, use-by/expiration, lot code, purchase line, unit cost, state (`sealed`, `open`, `frozen`, `depleted`, `wasted`, `archived`).

#### `InventoryMovement`

Immutable ledger entry for acquisition, consumption, adjustment, transfer, waste, return, or archive.

Core fields: lot/food, movement type, signed quantity, occurred time, from/to location, cause entity/type, actor/origin, idempotency key, note. Current lot quantity is a projection of confirmed movements, enabling undo and waste analysis.

### C. Recipes

#### `Recipe`

Stable household recipe identity.

Core fields: name, description, current state (`inbox`, `want_to_try`, `favorite`, `regular`, `archived`), image, author, source URL, source recipe ID, cuisine, course/category, tags, diets, cooking method, servings, prep/cook/total minutes, difficulty, estimated cost, rating, language, current revision, parent recipe/fork, license/provenance.

#### `RecipeRevision`

Immutable snapshot created on import, accepted edit, AI personalization, or publication.

Core fields: recipe, revision number, derived-from revision, reason, creator/origin, servings, content hash, ingredient/step snapshot, nutrition snapshot, created time. Normal human edits update Recipe and its parts; accepted checkpoints create revisions.

#### `RecipeIngredient`

One ordered quantity-bearing line.

Core fields: recipe, section, order, original display text, food relation nullable until matched, product preference nullable, quantity, preparation, optional, substitution group, scaling behavior (`linear`, `fixed`, `to_taste`), match confidence.

#### `RecipeStep`

One ordered instruction compatible with schema.org `HowToStep`/`HowToSection`.

Core fields: recipe, section, order, name, instruction, duration, timer seconds, temperature value/unit, referenced ingredients, media, tip/safety note.

#### `RecipeTool`

Optional relation for equipment that affects feasibility, such as pressure cooker or air fryer. Fields: recipe, tool term, required/optional, quantity.

### D. Plans, meals, and consumption

#### `MealPlan`

Supports both scheduled weeks and reusable templates.

Core fields: name, kind (`scheduled`, `template`), start/end dates nullable for templates, timezone, status, household servings, source plan/template, sharing state, notes.

#### `Meal`

One meal idea or occurrence. Planning and household completion remain one lifecycle.

Core fields: plan, scheduled date/time or template day offset, slot, name, recipe and pinned recipe revision, servings, people count, state (`idea`, `planned`, `prepping`, `cooking`, `served`, `eaten`, `skipped`), leftover source, recurrence rule optional, notes.

#### `MealConsumption`

Managed record only when nutrition/member tracking is enabled. This avoids duplicating everyday meals while allowing different members/portions.

Core fields: meal, member, consumed time, servings/quantity, nutrition snapshot, Health Connect external ID and sync state.

### E. Shopping

#### `ShoppingList`

Supports one default household cart plus future store/event-specific lists.

Core fields: name, status, store, owner, currency, plan, sort/group preference, created/completed dates.

#### `ShoppingItem`

Visible merged line the household checks off.

Core fields: list, label, food/product optional, requested quantity, category/aisle, priority, state (`need`, `in_cart`, `bought`, `skipped`), preferred store, assignee optional, note, purchased quantity, purchase relation.

#### `ShoppingDemand`

Managed contribution from one recipe ingredient, meal, low-stock rule, manual request, or automation to one ShoppingItem.

Core fields: shopping item, source type/ID, food, quantity, quantity after pantry subtraction, reason, generation run, satisfied/cancelled state. This allows deduplication without losing why an item is needed.

### F. Purchases and finance

#### `Purchase`

One receipt/order transaction.

Core fields: merchant, store location, purchased time, currency, subtotal, tax, discount, tip, fees, total, payment note, receipt attachment, state (`draft`, `needs_review`, `confirmed`, `returned`), capture/provenance.

#### `PurchaseLine`

One bought line, separate from ShoppingItem because partial buys, substitutions, returns, taxes, and price history differ from intent.

Core fields: purchase, shopping item optional, food/product optional, label, quantity, package quantity, unit price, list price, discount, tax allocation, line total, category, return state, created inventory lot.

Monthly spending, average product price, pantry valuation, and waste cost are derived from confirmed Purchase/PurchaseLine and InventoryMovement records.

### G. Nutrition

#### `NutritionProfile`

Reusable nutrition facts for a Food, Product, Recipe revision, Meal, or MealConsumption.

Core fields: subject type/ID, basis type (`per_100g`, `per_100ml`, `per_serving`, `per_recipe`, `consumed`), basis quantity, servings, source/provider, external food ID, captured time, confidence.

#### `NutrientValue`

Core fields: profile, nutrient code, amount, unit, daily-value percent optional, min/max optional. Nutrient codes map to USDA nutrient IDs and Health Connect fields where available.

The UI materializes energy, protein, carbohydrate, fat, fiber, sugar, sodium, saturated fat, and cholesterol. Micronutrients remain available without adding dozens of visible Notion properties.

### H. Files, provenance, automation, and sync

#### `Attachment`

URI/file reference, media type, kind, label, checksum, dimensions/duration, source and retention state.

#### `Attribution`

Subject, source URL, author/publisher, license, original identifier/version, imported time, content hash, extraction method, confirmation state.

#### `AuditEvent`

Append-only event: actor/origin, action, subject, timestamp, idempotency key, before/after patch, reason, AI confidence, confirmation, undo link.

#### `RemoteBinding`

Canonical record ID, backend type, workspace, provider object ID/page ID/table row metadata, provider version/last-edited timestamp, last content hash, sync cursor, state.

#### `Conflict`

Record/field, local value/version, remote value/version, detected time, classification, resolution, resolver and resolved time.

`OutboxCommand` and sync cursors remain SQLite/Postgres implementation tables; they are not wife-facing Notion databases.

## 5. Hosted catalog and community model

These entities never bulk-sync to household Notion/Sheets:

| Entity | Purpose |
|---|---|
| `CatalogRecipe` + revision | Searchable normalized recipe corpus and provenance |
| `CatalogFood/Product` | Shared identifiers and nutrition references |
| `Account` | Hosted identity; distinct from household Member |
| `Community` + `Membership` | Cooking groups and permissions |
| `Publication` | Published recipe or meal-plan version with visibility/license |
| `Collection` | Curated recipe/plan collection |
| `Post`, `Comment`, `Reaction`, `Follow` | ActivityStreams-aligned social layer |
| `ModerationEvent` | Reports, decisions, safety/audit |

Saving from Explore copies or links a versioned recipe into the household domain with Attribution. Editing creates a household fork; it never mutates the global source. Publishing sends an explicit immutable revision, not the household's live working page.

## 6. Notion projection

### Everyday databases

| Notion database | Canonical source | Household use |
|---|---|---|
| `Kitchen` | InventoryLot with Food/Product rollups | Quantity, location, expiry, low/out state |
| `Recipes` | Recipe | Rich pages, gallery, source, tags, time, servings |
| `Meals` | Meal | Today, calendar, planned-to-eaten lifecycle |
| `Plans` | MealPlan | Current week and reusable plans |
| `Shopping` | ShoppingItem | Default cart and optional lists |
| `Purchases` | Purchase | Receipts and spending views |
| `Goals` | NutritionGoal/Preference projection | Optional personal/household goals |

### Managed databases under `Data / System`

`Foods`, `Products`, `Recipe Ingredients`, `Recipe Revisions`, `Inventory Activity`, `Shopping Demand`, `Purchase Lines`, `Nutrition Facts`, `Members`, `Activity`, and `Workspace`.

`Recipe Steps` are represented as numbered recipe page blocks for excellent Notion reading and as managed step records internally. The adapter must round-trip supported blocks and preserve unknown page blocks.

### Notion Home

Provision linked views, not copied databases:

- `Today`: Meals today.
- `This week`: meal calendar.
- `Cook next`: Recipes ranked by pantry coverage, use-soon ingredients, time, and preferences.
- `Use soon`: Kitchen lots by expiry.
- `Shopping`: Need/In cart only, grouped category.
- `Spending`: this month total/chart and needs-review receipts.
- `Goals`: optional weekly nutrition progress.

Notion forms: Quick food, Save recipe URL, Add meal, Add shopping item, Add purchase. Recipe templates include at-a-glance, filtered ingredient view, directions, notes, source, and app deep link.

Everyday views hide system properties. Managed databases are unlocked for automation but clearly labeled `Managed by WonderFood`; unknown user properties and blocks are preserved.

## 7. Google Sheets projection

### Visible tabs

`Home`, `Kitchen`, `Recipes`, `Meals`, `Plans`, `Shopping`, `Purchases`, `Goals`.

### Hidden/grouped managed tabs

`_Foods`, `_Products`, `_RecipeIngredients`, `_RecipeRevisions`, `_InventoryActivity`, `_ShoppingDemand`, `_PurchaseLines`, `_NutritionFacts`, `_Members`, `_Activity`, `_Workspace`, `_Schema`.

Each source tab is a typed Sheets Table. Relations use canonical ID columns plus formula/display columns. System columns are grouped at the right, hidden, and warning-protected. Developer metadata binds entity and field keys independent of tab/column position.

Rules:

- Never clear/rewrite a human tab.
- Patch rows by canonical ID and field metadata.
- Preserve unknown columns, formulas, comments, views, and formatting.
- Inputs are typed table columns; formulas/pivots/charts are derived and ignored on import.
- Long recipe instructions remain in Recipes as multiline text or an optional `_RecipeSteps` table; no cell may be assumed to exceed Sheets' 50,000-character conversion limit.
- Batch API calls, target payloads below 2 MB, retry `429` with jittered exponential backoff.
- Archive old Activity rows to files/Postgres before workbook growth becomes a usability problem; never attempt to place the global catalog in a Sheet.

## 8. SQLite and Postgres projection

SQLite and Postgres implement the complete normalized model. SQLite always stores cache, outbox, remote bindings, conflicts, and recent audit events. In Local mode it is authoritative.

Postgres requirements:

- UUID primary keys and household foreign keys.
- Decimal numeric types for amounts/money.
- Check constraints for enums/ranges and unique idempotency keys.
- RLS by household/member in hosted mode.
- Immutable movement/audit/revision rows.
- Indexed active records, relation keys, dates, GTIN, external IDs, and sync cursors.
- Views matching Notion/Sheets everyday projections.
- Direct DSN mode only for trusted/internal use; normal Android uses a protected HTTP/PostgREST/Supabase boundary.

## 9. Cross-provider field policy

| Field class | Authority behavior |
|---|---|
| User input | Bidirectional |
| Relation | Bidirectional after ID validation; unresolved names go to review |
| Derived | Recomputed; never accepted as source truth |
| Provider decoration | Preserved but ignored by other providers |
| System ID/version | App-managed and protected/hidden |
| Unknown property/column/block | Preserve in provider; do not delete |
| AI suggestion | Proposal until accepted |

Notion formulas/rollups and Sheets formulas are presentation conveniences. The canonical engine independently computes safety-critical quantities, nutrition, and shopping demand.

## 10. Sync protocol

1. Discover schema manifest and provider capabilities.
2. Pull records changed after provider cursor/last edit.
3. Convert provider representation to canonical field patches.
4. Validate IDs, types, units, enums, relations, invariants, and authority.
5. Merge non-overlapping field changes using field versions.
6. Put unsafe same-field conflicts or ambiguous name-only relations in Conflict inbox.
7. Apply accepted changes to SQLite projection.
8. Execute local/AI/automation commands with idempotency key and expected version.
9. Push minimal provider patches in dependency order.
10. Confirm provider hash/version before acknowledging outbox.

Notion and Sheets direct-to-device modes poll on foreground and scheduled WorkManager opportunities; they cannot rely on inbound webhooks without a hosted callback. Postgres/Supabase may use change feeds. Offline operation never changes which backend is authoritative.

## 11. Workflow invariants

- Buying a product creates PurchaseLine -> InventoryLot -> acquisition movement.
- Marking ShoppingItem bought does not alone increase pantry until purchase/quantity is confirmed.
- Consuming a Meal creates consumption movements only after confirmation or trusted automation.
- Shopping generation creates Demand rows, subtracts compatible inventory, normalizes units, merges visible items, and preserves source reasons.
- Recipe personalization creates a new revision/fork with Attribution.
- Nutrition always states its basis and source.
- Archive is reversible; hard deletion requires explicit destructive action.
- AI and external automations cannot bypass validation, idempotency, audit, or user confirmation policy.

## 12. Future feature coverage

| Feature | Required records |
|---|---|
| Browse 180k+ recipes | Hosted CatalogRecipe/search |
| Communities | Account, Community, Membership, Publication, Activity |
| Save from anywhere | Attribution, Recipe, parts, revision |
| Make/share plans | MealPlan, Meal, Publication |
| Shopping lists | ShoppingList, Item, Demand |
| AI-guided cooking | Recipe revision, Step, Tool, timers |
| Saved plans | Template MealPlan with day offsets |
| AI personalization | Recipe fork/revision and provenance |
| Tailored seven-day plans | Preferences, Goals, history, inventory, recipes |
| Search by food list | Food relations on structured ingredients |
| Automated pantry | Product/lot/movement plus receipt/barcode capture |
| Nutrition goals | Member, Goal, Consumption, Nutrition profiles/values |
| Spending/waste | Purchase lines, lot costs, waste movements |

## 13. Migration from current V1

1. Freeze a provider-neutral V1 export and provider-native backup.
2. Create sibling V3 workspace/schema; do not mutate/delete V1.
3. Convert Food/StockLot to Food/Product/InventoryLot and opening-balance movements.
4. Convert recipes to structured parts; retain original ingredient/instruction text and provenance.
5. Convert plans/logs into MealPlan/Meal; match duplicate planned/eaten records conservatively.
6. Convert shopping and receipts into Lists/Items/Purchases/Lines; unresolved links enter review.
7. Convert nutrition snapshots with explicit basis/source.
8. Build views/templates/formulas after data import.
9. Compare counts, totals, quantities, relations, and sampled records.
10. User approves V3, then active backend points to V3; V1 becomes read-only Legacy.

Rollback restores the old workspace reference and pre-migration SQLite snapshot. Migration never deletes provider content.

## 14. Implementation order

1. Canonical value objects, entity registry, enums, standards mappings, and fixtures.
2. SQLite normalized migration plus compatibility bridge from current `FoodChatStore`/snapshot path.
3. Notion V3 disposable workspace builder and round-trip adapter.
4. Sheets V3 disposable workbook builder and round-trip adapter.
5. Postgres schema/API and shared backend contract tests.
6. Migration preview/review/rollback tooling.
7. AI Home Spaces UI and provider deep links.
8. Catalog/import, plan templates, nutrition/Health Connect, then communities.

## 15. Acceptance gates

- Semantic parity fixture passes for every backend.
- Notion wife test completes pantry update, recipe edit, plan, shopping, and spending without opening System databases.
- Sheets test survives row sorting, column movement, custom columns/formulas, and filtered views.
- Offline edits retry without duplicates.
- Same-field conflicts are visible and recoverable.
- Shopping math handles compatible units, pantry subtraction, multiple demand sources, and partial purchases.
- Nutrition values retain basis/source and export cleanly to Health Connect when authorized.
- Recipe import/export validates schema.org structured data and preserves license/attribution.
- V1 migration produces backup, report, preview, and rollback proof.
- Global catalog/community scale has no effect on household Notion/Sheets size.

## 16. Deliberate limits

- No clinical diet advice or diagnosis model.
- No global recipe corpus inside household backends.
- No CRDT complexity until real multi-writer evidence requires it; field versions and explicit conflicts first.
- No arbitrary EAV model for core fields; extensibility uses typed registries only for nutrients/preferences.
- No raw provider credentials in records, logs, Sheets, or Notion.
- No requirement that a household enable people, goals, lots, social, or finance to use basic recipes and shopping.
