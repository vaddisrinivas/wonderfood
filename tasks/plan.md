# Utopia Implementation Plan: Horizons 1 and 2

## Outcome

Build Utopia in two release horizons without deleting later ambitions.

**Horizon 1 — Useful Utopia V1**

> Create an app with AI, preview it, install isolated copies, migrate safely,
> back it up locally, and distribute it through immutable JSON links.

**Horizon 2 — Connected Utopia**

> Keep the local-first product working without an account while optionally
> adding accounts, encrypted cloud backup, device management, collaboration,
> expanded plugins, app compositions, and full schema convergence.

Horizon 3 remains parked: full public marketplace, signing/update service,
commercial control plane, and 500-app production hardening.

## Current State

Last focused-gate run: 2026-07-27. Source inventory refreshed on 2026-07-28;
gates were not rerun during this planning turn.

- AppInstallation persistence, package scoping, record/operation isolation, and
  secondary isolation have focused tests.
- Link-install persistence works in tests, but the Expo installer still activates
  the default package and no `/apps/[installationId]` route exists.
- Compiler, plugin, migration, control-room, vault, registry, and sharing
  services are first slices, not complete product flows.
- No canonical `packages/schemas` registry exists.
- The worktree contains unrelated/uncommitted paths.
- Current repository instructions restrict implementation to server chat/runtime
  and forbid Expo UI edits.

## Authorization Boundary

This turn changes planning files only.

Before implementation:

1. Explicitly authorize broader Utopia work beyond server chat/runtime.
2. Explicitly authorize Expo UI edits for installer, control room, Vault,
   account, collaboration, and composition screens.
3. Keep `packages/domain-shared` forbidden unless separately approved.
4. Start from an isolated worktree/branch or a recorded owned-diff manifest.

## Architecture Decisions

1. Libraries surround the kernel; they do not own record mutation, approval,
   installation identity, receipts, or policy.
2. Local SQLite remains the source of truth for offline runtime state.
3. Runtime loads compiled AppPackage artifacts only; AI edits package source.
4. `packages/schemas` is the index and validation entry point for proven portable
   contracts. It is not a speculative master model.
5. New schemas land vertically with a working consumer, fixtures, diagnostics,
   and compatibility proof.
6. Validation pipeline:
   structural schema -> semantic references -> compatibility -> policy ->
   checksum/signature -> complexity budget.
7. Existing serialized `schemaVersion` values remain stable. Registry aliases
   resolve schemas and never rewrite payloads.
8. Canonical source edits use bounded RFC 6902 JSON Patch operations and RFC 6901
   paths. Higher-level editing helpers compile to patches; they are not a second DSL.
9. Canonical payload hashing uses RFC 8785 JSON Canonicalization Scheme.
10. Every app/data/action path is workspace- and installation-scoped.
11. Risky migration mutation and package activation share one SQLite transaction.
12. Vault encryption/decryption occurs locally. Cloud storage receives ciphertext.
13. Collaboration syncs canonical operations, never raw SQLite files.
14. Cloud identity and storage use adapters. OIDC, PostgreSQL metadata, and
    S3-compatible object storage are the target interfaces; provider selection is
    a Horizon 2 decision gate.
15. Initial collaboration uses authenticated HTTP push/pull and durable cursors,
    not CRDTs or a framework-owned operation model.
16. Compositions receive typed capabilities and grants, not raw database access.
17. Focused gates, aggregate gates, device proof, and release acceptance are
    reported separately.

## Code-Reduction and Standards Strategy

### Use now

| Area | Decision | Expected effect |
|---|---|---|
| Validation | One Draft 2020-12 Ajv adapter, shared fixtures, stable diagnostic codes and JSON Pointer paths | Delete duplicate client/server validation and raw Ajv mapping; roughly 700-1,200 LOC removable |
| Fixtures | One package-validation corpus consumed by root/server/runtime gates | Remove roughly 955 byte-identical fixture LOC immediately after parity |
| Authoring | RFC 6902 patches via existing `fast-json-patch`; RFC 6901 path allowlist | Replace canonical semantic edit DSL and much template plumbing; roughly 500-900 LOC removable |
| Hashing | RFC 8785 via existing `json-canonicalize` | Consolidate canonical stringify/hash helpers; roughly 100-250 LOC removable |
| Distribution | GitHub Actions + immutable Releases + Pages index | Avoid a custom Horizon 1 registry backend and publisher service |
| State | SQLite state rows and receipts for durable workflows | Avoid adding framework state machines where DB state is authoritative |
| HTTP | Move routes from the 1,842-line server entrypoint into existing Hono modules | Roughly 450-700 LOC of routing/response plumbing removable |
| Provider sync | One replay/refetch/receipt pipeline with provider adapters | Roughly 300-550 LOC removable while preserving provider authority |
| Tool args | Reuse the existing Ajv adapter for MCP tool schemas and cache compiled validators | Roughly 80-100 LOC removable |

Current evidence:

- `tests/fixtures/package-validation` and
  `server/test/fixtures/package-validation` are byte-identical.
- Structural package checks overlap in `packages/shared/contracts/package.ts`,
  `server/src/kernel/package.ts`, and `server/src/kernel/package-schema.ts`.
- Regex/template authoring is concentrated in the 1,335-line
  `src/domain/package-change-templates.ts`.
- Route plumbing is concentrated in the 1,842-line `server/src/index.ts` while
  Hono modules already exist.
- Replay/refetch lifecycles overlap in Notion and Sheets webhook adapters.

### Evaluate at H2-00

- Use provider-neutral OIDC, PostgreSQL, and S3-compatible storage interfaces
  around the Utopia kernel. Do not adopt a platform-specific cloud shortcut as
  product architecture.
- Product code must not import a cloud-provider SDK in the kernel or mobile
  runtime. Enforce this with a repository static-import check. Mobile talks to
  the Utopia API, not directly to provider tables or object storage.
- Account identity maps external OIDC subjects to internal Utopia account IDs;
  provider identifiers never become tenant authority.
- RLS policies must be ordinary PostgreSQL SQL where possible. Any
  provider-specific policy behavior must be quarantined inside an adapter and
  covered by an exit test against plain PostgreSQL-compatible infrastructure.
- Object storage uses opaque keys, S3-compatible operations where possible, and
  full metadata/blob export and restore, including receipts and audit rows.
- Make `@json-render/core` the canonical presentation contract and keep one V3
  compatibility adapter; remove the parallel A2UI-shaped translation only after
  renderer parity, which may remove roughly 650-950 LOC.
- Use a maintained OIDC client for authorization code, PKCE, token validation,
  refresh rotation, and JWKS handling; keep Utopia device/session policy custom.
- Evaluate SQLite JSON1/set-based migrations and supported serialization/backup
  APIs, but only after atomicity and cross-platform capability probes.
- Acceptance requires local development, exportability, provider-neutral
  repository interfaces, RLS hostile-query tests, tenant-context reset proof,
  storage policy proof, provider-SDK import rejection in kernel/mobile, exit
  restore into a non-provider stack, and no server access to Vault plaintext or
  keys.
- If it fails those gates, use standards-compatible components separately.

### Keep custom

- Installation identity and activation.
- Canonical record/operation writer and approval policy.
- Migration transaction, recovery journal, and receipts.
- Collaboration authorization, sequencing, conflicts, and revocation.
- Composition grants and proposal-only writes.

### Avoid for Horizons 1-2

- A giant all-domain schema.
- A second semantic authoring language.
- CRDTs, OpenFGA, workflow engines, or a marketplace backend before demonstrated need.
- Calling checksum-only GitHub packages “trusted” or “signed.”

### Standards and platform references

