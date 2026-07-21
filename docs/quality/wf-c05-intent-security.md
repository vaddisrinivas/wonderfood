# Intent security and external command intake

## Security Alignment Area

Safe exported Activity deep-link handling for assistant and shortcut entry points.

## Impact And Priority

Priority: high. `MainActivity` is exported for launcher, shares, shortcuts, verified
HTTPS links, custom-scheme links, and explicit command intents. All input is untrusted.

## Scope Of Changes

- `app/src/main/java/com/wonderfood/app/WonderFoodDeepLink.kt`
- `app/src/main/java/com/wonderfood/app/ui/main/MainScreenViewModel.kt`
- `app/src/androidTest/java/com/wonderfood/app/WonderFoodDeepLinkTest.kt`
- `scripts/adb-direct-actions.sh`

## Implementation Summary

- Parsing accepts only the documented VIEW, SEND, and explicit command actions.
- HTTPS hosts are restricted to `wonderfood.app` and `www.wonderfood.app`; supported
  paths are `/add` and `/action`. Custom-scheme hosts are allowlisted.
- Text, field sizes, bulk count, action types, target types, fields, enums, and numeric
  values are bounded and allowlisted before a proposal can reach persistence.
- Malformed explicit extras, URLs, and bulk JSON fail closed without crashing.
- Every external mutation opens a review form. Users can edit, accept, or reject it.
- Bulk acceptance uses one SQLite transaction; any failed child rolls back all children.
- Destructive and preference actions remain explicitly marked for confirmation.
- Explicit `requestId`, `request_id`, or `idempotencyKey` query parameters are preserved so
  assistant retries can be deduped.
- Unsupported `voice` or `quick` utterances become AI-review commands instead of disappearing.
- Direct-action idempotency is persisted in app preferences only when an explicit idempotency key
  is present, so intentional repeated commands without a retry key can still run.

## Verification

- Local and instrumented parser tests: `WonderFoodDeepLinkTest`
- Executor policy tests: `FoodDraftCommandExecutorTest`
- Canonical transaction and rollback tests: `HouseholdCommandExecutorTest`,
  `RoomHouseholdRepositoryTest`, and `WonderFoodMigrationsTest`
- Manual/CI ADB suite: `scripts/adb-direct-actions.sh`
