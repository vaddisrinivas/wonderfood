# Worker Contracts

## Shared Rules

- Base work on the coordinator-provided checkpoint commit and assigned worktree.
- Read this file, the assigned acceptance rows, and only the relevant source contracts.
- Do not edit files outside assigned ownership.
- Do not create alternate household, sync, proposal, or provider models.
- Do not preserve legacy compatibility.
- Do not run the full Android harness; run assigned focused tests once after a coherent patch.
- Never print credentials. Use `agent-env` for credentialed proof.
- Stop after 15 minutes without a patch, test result, or exact blocker.
- Commit coherent work locally. Do not push, open PRs, merge, tag, or release.

## Worker A: Canonical Runtime

Owns:

- `core/model/**`
- `core/data/**`
- `core/engine/**`
- `app/src/main/java/com/wonderfood/app/data/**`
- Legacy runtime deletion after confirmed caller migration
- Canonical AppFunctions executor, excluding coordinator-owned ViewModel edits

Must not edit: UI, sync providers, docs, build files, planning files, `MainScreenViewModel.kt`.

Assigned acceptance rows: A01-A10, E02, E05, E06, E07, E13.

Focused proof:

- Canonical model contract tests.
- Command executor tests.
- Room repository and migration tests.
- App draft/command mapper and AppFunctions idempotency tests.
- Production legacy-reference scan.

## Worker B: Android Product

Owns:

- `app/src/main/java/com/wonderfood/app/ui/**` except `MainScreenViewModel.kt`
- `app/src/androidTest/**`
- UI-focused app unit tests

Must not edit: core contracts, sync providers, docs, build files, planning files, `MainScreenViewModel.kt`.

Assigned acceptance rows: B01-B13, E08, E09.

Focused proof:

- Compose/unit tests for projections and actions.
- Critical connected `MainScreenTest` journey.
- Screenshots for onboarding, empty/populated/error/conflict, light/dark, large font, landscape, tablet.
- Accessible alternatives for every gesture.

## Worker C: Data Homes

Owns:

- `app/src/main/java/com/wonderfood/app/sync/**`
- Provider-specific source sets
- Provider tests and live-proof scripts
- Postgres schema/migrations and HTTPS/API proof

Must not edit: core contracts, UI, docs, build files, planning files, `MainScreenViewModel.kt`.

Assigned acceptance rows: C01-C25, E03, E04, E12, E14.

Focused proof:

- Fake `DataHomeAdapter` contract suite.
- Merge/conflict/idempotency/retry/recovery tests.
- Provider schema and secret-leak tests.
- Live Notion, Sheets, and Postgres proof scripts during Campaign 2.

## Worker D: Skills and Release Assets

Owns:

- `core/ai/**`
- AI skill assets, fixtures, and tests
- `README.md`, `FEATURES.md`, `CHANGELOG.md`, architecture/privacy/setup/release-note drafts

Must not edit: canonical runtime, UI, sync providers, build files, planning files, `MainScreenViewModel.kt`.

Assigned acceptance rows: D01-D11, E10-E11, E15-E17.

Focused proof:

- Skill fixture and parity tests.
- Allergy/hard exclusion fail-closed tests.
- Direct-write prohibition scan.
- Documentation claims checked against acceptance evidence.

## Coordinator

Owns:

- Contracts and baseline checkpoint.
- `MainScreenViewModel.kt` and all cross-lane wiring.
- Build/configuration files.
- Worker branches/worktrees, integration, conflict resolution, and duplicate deletion.
- Time/token/credit checks at fixed milestones.
- Emulator/device allocation and final quality gates.
- Acceptance matrix, GitHub PR, merge, tag, release, artifacts, and final report.

## Required Handoff Format

```text
Outcome: COMPLETE | PARTIAL | BLOCKED
Commit: <sha or NONE>
Acceptance rows: <ids>
Changed files: <paths>
Tests: <command and pass/fail>
Blocker: <one exact statement or NONE>
Coordinator action: <one exact request or NONE>
```

Maximum 40 lines. No narrative chronology.
