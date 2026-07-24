# Food provider template contract

Status: active  
Date: 2026-07-23

Food records must carry enough structure for the app, Notion, Sheets, Chat, agents and MCP clients to render the same rich page.

## Canonical columns/properties

Every Food provider surface should include:

| Field | Required | Notes |
|---|---:|---|
| `id` | yes | Stable LifeOS record id. |
| `title` / `Name` | yes | Record page title. |
| `collection` | yes | One of the Food collections, e.g. `recipe`, `meal_plan`, `inventory`, `shopping_item`. |
| `status` | yes | Human status shown as a pill. |
| `meta` | yes | Short context line, e.g. `Recipe · 35 min · High protein`. |
| `body` | yes | Editable note/body. |
| `food_detail` / `Food detail` | recommended | JSON object matching `packages/domain-config/schemas/food-detail.v1.schema.json`. |
| `relations` / `Relations` | recommended | JSON array of `{ "name": "...", "target_id": "..." }`, or comma-separated target ids. |

## `food_detail` JSON

```json
{
  "nutrition": [["Calories", "~520 kcal"], ["Protein", "~24 g"]],
  "ingredients": [
    { "name": "Moong dal", "amount": "1 cup dry", "state": "available" },
    { "name": "Baby spinach", "amount": "1 bag", "state": "shopping" }
  ],
  "instructions": ["Rinse dal.", "Simmer until soft."],
  "logs": [["Planned", "Thursday dinner"]],
  "variations": ["Use frozen spinach if fresh is unavailable."]
}
```

The minimum shape above is enough for a useful page. The original WonderFood
schema adds optional sections that templates should preserve when present:

- `identity`: household food/product IDs, aliases, category, unit, allergen tags, USDA/Open Food Facts/GTIN refs.
- `products`: branded/package variants with GTIN, package quantity, label nutrition and source.
- `lots`: acquired/opened/frozen/use-by state for pantry batches.
- `movements`: acquisition, consumption, transfer, waste, return and adjustment ledger.
- `recipe`: servings, prep/cook time, source URL, current revision, structured steps, tools and revisions.
- `meal`: planned/cooking/eaten/skipped lifecycle, portions and Health Connect sync refs.
- `shopping`: list, aisle, state, requested/purchased quantities and demand reasons.
- `purchase`: merchant, subtotal/tax/discount/total and receipt lines.
- `nutrition_profile`: nutrient basis, serving, macros/micros, confidence and source.
- `provenance`: source URL/provider/version/hash/human-confirmed flags.
- `audit`: event, actor, versions, source IDs, idempotency key and Undo window.

Allowed ingredient states:

- `available`
- `needed`
- `shopping`
- `previous`

## Relations

Preferred JSON:

```json
[
  { "name": "plans", "target_id": "shopping-spinach" },
  { "name": "uses", "target_id": "pantry-yogurt" }
]
```

Fallback comma list:

```text
shopping-spinach, pantry-yogurt
```

Comma-list relations import as `supports`.

## Why this matters

Without these fields, the app can only show a thin note. With them, every Food page can show:

- full nutrition profile
- ingredients needed and available
- shopping-cart state
- previous items and substitutions
- cooking instructions
- prior cooking notes/logs
- variations
- exact product/package identity
- pantry lots, expiry and movement ledger
- recipe steps, revisions, equipment and scaling
- meal portions and Health Connect context
- shopping demand provenance
- receipt lines, prices, returns and spending
- linked records with titles and statuses
- source provenance

This is the contract Notion templates, Google Sheets workbooks, local SQLite, Chat, agents and MCP clients should share.

## Collection map restored from the original schema blueprint

Food now declares both everyday surfaces and managed correctness records:

| Everyday surface | Managed collections behind it |
|---|---|
| Kitchen | `food_item`, `product`, `inventory`, `inventory_lot`, `inventory_movement`, `ingredient` |
| Recipes | `recipe`, `recipe_revision`, `recipe_step`, `recipe_tool`, `nutrition_profile` |
| Meals | `meal_plan`, `meal_template`, `meal_log`, `meal_consumption` |
| Shopping | `shopping_list`, `shopping_item`, `shopping_demand` |
| Purchases | `purchase`, `purchase_line`, `store`, `attachment` |
| Household context | `household`, `member`, `preference`, `nutrition_goal`, `audit_event`, `source_record` |

Provider projections do not need identical physical layouts. They must preserve
stable LifeOS IDs, enum meanings, relation targets, source snapshots and the
rich `food_detail` JSON when a surface cannot represent a field natively.