- JSON Schema Draft 2020-12: `https://json-schema.org/draft/2020-12`
- RFC 6902 JSON Patch: `https://datatracker.ietf.org/doc/html/rfc6902`
- RFC 8785 JSON Canonicalization: `https://www.rfc-editor.org/rfc/rfc8785.html`
- GitHub immutable Releases:
  `https://docs.github.com/en/code-security/concepts/supply-chain-security/immutable-releases`
- GitHub artifact attestations:
  `https://docs.github.com/en/actions/concepts/security/artifact-attestations`
- OIDC Core: `https://openid.net/specs/openid-connect-core-1_0.html`
- PostgreSQL row security: `https://www.postgresql.org/docs/current/ddl-rowsecurity.html`
- Amazon S3 API reference: `https://docs.aws.amazon.com/AmazonS3/latest/API/Welcome.html`

## Dependency Graph

```text
Owned baseline
  -> core schema authority
      -> secure immutable installer -> app routes/library
      -> durable atomic migrations
      -> AI control room
      -> local encrypted Vault
      -> GitHub registry distribution
          -> Horizon 1 acceptance
              -> cloud architecture decision
                  -> vertical schemas + plugins
                  -> accounts/devices + cloud Vault
                  -> membership + operation sync
                  -> app compositions
                      -> Horizon 2 acceptance
```

# Horizon 1 — Useful Utopia V1

## H1-00: Establish an Owned Baseline

**Description:** Classify current dirty paths, preserve unrelated work, record
the exact base SHA, and create an isolated implementation branch/worktree or
owned-diff manifest.

**Acceptance criteria:**

- [ ] Every dirty path has an owner and category.
- [ ] Exact base SHA and existing gate results are recorded.
- [ ] No unrelated path is reverted, staged, or included.

**Verification:**

- [ ] `git diff --check`
- [ ] `npm run check:platform-day1`

**Dependencies:** None

**Files likely touched:**

- `tasks/todo.md`
- `docs/evidence/utopia-h1-baseline.md`

**Estimated scope:** S

## H1-01: Freeze Registry and Diagnostic Contracts

**Description:** Define registry keys, stable URIs, aliases, maturity,
validator pipeline metadata, fixture manifests, and normalized diagnostics.
Preserve `wonder.app-package.v2` and `wonder.app-package.v3`; a materially new
Utopia package becomes V4.

**Acceptance criteria:**

- [ ] Registry rejects duplicate IDs/URIs/aliases, missing files, bad `$id`,
      unsupported drafts, and circular references.
- [ ] Diagnostic codes and categories are stable; raw Ajv messages never cross
      the adapter boundary.
- [ ] Registry key, schema `$id`, and payload `schemaVersion` remain distinct.

**Verification:** Registry integrity and diagnostic fixture tests.

**Dependencies:** H1-00

**Likely files:** `packages/schemas/registry.v1.json`,
`packages/schemas/diagnostics/diagnostic-codes.v1.json`,
`packages/schemas/README.md`

**Estimated scope:** M

## H1-02: Register Existing AppPackage V2/V3

**Description:** Faithfully register the current V2/V3 compiled contracts.
Do not redesign or add future schemas.

**Acceptance criteria:**

- [ ] Food and neutral reference packages preserve behavior and checksums.
- [ ] One valid/invalid fixture manifest drives shared, mobile, and server tests.
- [ ] Unknown package IDs remain valid; runtime files do not import package IDs.

**Verification:** AppPackage parity and fixture-corpus tests.

**Dependencies:** H1-01

**Likely files:** `packages/schemas/schemas/package/*`,
`packages/schemas/fixtures/app-package/*`

**Estimated scope:** M

## H1-03: Build Shared Validation Adapter

**Description:** Provide cached Draft 2020-12 structural validation, semantic
hooks, RFC 8785 canonicalization, hashing, and normalized diagnostics through one
API used by client and server.

**Acceptance criteria:**

- [ ] `validateArtifact({ schemaId, value })` returns deterministic issues.
- [ ] Structural/reference/compatibility/capability/policy/checksum/budget errors
      remain distinguishable.
- [ ] Old imports are temporary re-exports with a removal issue, not copied validators.

**Verification:**

- [ ] Add `npm run check:schema-registry`.
- [ ] Add it to `npm run check:platform-day1`.
- [ ] Shared/server/runtime failure categories match.

**Dependencies:** H1-02

**Likely files:** `packages/schemas/src/*`, `scripts/check-schema-registry.mjs`,
`server/src/kernel/package-schema.ts`, `packages/shared/contracts/package.ts`

**Estimated scope:** L

## H1-04: Prove Core Authority

**Description:** Route compiler output and runtime package loading through the
shared adapter. Other artifact schemas wait for their consumer vertical.

**Acceptance criteria:**

- [ ] Compiler output is validated by the runtime contract.
- [ ] Existing package behavior and wire IDs are unchanged.
- [ ] Repository scan finds no active duplicate AppPackage structural validator.

**Verification:** `check:schema-registry`, `check:platform-day1`, typecheck.

**Dependencies:** H1-03

**Estimated scope:** M

## Checkpoint H1-A: AppPackage Authority

- [ ] Registry, adapter, fixtures, server parity, and runtime parity pass.
- [ ] Existing V2/V3 identity and checksums are preserved.
- [ ] No speculative installation/authoring/migration/Vault schema was added.

## H1-05A: Register AppInstallation Persistence

**Description:** Add `AppInstallation` and installation-package-state schemas
against the existing persistence/isolation consumer.

**Acceptance criteria:**

- [ ] Lifecycle, workspace, installation ID, package binding, provenance,
      active revision, and failure state are explicit.
- [ ] Existing persistence/isolation fixtures preserve behavior.
- [ ] Invalid or cross-workspace state fails before persistence.

**Verification:** Schema/persistence parity and file-backed restart tests.

**Dependencies:** H1-04

**Estimated scope:** M

## H1-05B: Harden Immutable Link Resolution

**Description:** Add `InstallDescriptor` with its real resolver consumer. Make
descriptor/artifact resolution read-only and hostile-input safe before UI.

**Acceptance criteria:**

- [ ] Descriptor binds exact package ID/version, artifact URL/checksum, source
      repository/revision, and minimum runtime.
- [ ] The server-safe resolver returns final URL, redirect chain, resolved
      addresses, bytes, content type, and checksum.
- [ ] Every redirect is revalidated; DNS rebinding, loopback/private/link-local/
      metadata IPs, credentials, file URLs, and downgrade redirects fail.
- [ ] Streaming byte limit, connect/read/total timeout, abort, CORS/proxy policy,
      content type, and checksum fail closed.
- [ ] Resolution never mutates installation/package state.
- [ ] Descriptor display metadata is untrusted; authoritative preview identity,
      permissions, capabilities, plugins, and migrations come from the validated artifact.

**Verification:**

- [ ] Add SSRF, redirect chain, DNS rebinding, oversized/chunked body, slow body,
      timeout/abort, CORS, and checksum adversarial tests.
- [ ] `npm run check:link-install`

**Dependencies:** H1-05A

**Files likely touched:**

- `src/domain/package-install.ts`
- `packages/shared/contracts/package-install.ts`
- `tests/domain/package-install.test.ts`
- `tests/platform/package-install-flow.test.ts`

**Estimated scope:** M

## H1-06: Make Installation Atomic

**Description:** Persist a durable install intent before mutation, then validate
and activate package/installation/receipt in one exclusive SQLite transaction
keyed by intent and idempotency IDs. Add the install-receipt schema here with
its activation consumer.

**Acceptance criteria:**

- [ ] Failure leaves prior installations and active packages unchanged.
- [ ] Approval binds workspace, installation intent, descriptor hash, artifact
      hash, identity/version, compatibility, actor, expiry, nonce, and policy.
- [ ] Consumed approvals and receipts reject replay.
- [ ] Restart deterministically resumes an unconsumed intent or records failure;
      no impossible half-active `installing` state exists.

