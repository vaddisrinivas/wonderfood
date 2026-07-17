# WonderFood testing

WonderFood uses JUnit4, Robolectric, coroutine-test, hand-written fakes, Compose UI
tests, AndroidX Test/Espresso, Room migration tests, and isolated SQLite store tests.
Tests are offline by default and fixtures must remain deterministic and generic.

## Commands

Run the local quality harness:

```bash
./scripts/quality/android-harness.sh local
```

Run app local unit tests only:

```bash
./gradlew :app:testDebugUnitTest
```

Run instrumented tests when an emulator/device is available:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Run the full app plus Room migration instrumented suite:

```bash
./scripts/quality/android-harness.sh connected
```

Collect phone/emulator evidence after a failure:

```bash
ANDROID_SERIAL=R3CW10MSVRT ./scripts/quality/collect-device-evidence.sh
```

## Harness files

- `app/src/test/java/com/wonderfood/app/testing/DeterministicTestClock.kt`: fixed test clock with explicit advancement.
- `app/src/test/java/com/wonderfood/app/testing/DeterministicTestIds.kt`: deterministic long IDs and UUIDs.
- `app/src/test/java/com/wonderfood/app/testing/MainDispatcherRule.kt`: JUnit4 rule for `Dispatchers.Main`.
- `app/src/test/java/com/wonderfood/app/testing/TestFoodSeeds.kt`: generic foods, recipes, meals, plans, receipts, events, and preferences.
- `app/src/test/java/com/wonderfood/app/testing/InMemoryFoodMemoryRepository.kt`: test-only fake repository for prototype `FoodMemory` and `FoodDraft` models.
- `app/src/test/java/com/wonderfood/app/testing/FakeAiGateway.kt`: test-only fake AI gateway and command-envelope fixture catalog.
- `app/src/test/java/com/wonderfood/app/testing/TestFixtureResources.kt`: classpath fixture loader and lightweight JSON sanity probe.
- `app/src/test/resources/fixtures/**`: generic offline JSON fixtures for nutrition, receipts, and command envelopes.
- `app/src/androidTest/java/com/wonderfood/app/data/FoodChatStoreTest.kt`: isolated SQLite coverage for draft application plus editable object-page updates.

The app runtime uses `FoodChatStore` (`SQLiteOpenHelper`), while `core:data` owns
the canonical Room repository and migration foundation. Both paths have dedicated
tests; contract changes must remain synchronized until the runtime migration is done.

## Harness Rules

- Tests must run offline by default.
- `./scripts/quality/android-harness.sh local` is the pre-push gate.
- `./scripts/quality/android-harness.sh connected` is the emulator/device gate.
- Time, IDs, and UUIDs must come from deterministic test helpers when asserted.
- Fakes should fail fast when no response is queued.
- Fakes and seed builders must preserve display metadata such as `imageUri` and `imageUrl`; object pages depend on it.
- New editable fields on inventory, grocery, recipe, meal, plan, or receipt pages need a persistence test.
- Fixtures must stay generic and should not encode personal rows, locations, accounts, provider tokens, private screenshots, or real receipt images.
- AI, nutrition, receipt, and command-envelope fixtures should keep unknown values as `null` instead of inventing defaults.
