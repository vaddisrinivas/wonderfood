# Wonder Utopia — Remaining Work, Single-Shot Queue Plan

**Baseline:** `codex/lifeos-e2e-implementation` at `9c8f749`  
**Goal:** complete the convergence plan through package-driven Food, safe AI package changes, official AI/MCP runtimes, durable rules/workflows/providers, generality fixtures, and deletion of replaced code.  
**Not included:** release signing, store release, or physical-phone certification.

## 1. Current truth

- Phase 1 is accepted by `P1R3`.
- Phase 2 is rejected by `P2R2`.
- Current production typecheck fails because `server/src/agents/chat-agent.ts` imports undeclared `ai` and `@ai-sdk/openai`.
- `localQuery` has a useful bounded contract, but is not shared/Expo-safe, does not enforce row truncation correctly, and is not wired through client tool continuation.
- The new server agent deleted web-search parity prematurely.
- Phase 3–10 remain incomplete.
- Useful spikes already prove the pinned AI SDK and official MCP SDK patterns.
- Current `.gitignore` modification and untracked `AGENTS.md` are user work and must be preserved.

## 2. Execution decision

Queue the complete DAG once. Execute only the dependency-ready wave.

Use at most:

- 3 isolated implementation workers.
- 1 integration/review worker.
- One writer for manifests/lockfiles.
- One writer for migrations/outbox.
- One writer for `server/src/index.ts`.

Ten writable workers are forbidden. The remaining work has overlapping authority files; extra writers increase conflicts and false acceptance.

## 3. ORCH safety correction

Installed ORCH `1.0.28` merges successful worktree branches before review. Its `auto` approval policy can also mark failed review criteria done. `1.0.30` retains the same ordering.

Before product work:

1. Set all agents to `approval_policy: manual`.
2. Run implementation tasks in `workspace_mode: isolated`.
3. Require each implementation task to:
   - start from the recorded accepted SHA;
   - change only owned files;
   - create one commit;
   - write a machine-readable evidence report;
   - leave integration to the gate task.
4. Gate worker inspects the isolated commit, owned diff, forbidden diff, and current checks.
5. Gate worker alone cherry-picks accepted commits into the canonical branch.
6. Downstream tasks require a committed `PASS` gate artifact, not ORCH `done`.
7. A failed gate blocks dependents. No model fallback, dependency bypass, or historical evidence.

## 4. Scope prerequisite

The current `AGENTS.md` restricts this pass to server chat/runtime and forbids Expo UI and `packages/domain-shared`.

Full execution therefore requires an explicit scope amendment permitting:

- `app/**` and `src/chat/**` for P2 client transport and approval UI.
- `packages/domain-shared/**` or a new isomorphic shared-contract package for `localQuery`, proposal, and receipt contracts.
- Food UI/package paths for P7.

Until amended, only P2 server repair may execute.

## 5. Reuse policy

Adopt:

- `ai@7.0.37`
- `@ai-sdk/openai@4.0.20`
- `@ai-sdk/react@4.0.40` when Expo transport starts
- `@modelcontextprotocol/sdk@1.29.0`
- `zod@4.4.3`
- `ajv@8.20.0` plus `ajv-formats`
- `fast-json-patch@3.1.1`
- `json-logic-js@2.0.5`
- `xstate@5.32.5`

Copy proven repository spikes:

- `spikes/ai-sdk/src/server/**`
- `spikes/ai-sdk/src/client/**`
- `spikes/mcp-sdk/src/server.ts`
- `spikes/mcp-sdk/tests/parity.test.ts`

Wonder still owns:

- `applyOperation` authority.
- proposal/approval identity and consumption.
- revision/package/hash binding.
- receipts, provenance, inverse operations, and Undo.
- QuerySpec semantics.
- provider write/readback truth.
- package capability policy.

## 6. Full task DAG — 36 tasks

### Wave 0 — Control plane and scope

| ID | Task | Depends | Owner | Acceptance |
|---|---|---|---|---|
| U00 | Record baseline, dirty files, accepted SHA, rejected P2 evidence | — | reviewer | Baseline report matches current Git and `P2R2` |
| U01 | Amend execution scope for shared contracts, Expo chat, approval UI, Food | U00 | lead | Governing instructions are unambiguous |
| U02 | Convert ORCH to manual + isolated mode; prove a rejected dry run does not change canonical HEAD | U00 | control | Canonical HEAD unchanged; rejection blocks dependent |

**Gate W0:** U00–U02 PASS.

### Wave 1 — Repair and finish Phase 2

