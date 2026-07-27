# Wonder V1: Four-Vertical Convergence Plan

**Baseline:** `1b4ac49` on `codex/lifeos-e2e-implementation`  
**Objective:** finish the reusable platform, not 500 apps. The result must generate hundreds of substantial personal, family, group, and small-company apps from validated packages.

## Definition of “Utopian V1”

One app package controls:

- schemas and records;
- A2UI surfaces and navigation;
- queries, actions, rules, and workflows;
- DTCG theme tokens;
- Notion, Sheets, and Drive bindings;
- native capability declarations and permission requests.

AI may propose package changes. Every change is validated, previewed, hash-bound to approval, activated through the kernel, and rollbackable.

The platform has:

- one public UI protocol: **A2UI v0.9.1**;
- one renderer: **JSON Render React Native**;
- one canonical writer: `applyOperation`;
- one standard query representation;
- official AI, MCP, Notion, Sheets, and Drive SDK paths;
- no Wonder-specific UI DSL, second renderer, agent framework, CRDT, or plugin runtime.

## Critical A2UI Decision

A2UI is the persisted/interchange UI contract. JSON Render remains the implementation runtime.

```text
AI / AppPackage
    ↓ emits
A2UI v0.9.1 messages + data model
    ↓ official validation, state, binding
thin representation-only adapter
    ↓ creates ephemeral
JSON Render Spec
    ↓
@json-render/react-native
```

Rules:

1. Persist A2UI only. Never persist both A2UI and JSON Render Specs.
2. Remove `wonder.ui.v1`, `PackageUiComponent`, `PackagePresentationUi`, `buildSpec()`, and code-authored shell schemas.
3. The adapter contains no business, query, workflow, permission, or provider logic.
4. Catalog extensions are named installed widgets—not a new DSL.
5. Unknown components, bindings, actions, and capabilities fail closed.
6. UI actions produce typed kernel proposals; components never mutate records directly.
7. Native capabilities live in a separate package manifest because some permissions/modules are build-time concerns.
8. If `@a2ui/web_core/v0_9` cannot run under Expo/Metro, vendor the exact upstream processor/schema temporarily. Do not recreate A2UI.

## Code We Delete or Replace

| Existing area | Replacement | Required deletion |
|---|---|---|
| Custom package UI schema and shell specs | A2UI v0.9.1 | old UI contracts, `buildSpec`, `shell-ui.ts`, `surfaceConfig` |
| Manual chat streaming/client plumbing | Vercel AI SDK | superseded portions of `src/chat/client.ts` |
| Manual MCP protocol/transport | official MCP TypeScript SDK | `official-server.ts`, `protocol-compat.ts`, redundant validation/transport code |
| Raw Notion HTTP | `@notionhq/client` | request/auth/pagination plumbing |
| Raw Google HTTP | `@googleapis/sheets` and `@googleapis/drive` | request/auth/pagination plumbing |
| Custom query representation | React Query Builder `RuleGroupType` | duplicate predicate/query AST |
| Custom theme language | DTCG tokens + Style Dictionary | Wonder-only token schema/compiler |
| Completed experiments | production paths | `spikes/ai-sdk`, `spikes/mcp-sdk`, `spikes/json-render` |

Keep:

- `applyOperation`, receipts, provenance, Undo, idempotency, provider authority;
- record/provider projection and reread verification;
- Zod/Ajv validation;
- JSON Logic predicates, XState orchestration, JSON Patch evidence;
- Expo/React Native native shell.

Do not add during this convergence:

- Hono or Drizzle migrations;
- TinyBase, RxDB, PowerSync, Automerge, or another source of truth;
- LangGraph, Mastra, or another agent runtime;
- arbitrary code plugins;
- a fifth custom schema layer.

These migrations would touch the same central files without increasing the 500-app capability enough to justify the collision.

## Execution Topology

Four strong agents. Vertical 1 is the contract owner and integrator. Verticals 2–4 start after the contract-lock commit, then run in parallel to completion.

```text
V1 contract lock
      ├── V2 A2UI/UI/native
      ├── V3 AI/MCP
      └── V4 providers/sharing
V1 integrates once → current-tree acceptance
```

This avoids repeated layer-by-layer rewrites.

### Shared rules

