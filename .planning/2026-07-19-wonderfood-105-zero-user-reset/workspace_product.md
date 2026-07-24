# WonderFood standalone workspace product contract

## Product promise

Notion and Google Sheets are complete household tools, not dumps from WonderFood. A household can manage the kitchen, shopping, recipes, meals, and spending in either workspace for weeks without opening the Android app. WonderFood adds the fastest mobile actions, offline access, capture, normalization, matching, and AI.

## Shared household language

Both providers use the same visible concepts and labels:

| Household concept | Meaning |
|---|---|
| Kitchen | Food and non-food things currently owned |
| Shopping | Anything the household may buy, from groceries to batteries |
| Meals | Planned and completed meals in one calendar |
| Recipes | Saved recipes and cooking knowledge |
| Spending | Purchases, receipts, categories, merchants, and totals |
| Needs review | Rare invalid or overlapping high-risk changes |

Provider-native behavior may differ, but the visible terminology, statuses, categories, units, and seed examples remain aligned.

## Independent-use boundary

Works without WonderFood:

- Add, edit, archive, restore, sort, filter, and search household records.
- Track on-hand quantities, locations, expiry, and low-stock intent.
- Build and complete a mixed shopping cart.
- Save recipes and ingredient text.
- Plan and log meals.
- Record purchases and review monthly spending.
- Use dashboards, formulas, charts, views, templates, and provider-native automation.

Enhanced by WonderFood:

- Receipt, barcode, photo, voice, share-sheet, and recipe-URL capture.
- Ingredient normalization and recipe-to-inventory matching.
- AI meal plans, cart suggestions, nutrition estimates, and cooking coach.
- Offline Android use and sync between the app and selected workspace.
- Detailed inventory lots, purchase-line reconciliation, provenance, and high-risk conflict repair.

The workspace never pretends stale app-derived values are live. Every derived match, estimate, or AI field includes a last-refreshed timestamp or clearly remains a suggestion.

## Notion product

### Navigation

Keep navigation at two levels:

1. `WonderFood Home`
2. `Kitchen`
3. `Shopping`
4. `Meals`
5. `Recipes`
6. `Spending`
7. `Help & setup`

Advanced data sources live under `Help & setup` and stay out of daily navigation.

### WonderFood Home

Create a warm household dashboard with:

- Today meals.
- Use first: open or expiring kitchen items.
- Low stock and `Buy next` items.
- Active shopping count and estimated cart total.
- This month spending versus last month.
- Quick-create buttons/templates for kitchen item, shopping item, meal, recipe, and purchase.
- `Needs review` callout only when records exist.

### Kitchen data source

Daily properties:

- Item, kind, category, on hand, unit, location, best before, opened, low at, buy next, buy quantity, preferred store, notes, archived.

Views:

- `Kitchen now`: active items grouped by location.
- `Use first`: opened or expiring within seven days.
- `Low stock`: on hand at or below low-at quantity.
- `Buy next`: buy-next checked or low stock.
- `Non-food`: household, cleaning, care, medicine, pet, and other.
- `Archive`: archived items only.

One visible row represents the practical household item. Advanced stock lots are related in a supporting data source for app-created multi-lot cases, but a person never needs to open it for normal use.

### Shopping data source

Daily properties:

- Item, amount, unit, category, store, status, reason, needed for, estimated price, actual price, notes, archived.

Views:

- `Cart`: needed and in-cart rows, grouped by store then category.
- `Recipe gaps`: items created for planned meals.
- `Household`: non-food and staple items.
- `Bought`: purchased this week.
- `Skipped`: intentionally skipped rows.

The item relation is optional so arbitrary free-text shopping remains friction-free.

### Meals data source

Daily properties:

- Meal, date/time, slot, recipe, servings, status, leftovers, people, notes.

Views:

- `This week`: calendar.
- `Plan`: board grouped by meal slot.
- `Today`: compact list.
- `Cooked`: completed meal log.
- `Ideas`: proposed meals without a date.

Planning and logging use one database. Changing status from planned to cooked/eaten avoids duplicate records.

### Recipes data source

Daily properties:

- Recipe, source URL, image, cuisine, tags, servings, prep time, cook time, ingredient text, instructions, favorite, can make percent, missing items, last matched, archived.

Views:

- `Can make`: app match at or above the ready threshold.
- `Almost`: one or two missing items.
- `Favorites`: favorites first.
- `Quick`: short total time.
- `All recipes`: gallery.

Ingredient text is the human editing surface. A supporting normalized ingredient data source powers app matching and remains available as an advanced relation, not required daily work.

### Spending data source

Daily properties:

- Purchase, date, merchant, total, currency, primary category, food amount, non-food amount, tax, discount, receipt, status, notes.

Views:

- `This month`: current month, newest first.
- `Last month`: prior month.
- `By category`: board or chart.
- `By merchant`: grouped table.
- `Receipts`: gallery when attachments exist.
- `Needs details`: draft or unreconciled purchases.

A quick total is valid standalone input. Detailed purchase lines are optional and live in a supporting data source. WonderFood can later expand a receipt into lines without invalidating the original purchase.

### Notion templates and buttons

- Kitchen item template preselects active status and common units.
- Grocery, household, and medicine templates preselect appropriate kinds/categories.
- Meal template lays out recipe, servings, leftovers, and notes.
- Recipe template provides ingredient and step sections.
- Purchase template provides receipt attachment and reconciliation fields.
- Buttons create common rows; no button performs an irreversible delete.

### Notion binding and setup

