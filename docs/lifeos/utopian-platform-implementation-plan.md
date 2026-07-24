# Wonder Utopian Platform — Detailed Convergence and Execution Plan

**Status:** Final implementation specification  
**Baseline branch:** `codex/lifeos-e2e-implementation`  
**Baseline commit:** `a3fc52e`  
**Scope:** Web and Android debug application  
**Execution strategy:** Inventory contracts, delete custom infrastructure in an isolated ORCH worktree, adopt the official library, verify, then merge  
**Maximum parallel implementation lanes:** Four  
**Only execution control plane:** ORCH  
**Only worker adapter:** `codex`  
**Only worker model:** `gpt-5.3-codex-spark`  
**Canonical local authority:** Expo SQLite  
**Primary product proof:** WonderFood  
**Explicitly deferred:** Store signing, physical-device certification, iOS release, hosted multiplayer sync, general renderer, plugin marketplace, paired-device MCP, arbitrary downloaded code

---

# 0. ORCH and Spark execution contract

All implementation work must exist as a task under `.orchestry/tasks` and be
launched by ORCH. No implementation worker may be started directly through
Codex, shell loops, or another agent framework.

Required policy:

```yaml
defaults:
  agent:
    adapter: codex
    model: gpt-5.3-codex-spark
scheduling:
  max_concurrent_agents: 4
```

Hard rules:

- Every enabled agent uses `codex` and `gpt-5.3-codex-spark`.
- Model fallback is forbidden. Spark unavailable means blocked, not fallback.
- Agents may not invent tasks outside this plan's dependency graph.
- Every task references its plan ID and exact section.
- Every task declares owned paths, forbidden paths, dependencies, checks,
  rollback, report path, and stop conditions.
- Four agents is a ceiling. Overlapping write scopes run serially.
- Every report records ORCH task/run/agent IDs, confirmed model, baseline and
  result commits, changed files, checks, evidence class, remaining risk, and
  deletion unlocked.
- Queue reset, policy enforcement, monitoring, approval/rejection, and retry
  are control-plane maintenance and may be performed directly with `orch`.

Delete-first applies to replaceable infrastructure, not product semantics:

| Delete custom implementation | Adopt |
|---|---|
| MCP JSON-RPC, protocol negotiation, SSE, transport validation | Official [MCP TypeScript SDK](https://github.com/modelcontextprotocol/typescript-sdk) v1.29 |
| Fake agent roles, static planner, regex executor | Official [Vercel AI SDK](https://github.com/vercel/ai) `ToolLoopAgent` |
| Manual chat stream parser and tool continuation plumbing | AI SDK `useChat` and `DefaultChatTransport` |
| Handwritten workflow lifecycle and duplicate server runner | Official [XState](https://github.com/statelyai/xstate) |
| Handwritten JSON-Schema validation | Ajv |
| Ad-hoc record/package diff implementation | `fast-json-patch` |
| Ad-hoc rule predicate evaluator | `json-logic-js` |

Deletion happens first inside the task worktree. The same task immediately
adopts the library and restores required behavior before it can merge. No
compatibility shim, dual runtime, or old/new production switch survives the
task.

The following are product contracts, not replaceable infrastructure, and must
not be deleted: `Operation`, `planOperation`, `applyOperation`, QuerySpec,
approval receipts, provenance, Undo, provider authority/readback rules,
AppPackage activation/rollback, capability policy, and canonical SQLite data.

The 15-minute monitor runs:

```bash
orch status --json
orch task list
orch logs --since 20m
```

It reports transitions, Spark run identity, checks, stalls, failures, dependency
blocks, and ready work left idle. It must not invent work, bypass dependencies,
or switch models.

# 1. Executive objective

Wonder is an AI-operable, local-first application platform governed by one rule:

> **Data defines the app. AI changes that definition safely. One kernel controls every mutation.**

This convergence program must turn the repository from several partially overlapping runtimes into one coherent system:

```text
User, AI, rule, workflow, provider, or MCP
                    ↓
              Typed proposal
                    ↓
         Validation, policy, preview
                    ↓
          Durable user authorization
                    ↓
               Operation
                    ↓
             planOperation()
                    ↓
             applyOperation()
                    ↓
       Canonical SQLite transaction
                    ↓
       Receipt, inverse, and outbox work
```

The program is primarily a **replacement-and-deletion campaign**. It is not permission to create a cleaner second platform beside the existing one.

Every new module must satisfy at least one of these conditions:

1. It directly replaces an existing production implementation.
2. It creates the smallest bridge required to connect two existing components.
3. It establishes a contract needed to delete duplicate ownership.
4. It provides executable proof that an old implementation can be removed.

No task may add a new framework, runtime, persistence system, queue, renderer, or DSL unless the plan explicitly authorizes it.

---

# 2. Scope definition

## 2.1 In scope

This program must deliver:

- one logical canonical local operation history;
- Expo SQLite as the only canonical record authority in local-only mode;
- one mutation contract;
- one operation planner;
- one operation commit boundary;
- one durable approval model;
- one durable deferred-work mechanism based on existing `outbox_events`;
- local SQLite queries requested by server AI without copying canonical records to the server;
- AI-generated operation proposals;
- user preview, approval, application, receipt, and Undo;
- rule and workflow processing from committed operation events;
- provider effects with write and readback verification;
- official MCP transport with proposal-only mutation behavior;
- persistent AppPackage activation and rollback;
- one package-driven Food feature;
- safe AI package modification for that feature;
- two minimal non-Food fixture packages;
- removal of superseded JSON runtimes, fake agents, and custom protocol infrastructure;
- four consolidated product-level gates.

## 2.2 Explicitly out of scope

The following must not enter the implementation queue:

- Google Play or Apple App Store submission;
- release signing keys;
- physical-phone certification;
- iOS release;
- hosted canonical record storage;
- multi-user collaboration;
- CRDTs;
- multiplayer conflict resolution;
- paired-device MCP;
- offline remote phone querying;
- general generated screen renderer;
- additional polished vertical applications;
- plugin marketplace;
- arbitrary JavaScript, React, native code, or WebAssembly packages;
- Drizzle migration;
- TinyBase, PowerSync, Evolu, Automerge, RxDB, or another canonical store;
- Mastra, LangGraph, OpenAI Agents SDK, n8n, Dify, or a second agent runtime;
- MCP v2 prerelease features;
- a new `durable_jobs` table;
- a second package registry;
- a second workflow queue;
- a second query language.

Any discovered requirement from this list must be documented as a future program, not implemented opportunistically.

---

# 3. Architectural laws

These laws take precedence over local convenience.

## 3.1 Canonical authority law

> In local-only mode, Expo SQLite is the sole canonical authority for records, operations, packages, approvals, workflow snapshots, deferred work, and local receipts.

The server may hold:

- AI request state;
- OAuth credentials;
- provider transport state;
- transient streamed responses;
- MCP transport state;
- short-lived correlation IDs.

The server may not own a second authoritative copy of:

- canonical user records;
- canonical operation history;
- approval decisions;
- authoritative package activation state;
- authoritative workflow state;
- authoritative Undo state.

## 3.2 Mutation law

> `Operation`, `planOperation()`, and `applyOperation()` are the only canonical mutation language and write boundary.

All mutation sources must converge on the same path:

| Source | Permitted behavior |
|---|---|
| Human UI | Create typed `Operation`; call local commit boundary |
| AI | Create `OperationProposal`; never commit directly |
| Rule | Create `OperationProposal`; never commit directly |
| Workflow | Create `OperationProposal`; never mutate from an XState action |
| Provider | Return verified external effect receipt; local reconciliation uses `Operation` |
| MCP | Create proposal only |
| Import | Create import-origin `Operation` |
| Sync | Future adapter must produce the same `Operation` semantics |

No handler may directly run canonical `INSERT`, `UPDATE`, or `DELETE` statements outside the storage implementation of `applyOperation()`.

## 3.3 Package law

> `AppPackage` defines application behavior. Legacy `DomainManifest` is an import or compatibility representation, not long-term platform authority.

An active package may define:

- collections and fields;
- bounded queries;
- views;
- surface metadata;
- rules;
- workflows;
- capabilities;
- presentation metadata;
- theme tokens;
- acceptance-test identifiers.

AI may propose package changes. It may not directly modify package tables or files.

## 3.4 AI law

> AI interprets intent and proposes typed work. It never owns canonical execution semantics.

The AI runtime may:

- request bounded local data;
- propose operations;
- propose a package patch;
- explain previews and receipts;
- ask the user for a decision;
- resume after a local tool result.

It may not:

- receive raw SQLite access;
- produce arbitrary SQL;
- call `applyOperation()` on the server;
- create authorization by returning `approved: true`;
- invent capabilities;
- modify provider authority;
- mark its own proposal verified;
- persist SDK-specific messages as canonical platform state.

## 3.5 Provider law

> A provider write is not successful until the provider has been reread and the expected canonical projection has been verified.

Required sequence:

```text
Local operation accepted
→ provider effect queued
→ provider API write attempted
→ provider record reread
→ expected identity and state compared
→ provider receipt returned
→ local verification/reconciliation Operation committed
```

An HTTP `200` from the write API alone is not success.

## 3.6 Workflow law

> XState owns lifecycle. Wonder owns contracts, persistence, proposals, receipts, and mutations.

XState actions may:

- enqueue effects;
- emit proposals;
- wait for events;
- transition state.

XState actions may not mutate canonical records directly.

## 3.7 Library law

Libraries may:

- validate;
- compile;
- transport;
- stream;
- route;
- diff;
- orchestrate lifecycle;
- render existing components.

Only Wonder contracts may define:

- record identity;
- query semantics;
- mutation semantics;
- revisions;
- idempotency;
- authorization;
- package authority;
- Undo;
- provider authority;
- verification;
- canonical receipts.

---

# 4. Final ownership model

| Concern | Authoritative owner | Implementation |
|---|---|---|
| Canonical records | Expo | SQLite `records` |
| Relations | Expo | SQLite `record_relations` |
| Operation history | Expo | SQLite `operations` |
| Mutation planning | Shared pure code | `planOperation()` |
| Mutation commit | Expo | `applyOperation()` |
| Undo | Shared kernel + Expo commit | Inverse `Operation` |
| Approval receipts | Expo | New SQLite `approval_receipts` |
| Deferred work | Expo | Existing `outbox_events` |
| Workflow snapshots | Expo | Existing workflow SQLite rows |
| Installed package state | Expo | Existing AppPackage SQLite registry |
| Query semantics | Wonder | Existing bounded `QuerySpec` |
| Query execution | Expo | SQLite compiler/executor |
| Rule evaluation | Shared pure code | JSON Logic predicates |
| Workflow lifecycle | Shared pure code | XState |
| AI conversation | Server | Vercel AI SDK |
| Local AI tools | Expo | Bounded dispatcher |
| Provider credentials | Server | OAuth/token storage |
| Provider effects | Server adapter | Notion/Sheets APIs |
| Provider reconciliation | Expo | Verified receipt → `Operation` |
| MCP protocol | Server | Official MCP SDK |
| MCP mutation behavior | Wonder | Proposal-only |
| User interface | Expo | Existing Food UI |
| Package-driven proof | Expo | One Food surface using package query |

---

# 5. Dependency and version policy

## 5.1 Versions to pin during convergence

The implementation must begin with a dependency spike against exact versions.

Proposed pinned versions:

```json name=convergence-dependencies.json
{
  "ai": "7.0.37",
  "@ai-sdk/react": "4.0.40",
  "@ai-sdk/openai": "4.0.20",
  "@modelcontextprotocol/sdk": "1.29.0"
}
```

Existing approved dependencies remain pinned to their repository versions:

- Ajv;
- Zod;
- XState;
- `fast-json-patch`;
- `json-logic-js`;
- Expo;
- React Native;
- Playwright or current browser-test tooling.

## 5.2 Spike requirement

The pinned-version spike must prove:

- packages install from a clean lockfile;
- root and server typecheck;
- one server AI stream compiles;
- one Expo `useChat` transport compiles;
- client-side tool output compiles;
- one official MCP server initializes;
- Streamable HTTP compiles;
- Node and Expo compatibility are documented from actual package typings.

No later task may rely on undocumented API assumptions.

## 5.3 Forbidden API assumptions

Do not use unless proven by the pinned versions:

- deprecated `maxSteps`;
- generic `needsApproval` as durable authorization;
- SDK message objects as canonical state;
- custom SSE parser beside AI SDK transport;
- MCP v2 APIs;
- deprecated MCP SSE transport;
- `@ai-sdk/mcp`;
- a second model orchestration framework.

---

# 6. Execution policy

## 6.1 Delete-and-adopt sequence

Every library-backed replacement follows this order:

1. Define target ownership.
2. Inventory only the Wonder-owned contracts and observable behavior that must
   survive.
3. In the isolated ORCH worktree, delete the custom infrastructure.
4. Copy the documented official-library pattern.
5. Reconnect the preserved Wonder contracts directly to that library.
6. Add or update contract and integration tests.
7. Confirm no production import or compatibility shim reaches the deleted path.
8. Run the full relevant checks.
9. Merge only when replacement behavior passes.
10. Remove obsolete storage only after migration and recovery review.

The main branch must never receive a deletion-only broken commit. The task
worktree may be temporarily broken while deleting and adopting.

## 6.2 PR requirements

Each task must produce one focused PR containing:

- problem statement;
- owned files;
- unchanged invariants;
- implementation summary;
- tests added;
- tests run;
- tests not run;
- evidence artifacts;
- migration impact;
- rollback strategy;
- deletion candidates enabled by the change;
- remaining risks.

Each report must also include:

- ORCH task, run, and agent IDs;
- confirmed model `gpt-5.3-codex-spark`;
- baseline and resulting commits;
- evidence class: contract, unit, integration, Android, provider-live, or
  unavailable;
- exact plan acceptance criteria satisfied.

## 6.3 File ownership

At most one active task owns a writable file.

If two tasks need the same file:

- the earlier dependency task owns the file;
- the later task waits;
- shared edits are integrated by the orchestration owner;
- agents do not resolve architectural conflicts independently.

## 6.4 Maximum concurrency

Maximum four active lanes:

1. Kernel/contracts
2. AI/chat
3. Runtime/workflow/provider
4. MCP or focused product proof

Concurrency is a cap, not a target. Use fewer lanes when files overlap.

---

# 7. ORCH queue reset

All pre-convergence tasks under `.orchestry/tasks` must be cancelled and kept
as history. New tasks come only from this plan. Queue reset is control-plane
bootstrap and finishes before workers start.

## 7.1 Queue task schema

Each queued task must include:

```yaml name=.orchestry/task-template.yaml
id: P1-KERNEL-CONFORMANCE
title: Add canonical operation conformance suite
status: ready
priority: critical

depends_on:
  - P0-BASELINE
  - P0-DEPENDENCY-SPIKE

owns:
  - src/ops/plan.ts
  - src/ops/apply.ts
  - tests/ops/

forbidden:
  - app/
  - server/src/providers/
  - server/src/mcp/
  - src/theme.ts

invariants:
  - Operation API remains backward-compatible.
  - No new canonical persistence store.
  - applyOperation remains the local write boundary.

deliverables:
  - Shared conformance fixtures.
  - SQLite adapter conformance test.
  - Normalized receipt comparison.
  - Report under artifacts/orchestry/P1-KERNEL-CONFORMANCE.md.

checks:
  - npm run typecheck
  - npm run test -- tests/ops
  - npm run check:kernel

rollback:
  - Revert the task commit.
  - No destructive migration is permitted.

completion:
  - All acceptance criteria pass.
  - No unowned files changed.
  - Report lists remaining risks.

runtime:
  adapter: codex
  model: gpt-5.3-codex-spark
  fallback: forbidden
```

## 7.2 Forbidden task categories

The new queue must contain no task involving:

- Drizzle;
- general generated renderer;
- UI framework migration;
- paired-device MCP;
- additional vertical polish;
- plugin marketplace;
- hosted sync;
- new multi-agent framework;
- new queue table;
- release signing.
- another model or adapter;
- autonomous task creation outside this plan.

---

# 8. Phase 0 — API lock, baseline, and queue reset

**Goal:** Establish trustworthy starting evidence and freeze the contracts agents will build against.

**Estimated duration:** 1–2 focused days  
**Concurrency:** One owner for baseline and API lock; one independent dependency spike may run in parallel  
**Blocking:** All later phases

---

## Task P0-01 — Record repository baseline

### Objective

Capture the exact condition of the repository at `a3fc52e`.

### Owned files

- `docs/lifeos/convergence/baseline-a3fc52e.md`
- `artifacts/convergence/baseline/`
- no production source files

### Required data

- branch;
- full commit SHA;
- tree SHA;
- dirty status;
- Node version;
- npm version;
- Expo version;
- Java version;
- Android SDK/build-tools version;
- current database version;
- package-lock hash;
- server package-lock hash;
- current runtime JSON paths;
- current SQLite tables;
- current route list;
- current CI URL and result.

### Required commands

```bash
git rev-parse HEAD
git rev-parse HEAD^{tree}
git status --short
node --version
npm --version
npm ci --no-audit --no-fund
npm ci --prefix server --no-audit --no-fund
npm run typecheck
npm run test
npm run test:server:direct
npm run config:validate
npm run export:web
npm run export:android
```

### Deliverables

- machine-readable baseline JSON;
- human-readable baseline Markdown;
- command logs;
- list of failing checks, if any;
- list of ignored local evidence directories;
- list of current production imports into deprecated runtimes.

### Acceptance criteria

- Baseline can be reproduced from a clean checkout.
- Every result distinguishes pass, failure, skipped, or credentials unavailable.
- No historical evidence is represented as current evidence.

---

## Task P0-02 — Delete fake agent/chat plumbing and adopt pinned AI SDK

### Objective

In an isolated ORCH worktree, delete the fake agent orchestration and manual
chat-stream plumbing, adopt the pinned AI SDK, reconnect preserved Wonder
conversation/proposal contracts, and merge only after parity checks pass.

### Owned files

- `server/src/agents/**`
- `server/src/chat.ts`
- focused AI/chat route modules extracted from `server/src/index.ts`
- `src/chat/client.ts`
- AI/chat tests
- package manifests and lockfiles

The visible Expo chat screen is forbidden except for minimal transport wiring;
no design changes are allowed.

### Required proof

1. Server creates one `ToolLoopAgent`.
2. Agent streams one response.
3. Agent declares `localQuery` without a server executor.
4. Expo-compatible client compiles with `useChat`.
5. Tool output can be attached and the conversation resumed.
6. Cancellation compiles.
7. No custom SSE parser is introduced.
8. Static planner, regex executor, role registry, duplicate command processor,
   and superseded manual stream parser are absent from production.
9. No compatibility adapter preserves the old orchestration runtime.

### Example server tool

```typescript name=spikes/ai-sdk/server-agent.ts
import { ToolLoopAgent, tool } from "ai";
import { z } from "zod";

const localQuery = tool({
  description: "Request a bounded query from the user's local device.",
  inputSchema: z.object({
    from: z.string().min(1),
    limit: z.number().int().min(1).max(25)
  })
  // Intentionally no execute function.
});

export const agent = new ToolLoopAgent({
  model: "provider-model-placeholder",
  tools: { localQuery }
});
```

### Negative checks

- Attempting to add a server `execute` to `localQuery` should fail a repository guard.
- SDK approval objects must not be accepted as Wonder approval receipts.
- SDK messages must not enter canonical SQLite tables.

### Acceptance criteria

- Clean root/server typecheck.
- Expo target compiles.
- One mocked client-side tool continuation test passes.
- API usage is documented from pinned typings.

---

## Task P0-03 — Delete custom MCP protocol and adopt official SDK

### Objective

In an isolated ORCH worktree, delete the handwritten MCP JSON-RPC,
protocol-negotiation, SSE, and transport-validation implementation; adopt the
official MCP v1.29 Streamable HTTP server; reconnect only Wonder-owned
proposal/resource/policy handlers; and merge only after contract checks pass.

### Owned files

- `server/src/mcp/**`
- focused MCP route wiring extracted from `server/src/index.ts`
- MCP tests
- server package manifest and lockfile

MCP JSON record/action/workflow authority is not preserved. Required product
handlers must use existing package/query/proposal contracts; provider and
canonical local mutation semantics remain outside MCP.

### Required proof

- stateless `McpServer`;
- `StreamableHTTPServerTransport`;
- JSON response mode;
- one read-only tool;
- one resource;
- one prompt;
- bearer-auth rejection fixture;
- official client initialization and tool invocation.
- removal of `protocol-compat.ts` and custom JSON-RPC/SSE parsing;
- no old/new protocol switch or compatibility shim.

### Acceptance criteria

- No custom JSON-RPC parser in spike.
- No deprecated SSE transport.
- Inspector or official client lists capabilities.
- Mutation-like request returns proposal JSON only.

---

## Control-plane bootstrap — Reset ORCH queue

### Objective

Replace existing task definitions with this plan’s DAG.

### Deliverables

- archive of previous queue;
- new dependency graph;
- file ownership map;
- task status dashboard;
- no conflicting Drizzle/general-renderer tasks.

### Acceptance criteria

- Every task has owner files, forbidden files, dependencies, checks, and report path.
- No task starts before its dependencies are merged.
- Every enabled agent and default uses `codex` with
  `gpt-5.3-codex-spark`.
- `orch doctor` passes before the first task starts.
- Reset finishes before P0-01, P0-02, or P0-03 and is not queued behind them.

---

# 9. Phase 1 — Freeze and prove the canonical kernel

**Goal:** Establish one executable semantic authority before building AI or runtime bridges.

**Estimated duration:** 3–5 days  
**Concurrency:** Up to three lanes after Phase 0  
**Blocking:** AI local tools, approvals, runtime consolidation

---

## Task P1-A — Operation conformance fixtures

### Objective

Prove `planOperation()` and `applyOperation()` produce consistent decisions and receipts.

### Fixture categories

#### Create

- new local record;
- duplicate record ID;
- create with idempotency key replay;
- provider-backed record;
- invalid collection;
- invalid domain;
- malformed properties;
- relation validation.

#### Update

- correct revision;
- missing expected revision;
- stale revision;
- primitive property change;
- nested JSON change;
- null-as-delete semantics;
- relation addition/removal;
- source update;
- idempotent replay.

#### Archive and restore

- archive at correct revision;
- stale archive;
- archive already archived record;
- restore;
- repeated restore;
- inverse operation correctness;
- revision progression.

#### Undo

- create → delete inverse;
- update → restore prior state;
- archive → restore;
- failed operation has no inverse;
- duplicate does not create a second Undo action.

### Normalized fixture format

```json name=tests/fixtures/kernel/update-nested.json
{
  "name": "update nested pantry metadata",
  "current": {
    "id": "spinach",
    "domain": "food",
    "collection": "inventory",
    "title": "Baby spinach",
    "properties": {
      "quantity": 1,
      "storage": {
        "location": "fridge",
        "drawer": 2
      }
    },
    "revision": 3
  },
  "operation": {
    "op_id": "op-update-spinach",
    "kind": "update",
    "domain": "food",
    "collection": "inventory",
    "record_id": "spinach",
    "expected_revision": 3,
    "actor": "user",
    "origin": "manual",
    "changes": {
      "storage": {
        "location": "fridge",
        "drawer": 1
      }
    }
  },
  "expected": {
    "status": "applied",
    "revision": 4,
    "changed_fields": ["properties.storage"]
  }
}
```

### Deliverables

- shared fixture loader;
- pure planner suite;
- SQLite adapter suite;
- normalized result comparison;
- failed-seed reporting.

### Acceptance criteria

- All fixtures pass.
- Duplicate idempotency keys never generate a second effect.
- Revisions are identical in planning and committed state.
- Inverse operations restore expected canonical state.

---

## Task P1-B — Import and write-boundary guards

### Objective

Stop new cross-runtime coupling.

### Required guards

1. App code cannot import server implementation files.
2. Server code cannot import Expo database/UI implementation.
3. AI modules cannot import `applyOperation`.
4. MCP modules cannot import `applyOperation`.
5. Provider transport modules cannot mutate canonical tables.
6. UI routes cannot execute canonical SQL directly.
7. Workflow actions cannot mutate canonical records.

### Example rule

```javascript name=scripts/quality/check-convergence-boundaries.mjs
const forbiddenImports = [
  {
    source: /^app\/|^src\//,
    target: /server\/src\/(kernel|mcp|providers)/
  },
  {
    source: /^server\/src\/(ai|mcp|providers)/,
    target: /src\/(db|ops\/apply)/
  }
];
```

### Deliverables

- import graph report;
- allowlist with explicit rationale;
- CI check;
- initial violations categorized as:
  - must fix now;
  - compatibility window;
  - test-only;
  - safe shared type candidate.

### Acceptance criteria

- No new violations.
- App-facing package types no longer import server implementation paths.
- Every remaining exception has removal phase and owner.

---

## Task P1-C — Small shared-contract extraction

### Objective

Move only contracts with proven client/server consumers.

### Candidate shared contracts

- `CanonicalRecord`;
- `Operation`;
- operation result/receipt;
- QuerySpec;
- QueryResult;
- AppPackage durable type;
- OperationProposal;
- ApprovalReceipt;
- ProviderVerificationReceipt;
- workflow durable envelope.

### Repository shape

```text
packages/shared/
  contracts/
    canonical-record.ts
    operation.ts
    query.ts
    package.ts
    proposals.ts
    receipts.ts
  kernel/
    plan-operation.ts
```

### Explicit non-goals

Do not move:

- SQLite adapter;
- provider HTTP code;
- Expo components;
- server routes;
- package registry implementation;
- workflow persistence;
- Food code.

### Acceptance criteria

- Client and server compile against shared contracts.
- Shared code imports neither app nor server implementation.
- No behavior changes in this task beyond import replacement.

---

## Task P1-D — QuerySpec parity baseline

### Objective

Freeze existing QuerySpec semantics before AI begins generating queries.

### Required semantics

- supported source interpretation;
- field-path lookup;
- null versus missing;
- equality;
- comparison;
- `contains`;
- prefix;
- sorting;
- limit;
- offset;
- deterministic hash.

### Required tests

- pure evaluator fixtures;
- SQLite compiler fixtures;
- parity for all currently supported operators;
- escaping and injection rejection;
- stable ordering tie-breaker;
- nested property access;
- null/missing behavior.

### Acceptance criteria

- Every supported QuerySpec gives identical ordered record IDs in reference and SQLite execution.
- Unsupported operations fail explicitly.
- No raw SQL enters QuerySpec.

---

## Task P1-E — Post-commit operation event

### Objective

Make committed SQLite operations the only source of deferred behavior.

### Required behavior

Within or immediately after a successful transaction:

1. canonical record commit succeeds;
2. operation receipt is persisted;
3. committed-operation outbox event is inserted;
4. transaction commits;
5. worker may later claim the outbox event.

Rejected, duplicate, or dry-run operations must not emit a committed-operation event.

### Proposed payload

```json name=examples/committed-operation-event.json
{
  "schema_version": "wonder.committed-operation.v1",
  "operation_id": "op-update-spinach",
  "cause_id": "chat-message-42",
  "domain": "food",
  "collection": "inventory",
  "record_id": "spinach",
  "before_revision": 3,
  "after_revision": 4,
  "changed_fields": ["properties.storage"],
  "committed_at": "2026-07-24T18:00:00.000Z"
}
```

### Acceptance criteria

- Event exists only after successful commit.
- Event and operation record are transactionally consistent.
- Event replay does not mutate state itself.
- No server JSON observer is required to detect local operations.

---

# 10. Phase 2 — Prove server AI reading phone-local SQLite

**Goal:** Complete the most important architectural bridge without copying canonical data to the server.

**Estimated duration:** 3–6 days  
**Dependencies:** P1-B, P1-C, P1-D  
**Concurrency:** AI server and Expo dispatcher may proceed in parallel after tool contract freezes

---

## Task P2-A — Define `localQuery`

### Input requirements

```typescript name=packages/shared/contracts/ai-tools.ts
export type LocalQueryRequest = {
  schemaVersion: "wonder.local-query.v1";
  query: QuerySpec;
  purpose: string;
  requestedFields: string[];
  maxRows: number;
};
```

### Output requirements

```typescript name=packages/shared/contracts/ai-tools.ts
export type LocalQueryResult = {
  schemaVersion: "wonder.local-query-result.v1";
  queryHash: string;
  resultHash: string;
  activePackageId: string;
  activePackageVersion: string;
  rows: Array<{
    id: string;
    collection: string;
    fields: Record<string, unknown>;
  }>;
  truncated: boolean;
  executedAt: string;
};
```

### Privacy limits

- maximum 25 rows by default;
- hard maximum 100 rows;
- maximum 20 projected fields;
- no secret/provider-token fields;
- no raw source snapshots unless explicitly permitted;
- privacy-sensitive collections require local confirmation;
- no unconstrained “all records” query;
- maximum execution time;
- maximum serialized output size;
- stable result hash;
- tool call deduplication.

---

## Task P2-B — Server `ToolLoopAgent`

### Objective

Replace only the model/tool loop, not all chat behavior yet.

### Tools in this phase

- `localQuery` only.

### Required server behavior

- declare tool without server execution;
- stream the tool request to Expo;
- preserve tool call ID;
- accept resumed conversation containing tool result;
- continue model response;
- support cancellation;
- avoid persisting local rows beyond current conversation need;
- redact tool output from logs by default.

### Negative test

If a developer adds a server-side `execute` handler to `localQuery`, the boundary gate must fail.

---

## Task P2-C — Expo local tool dispatcher

### Required sequence

```text
Receive localQuery call
→ verify toolCallId not already completed
→ parse input
→ enforce collection/field/privacy budget
→ execute QuerySpec locally
→ redact result
→ hash result
→ persist minimal continuation metadata
→ add tool output
→ continue chat
```

### Example

User asks:

> “What food should I use before Monday?”

Agent requests:

```json name=examples/local-query-expiring-food.json
{
  "schemaVersion": "wonder.local-query.v1",
  "purpose": "Find food expiring before Monday for meal planning.",
  "requestedFields": [
    "title",
    "properties.expires_at",
    "properties.quantity"
  ],
  "maxRows": 10,
  "query": {
    "from": "inventory",
    "where": {
      "op": "lte",
      "field": "properties.expires_at",
      "value": "2026-07-27"
    },
    "orderBy": [
      {
        "field": "properties.expires_at",
        "direction": "asc"
      }
    ],
    "limit": 10
  }
}
```

Expo may return:

```json name=examples/local-query-expiring-food-result.json
{
  "schemaVersion": "wonder.local-query-result.v1",
  "queryHash": "sha256:...",
  "resultHash": "sha256:...",
  "activePackageId": "food",
  "activePackageVersion": "1.0.0",
  "rows": [
    {
      "id": "spinach",
      "collection": "inventory",
      "fields": {
        "title": "Baby spinach",
        "properties.expires_at": "2026-07-26",
        "properties.quantity": 1
      }
    }
  ],
  "truncated": false,
  "executedAt": "2026-07-24T18:10:00.000Z"
}
```

### Acceptance criteria

- AI answer cites returned record IDs.
- Server receives only projected bounded rows.
- Duplicate tool continuation does not execute the query twice.
- Invalid field paths fail locally.
- Offline interruption resumes safely.
- No server record copy is created.

---

## Task P2-D — Chat transport parity

### Behaviors to preserve

- current conversation UI;
- message history;
- cancellation;
- retry;
- provider lookup where still supported;
- existing visual styling;
- error presentation;
- Undo affordance where applicable.

### Acceptance criteria

- Existing chat checks pass or are replaced by equivalent user-visible tests.
- No custom stream parser remains on the new route.
- Old and new transcript rendering are visually equivalent.

---

# 11. Phase 3 — Durable proposal, approval, application, receipt, and Undo

**Goal:** Allow AI to safely propose mutations without server ownership of canonical state.

**Estimated duration:** 4–7 days  
**Dependencies:** Phase 1 kernel and Phase 2 transport  
**Blocking:** Agent deletion, MCP proposal tools, rule/workflow consolidation

---

## Task P3-A — Canonical OperationProposal

### Contract

```typescript name=packages/shared/contracts/proposals.ts
export type OperationProposal = {
  schemaVersion: "wonder.operation-proposal.v1";
  proposalId: string;
  actionId: string;
  causeId: string;
  idempotencyKey: string;
  actorIntent: string;
  operations: Operation[];
  targetVersions: Array<{
    recordId: string;
    expectedRevision: number | null;
  }>;
  package: {
    id: string;
    version: string;
    hash: string;
  };
  policy: {
    risk: "low" | "standard" | "sensitive" | "restricted";
    reviewRequired: boolean;
    reasons: string[];
  };
  previewHash: string;
  createdAt: string;
  expiresAt?: string;
};
```

### Constraints

- operations must be bounded;
- target IDs must be stable;
- idempotency identity cannot change after proposal;
- package hash must match active package;
- proposal cannot grant capabilities;
- proposal cannot contain raw SQL;
- every operation is dry-run before preview.

---

## Task P3-B — Approval receipts migration

### Only new convergence table

```sql name=src/db/migrations/approval-receipts.sql
CREATE TABLE approval_receipts (
  approval_id TEXT PRIMARY KEY,
  proposal_id TEXT NOT NULL,
  action_id TEXT NOT NULL,
  actor_id TEXT NOT NULL,
  workspace_id TEXT NOT NULL,
  idempotency_key TEXT NOT NULL,
  proposal_hash TEXT NOT NULL,
  operation_hash TEXT NOT NULL,
  package_id TEXT NOT NULL,
  package_version TEXT NOT NULL,
  package_hash TEXT NOT NULL,
  target_versions_json TEXT NOT NULL,
  decision TEXT NOT NULL CHECK(decision IN ('approved', 'denied')),
  source TEXT NOT NULL,
  created_at TEXT NOT NULL,
  expires_at TEXT,
  consumed_at TEXT,
  revoked_at TEXT
);
```

### Required invariants

- approval is immutable except consumption/revocation timestamps;
- one receipt cannot authorize a different proposal;
- denied receipts cannot execute;
- expired receipts cannot execute;
- target revision changes invalidate approval;
- package changes invalidate approval;
- consumed receipt replay returns the same operation receipt, not a new mutation.

---

## Task P3-C — Preview and approval UI

### User-facing preview must show

- what records change;
- human-readable before/after;
- reason;
- risk;
- remote-provider effect;
- whether Undo exists;
- whether provider verification is pending;
- package version involved.

### Example preview

```text
Add milk to Shopping

What changes:
• Create “Milk” in Shopping
• Quantity: 1 gallon
• Status: To buy

Why:
You asked WonderFood to add milk.

Where:
This device only

Risk:
Low

Undo:
Available
```

Do not show raw operation JSON by default.

---

## Task P3-D — Apply approved proposal

### Required sequence

```text
Load proposal
→ load approval
→ compare hashes
→ compare active package
→ compare target revisions
→ ensure approval unexpired/unrevoked
→ dry-run operations again
→ apply operations
→ persist operation receipts
→ mark approval consumed
→ return result to AI
```

### Atomicity

For a single operation, approval consumption and operation commit must not permit:

- approval consumed with no operation;
- operation applied without approval consumption;
- duplicate operation after crash.

If atomic transaction boundaries prevent combining all tables, use deterministic idempotency and recovery tests proving one eventual result.

---

## Task P3-E — Undo

Undo is another canonical operation.

Required flow:

```text
User selects Undo
→ load original operation receipt
→ validate inverse remains applicable
→ preview inverse
→ apply inverse Operation
→ persist Undo operation receipt
→ return result
```

### Acceptance scenario

```text
AI proposes “Add milk”
→ user approves
→ milk appears
→ app restarts
→ AI explains completed operation
→ user presses Undo
→ milk is removed/archived according to inverse semantics
→ second Undo attempt does not duplicate effects
```

---

# 12. Phase 4 — Replace fake agents and chat orchestration

**Goal:** Delete role theater only after the new local-query and proposal paths have parity.

**Estimated duration:** 3–6 days  
**Dependencies:** Phase 3  
**Primary success metric:** Net deletion and simpler runtime ownership

---

## Task P4-A — Behavior inventory

Before deletion, categorize every exported behavior from:

- orchestrator;
- planner;
- registry;
- domain agent;
- executor;
- command processor;
- verifier orchestration;
- client action engine;
- client Undo wrapper.

Categories:

- real retrieval logic;
- real provider logic;
- real validation;
- reusable provenance conversion;
- static theater;
- regex Food command parsing;
- duplicate mutation;
- dead code;
- test-only.

Produce a keep/delete/migrate table.

---

## Task P4-B — Move useful behavior

Examples:

| Existing behavior | New home |
|---|---|
| Provider retrieval | Focused provider read module |
| Proposal schema conversion | Shared proposal module |
| Policy checks | Shared/local policy module |
| Verification predicates | Kernel verification module |
| Trace role labels | OpenTelemetry/log spans |
| Static planner | Delete |
| Echo role execution | Delete |
| Regex Food command parser | Replace with typed AI tools |
| Direct MCP JSON writes | Delete after proposal parity |

---

## Task P4-C — Delete superseded paths

Deletion is allowed only when:

- no production imports;
- new path has equivalent tests;
- migration compatibility reviewed;
- rollback commit identified.

### Required proof

```bash
rg "runChatOrchestrator|executeAgentRole|static plan marker|regex intent marker" app src server
```

Expected production matches: zero.

---

# 13. Phase 5 — Replace custom MCP with official proposal-only MCP

**Goal:** Use official transport while preventing MCP from becoming a second canonical backend.

**Estimated duration:** 3–6 days  
**Dependencies:** Phase 3 proposal contract  
**May run beside:** Late Phase 4 if files do not overlap

---

## V1 MCP capability list

### Resources

- active package public metadata;
- package schema;
- provider status summary;
- application capability summary;
- proposal status by portable proposal ID, where server-honest.

### Tools

- inspect package metadata;
- propose operations;
- preview package patch;
- inspect provider connection status.

### Prompts

Optional safe prompts such as:

- propose a Food change;
- explain package capabilities;
- draft a provider-safe request.

### Explicitly unavailable

- direct phone data query;
- direct canonical record mutation;
- approval;
- workflow-state mutation;
- remote Undo;
- server canonical record ownership.

---

## Example mutation response

MCP request:

```json name=examples/mcp-add-milk-request.json
{
  "tool": "wonder.propose_operations",
  "arguments": {
    "intent": "Add milk to my shopping list"
  }
}
```

MCP response:

```json name=examples/mcp-add-milk-response.json
{
  "status": "proposal_created",
  "proposal": {
    "schemaVersion": "wonder.operation-proposal.v1",
    "proposalId": "proposal-add-milk",
    "requiresDeviceApproval": true
  },
  "message": "Open WonderFood to review and apply this proposal."
}
```

No record may be returned claiming it was created.

---

# 14. Phase 6 — Consolidate rules, workflows, provider effects, and retries

**Goal:** Make existing SQLite `outbox_events` the only durable deferred-work mechanism.

**Estimated duration:** 7–12 days  
**Dependencies:** Committed-operation events and proposal flow  
**Highest-risk phase**

---

## Task P6-A — Extend existing outbox kinds

Proposed kinds:

```text
operation_committed
rule_evaluate
workflow_start
workflow_resume
workflow_effect
provider_write
provider_verify
provider_reconcile
notification
```

Do not introduce another queue table.

### Required queue semantics

- deterministic job ID;
- status;
- attempts;
- available time;
- lease owner;
- lease expiry;
- last error;
- cause ID;
- operation ID;
- payload;
- result receipt;
- created/updated timestamps.

If the current table lacks required columns, add a migration to the existing table.

---

## Task P6-B — Rule worker

### Input

Committed operation event.

### Output

- no-op receipt;
- typed proposal;
- workflow-start event;
- notification event.

### Loop controls

- causal depth;
- rule ID and operation lineage;
- deterministic idempotency key;
- per-rule cooldown;
- maximum output count;
- target revision evidence.

### Example

```text
Operation archives pantry item
→ Expiring Soon rule no longer matches
→ no dinner workflow starts
→ rule receipt records “condition false”
```

A rule must explain its outcome only when requested; normal UI should not show rule internals.

---

## Task P6-C — XState workflow convergence

### Required lifecycle

```text
idle
running
waiting_for_local_query
waiting_for_user
waiting_for_provider
paused
completed
failed
cancelled
compensating
compensated
```

### Workflow steps

For this scope:

- query;
- deterministic branch;
- AI decision request;
- propose operation;
- propose bounded batch;
- await user;
- provider effect;
- notify;
- fixed-size parallel branch.

No script step.

### Persistence

Persist:

- workflow ID;
- package version/hash;
- XState snapshot;
- input;
- output;
- cause ID;
- operation lineage;
- waiting tool/proposal IDs;
- compensation stack;
- timestamps.

### Compensation

Compensation emits inverse Operations. It does not restore database rows directly.

---

## Task P6-D — Provider service boundary

### Server receives

- provider;
- exact operation/effect identity;
- expected provider record ID;
- expected payload;
- authority information;
- idempotency key;
- OAuth subject/workspace.

### Server performs

- authorization;
- API write;
- ambiguous-result handling;
- provider reread;
- normalized comparison;
- verified receipt.

### Server returns

```json name=examples/provider-writeback-receipt.json
{
  "schemaVersion": "wonder.provider-verification.v1",
  "effectId": "effect-update-spinach",
  "idempotencyKey": "provider:...",
  "provider": "notion",
  "operation": "update_record",
  "providerRecordId": "notion-page-123",
  "writeSnapshotHash": "sha256:...",
  "readbackSnapshotHash": "sha256:...",
  "verified": true,
  "verifiedAt": "2026-07-24T20:00:00.000Z"
}
```

### Expo reconciliation

Expo validates the receipt and applies a local source/projection `Operation`.

The server must not update a server canonical record copy.

---

## Task P6-E — Remove JSON runtimes

After parity:

- migrate or drain pending reactive work;
- stop writing reactive runtime JSON;
- stop writing workflow checkpoint JSON;
- stop using server package registry as canonical package authority;
- stop using MCP JSON state for canonical records/actions;
- archive old files;
- delete readers after compatibility window.

### Acceptance criteria

- Restart at every workflow boundary succeeds.
- Duplicate outbox delivery is harmless.
- Provider timeout followed by successful readback does not duplicate writes.
- Unknown provider result triggers readback before retry.
- Rules trigger only from committed SQLite operation events.
- No JSON runtime remains in production canonical flow.

---

# 15. Phase 7 — One package-driven Food feature

**Goal:** Prove AppPackage consumption without building a general renderer.

**Feature:** `Expiring soon`

**Estimated duration:** 4–7 days  
**Dependencies:** Query parity, package authority, rule/workflow worker

---

## Package-owned elements

- surface identifier;
- query;
- projected fields;
- sort;
- empty copy;
- loading copy;
- error copy;
- action identifiers;
- rule definition;
- workflow identifier;
- theme token choices.

## Existing UI-owned elements

- Food card;
- list layout;
- typography;
- touch behavior;
- navigation;
- accessibility behavior;
- responsive styling.

## Example package excerpt

```json name=examples/food-expiring-soon-package.json
{
  "queries": {
    "food.expiring_soon": {
      "from": "inventory",
      "where": {
        "op": "lte",
        "field": "properties.expires_at",
        "value": {
          "$relativeDate": "+3d"
        }
      },
      "orderBy": [
        {
          "field": "properties.expires_at",
          "direction": "asc"
        }
      ],
      "limit": 10
    }
  },
  "views": {
    "food.expiring_soon": {
      "id": "food.expiring_soon",
      "query": "food.expiring_soon",
      "mode": "list",
      "fields": [
        "title",
        "properties.expires_at",
        "properties.quantity"
      ]
    }
  }
}
```

If relative-date values are not in current QuerySpec, either:

- resolve them before validation as package runtime input; or
- use an explicit date parameter.

Do not casually add a general expression system.

---

## Required user loop

```text
Spinach enters Expiring Soon
→ committed operation event emitted
→ rule evaluation job runs
→ dinner workflow starts
→ local query finds matching recipes
→ AI proposes one to three dinners
→ user selects dinner
→ meal-plan OperationProposal created
→ shopping batch proposal created
→ user approves where required
→ operations commit
→ provider effects run where configured
→ receipts displayed
→ one Undo action reverses the local bundle
```

### Failure scenarios

- no recipes;
- duplicate event;
- stale spinach revision;
- workflow restart;
- user cancellation;
- provider offline;
- provider readback mismatch;
- package rolled back mid-workflow;
- Undo after partial provider completion.

---

# 16. Phase 8 — Safe AI AppPackage builder

**Goal:** Allow AI to modify the proven Food feature without editing source code.

**Estimated duration:** 5–9 days  
**Dependencies:** Phase 7

---

## PackageChangeRequest

Example user request:

> “Show food expiring within five days instead of three, and call the section ‘Use this week.’”

AI output:

```json name=examples/package-change-request.json
{
  "schemaVersion": "wonder.package-change-request.v1",
  "intent": "Expand the expiry window and rename the Food surface.",
  "targetPackage": {
    "id": "food",
    "expectedVersion": "1.0.0"
  },
  "requestedChanges": [
    {
      "kind": "query_parameter",
      "queryId": "food.expiring_soon",
      "field": "expiryWindowDays",
      "value": 5
    },
    {
      "kind": "presentation_copy",
      "surfaceId": "food.expiring_soon",
      "field": "label",
      "value": "Use this week"
    }
  ]
}
```

The server or local package-authoring module converts this into an allowlisted JSON Patch proposal.

## Required package preview

- JSON Patch;
- current package hash;
- proposed package hash;
- changed paths;
- risk level;
- affected queries;
- affected rules/workflows;
- acceptance tests to run;
- visible before/after preview;
- rollback availability.

## Restricted paths

AI cannot change:

- capability grants;
- provider authority;
- plugin grants;
- executable code fields;
- arbitrary schema migration scripts;
- package publisher identity;
- approval policy;
- secret fields.

## Activation requirements

- expected active version matches;
- package validates with Ajv;
- semantic references validate;
- affected query parity passes;
- rule dry-run passes;
- workflow graph passes;
- acceptance tests pass;
- patch hash matches approval;
- activation and receipt persist atomically;
- crash produces old or new package, never partial state.

---

# 17. Phase 9 — Generality and optional plugin escape

**Goal:** Prove the platform is not Food-only without building more polished apps.

**Estimated duration:** 4–8 days  
**Dependencies:** Phase 8

## Fixture package A — Trip skeleton

Must prove:

- date fields;
- itinerary query;
- booking relation;
- budget sum;
- operation proposal;
- package activation/rollback.

No bespoke route or component.

## Fixture package B — Plant-care skeleton

Must prove:

- schedule-triggered workflow;
- due-item query;
- care completion proposal;
- reschedule;
- rollback.

No bespoke route or component.

## Generality rule

A new generic primitive may be promoted only when:

1. Food needs it; and
2. at least one fixture package needs the same semantic behavior.

Otherwise it remains package-specific or deferred.

## Optional plugin boundary

Implement only if a concrete fixture needs something declarative packages cannot express.

Plugin rules:

- server-side or explicitly sandboxed;
- capability-scoped;
- no canonical writes;
- proposal-only mutation;
- network allowlist;
- timeout;
- output-size limit;
- secret redaction;
- invocation receipt;
- revoke/uninstall.

No marketplace.

---

# 18. Phase 10 — Final deletion and consolidated proof

**Goal:** Remove the obsolete architectures and leave one production path.

**Estimated duration:** 4–8 days  
**Dependencies:** All parity gates

## Deletion candidates

- fake agent roles;
- static planner;
- regex Food command executor;
- duplicate command processor;
- custom chat stream parser;
- custom MCP protocol compatibility;
- custom JSON-RPC/SSE transport;
- MCP canonical JSON state;
- reactive runtime JSON;
- reactive receipt JSON;
- server package registry;
- server workflow checkpoint JSON;
- synthetic client action engine;
- unused client workflow runtime;
- duplicate Undo layer;
- redundant top-level completion scripts.

## Deletion checklist per subsystem

- [ ] Production imports equal zero.
- [ ] New path has parity tests.
- [ ] Data migration reviewed.
- [ ] Old persisted state handled.
- [ ] Rollback plan documented.
- [ ] Focused checks pass.
- [ ] Full relevant product gate passes.
- [ ] No documentation still recommends old path.

---

# 19. Consolidated test gates

## 19.1 `check:kernel`

Must prove:

- planner;
- SQLite application;
- revisions;
- idempotency;
- approval binding;
- proposal tampering rejection;
- verification binding;
- nested JSON;
- Undo;
- package authority;
- QuerySpec parity;
- operation-commit event.

## 19.2 `check:food-e2e`

Must use real Expo SQLite where environment permits and Maestro for user interaction.

Required path:

```text
fresh app
→ empty state
→ add pantry item
→ add meal
→ add shopping item
→ search
→ edit
→ archive
→ Undo
→ restart
→ verify persistence
→ Expiring Soon query
→ rule/workflow
→ proposal
→ approval
→ application
→ Undo
```

## 19.3 `check:package-e2e`

Required path:

```text
active Food package
→ AI change request
→ bounded patch
→ preview
→ approval
→ activation
→ generated package-driven Food feature changes
→ restart
→ rollback
→ prior behavior restored
```

## 19.4 `check:provider-e2e`

Deterministic matrix:

- write success/readback success;
- timeout/readback success;
- timeout/readback missing;
- `401`;
- `403`;
- `409`;
- `429`;
- stale readback;
- provider deletion;
- duplicate job;
- restart during delivery;
- no credentials;
- expired credentials.

Live Notion/Sheets proof is additional when credentials exist.

---

# 20. Parallel execution DAG

```text
P0 baseline/API lock/queue reset
│
├── P1-A operation conformance
├── P1-B import/write boundaries
├── P1-C shared-contract extraction
└── P1-D QuerySpec parity
         │
         ├── P2-A/P2-B server localQuery agent
         └── P2-C Expo local tool dispatcher
                  │
                  └── P3 proposal/approval/apply/Undo
                          │
                          ├── P4 fake-agent/chat deletion
                          ├── P5 official MCP
                          └── P6 rule/workflow/provider convergence
                                      │
                                      └── P7 package-driven Food
                                              │
                                              └── P8 AI package builder
                                                      │
                                                      └── P9 fixtures/plugin decision
                                                              │
                                                              └── P10 deletion/final proof
```

---

# 21. Suggested agent waves

## Wave 0

| Agent | Task | Deliverable |
|---|---|---|
| Lead | Baseline and architectural ADR | Baseline report and locked ownership |
| Spike agent | AI SDK pinned spike | Compile/run report |
| Spike agent | MCP SDK pinned spike | Official transport proof |
| Queue agent | ORCH reset | Dependency-valid task queue |

## Wave 1

| Agent | Owns |
|---|---|
| Kernel agent | Operation fixtures and conformance |
| Boundary agent | Import/write-boundary checks |
| Query agent | QuerySpec reference/SQLite parity |
| Contract agent | Minimal shared extraction and approval design |

## Wave 2

| Agent | Owns |
|---|---|
| AI server agent | ToolLoopAgent and `localQuery` tool |
| Expo bridge agent | `useChat` and local dispatcher |
| Runtime agent | Committed-operation outbox integration |
| MCP agent | Isolated official MCP adapter |

## Wave 3

| Agent | Owns |
|---|---|
| Approval agent | Receipt persistence and validation |
| Proposal UI agent | Preview and approval UX |
| Workflow agent | XState SQLite convergence |
| Provider agent | Provider effect/readback contract |

## Wave 4

| Agent | Owns |
|---|---|
| Food agent | Package-driven Expiring Soon feature |
| AI builder agent | Package patch flow |
| Deletion agent | Fake-agent/chat cleanup |
| MCP agent | Custom MCP deletion |

---

# 22. Agent reporting template

Every agent must leave:

````markdown name=artifacts/orchestry/TASK_ID.md
# Task outcome

## Status

- Complete / Partial / Blocked

## Baseline

- Branch:
- Commit:
- Dirty state:

## Changed files

- Exact paths

## Behavior changed

- User-visible:
- Internal:
- Persistence:
- Contracts:

## Acceptance criteria

- [x] Criterion
- [ ] Criterion — reason

## Checks run

- Command:
- Result:
- Evidence:

## Checks not run

- Command:
- Reason:

## Deletion enabled

- Files/modules now eligible for quarantine or removal

## Remaining risks

- Concrete risks only

## Rollback

- Exact revert or migration rollback steps

## Next dependency

- Task ID that may now start
````

---

# 23. Stop conditions

Pause the program if any occurs:

- app production code gains a new server implementation import;
- a server handler mutates canonical records;
- a second queue is added;
- a second package registry becomes authoritative;
- AI SDK objects enter canonical tables;
- a workflow directly mutates storage;
- provider success is accepted before readback;
- MCP claims a mutation completed;
- Food E2E regresses;
- an agent edits unowned files;
- current-tree evidence cannot distinguish actual execution from flags;
- feature work begins before dependency contracts merge.

---

# 24. Completion definition

This convergence scope is complete only when all are true:

1. Expo SQLite is the only canonical local record and operation authority.
2. Every mutation source uses the same proposal/approval/operation/receipt path.
3. `planOperation()` is the shared semantic authority.
4. `applyOperation()` is the local commit boundary.
5. AI reads bounded phone-local data through a client-side tool bridge.
6. AI can propose and explain mutations but cannot execute them remotely.
7. Approval is durable and hash-bound.
8. Undo is a canonical inverse Operation.
9. Rules start only from committed operation events.
10. XState is the only workflow lifecycle.
11. Existing `outbox_events` is the only durable deferred-work queue.
12. Provider success requires verified readback.
13. Official MCP replaces custom protocol code.
14. MCP remains proposal-only.
15. Active AppPackage controls the proven Food feature.
16. AI can safely patch that feature and roll it back.
17. Two fixture packages prove non-Food generality.
18. Fake agents and duplicate executors are removed.
19. Server canonical JSON state is removed from production paths.
20. The four consolidated product gates pass on the current tree.

---

# 25. First immediate tasks

Start only these tasks:

1. **P0-01:** Baseline and evidence report.
2. **P0-02:** Delete fake agent/chat plumbing and adopt pinned AI SDK.
3. **P0-03:** Delete custom MCP protocol and adopt official MCP v1.29.
4. After those merge:
   - P1-A kernel conformance;
   - P1-B boundary enforcement;
   - P1-C minimal shared extraction;
   - P1-D QuerySpec parity.

Do not start mutation tools, workflow convergence, package editing, MCP replacement, or Food package work until those dependencies pass.

The queue and Spark-only policy must already be reset and verified before these
three tasks launch.

---

# Final operating principle

> **Do not build a cleaner parallel platform. Make one existing production path authoritative, route all behavior through it, prove parity, and delete every competing path.**

Success is not measured by:

- commit count;
- new abstractions;
- number of quality scripts;
- number of supported roles;
- number of package fields;
- number of agents running concurrently.

Success is measured by whether these two flows are singular and obvious:

```text
User asks
→ AI requests bounded local data
→ Expo reads SQLite
→ AI proposes
→ user approves
→ applyOperation commits
→ receipt and Undo return
```

and:

```text
Operation commits
→ one outbox
→ rule/workflow/provider work
→ verified result
→ local receipt
```

When every major feature fits those flows and the competing runtimes are gone, Wonder has converged into one real local-first platform.