- Each agent owns complete vertical files: replace, test, and delete legacy code in the same task.
- No agent edits another vertical’s files.
- Only V1 edits manifests, lockfiles, shared contracts, central server routing, or aggregate gates.
- V2–V4 receive dependencies through V1’s contract-lock commit.
- No contract change after lock. If unavoidable: stop all branches, make one amendment, rebase once.
- Agents commit locally and report SHA, owned diff, deletions, gates, and residual risk. No agent merges.
- No migration compatibility is required for inactive package/config state. Preserve user record data; incompatible package/config fixtures may be regenerated.
- Never log secrets. Live provider tests use the `agent-env` wrapper.

---

## Vertical 1 — AppPackage V3, Factory Contract, Dependencies, Integration

**Role:** architecture owner and final integrator  
**Starts from:** `1b4ac49`  
**Estimated agent time:** 8–14 hours including integration  
**Primary difficulty:** highest

### Owns

- `package.json`, all dependency manifests and lockfiles;
- `packages/shared/contracts/**`;
- package/query kernel and validation under `server/src/kernel/**`;
- package registries and package builder endpoints;
- central `server/src/index.ts`;
- aggregate scripts and acceptance fixtures;
- `tasks/**` and final convergence evidence.

### Forbidden

- implementing V2 presentation widgets;
- implementing V3 chat/MCP transport;
- implementing V4 provider clients.

### Work

1. Pin once:
   - `@a2ui/web_core`;
   - `@notionhq/client`;
   - `@googleapis/sheets`;
   - `@googleapis/drive`;
   - React Query Builder core package/API;
   - Style Dictionary;
   - retain existing AI SDK, MCP SDK, Zod, Ajv, JSON Render, JSON Logic, XState, JSON Patch.
2. Run minimal Expo/Metro smoke for `@a2ui/web_core/v0_9` before freezing the contract.
3. Define `AppPackage V3` using external standards:
   - identity/version;
   - record schemas;
   - A2UI surface message bundles or references;
   - React Query Builder-compatible query AST;
   - actions, rules, workflows;
   - DTCG theme tokens;
   - provider bindings;
   - native capability envelope.
4. Native capability entry must specify:
   - stable capability ID;
   - platform availability;
   - build-time native module/manifest declaration;
   - runtime permission strategy;
   - user-facing rationale;
   - action/intent/deep-link exposure;
   - denial and unavailable fallback.
5. Implement package lifecycle:
   - AI proposes RFC 6902 JSON Patch;
   - validate package and referenced catalogs;
   - show semantic preview;
   - persist durable approval bound to actor, package revision, patch hash, and target;
   - activate via kernel operation;
   - retain previous package and rollback receipt.
6. Export stable factories/interfaces for V2–V4.
7. Commit the contract lock and publish `CONTRACT_LOCK_SHA`.
8. After V2–V4: integrate each commit once, resolve only central wiring, run all acceptance gates, and produce one accepted SHA.

### Acceptance

- AppPackage V3 rejects unknown UI components, actions, queries, providers, and capabilities.
- A package patch cannot execute with stale revision, altered hash, wrong actor, or wrong target.
- Activation and rollback survive restart.
- No component/provider bypasses `applyOperation`.
- Five conformance packages validate without application-specific TSX.
- V2–V4 build against the frozen contract without editing it.

### Verification

```bash
npm run config:validate
npm run typecheck
npm run doctor
npm run check:package-builder-api
npm run export:web
npm run export:android
```

### Copy-ready launch prompt

```text
You own Wonder V1 Vertical 1: AppPackage V3 contract, dependencies, package
factory, central integration, and final acceptance. Work from commit 1b4ac49.

This task explicitly expands scope beyond the current server-chat AGENTS.md pass.
Do not implement UI widgets, MCP/chat transports, or provider clients.

Own: package/lockfiles, packages/shared/contracts/**, package/query kernel,
package registries/builder, server/src/index.ts, aggregate gates and fixtures.
Freeze a standards-based contract: A2UI v0.9.1 persisted UI, JSON Render as the
only renderer, RQB-compatible query AST, DTCG themes, typed actions/rules/
workflows/providers, and a native capability envelope. Implement validated,
previewed, hash-bound, rollbackable JSON-Patch package changes through the
canonical kernel. Pin all dependencies once. First prove @a2ui/web_core/v0_9
imports and processes a fixture under Expo/Metro.

Do not create a Wonder UI DSL, dual persisted schemas, second writer/renderer,
CRDT, plugin runtime, Hono, or Drizzle migration. Preserve record data; inactive
package/config fixtures may reset. Commit the contract lock and report
CONTRACT_LOCK_SHA, interfaces, dependencies, diff, checks, and blockers.
Then wait for V2-V4 commit SHAs, integrate each once, and run the full final gate
listed in tasks/plan.md.
```