**Verification:**

- [ ] Add file-backed SQLite interruption/restart tests.
- [ ] `npm run check:link-install`

**Dependencies:** H1-05B

**Files likely touched:**

- `src/db/app-package-registry.ts`
- `src/db/migrations.ts`
- `tests/platform/package-install-flow.test.ts`
- `tests/db/package-install-recovery.test.ts`

**Estimated scope:** M

## H1-07A: Add Installation Routes and Provider

**Description:** Create nested installation routes and one route-owned
installation context/provider.

**Acceptance criteria:**

- [ ] `/apps/[installationId]` loads the selected installation only.
- [ ] Missing/disabled/failed installations render deterministic states.
- [ ] Package ID/default package is never used as installation identity.

**Verification:**

- [ ] Add route/runtime integration tests for two installations.
- [ ] Manual web and Android route check.

**Dependencies:** H1-06 and Expo UI authorization

**Files likely touched:**

- `app/apps/[installationId]/_layout.tsx`
- `app/apps/[installationId]/index.tsx`
- `src/domain/runtime-context.tsx`
- `src/presentation/json-render-route.tsx`
- `tests/domain/runtime-context.test.ts`

**Estimated scope:** M

## H1-07B: Scope Runtime Repository Consumers

**Description:** Bind repository, package lifecycle, renderer, chat, search,
provider writeback, workflows, and navigation to explicit installation context;
remove default-install fallbacks from app routes.

**Acceptance criteria:**

- [ ] Every read/write requires workspace and installation.
- [ ] Missing scope fails closed instead of selecting a default.
- [ ] Same package installed twice remains isolated across every consumer.

**Verification:** Focused two-install tests for each consumer and repository scan
for default-install fallbacks.

**Dependencies:** H1-07A

**Estimated scope:** L

## H1-07C: Prove Core Runtime Isolation

**Description:** Run one adversarial flow across route, records, operations,
chat, search, workflows, and provider writeback. Migration/control-room/Vault
isolation joins H1-20 after those verticals exist.

**Acceptance criteria:**

- [ ] Cross-installation reads/writes/replays are rejected.
- [ ] Restart preserves route and installation identity.

**Verification:** Add `npm run check:installation-isolation-e2e`.

**Dependencies:** H1-07B

**Estimated scope:** M

## H1-08: Complete Installer and Local App Library

**Description:** Connect install approval to H1-06, route to H1-07A/C, and provide
first-run/local-library choices.

**Acceptance criteria:**

- [ ] Link, registry, bundled demo, and existing installation paths work.
- [ ] Preview derives authoritative package identity, permissions, capabilities,
      plugins, and migrations from the validated artifact, not descriptor prose.
- [ ] Successful install opens the new installation route.
- [ ] Restart and second install preserve both app instances.

**Verification:**

- [ ] Add installer/library UI integration tests.
- [ ] Manual web and Android install/open/restart check.

**Dependencies:** H1-07C and Expo UI authorization

**Files likely touched:**

- `app/install.tsx`
- `app/apps/index.tsx`
- `app/(tabs)/index.tsx`
- `src/presentation/app-library.tsx`
- `tests/platform/package-install-ui.test.tsx`

**Estimated scope:** M

## Checkpoint H1-B: Portable Isolated Apps

- [ ] Two packages install as separate app instances.
- [ ] Same record ID remains isolated between installations.
- [ ] Install restart/replay is deterministic.
- [ ] Real UI flow, not only service tests, passes.

## H1-09: Persist the Migration Journal

**Description:** Add `MigrationPlan`, snapshot, approval, and receipt schemas
with installation-scoped journal tables and explicit lifecycle states.

**Acceptance criteria:**

- [ ] Planned, approved, applying, activated, rolled_back, recovered, failed,
      and manual_review states persist.
- [ ] Approval binds workspace, installation, current package revision, snapshot,
      from/to package hashes, exact operation set, actor, policy category,
      expiry, nonce, and consumed receipt.
- [ ] Snapshot, affected-record counts, plan, approval, package, and actor hashes
      are durable.
- [ ] Cross-installation transition and replay attempts fail.

**Verification:**

- [ ] `npm run check:migrations`
- [ ] Add real SQLite restart and replay tests.

**Dependencies:** H1-04

**Files likely touched:**

- `src/db/migrations.ts`
- `src/db/package-migrations.ts`
- `tests/db/package-migration-journal.test.ts`
- `tests/helpers/memory-db.ts`

**Estimated scope:** M

## H1-10: Execute Migrations Atomically

**Description:** Apply supported declarative record operations and package
activation in one SQLite transaction.

Supported V1 operations:

```text
add_field
rename_field
copy_field
set_default
map_enum
archive_collection
assert_invariant
```

**Acceptance criteria:**

- [ ] Every operation executes or rejects before mutation.
- [ ] Invariant/package failure restores records and package state exactly.
- [ ] Destructive/review-required changes require matching approval.

**Verification:**

- [ ] `npm run check:package-migrations`
- [ ] Fault injection covers each transaction boundary.

**Dependencies:** H1-09

**Files likely touched:**

- `src/domain/package-migrations.ts`
- `src/db/package-migrations.ts`
- `tests/domain/package-migrations.test.ts`
- `tests/db/package-migration-atomicity.test.ts`

**Estimated scope:** M

## H1-11: Complete Migration Recovery

**Description:** Recover interrupted migration state from the durable journal,
without caller-supplied in-memory snapshots.

**Acceptance criteria:**

- [ ] Restart completes or rolls back exactly once.
- [ ] Previous invariants and package checksum are verified after recovery.
- [ ] Ambiguous state becomes manual review with a durable receipt.

**Verification:**

- [ ] File-backed process-death/restart test.
- [ ] Idempotent recovery replay test.

**Dependencies:** H1-10

**Files likely touched:**

- `src/db/package-migrations.ts`
- `src/domain/package-migrations.ts`
- `tests/db/package-migration-recovery.test.ts`

**Estimated scope:** M

## H1-12A: Establish Durable Package Source Authority

**Description:** Add the `PackageSource` schema only now, with an
installation-scoped source store, immutable revisions, source-to-artifact
bindings, and rollback.

**Acceptance criteria:**

- [ ] Active source, revision history, compiled artifact hash, and installation
      binding survive restart.
- [ ] Source rollback restores a prior source revision and recompiles without
      mutating records.
- [ ] Compiled packages are outputs; they are never the editable authority.

**Verification:**

- [ ] File-backed revision, stale-base, path traversal, replay, and rollback tests.

**Dependencies:** H1-04, H1-07B

**Files likely touched:**

- `src/db/package-source.ts`
- `src/domain/package-authoring.ts`
- `src/domain/package-control-room.ts`
- `packages/schemas/schemas/authoring/*`
- `tests/domain/package-source.test.ts`

**Estimated scope:** L

## H1-12B: Build Control-Room Source Browser and Forms

**Description:** Add an installation-scoped package source browser and
registry-generated forms for core package sections.

**Acceptance criteria:**

- [ ] App, Collections, Screens, Queries, Rules, Workflows, Providers, Theme,
      and Capabilities are browsable.
- [ ] Form edits create bounded patches against the current source revision.
- [ ] Invalid fields show stable diagnostics at the relevant JSON Pointer.

**Verification:** UI integration plus web/Android accessibility/layout checks.

**Dependencies:** H1-12A and Expo UI authorization

**Likely files:** `app/apps/[installationId]/control-room.tsx`,
`src/presentation/control-room/*`

**Estimated scope:** M

## H1-13: Add Real AI Creation and Authoring

**Description:** Add `AuthoringChange` with its temporary-workspace and review
consumers. Use the existing AI SDK structured-output path to create a blank app
or propose bounded RFC 6902 patches, then compile without touching active source.

