# Utopia Milestone Implementation Plan

Status: proposed  
Scope: platform milestones, not app-by-app product work  
Primary goal: make Wonder/Utopia a personal software runtime where useful apps are portable JSON artifacts, installable by link, rendered by JSON Render, mutated only through the kernel, and later editable by AI.

## Product definition

Utopia is a local-first personal software platform for individuals, families, groups, and small companies.

The platform should let one person create and distribute many useful private apps without writing app-specific runtime code.

Core rule:

> AI proposes. The compiler defines meaning. The runtime validates. JSON Render displays. The kernel mutates data. Plugins extend capability. Users approve risky changes.

## Non-goals for V1

- Enterprise marketplace.
- Billing.
- CRDT multiplayer.
- Arbitrary package JavaScript.
- Package-supplied SQL.
- Silent install from unknown links.
- Native dependency changes at runtime.
- Full commercial package signing.
- Building 500 apps now.

V1 must prove the platform can support 500 apps later.

## Milestone sequence

```text
0. Arbitrary compiled package proof
0.5 Link install and registry launcher
1. Thin package compiler and preview
2. AI package authoring loop
3. Plugin escape-hatch contract
4. AppInstallation identity and isolation
5. Migration safety
6. Simple sharing and collaboration
```

Each milestone has its own acceptance gate. Do not count a milestone as done from docs or merged code alone.

---

# Milestone 0 — Arbitrary compiled package proof

## Claim earned

The runtime can load and execute an arbitrary compiled JSON app package without app-specific TypeScript.

## Required behavior

1. Load an arbitrary `AppPackage` object from JSON.
2. Validate complete V2/V3 package contracts.
3. Pass package through `RuntimeContext`.
4. Avoid package mutation during React render.
5. Render at least three package-defined screens.
6. Create/update/archive a record through existing generic operations.
7. Activate a second package version.
8. Roll back to the first version.
9. Confirm records remain readable after upgrade and rollback.
10. Confirm package ID is absent from production runtime source.

## Required gate

```bash
npm run check:platform-day1
```

## Implementation lanes

- Lane A: shared validation contract and fixture parity.
- Lane B: dynamic loader and `RuntimeContext`.
- Lane C: renderer purity and neutral reference package.
- Lane D: focused acceptance command and evidence report.

## Done when

- New package does not need `catalog.ts`.
- Package-specific words are absent from runtime renderer code.
- Validation errors are stable enough for AI/compiler use.
- Existing operation kernel remains the only write path.

---

# Milestone 0.5 — Link install and registry launcher

## Claim earned

Apps are portable artifacts. A user can install one from a link or registry without a repo checkout.

## Why this moves forward

This is the first "Play Store, but just JSON" moment.

Without it:

> Apps exist as repo config.

With it:

> Apps can be sent as links.

## First-run screen

When no app is installed, the first screen should show:

- Install from link.
- Paste package URL.
- Choose from registry.
- Open bundled demo app.

## Install link format

Deep link:

```text
wonder://install?url=https://example.com/apps/food.package.json
```

Universal link:

```text
https://wonder.app/install?url=https://example.com/apps/food.package.json
```

## Install flow

```text
Open link
↓
Fetch package JSON
↓
Validate package
↓
Resolve runtime compatibility
↓
Show install preview
↓
User approves
↓
Create installation
↓
Activate package
↓
Render app
```

No silent install. Unknown remote packages always require review.

## Install preview must show

- App name, icon, description.
- Source URL.
- Package id/version.
- Runtime compatibility.
- Screens included.
- Data collections it creates.
- Providers requested.
- Native permissions requested.
- Widgets/plugins required.
- Fallbacks for unsupported capabilities.
- Trust status.
- Install/cancel.

## Registry manifest

Minimal V1 registry:

```json
{
  "schemaVersion": "utopia.registry.v1",
  "name": "Personal App Shelf",
  "packages": [
    {
      "id": "wonder.food",
      "name": "WonderFood",
      "version": "1.0.0",
      "url": "https://raw.githubusercontent.com/me/apps/main/wonder-food.package.json",
      "checksum": "sha256:...",
      "description": "AI pantry, meals, shopping, freshness."
    }
  ]
}
```

