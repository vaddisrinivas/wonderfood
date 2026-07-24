# Original WonderFood schema reconciliation

Status: active bridge  
Date: 2026-07-23

This file ties the pre-Expo WonderFood household schema back into the current
Expo LifeOS package model.

## Source plans

- `.planning/2026-07-19-wonderfood-notion-first-schema/schema_blueprint.md`
- `.planning/2026-07-19-wonderfood-notion-first-schema/task_plan.md`
- `.planning/2026-07-19-wonderfood-105-zero-user-reset/schema.md`
- `.planning/2026-07-19-wonderfood-105-zero-user-reset/architecture.md`
- `.planning/2026-07-19-wonderfood-105-zero-user-reset/workspace_product.md`

## Current package target

- `packages/domain-config/domains/food.v1.json`
- `packages/domain-config/schemas/food-detail.v1.schema.json`
- `docs/lifeos/food-provider-template-contract.md`

## Restored collection depth

The current Food package now keeps the original distinction between everyday
surfaces and managed correctness records.

| Original blueprint area | Current Food collections |
|---|---|
| Household and people | `household`, `member`, `preference`, `nutrition_goal` |
| Food/products/inventory | `food_item`, `product`, `inventory`, `inventory_lot`, `inventory_movement` |
| Recipes | `recipe`, `recipe_revision`, `ingredient`, `recipe_step`, `recipe_tool` |
| Plans/meals/consumption | `meal_template`, `meal_plan`, `meal_log`, `meal_consumption` |
| Shopping | `shopping_list`, `shopping_item`, `shopping_demand` |
| Purchases/finance | `purchase`, `purchase_line`, `store`, `attachment` |
| Nutrition/provenance/audit | `nutrition_profile`, `nutrition_observation`, `source_record`, `audit_event` |

## Restored rich detail sections

`food_detail` keeps the existing minimal record-page shape and adds optional
sections for the original model:

- `identity`
- `products`
- `lots`
- `movements`
- `recipe`
- `meal`
- `shopping`
- `purchase`
- `nutrition_profile`
- `provenance`
- `audit`

This lets Notion, Sheets, SQLite, Chat, agents and MCP clients preserve the
full household graph even when a provider surface cannot represent each field
natively.

## Guard

`npm run check:food-schema-depth` fails if the Food package loses the restored
managed collections, core relations, or rich detail value objects.

`npm run check:product` now includes that guard.

## Still pending

This bridge restores the contract. It does not by itself finish:

- real Notion-only and Sheets-only authority loops;
- provider Undo for all restored entities;
- receipt-to-lot-to-movement accounting;
- native/offline UI for every managed collection;
- Postgres authority adapter;
- full Health Connect nutrition/meal-consumption sync;
- complete visual/accessibility/performance release gates.