**Acceptance criteria:**

- [ ] A real model call can create a minimal valid PackageSource from blank state.
- [ ] Structured output is schema-bound; prose, malformed, oversized, and
      provider-failure responses leave source unchanged.
- [ ] Runtime/server/native/config/secrets paths are forbidden.
- [ ] Allowed paths, operations, patch count, payload size, and complexity are bounded.
- [ ] Stale base revision rejects before patch application.
- [ ] Temporary workspace cleanup is deterministic on success and failure.
- [ ] Deterministic forms and tests do not depend on model availability.

**Verification:**

- [ ] `npm run check:package-authoring`
- [ ] Add forbidden-path, stale-revision, and cleanup tests.

**Dependencies:** H1-12A

**Files likely touched:**

- `src/domain/package-authoring.ts`
- `packages/shared/contracts/package-authoring.ts`
- `packages/app-compiler/index.ts`
- `tests/platform/package-authoring.test.ts`

**Estimated scope:** M

## H1-14: Complete Diff, Preview, Approval, Activation, and Rollback

**Description:** Connect proposals to compiler diagnostics, semantic diff,
production-renderer preview, hash-bound approval, selected-installation
activation, migration proof, and rollback.

**Acceptance criteria:**

- [ ] Valid proposals show changed files, semantic risk, diagnostics, and preview.
- [ ] Approval binds workspace, installation, base/source revision, snapshot,
      from/to hashes, exact operations, actor, policy category, expiry, nonce,
      and consumed receipt.
- [ ] Activation/rollback emits receipts and preserves installation isolation.

**Verification:**

- [ ] Add `npm run check:package-control-room-e2e`.
- [ ] Manual add-field/add-screen/invalid-proposal/rollback flow.

**Dependencies:** H1-11, H1-12B, H1-13

**Files likely touched:**

- `src/presentation/control-room/proposal-panel.tsx`
- `src/presentation/control-room/diff-panel.tsx`
- `src/presentation/control-room/preview-panel.tsx`
- `src/presentation/control-room/control-bar.tsx`
- `tests/platform/package-control-room-e2e.test.tsx`

**Estimated scope:** M

## Checkpoint H1-C: Safe App Evolution

- [ ] AI edits source, not runtime.
- [ ] Preview uses the production renderer.
- [ ] Risky migration is durable, atomic, and recoverable.
- [ ] Selected installation activates and rolls back without cross-app effects.

## H1-15A: Freeze Platform-Safe Vault Crypto

**Description:** Run a web/native spike and approve exact AEAD, passphrase KDF,
library, parameters, random source, envelope limits, versioning, and key-erasure
behavior before storing real backups.

**Acceptance criteria:**

- [ ] The same known-answer vectors pass in Node, web, and Android runtimes.
- [ ] ADR records security assumptions, dependency maintenance, parameter
      upgrade path, malformed-input limits, and lost-password behavior.
- [ ] No homemade cryptographic primitive is accepted.

**Verification:** Cross-platform spike and independent security review.

**Dependencies:** H1-04

**Estimated scope:** M

## H1-15B: Implement Local Vault Crypto

**Description:** Add `VaultEnvelope` with its approved crypto consumer and
platform adapters.

**Acceptance criteria:**

- [ ] Envelope records algorithm, KDF, parameters, salt, nonce, tag, and version.
- [ ] Nonces are unique and keys/plaintext are never logged or serialized.
- [ ] Wrong-key, tamper, truncation, oversized/malformed, and unknown-version
      inputs fail closed before expensive allocation.

**Verification:**

- [ ] Add crypto known-answer and hostile-envelope tests.
- [ ] Run web and Android export builds.

**Dependencies:** H1-15A

**Files likely touched:**

- `src/crypto/vault.ts`
- `src/crypto/vault.native.ts`
- `src/crypto/vault.web.ts`
- `tests/domain/vault-crypto.test.ts`

**Estimated scope:** M

## H1-16: Build Atomic Workspace Export and Restore

**Description:** Add `VaultPayload` and `RestorePreview` with their real
export/restore consumers. Take one consistent snapshot of all runnable state,
encrypt it, preview it, and restore in dependency order through one transaction.

**Acceptance criteria:**

- [ ] Versioned manifest includes installations, package artifacts, source and
      revision history, schema/config, records, relations, operations, migration
      journals/snapshots/receipts, grants, and required metadata.
- [ ] Export omits secrets and includes deterministic counts/checksums.
- [ ] Restore preview reports affected counts, collisions, missing dependencies,
      quota/size limits, schema upgrades, and chosen collision policy.
- [ ] Failed restore leaves the local workspace unchanged.

**Verification:**

- [ ] Add file-backed encrypted round-trip and crash/failure tests.
- [ ] `npm run check:vault`

**Dependencies:** H1-11, H1-12A, H1-15B

**Files likely touched:**

- `src/domain/package-sharing.ts`
- `src/db/workspace-vault.ts`
- `tests/domain/vault.test.ts`
- `tests/platform/vault-restore.test.ts`

**Estimated scope:** M

## H1-17: Add Local Vault Product Flow

**Description:** Add export, inspect, restore-preview, approve, restore, and
error/recovery controls without requiring an account.

**Acceptance criteria:**

- [ ] User can export and restore entirely offline.
- [ ] Web/native file-picker, document storage, cancellation, and permission
      adapters are explicit and tested.
- [ ] Password/key input is never persisted accidentally.
- [ ] Consequences and conflicts appear before restore approval.

**Verification:**

- [ ] Add local Vault UI integration tests.
- [ ] Manual web and Android export/restore check.

**Dependencies:** H1-16 and Expo UI authorization

**Files likely touched:**

- `app/system.tsx`
- `src/presentation/vault/export-panel.tsx`
- `src/presentation/vault/restore-panel.tsx`
- `tests/presentation/vault-ui.test.tsx`

**Estimated scope:** M

## H1-18: Build Immutable GitHub Registry Distribution

**Description:** Add `RegistryManifest` and `RegistryIndex` with deterministic
build/hash/descriptor/index tooling. Publish immutable GitHub Release assets plus
a GitHub Pages discovery index, without marketplace infrastructure.

**Acceptance criteria:**

- [ ] Build output, manifest, descriptor, and index are reproducible from an
      exact source revision.
- [ ] Release upload/download round-trip resolves an exact immutable asset and
      checksum, never mutable `main`.
- [ ] Registry validates integrity metadata and duplicate package/version rules.
- [ ] Product labels this checksum-only lane “unsigned integrity,” not publisher trust.
- [ ] Installer receives the same descriptor path used by direct links.

**Verification:**

- [ ] `npm run check:registry-scale`
- [ ] Add mutable-ref, checksum, redirect, and duplicate-entry tests.

**Dependencies:** H1-05B

**Files likely touched:**

- `src/domain/package-registry.ts`
- `src/domain/package-sharing.ts`
- `tests/domain/registry-scale.test.ts`
- `tests/fixtures/package-install/registry.json`

**Estimated scope:** M

## H1-19: Integrate Registry Picker and Install Metadata

**Description:** Connect registry browsing to the installer and retain source,
release, checksum, channel, and update-review metadata on the installation.

**Acceptance criteria:**

- [ ] Registry selection previews and installs through the atomic installer.
- [ ] Installation records immutable source/release/checksum provenance.
- [ ] Changed permissions/plugins/migrations always require review.

**Verification:**

- [ ] Add `npm run check:registry-distribution`.
- [ ] Manual registry install and changed-release review check.

**Dependencies:** H1-08, H1-18, and Expo UI authorization

**Files likely touched:**

- `app/install.tsx`
- `src/db/app-package-registry.ts`
- `src/presentation/registry-picker.tsx`
- `tests/platform/registry-install-ui.test.tsx`

**Estimated scope:** M