## Registry sources

V1 can support:

- GitHub raw file.
- Any HTTPS URL returning JSON.
- Local bundled registry.

Later:

- Google Drive.
- Notion page/database.
- S3/R2 bucket.
- Signed commercial registry.

## Required gates

1. Fresh app opens install screen.
2. Paste package URL installs valid package.
3. Registry URL lists multiple packages.
4. Install preview blocks invalid package.
5. Bad URL fails safely.
6. Restart keeps installed app.
7. Installing second package does not require code changes.

---

# Milestone 1 — Thin package compiler and preview

## Claim earned

Apps can be authored as readable source folders and compiled into deterministic immutable package JSON.

## Why compiler comes before AI authoring

AI should not edit one giant compiled blob. It should edit small source files, then compiler validates and builds the artifact.

This is overhead, but it is the good kind:

- deterministic output
- better diffs
- reference checks
- checksums
- preview
- rollback safety

## Source folder shape

```text
app.json
collections/*.json
screens/*.json
queries/*.json
rules/*.json
workflows/*.json
providers/*.json
capabilities/*.json
theme/*.json
fixtures/*.json
acceptance/*.json
```

## CLI commands

```bash
utopia package validate <folder>
utopia package compile <folder> --out app.package.json
utopia package diff old.package.json new.package.json
utopia package preview app.package.json
utopia package test app.package.json
```

## Compiler responsibilities

- Resolve IDs.
- Validate references.
- Normalize ordering.
- Validate schema.
- Validate widget availability.
- Validate native capability declarations.
- Resolve plugin versions.
- Compute artifact checksum.
- Produce semantic diff.
- Produce install preview metadata.

## Compiler non-goals

- No marketplace publishing.
- No native build generation.
- No arbitrary code bundling.
- No package JavaScript.

## Done when

- Same source produces same package bytes.
- Invalid references fail before runtime.
- Diff shows user-meaningful changes.
- Install-from-link accepts compiler output.

---

# Milestone 2 — AI package authoring loop

## Claim earned

AI can safely create or change apps by editing package source, not runtime code.

## Flow

```text
User asks for app/change
↓
AI proposes source-file patch
↓
Compiler validates
↓
Preview renders
↓
Semantic diff shown
↓
User approves
↓
Package artifact created
↓
Runtime activates
↓
Receipt and rollback saved
```

## AI proposal shape

```json
{
  "schemaVersion": "utopia.authoring-change.v1",
  "baseSourceRevision": "sha256:...",
  "intent": "Add a groceries board",
  "changes": [
    {
      "op": "add",
      "path": "screens/groceries.json",
      "value": {}
    }
  ]
}
```

## Safety rules

- AI cannot activate its own proposal.
- AI cannot approve its own proposal.
- AI cannot write arbitrary JS.
- AI cannot write SQL.
- AI cannot bypass compiler.
- AI cannot bypass kernel.
- Risky changes require explicit approval.

## Done when

- AI can create a small app from prompt.
- AI can add a screen/table/widget to an existing app.
- User sees preview and diff.
- Activation is receipt-backed.
- Rollback works.

---

# Milestone 3 — Plugin escape-hatch contract

## Claim earned

The platform can support apps beyond pure JSON without letting packages become unsafe executable code.

## Plugin classes

### Runtime plugin

Already compiled into the installed shell. Can be enabled by package config.

Examples:

- map widget
- camera picker already present in shell
- chart widget
- media player
- link preview
- calendar picker

### Build plugin

Requires a new compatible native build.

Examples:

- new ARCore dependency
- new iOS entitlement
- new Android background service
- new native SDK

### Server plugin

Runs behind a trusted service boundary.

Examples:

- data connector
- scraper
- server-side OAuth connector

### Specialized runtime

Rendered by another client while sharing Utopia contracts.

Examples:

- Unity
- Quest
- visionOS
- CAD
- robotics

## Compatibility results

Compiler/runtime resolver returns:

```text
compatible
compatible_with_fallback
requires_new_build
unsupported
```

## Plugin manifest

```json
{
  "schemaVersion": "utopia.plugin.v1",
  "id": "utopia.maps",
  "version": "1.2.0",
  "runtimeTargets": ["expo", "web"],
  "provides": {
    "widgets": ["map"],
    "tools": ["maps.geocode"],
    "dataSources": [],
    "backgroundTasks": []
  },
  "permissions": ["location"],
  "packageDependencies": []
}
```

## Artifact lock

Authoring source may say:

```json
{ "id": "utopia.maps", "version": "^1.0.0" }
```

Compiled artifact must lock:

```json
{
  "id": "utopia.maps",
  "version": "1.2.0",
  "checksum": "sha256:...",
  "capabilities": ["widget:map", "tool:maps.geocode"]
}
```

## Mutation rule

Plugins never write records directly.

Plugins return:

- observations
- proposals
- canonical operations
- external evidence

Kernel remains the only record mutation authority.

## Done when

- One simple runtime plugin proof exists.
- Package can require/optionally use plugin capability.
- Unsupported capability shows declared fallback.
- Build-only capability blocks install with clear reason.

---

# Milestone 4 — AppInstallation identity and isolation

## Claim earned

Multiple apps can coexist safely on one device/account.

## Core objects

```text
Workspace
AppPackage
AppPackageVersion
AppInstallation
Record
Operation
Conversation
ProviderBinding
WorkflowRun
OutboxEvent
```

## Required scoping

Scope all of these by installation:

- records
- operations
- receipts
- conversations
- workflows
- provider settings
- outbox events
- AI context
- search
- package state

## Compatibility path

Existing global active package can remain temporarily as:

```text
default installation
deprecated compatibility path
```

But new APIs should require `appInstallationId`.

## Done when

- Two arbitrary apps can install.
- Same record ID can exist in two apps without collision.
- Chat/search/query cannot cross apps.
- Provider binding cannot leak across apps.
- Package upgrade affects only selected installation.
- Old data migrates to default installation.

---

# Milestone 5 — Migration safety

## Claim earned

Package upgrades are safe, explainable, reversible, and data-preserving.

## Flow

```text
Package diff
↓
Compatibility classification
↓
Affected record count
↓
Snapshot
↓
Dry run
↓
Invariant checks
↓
Approval if risky
↓
Transactional activation
↓
Postcondition checks
↓
Receipt
```

## Migration classes

```text
additive
compatible_rename
review_required
destructive
unsupported
```

## Rules

- No arbitrary migration JS.
- No direct database edits from package.
- Destructive changes require explicit approval.
- Rollback path must be known before activation.

## Done when

- `1.0.0 -> 1.1.0` additive migration works.
- `1.1.0 -> 2.0.0` risky migration requires approval.
- Failed migration restores previous version.
- Records remain readable after rollback.

---

# Milestone 6 — Simple sharing and collaboration

## Claim earned

Small groups can share apps and data without CRDT complexity.

## Simplest collaboration model

Use a small collaboration plane for:

- account identity
- workspace identity
- membership
- invitations
- device/session credentials
- authorization
- operation sequencing
- revocation

Do not start with CRDT. Share canonical operations.

## Sharing flow

```text
Owner installs app
↓
Owner invites member
↓
Member installs same package from link/registry
↓
Shared provider/resource access configured
↓
Operations sync through trusted service or provider-backed exchange
↓
Conflicts resolved by explicit package policy
```

## Provider sharing

For personal/small-team V1, Notion/Sheets/Drive can act as shared homes when users explicitly share those resources.

But provider sharing is not the full correctness model. It is one integration path.

## Done when

- Owner/member roles exist.
- Invite link can bootstrap package + membership.
- Private vs shared records are explicit.
- Revocation prevents future writes.
- Conflicts are visible, not silent overwrite.

---

# Cross-cutting standards

## Package metadata

