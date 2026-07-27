# ADR 0002: Package Installation Identity

Status: accepted for Day 1
Date: 2026-07-27

## Decision

Compiled package identity is not installation identity.

- `package.id` and `package.version` identify the compiled JSON artifact.
- The active installation is the runtime-selected package held by `RuntimeContext` and the registry.
- Day 1 supports one active installation at a time.
- Package loading and activation must not depend on `src/domain/catalog.ts` naming the package.

## Consequences

- `reference-app@1.0.0` and `reference-app@1.1.0` can be loaded as artifacts, while runtime state still tracks one active package.
- Route code reads the active package from `RuntimeContext` instead of mutating package globals during render.
- Multi-installation, workspace isolation, and installation-scoped identities stay deferred.
