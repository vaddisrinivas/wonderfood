# Research and Decisions

## Verified Current State

- The current Android domain already models foods, stock lots, recipes, structured recipe ingredients/steps, meal plans/entries, meal logs, shopping items, receipts, nutrition snapshots, events, and relations.
- The current human workspace adapter flattens that richer model into Kitchen, Shopping, Meal Plan, Recipes, Spending, and Dashboard tables.
- Recipe ingredients/instructions and nutrition are currently serialized into rich-text properties, relations are exported as IDs, and Dashboard is written as rows.
- Current Google Sheets human tabs are cleared and rewritten during export, which is unsafe for a user-owned database with formulas, views, or custom columns.
- Current Notion/Sheets code is uncommitted work in a dirty main worktree and must be preserved until V2 migration is proven.

## User Intent

- Notion is the primary human experience because the user's wife prefers it.
- Google Sheets should be equally real and automation-friendly, not a blank transport layer.
- The app should become a custom, fast AI home-space over the selected database.
- The data model must support future recipe discovery, communities, import from anywhere, meal plans, shopping, guided cooking, personalization, pantry automation, and nutrition goals.
- Simplicity means few understandable human databases and low duplication, not loss of the structured relations required for useful automation.

## Platform Findings

### Notion

- Current Notion API version `2026-03-11` exposes views as first-class resources.
- The API can create linked database views with filters, sorts, table/board/calendar/timeline/gallery/list/form/chart layouts, and dashboard views.
- New databases receive a default table view; additional views can be provisioned programmatically.
- Current data-source properties include relations, rollups, formulas, status, unique ID, files, dates, and other types needed by the proposed schema.
- Pages can be created from data-source templates; recipe pages can therefore have consistent ingredients/directions/notes structure.
- Database automations can run against an entire database or a filtered view, making clear lifecycle statuses important.

### Google Sheets

- The Sheets API now supports first-class Tables with headers, typed columns, dropdowns, filters, views, table references, and names.
- Table columns support number, currency, percent, date, time, date-time, text, boolean, dropdown, and smart-chip types.
- Filter views are persistent named views and can be backed by a table.
- Developer metadata can bind semantic keys to sheets/rows/columns so column movement does not break app logic.
- Named/protected ranges and warning-only protection can guard IDs, formulas, and system areas while preserving human editability.

### Schema.org

- Schema.org remains useful for export/discovery mappings such as Recipe, NutritionInformation, FoodEvent, HowToSupply, and MonetaryAmount.
- Schema.org property names are web interchange vocabulary, not good household UI labels.
- The canonical registry should retain mappings while provider adapters use human labels and native types.

## Product Reference Findings

- Samsung Food frames the complete loop as recipe discovery/personalization, meal planning, shopping, connected cooking, and social sharing.
- AnyList's strongest loop is recipe -> calendar -> generated grocery list, with household sharing and fast list capture.
- Repeated user feedback around pantry apps asks for pantry-aware grocery subtraction, easier pantry maintenance, reusable plans, quick import from varied sources, widgets/at-a-glance views, and clear cooked/not-cooked state.
- The durable product loop is not a feature directory. It is `what we have -> what we can cook -> what we plan -> what we need -> what we bought/ate -> better next suggestion`.

## Core Decisions

| Decision | Reason |
|---|---|
| Notion-first workspace design | Primary real household user preference |
| Seven small data sources including one managed junction and one managed settings source | Minimal model that still supports exact matching, automation, and finance |
| Food catalog and pantry state unified | One household row per food is easy to understand and avoids duplicate catalog/stock concepts |
| RecipeIngredient remains structured | Quantity-bearing many-to-many relation cannot be represented correctly as a simple Recipe-Food relation |
| Meal planning and logging unified into Meal lifecycle | A planned dinner and eaten dinner are the same household event |
| Shopping row survives as bought purchase line | Removes a separate receipt-line database and preserves history |
| Dashboard is derived | Views, rollups, formulas, and charts should never sync as source records |
| Global catalog/community remain hosted | Household databases should contain saved/personal data, not platform-scale shared data |
| Visible provider schemas may differ | Semantic uniformity matters; identical physical layout would make each provider worse |
| Record-level patch sync | Required to respect user-owned databases and custom automation |
| Defer FoodLot/Profile/PlanTemplate until needed | Keeps initial Notion model approachable while preserving clean extension points |

## Sources

- https://developers.notion.com/guides/data-apis/working-with-views
- https://developers.notion.com/reference/view
- https://developers.notion.com/reference/property-object
- https://developers.notion.com/guides/data-apis/creating-pages-from-templates
- https://www.notion.com/help/category/database-views/all
- https://www.notion.com/help/category/automations
- https://developers.google.com/workspace/sheets/api/guides/tables
- https://developers.google.com/workspace/sheets/api/guides/metadata
- https://developers.google.com/workspace/sheets/api/guides/filters
- https://developers.google.com/workspace/sheets/api/samples/ranges
- https://schema.org/Recipe
- https://schema.org/NutritionInformation
- https://schema.org/FoodEvent
- https://schema.org/HowToSupply
- https://schema.org/MonetaryAmount
- https://samsungfood.com/
- https://anylist.net/meal-planning

## Complete Schema Research Pass