---

## Vertical 2 — A2UI → JSON Render, Catalog, Theme, Native Capabilities

**Starts from:** `CONTRACT_LOCK_SHA`  
**Estimated agent time:** 12–22 hours  
**Primary difficulty:** highest UI/runtime risk

### Owns

- `app/**`;
- `src/presentation/**`;
- presentation catalogs and adapters;
- settings/package surfaces;
- domain package presentation config and presentation tests;
- `spikes/json-render` deletion.

### Forbidden

- manifests/lockfiles;
- shared contracts;
- `server/src/index.ts`;
- chat/MCP and provider internals;
- direct record writes.

### Work

1. Use `@a2ui/web_core/v0_9` for message validation, state, binding, and catalog semantics.
2. Build one thin A2UI-to-JSON-Render adapter.
3. Support the A2UI basic catalog:
   - text, image, icon, video, audio;
   - row, column, list, card, tabs, divider, modal;
   - button, checkbox, text field, date/time, choice, slider.
4. Add a bounded extension catalog:
   - record table/list/form;
   - post/feed/poll/link preview;
   - Kanban board;
   - chart;
   - map;
   - media capture/playback;
   - permission/status card.
5. Compose feed, poll, post, link preview, and board from basic A2UI components where possible. Add native adapters only for chart/map/media/capabilities.
6. Use package DTCG tokens; compile platform tokens through Style Dictionary. No arbitrary CSS schema.
7. Convert Home, Chat, Settings, Sources, and package screens into package-provided A2UI surfaces. Native routes remain thin hosts only.
8. Hide provenance, provider mechanics, and package internals behind settings/details.
9. Implement capability request/status flows as A2UI surfaces backed by the native capability registry.
10. Delete legacy shell/spec generation and the JSON Render spike.
11. Create five conformance packages, not five product apps:
    - CRUD + relational form;
    - feed + poll + link/media;
    - board + workflow;
    - dashboard + charts;
    - map + native permission/action.

### Acceptance

- Changing package A2UI changes every product surface without editing TSX.
- Navigation shell labels, tabs, settings, and Chat shell are package-controlled.
- AI output can be validated as A2UI before rendering.
- Unknown components/actions fail closed with a safe error surface.
- No `wonder.ui.v1`, custom UI component union, `buildSpec`, or `shell-ui.ts`.
- Five conformance packages render on web and Android.
- No route contains product-specific screen construction.

### Verification

```bash
npm run config:validate
npm run typecheck
npm run doctor
npm run export:web
npm run export:android
rg -n 'wonder\.ui\.v1|PackageUiComponent|PackagePresentationUi|buildSpec|surfaceConfig' app src packages
test ! -e src/presentation/shell-ui.ts
test ! -e spikes/json-render
```

### Copy-ready launch prompt

```text
You own Wonder V1 Vertical 2: all package-driven UI, A2UI/JSON Render bridging,
themes, catalog widgets, native capability surfaces, and legacy UI deletion.
Start only from CONTRACT_LOCK_SHA supplied by Vertical 1.

This task explicitly authorizes Expo UI edits. Own app/**, src/presentation/**,
presentation catalogs/settings surfaces/domain presentation config/tests, and
spikes/json-render deletion. Do not edit manifests, lockfiles, shared contracts,
server/src/index.ts, chat/MCP internals, provider internals, or canonical writes.

A2UI v0.9.1 is the only persisted UI contract. @json-render/react-native is the
only renderer. Build one representation-only adapter using
@a2ui/web_core/v0_9. Persist no JSON Render Specs. Implement A2UI basic catalog
plus bounded record/form/feed/poll/post/link/board/chart/map/media/permission
extensions. Prefer composition and installed libraries; do not create a plugin
runtime or another DSL. DTCG tokens drive theme. Convert Home, Chat, Settings,
Sources, and package screens to package A2UI. Routes are thin native hosts.

Delete legacy code in the same commit: custom UI schema consumers, buildSpec,
shell-ui.ts, surfaceConfig, and spikes/json-render. Build five conformance
packages listed in tasks/plan.md. Commit locally. Report SHA, owned diff,
deletions, web/Android evidence, gates, and residual limitations.
```

---

## Vertical 3 — Official AI SDK and Official MCP

**Starts from:** `CONTRACT_LOCK_SHA`  
**Estimated agent time:** 6–10 hours  
**Primary difficulty:** security and replay correctness

### Owns