## H1-20: Horizon 1 Acceptance

**Description:** Verify the complete product journey from one exact commit and
publish evidence without conflating focused, aggregate, device, or release status.

**Acceptance criteria:**

- [ ] Create/edit, install, migrate, rollback, export/restore, and registry install pass.
- [ ] Two installations remain isolated across every flow.
- [ ] Deterministic longitudinal test performs: clean DB -> fixture structured
      AI output -> compile -> install two copies -> mutate -> migrate -> rollback
      -> export -> delete -> restore -> local immutable-registry reinstall.
- [ ] Separate live evidence proves one real model creation and one immutable
      GitHub Release publish/install round-trip.
- [ ] Evidence names exact commit SHA and owned diff.

**Verification:**

- [ ] `npm run config:validate`
- [ ] `npm run typecheck`
- [ ] `npm run doctor`
- [ ] `npm run export:web`
- [ ] `npm run export:android`
- [ ] `npm run phase3:check:chat-send`
- [ ] `npm run phase3:check:chat-rollback-idempotency`
- [ ] `npm run check:platform-day1`
- [ ] `npm run check:app-installation-foundation`
- [ ] `npm run check:app-installation-data`
- [ ] `npm run check:app-installation-secondary`
- [ ] `npm run check:schema-registry`
- [ ] `npm run check:link-install`
- [ ] `npm run check:package-compiler`
- [ ] `npm run check:package-authoring`
- [ ] `npm run check:package-migrations`
- [ ] `npm run check:package-control-room-e2e`
- [ ] `npm run check:vault`
- [ ] `npm run check:registry-distribution`
- [ ] `npm run check:utopia-h1-e2e`
- [ ] Live AI provider proof; reported separately from deterministic gates.
- [ ] Live GitHub Release round-trip; reported separately from deterministic gates.

**Dependencies:** H1-14, H1-17, H1-19

**Files likely touched:**

- `docs/evidence/utopia-h1-acceptance.md`
- `tasks/todo.md`

**Estimated scope:** S

## Horizon 1 Exit Gate

Horizon 2 cannot start until:

- [ ] H1-20 passes from one exact commit.
- [ ] Real web and Android flows pass.
- [ ] No P0/P1 Horizon 1 acceptance issue is open or partial.
- [ ] Local-only use works without account/network.
- [ ] User reviews and accepts the Horizon 1 product loop.

# Horizon 2 — Connected Utopia

## H2-00: Approve Cloud Architecture and Threat Model

**Description:** Write the production cloud ADR before adding account or sync
code. Select concrete OIDC, PostgreSQL, object-storage, deployment, key-recovery,
retention, and incident ownership behind replaceable provider adapters.

**Acceptance criteria:**

- [ ] Trust boundaries, data classes, attackers, credential lifecycle, metadata
      exposure, server-compromise model, and cryptographic deletion are explicit.
- [ ] Exact key hierarchy, AEAD/KDF, device enrollment/proof, DEK wrapping,
      recovery, rotation/rewrap, revoked-device behavior, old-backup access, and
      unrecoverable-key behavior are frozen.
- [ ] Client-verifiable device-key binding and anti-substitution design is
      frozen: owner signatures or equivalent transparency detect server key swaps.
- [ ] Cloud spike proves local development, OIDC, PostgreSQL RLS,
      S3-compatible storage, exportability, outage behavior, and
      provider-neutral adapters.
- [ ] Static import check rejects cloud-provider SDK imports from kernel and
      mobile runtime.
- [ ] Mobile uses Utopia API endpoints; direct table/object mutation and direct
      object-store mutation are rejected.
- [ ] Exit test restores account metadata, object metadata, blobs, and receipts
      plus audit rows into a non-provider local stack.
- [ ] Provider choices and operational owners are approved.
- [ ] Local-only behavior remains a non-negotiable compatibility requirement.

**Verification:**

- [ ] Security review of ADR and data-flow diagram.
- [ ] No cloud implementation starts while decisions are unresolved.

**Dependencies:** Horizon 1 Exit Gate

**Files likely touched:**

- `docs/adr/utopia-cloud-architecture.md`
- `docs/security/utopia-cloud-threat-model.md`
- `docs/architecture/utopia-cloud-data-flow.md`

**Estimated scope:** M

## H2-01: Expand the Schema Registry

**Description:** Inventory remaining portable artifacts and assign every future
schema to its owning implementation vertical. Do not pre-create all contracts.

**Acceptance criteria:**

- [ ] Every candidate has owner, consumer, maturity, compatibility, fixtures,
      diagnostics, and target task.
- [ ] No schema lands without a working consumer and boundary tests.

**Verification:**

- [ ] Add `npm run check:schema-registry-full`.
- [ ] Registry inventory has no duplicate canonical owner.

**Dependencies:** H2-00

**Files likely touched:**

- `packages/schemas/registry.v1.json`
- `packages/schemas/schemas/presentation.v1.schema.json`
- `packages/schemas/schemas/data-operation.v1.schema.json`
- `packages/schemas/schemas/cloud-collaboration.v1.schema.json`
- `tests/contracts/schema-registry-full.test.ts`

**Estimated scope:** M

## H2-02: Finish Schema Convergence

**Description:** After all Horizon 2 verticals land, route proven presentation,
provider, cloud, collaboration, and composition contracts through the registry,
then remove duplicate structural validators only after parity.

**Acceptance criteria:**

- [ ] Structural validation has one owner for each registered artifact.
- [ ] Semantic authority checks remain product-owned and installation-scoped.
- [ ] Old schema paths are aliases or removed with no live imports.

**Verification:**

- [ ] Import-boundary and parity tests pass.
- [ ] Repository scan finds no duplicate canonical schema implementation.

**Dependencies:** H2-04, H2-11, H2-16, H2-20

**Files likely touched:**

- `packages/shared/contracts/index.ts`
- `server/src/kernel/package-schema.ts`
- `src/domain/catalog.ts`
- `tests/contracts/import-boundary.test.ts`

**Estimated scope:** M

## H2-03: Expand Plugin Classes and Resolution

**Description:** Add plugin manifest/requirement/lock schemas with their
runtime, build, server, and specialized-runtime resolver consumers.

**Acceptance criteria:**

- [ ] Resolver returns compatible, fallback, requires-new-build, or unsupported.
- [ ] Compiled package locks exact plugin version/checksum/capabilities.
- [ ] Dependency cycles, checksum drift, and missing required plugins fail.

**Verification:**

- [ ] Add `npm run check:plugin-compatibility-expanded`.
- [ ] Add hostile dependency/lock fixtures.

**Dependencies:** H2-01

**Files likely touched:**

- `packages/shared/contracts/plugin.ts`
- `src/domain/plugin-resolver.ts`
- `server/src/kernel/package.ts`
- `tests/platform/plugin-compatibility.test.ts`

**Estimated scope:** M

## H2-04: Prove Plugin Authority and Fallbacks

**Description:** Build one harmless runtime plugin and one fake build-required
plugin. Enforce observation/proposal/operation-only mutation and functional fallback.

**Acceptance criteria:**

- [ ] Runtime plugin returns evidence and a proposed canonical operation.
- [ ] Kernel remains the only mutation writer.
- [ ] Unsupported optional/required capabilities fallback or block correctly.

**Verification:**

- [ ] Expanded plugin gate covers direct-write attempts and fallback rendering.

**Dependencies:** H2-03

**Files likely touched:**

- `src/plugins/device-context.ts`
- `src/plugins/registry.ts`
- `tests/fixtures/plugins/device-context.json`
- `tests/platform/plugin-runtime-proof.test.ts`

**Estimated scope:** M

## H2-05: Define Account, Session, and Device Contracts

**Description:** Add account/session/device schemas with their first real
consumer. Define optional identity, short-lived sessions, device credentials,
recovery-key metadata, revocation, and local/account linking.

