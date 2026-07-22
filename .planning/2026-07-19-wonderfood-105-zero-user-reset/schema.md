# Canonical household schema

## Shared fields

Every persisted domain entity has:

- `id: UUID`
- `householdId: UUID`
- `createdAt: Instant`
- `updatedAt: Instant`
- `archivedAt: Instant?`
- `revision: Long`
- `source: SourceRef`
- `confidence: Confidence?`

`SourceRef` identifies manual UI, remote human edit, deterministic import, provider, AI proposal, receipt, recipe, shopping line, or system command. It carries enough external reference information for attribution without embedding credentials.

## Household and people

### Household

- name
- defaultCurrency
- timezone
- locale
- activeDataHome
- schemaVersion

### Profile

- displayName
- role
- diet tags
- allergies and hard exclusions
- dislikes and soft preferences
- nutrition goals
- budget sensitivity
- active weekdays/meal slots

Profiles are household configuration, not social accounts. Authentication identity stays provider-specific.

## Items and inventory

### Item

- name
- kind: food, household, cleaning, personal care, medicine, pet, other
- category
- brand
- barcode/GTIN identifiers
- aliases
- default unit
- preferred store
- refill threshold
- notes
- image/attachment references
- food details ID, nullable

### FoodDetails

- dietary tags
- allergen tags
- typical shelf-life hints
- default serving basis
- nutrition snapshot references
- external provider identifiers

### InventoryLot

- itemId
- quantity value and unit
- locationId
- purchasedAt
- openedAt
- expiresAt
- purchaseLineId
- unit cost, nullable
- status: available, opened, reserved, consumed, discarded, archived

### StorageLocation

- name
- type: fridge, freezer, pantry, cabinet, bathroom, garage, medicine, other
- parent location ID, nullable
- sort order

### InventoryEvent

- item/lot ID
- type: add, adjust, move, open, consume, discard, archive, restore
- quantity delta, nullable
- reason
- related command/meal/purchase

## Shopping

### ShoppingList

- name
- status: active, completed, archived
- store/merchant ID, nullable
- plannedFor, nullable

### ShoppingLine

- shoppingListId
- itemId, nullable for free text
- displayName
- quantity and unit, nullable
- category
- preferred store, nullable
- status: needed, in cart, purchased, skipped, archived
- reason: manual, low stock, recipe gap, household staple, receipt reorder, AI suggestion
- recipe/meal/source references
- estimated price, nullable

One active default list supports the simple cart UI; the entity permits saved/shared lists later.

## Recipes and cooking

### Recipe

- name and description
- source URL/provider/author
- cuisine/category/tags
- yield quantity and unit
- prep/cook/total duration
- difficulty
- status and visibility readiness
- image/attachment references
- nutrition snapshot references

### RecipeIngredient

- recipeId
- itemId, nullable until normalized
- original text
- quantity and unit, nullable
- preparation
- optional flag
- group/section
- order
- substitute item references

### RecipeStep

- recipeId
- section
- order
- instruction
- duration, nullable
- timer label, nullable
- ingredient references
- attachment references

### CookingSession

- recipeId
- started/finished timestamps
- current step
- servings
- state
- resulting prepared batch ID, nullable

### PreparedBatch

- recipe/meal ID
- preparedAt
- total quantity or portions
- remaining quantity or portions
- storage location
- consumeBy
- per-serving nutrition reference

## Meals and plans

### MealPlan

- name
- start/end dates
- status: draft, active, completed, archived, template
- target profile IDs
- budget and nutrition targets, nullable

### MealEntry

- mealPlanId, nullable for standalone log
- date/time
- slot
- recipeId, preparedBatchId, and free-text title as applicable
- servings
- status: proposed, planned, cooked, eaten, skipped, archived
- leftover count/intent
- nutrition snapshot references
- notes

Planned and logged meals share one entity so Notion/Sheets views do not duplicate the week.

## Purchases and expenses

### Merchant

- name
- category
- location/address text, nullable
- external identifiers

### Purchase

- merchantId, nullable
- occurredAt
- receipt attachment IDs
- subtotal, tax, discount, tip, and total money, nullable
- payment note, nullable
- status: draft, reviewed, reconciled, refunded, archived
- reconciliation difference, nullable

### PurchaseLine

- purchaseId
- itemId, nullable
- shoppingLineId, nullable
- displayName
- quantity and unit, nullable
- unit price, line subtotal, discount, tax allocation, and final amount, nullable
- spend category
- disposition: inventory, consumed, service, ignored
- inventory lot ID, nullable
- confidence/review state

