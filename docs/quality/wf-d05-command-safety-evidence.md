# WF-D05 Command Safety Evidence

## Scope

This evidence covers the canonical `core:engine` and `core:data` FoodCommand path.
The prototype app still has `FoodChatStore` SQLite code until the planned prototype migration
ticket moves app flows fully onto the canonical repositories.

## Added Coverage

- Command confirmation policy table covers every current `FoodCommand` action type.
- Invalid commands are rejected before opening a repository transaction.
- Exact stock consumption requires confirmation before mutation.
- Uncertain stock consumption records proposed usage without changing stock.
- Unconfirmed stock-lot correction leaves the lot, events, and actions unchanged.
- Confirmed correction creates action/event history and leaves the lot visible.
- Failed merge leaves existing lots, events, and actions unchanged.
- Duplicate idempotency keys remain no-ops.
- Undo creates new history instead of erasing prior events.

## Direct DAO Scan

Core DAO symbols were scanned outside `core:data`:

```sh
rg -n "wonderFoodDao\\(|WonderFoodDao|upsertFood\\(|insertFoodAction\\(" --glob '!core/data/**' --glob '!docs/**' .
```

No canonical Room DAO access was found outside `core:data`. Repository interface calls remain in
`core:engine`, as intended.

## Verification

```sh
./gradlew :core:engine:test :core:data:testDebugUnitTest
```
