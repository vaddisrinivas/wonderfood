# Utopia Tools-First Next Plan

Status: proposed  
Goal: use existing libraries where they help, while keeping Utopia's identity, package lifecycle, kernel, receipts, and approval boundaries product-owned.

## Current reality

Already first-slice present:

- arbitrary package loading
- package compiler
- package authoring contract
- link-install/registry contracts
- plugin/capability contract

Still missing:

- `AppInstallation`
- true multi-app package state
- record/operation isolation
- secondary isolation for chat/search/provider/workflow
- migration safety
- real AI control-room UX
- sharing/vault/registry scale

Current aggregate gate must be green before foundation work starts.

---

# Phase 0 — Restore green baseline

## Goal

Make current platform gates trustworthy before adding identity work.

## Tools

- Ajv: domain/package schema validation.
- Prettier/Biome: JSON formatting only.
- Existing repo gates.

## Tasks

1. Fix invalid dirty Food JSON fields.
2. Run:

```bash
npm run config:validate
npm run check:platform-day1
npm run check:package-compiler
npm run check:package-authoring
npm run check:plugin-compatibility
```

## Acceptance

- Platform Day 1 gate passes.
- Compiler/authoring/plugin/link-install focused tests pass.
- No unrelated source changes.

---

# Phase 1 — AppInstallation foundation

## Goal

Create the real app boundary.

Claim:

> Two local app installations can exist, and package activation/rollback for one does not affect the other.

## Tools

Use now:

- Zod or Ajv for installation contract validation.
- SQLite directly, matching current repo style.
- canonical JSON/hash helper for receipts.

Avoid now:

- Drizzle/Kysely migration.
- PowerSync/Electric.
- account/collab frameworks.

## Tasks

1. Add shared contract:

```text
WorkspaceId
AppInstallationId
AppInstallation
AppInstallationStatus
InstallationPackageState
```

2. Add tables:

```text
workspaces
app_installations
app_installation_package_state
```

3. Migrate singleton state:

```text
app_package_state id='default'
→ default workspace
→ default app installation
```

4. Add installation-scoped package lifecycle:

```text
getActiveAppPackage(db, installationId)
activateAppPackage(db, installationId, candidate)
rollbackAppPackage(db, installationId)
bootstrapAppPackageRegistry(db, installationId)
```

5. Keep deprecated wrappers:

```text
getActiveAppPackage(db)
activateAppPackage(db, candidate)
```

They resolve the default installation only for compatibility.

6. Add local app library API:

```text
listAppInstallations(workspaceId)
getAppInstallation(id)
```

## Acceptance

Add:

```bash
npm run check:app-installation-foundation
```

It must prove:

- fresh DB creates default workspace
- existing singleton migrates once
- migration is idempotent
- two installations can reference same package
- activate A does not change B
- rollback A does not change B
- app library lists both installations

---

# Phase 2 — Link install creates AppInstallation

## Goal

Turn app links into real local installations, not global package replacement.

## Tools

Use now:

- existing package-install contract
- Ajv/Zod validation
- canonical checksum
- safe fetch wrapper

Borrow patterns:

- npm metadata
- VS Code extension install preview
- Obsidian plugin trust warning

Avoid:

- marketplace framework
- auto-update
- signing infrastructure

## Tasks

1. Update install flow:

```text
fetch package
→ validate
→ preview
→ approve
→ create AppInstallation
→ activate package for that installation
→ open /apps/[installationId]
```

2. Add first-run launcher:

```text
Install from link
Choose from registry
Open bundled demo
```

3. Add local registry picker from existing registry manifest.

4. Bind approval to:

```text
package URL
package id
package version
checksum
compatibility result
```

## Acceptance

Add:

```bash
npm run check:link-install
```

Must prove:

- bad URL fails before fetch
- invalid package cannot install
- checksum mismatch blocks install
- approved package creates installation
- restart keeps installed app
- second install creates second installation

---

# Phase 3 — Record and operation isolation

## Goal

Move from package isolation to data isolation.

Claim:

> Two apps can contain the same record ID and cannot mutate each other.

## Tools

Use:

- fast-json-patch for operation/evidence deltas.
- Zod/Ajv for operation envelopes.
- SQLite indexes and adversarial tests.

Avoid:

- sync libraries
- CRDTs
- ORM migration

## Tasks

1. Add `app_installation_id` to:

```text
records
relations
operations
operation receipts
undo events
outbox
provider links/snapshots where record-bound
```

2. Change uniqueness:

```text
record_id
→ (app_installation_id, record_id)
```

3. Add scoped repository:

```ts
createInstallationRepository({ db, workspaceId, installationId })
```

4. Kernel validates installation scope before mutation.

5. Deprecated unscoped paths allowed only through default installation compatibility wrapper.

## Acceptance

Add:

```bash
npm run check:app-installation-data
```

Must prove:

- same record ID exists in A and B
- update/archive/restore A does not touch B
- undo A cannot target B
- idempotency keys do not collide across installations
- forged operation installation ID fails closed

---

# Phase 4 — Chat/search/provider/workflow isolation

## Goal

Stop secondary systems from leaking data across apps.

## Tools

Use:

- Vercel AI SDK for AI transport/streaming where not already adopted.
- XState for workflow lifecycle if workflow state grows.
- MiniSearch or Fuse.js for local installation-scoped search.
- Zod/Ajv for provider/workflow envelopes.