**Acceptance criteria:**

- [ ] Account absence never blocks local use.
- [ ] Session/device tokens are scoped, expiring, rotatable, and revocable.
- [ ] Device enrollment requires proof of possession; redirect and client
      storage contracts are platform-specific and fail closed.
- [ ] Caller headers cannot invent server-trusted identity.

**Verification:**

- [ ] Contract fixtures cover replay, expiry, forged principal, and revoked device.

**Dependencies:** H2-00, H2-01

**Files likely touched:**

- `packages/shared/contracts/account.ts`
- `packages/shared/contracts/device.ts`
- `server/src/cloud/contracts.ts`
- `tests/contracts/account-device.test.ts`

**Estimated scope:** M

## H2-06: Add Cloud Metadata Persistence

**Description:** Add PostgreSQL-backed account, device, workspace, session, and
base audit tables behind repository interfaces. Membership, Vault, and operation
tables land later with their owning vertical contracts.

**Acceptance criteria:**

- [ ] Migrations are transactional, idempotent, and rollback tested.
- [ ] Workspace-owned tables use composite tenant keys and PostgreSQL RLS;
      account/global rows have explicit non-workspace policy.
- [ ] Request transactions set trusted principal/workspace context locally and
      pooled connections prove context reset before reuse.
- [ ] Repository APIs never expose an unscoped list or mutation.

**Verification:**

- [ ] Add `npm run check:cloud-migrations`.
- [ ] Real PostgreSQL tests prove FKs, RLS, hostile direct queries, pool reuse,
      transaction-local tenant context reset, backup, and restore.

**Dependencies:** H2-05

**Files likely touched:**

- `server/src/cloud/db.ts`
- `server/src/cloud/migrations.ts`
- `server/src/cloud/repositories.ts`
- `server/test/cloud-migrations.ts`

**Estimated scope:** M

## H2-07: Implement OIDC Session Exchange

**Description:** Use a maintained OIDC client library for authorization code +
PKCE, verify tokens at the server boundary, issue short-lived product sessions,
rotate refresh-token families, and bind sessions to devices.

**Acceptance criteria:**

- [ ] Exact redirect allowlist, state/CSRF ownership, issuer, audience,
      signature, nonce, expiry, JWKS rotation, and device proof verify.
- [ ] Refresh-family reuse revokes the family; revoked device, forged identity,
      stale JWKS, and insecure client storage fail closed.
- [ ] Existing local/MCP token flows remain separate and compatible.

**Verification:**

- [ ] Add `npm run check:account-auth`.
- [ ] Adversarial token/session tests pass, including strict redirect/state,
      issuer/audience, stale JWKS, refresh-family reuse, and forged identity.

**Dependencies:** H2-06

**Files likely touched:**

- `server/src/cloud/auth.ts`
- `server/src/security/auth.ts`
- `server/src/cloud/routes/account.ts`
- `server/test/account-auth.ts`

**Estimated scope:** M

## H2-08: Add Optional Account and Device UI

**Description:** Add sign-in/link, device naming/listing, session status, revoke,
and local-only continuation flows.

**Acceptance criteria:**

- [ ] Declining/signing out preserves local installations and records.
- [ ] Device revoke is clearly confirmed and reflected immediately.
- [ ] Auth errors never erase or replace local state.

**Verification:**

- [ ] Account/device UI integration tests.
- [ ] Manual offline, sign-in, sign-out, and revoke checks.

**Dependencies:** H2-07 and Expo UI authorization

**Files likely touched:**

- `app/account.tsx`
- `app/devices.tsx`
- `src/cloud/account-client.ts`
- `src/cloud/session-store.ts`
- `tests/presentation/account-device-ui.test.tsx`

**Estimated scope:** M

## Checkpoint H2-A: Optional Identity

- [ ] Local-only product still passes Horizon 1.
- [ ] Account/device lifecycle passes hostile tests.
- [ ] Revocation blocks future refresh and cloud operations.

## H2-09: Add Ciphertext Object Storage

**Description:** Add cloud-Vault metadata schemas with their real consumer.
Upload/download locally encrypted Vault objects through an S3-compatible adapter
with scoped metadata, integrity checks, retention, and quotas.

**Acceptance criteria:**

- [ ] Server stores ciphertext and metadata, never decryption keys/plaintext.
- [ ] Upload/download are workspace/device authorized and checksum verified.
- [ ] Metadata/object publication is atomic or recoverable; object keys are
      opaque and presigned requests restrict key, method, size, checksum, and expiry.
- [ ] Partial, oversized, replayed, quota-racing, orphaned, and unauthorized
      uploads fail or are cleaned deterministically.
- [ ] Bucket policy, lifecycle deletion, and orphan cleanup have integration proof.

**Verification:**

- [ ] Add `npm run check:cloud-vault-storage`.
- [ ] Object-store integration tests cover interruption and tampering.

**Dependencies:** H2-06, H2-07

**Files likely touched:**

- `server/src/cloud/object-store.ts`
- `server/src/cloud/vault.ts`
- `server/src/cloud/routes/vault.ts`
- `server/test/cloud-vault-storage.ts`

**Estimated scope:** M

## H2-10: Build Cloud Backup and Restore Flow

**Description:** Upload local checkpoints, list backups, download ciphertext,
decrypt locally, preview, approve, restore, and verify.

**Acceptance criteria:**

- [ ] Backup/restore resumes safely after network or process interruption.
- [ ] Wrong account/device/workspace cannot list or fetch metadata/object.
- [ ] Cloud restore uses the same local preview/atomic restore path as Horizon 1.
- [ ] Per-backup DEK is wrapped only for authorized account/recovery/device keys;
      server metadata cannot decrypt it.
- [ ] Clients verify device-key binding before wrapping and reject substituted
      or unexpectedly changed device keys.
- [ ] Authenticated manifests include monotonic backup generation and a
      client-verifiable latest anchor; replay of an older valid backup is visible
      and cannot silently become current.

**Verification:**

- [ ] Add `npm run check:cloud-vault`.
- [ ] Hostile key-substitution and valid-old-backup replay tests pass.
- [ ] Web/Android transfer between two test devices passes.

**Dependencies:** H2-08, H2-09

**Files likely touched:**

- `src/cloud/vault-client.ts`
- `src/cloud/backup-orchestrator.ts`
- `app/backups.tsx`
- `tests/platform/cloud-vault-flow.test.ts`

**Estimated scope:** M

## H2-11: Complete Device and Cloud Data Controls

**Description:** Add last-backup status, device revoke, recovery-key rotation,
cloud export/delete, and audit receipts.

**Acceptance criteria:**

- [ ] Revoked devices cannot upload, download, refresh, or sync.
- [ ] Rotation/rewrap policy states whether old backups remain readable and
      proves revoked devices cannot unwrap new keys.
- [ ] Lost recovery key behavior is explicit; unrecoverable backups are not
      falsely presented as restorable.
- [ ] Export/delete jobs are scoped, idempotent, and auditable.

**Verification:**

- [ ] Add `npm run check:cloud-data-controls`.
- [ ] Add key-rotation, revoke-race, export, and delete integration tests.

**Dependencies:** H2-10

**Files likely touched:**

- `server/src/cloud/device-service.ts`
- `server/src/cloud/data-rights.ts`
- `src/cloud/device-client.ts`
- `tests/platform/cloud-data-controls.test.ts`

**Estimated scope:** M

## H2-12A: Add Membership, Roles, and Invitations

**Description:** Add membership/invite schemas and tables with Owner/Member/Viewer
policy, separate workspace invite tokens, expiry/revocation, and bootstrap metadata.

**Acceptance criteria:**

