# WonderFood v1.0.5 Final Rebaseline

Generated: 2026-07-20
Scope: read-only acceptance audit after V4 Notion/Sheets rewrite, except this report.
Device policy: no physical Android device was used or touched by this audit.

## Proposed Current Counts

Strict current status: **64 PASS / 0 BLOCKED / 12 TODO**.

Current `acceptance_matrix.md` text says **65 PASS / 0 BLOCKED / 11 TODO**. That count is stale because the current local Android quality harness fails lint, so `E06` cannot remain PASS.

## Row Changes From Current Matrix

| Row | Matrix status | Proposed status | Reason |
|---|---|---|---|
| `E06` | PASS | TODO | `./scripts/quality/android-harness.sh local` failed on current tree at `:app:lintFossDebug`; full local Android quality harness is not green. |

No rows are proposed as `BLOCKED`. The release has real blockers listed below, but they are closure dependencies for TODO rows rather than contradictions that make a row impossible.

## Rows Remaining TODO

`B13`, `C25`, `E01`, `E06`, `E07`, `E08`, `E09`, `E12`, `E13`, `E14`, `E15`, `E16`.

## Evidence Used

### Matrix And Repo State

- Matrix parsed from `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/acceptance_matrix.md`: 76 rows, currently marked `65 PASS / 0 BLOCKED / 11 TODO`.
- Git state checked with `git status --short`, `git branch --show-current`, `git remote -v`, and `git worktree list`.
- Current branch: `codex/wonderfood-105-zero-user-reset`.
- Worktree is dirty; the accelerated campaign folder is untracked.

### Current Green Evidence

- `./gradlew --no-daemon :app:compileFossDebugKotlin :app:testFossDebugUnitTest --tests 'com.wonderfood.app.sync.WorkspaceGraphProjectionTest' --tests 'com.wonderfood.app.sync.GoogleSheetsGatewayTest' --tests 'com.wonderfood.app.sync.NotionGatewayTest' --tests 'com.wonderfood.app.sync.DataHomeAdapterTest'`
  - Result: `BUILD SUCCESSFUL in 16s`.
  - Supports keeping current V4 Notion/Sheets focused source/request-builder/adapter rows PASS where the matrix already has focused or live evidence.
- `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/worker-reports/data-home-v4.md`
  - Reports `DataHomeAdapterTest` PASS and `:app:compileFossDebugKotlin` PASS for the worker scope.
  - Caveat recorded by that worker: Postgres inbound still uses `WonderFoodSnapshot?`; provider interface cleanup remains follow-up.
- `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/worker-reports/sheets-v4.md`
  - Reports `:app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.GoogleSheetsGatewayTest` PASS.
  - Contains one expected failed attempt using ambiguous `:app:testDebugUnitTest`; not a current row failure.
- `app/build/evidence/live-workspace/notion_scenarios-1784579938.json`
  - `all_scenarios_passed=true`, `no_token_or_secret_visible=true`, provision/bind, app create, Notion edit/pull, app edit, conflict input, archive, retry, and repair all true.
- `app/build/evidence/live-workspace/notion-1784585355344.json`
  - V4 Notion live export/read proof: `schema_version=4`, `upserted_rows=9`, `read_rows=7`, tables include `Kitchen`, `Recipes`, `Ingredients`, `Lists & Help`, `Stock Lots`.
- `app/build/evidence/live-workspace/google_sheets_scenarios-1784586641.json`
  - `all_scenarios_passed=true`, `no_token_or_secret_visible=true`, live create/edit/conflict/archive/repair/retry proof true, seed pull read tables `Kitchen` and `Shopping`.
- `app/build/evidence/live-workspace/google_sheets-1784586689680.json`
  - V4 Sheets live export/read proof: title `WonderFood V4 Linked Workspace Proof`, `schema_version=4`, 13 initialized tabs, `export_rows=10`, `read_rows=7`.

### Current Red Evidence

- `./scripts/quality/android-harness.sh local`
  - Result: `BUILD FAILED in 1m 7s`.
  - Failing task: `:app:lintFossDebug`.
  - Full report: `app/build/intermediates/lint_intermediate_text_report/fossDebug/lintReportFossDebug/lint-results-fossDebug.txt`.
  - Lint errors:
    - `app/src/main/java/com/wonderfood/app/data/CanonicalHouseholdUiSummary.kt:91` `LocalDate.ofInstant`
    - `app/src/main/java/com/wonderfood/app/data/CanonicalHouseholdUiSummary.kt:100` `LocalDate.ofInstant`
    - `app/src/main/java/com/wonderfood/app/data/CanonicalHouseholdUiSummary.kt:113` `LocalDate.ofInstant`
    - `app/src/main/java/com/wonderfood/app/data/CanonicalHouseholdUiSummary.kt:188` `LocalDate.ofInstant`
    - `app/src/main/java/com/wonderfood/app/data/CanonicalHouseholdUiSummary.kt:218` `LocalDate.ofInstant`
    - `app/src/main/java/com/wonderfood/app/data/CanonicalWeekPlanItem.kt:28` `LocalDate.ofInstant`
    - `app/src/main/java/com/wonderfood/app/data/CanonicalWeekPlanItem.kt:43` `LocalDate.ofInstant`
  - Common cause: `java.time.LocalDate#ofInstant` requires API 34 or core library desugaring; current min SDK is 26.

