# AI Command Envelope v1

`wf.ai.command-envelope.v1` is the only AI output shape accepted by the future
structured gateway. It carries evidence, confidence, warnings, confirmation policy,
and typed commands. It does not carry product persistence instructions.

## Envelope Shape

Required top-level fields:

| Field | Contract |
| --- | --- |
| `schema_version` | Literal `wf.ai.command-envelope.v1`. |
| `catalog_version` | Literal `wf.ai.skill-catalog.v1`. |
| `skill_id` | One of the versioned skills in `skill-catalog-v1.md`. |
| `skill_version` | Semantic version of the selected skill, starting at `1.0.0`. |
| `envelope_id` | Provider-local id for traceability. Not a database id. |
| `idempotency_key` | Stable hash/source key for retry-safe proposal handling. |
| `status` | `commands`, `needs_confirmation`, `needs_clarification`, or `unsupported`. |
| `evidence` | Array of input evidence objects used by commands. |
| `commands` | Array of typed commands. Empty when unsupported or clarification needed. |
| `confidence` | Overall confidence object with score and rationale. |
| `confirmation` | Envelope-level confirmation object. |
| `warnings` | Stable warning objects. Empty when none. |
| `unsupported` | Null or object explaining why no command can be emitted. |

## Evidence

Evidence objects describe what the AI saw, not what the app should do.

Supported evidence types:

- `user_text`
- `receipt_text`
- `receipt_image`
- `barcode`
- `inventory_snapshot`
- `shopping_snapshot`
- `recipe_snapshot`
- `meal_snapshot`
- `plan_snapshot`
- `preference_snapshot`
- `nutrition_label`
- `provider_result`
- `app_context`

Evidence fields:

- `evidence_id`: stable id inside the envelope, such as `ev_user_1`.
- `type`: one of the supported evidence types.
- `source_ref`: source pointer, such as `turn:user:local` or `receipt:pending`.
- `quote`: short excerpt when available.
- `observed_at`: ISO-8601 timestamp or null.
- `confidence`: score from `0.0` to `1.0`.

## Confidence

Confidence is explicit and never substitutes for validation.

- `score >= 0.90`: high confidence; normal review can apply.
- `0.70 <= score < 0.90`: usable but review important.
- `score < 0.70`: require confirmation or clarification before mutation.
- Item-level command confidence may be lower than envelope confidence.

## Confirmation

Confirmation object:

| Field | Contract |
| --- | --- |
| `required` | Boolean. True when any command is destructive, stock-decreasing, preference-clearing, health-sensitive, or uncertain. |
| `level` | `none`, `review`, `confirm`, or `confirm_destructive`. |
| `reason` | Human-readable reason. Empty only when level is `none`. |
| `prompt` | User-facing confirmation question. Empty only when level is `none`. |

Command-level confirmation mirrors or strengthens the envelope level. The engine must
reject a command if `destructive` is true and confirmation is not required.

## Warnings

Warnings are stable objects:

- `code`: machine-readable snake_case.
- `severity`: `info`, `review`, or `blocker`.
- `message`: concise human explanation.
- `evidence_refs`: evidence ids behind the warning.

Common warning codes:

- `ambiguous_quantity`
- `ambiguous_target`
- `possible_duplicate`
- `allergy_conflict`
- `nutrition_unverified`
- `receipt_line_uncertain`
- `stock_decrease_uncertain`
- `destructive_action`
- `unsupported_medical_advice`
- `outside_catalog`

## Command Object

Required command fields:

| Field | Contract |
| --- | --- |
| `command_id` | Envelope-local stable id, such as `cmd_1`. |
| `type` | Typed command name from the enum below. |
| `summary` | Human review label. |
| `payload` | Domain-shaped proposal payload. No SQL or persistence details. |
| `evidence_refs` | Evidence ids that support this command. |
| `confidence` | Command confidence object. |
| `confirmation` | Command confirmation object. |
| `destructive` | True when command can remove, archive, clear, decrease, or overwrite important data. |
| `mutation` | True for product-data changes; false for navigation-only commands. |

Disallowed command names:

- `create`
- `update`
- `delete`
- `upsert`
- `crud.*`
- `sql.*`
- `database.*`
- `room.*`
- `dao.*`

## Tool Catalog: Command Types

The AI tool surface is this enum of typed command names. Tool names are stable
product intentions, not persistence operations.

### Inventory

`inventory.add_lot`

- Payload: `name`, `quantity`, `storage_zone`, `category`, `source`, optional
  `expires_on`, `notes`, `nutrition`, `aliases`.
- Confirmation: review; confirm if quantity or item split is uncertain.
- Never silently merges lots; matching is deterministic product logic.

`inventory.adjust_quantity`

- Payload: `inventory_item_ref`, `delta_quantity`, `reason`, optional
  `related_meal_ref`, `remaining_quantity`.
- Confirmation: required for decreases and ambiguous units.