- `src/chat/**`;
- `server/src/chat.ts` and chat/agent runtime;
- `server/src/mcp/**`;
- chat/MCP tests;
- `spikes/ai-sdk` and `spikes/mcp-sdk` deletion.

### Forbidden

- manifests/lockfiles;
- shared package contracts;
- `server/src/index.ts`;
- providers;
- presentation UI;
- kernel writer semantics.

### Work

1. Replace manual streaming/client transport with Vercel AI SDK.
2. Use AI SDK agents/tools only for reasoning and typed proposals.
3. Replace manual MCP JSON-RPC/session/transport with official MCP SDK.
4. Preserve:
   - trusted bearer identity;
   - tenant/domain/resource authorization;
   - body limits, origin/host checks, and loopback safety;
   - durable review receipts;
   - stable idempotency and operation hash;
   - tool/resource catalog semantics;
   - proposal-only AI/MCP writes.
5. Remove fake multi-agent or duplicated agent orchestration.
6. Export handler factories for V1 central routing; do not edit `server/src/index.ts`.
7. Delete superseded transports, protocol compatibility code, redundant validation, and AI/MCP spikes.

### Acceptance

- Official MCP initialize/session/tool/resource flows pass current protocol conformance.
- Forged identity headers, cross-tenant access, oversized bodies, altered approvals, and replay attacks fail.
- Review-required tool calls do not mutate or report execution.
- Chat streaming, cancellation, reconnect, tool results, and rollback remain deterministic.
- No custom MCP protocol version constants or hand-built JSON-RPC transport remain.
- No second write path is introduced by AI SDK tools.

### Verification

```bash
npm run typecheck
npm run phase3:check:chat-send
npm run phase3:check:chat-rollback-idempotency
npm run phase4:check:mcp
npm run doctor
rg -n 'protocol-compat|PROTOCOL_VERSION.*2026-03-11' server/src/mcp src/chat
test ! -e spikes/ai-sdk
test ! -e spikes/mcp-sdk
```

### Copy-ready launch prompt

```text
You own Wonder V1 Vertical 3: official Vercel AI SDK chat/agent runtime and
official MCP TypeScript SDK transport, including deletion of superseded custom
code. Start only from CONTRACT_LOCK_SHA.

Own src/chat/**, server chat/agent runtime, server/src/mcp/**, associated tests,
spikes/ai-sdk, and spikes/mcp-sdk. Do not edit manifests/lockfiles, shared
contracts, server/src/index.ts, providers, presentation UI, or kernel writer
semantics. Export handler factories for the integrator.

Replace manual streaming with AI SDK and manual MCP JSON-RPC/session transport
with official MCP SDK. Preserve trusted server identity, resource/domain scope,
body/origin/host protections, durable hash-bound approval, stable idempotency,
and proposal-only writes. AI/MCP tools may propose typed operations only.
Delete fake agent theater, custom protocol compatibility, redundant transport
validation, superseded client frames/retries, and both spikes after parity.

Run the current chat/MCP gates plus hostile identity/replay/body tests. Commit
locally. Report SHA, owned diff, deleted lines/files, checks, and residual risk.
```

---

## Vertical 4 — Official Providers and Simple Sharing

**Starts from:** `CONTRACT_LOCK_SHA`  
**Estimated agent time:** 7–12 hours  
**Primary difficulty:** provider authority and live verification

### Owns

- `server/src/providers/**`;
- `src/providers/**`;
- provider-specific config/fetchers/tests/scripts;
- provider live-proof artifacts with secrets excluded.

### Forbidden

- manifests/lockfiles;
- shared contracts;
- `server/src/index.ts`;
- presentation UI;
- chat/MCP;
- direct local record mutation.

### Work

1. Replace Notion raw HTTP/auth/pagination with `@notionhq/client`.
2. Replace Sheets/Drive raw HTTP/auth/pagination with narrow Google clients.
3. Preserve Wonder-specific:
   - canonical mapping/projection;
   - provider proposal authority;
   - outbox leasing and retry;
   - reread verification;
   - receipts, Undo, compensation, and provenance;
   - exact disposable-target guards.
4. Implement simple collaboration:
   - Google: app package plus resources may live in/share through a Drive folder; invite uses Drive permissions.
   - Notion: OAuth connection/page selection/access verification; users share the source page/workspace in Notion.
   - no CRDT or canonical collaboration database.
5. Export provider factories/handlers for V1 integration.
6. Remove superseded raw request/auth/pagination clients.
7. Run deterministic contract proofs, then live disposable Notion and Sheets/Drive proof via `agent-env`.

### Acceptance

