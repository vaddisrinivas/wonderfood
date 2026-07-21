# WonderFood v1.0.5 Release Audit

Generated: 2026-07-20
Role: acceptance/release auditor
Scope: read-only audit except this report

## Executive Result

The 65/76 matrix count is stale after the V4 Notion/Sheets rewrite. Current source also does not compile.

Proposed strict current count: **50 PASS / 0 BLOCKED / 26 TODO**.

This is not a release-ready tree. The hard blocker is `:app:compileFossDebugKotlin` failing in `GoogleSheetsGateway.kt`; after that is fixed, the downgraded rows need fresh V4 proof before the count can recover.

## Commands Run

- `git status --short --branch`
- `git worktree list`
- `git diff --check`
- `perl -ne '...' acceptance_matrix.md`
- `rg` scans for V4/provider/legacy/secret terms
- `./gradlew :app:testFossDebugUnitTest --tests 'com.wonderfood.app.sync.WorkspaceGraphProjectionTest' --tests 'com.wonderfood.app.sync.GoogleSheetsGatewayTest' --tests 'com.wonderfood.app.sync.NotionGatewayTest' --tests 'com.wonderfood.app.sync.DataHomeAdapterTest'` -> PASS
- `./scripts/quality/android-harness.sh local` -> FAIL
- `./gradlew --no-daemon :app:compileFossDebugKotlin` -> FAIL
- `gh pr list --head codex/wonderfood-105-zero-user-reset --state all --json ...` -> `[]`
- `gh run list --branch codex/wonderfood-105-zero-user-reset --limit 10 --json ...` -> `[]`
- `gh release view v1.0.5 --json ...` -> release not found
- `git ls-remote --heads origin codex/wonderfood-105-zero-user-reset` -> no remote branch
- `git ls-remote --tags origin 'v1.0.5*'` -> no tag
- `adb devices -l` -> S23 connected: `SM_S918U1`, Android device serial via adb TLS
- `agent-env` presence check only, no values printed

## Current Compile Blocker

`./gradlew --no-daemon :app:compileFossDebugKotlin` fails with unresolved references in:

- `app/src/main/java/com/wonderfood/app/sync/GoogleSheetsGateway.kt:157` `SPREADSHEET_FIELDS`
- `GoogleSheetsGateway.kt:272` `tables`
- `GoogleSheetsGateway.kt:283` `tablePresentationRequest`
- `GoogleSheetsGateway.kt:331` `relationNamedRangeName`
- `GoogleSheetsGateway.kt:425` `WORKSPACE_VALIDATION_ROW_LIMIT`
- `GoogleSheetsGateway.kt:458` `namedRanges`
- `GoogleSheetsGateway.kt:684` `protectedRanges`
- `GoogleSheetsGateway.kt:730` `developerMetadata`
- plus related `tableId`, `metadataId`, `protectedRangeId`, and `put` unresolved references.

Because app compile is red, rows depending on current app/provider test/build proof need fresh proof after this is fixed.

## Rows That Can Remain PASS

These rows are not contradicted by the V4 audit and have current or still-valid evidence:

- Canonical/core: `A01`, `A03`, `A06`
- Legacy-removal/source scan: `A04`, `A05`
- Product/source rows with prior connected/source proof but requiring post-compile smoke before release: `B01-B12`
- Core merge/safety semantics: `C02-C08`
- Sheets source-level V4 schema/builders from focused tests: `C15-C18`
- Postgres local proof/security rows: `C20-C23`
- AI/source contract rows, pending post-compile rerun: `D01-D11`
- Docs/secret hygiene: `E10`, `E11`, `E17`

Keep these PASS only as implementation/evidence rows, not as release readiness. They still need the final compile/local harness to go green before `E01`.

## Rows Requiring Fresh Proof Or Downgrade

Existing TODO rows remain TODO:

- `B13`: missing clean error-state screenshot and full visual matrix.
- `C25`: API-derived visual report is not direct Chrome provider UI proof; older Sheets/Notion visual evidence is stale.
- `E01`: cannot close while any row is TODO and compile is red.
- `E07`: no green supported low/current connected matrix.
- `E08`: visual proof incomplete.
- `E09`: physical S23 proof incomplete; S23 is connected now, but current APK cannot build.
- `E12`: branch has no CI run.
- `E13`: signing vars missing; no signed APK/checksum.
- `E14`: no PR.
- `E15`: no `v1.0.5` tag/release.
- `E16`: no verified install artifact/URL.

Additional stale V3 / broken V4 rows to downgrade until reproven:

- `C01`: lifecycle contract exists, but V4 provider adapter integration is not complete; Sheets adapter push still calls `exportSnapshotRows`, which now errors for V4.
- `C09`: V4 Notion creates support/detail sources; hiding/primary navigation behavior is not freshly proven, and support sources are still created as Notion child databases.
- `C10`: useful Notion views are contract/prose/test declarations, not current live V4 view proof.
- `C11`: Notion standalone buttons/templates/charts are not proven in current V4 implementation.
- `C12`: property/data-source ID repair and rename-safe V4 binding behavior need fresh proof; current evidence is partly old contract wording.
- `C14`: latest full Notion scenario evidence is from the old scenario script path; current V4 live proof only shows export/readback.
- `C19`: latest Sheets scenario/visual evidence is stale V3 tab/header proof (`Plans`, `Purchases`, `Goals`, `Foods`, `Recipe Ingredients` appear). Current V4 source tests pass, but no fresh live V4 scenario.
- `C24`: provider sync coordinator/adapters still use `WonderFoodSnapshot`; Sheets real push path is broken, so this cannot be called non-snapshot-dump V4 sync.
- `E04`: all-provider live round-trip is stale for V4 Sheets and incomplete for V4 scenario semantics.
- `E06`: current local harness fails at app compile.

## External Blockers

- Signing env missing: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
- Google release/OAuth env missing: `GOOGLE_WEB_CLIENT_ID`; docs still have `TODO_ADD_GOOGLE_WEB_CLIENT_ID`.
- Asset Links remain placeholder per release evidence.
- Postgres hosted live env missing: `POSTGRES_TEST_API_ROOT`, `POSTGRES_TEST_API_TOKEN`, `POSTGRES_TEST_HOUSEHOLD_ID`, and `WONDERFOOD_POSTGRES_*` aliases.
- GitHub state: local branch is not pushed, no upstream, no PR, no branch CI, no `v1.0.5` tag, no `v1.0.5` release.

Not currently blocked:

- Notion env exists: `NOTION_TOKEN` and `NOTION_API_KEY` are set.
- Physical S23 is currently visible to adb.
- `git diff --check` is clean.
- Legacy runtime production scan is clean for `FoodChatStore`, `readMemory`, legacy snapshot bridge names, and `store.` authority paths.
- Secret-shaped scan found policy/script variable references only, not committed secret values.

## Ordered Closure Checklist

1. Fix current compile break:
   `./gradlew --no-daemon :app:compileFossDebugKotlin`

2. Re-run focused V4 tests:
   `./gradlew :app:testFossDebugUnitTest --tests 'com.wonderfood.app.sync.WorkspaceGraphProjectionTest' --tests 'com.wonderfood.app.sync.GoogleSheetsGatewayTest' --tests 'com.wonderfood.app.sync.NotionGatewayTest' --tests 'com.wonderfood.app.sync.DataHomeAdapterTest'`

3. Rewire V4 provider adapters/coordinator so Notion/Sheets push/pull use `WorkspaceGraphProjection` or a V4-native contract, not `WonderFoodSnapshot` export.

4. Re-run local harness:
   `./scripts/quality/android-harness.sh local`

5. Run fresh V4 provider proofs:
   `scripts/quality/run-notion-scenario-proof.sh`
   `scripts/quality/run-google-sheets-scenario-proof.sh`
   `scripts/quality/run-local-postgres-scenario-proof.sh`

6. Direct Chrome C25 proof:
   open fresh V4 Notion and Sheets workspaces in signed-in Chrome, capture screenshots showing populated linked relations/formulas/tabs, and record app-offline state.

7. Android visual/device proof:
   `./scripts/quality/android-harness.sh connected`
   `./scripts/quality/collect-device-evidence.sh <S23-serial> <evidence-dir>`

8. Release proof after signing env is loaded:
   `/Users/srinivasvaddi/.codex/skills/agent-env/scripts/run-with-agent-env.sh ./scripts/quality/collect-release-evidence.sh <evidence-dir>`

9. Push, PR, CI, merge, tag, release:
   `git push -u origin codex/wonderfood-105-zero-user-reset`
   `gh pr create ...`
   wait for CI
   merge PR
   tag `v1.0.5`
   verify release workflow publishes APKs and `SHA256SUMS.txt`

## Final Auditor Note

The fastest honest recovery path is not another broad pass. Fix the Sheets compile break first, then make one V4-native sync contract pass through Notion and Sheets, then redo only the provider/live/device/release rows that the rewrite invalidated.
