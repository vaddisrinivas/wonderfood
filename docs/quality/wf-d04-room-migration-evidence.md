# WF-D04 Room Migration Evidence

Ticket: `WF-D04 - Test Room Schema And Every Migration`

## Current Schema

- Room database: `com.wonderfood.core.data.room.WonderFoodDatabase`
- Current version: `2`
- Exported schema: `core/data/schemas/com.wonderfood.core.data.room.WonderFoodDatabase/2.json`
- Registered migrations: `WonderFoodMigrations.ALL`

## Coverage

- DAO, constraint, index, append-only history, and transaction rollback checks:
  `core/data/src/test/kotlin/com/wonderfood/core/data/room/WonderFoodDatabaseTest.kt`
- Real Android SQLite migration, V1 fixture, unsupported future version, corruption, row-count,
  foreign-key, and index checks:
  `core/data/src/androidTest/kotlin/com/wonderfood/core/data/room/WonderFoodMigrationInstrumentedTest.kt`

## Commands

```bash
./gradlew :core:data:testDebugUnitTest
ANDROID_SERIAL=emulator-5554 ./gradlew :core:data:connectedDebugAndroidTest
```

## CI Artifact

`.github/workflows/android-quality.yml` uploads `core/data/schemas/**` as `room-schemas`
so schema changes are retained with quality runs.
