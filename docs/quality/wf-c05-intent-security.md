# WF-C05 Intent Security And Direct Actions

## Security Alignment Area

Safe exported Activity deep-link handling for assistant and shortcut entry points.

## Impact And Priority

Priority: medium. `MainActivity` is exported for launcher, shortcuts, and `wonderfood://`
assistant links, so incoming intents must be allowlisted and parsed without forwarding,
granting URI permissions, or accepting arbitrary actions.

## Scope Of Changes

- `app/src/main/java/com/wonderfood/app/WonderFoodDeepLink.kt`
- `app/src/main/java/com/wonderfood/app/ui/main/MainScreenViewModel.kt`
- `app/src/androidTest/java/com/wonderfood/app/WonderFoodDeepLinkTest.kt`
- `scripts/adb-direct-actions.sh`

## Implementation Summary

- Deep-link parsing now accepts only `Intent.ACTION_VIEW` and the `wonderfood` scheme.
- Hosts are allowlisted to `open`, `voice`, and `quick`.
- Assistant text fields are trimmed, control characters are removed, and oversized text is capped.
- Explicit `requestId`, `request_id`, or `idempotencyKey` query parameters are preserved so
  assistant retries can be deduped.
- Unsupported `voice` or `quick` utterances become AI-review commands instead of disappearing.
- Direct-action idempotency is persisted in app preferences only when an explicit idempotency key
  is present, so intentional repeated commands without a retry key can still run.

## Verification

- Instrumented parser tests: `WonderFoodDeepLinkTest`
- Manual/CI ADB suite: `scripts/adb-direct-actions.sh`
- Replay evidence from emulator on 2026-07-16:
  - `WATER` event for `requestId=adb-water-250`: 1 row after two launches.
  - `SHOP` start event for `requestId=adb-shopping-start`: 1 row after two launches.
  - Grocery item `oats` for `requestId=adb-grocery-oats`: 1 row after two launches.
  - Meal log `chicken rice` for `requestId=adb-meal-chicken-rice`: 1 row after two launches.
  - AI-review voice note `need Greek yogurt this week` for `requestId=adb-ai-note-yogurt`: 1 user message after two launches.