- [ ] Invite links are distinct from install/data-import links and contain no records.
- [ ] Operation-level permission matrix is explicit for Owner/Member/Viewer.
- [ ] Identity-bound acceptance, last-owner protection, atomic role changes, and
      monotonically increasing membership epochs are enforced.
- [ ] Invite replay, expiry, wrong identity/workspace, revoke-vs-accept races,
      and privilege escalation fail.

**Verification:**

- [ ] Add `npm run check:collaboration-membership`.
- [ ] Hostile invite/role tests pass.

**Dependencies:** H2-07

**Files likely touched:**

- `server/src/cloud/membership.ts`
- `server/src/cloud/invites.ts`
- `server/src/cloud/routes/invites.ts`
- `server/test/collaboration-membership.ts`

**Estimated scope:** M

## H2-12B: Establish Authorization, Visibility, and Revocation Policy

**Description:** Implement the server policy engine before an operation stream
can append. Cover membership/device epoch, workspace, installation, visibility,
operation kind, revision, and provider/AI/export surfaces.

**Acceptance criteria:**

- [ ] Unauthorized data is absent from query, search, AI, providers, sync,
      notifications, composition, and export.
- [ ] Revocation/role change invalidates future decisions using current epochs.
- [ ] One normalized decision and receipt contract is reused at every boundary.

**Verification:**

- [ ] Add `npm run check:collaboration-policy`.
- [ ] Cross-tenant, selected-member visibility, forged-context, and
      revocation-race tests.

**Dependencies:** H2-06, H2-12A

**Estimated scope:** L

## H2-13: Persist the Authorized Operation Stream

**Description:** Add operation-envelope/receipt/checkpoint schemas and tables.
In one transaction, authorize and append a per-workspace sequence with expected
revision, idempotency, acknowledgment, and checkpoint.

**Acceptance criteria:**

- [ ] Server sequence is monotonic and idempotency is principal/workspace scoped.
- [ ] Unauthorized, stale, malformed, replayed, or cross-installation operations fail.
- [ ] Receipt freezes actor, device, membership epoch, installation, revision,
      policy version/decision, idempotency key, and operation hash.
- [ ] Authorization and append cannot race membership or device revocation.
- [ ] Audit history survives membership/device revocation.

**Verification:**

- [ ] Add `npm run check:collaboration-stream`.
- [ ] Concurrency/restart/replay tests use real persistence.

**Dependencies:** H2-12B

**Files likely touched:**

- `server/src/cloud/operation-stream.ts`
- `server/src/cloud/routes/operations.ts`
- `server/src/cloud/repositories.ts`
- `server/test/collaboration-stream.ts`

**Estimated scope:** M

## H2-14: Implement Client Push/Pull and Kernel Apply

**Description:** Upload local outbox operations, pull authorized operations by
cursor, apply through the local installation kernel, and acknowledge durably.

**Acceptance criteria:**

- [ ] Network retry never duplicates a committed operation.
- [ ] Pulled operations pass local schema, scope, revision, and policy checks.
- [ ] Cursor advances only after durable local apply.
- [ ] Poison operations quarantine without blocking the stream.
- [ ] Partial-batch crash, acknowledgment loss, checkpoint corruption, history
      truncation, compaction, and backpressure recover deterministically.

**Verification:**

- [ ] Add two-client offline/reconnect/replay tests.
- [ ] Provider writeback remains installation-scoped.

**Dependencies:** H2-13

**Files likely touched:**

- `src/cloud/sync-client.ts`
- `src/db/sync-checkpoints.ts`
- `src/ops/operation.ts`
- `tests/platform/collaboration-sync.test.ts`

**Estimated scope:** M

## H2-15: Integrate Conflicts and Revocation

**Description:** Integrate the pre-existing policy engine with client conflict
review and prove end-to-end revocation behavior.

**Acceptance criteria:**

- [ ] Stale same-record writes create conflict receipts; no silent overwrite.
- [ ] Revoked member/device loses pulls, pushes, AI, provider, and refresh access.

**Verification:**

- [ ] Add `npm run check:collaboration`.
- [ ] Cross-tenant, visibility, stale-write, and revoke-race tests pass.

**Dependencies:** H2-12B, H2-14

**Files likely touched:**

- `server/src/cloud/authorization.ts`
- `server/src/cloud/operation-stream.ts`
- `src/chat/local-query.ts`
- `tests/platform/collaboration-adversarial.test.ts`

**Estimated scope:** M

## H2-16: Add Collaboration Product Controls

**Description:** Add invite, member list, roles, visibility, conflict review,
sync status, and revoke controls.

**Acceptance criteria:**

- [ ] Owner/member/viewer UI exposes only authorized commands.
- [ ] Conflicts require explicit review and resolution.
- [ ] Offline/reconnecting/revoked states are clear and recoverable.

**Verification:**

- [ ] Collaboration UI integration tests.
- [ ] Manual two-account/two-device workflow.

**Dependencies:** H2-15 and Expo UI authorization

**Files likely touched:**

- `app/collaboration.tsx`
- `src/presentation/collaboration/member-panel.tsx`
- `src/presentation/collaboration/conflict-panel.tsx`
- `src/cloud/collaboration-client.ts`
- `tests/presentation/collaboration-ui.test.tsx`

**Estimated scope:** M

## Checkpoint H2-B: Encrypted Cloud and Collaboration

- [ ] Cloud Vault never exposes plaintext to server storage.
- [ ] Device/member revocation is effective and tested.
- [ ] Two clients converge through authorized canonical operations.
- [ ] Conflicts are visible; no silent overwrite.

## H2-17: Define Exported Capabilities and Grants

**Description:** Add capability/grant/invocation-receipt schemas with their
authorization consumer. Let installations export typed queries, commands,
summaries, and selected-record capabilities.

**Acceptance criteria:**

- [ ] Composition never receives raw repository/database access.
- [ ] Grants are explicit, least-privilege, expiring, and revocable.
- [ ] Invocation binds installation, capability version, actor, and receipt.
- [ ] Every invocation revalidates current grant, membership/device epoch,
      visibility, capability version, and policy.

**Verification:**

- [ ] Add `npm run check:composition-contracts`.
- [ ] Cross-installation/no-grant/expired-grant tests pass.

**Dependencies:** H2-01, H2-12B

**Files likely touched:**

- `packages/shared/contracts/composition.ts`
- `src/domain/composition-grants.ts`
- `server/src/cloud/composition-authorization.ts`
- `tests/contracts/composition-contracts.test.ts`

**Estimated scope:** M

## H2-18: Build Read-Only Composition Runtime

**Description:** Invoke granted capabilities, collect typed results, degrade when
sources are unavailable, and emit invocation receipts.

**Acceptance criteria:**

- [ ] First version is read-only across source installations.
- [ ] Uninstall, grant expiry, offline source, and schema mismatch degrade safely.
- [ ] Results and receipts contain no ungranted fields.

**Verification:**

- [ ] Add read-only multi-installation composition tests.

**Dependencies:** H2-15, H2-17

**Files likely touched:**

- `src/domain/composition-runtime.ts`
- `src/domain/composition-receipts.ts`
- `tests/domain/composition-runtime.test.ts`

**Estimated scope:** M

## H2-19: Prove the Weekend Trip Planner

**Description:** Build a reference composition using three installations through
explicit grants and the read-only composition runtime.

**Acceptance criteria:**

- [ ] Planner uses typed capabilities from three installations.
- [ ] Grant revocation immediately removes the affected input.
- [ ] Temporary/saved lifecycle and export behavior are explicit.

**Verification:**

- [ ] Add `npm run check:app-compositions`.
- [ ] Manual grant/revoke/offline composition flow.

**Dependencies:** H2-18 and Expo UI authorization

**Files likely touched:**

- `tests/fixtures/compositions/weekend-trip.json`
- `app/compositions/[compositionId].tsx`
- `src/presentation/composition-surface.tsx`
- `tests/platform/weekend-trip-composition.test.tsx`

