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
- linked records with titles and statuses
- source provenance

This is the contract Notion templates, Google Sheets workbooks, local SQLite, Chat, agents and MCP clients should share.