- schema.org Recipe permits structured ingredient lists and ordered HowToStep/HowToSection instructions; provenance, language, license, author, revision relationships, images, duration, cost, ratings, and diet suitability are relevant for a long-lived recipe model.
- Google recipe structured-data guidance reinforces keeping recipe yield when nutrition is per serving and retaining structured steps, sections, images, times, ingredients, cuisine/category, author, and publication metadata.
- GS1 distinguishes a general product/food concept from a trade product and from lot/date-mark facts. GTIN, net content, batch/lot, production, best-before, and expiration data justify separate Food, Product, and InventoryLot records.
- USDA FoodData Central distinguishes identifiers, food data types, branded GTIN products, food categories, serving measures, nutrient source/provenance, and revisions. Nutrition values need an explicit subject, basis, unit, source, and captured version.
- Open Food Facts provides barcode-oriented product ingredients, label nutrition, serving bases, images, and community-confirmed data. It is a reference/source, not household authority.
- UCUM exists to make quantities plus units unambiguous. Household display expressions still need preservation because culinary units and density conversions are contextual.
- Health Connect NutritionRecord models a timed meal, meal type, energy, macros, fats, vitamins, and minerals. Per-member MealConsumption and extensible NutrientValue records are necessary for responsible future integration.
- schema.org Order/OrderItem/Invoice/PriceSpecification concepts support separating shopping intent from purchase lines, prices, discounts, taxes, and totals.
- ActivityStreams 2.0 provides a suitable hosted community activity vocabulary. Social objects should not be added to household Notion/Sheets.
- RFC 5545 concepts support scheduled and reusable meal plans with local date, timezone, and optional recurrence.
- RFC 6902 patch semantics fit typed field-level mutation/audit commands better than whole-record rewrites.
- Sheets has a 10-million-cell workbook ceiling, a 50,000-character conversion caveat per cell, recommended request payloads below 2 MB, per-minute quotas, atomic batch behavior, and documented backoff requirements. This confirms that global catalog/activity scale must stay outside Sheets and sync must batch narrowly.

## Revised Decisions

- The first seven-record model remains the everyday projection, not the complete domain.
- Food concept, commercial Product, and InventoryLot are separate internally; Kitchen is a friendly InventoryLot projection.
- InventoryMovement is immutable and supplies quantity history, undo, waste, and automation safety.
- RecipeRevision, RecipeStep, and provenance are first-class managed records.
- Meal remains the shared planning lifecycle; optional MealConsumption handles per-person nutrition without duplicating daily planning.
- ShoppingItem is intent; PurchaseLine is actual commerce. ShoppingDemand preserves the many sources merged into one visible line.
- Nutrition uses subject/basis profiles plus nutrient values, with common nutrients projected for humans.
- MealPlan supports scheduled and template kinds from the start so saved/shareable plans do not require a redesign.
- Provider-visible schemas are projections over the complete domain, and derived fields are never authoritative.
- Catalog and community schemas are planned now but hosted separately.

## Implementation Findings

- `WonderFoodWorkspaceSchema` is the correct narrow insertion point for the first V3 slice because both Notion and Sheets consume it.
- The lower-level `WonderFoodSnapshotCodec` typed tabs should remain intact for lossless sync while the human workspace becomes friendlier.
- Existing Notion creation code needed URL and checkbox property support before the V3 projection could represent recipe sources and optional ingredients.
- Existing Sheets tests were still asserting the older Kitchen, Shopping, Meal Plan, Recipes, Spending, Dashboard projection.
- The safest first Sheets import path is reviewable `FoodDraft` conversion from friendly rows, not direct mutation of canonical records. This keeps user-owned spreadsheet edits explicit until field-version merge is implemented.
- Notion can use the same friendly workspace row importer as Sheets once page properties are normalized to header/value strings. This keeps provider-specific parsing separate from household meaning.
- Seed data should be generated through the same `FoodMemory` -> canonical snapshot exporter as live data. This avoids a pretty demo fixture that bypasses the real app bridge.
- Workspace projections need page-title fallbacks for human labels. Raw reason/category fields are not good display names when Food relations are absent.
- A conservative first canonical merge should update existing records by stable `identifier` before allowing new-row creation. This keeps provider edits useful while avoiding accidental duplicate foods/recipes from partially filled human rows.
- New provider rows need deterministic identifiers when humans leave the ID column blank. Table plus visible title is a reasonable first stable key for no-deployment Notion/Sheets workflows.

## Additional Sources

- https://ref.gs1.org/voc/
- https://ref.gs1.org/voc/Product
- https://ref.gs1.org/ai/
- https://fdc.nal.usda.gov/api-guide/
- https://fdc.nal.usda.gov/help/
- https://openfoodfacts.github.io/openfoodfacts-server/api/
- https://ucum.org/ucum
- https://developer.android.com/reference/androidx/health/connect/client/records/NutritionRecord
- https://developers.google.com/search/docs/appearance/structured-data/recipe
- https://www.w3.org/TR/activitystreams-core/
- https://www.rfc-editor.org/info/rfc5545/
- https://www.rfc-editor.org/info/rfc6902/
- https://schema.org/Order
- https://schema.org/OrderItem
- https://schema.org/Invoice
- https://schema.org/PriceSpecification
- https://developers.google.com/workspace/sheets/api/limits
- https://support.google.com/drive/answer/37603