- Create/update/delete/undo are provider-first and reread-verified.
- Rate limit, pagination, timeout, retry, duplicate delivery, expired lease, malformed response, and permission denial are covered.
- Local state never reports provider success before verified reread.
- Sharing grants only requested resource scope and records a non-secret audit receipt.
- Live proofs mutate only exact disposable bound targets and clean them up.
- No raw `api.notion.com` or Sheets/Drive request construction remains outside approved SDK adapters/tests.

### Verification

```bash
npm run typecheck
npm run phase5:check
npm run phase6:check
npm run doctor
rg -n 'api\.notion\.com|sheets\.googleapis\.com|www\.googleapis\.com/drive' server/src/providers src/providers
```

Live proof must use:

```bash
/Users/srinivasvaddi/.codex/skills/agent-env/scripts/run-with-agent-env.sh <proof-command>
```

### Copy-ready launch prompt

```text
You own Wonder V1 Vertical 4: official Notion, Google Sheets, and Google Drive
provider clients; verified writeback; and simple provider-native sharing.
Start only from CONTRACT_LOCK_SHA.

Own server/src/providers/**, src/providers/**, provider-specific config/fetchers/
tests/scripts, and secret-free proof artifacts. Do not edit manifests/lockfiles,
shared contracts, server/src/index.ts, presentation UI, chat/MCP, or canonical
kernel writes. Export factories/handlers for the integrator.

Use @notionhq/client, @googleapis/sheets, and @googleapis/drive for auth,
pagination, requests, and response types. Keep Wonder mapping, provider
authority, leases, retry, receipts, reread verification, Undo, compensation,
provenance, and exact disposable-target guards. Implement collaboration through
Drive permissions and Notion-native page/workspace sharing plus access checks.
Do not add CRDT or a cloud canonical database. Delete superseded raw HTTP code.

Run deterministic provider gates and fresh disposable live proofs through the
agent-env wrapper without printing secrets. Commit locally. Report SHA, owned
diff, deletions, exact non-secret proof targets/results, checks, cleanup, and
residual risk.
```

---

## Final Integrated Acceptance

One clean, pushed SHA must pass all of this. Historical branch evidence does not count.

### Existing gates

```bash
npm run config:validate
npm run typecheck
npm run doctor
npm run export:web
npm run export:android
npm run check:package-builder-api
npm run phase3:check:chat-send
npm run phase3:check:chat-rollback-idempotency
npm run phase4:check:mcp
npm run phase5:check
npm run phase6:check
```

### New acceptance evidence

- A2UI official create/update/delete/data-binding/action fixtures: PASS.
- Five package-only conformance surfaces render on web and Android: PASS.
- Package propose/preview/approve/activate/restart/rollback: PASS.
- Unknown UI/query/action/provider/capability rejection: PASS.
- Canonical writer and direct-write audit: PASS.
- Native capability declaration/request/deny/unavailable checks: PASS.
- Current-tree live Notion, Sheets, and Drive sharing proofs: PASS.
- Legacy absence checks and tracked spike deletion: PASS.
- Clean worktree and independent read-only review: PASS.

### Stop conditions

Do not call V1 complete if:

- A2UI is replaced with a home-grown lookalike;
- both A2UI and JSON Render Specs are persisted;
- a widget, AI tool, MCP tool, or provider writes around the kernel;
- package approval is a boolean rather than a durable hash-bound receipt;
- any conformance app needs product-specific TSX;
- Android/web export or live provider evidence comes from a different SHA;
- tests are weakened or legacy code is merely hidden rather than deleted.

## Effort and Result

Expected elapsed agent time with four strong roles: **18–30 hours**, assuming clean dependency compatibility and no provider credential block. This is not a one-hour migration; the A2UI adapter, package contract, hostile MCP tests, and provider reread proofs are the hard work.

Expected reduction:

- roughly **4k–7k production TypeScript lines** replaced/deleted;
- roughly **16k tracked spike/lockfile lines** removed;
- some unavoidable adapter/catalog code added;
- likely net production reduction: **2k–5k lines**, with much larger conceptual reduction.

After all four verticals pass on one SHA, call it **Utopian Platform V1**:

- yes for hundreds of config-driven CRUD, collaboration, content, workflow, dashboard, mapping, media, and native-capability apps;
- no for arbitrary games/3D/GPU tools, advanced media editors, custom native modules absent from the shell, enterprise offline CRDT, or unrestricted third-party code plugins.

That boundary is intentional. V1 is a powerful app factory, not a universal operating system.
