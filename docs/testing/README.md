# WonderFood Test Harness

Updated: 2026-07-16
Ticket: WF-D02

## Current Stack

- Dependency injection: none in the prototype. Production code constructs its own collaborators.
- Local unit tests: JUnit4 through `testImplementation(libs.junit)`.
- Coroutine tests: `kotlinx-coroutines-test` is available for local tests.
- Mocking: no Mockito or MockK dependency. Use hand-written fakes.
- Robolectric: not installed.
- UI behavior tests: Compose UI test APIs plus AndroidX Test/Espresso in `app/src/androidTest`.
- Screenshot tests: not installed.
- End-to-end tests: not installed.
- App UI: Compose.
- Persistence tests: current `FoodChatStoreTest` runs as an instrumented test with an isolated SQLite database name.

No new frameworks were added for WF-D02. Agent D did not edit Gradle, production code, manifests, UI, or core production sources.

## Commands

Run local tests:

```bash
./gradlew test
```

Run app local unit tests only:

```bash
./gradlew :app:testDebugUnitTest
```

Run instrumented tests when an emulator/device is available:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Known current instrumented limitation: before Wave 1 UI changes are merged, `MainScreenTest.mainScreenShowsFoodChatShell` can fail because it still expects the old placeholder text `Tell AI what changed...`. That is a stale UI assertion, not a WF-D02 harness regression. Fix it in a UI/test ticket that owns the current screen contract.

## WF-D02 Harness Files

- `app/src/test/java/com/wonderfood/app/testing/DeterministicTestClock.kt`: fixed test clock with explicit advancement.
- `app/src/test/java/com/wonderfood/app/testing/DeterministicTestIds.kt`: deterministic long IDs and UUIDs.
- `app/src/test/java/com/wonderfood/app/testing/MainDispatcherRule.kt`: JUnit4 rule for `Dispatchers.Main`.
- `app/src/test/java/com/wonderfood/app/testing/TestFoodSeeds.kt`: generic foods, recipes, meals, plans, receipts, events, and preferences.
- `app/src/test/java/com/wonderfood/app/testing/InMemoryFoodMemoryRepository.kt`: test-only fake repository for prototype `FoodMemory` and `FoodDraft` models.
- `app/src/test/java/com/wonderfood/app/testing/FakeAiGateway.kt`: test-only fake AI gateway and command-envelope fixture catalog.
- `app/src/test/java/com/wonderfood/app/testing/TestFixtureResources.kt`: classpath fixture loader and lightweight JSON sanity probe.
- `app/src/test/resources/fixtures/**`: generic offline JSON fixtures for nutrition, receipts, and command envelopes.

## Compatibility Notes

WF-C02 has not yet introduced a production AI gateway interface. `FakeAiGateway` is intentionally test-only and does not guess that future interface. When WF-C02 lands, adapt the fake by implementing the production gateway contract and keep the queued deterministic responses.

WF-A03 has not yet introduced Room or a production database factory. The current prototype uses `FoodChatStore`, an Android `SQLiteOpenHelper`. Until a Room/in-memory factory exists, use `InMemoryFoodMemoryRepository` for local unit tests and isolated test database names for instrumented persistence tests.

WF-A02 canonical domain models are present in `core:model`, but the repo does not enable Gradle test fixtures. `TestFoodSeeds` stays in `app/src/test` and matches current prototype app models; future core tests can add model-specific builders inside their own test source sets without adding a production dependency.

## Harness Rules

- Tests must run offline by default.
- Time, IDs, and UUIDs must come from deterministic test helpers when asserted.
- Fakes should fail fast when no response is queued.
- Fixtures must stay generic and should not encode personal rows, locations, accounts, provider tokens, private screenshots, or real receipt images.
- AI, nutrition, receipt, and command-envelope fixtures should keep unknown values as `null` instead of inventing defaults.
