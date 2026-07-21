# Template Spike: Notion and Sheets Workspace References

## Scope

User asked to clone/test external Notion and Google Sheets templates so WonderFood's Notion and Sheets mapping becomes easier.

This spike inspected public templates only. No user Notion workspace or Google Drive file was mutated.

## Sources

- Notion: Tim Rawling, "Recipes, shopping list and meal plan" template: https://www.notion.com/templates/recipes-shopping-list-and-meal-plan
- Notion roundup with structure summary: https://www.notion4teachers.com/blog/best-notion-recipe-meal-planner
- Sheets: Smartsheet Restaurant Inventory template: https://docs.google.com/spreadsheets/d/11qMq3kMbVfnIkLkkuJJ7E4gBwLmyQx-_EdmNTV0FJZk/edit?usp=sharing
- Sheets: Smartsheet Simple Inventory template: https://docs.google.com/spreadsheets/d/1gtZs15cx7zBTVqDptIcBZYJpN7RuwoBsPHpdqKMWPIQ/edit?usp=sharing
- Sheets meal planner reference: https://www.lambertslately.com/meal-plan-template-google-sheets

## Clone/Test Result

### Notion

Chrome computer-use follow-up successfully duplicated the Tim Rawling template into the user's logged-in Notion workspace under Private.

Duplicated page:

- https://app.notion.com/p/manasa-srinivas/Recipes-shopping-list-and-meal-plan-6c65dd535a938364876f813daf62acb9

Visible duplicated structure:

- Page: `Recipes, shopping list and meal plan`
- Linked sections/databases: `Recipes`, `Meal plan`, `Ingredients`
- Recipes properties: `Recipe`, `Meal Type`, `Status`, `Category`, `Number of instances`, `Planned for`, `Recipe write-up`, relations to `Meal plan` and `Ingredients`
- Ingredients properties: `Ingredient`, `Status`, `Store`, `Type`, relation to `Recipes`
- Seed/status values observed: `Dinner`, `Lunch`, `Vegetable`, `Shopping List`

No WonderFood integration token or database binding was created in this spike.

Recommendation: borrow the information architecture, not the actual template as source of truth.

Recommendation: borrow the information architecture, not the actual template as source of truth.

Why:

- Public Notion marketplace page shows 4.7/5 from 95 ratings.
- Public reviews suggest useful recipe/list layout but one recent review reports broken ingredient-to-recipe relation.
- Third-party roundup describes the template as integrating ingredient database, meal planning, recipes, shopping-list automation, pantry inventory, weekly/monthly planning, and budget support.

Verdict: `Borrow pieces only`.

Use for:

- Dashboard/page ordering.
- Recipe + ingredient + meal + shopping mental model.
- Human-friendly recipe gallery/list views.

Do not inherit:

- Existing relations or property IDs.
- Any generated shopping-list relation behavior.
- Any hidden schema, because it is not proven stable through API binding.

### Google Sheets: Smartsheet Restaurant Inventory

Downloaded public workbook to `/tmp/wonderfood-smartsheet-restaurant-inventory.xlsx` and inspected with `openpyxl`.

Workbook tabs:

- `EXAMPLE Restaurant Inventory`
- `BLANK Restaurant Inventory`
- `DROPDOWN KEYS - DO NOT DELETE`
- `- Disclaimer -`

Relevant layout:

- Two inventory blocks per sheet section.
- Pantry, Freezer, Fridge, Beverages, Paper Goods and Disposables, Cleaning and Supplies.
- Headers: Brand, Item Name, Category, Needed for a Specific Recipe?, Last Purchased, Use-By Date, Quantity, Max Quantity, Need to Purchase, Notes.
- Formulas: `Need to Purchase = Max Quantity - Quantity`.
- Data validation: one dropdown list for Needed for Recipe? (`YES`/`NO`).

Verdict: `Adopt layout influence for Kitchen`.

Use for:

