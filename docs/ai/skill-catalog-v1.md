# WonderFood AI Skill Catalog v1

Catalog id: `wf.ai.skill-catalog.v1`
Envelope id: `wf.ai.command-envelope.v1`
Status: Wave 0 contract for WF-C01

This catalog defines provider-facing skills. A skill interprets evidence and emits
typed command envelopes. Product logic owns validation, matching, persistence, undo,
navigation execution, and user confirmation.

## Shared Rules

- Emit one envelope per user turn.
- Prefer `needs_clarification` over guessing when target, quantity, or safety impact
  is unclear.
- Use warnings for uncertainty; use confirmation for uncertain or destructive mutation.
- Use stable command types only. Never invent a command type.
- Never output SQL, table names, DAO names, Room entities, raw migrations, or generic CRUD.
- Attach evidence refs to every command.
- Keep provider-specific metadata out of payloads.

## Skills

### 1. Inventory

- Skill id: `inventory`
- Skill version: `1.0.0`
- Purpose: turn food acquisition, storage, movement, usage, and archive language into
  inventory lot proposals.
- Input evidence: user text, receipt text/image, barcode result, inventory snapshot,
  preference snapshot, app context.
- Commands: `inventory.add_lot`, `inventory.adjust_quantity`, `inventory.move_lot`,
  `inventory.archive_lot`.
- Confirmation: required for quantity decreases, archive, ambiguous item match,
  ambiguous unit, possible duplicate merge, or allergy-relevant substitutions.
- Warnings: `ambiguous_quantity`, `ambiguous_target`, `possible_duplicate`,
  `stock_decrease_uncertain`, `destructive_action`.
- Confidence: high only when item names, quantities, and storage zones are explicit.
- Unsupported: "clean out expired stuff" without item targets; requests to hard delete.
- Golden fixture: `fixtures/golden/inventory-add-lots.json`.

Example user intent:

```text
I bought a dozen eggs and two bags frozen berries.
```

Expected commands:

- `inventory.add_lot` for eggs
- `inventory.add_lot` for frozen berries

### 2. Shopping

- Skill id: `shopping`
- Skill version: `1.0.0`
- Purpose: manage to-buy items and shopping completion proposals.
- Input evidence: user text, shopping snapshot, recipe snapshot, plan snapshot,
  receipt text/image, preference snapshot.
- Commands: `shopping.add_item`, `shopping.mark_item_bought`, `shopping.remove_item`.
- Confirmation: required for removal, purchased conversion with uncertain quantity,
  or duplicate suppression.
- Warnings: `possible_duplicate`, `ambiguous_quantity`, `receipt_line_uncertain`.
- Confidence: high when request uses need/buy/to-buy language and item names are clear.
- Unsupported: store price comparison, payment, or delivery ordering.
- Golden fixture: `fixtures/golden/shopping-add-items.json`.

### 3. Recipes

- Skill id: `recipes`
- Skill version: `1.0.0`
- Purpose: create or revise structured personal recipes from user text, pasted recipes,
  or cooked-meal descriptions.
- Input evidence: user text, recipe snapshot, inventory snapshot, preference snapshot,
  source URL/title when provided as text.
- Commands: `recipe.save_structured`, `recipe.update_structured`, `recipe.archive`.
- Confirmation: required when overwriting existing recipe text, archiving, or using
  ambiguous servings/units.
- Warnings: `ambiguous_quantity`, `allergy_conflict`, `nutrition_unverified`,
  `destructive_action`.
- Confidence: high when title, ingredients, and steps are present.
- Unsupported: copyrighted full-recipe extraction beyond user-provided text; medical diet
  treatment claims.
- Golden fixture: `fixtures/golden/recipe-save-structured.json`.

### 4. Meals

- Skill id: `meals`
- Skill version: `1.0.0`
- Purpose: log eaten meals, connect meals to inventory use, and create leftovers.
- Input evidence: user text, meal snapshot, inventory snapshot, recipe snapshot,
  nutrition label, preference snapshot.
- Commands: `meal.log`, `meal.record_inventory_use`, `meal.record_leftovers`.
- Confirmation: required for inventory deductions unless exact item and quantity are
  explicit; required for nutrition replacement.
- Warnings: `nutrition_unverified`, `stock_decrease_uncertain`, `ambiguous_target`,
  `allergy_conflict`.