| ID | Task | Depends | Owned surface | Acceptance |
|---|---|---|---|---|
| U03 | Make `localQuery` isomorphic; fix truncation, output bounds, formats, schema drift | W0 | shared contract, server adapter, focused tests | reference/SQLite parity; malformed/oversized results reject |
| U04 | Pin server AI dependencies and repair Responses `ToolLoopAgent`; restore official web search | W0 | server manifest/lock, chat agent, web-search tests | typecheck; Responses IDs; citations; no custom OpenAI provider |
| U05 | Add official server UI-stream endpoint; preserve legacy endpoint without local query | U03,U04 | server chat route, contract tests | exact `toolCallId`; abort; continuation; no row persistence |
| U06 | Add Expo `localQuery` dispatcher, SQLite execution, dedupe, restart recovery | U03 | Expo chat client/dispatcher tests | bounded result; matching ID; duplicate safe; no server execute |
| U07 | Replace manual client chat transport with `useChat` + `DefaultChatTransport` | U05,U06 | Expo chat transport and screen | streaming, retry, cancel, resume, sources, errors |
| U08 | Phase 2 fresh-context integration review | U03–U07 | review artifact only | all Phase 2 and repository gates PASS on canonical tree |

**Hard checks:** config validation, typecheck, doctor, web/Android exports, AI runtime, localQuery contract/privacy/dedupe/resume, web search, query parity/SQL, multiturn, chat send, rollback idempotency.

### Wave 2 — Durable proposal, approval, apply, receipt, Undo

| ID | Task | Depends | Owned surface | Acceptance |
|---|---|---|---|---|
| U09 | Freeze canonical `OperationProposal` and `ApprovalReceipt` schemas | U08 | shared contracts and fixtures | stable action/proposal IDs; operations; targets; package/hash binding |
| U10 | Add approval receipt migration and repository | U09 | migrations, approvals DB module | immutable decision; expiry/revoke/consume; restart proof |
| U11 | Implement preview/dry-run and revision/package/hash binding | U09 | operation planning/tests | target-record version evidence; stale target rejects |
| U12 | Atomically consume approval and call `applyOperation`; idempotent replay/recovery | U10,U11 | canonical ops path | same action/idempotency key; crash recovery; no second writer |
| U13 | Wire canonical inverse Undo to completed receipt | U12 | canonical Undo/tests | verified completed action only; replay safe |
| U14 | Build preview/approve/reject/Undo UI with advanced evidence hidden | U09,U12,U13 | approval UI | default useful view; settings exposes provenance/debug |
| U15 | Phase 3 integration review | U09–U14 | review artifact only | `check:kernel`; restart, stale, replay, reject, Undo matrices PASS |

### Wave 3 — Replace fake agents and custom MCP in parallel

| ID | Task | Depends | Owned surface | Acceptance |
|---|---|---|---|---|
| U16 | Move useful retrieval/provenance/verification behavior behind official AI agent tools | U15 | AI agent modules/tests | parity fixtures PASS |
| U17 | Delete planner/executor/registry/domain/verifier/command-processor theater | U16 | `server/src/agents/**` | production import count zero |
| U18 | Adopt official stateless MCP Streamable HTTP server | U15 | MCP server and route | initialize/list/read/call/auth parity |
| U19 | Convert MCP mutations to proposal-only tools using P3 contract | U18,U15 | MCP tools/resources/tests | no direct records/actions/workflows/provider writes |
| U20 | Delete handwritten MCP JSON-RPC/SSE/protocol compatibility paths | U18,U19 | custom MCP files/tests | official transport only; deprecated APIs absent |
| U21 | Integrate AI/MCP route and dependency changes | U17,U20 | server index, manifest/lock owner | one dependency owner; typecheck and protocol gates PASS |
| U22 | Phase 4/5 fresh-context review | U16–U21 | review artifact only | AI parity, MCP parity, zero-import and no-direct-write audits PASS |

### Wave 4 — Durable reactive runtime

| ID | Task | Depends | Owned surface | Acceptance |
|---|---|---|---|---|
| U23 | Extend SQLite outbox with kind, availability, lease, cause, operation, result receipt | U22 | migrations/outbox/contracts | atomic claim, expiry, duplicate delivery, recovery |
| U24 | Build JSON Logic rule worker over committed-operation events | U23 | rules worker/tests | causal bound; cooldown; deterministic proposals only |
| U25 | Converge workflow runtime on persisted XState v5 snapshots | U23 | workflow runtime/tests | restart at every wait state; pause/resume/cancel; compensation proposals |
| U26 | Normalize provider write → reread → compare → verification receipt | U23 | provider adapters/tests | success/failed/ambiguous matrix; no HTTP-only success |
| U27 | Drain/archive and delete reactive, workflow, and package JSON authorities | U24–U26 | legacy JSON runtimes and migration | SQLite parity proof; no production imports |
| U28 | Phase 6 integration review | U23–U27 | review artifact only | rule, workflow, outbox, provider, migration and deletion gates PASS |

