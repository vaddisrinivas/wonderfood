# WonderFood-Owned Notion and Sheets Workspace Mapping

## Decision

Use the cloned/public templates as workflow references only. WonderFood generates and binds its own Notion databases, Sheets tabs, IDs, hidden support tables, formulas, seed data, and review surfaces.

References to borrow:

- Tim Rawling Notion template: recipe -> ingredient -> meal plan -> shopping-list mental model.
- Smartsheet Restaurant Inventory: Kitchen grouped by pantry, freezer, fridge, beverages, paper goods, cleaning, and supplies.
- Smartsheet Simple Inventory: low-stock/reorder and inventory-value formula pattern.
- LL Home meal planner: weekly plan -> grocery list pattern.

References to reject:

- Third-party Notion relation/property IDs.
- Food-only workspace shape.
- Restaurant/SKU/business inventory language.
- Apps Script, macros, or hidden automations.
- Provider-visible sync bases, secrets, raw snapshots, or private prompts.

## Canonical Provider Contract

Every provider row has:

- `identifier`: stable WonderFood entity ID or deterministic row ID.
- `updated_at`: provider-visible edit timestamp when supported.
- `_wf_type`: hidden/system binding type where needed.
- `_wf_revision`: hidden/system revision.
- `_wf_source`: hidden/system source/provenance label.
- `_wf_archived`: hidden/system archive marker when the visible database lacks an archive filter.

Human-editable fields are whitelisted per table. Unknown provider columns are preserved. Renames do not break bindings when provider IDs are available.

## Notion Workspace

Top navigation stays at two levels:

1. `WonderFood Home`
2. Daily databases: `Kitchen`, `Shopping`, `Meals`, `Recipes`, `Spending`, `Needs Review`
3. Support databases under `Help & setup`: `_wf_meta`, `_wf_lots`, `_wf_ingredients`, `_wf_purchase_lines`, `_wf_bindings`

### WonderFood Home

Purpose: daily dashboard, not canonical storage.

Contains:

- Today meals.
- Use-first kitchen items.
- Low-stock and buy-next items.
- Active cart count and estimated cart total.
- This month spending summary.
- Quick-create buttons/templates for kitchen item, shopping item, meal, recipe, and purchase.
- Needs Review callout only when review records exist.

### Kitchen

Canonical concepts: `Item`, practical current stock, low-stock intent, storage.

Visible properties:

| Property | Type | Human edit | Notes |
|---|---|---:|---|
| Item | title | yes | Display name. |
| Kind | select | yes | food, household, cleaning, personal care, medicine, pet, other. |
| Category | select/text | yes | Household-friendly category. |
| Brand | text | yes | Optional. |
| Location | select | yes | Pantry, Freezer, Fridge, Beverages, Paper Goods, Cleaning, Supplies, Other. |
| On hand | number | yes | Blank means unknown, not zero. |
| Unit | select | yes | count, g, kg, oz, lb, ml, L, cup, tbsp, tsp, serving, pack, other. |
| Low at | number | yes | Reorder threshold. |
| Buy quantity | number | yes | Preferred replenishment amount. |
| Need to purchase | formula/rollup | no | `max(0, Buy quantity or Low at - On hand)` where provider supports formula. |
| Best before | date | yes | Optional. |
| Opened | date/checkbox | yes | Date preferred; checkbox acceptable fallback. |
| Preferred store | text/select | yes | Optional. |
| Buy next | checkbox | yes | Manual override. |
| Notes | text | yes | User notes. |
| Archived | checkbox | yes | Soft archive. |

Required views:

- `Kitchen now`: active items grouped by Location.
- `Use first`: opened or expiring within seven days.
- `Low stock`: On hand <= Low at.
- `Buy next`: Buy next checked or low stock.
- `Non-food`: household, cleaning, personal care, medicine, pet, other.
- `Archive`: archived only.

### Shopping

Canonical concepts: `ShoppingList`, `ShoppingLine`.

Visible properties:

