# WonderFood open-issues release slice

## Goal

Implement and release the largest truthful slice of the current open GitHub issue backlog without falsely claiming future-scale features are complete. Validate on emulator, open a PR, merge it, publish a release, and leave the phone-install path ready.

## Current open issue set

Source snapshot: `.planning/2026-07-19-release-open-issues/open_issues.json`.

Issues: #1, #2, #5, #6, #7, #11-#38.

## Release strategy

1. Preserve the existing large local implementation for local-first SQLite plus Notion/Sheets/Postgres workspace sync.
2. Add concrete foundation code for the new AI/provider backlog: provider interfaces, lookup clients/mappers, recipe URL/share import detection, typed AI skill contracts, confidence/provenance surfaces, and export/share helpers.
3. Do not close or claim completion for features that require full product UX/community/catalog scale unless the implementation and tests prove it.
4. Use emulator validation for install/launch and core flows; physical phone install comes after release artifact is available.

## Phases

- [in_progress] Phase 1: Inventory issue scope, current dirty tree, and release blockers.
- [pending] Phase 2: Implement provider/AI skill foundations and share/import hooks.
- [pending] Phase 3: Run focused unit tests, build, emulator install/launch proof.
- [pending] Phase 4: Commit, push PR, merge, tag/release, verify artifacts.

## Risks

- The backlog includes broad roadmap features (#11-#38); completing every issue literally is not plausible in one release without cutting corners.
- Current tree is already dirty with substantial prior implementation. We must preserve it and avoid reverting.
- Merge/release must only happen after green checks and a clear PR description of what is and is not complete.
