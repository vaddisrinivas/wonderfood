# API 34 AVD Startup + Focused MainScreenTest Proof

Date: 2026-07-20
Scope: local API 34 AVD startup repair only. No repo source files edited. No AVD wipe/recreate. Physical S23 was visible to adb but not targeted.

## AVD / SDK Inspection

- SDK root: `/Users/srinivasvaddi/Library/Android/sdk`
- Emulator binary: `/Users/srinivasvaddi/Library/Android/sdk/emulator/emulator`
- ADB binary used: `/Users/srinivasvaddi/Library/Android/sdk/platform-tools/adb`
- Emulator version: `Android emulator version 36.6.11.0 (build_id 15507667)`
- Target AVD: `Pixel_3a_API_34_extension_level_7_arm64-v8a`
- AVD config: `/Users/srinivasvaddi/.android/avd/Pixel_3a_API_34_extension_level_7_arm64-v8a.avd/config.ini`
- AVD target: `android-34`
- System image: `system-images/android-34/google_apis/arm64-v8a/`

## Failure Reproduced

Command:

```sh
/Users/srinivasvaddi/Library/Android/sdk/emulator/emulator \
  -avd Pixel_3a_API_34_extension_level_7_arm64-v8a \
  -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect \
  -no-snapshot -read-only
```

Observed failure before config repair:

```text
ERROR | unknown skin name 'pixel_3a'
```

The stale config values were:

```ini
skin.name=pixel_3a
skin.path=skins/pixel_3a
```

Available SDK skins did not include `pixel_3a`; installed platform skin directories were only under newer generic platform folders and emulator resources.

## Non-Destructive Local Fix

Backed up the AVD config before editing:

```text
/Users/srinivasvaddi/.android/avd/Pixel_3a_API_34_extension_level_7_arm64-v8a.avd/config.ini.before-stale-pixel3a-skin-fix-20260720
```

Smallest local config change:

```diff
-skin.name=pixel_3a
-skin.path=skins/pixel_3a
+skin.dynamic=yes
```

No userdata, snapshots, disk images, or AVD data directories were deleted or wiped.

## Boot Proof

Launch command:

```sh
/Users/srinivasvaddi/Library/Android/sdk/emulator/emulator \
  -avd Pixel_3a_API_34_extension_level_7_arm64-v8a \
  -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect \
  -no-snapshot
```

Startup passed the old skin failure and emitted:

```text
androidboot.qemu.avd_name=Pixel_3a_API_34_extension_level_7_arm64-v8a
androidboot.qemu.skin=1080x2220
```

Boot wait result:

```text
poll=5 serial=emulator-5554 sys.boot_completed=1 dev.bootcomplete=1
```

ADB devices showed the physical S23 and the emulator; all install/test commands below used `-s emulator-5554` only.

## Build / Install

Build command:

```sh
./gradlew :app:assembleFossDebug :app:assembleFossDebugAndroidTest
```

Result:

```text
BUILD SUCCESSFUL in 1s
```

Installed only to emulator serial `emulator-5554`:

```sh
adb -s emulator-5554 install -r -t app/build/outputs/apk/foss/debug/app-foss-debug.apk
adb -s emulator-5554 install -r -t app/build/outputs/apk/androidTest/foss/debug/app-foss-debug-androidTest.apk
```

Result:

```text
Success
Success
instrumentation:com.wonderfood.app.foss.test/androidx.test.runner.AndroidJUnitRunner (target=com.wonderfood.app.foss)
```

## Focused FOSS MainScreenTest Results

Runner:

```text
com.wonderfood.app.foss.test/androidx.test.runner.AndroidJUnitRunner
```

Method: `com.wonderfood.app.ui.main.MainScreenTest#abConflictInboxRendersWithDismissSemantics`

```text
Time: 7.106
OK (1 test)
```

Method: `com.wonderfood.app.ui.main.MainScreenTest#acPostgresRejectsRawEndpointBeforeSavingChoice`

```text
Time: 7.288
OK (1 test)
```

## Outcome

PASS. The stale `pixel_3a` skin startup failure is repaired by a config-only AVD change. The API 34 AVD boots headless without wiping data, and both requested focused FOSS `MainScreenTest` methods pass on `emulator-5554`.