### Visual And Provider Standalone TODO Evidence

- `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/visual-proof-b13-e08/visual_matrix.md`
  - `B13` and `E08` remain TODO.
  - Missing clean error-state screenshot and full required visual matrix.
- `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/c25-visual-proof-scout.md`
  - Recommendation: keep `C25` TODO.
  - API-backed Notion/Sheets seed/export/read proof passes, but direct rendered provider UI inspection was blocked by provider sign-in pages.
- `app/build/evidence/live-workspace/provider-standalone-visual-1784573554/provider-standalone-visual-proof.json`
  - Partial historical/API-derived support only.
  - It has `all_visual_checks_passed=true`, but the scout says direct Notion/Sheets UI inspection is still missing and should not close `C25`.

### Android Device And Connected TODO Evidence

- `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/worker-reports/android-proof.md`
  - S23/API 36 conflict inbox instrumentation passed in prior worker evidence.
  - Required post-edit compile gate was blocked at that time by an out-of-scope `GoogleSheetsGateway.kt` compile error; current compile is now green, but no current connected rerun was performed by this audit.
  - API 34 proof was blocked by AVD configuration: `unknown skin name 'pixel_3a'`.
  - Clean S23 visual/relaunch/persistence proof was partial only; screenshots had Samsung/DeX trackpad overlay and app paused by Maximum power saving.
- This audit did not run `adb`, install, launch, screenshot, or otherwise touch the physical phone.

### Release TODO Evidence

- `.planning/2026-07-20-wonderfood-105-accelerated-release-campa/release-evidence-20260720-1513/release-evidence.txt`
  - `version_name=1.0.5`, `version_code=5`, `package=com.wonderfood.app`, `git_head=db7202f`.
  - `signing_env=missing`.
  - Missing signing env: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
  - `release_build=skipped_missing_signing_env`, `verified_apk=none`.
  - `google_web_client_id=placeholder`, `assetlinks=placeholder`.
- GitHub read-only checks:
  - `gh pr list --head codex/wonderfood-105-zero-user-reset --state all --json number,state,title,url` -> `[]`.
  - `gh run list --branch codex/wonderfood-105-zero-user-reset --limit 10 --json ...` -> `[]`.
  - `gh release view v1.0.5 --json tagName,name,url,isDraft,isPrerelease` -> `release not found`.
  - `git ls-remote --heads origin codex/wonderfood-105-zero-user-reset` -> no remote branch.
  - `git ls-remote --tags origin 'v1.0.5*'` -> no matching tag.

## Genuine Blockers

- `E06`: local harness fails lint on API 34-only `LocalDate.ofInstant` calls while min SDK is 26.
- `B13` / `E08`: clean error-state screenshot and full visual matrix are missing.
- `C25`: direct signed-in Notion and Google Sheets rendered UI inspection with seed data is missing; API JSON and generated visual reports are not enough.
- `E07`: supported low/current connected suite proof is incomplete; API 34 AVD has `unknown skin name 'pixel_3a'` in current evidence.
- `E09`: physical phone install/relaunch/persistence proof is incomplete; current evidence is partial and this audit did not touch the phone.
- `E12`: current branch has no GitHub workflow runs.
- `E13`: signing environment is missing, so no signed APK/checksum proof exists.
- `E14`: no PR exists for the current branch.
- `E15`: no `v1.0.5` tag or release exists.
- `E16`: no signed/published install artifact or verified install URL exists; OAuth web client and asset links are still placeholders in release evidence.

## Proposed Next Closure Order

1. Fix the lint `LocalDate.ofInstant` API compatibility issue or enable the required desugaring, then rerun `./scripts/quality/android-harness.sh local`.
2. Capture the missing clean error/full visual matrix proof for `B13/E08`.
3. Use a signed-in browser session to capture direct Notion and Sheets provider UI proof for `C25`.
4. Complete connected low/current API proof and physical phone proof without relying on partial S23 screenshots.
5. Load signing env, build signed artifacts/checksums, push branch, open PR, wait for CI, merge, tag/release `v1.0.5`, and verify install artifact/URL.