### WasteEvent

- inventory lot ID
- quantity
- reason
- estimated cost, nullable
- occurredAt

Spending aggregates purchase lines. Known waste cost aggregates waste events linked to priced lots.

## Nutrition

### NutritionSnapshot

- subject entity reference
- basis quantity/unit
- energy, protein, carbohydrate, fat, fiber, sugar, sodium, nullable individually
- provider/source
- capturedAt
- confidence and warnings

Nutrition is a versioned observation, not mutable columns copied onto every entity.

## Attachments, proposals, and audit

### Attachment

- kind
- local URI and/or remote reference
- MIME type
- checksum
- label
- capturedAt

### ChangeProposal

- source kind and source payload reference
- requested canonical commands
- confidence and warnings
- status: pending, accepted, rejected, expired
- reviewedAt/reviewer

### CommandRecord

- command ID and type
- actor/device/source
- requestedAt/appliedAt
- affected entity IDs
- before/after hashes
- undo command reference, nullable

## Sync internals

### DataHomeConnection

- backend kind
- non-secret configuration
- credential reference
- status and schema version
- last health and sync timestamps

### RemoteBinding

- entity type and ID
- connection ID
- remote object/row ID
- property/column mapping version

### SyncBase

- binding ID
- local revision
- remote fingerprint/revision
- normalized base payload

### OutboxEntry

- command/entity ID
- operation
- idempotency key
- attempt count
- next attempt
- last error

### SyncConflict

- binding/entity
- base/local/remote normalized values
- conflicting fields
- severity and reason
- resolution status and command ID

### SyncCursor

- connection and entity scope
- opaque provider cursor/token
- updatedAt

## Schema.org boundary mapping

- `Recipe` maps to Schema.org `Recipe`.
- `RecipeStep` maps to `HowToStep`/`HowToSection`.
- `RecipeIngredient` imports from structured `PropertyValue` or original text.
- `NutritionSnapshot` maps to `NutritionInformation`.
- `Item` maps to `Product` where applicable.
- Purchase price observations can map to `Offer`/`MonetaryAmount` for import/export.
- Merchant maps to `Organization` or `Store` where applicable.

Provider-specific fields remain in adapter/source metadata rather than expanding the canonical entities for every external API.

## Human workspace projections

External Notion/Sheets tables intentionally omit command logs, raw proposal payloads, credentials, outbox internals, and complete sync bases. Those stay local or in protected provider metadata. Human-visible tables use stable identifiers but prioritize readable names, dates, statuses, quantities, relations, and summaries.

The human workspace is an input model as well as an output model. Provider adapters accept a deliberately small set of convenience fields that are easier for a household than maintaining normalized records:

- `Buy next` and `Buy quantity` on a kitchen item can produce or update a canonical shopping line.
- Free-text recipe ingredients can produce reviewed normalized `RecipeIngredient` commands.
- A quick purchase total can produce a canonical purchase with an uncategorized summary line until detailed lines are added.
- A single visible on-hand quantity can adjust the item's primary lot; advanced multiple lots remain available in supporting tables.
- Archive/status changes map to canonical archive commands rather than physical deletion.

These convenience fields are not duplicate durable domain models. They are documented command inputs with deterministic normalization, provenance, and validation.

### Standalone workspace contract

- Notion and Sheets must support kitchen, shopping, recipes, meals, and spending without WonderFood running.
- Each visible record has a stable hidden/bound identifier, but a missing identifier on a new human row is valid and receives one on the next sync.
- Formulas, views, charts, user notes, and unsupported user-added columns are provider-owned and survive routine sync.
- Provider deletion becomes archive review unless the row is unchanged since the last base.
- App-derived fields such as inventory match percentage show their last refreshed value and label when WonderFood last calculated them.

## Future-feature readiness

This schema supports later work without shipping it now:

- Browse large external recipe catalogs through source/provider IDs.
- Save recipes from URLs and shares.
- Personalize recipes through proposals and recipe versions.
- Save/reuse meal plans through template status.
- Tailored seven-day plans through profiles and goals.
- Search recipes by inventory coverage.
- Automate pantry from receipts/barcodes.
- Track nutrition goals through profiles and meal entries.
- Share plans/lists through a future hosted household service.
- Add communities later through separate account, publication, and moderation models rather than polluting private household data.