| Property | Type | Human edit | Notes |
|---|---|---:|---|
| Item | title | yes | Free text allowed. |
| Amount | number | yes | Optional. |
| Unit | select | yes | Optional. |
| Category | select/text | yes | Includes food and non-food. |
| Store | text/select | yes | Optional. |
| Status | select | yes | needed, in cart, purchased, skipped, archived. |
| Reason | select/text | yes | manual, low stock, recipe gap, staple, receipt reorder, AI suggestion. |
| Needed for | relation/text | yes | Recipe/meal/source note. |
| Estimated price | number | yes | Optional. |
| Actual price | number | yes | Optional. |
| Notes | text | yes | User notes. |
| Archived | checkbox | yes | Soft archive. |

Required views:

- `Cart`: needed and in-cart, grouped by Store then Category.
- `Recipe gaps`: Reason is recipe gap.
- `Household`: non-food/staple rows.
- `Bought`: purchased this week.
- `Skipped`: skipped rows.

### Meals

Canonical concepts: `MealPlan`, `MealEntry`, meal log.

Visible properties:

| Property | Type | Human edit | Notes |
|---|---|---:|---|
| Meal | title | yes | Recipe name or free text. |
| When | date | yes | Date/time. |
| Slot | select | yes | breakfast, lunch, dinner, snack, prep, other. |
| Recipe | relation/text | yes | Optional relation to Recipes. |
| Servings | number | yes | Optional. |
| Status | select | yes | proposed, planned, cooked, eaten, skipped, archived. |
| Leftovers | number/text | yes | Optional. |
| People | text/multi-select | yes | Optional. |
| Notes | text | yes | User notes. |

Required views:

- `This week`: calendar.
- `Plan`: board grouped by Slot.
- `Today`: compact list.
- `Cooked`: completed/eaten meals.
- `Ideas`: proposed meals without date.

### Recipes

Canonical concepts: `Recipe`, visible ingredient text, optional normalized ingredients.

Visible properties:

| Property | Type | Human edit | Notes |
|---|---|---:|---|
| Recipe | title | yes | Display name. |
| Source URL | url | yes | Optional. |
| Image | files/url | yes | Optional. |
| Cuisine | select/text | yes | Optional. |
| Tags | multi-select | yes | Optional. |
| Servings | number | yes | Optional. |
| Prep time | number | yes | Minutes. |
| Cook time | number | yes | Minutes. |
| Ingredient text | text | yes | Primary human surface. |
| Instructions | text | yes | Primary human surface. |
| Favorite | checkbox | yes | Optional. |
| Can make percent | number | no | App-derived suggestion. |
| Missing items | text | no | App-derived suggestion. |
| Last matched | date | no | App-derived freshness marker. |
| Archived | checkbox | yes | Soft archive. |

Required views:

- `Can make`: ready threshold met.
- `Almost`: one or two missing items.
- `Favorites`: favorites first.
- `Quick`: short total time.
- `All recipes`: gallery/list.

### Spending

Canonical concepts: `Purchase`, optional detailed `PurchaseLine`.

Visible properties:

| Property | Type | Human edit | Notes |
|---|---|---:|---|
| Purchase | title | yes | Merchant/date summary. |
| Date | date | yes | Purchase date. |
| Merchant | text/select | yes | Optional. |
| Total | number | yes | Quick total allowed. |
| Currency | select | yes | Default household currency. |
| Primary category | select | yes | food, household, cleaning, personal care, medicine, pet, other. |
| Food amount | number | yes | Optional split. |
| Non-food amount | number | yes | Optional split. |
| Tax | number | yes | Optional. |
| Discount | number | yes | Optional. |
| Receipt | files/url | yes | Optional. |
| Status | select | yes | draft, reviewed, reconciled, refunded, archived. |
| Notes | text | yes | User notes. |

Required views:

- `This month`: current month, newest first.
- `Last month`: prior month.
- `By category`: grouped.
- `By merchant`: grouped.
- `Receipts`: attachment gallery.
- `Needs details`: draft/unreconciled.

### Needs Review

Canonical concepts: high-risk conflict and invalid provider edits.

Visible properties:

| Property | Type | Human edit | Notes |
|---|---|---:|---|
| Review | title | no | Human-readable issue. |
| Area | select | no | Kitchen, Shopping, Meals, Recipes, Spending. |
| Field | text | no | Conflicted field. |
| Base value | text | no | Last common value. |
| App value | text | no | Local value. |
| Data home value | text | no | Provider value. |
| Source | text | no | Actor/source summary. |
| Detected at | date | no | Timestamp. |
| Action | select | yes | use app, use data home, edit manually, ignore. |
| Notes | text | yes | User decision notes. |