Compiled package should include:

```json
{
  "minimumRuntimeVersion": "1.4.0",
  "authoringSchemaVersion": "utopia.authoring.v1",
  "compilerVersion": "1.0.0",
  "packageContractVersion": "wonder.app-package.v3",
  "artifactChecksum": "sha256:..."
}
```

## Runtime compatibility

Every install checks:

- runtime version
- package contract version
- widget availability
- plugin availability
- native permission support
- provider capability support
- required fallbacks

## Native capability envelope

Packages declare needs, not implementation details.

Examples:

- camera
- photos
- location
- contacts
- health
- share target
- deep links
- notifications
- background tasks
- microphone
- media library

Runtime maps declarations to Expo/native behavior.

Unsupported required capability blocks install. Unsupported optional capability uses fallback.

## Widget registry V1

High-leverage built-ins:

- text
- action
- metric
- record list
- form
- feed
- post
- poll
- link preview
- board
- chart
- media
- audio
- video
- map
- calendar
- timeline
- gallery
- detail page
- settings section
- permission card
- provider connection card

Prefer existing React Native/web libraries behind JSON adapters. Do not build giant custom UI frameworks.

## Trust model V1

Allow:

- local dev package
- HTTPS package with warning
- registry package with checksum

Later:

- signed package
- trusted publisher
- revocation list
- release channels

## Documentation/evidence rule

Every milestone must produce:

- acceptance command
- output artifact
- known gaps
- current SHA
- rollback notes

No “done” from old evidence.

---

# One-person company priority

If time is tight, build in this order:

1. Milestone 0.
2. Milestone 0.5.
3. Minimal compiler.
4. Minimal AI edit loop.
5. Runtime plugin contract.
6. AppInstallation.
7. Migration safety.
8. Simple sharing.

This gives the fastest path to:

> Make JSON apps, send them as links, install them, run them, improve them with AI.

That is the first real Utopia.

---

# Parallel vertical execution plan

Use verticals, not milestone-wide mega-tasks. Each vertical should deliver one user-visible platform capability across contract, runtime, tests, and evidence.

Default concurrency: 4 agents.

Default workspace model: one worktree per active vertical or sub-vertical. Agents should not share a dirty checkout.

Default merge rule:

1. Merge foundation contracts first.
2. Merge runtime behavior second.
3. Merge UI/preview third.
4. Merge acceptance/docs last.

Do not let two agents edit the same file unless the split is by named symbols and one integration owner resolves the final patch.

## Worktree model

Use separate worktrees for parallel implementation.

```text
main checkout
  integration only

../wonderfood-utopia-worktrees/
  v0-acceptance/
  v1-runtime/
  v1-renderer/
  v2-link-install/
  v2-registry/
  v3-compiler/
  v4-ai-authoring/
  v5-plugins/
  v6-installation/
  v7-migrations/
  v8-sharing/
```

Branch naming:

```text
codex/utopia-v0-acceptance
codex/utopia-v1-runtime
codex/utopia-v1-renderer
codex/utopia-v2-link-install
codex/utopia-v2-registry
codex/utopia-v3-compiler
codex/utopia-v4-ai-authoring
codex/utopia-v5-plugins
codex/utopia-v6-installation
codex/utopia-v7-migrations
codex/utopia-v8-sharing
```

Create worktrees from the same clean base SHA.

Example setup:

```bash
BASE="$(git rev-parse HEAD)"
ROOT="../wonderfood-utopia-worktrees"
mkdir -p "$ROOT"

git worktree add -b codex/utopia-v0-acceptance "$ROOT/v0-acceptance" "$BASE"
git worktree add -b codex/utopia-v1-runtime "$ROOT/v1-runtime" "$BASE"
git worktree add -b codex/utopia-v1-renderer "$ROOT/v1-renderer" "$BASE"
git worktree add -b codex/utopia-v2-link-install "$ROOT/v2-link-install" "$BASE"
git worktree add -b codex/utopia-v2-registry "$ROOT/v2-registry" "$BASE"
git worktree add -b codex/utopia-v3-compiler "$ROOT/v3-compiler" "$BASE"
git worktree add -b codex/utopia-v5-plugins "$ROOT/v5-plugins" "$BASE"
```

