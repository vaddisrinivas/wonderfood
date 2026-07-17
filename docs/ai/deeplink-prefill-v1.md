# WonderFood Direct Link v1

This is the no-backend handoff for ChatGPT, custom GPTs, Google, notes apps,
or any outside assistant that can create a normal clickable URL.

The link opens WonderFood and pre-fills a reviewable draft. It never auto-saves,
edits, archives, or deletes. The user can review, correct inside the app flow,
then tap Add/Accept.

## Add link shape

```text
https://wonderfood.app/add?kind=<kind>&name=<name>&quantity=<quantity>&zone=<zone>&category=<category>&requestId=<id>
```

Allowed `kind` values:

- `pantry`, `inventory`, `kitchen`, `fridge`, `freezer`
- `shopping`, `grocery`, `groceries`, `shop`
- `meal`, `meal-log`
- `recipe`
- `plan`, `meal-plan`

For short lists, use `text` instead of `name`:

```text
https://wonderfood.app/add/shopping?text=milk%202%20gallons%2C%20rice%2C%20lentils
```

## Examples

Add one pantry item:

```text
https://wonderfood.app/add?kind=pantry&name=Eggs&quantity=12&zone=fridge
```

Add a shopping list:

```text
https://wonderfood.app/add/shopping?text=oats%2C%20bananas%2C%20chicken%20thighs
```

Log a meal:

```text
https://wonderfood.app/add?kind=meal&name=Chicken%20rice%20bowl&calories=650
```

Draft a recipe from pasted text:

```text
https://wonderfood.app/add?kind=recipe&text=Save%20recipe%20for%20rajma%20with%20kidney%20beans%2C%20onion%2C%20tomato
```

## Privacy and size rules

- Use this only for small, user-visible prefill data.
- Do not put secrets, OAuth tokens, medical history, or large pantry snapshots in
  the URL.
- URLs are intentionally bounded by the Android parser. Long or complex proposals
  should use Android share/file import with `wf.proposal-package.v1`.
- The app accepts only trusted hosts: `wonderfood.app` and `www.wonderfood.app`.
- The Android app treats every direct add link as untrusted and review-only.

## Custom GPT instruction

When the user asks to add simple food, return a normal Markdown link:

```markdown
[Open in WonderFood](https://wonderfood.app/add?kind=pantry&name=Eggs&quantity=12&zone=fridge)
```

For multiple items, prefer `text`:

```markdown
[Open shopping list in WonderFood](https://wonderfood.app/add/shopping?text=oats%2C%20bananas%2C%20milk)
```

If the request needs a large structured plan, recipe, or private pantry snapshot,
use the proposal-package/share-file path instead of placing the data in the URL.

## Universal action link shape

Use `/action` when ChatGPT/custom GPT needs to propose editing, updating,
archiving, deleting, or batching existing WonderFood data.

```text
https://wonderfood.app/action?type=<target>.<verb>&id=<local-id>&field=value&requestId=<id>
```

Supported targets:

- `inventory` / `pantry`
- `grocery` / `shopping`
- `recipe`
- `meal_log` / `meal`
- `meal_plan` / `plan`
- `plan_entry`
- `event`
- `preferences`

Common verbs:

- `add`, `create`, `save`, `log`
- `edit`, `update`
- `delete`, `archive`, `remove`
- `mark_bought`, `eaten`, `skipped`

Examples:

```markdown
[Review pantry edit](https://wonderfood.app/action?type=inventory.edit&id=42&quantity=6&zone=fridge&requestId=gpt-edit-eggs-1)
```

```markdown
[Review grocery delete](https://wonderfood.app/action?type=grocery.delete&id=99&requestId=gpt-delete-old-milk-1)
```

If the GPT does not know a local id, it may use an exact visible name/title:

```markdown
[Review recipe edit](https://wonderfood.app/action?type=recipe.edit&target=Rajma&steps=Soak%2C%20pressure%20cook%2C%20simmer&requestId=gpt-rajma-steps-1)
```

Name/title resolution is intentionally conservative. If multiple local records
match, WonderFood shows a safe “could not resolve” result instead of guessing.

## Bulk action links

For multiple actions in one click, URL-encode a JSON array in `actions`.
WonderFood opens one batch review card. Accept executes each child action through
the command executor/audit pipeline; reject discards the whole batch.

```text
https://wonderfood.app/action?actions=<url-encoded-json-array>&requestId=<id>
```

Decoded `actions` example:

```json
[
  {
    "type": "inventory.edit",
    "id": "42",
    "fields": {
      "quantity": "6",
      "zone": "fridge"
    }
  },
  {
    "type": "grocery.delete",
    "id": "99",
    "name": "old milk"
  }
]
```

GPT-facing Markdown:

```markdown
[Review WonderFood batch](https://wonderfood.app/action?actions=%5B%7B%22type%22%3A%22inventory.edit%22%2C%22id%22%3A%2242%22%2C%22fields%22%3A%7B%22quantity%22%3A%226%22%7D%7D%2C%7B%22type%22%3A%22grocery.delete%22%2C%22id%22%3A%2299%22%7D%5D&requestId=gpt-batch-1)
```

Bulk links are capped and field-allowlisted by the app. Use proposal packages or
share/file import for very large meal plans, pantry snapshots, or private context.

## Action safety rules

- A link can open a review/diff only; it cannot directly mutate data.
- Destructive actions (`delete`, `archive`, `remove`, `clear`) are accepted only
  after the user taps Accept in WonderFood.
- Preferences/allergies are labeled as sensitive review actions.
- Unknown hosts, unknown action targets, unknown fields, oversized links, and
  malformed JSON are rejected/ignored locally.
- Include `requestId` when possible so repeated assistant taps do not re-stage the
  same action repeatedly.
