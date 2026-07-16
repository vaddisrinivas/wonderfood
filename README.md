# WonderFood

WonderFood is a local-first Android food workspace. It turns voice, photos, receipts,
barcodes, and direct UI actions into reviewable food data for kitchen inventory,
recipes, meal planning, shopping, meal logs, and optional Health Connect sync.

The 1.0 implementation plan lives in
[`docs/EXECUTION_TICKETS.md`](docs/EXECUTION_TICKETS.md).

## Current State

This repository starts from the prototype baseline captured in
[`docs/baseline/BASELINE.md`](docs/baseline/BASELINE.md).

Build and test:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Install debug build:

```bash
./gradlew :app:installDebug
```

## Release Identity

- App name: WonderFood
- Android package: `com.wonderfood.app`
- Min SDK: 26
- Target SDK: 36
- License: Apache-2.0