Only create V4/V6/V7/V8 worktrees after their blockers merge.

```bash
git worktree add -b codex/utopia-v4-ai-authoring "$ROOT/v4-ai-authoring" HEAD
git worktree add -b codex/utopia-v6-installation "$ROOT/v6-installation" HEAD
git worktree add -b codex/utopia-v7-migrations "$ROOT/v7-migrations" HEAD
git worktree add -b codex/utopia-v8-sharing "$ROOT/v8-sharing" HEAD
```

## Worktree rules

- Each agent works only inside its assigned worktree.
- Each worktree starts clean.
- Each worktree commits before handoff.
- Integration happens only in the main checkout or a dedicated integration worktree.
- Rebase each vertical onto integration branch before merge.
- Never accept a "done" report without a commit SHA and gate output.
- Delete or archive stale worktrees after merge.

## Integration branch

Use one integration branch:

```text
codex/utopia-integration
```

Merge order:

```text
v0 acceptance
v1 runtime
v1 renderer
v2 registry contract
v2 link install
v3 compiler
v5 plugin contract
v4 AI authoring
v6 installation identity
v7 migration safety
v8 sharing
```

After each merge:

```bash
npm run check:platform-day1
```

After Packet 3:

```bash
npm run check:package-compiler
```

After Packet 5:

```bash
npm run check:plugin-compatibility
```

Do not batch all merges and debug at the end. That wastes more time than it saves.

## Vertical overview

| Vertical | Goal | Can run parallel? | Merge order |
|---|---|---:|---:|
| V0 | Baseline and acceptance harness | Yes | 1 |
| V1 | Arbitrary package runtime | Yes, with V2 | 2 |
| V2 | Link install and registry launcher | After V1 contracts | 3 |
| V3 | Compiler and package source format | Yes | 4 |
| V4 | AI package authoring loop | After V3 | 5 |
| V5 | Plugin/capability contract | Yes | 4 |
| V6 | AppInstallation isolation | After V1/V2 | 6 |
| V7 | Migration safety | After V6 | 7 |
| V8 | Sharing bootstrap | After V6 | 8 |

## Wave plan

### Wave 0 — no-edit baseline

Run before coding.

Tasks:

- Confirm branch, HEAD, dirty state.
- Run current focused gates.
- Record which gates are red before work.
- Confirm package/runtime source boundaries.

Output:

```text
docs/evidence/utopia-baseline-<sha>.md
```

Acceptance:

```bash
git status --short --branch
npm run check:platform-day1
```

If the gate is already red, record it. Do not hide baseline failures.

## V0 — Acceptance harness vertical

### Goal

One command proves the current milestone against the current tree.

### Owned paths

```text
scripts/quality/**
tests/fixtures/**
tests/platform/**
docs/evidence/**
package.json
```

### Forbidden paths

```text
src/db/**
src/domain/**
src/presentation/**
server/src/**
packages/domain-config/domains/**
```

### Work

- Keep `npm run check:platform-day1` focused.
- Add fixture checks for arbitrary package ID absence.
- Add install-link/registry checks when V2 lands.
- Emit evidence tied to current SHA.

### Acceptance

```bash
npm run check:platform-day1
```

### Model fit

Mini is fine after exact expected behavior exists. Use stronger model if the harness must define new architecture.

## V1 — Arbitrary package runtime vertical

### Goal

Runtime loads unknown package JSON without `catalog.ts` entry or render-time global mutation.

### Owned paths

```text
src/domain/package-loader.ts
src/domain/runtime-context.tsx
src/domain/catalog.ts
src/db/app-package-registry.ts
packages/shared/contracts/package.ts
tests/fixtures/app-packages/reference-app/**
tests/platform/package-runtime.test.*
```

### Symbol ownership warning

`src/db/app-package-registry.ts` is high-conflict.

