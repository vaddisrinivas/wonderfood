# LifeOS 2026 data-plane template

Status: source template model  
Date: 2026-07-23

This is the concrete model for the user-facing Notion template and Google
Sheets workbook. It is not a proof page. It is the data plane the app, Chat,
MCP clients and future domains should share.

Source file:

- `packages/domain-config/templates/lifeos-data-plane-template.v1.json`

Generated import artifacts:

- `packages/domain-config/templates/generated/notion-import.md`
- `packages/domain-config/templates/generated/template-summary.json`
- `packages/domain-config/templates/generated/sheets/*.csv`

Validation:

- `npm run generate:data-plane-template`
- `npm run check:data-plane-template`

## Product promise

- Notion is the beautiful, relation-heavy human workspace.
- Sheets is the structured workbook for spreadsheet-primary users.
- The app is a native/mobile/web surface over the same records.
- SQLite, Notion, Sheets or Postgres can be selected as the authority.
- No hosted bridge, webhook, tunnel, Mac, model provider or vendor is mandatory.

## Notion template

Navigation stays three levels deep:

1. `Today`
   - Today decision
   - Food command center
   - Use-first queue
   - Review before writing
   - Ask LifeOS
   - Connected sources
2. `Food`
   - Meals
   - Kitchen
   - Recipes
   - Shopping
   - Purchases
   - Household
3. `Sources` and `Settings`
   - Source records
   - Template health
   - Schema registry
   - Providers, domains, skills, schemas, workflows and appearance

Core Notion databases:

| Database | User job |
|---|---|
| LifeOS Records | Canonical import/export table and citation registry. |
| Kitchen | Pantry, products, lots, expiry and movement ledger. |
| Recipes | Recipes, revisions, steps, tools, substitutions and nutrition. |
| Meals | Meal plans, logs, servings, portions and Health context. |
| Shopping | Lists, cart state, demand reasons, stores and bought state. |
| Purchases | Receipts, line prices, returns, waste and attachments. |
| Household | Members, preferences, goals and constraints. |
| Sources and audit | Source snapshots, audit events, Undo windows and template QA. |

Important Notion buttons:

- Plan dinner from pantry
- Add receipt
- Log cooked
- Build shopping list

Buttons may create records or mark command requests. They are not required for
headless sync, and the app must still work if Notion buttons are absent.

## Google Sheets workbook

Workbook: `LifeOS 2026 Food Workbook`

Tabs:

| Tab | Job |
|---|---|
| Home | Setup checklist and readable overview. |
| Records | Canonical import/export table. |
| Relations | Edge list for relations. |
| Kitchen | Food items, products, inventory, lots and movements. |
| Recipes | Recipes, revisions, steps, tools and nutrition profiles. |
| Meals | Plans, templates, logs, consumption and observations. |
| Shopping | Shopping lists, items, demand reasons and stores. |
| Purchases | Receipts, purchase lines, prices, returns and attachments. |
| Household | Household, members, preferences and nutrition goals. |
| Sources | Source snapshots, audit events, sync receipts and citations. |
| Schema | Collection registry and validation notes. |

Sheets may contain user formulas and extra columns. Canonical writes update only
known columns and must preserve formulas, stable IDs, collection names,
`food_detail_json`, `relations_json` and source snapshot fields.

## Food collection coverage

The template covers all active Food collections:

- household, member
- food_item, product, inventory, inventory_lot, inventory_movement, ingredient
- recipe, recipe_revision, recipe_step, recipe_tool
- meal_template, meal_plan, meal_log, meal_consumption
- shopping_list, shopping_item, shopping_demand
- purchase, purchase_line, store
- preference, nutrition_goal, nutrition_profile, nutrition_observation
- attachment, audit_event, source_record

If the Food manifest changes, `npm run check:data-plane-template` must fail
until the Notion/Sheets template coverage and generated import artifacts are
updated.

## Generated artifacts

`notion-import.md` is the Notion build/import checklist: Today page, page tree,
database jobs, relation views, buttons and duplication-health checks.

`generated/sheets/*.csv` is the workbook starter pack. It includes:

- one setup `Home` tab
- one canonical `Records` tab with one starter row per Food collection
- one `Relations` edge-list tab with one row per Food relation
- domain tabs for Kitchen, Recipes, Meals, Shopping, Purchases, Household and Sources
- one `Schema` registry tab proving every collection has a Notion and Sheets home
