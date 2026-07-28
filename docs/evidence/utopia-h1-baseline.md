# Utopia H1 Baseline

Date: 2026-07-28

Base SHA: `bc05cbe427053098f9fd5114fd353dd08425c525`

## Ownership Rules

- Current dirty paths outside this task are treated as user-owned.
- Expo UI files under `app/` remain off-limits until explicit UI authorization.
- `packages/domain-shared` remains off-limits.
- Implementation may touch server/domain/schema/test/check files needed by
  Horizons 1-2.

## Preexisting Dirty Areas

- Expo UI: `app/**`
- Domain config: `packages/domain-config/**`
- Shared contracts: `packages/shared/contracts/**`
- Server package validation: `server/src/kernel/package*.ts`
- Local data/runtime/db/provider code: `src/**`
- Tests and quality scripts: `tests/**`, `scripts/quality/**`
- Utopia planning artifacts: `tasks/plan.md`, `tasks/todo.md`

## Baseline Gate Status

No product gates were rerun before this implementation pass. Prior planning
inventory said focused installation/isolation gates existed, but aggregate
Horizon 1 and Horizon 2 acceptance were not complete.

## Deterministic H1 Gate

- Command: `npm run check:utopia-h1-e2e`
- Contract:
  clean temp SQLite DB, fixture AI source patch, compile, install twice,
  mutate scoped data, migrate, rollback, export, delete, restore, then
  reinstall from a local immutable registry descriptor.
- Evidence:
  `app/build/evidence/utopia-h1-e2e/<timestamp>/summary.json`