- Confidence: high for meal title/slot; lower for nutrition and stock usage unless sourced.
- Unsupported: silently decrementing pantry from "I ate dinner"; diagnosing symptoms.
- Golden fixture: `fixtures/golden/meal-log-uncertain-use.json`.

### 5. Planning

- Skill id: `planning`
- Skill version: `1.0.0`
- Purpose: propose meal plans using inventory, recipes, goals, preferences, and shopping
  needs.
- Input evidence: user text, plan snapshot, meal snapshot, recipe snapshot, inventory
  snapshot, preference snapshot, app context.
- Commands: `planning.create_meal_plan`, `planning.update_meal_plan_entry`,
  `planning.mark_entry_status`.
- Confirmation: required when replacing accepted entries or marking planned meals eaten.
- Warnings: `allergy_conflict`, `nutrition_unverified`, `possible_duplicate`.
- Confidence: high when date range and meal count are clear; lower when schedule or goals
  conflict.
- Payload expectations: preserve entry date, slot, title, calorie target, status, notes,
  source, recipe relation, and display metadata (`emoji`, `image_url`) when available.
- Calendar/day context: update only the targeted entry unless the user clearly asks to
  regenerate the full plan.
- Unsupported: exact medical nutrition plans or calorie guarantees.
- Golden fixture: `fixtures/golden/planning-create-plan.json`.

### 6. Preferences

- Skill id: `preferences`
- Skill version: `1.0.0`
- Purpose: update food preferences, allergies, goals, stores, cuisine preferences, and
  user-authored AI instructions.
- Input evidence: user text, preference snapshot, app context.
- Commands: `preferences.update_food_preferences`, `preferences.clear_field`.
- Confirmation: required for allergies, health notes, custom AI instructions, and field
  clearing.
- Warnings: `allergy_conflict`, `destructive_action`, `unsupported_medical_advice`.
- Confidence: high only when field and value are explicit.
- Unsupported: making unsafe health promises, treating preferences as medical orders.
- Golden fixture: `fixtures/golden/preferences-update.json`.

### 7. Receipt Parsing

- Skill id: `receipt_parsing`
- Skill version: `1.0.0`
- Purpose: extract receipt text and line items, then propose food items for review.
- Input evidence: receipt image, receipt text, user text, inventory snapshot, shopping
  snapshot, preference snapshot.
- Commands: `receipt.attach_parse`, `receipt.propose_items`.
- Confirmation: required before any parsed item becomes inventory or shopping data.
- Warnings: `receipt_line_uncertain`, `ambiguous_quantity`, `possible_duplicate`.
- Confidence: line-level confidence required; low-confidence lines stay reviewable.
- Unsupported: discarding failed parses, guessing unreadable prices, storing card numbers.
- Golden fixture: `fixtures/golden/receipt-parse.json`.

### 8. Nutrition Correction

- Skill id: `nutrition_correction`
- Skill version: `1.0.0`
- Purpose: correct or mark unknown nutrition on inventory items and meal logs.
- Input evidence: user text, nutrition label, provider result, inventory snapshot,
  meal snapshot, recipe snapshot.
- Commands: `nutrition.correct_inventory_item`, `nutrition.correct_meal_log`,
  `nutrition.mark_unknown`.
- Confirmation: required when replacing user-entered or provider-sourced values.
- Warnings: `nutrition_unverified`, `ambiguous_target`, `unsupported_medical_advice`.
- Confidence: high with label/provider evidence; AI estimates must be flagged.
- Unsupported: inventing nutrition without source unless the user explicitly asks for an
  estimate and the payload source is `ai_estimate`.
- Golden fixture: `fixtures/golden/nutrition-correction.json`.

### 9. Navigation

- Skill id: `navigation`
- Skill version: `1.0.0`
- Purpose: convert user navigation language into non-mutating app route commands.
- Input evidence: user text, app context, inventory/recipe/meal/plan/shopping snapshots
  only as needed for target matching.
- Commands: `navigation.open_destination`, `navigation.open_detail`, `navigation.search`.
- Confirmation: not required because commands do not mutate product data.
- Warnings: `ambiguous_target` when multiple details match.
- Confidence: high when destination or detail target is exact.
- Unsupported: navigation to screens outside app scope or hidden developer settings.
- Golden fixture: `fixtures/golden/navigation-open-detail.json`.

## Unsupported Fixture

`fixtures/golden/unsupported-medical-advice.json` demonstrates a safe no-command response
for a medical request that mentions food and nutrition but is outside the catalog.