### Notion Support Databases

Support databases are under `Help & setup`; not daily navigation.

| Database | Purpose | Human edit |
|---|---|---:|
| `_wf_meta` | schema version, workspace ID, household ID, sync health, last safety snapshot marker | no |
| `_wf_lots` | multi-lot inventory, purchase linkage, expiry/source details | advanced only |
| `_wf_ingredients` | normalized recipe ingredient rows for app matching | advanced only |
| `_wf_purchase_lines` | receipt lines and spending reconciliation detail | advanced only |
| `_wf_bindings` | Notion database/property IDs and stable binding metadata | no |

## Google Sheets Workbook

Visible tabs:

- `Home`
- `Kitchen`
- `Shopping`
- `Meals`
- `Recipes`
- `Spending`
- `Needs Review`
- `Lists & Help`

Hidden tabs:

- `_wf_meta`
- `_wf_lots`
- `_wf_ingredients`
- `_wf_purchase_lines`
- `_wf_bindings`

### Sheet Tables

Use one named table per visible data tab:

| Tab | Table name | Key columns |
|---|---|---|
| Kitchen | `KitchenTable` | Identifier, Item, Kind, Category, Brand, Location, On hand, Unit, Low at, Buy quantity, Need to purchase, Best before, Opened, Preferred store, Buy next, Notes, Archived, Updated at |
| Shopping | `ShoppingTable` | Identifier, Item, Amount, Unit, Category, Store, Status, Reason, Needed for, Estimated price, Actual price, Notes, Archived, Updated at |
| Meals | `MealsTable` | Identifier, Meal, When, Slot, Recipe, Servings, Status, Leftovers, People, Notes, Archived, Updated at |
| Recipes | `RecipesTable` | Identifier, Recipe, Source URL, Cuisine, Tags, Servings, Prep time, Cook time, Ingredient text, Instructions, Favorite, Can make percent, Missing items, Last matched, Archived, Updated at |
| Spending | `SpendingTable` | Identifier, Purchase, Date, Merchant, Total, Currency, Primary category, Food amount, Non-food amount, Tax, Discount, Receipt, Status, Notes, Archived, Updated at |
| Needs Review | `NeedsReviewTable` | Identifier, Review, Area, Field, Base value, App value, Data home value, Source, Detected at, Action, Notes, Updated at |

### Sheet Formulas

Required formula behavior:

- Kitchen `Need to purchase`: `MAX(0, IF([Buy quantity] blank, [Low at], [Buy quantity]) - [On hand])`; blank On hand stays unknown and should not produce false zero certainty.
- Kitchen low-stock helper can use `AND([On hand] not blank, [Low at] not blank, [On hand] <= [Low at])`.
- Shopping `Buy from kitchen` section reads Kitchen rows where `Buy next` is true or low-stock helper is true.
- Home counts active cart, low stock, expiring in seven days, meals this week, this-month spend, last-month spend, food spend, and non-food spend.
- Spending category chart reads `SpendingTable`, not hardcoded cells.

### Sheet Data Validation

Dropdown lists live in `Lists & Help` and named ranges:

- Item kinds.
- Units.
- Storage locations.
- Shopping statuses.
- Shopping reasons.
- Meal slots.
- Meal statuses.
- Purchase statuses.
- Spending categories.
- Review actions.
- Currencies.

Protected or app-owned columns:

- Identifier.
- Updated at.
- App-derived match fields.
- Formula output columns.
- Hidden `_wf_*` tabs.

User-added columns beyond WonderFood schema must be preserved on write.

## Implementation Targets

Worker C should replace older provider workspace names with this product surface:

- Collapse `Plans` into `Meals` unless an internal support table is necessary.
- Collapse `Purchases` into `Spending` for daily navigation.
- Remove daily `Goals` from v1.0.5 provider scope unless docs/tests explicitly prove it.
- Keep advanced tables hidden/support-only with `_wf_` names.
- Add `Needs Review` to both Notion and Sheets as the visible conflict surface.
- Use `identifier` as the stable upsert key, with provider-native IDs stored in bindings.

Acceptance links:

- C09-C14: Notion generated workspace and live proof.
- C16-C19: Sheets generated workbook, idempotent writes, and live proof.
- C25: visual inspection of generated Notion and Sheets with seed data while app is offline.
