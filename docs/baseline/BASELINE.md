# WonderFood Prototype Baseline

Recorded: 2026-07-16

This folder preserves the prototype before the 1.0 ticket implementation begins.

## Build Evidence

- Debug APK: `apk/wonderfood-prototype-debug.apk`
- Gradle log: `build/gradle-debug-unit-test.txt`
- Device list: `build/adb-devices.txt`
- Verification command: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
- Result: passed

## Data Evidence

- Package at capture time: `com.example.wonderfood`
- Database name: `wonderfood.db`
- Prototype schema version: `6`
- Pulled database: `db/wonderfood-prototype-v6.db`
- Round-trip restore smoke copy: `db/wonderfood-restore-test-roundtrip.db`
- Schema dump: `db/schema-v6.sql`
- Integrity check: `ok`

Captured row counts:

| Table | Count |
|---|---:|
| `chat_messages` | 1 |
| `inventory_items` | 42 |
| `recipes` | 11 |
| `meal_plans` | 1 |
| `grocery_items` | 0 |
| `food_events` | 0 |

The emulator restore smoke test streamed `db/wonderfood-prototype-v6.db` back into the
app sandbox as `wonderfood-restore-test.db`, pulled it back out, and verified the SHA-256
hash matched the original baseline database.

## Visual Evidence

Screenshots from `outputs/screenshots/` and the final polish captures from `outputs/`
were copied into `screenshots/`.

## Hashes

`baseline-sha256.txt` contains SHA-256 hashes for all preserved APK, database, and
screenshot artifacts.