Split:

- validation helper owner owns shape/assertion functions only.
- runtime owner owns lookup, activation, rollback, load flow only.

### Work

- Complete V2/V3 base validation parity.
- Load arbitrary parsed package.
- Keep old globals only as deprecated compatibility.
- Route rendering through `RuntimeContext`.
- Prove create/update/archive through generic operations.
- Prove activate and rollback preserve records.

### Acceptance

```bash
npm run check:platform-day1
```

### Blocks

- V2 install flow.
- V6 installation isolation.
- V7 migration safety.

### Model fit

Use strongest available model. This is kernel-adjacent.

## V2 — Link install and registry launcher vertical

### Goal

A package URL or registry URL becomes an installable app artifact after validation and user preview.

### Owned paths

```text
app/_layout.tsx
app/**
src/domain/runtime-context.tsx
src/domain/package-install-link.ts
src/domain/package-registry.ts
src/presentation/json-render-surface.tsx
src/presentation/json-render-widgets.tsx
packages/shared/contracts/package-registry.ts
tests/fixtures/package-registries/**
tests/platform/package-install-link.test.*
```

### Forbidden paths

```text
server/src/**
src/db/migrations.ts
android/**
ios/**
```

### Work

- Add install route/deep-link parser.
- Add package URL fetch abstraction.
- Add registry manifest contract.
- Add first-run install launcher surface.
- Add install preview surface.
- Block silent install from remote links.
- Support checksum if registry provides one.
- Persist installed package through existing activation path.

### Acceptance

```bash
npm run check:platform-day1
```

Plus focused cases:

- valid package URL installs.
- registry lists packages.
- invalid package blocks with useful error.
- restart keeps installed package.

### Blocks

- V4 AI authoring distribution.
- V8 invite/bootstrap sharing.

### Model fit

Mixed. Strong model for trust/install flow. Mini okay for fixtures and parser tests.

## V3 — Thin compiler and preview vertical

### Goal

Readable package source compiles into deterministic immutable package JSON.

### Owned paths

```text
packages/app-compiler/**
packages/shared/contracts/**
scripts/package/**
tests/fixtures/package-source/**
tests/platform/package-compiler.test.*
docs/app-package-authoring/**
package.json
```

### Forbidden paths

```text
src/db/**
src/domain/runtime-context.tsx
src/presentation/**
server/src/**
android/**
ios/**
```

### Work

- Define source folder schema.
- Compile source to `AppPackage`.
- Normalize order.
- Validate references.
- Compute checksum.
- Produce semantic diff.
- Produce preview metadata consumed by V2/V4.

### Acceptance

```bash
npm run check:package-compiler
```

If adding the command is too much for first pass, make it part of:

```bash
npm run check:platform-day1
```

### Blocks

- V4 AI authoring loop.
- V5 plugin resolution lock.
- V7 migration diff quality.

### Model fit

Strong model for contract design. Mini okay for deterministic fixture expansion.

## V4 — AI package authoring vertical

### Goal

AI creates package source patches, compiler validates them, user previews and approves activation.

### Owned paths

```text
src/ai/**
src/domain/package-authoring.ts
src/presentation/json-render-widgets.tsx
packages/shared/contracts/package-authoring.ts
tests/platform/package-authoring.test.*
docs/app-package-authoring/**
```

### Forbidden paths

```text
src/db/migrations.ts
server/src/mcp/**
android/**
ios/**
```

### Work

- Define `utopia.authoring-change.v1`.
- AI proposes source-file patch only.
- Compiler validates proposal.
- Preview/diff shown before activation.
- User approval required.
- Activation creates receipt.
- Rollback remains available.

### Acceptance

```bash
npm run check:package-authoring
```

Minimum proof:

- prompt creates small app source.
- prompt adds one screen.
- invalid reference fails.
- AI cannot self-approve.

### Blocks

- Full "AI builds apps" product claim.

### Model fit

Strong model. Do not give this to mini as autonomous task.

## V5 — Plugin and capability vertical

### Goal

