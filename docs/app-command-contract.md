# WonderFood app command contract

WonderFood exposes one public intake model for other apps and automations: external callers propose food changes; WonderFood stages a review card; the user saves or rejects inside the app.

## Best entry points

### 1. Clickable link, best for ChatGPT and simple automations

Use verified app links when possible:

```text
https://wonderfood.app/action?type=inventory.add&name=Eggs&quantity=12&zone=fridge&category=dairy&requestId=abc123
```

Custom scheme also works:

```text
wonderfood://action?type=grocery.add&name=Bananas&quantity=6&requestId=abc124
```

### 2. Android share, best for WhatsApp/messages/notifications copied by the user

Share plain text to WonderFood. The app routes it to AI/local parsing and stages a proposal.

```bash
adb shell am start \
  -a android.intent.action.SEND \
  -t text/plain \
  --es android.intent.extra.TEXT "WhatsApp: bought rice, dal, yogurt" \
  com.wonderfood.app
```

### 3. Explicit command intent, best for Tasker, MacroDroid, Samsung-style routines, and launchers

Apps that can send an Android intent should use:

```text
action = com.wonderfood.app.action.COMMAND
```

Structured extras:

```bash
adb shell am start \
  -a com.wonderfood.app.action.COMMAND \
  --es requestId routine-001 \
  --es type inventory.add \
  --es name Milk \
  --es quantity "1 gal" \
  --es zone fridge \
  --es category dairy \
  com.wonderfood.app
```

Raw text fallback:

```bash
adb shell am start \
  -a com.wonderfood.app.action.COMMAND \
  --es requestId whatsapp-001 \
  --es android.intent.extra.TEXT "Need oats, bananas, and chicken thighs" \
  com.wonderfood.app
```

## Bulk actions

Bulk proposals are supported, capped at 12 external actions per review:

```text
https://wonderfood.app/action?actions=[{"type":"inventory.add","name":"Eggs","quantity":"12","zone":"fridge"},{"type":"grocery.add","name":"Bananas","quantity":"6"}]&requestId=bulk001
```

For real links, URL-encode the JSON `actions` value.

## Android AppFunctions (workflow actions, Android 16+)

WonderFood exposes the AppFunctions service at package service
`com.wonderfood.app.FoodWorkspaceAppFunctionService` for AI assistants and automation
integrations that already use Android AppFunctions.

Current AppFunctions shape:

- `proposeFoodWorkspaceActions`: proposal-only path. Returns review metadata and does
  not apply writes.
- `executeFoodWorkspaceActions`: applies non-sensitive and non-destructive actions through
  normal write execution and returns per-action status.

Top-level request schema:

- `requestId`: stable id for idempotency and replay control.
- `action`: single `FoodWorkspaceAction` for convenience.
- `actions`: array of up to 12 `FoodWorkspaceAction` items (preferred path).

Action schema:

- `type`: verb such as `inventory.add`, `grocery.mark_bought`, `recipe.add`, `preferences.update`.
- `targetKind`: one of `inventory`, `grocery`, `recipe`, `meal_log`, `meal_plan`, `plan_entry`, `preferences`, `event`.
- `targetRef`: required when mutating existing objects.
- `displayName`: user-facing label for create-like operations.
- `fields`: key/value list such as `quantity`, `zone`, `category`, `status`, `servings`, `slot`.
- `idempotencyKey`: optional dedupe token; if omitted, `requestId + index` is used when possible.

Example request payload (shared for both methods):

```json
{
  "requestId": "wf-af-001",
  "actions": [
    {
      "type": "inventory.add",
      "targetKind": "inventory",
      "targetRef": "",
      "displayName": "Greek Yogurt",
      "fields": [
        { "key": "quantity", "value": "6" },
        { "key": "zone", "value": "fridge" }
      ],
      "idempotencyKey": "wf-af-001-0"
    }
  ]
}
```

Validation expectations:

- Unsupported actions are rejected without writes.
- Destructive and preference mutations always return review-only.
- Missing `requestId` and duplicate idempotency keys can increase repeat exposure.
- This feature is feature-gated by Android API level (works on Android 16+ build targets with
  AppFunctions runtime support). On older devices the existing intent/link routes remain the
  primary path.

Canonical usage pattern:

```text
1) Call proposeFoodWorkspaceActions first for review preview.
2) Optionally call executeFoodWorkspaceActions for approved non-sensitive actions.
```

## Supported action shapes

Canonical examples:

```text
inventory.add
inventory.edit
inventory.delete
grocery.add
grocery.edit
grocery.delete
grocery.mark_bought
recipe.add
recipe.edit
recipe.delete
meal_log.log
meal_log.edit
meal_log.delete
meal_plan.add
meal_plan.edit
meal_plan.delete
plan_entry.add
plan_entry.edit
plan_entry.delete
preferences.edit
event.log
```

Useful fields include `name`, `title`, `quantity`, `zone`, `category`, `notes`, `calories`, `protein_g`, `carbs_g`, `fat_g`, `ingredients`, `steps`, `date_epoch_day`, `slot`, `status`, and `target_id`.

## Safety principles

- External commands fill review forms; they do not silently write ordinary food data.
- Destructive actions and preference/allergy changes stay review-first.
- External bulk proposals are limited to 12 actions.
- `requestId`, `request_id`, or `idempotencyKey` prevents duplicate handling.
- Raw text from WhatsApp/notifications is treated as AI/local interpretation input, not trusted structure.