1. User duplicates the official WonderFood template.
2. User creates/shares a Notion integration with the WonderFood Home page.
3. User pastes the Home URL and integration token into WonderFood advanced connection setup.
4. WonderFood probes every required data source, stores IDs/property IDs, creates a local `latest-safety` snapshot, previews the first import, then activates the workspace.
5. Renaming pages, visible views, or properties does not break binding because IDs are stored.

API provisioning may repair missing properties and seed records, but does not attempt to manufacture polished views because Notion does not expose view management through its API.

## Google Sheets product

### Visible tabs

1. `Home`
2. `Kitchen`
3. `Shopping`
4. `Meals`
5. `Recipes`
6. `Spending`
7. `Lists & Help`

### Home

Provide a clean dashboard with formula-driven cards and charts:

- Expiring in seven days.
- Low-stock count.
- Buy-next count.
- Active cart count and estimate.
- Meals planned this week.
- This month and last month spending.
- Food versus non-food spending.
- Category spending chart.

Every formula uses named tables/ranges, handles empty data, and avoids fragile whole-column references where practical.

### Kitchen table

Columns mirror Notion Kitchen. Use typed date/number/dropdown/checkbox columns, conditional colors for expiry and low stock, and saved views for Kitchen now, Use first, Low stock, Buy next, and Non-food.

### Shopping tab

Use two clear sections:

- `Buy from kitchen`: formula-driven view of Kitchen rows with `Buy next` checked or low stock.
- `Other shopping`: editable Table for free-text, recipe-gap, and one-off shopping lines.

This keeps standalone shopping useful without Apps Script. WonderFood normalizes both sections into canonical shopping lines when connected.

### Meals table

Columns mirror Notion Meals. Provide current-week filtering, meal-slot dropdowns, status chips, servings validation, and a simple seven-day planning grid fed from the table.

### Recipes table

Columns mirror Notion Recipes. Long ingredient/instruction text remains readable through wrapping and note-friendly widths. App-derived match fields are clearly labeled with last refresh.

### Spending table

Columns mirror Notion Spending. Use typed currency/date columns, category and merchant summaries, month helper fields, and pivot/chart outputs on Home. Quick totals work without detailed lines.

### Lists & Help

Keep editable household vocabularies for categories, locations, units, meal slots, statuses, stores, and currency. Include short instructions, schema version, last WonderFood sync, and repair guidance. Do not expose OAuth tokens or secrets.

### Hidden support tabs

- `_wf_meta`: schema version, workbook ID, last sync, table/column bindings.
- `_wf_lots`: advanced inventory lots.
- `_wf_ingredients`: normalized recipe ingredients.
- `_wf_purchase_lines`: detailed receipt lines.
- `_wf_bindings`: stable canonical IDs, row metadata IDs, hashes, and revisions.

Technical tabs are hidden and protected from accidental editing, but never contain credentials, raw AI prompts, or opaque whole-household snapshots.

### Sheets binding and setup

1. User signs in with Google OAuth.
2. User chooses `Create WonderFood workbook`, selects an existing workbook, or pastes a Sheet URL.
3. For a new workbook, WonderFood creates all tabs, Tables, typed columns, formats, validations, named ranges, formulas, charts, views, protections, metadata, and seed examples in idempotent batches.
4. For an existing workbook, WonderFood previews additions and never replaces unknown tabs/columns/formulas.
5. WonderFood creates `latest-safety`, previews first import, then activates the workbook.

No Apps Script, web app, connector server, public-edit link, or deployment is required.

## Conflict and recovery product

### Why retain conflicts at all

The app can be offline while a household member edits Notion or Sheets. Without a base comparison, a later push could silently replace quantities, prices, planned dates, or deletions. A small conflict mechanism prevents this data loss.

### Resolution matrix

| Situation | Default |
|---|---|
| Only workspace changed | Import automatically |
| Only app changed | Push automatically |
| Both changed different fields | Merge automatically |
| Both changed same low-risk text/tag/note | Workspace wins; retain app value in recovery history |
| Both changed same quantity or money | Needs review |
| Archive/delete overlaps any edit | Needs review |
| Ingredient relation or meal date/servings overlap | Needs review |
| Invalid value or duplicate stable ID | Quarantine in Needs review |

### Review surface

Show one focused card with the item, changed field, app value, workspace value, timestamp, and actions: `Use workspace`, `Use app`, `Edit`, or `Archive`. Do not expose revisions, hashes, cursors, or provider jargon.

## Seed household

Both templates use the same realistic starter examples:

- Milk, eggs, spinach, basmati rice, rasam powder, dish soap, batteries, and pain reliever.
- Low milk, spinach expiring soon, dish soap marked Buy next.
- Dosa breakfast, tomato rasam dinner, and leftovers meal.
- Recipes for tomato rasam and vegetable pulao with ingredient text.
- Shopping rows for milk, tomatoes, dish soap, and batteries.
- Purchases from a grocery store and a pharmacy with food/non-food totals.

Seed rows are visibly labeled examples and can be archived or removed without affecting schema.

## Acceptance proof

Notion proof:

- Duplicate template, bind, inspect every daily view, add/edit/archive in each core data source, calculate dashboard rollups, reconnect app, round-trip changes, exercise one high-risk conflict, and repair a renamed property.

Sheets proof:

- Create workbook, inspect every visible tab, verify formulas/charts with seed and empty data, sort/move/filter rows, add user column/formula, add/edit/archive in every core table, reconnect app, round-trip changes, exercise one high-risk conflict, and repair a moved/renamed column.

Independence proof:

- Disable WonderFood network/app execution, perform a complete weekly kitchen-shopping-meal-spending workflow in each provider, and confirm dashboards remain useful without any connector process.