Packages can declare optional/required capabilities without arbitrary package code.

### Owned paths

```text
packages/shared/contracts/native-capabilities.ts
packages/shared/contracts/native-capability-kinds.ts
packages/shared/contracts/plugin.ts
src/domain/plugin-resolver.ts
src/presentation/json-render-surface.tsx
tests/fixtures/plugins/**
tests/platform/plugin-compatibility.test.*
docs/plugin-contracts/**
```

### Work

- Define runtime/build/server/specialized plugin classes.
- Resolve compatibility:
  - `compatible`
  - `compatible_with_fallback`
  - `requires_new_build`
  - `unsupported`
- Lock plugin versions/checksums in compiled artifact.
- Enforce plugin mutation rule.
- Add one runtime plugin proof.
- Add fallback rendering proof.

### Acceptance

```bash
npm run check:plugin-compatibility
```

### Can run parallel with

- V3, if artifact-lock interface is agreed first.
- V2, if install preview consumes compatibility result later.

### Model fit

Strong model for contract. Mini okay for fixtures.

## V6 — AppInstallation identity vertical

### Goal

Multiple installed apps are isolated by installation identity.

### Owned paths

```text
src/db/migrations.ts
src/db/app-package-registry.ts
src/db/records/**
src/domain/runtime-context.tsx
src/domain/app-installation.ts
server/src/**
tests/platform/app-installation.test.*
docs/adr/**
```

### High-risk warning

This is the largest conflict surface. Do not start until V1 and V2 stabilize.

### Work

- Add `AppInstallation`.
- Migrate existing global state to default installation.
- Scope records, operations, conversations, provider bindings, workflows, outbox, AI context, and package state.
- Add repository APIs that require installation ID.
- Mark unscoped APIs deprecated.

### Acceptance

```bash
npm run check:app-installation
```

Minimum proof:

- install two packages.
- same record ID can exist in both.
- query/chat/search/undo/provider cannot cross apps.

### Model fit

Strongest model only. This is not mini work.

## V7 — Migration safety vertical

### Goal

Package upgrades are dry-run checked, classified, reversible, and receipt-backed.

### Owned paths

```text
src/domain/package-migrations.ts
src/db/app-package-registry.ts
packages/shared/contracts/package-migration.ts
tests/fixtures/package-migrations/**
tests/platform/package-migration.test.*
docs/migrations/**
```

### Work

- Classify package diff.
- Count affected records.
- Dry-run migration.
- Snapshot before risky activation.
- Require approval for destructive changes.
- Restore on failure.
- Persist migration receipt.

### Acceptance

```bash
npm run check:package-migrations
```

### Blocks

- Safe AI-driven schema edits.
- Sharing upgrade confidence.

### Model fit

Strong model for behavior. Mini okay for additive fixtures.

## V8 — Simple sharing vertical

### Goal

Small groups can bootstrap shared app use without CRDT or marketplace.

### Owned paths

```text
src/domain/sharing/**
src/domain/package-install-link.ts
packages/shared/contracts/sharing.ts
src/presentation/json-render-widgets.tsx
tests/platform/sharing-bootstrap.test.*
docs/sharing/**
```

### Work

- Define owner/member/viewer roles.
- Define invite manifest.
- Invite link can include registry/package URL plus workspace metadata.
- Shared records are explicit.
- Private records remain private.
- Revocation blocks future writes.
- Conflict policy is visible.

### Acceptance

```bash
npm run check:sharing-bootstrap
```

### Model fit

Strong model for auth/trust. Mini okay for copy/fixtures.

---

# Recommended 4-agent execution packets

## Packet 1 — First Utopia proof

Run in parallel:

1. Agent A: V0 acceptance harness.
2. Agent B: V1 validation and package loader.
3. Agent C: V1 renderer purity and reference package.
4. Agent D: V1 activation/rollback operation proof.

Merge:

1. validation/fixtures
2. loader/runtime context
3. renderer/reference package
4. acceptance harness

Gate:

```bash
npm run check:platform-day1
```