Avoid:

- agent frameworks that own tool authority
- provider sync frameworks
- global search by default

## Tasks

1. Chat conversations include:

```text
workspaceId
installationId
packageId
packageVersion
```

2. AI retrieval receives installation repository only.

3. Search defaults to installation-local.

4. Workflow/rule runs persist installation ID.

5. Provider bindings and writeback are installation-scoped.

## Acceptance

Add:

```bash
npm run check:app-installation-secondary
```

Must prove:

- A chat cannot retrieve B records
- A search cannot find B records
- workflow in A cannot mutate B
- provider binding from A invisible to B
- AI-generated operation targets conversation installation

---

# Phase 5 — Migration safety

## Goal

Package upgrades cannot destroy app data silently.

## Tools

Use:

- fast-json-patch for diffs/previews.
- canonical hashes for receipts.
- JSON Schema diff ideas.
- SQLite transaction/snapshot.

Avoid:

- arbitrary migration JavaScript
- package SQL
- full migration DSL explosion

## Tasks

1. Package diff classifier:

```text
safe
review_required
destructive
requires_new_build
unsupported
```

2. Declarative migration operations:

```text
add field
rename field
copy field
set default
map enum
archive collection
assert invariant
```

3. Dry-run migration.
4. Affected record count.
5. Snapshot before risky activation.
6. Recovery on failure.
7. Migration receipt.

## Acceptance

Add:

```bash
npm run check:package-migrations
```

Must prove:

- additive upgrade passes
- destructive change requires approval
- failed migration restores previous app state
- restart after interrupted migration resolves deterministically

---

# Phase 6 — AI authoring UI/control room

## Goal

Give users a beautiful AI-driven place to inspect/change package source safely.

## Tools

Use/borrow:

- `react-jsonschema-form` for web package/config forms.
- `remoteoss/json-schema-form` style for headless mobile forms.
- Form.io as form-builder inspiration only.
- JSON Render for final app surfaces.

Avoid:

- replacing runtime with a form builder
- arbitrary drag/drop page builder for V1
- letting AI edit runtime code

## Tasks

1. Package source browser:

```text
App
Collections
Screens
Queries
Rules
Workflows
Providers
Theme
Capabilities
```

2. Schema-generated edit forms.
3. AI change proposal panel.
4. Compiler validation panel.
5. Semantic diff panel.
6. Preview panel.
7. Approve/activate/rollback controls.

## Acceptance

Add:

```bash
npm run check:package-control-room
```

Must prove:

- user can add collection field through schema form
- user can add screen through AI proposal
- invalid proposal fails with useful diagnostics
- approval creates receipt
- activation targets selected installation

---

# Phase 7 — Sharing, Vault, registry scale

## Goal

Add optional cloud convenience without surrendering local-first ownership.

## Tools

Evaluate later:

- PowerSync for serious SQLite sync.
- Electric for Postgres/local-first architecture.
- libsodium/age-style encryption for vault.
- GitHub releases/raw URLs for registry publishing.

Use first:

- GitHub raw URLs/releases for app package distribution.
- encrypted export/import for backup.
- operation stream for collaboration.

Avoid V1:

- CRDTs
- raw SQLite live sync
- full commercial marketplace
- billing/control plane

## Tasks

1. Registry as JSON index of install descriptors.
2. App package release via GitHub URL.
3. Encrypted local backup/export.
4. Restore preview.
5. Minimal invite descriptor:

```text
install descriptor + workspace invitation metadata
```

6. Operation-stream sync design after isolation is proven.

## Acceptance

Add staged gates:

```bash
npm run check:registry-scale
npm run check:vault
npm run check:sharing-bootstrap
```

---

# Parallel execution

Use vertical worktrees.

## Packet A — baseline

- Agent 1: fix dirty Food JSON
- Agent 2: verify platform gates
- Agent 3: inspect current link/compiler/plugin coverage
- Agent 4: prepare evidence doc

Merge only after green baseline.

## Packet B — AppInstallation foundation

- Agent 1: shared contracts and validation
- Agent 2: schema/migration
- Agent 3: package lifecycle scoping
- Agent 4: foundation gate/tests

Merge order:

```text
contracts
→ schema
→ lifecycle
→ tests/gate
```

## Packet C — link install creates installation

- Agent 1: install descriptor/approval binding
- Agent 2: installer persistence path
- Agent 3: first-run/local app library UI
- Agent 4: link-install tests

## Packet D — data isolation

- Agent 1: record schema
- Agent 2: scoped repository
- Agent 3: operation/undo scope
- Agent 4: adversarial tests

## Packet E — secondary isolation

- Agent 1: chat/search
- Agent 2: AI context
- Agent 3: workflows/rules
- Agent 4: providers/plugins

## Packet F — migration safety

- Agent 1: diff classifier
- Agent 2: dry run/snapshot
- Agent 3: approval/receipt
- Agent 4: recovery tests

## Packet G — control room

- Agent 1: schema form adapter
- Agent 2: package source browser
- Agent 3: proposal/diff/preview UI
- Agent 4: control-room tests

---

# Shortest path to real Utopia

Do these first:

1. Green baseline.
2. AppInstallation foundation.
3. Link install creates AppInstallation.
4. Record/operation isolation.
5. Package control room.

Then the platform can honestly say:

> I can install apps from JSON links, keep them separate, edit them safely with AI, and evolve them without app-specific runtime code.