- Kitchen grouped by storage/location.
- Food + non-food visibility.
- Quantity, max/low stock, need-to-purchase formula.
- Use-by date and notes.
- Paper goods / cleaning supplies as proof that WonderFood is household, not food-only.

Do not inherit directly:

- Restaurant language.
- Two-block horizontal layout as canonical schema.
- Smartsheet marketing/disclaimer rows.
- Single YES/NO recipe-needed dropdown as the only relation mechanism.

### Google Sheets: Smartsheet Simple Inventory

Downloaded public workbook to `/tmp/wonderfood-smartsheet-simple-inventory.xlsx` and inspected with `openpyxl`.

Workbook tabs:

- `Simple Google Sheets Inventory`
- `BLANK- Inventory Template`
- `- Disclaimer -`

Relevant layout:

- Headers: Reorder (auto-fill), Item No., Name, Manufacturer, Description, Cost Per Item, Stock Quantity, Inventory Value, Reorder Level, Days Per Reorder, Item Reorder Quantity, Item Discontinued?
- Formulas: `Reorder = IF(stock < reorder level, "REORDER", "OK")`; inventory value = cost * quantity.
- No useful dropdown validation found.

Verdict: `Borrow formulas only`.

Use for:

- Reorder/low-stock formula pattern.
- Inventory value / spending estimate ideas.

Do not inherit:

- SKU/manufacturer/business stock-control language.
- Discontinued field.
- Days per reorder as required household input.

### LL Home Meal Planner

Could not download the actual workbook without the site's form/access flow. Public article was enough to validate the pattern.

Verdict: `Borrow meal planner pattern only`.

Use for:

- Weekly planner tab.
- Grocery list generated from planned meals.
- Favorite meals/recipe cards idea.

Do not inherit:

- Form-gated workbook.
- Separate recipe-card UX as canonical data shape.

## WonderFood Mapping Decision

Build WonderFood's own stable Notion/Sheets schema. Use external templates only as UX references.

### Notion Mapping

- `WonderFood Home`: borrow meal-planner dashboard feel.
- `Kitchen`: WonderFood-owned database. Borrow storage/location and low-stock views.
- `Shopping`: WonderFood-owned database. Borrow generated-from-plan mental model.
- `Meals`: WonderFood-owned database. Combine planner and log.
- `Recipes`: WonderFood-owned database. Use human ingredient text plus advanced normalized ingredients.
- `Spending`: WonderFood-owned database. External templates are weak here; use WonderFood schema.
- Hidden support sources: WonderFood-owned only.

### Sheets Mapping

Recommended visible tabs:

- `Home`
- `Kitchen`
- `Shopping`
- `Meals`
- `Recipes`
- `Spending`
- `Lists & Help`

Recommended hidden tabs:

- `_wf_meta`
- `_wf_lots`
- `_wf_ingredients`
- `_wf_purchase_lines`
- `_wf_bindings`

Kitchen columns should combine WonderFood contract with Smartsheet Restaurant Inventory:

- Identifier
- Item
- Kind
- Category
- Brand
- Location
- On hand
- Unit
- Low at
- Buy quantity
- Need to purchase
- Best before
- Opened
- Preferred store
- Buy next
- Notes
- Archived
- Updated at

Formula ideas:

- `Need to purchase = MAX(0, Buy quantity or Low at - On hand)`.
- Low stock indicator from `On hand <= Low at`.
- Home dashboard counts from named ranges/tables.

## Plan Update

Template references should be added as design inputs for C09-C19, not acceptance evidence.

Acceptance rows remain TODO/PASS based on actual WonderFood generated workspace proof, not because an external template exists.

Next implementation action:

1. Add a WonderFood-owned Sheets template generator that mimics the useful Restaurant Inventory storage/location groups.
2. Add Notion workspace provisioning notes/templates that use Tim Rawling-style recipe/ingredient/meal/shopping flow, but with WonderFood-owned database IDs/properties.
3. Run provider visual proof against WonderFood-generated workspaces, not third-party duplicates.