**Estimated scope:** M

## H2-20: Add Proposal-Only Composition Writes

**Description:** Add composition-proposal/approval/receipt schemas with the
owning-kernel consumer. A composition proposes; the owning app validates,
previews, approves, and persists.

**Acceptance criteria:**

- [ ] Composition cannot directly write another installation.
- [ ] Approval binds proposal hash, current source grants, capability versions,
      target installation/revision, actor, policy version, expiry, nonce, and replay state.
- [ ] Approval consumption, target operation write, and receipt persistence
      commit in one transaction.
- [ ] Rejection/revocation/replay leaves target data unchanged.

**Verification:**

- [ ] Add `npm run check:composition-writes`.
- [ ] Add proposal-write authority, replay, and rollback tests.

**Dependencies:** H2-19, H1-14

**Files likely touched:**

- `src/domain/composition-runtime.ts`
- `src/domain/composition-proposals.ts`
- `src/ops/operation.ts`
- `tests/platform/composition-proposal-write.test.ts`

**Estimated scope:** M

## H2-21: Horizon 2 Acceptance

**Description:** Verify optional identity, cloud Vault, collaboration, plugins,
compositions, and full schema authority without regressing Horizon 1 local use.

**Acceptance criteria:**

- [ ] Horizon 1 passes offline and signed out.
- [ ] Account/device/Vault/collaboration/composition flows pass across two clients.
- [ ] Deterministic longitudinal journey performs: clean cloud/local state ->
      enroll two devices -> encrypted backup -> invite second account -> offline
      edits -> reconnect/converge -> revoke member/device -> verify denial ->
      read composition -> approved write proposal -> cloud delete -> signed-out
      Horizon 1 fallback.
- [ ] Exact-SHA evidence separates focused, aggregate, cloud, device, and release status.

**Verification:**

- [ ] All Horizon 1 commands.
- [ ] `npm run check:schema-registry-full`
- [ ] `npm run check:plugin-compatibility-expanded`
- [ ] `npm run check:cloud-migrations`
- [ ] `npm run check:account-auth`
- [ ] `npm run check:cloud-vault-storage`
- [ ] `npm run check:cloud-vault`
- [ ] `npm run check:cloud-data-controls`
- [ ] `npm run check:collaboration-membership`
- [ ] `npm run check:collaboration-policy`
- [ ] `npm run check:collaboration-stream`
- [ ] `npm run check:collaboration`
- [ ] `npm run check:composition-contracts`
- [ ] `npm run check:app-compositions`
- [ ] `npm run check:composition-writes`
- [ ] Add `npm run check:utopia-connected`.
- [ ] PostgreSQL backup/restore, object-store outage/orphan cleanup, recovery-key
      loss, cloud deletion verification, load/backpressure, invite/revoke race,
      and stream truncation tests pass.
- [ ] Manual two-account/two-device web and Android proof.

**Dependencies:** H2-02, H2-04, H2-11, H2-16, H2-20

**Files likely touched:**

- `docs/evidence/utopia-h2-acceptance.md`
- `tasks/todo.md`

**Estimated scope:** S

## Horizon 2 Exit Gate

- [ ] Local-only Horizon 1 remains fully functional.
- [ ] Cloud threat model and provider choices match implementation.
- [ ] No open/partial P0/P1 identity, tenant isolation, crypto, sync, or revocation issue.
- [ ] Device and cloud evidence names the exact tested commit.
- [ ] User accepts Connected Utopia before Horizon 3 starts.

# Parallel Execution

## Horizon 1 Waves

```text
Wave 0: H1-00
Wave 1: H1-01 -> H1-02 -> H1-03 -> H1-04
Wave 2 parallel:
  installer: H1-05A -> H1-05B -> H1-06 -> H1-07A -> H1-07B
  migration: H1-09 -> H1-10 -> H1-11
  vault crypto: H1-15A -> H1-15B
  registry distribution: H1-05B -> H1-18
Wave 3 after H1-07B:
  core isolation/UI: H1-07C -> H1-08
  source authority: H1-12A
Wave 4:
  authoring UI/AI: H1-12B and H1-13 -> H1-14
  vault persistence/UI: H1-16 -> H1-17
  registry UI: H1-19
Wave 5: H1-20
```

Rules:

- Registry owner alone edits `registry.v1.json`, schema scripts, and `package.json`.
- Installer and registry UI owners coordinate edits to `app/install.tsx`.
- Migration owner lands before control-room activation integration.
- Vault crypto owner and Vault persistence owner use a frozen adapter contract.
- Independent verifier runs H1-20 from the integration commit.

## Horizon 2 Waves

```text
Wave 0: H2-00
Wave 1: H2-01 schema inventory
Wave 2 parallel:
  plugins: H2-03 -> H2-04
  identity: H2-05 -> H2-06 -> H2-07 -> H2-08
Wave 3 parallel after identity:
  cloud Vault: H2-09 -> H2-10 -> H2-11
  collaboration: H2-12A -> H2-12B -> H2-13 -> H2-14 -> H2-15 -> H2-16
  composition contracts: H2-12B -> H2-17
Wave 4 after H2-15 and H2-17:
  composition runtime: H2-18 -> H2-19 -> H2-20
Wave 5 after all verticals:
  final schema convergence: H2-02
  acceptance: H2-21
```

Rules:

- Cloud database migration owner is singular.
- Auth, membership, and operation-stream agents use frozen contracts before parallel work.
- Security-sensitive tests are independently reviewed.
- No collaboration UI can compensate for missing server authorization.
- Independent verifier runs H2-21 from the exact integration commit.

# Stop Conditions

- Stop product implementation without broader-scope authorization.
- Stop UI work without Expo UI authorization.
- Stop schema work if an existing wire ID changes without compatibility proof.
- Stop install on resolver, checksum, compatibility, approval, or snapshot mismatch.
- Stop migration on journal drift, invariant failure, or missing rollback path.
- Stop Vault work if plaintext/key material can reach logs, storage, or telemetry.
- Stop Horizon 2 cloud code until H2-00 is approved.
- Stop sync if identity, membership, workspace, installation, revision, or cursor is ambiguous.
- Stop composition calls without explicit current grants.
- Stop completion claims if only focused tests ran.

# Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Dirty worktree mixes ownership | High | H1-00 isolated baseline; no reset/clean |
| Schema registry becomes a rewrite | High | AppPackage first; each later schema lands with its consumer |
| UI uses default installation | High | Route-bound runtime and two-install tests |
| Migration leaves half-mutated data | Critical | Durable journal plus one transaction and fault injection |
| Vault crypto differs by platform | Critical | Frozen adapter contract and cross-platform hostile tests |
| Cloud weakens local-first behavior | High | Horizon 1 offline gate reruns in H2 acceptance |
| Caller-controlled identity | Critical | Verified OIDC/session/device boundary |
| Cross-tenant data leak | Critical | Scoped repositories and adversarial tests |
| Sync silently overwrites | Critical | Expected revisions and conflict receipts |
| Composition bypasses app authority | High | Typed grants and proposal-only writes |
| Parallel agents collide | Medium | Disjoint file ownership and ordered integration |

# Effort Envelope

- Horizon 1: approximately 700k–1.3M total model tokens.
- Horizon 2: approximately 1.5M–2.5M additional model tokens.
- Parallel agents reduce wall time, not total token use.
- External provider setup, security review, device availability, and human
  product/legal decisions are not solved by token budget.

# Parked Horizon 3

Retained, not active:

- Full public marketplace and publisher portal.
- Signing/key-rotation/update service.
- Commercial plans, billing, moderation, and support control plane.
- 20–30 curated reference apps and 100–500 generated conformance packages.
- Production-scale performance, supply-chain, and operational hardening.