## Packet 2 — Portable app artifact

Run after Packet 1.

1. Agent A: package URL parser/fetcher.
2. Agent B: registry manifest contract/fixtures.
3. Agent C: first-run install launcher and preview.
4. Agent D: install acceptance tests.

Merge:

1. registry contract
2. URL/fetch
3. install UI
4. tests/evidence

Gate:

```bash
npm run check:platform-day1
```

## Packet 3 — App factory foundation

Run after Packet 2.

1. Agent A: source folder schema.
2. Agent B: compiler normalize/checksum.
3. Agent C: semantic diff/preview metadata.
4. Agent D: compiler fixtures/gate.

Gate:

```bash
npm run check:package-compiler
```

## Packet 4 — Safe AI app editor

Run after Packet 3.

1. Agent A: authoring-change contract.
2. Agent B: AI patch proposal adapter.
3. Agent C: preview/approval UI.
4. Agent D: authoring tests.

Gate:

```bash
npm run check:package-authoring
```

## Packet 5 — Capability escape hatch

Can run after Packet 3, before or beside Packet 4 if interfaces are stable.

1. Agent A: plugin manifest contract.
2. Agent B: compatibility resolver.
3. Agent C: fallback UI behavior.
4. Agent D: plugin fixtures/gate.

Gate:

```bash
npm run check:plugin-compatibility
```

## Packet 6 — Multi-app safety

Run after Packets 1 and 2.

1. Agent A: schema/default installation migration.
2. Agent B: repository scoping.
3. Agent C: runtime context scoping.
4. Agent D: cross-app leak tests.

Gate:

```bash
npm run check:app-installation
```

## Packet 7 — Safe upgrades

Run after Packet 6.

1. Agent A: migration diff classifier.
2. Agent B: dry-run/snapshot/restore.
3. Agent C: approval/receipt flow.
4. Agent D: migration tests.

Gate:

```bash
npm run check:package-migrations
```

## Packet 8 — Simple sharing

Run after Packet 6.

1. Agent A: sharing contracts.
2. Agent B: invite manifest/link.
3. Agent C: role/visibility runtime checks.
4. Agent D: sharing tests.

Gate:

```bash
npm run check:sharing-bootstrap
```

---

# Conflict map

Avoid concurrent writes unless explicitly coordinated:

| File or area | Risk | Rule |
|---|---|---|
| `src/db/app-package-registry.ts` | Very high | split by symbols; integration owner merges |
| `src/domain/runtime-context.tsx` | High | V1/V2/V6 cannot edit same time without handoff |
| `src/presentation/json-render-widgets.tsx` | High | widget additions and install UI need sequencing |
| `packages/shared/contracts/package.ts` | High | contract owner merges changes |
| `package.json` | Medium | one command owner at a time |
| `src/db/migrations.ts` | Very high | only V6 owns before V7 |
| `docs/evidence/**` | Low | each packet writes unique SHA file |

---

# Agent prompt template

```text
You own vertical: <V# name>
Cwd: /Users/srinivasvaddi/Projects/wonderfood
Branch/base: <branch>
Goal: <one behavior>

Owned files:
<list>

Forbidden files:
<list>

Must preserve:
- package code cannot contain arbitrary JS or SQL
- kernel remains only record writer
- AI cannot self-approve
- plugins cannot write records directly
- no silent remote package install

Before editing, report:
- branch
- HEAD
- dirty files
- files inspected
- exact acceptance command

After editing, report:
- changed files
- behavior added
- tests added
- checks run
- checks not run
- risks
- commit SHA if committed

Acceptance:
<command>

Stop if:
- owned-file boundary is insufficient
- required contract from another vertical is missing
- check failure is outside your scope
- you need to edit forbidden files
```

---

# Practical fastest path

For one-person-company V1, do only Packets 1–5 first.

That gets:

- arbitrary JSON app runtime
- install by link
- registry as app shelf
- deterministic package compiler
- AI source edit loop
- capability/plugin escape hatch

Defer Packets 6–8 until the platform has at least several real packages.
