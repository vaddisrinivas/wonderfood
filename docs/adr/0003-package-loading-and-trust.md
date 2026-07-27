# ADR 0003: Package Loading And Trust

Status: accepted for Day 1
Date: 2026-07-27

## Decision

Package loading uses explicit local JSON artifacts with a trust boundary.

- Loader inputs are compiled package JSON files, not product TypeScript source.
- Trusted sources for Day 1 are checked-in fixture packages under `tests/fixtures/app-packages/reference-app/compiled/`.
- Runtime source, UI routes, and `src/domain/catalog.ts` must stay package-neutral.
- Trust is structural, not remote-signature based, for this sprint.

## Consequences

- The acceptance gate can prove load, activate, rollback, and render without a product-specific code fork.
- Portability failures are caught by grep-based checks over runtime source and by required compiled artifact presence.
- A future authoring-folder compiler can replace fixture JSON with generated package output without changing the runtime trust model.