### Wave 5 — Package-driven Food

| ID | Task | Depends | Owned surface | Acceptance |
|---|---|---|---|---|
| U29 | Express Expiring Soon as AppPackage query/view/rule/workflow | U28 | Food package/fixtures | no hardcoded alternate runtime |
| U30 | Build polished Food surface using the package output and shared design tokens | U29 | Food UI only | useful default; edit/debug/provenance hidden; accessibility |
| U31 | Food end-to-end: expiry event → suggestion → approval → list update → Undo | U29,U30 | E2E fixtures/evidence | deterministic Android export evidence; `check:food-e2e` PASS |
| U32 | Phase 7 review | U29–U31 | review artifact only | package drives query, rule, workflow, provider-safe mutation |

### Wave 6 — Safe AI package builder

| ID | Task | Depends | Owned surface | Acceptance |
|---|---|---|---|---|
| U33 | Define allowlisted `PackageChangeRequest`; JSON Patch preview and validation | U32 | package builder contracts/tests | restricted pointers reject; non-mutating patch; Ajv + semantic checks |
| U34 | AI proposes package patch; durable approval binds exact patch/hash; atomic activate/rollback | U33,U15 | builder service, package registry, narrow UI | no AI direct activation; crash-safe rollback |
| U35 | Generality fixtures, optional plugin decision, final deletion/proof | U34 | trip/plant fixtures, cleanup, four gates | same engine runs Food/trip/plant; plugin only if proven necessary; all final gates PASS |

## 7. Parallel schedule

```text
W0: U00 + U01 + U02
W1: U03 || U04 -> U05; U06 || U05 -> U07 -> U08
W2: U10 || U11 after U09; U12 -> U13; U14 overlaps after contracts -> U15
W3: U16 -> U17  ||  U18 -> U19 -> U20; U21 -> U22
W4: U23 -> (U24 || U25 || U26) -> U27 -> U28
W5: U29 -> U30; U31 -> U32
W6: U33 -> U34 -> U35
```

Maximum productive concurrency is four. Several waves naturally expose only two or three safe writers.

## 8. Mandatory ownership locks

- `server/package*.json`: dependency owner only.
- root `package*.json`: dependency owner only.
- `server/src/index.ts`: integration owner only.
- `src/db/migrations.ts` and `src/db/outbox.ts`: kernel/outbox owner only.
- shared proposal/receipt contracts: contract owner only.
- `src/ops/**`: kernel owner only.
- `app/**`: product owner only.
- No task may weaken/delete tests outside an accepted replacement parity task.

## 9. Stop conditions

Stop the affected lane when:

- canonical base moves after task start;
- forbidden files change;
- a lockfile changes outside dependency ownership;
- tests are weakened/deleted without accepted parity;
- an implementation completes without a commit and current evidence;
- ORCH marks a rejected task done;
- a dependency lacks a committed PASS artifact;
- persisted JSON data has no migration/drain proof;
- provider success lacks reread verification;
- AI/MCP/XState bypasses proposal/approval/`applyOperation`.

## 10. Final definition of done

Done means all are true on one canonical commit:

- official AI SDK transport reads phone-local SQLite through bounded client tools;
- durable proposal → approval → apply → receipt → Undo works across restart/replay;
- fake multi-agent runtime is gone;
- production MCP uses the official SDK and is proposal-only;
- rules react to committed operations and emit proposals;
- XState workflows persist/resume/cancel/compensate without direct writes;
- provider writes are reread and verified or classified ambiguous/failed;
- Food is package-driven, polished, and proves the full loop;
- AI can safely preview and activate approved package changes;
- Food, trip, and plant fixtures use the same kernel;
- legacy JSON authorities and duplicate runtimes are removed;
- `check:kernel`, `check:food-e2e`, `check:package-e2e`, `check:provider-e2e`, typecheck, doctor, web export, and Android export all pass.

This is completion of the written convergence target, not every future vertical, plugin, release-signing, or device-certification possibility.

## 11. Expected duration

- Optimistic with healthy agents and no hidden migration/provider failures: **8–12 focused working days**.
- Realistic: **12–20 working days**.
- Primary risks: P2 transport continuation, approval atomicity, persisted JSON migration, provider reread normalization, and ORCH integration discipline.