`inventory.move_lot`

- Payload: `inventory_item_ref`, `from_zone`, `to_zone`, optional `reason`.
- Confirmation: review; confirm if target item is ambiguous.

`inventory.archive_lot`

- Payload: `inventory_item_ref`, `reason`.
- Confirmation: `confirm_destructive`.
- Archive only; no hard delete.

### Shopping

`shopping.add_item`

- Payload: `name`, optional `quantity`, `category`, `preferred_store`, `needed_by`,
  `recipe_ref`, `reason`.

`shopping.mark_item_bought`

- Payload: `shopping_item_ref`, optional `purchased_quantity`, `target_zone`,
  `purchase_context`.
- Confirmation: confirm if it also proposes inventory changes in a separate command.

`shopping.remove_item`

- Payload: `shopping_item_ref`, `reason`.
- Confirmation: `confirm_destructive`.

### Recipes

`recipe.save_structured`

- Payload: `title`, `servings`, `ingredients[]`, `steps[]`, optional `tags`,
  `source_text_ref`, `nutrition`, `warnings`.
- Ingredients include `name`, optional `quantity`, `preparation`, and `matched_inventory_ref`.

`recipe.update_structured`

- Payload: `recipe_ref`, changed fields, and `reason`.
- Confirmation: confirm if overwriting user-authored text.

`recipe.archive`

- Payload: `recipe_ref`, `reason`.
- Confirmation: `confirm_destructive`.

### Meals

`meal.log`

- Payload: `title`, `meal_slot`, `logged_on`, optional `nutrition`, `source`,
  `confidence`, and `external_food`.
- Nutrition source must be `user`, `label`, `provider`, `recipe_calculation`,
  `ai_estimate`, or `unknown`.

`meal.record_inventory_use`

- Payload: `meal_ref`, `uses[]` with `inventory_item_ref`, `quantity`, and `confidence`.
- Confirmation: required unless quantities and targets are exact.

`meal.record_leftovers`

- Payload: `meal_ref`, `items[]` with name, quantity, zone, and optional expiry.

### Planning

`planning.create_meal_plan`

- Payload: `title`, `start_on`, `entries[]`, optional `shopping_suggestions[]`,
  `constraints_used[]`.

`planning.update_meal_plan_entry`

- Payload: `plan_entry_ref`, fields to change, and `reason`.
- Supported changed fields: `date`, `slot`, `title`, `calorie_target`, `status`,
  `notes`, `recipe_ref`, and display metadata (`emoji`, `image_url`).
- Confirmation: confirm when replacing an accepted plan entry.

`planning.mark_entry_status`

- Payload: `plan_entry_ref`, `status`, optional `actual_meal_ref`.
- Allowed status: `planned`, `eaten`, `skipped`.

### Preferences

`preferences.update_food_preferences`

- Payload: `changes[]` with `field`, `value`, `mode` (`set`, `append`, `remove`),
  and `reason`.
- Confirmation: required for allergies, health notes, or custom AI instructions.

`preferences.clear_field`

- Payload: `field`, `reason`.
- Confirmation: `confirm_destructive`.

### Receipt Parsing

`receipt.attach_parse`

- Payload: `receipt_ref`, `merchant`, `purchased_at`, `raw_text`, `line_items[]`,
  `subtotal`, `tax`, `total`, and parser metadata.
- Line items include `description`, optional `quantity`, `price`, `confidence`.

`receipt.propose_items`

- Payload: `receipt_ref`, `items[]`, `destination` (`inventory` or `shopping`),
  and `reason`.
- Does not add inventory by itself; follow-up inventory/shopping commands do that.

### Nutrition Correction

`nutrition.correct_inventory_item`

- Payload: `inventory_item_ref`, `serving`, nutrition fields, `source`, `confidence`,
  optional `evidence_label`.
- Confirmation: required when replacing non-unknown existing values.

`nutrition.correct_meal_log`

- Payload: `meal_ref`, nutrition fields, `source`, `confidence`.
- Confirmation: required when replacing user-entered values.

`nutrition.mark_unknown`

- Payload: `target_ref`, `fields[]`, `reason`.
- Confirmation: review; use when source is missing or unreliable.

### Navigation

`navigation.open_destination`

- Payload: `destination` (`today`, `kitchen`, `plan`, `recipes`, `shop`, `settings`),
  optional `focus`.
- Mutation: false.

`navigation.open_detail`

- Payload: `detail_type`, `target_ref`, optional `source_destination`.
- Mutation: false.

`navigation.search`

- Payload: `query`, optional `scope`.
- Mutation: false.

## Unsupported Behavior

Return no commands when the user asks for:

- Medical diagnosis, treatment, or supplement dosing.
- Silent destructive actions.
- Food safety certainty from weak evidence.
- Inventory changes without item names.
- Nutrition numbers without source or explicit estimate permission.
- SQL, database edits, or developer internals.
- Anything outside the catalog command enum.
