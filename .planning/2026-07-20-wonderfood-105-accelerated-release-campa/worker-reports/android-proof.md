# Android Proof Worker Report

Timestamp: 2026-07-20 18:05 ET

Scope honored:

- Wrote only `app/src/androidTest/java/com/wonderfood/app/ui/main/MainScreenTest.kt`.
- Wrote proof files only under `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/worker-android-proof/`.
- Did not edit production UI, ViewModel, sync/providers, build files, docs, or acceptance matrix.

## Device State

- Connected device: `adb-R3CW10MSVRT-7R69a1._adb-tls-connect._tcp`
- Model/API: `SM-S918U1`, API `36`
- Installed WonderFood packages visible to shell:
  - `com.wonderfood.app.test`
  - `com.wonderfood.core.data.test`
  - `com.example.wonderfood`
- `com.wonderfood.app` / `com.wonderfood.app.foss` are not installed as launchable packages on the S23.
- Actual launchable app package on S23: `com.example.wonderfood/.MainActivity`
- App metadata observed for `com.example.wonderfood`: `versionCode=1`, `versionName=1.0`, target SDK `36`, first install `2026-07-16 13:35:29`, last update `2026-07-16 13:41:05`.

## Checks Run

Passed before new local test edit:

```text
./gradlew :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wonderfood.app.ui.main.MainScreenTest#abConflictInboxRendersWithDismissSemantics
BUILD SUCCESSFUL in 1m 18s
Starting 1 tests on SM-S918U1 - 16
Finished 1 tests on SM-S918U1 - 16
```

Required compile gate after new local test edit:

```text
./gradlew :app:compileFossDebugAndroidTestKotlin
FAILED before androidTest compilation because :app:compileFossDebugKotlin fails in out-of-scope production file app/src/main/java/com/wonderfood/app/sync/GoogleSheetsGateway.kt.
```

Representative out-of-scope compile errors:

```text
Unresolved reference 'SPREADSHEET_FIELDS'
Unresolved reference 'tables'
Unresolved reference 'tablePresentationRequest'
Unresolved reference 'relationNamedRangeName'
Unresolved reference 'WORKSPACE_VALIDATION_ROW_LIMIT'
Unresolved reference 'tableRange'
Unresolved reference 'namedRanges'
Unresolved reference 'namedRangeId'
```

Owned-file whitespace check:

```text
git diff --check -- app/src/androidTest/java/com/wonderfood/app/ui/main/MainScreenTest.kt
PASS
```

## Test Harness Change

Added deterministic test:

```text
MainScreenTest#acPostgresRejectsRawEndpointBeforeSavingChoice
```

Purpose:

- Opens first-boot data-home dialog.
- Selects Postgres.
- Inputs raw/non-HTTPS endpoint `postgres.example.com:5432/wonderfood`.
- Inputs a non-secret dummy token.
- Asserts the parser-level error `Hosted Postgres backends must use HTTPS.`
- Avoids network/provider dependency.

Status:

- Source change is scoped and deterministic.
- Not compiled or run because the required compile gate is currently blocked by unrelated production `GoogleSheetsGateway.kt` errors outside this worker's ownership.

## API 34 / Emulator

Installed AVDs include `Pixel_3a_API_34_extension_level_7_arm64-v8a`.

Attempted:

```text
android emulator start Pixel_3a_API_34_extension_level_7_arm64-v8a
```

Result:

```text
Emulator process has exited early
ERROR | unknown skin name 'pixel_3a'
```

API 34 connected proof is blocked by local AVD configuration, not by a test failure.

## S23 Visual / Relaunch Assessment

Instrumentation proof:

- Conflict inbox semantics test passed on S23/API 36.

Screenshot files:

- `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/worker-android-proof/s23-api36-conflict-inbox.png`
- `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/worker-android-proof/s23-api36-relaunch.png`
- `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/worker-android-proof/s23-api36-relaunch-com-example.png`

Visual caveat:

- Captured S23 screenshots show Samsung/DeX trackpad overlay, not clean app UI.
- Relaunching `com.example.wonderfood` shows Android system dialog: `App paused by Maximum power saving`.
- I did not tap `Unpause temporarily` or change power settings because that is OS-level user/device state.

S23 launch/restart/persistence status:

- Device is connected and reachable over ADB.
- App launch target exists as `com.example.wonderfood/.MainActivity`.
- Clean relaunch/persistence proof is blocked until the user unpauses the app from Maximum power saving and exits the trackpad overlay.
- No destructive uninstall or data clear was performed.

## Bottom Line

- Conflict inbox instrumented proof on S23/API 36: PASS.
- Clean visual conflict proof: BLOCKED by Samsung trackpad overlay.
- Clean error visual proof: BLOCKED by production compile failure and S23 power-saving pause.
- API 34 connected proof: BLOCKED by AVD skin configuration `unknown skin name 'pixel_3a'`.
- S23 install/restart/persistence proof: PARTIAL only; package exists and launches into a system pause dialog, but app-level restart/persistence cannot be claimed.
