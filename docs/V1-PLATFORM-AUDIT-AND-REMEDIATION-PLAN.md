# WonderFood V1 Platform Audit and Remediation Plan

Status: **independent acceptance audit complete; V1 not accepted**
Audit baseline: `61cb98b` (`2026-07-26`)
Reference docs: `docs/V1-CURRENT-ACCEPTANCE.md`, `docs/REPOSITORY-AUDIT.md`

## Executive decision

WonderFood is still **pre-V1 hardening**.

- `P0-01` is only `PARTIAL`: anonymous MCP reads are gone, but bearer-scoped confidentiality is still weak because scope and principal remain caller-asserted and authenticated unscoped callers still get global indexes.
- `P1-03` is `OPEN`: the chat executor still writes `local_sqlite` mutations directly through MCP state helpers instead of one canonical operation boundary.
- `P1-01`, `P1-02`, `P1-04`, `P1-06`, `P1-07`, and `P1-08` are still `PARTIAL`, so the server/runtime is not acceptance-safe.
- Release proof is still blocked: `npm run phase8:check:release-readiness` reports missing/stale signed mobile artifacts, and repo proof docs are stale at `8b2cb9a`, not `61cb98b`.

## Status snapshot at `61cb98b`

Summary:

- P0: `0 RESOLVED`, `1 PARTIAL`, `0 OPEN`
- P1: `1 RESOLVED`, `6 PARTIAL`, `1 OPEN`
- P2: `4 RESOLVED`, `6 PARTIAL`, `3 OPEN`

| ID | Status | Owner lane | Why it still matters |
|---|---|---|---|
| `P0-01` | `PARTIAL` | `A` | MCP reads are authenticated now, but scope/principal are still header-driven and global indexes are still exposed to authenticated unscoped callers. |
| `P1-01` | `PARTIAL` | `A` | Requests fail closed, but startup still allows external bind and `LIFEOS_LOCAL_DEV` bypass is not loopback-bound. |
| `P1-02` | `PARTIAL` | `B` | Durable review approval now blocks writes, but policy is still boolean/meta instead of one exhaustive decision union. |
| `P1-03` | `OPEN` | `B` | Default `local_sqlite` chat mutations still bypass the canonical app operation engine. |
| `P1-04` | `PARTIAL` | `D` | Provider undo readback exists, but undo still mutates MCP JSON state directly and still overloads `cancelled` as the terminal state. |
| `P1-05` | `RESOLVED` | `D` | Verifier now denies missing action, mismatched tool/status/record ids, missing reread, missing undo payload, and source-bound mismatch. |
| `P1-06` | `PARTIAL` | `C` | MCP/provider JSON stores improved, but conversations/reactive runtime still use raw JSON writes with no shared durability contract. |
| `P1-07` | `PARTIAL` | `C` | Body-size limits and `413` handling exist, but there are still no request deadlines/header limits/slow-body protections. |
| `P1-08` | `PARTIAL` | `C` | Idempotency is now principal and conversation scoped, but still process-memory only and not restart durable. |
| `P2-01` | `RESOLVED` | `B` | `plan_hint` no longer changes command semantics. |
| `P2-02` | `RESOLVED` | `B` | Client-supplied `previous_response_id` is ignored on `/chat/send` and `/chat/send/stream`; stored conversation state wins. |
| `P2-03` | `PARTIAL` | `F` | Retrieval now projects allowlisted facts and only calls live providers when the query selects them, but there is still no cache/timeout/circuit/source budget. |
| `P2-04` | `RESOLVED` | `F` | Notion pull paginates until target/limit instead of stopping at page one. |
| `P2-05` | `OPEN` | `E` | Reactive runtime still installs observers only; normal startup never drains the outbox. |
| `P2-06` | `RESOLVED` | `E` | Review-required reactive proposals now persist as `awaiting_review` instead of being acked away. |
| `P2-07` | `PARTIAL` | `E` | Observer failures now emit durable failed receipts, but the observer is still advisory rather than a transactional outbox stage. |
| `P2-08` | `PARTIAL` | `H` | Release readiness blocks unsigned/debug proof, but the artifact script can still debug-sign a release APK during build and only hard-fails when `REQUIRE_RELEASE_SIGNING=1`. |
| `P2-09` | `PARTIAL` | `H` | Ajv-backed config validation is live, but it still runs with `strict: false` and a small mutation corpus. |
| `P2-10` | `OPEN` | `H` | Persistence-sensitive app tests still rely on `tests/helpers/memory-db.ts` instead of real SQLite. |
| `P2-11` | `PARTIAL` | `B` | Shared record provenance exists, but confidence semantics are still split between numeric provenance and qualitative policy/command confidence. |
| `P2-12` | `OPEN` | `H` | Docs and evidence authority are still stale or conflicting: `README.md`, `FEATURES.md`, `docs/REPOSITORY-AUDIT.md`, and `check-lifeos-completion-audit.mjs` are not tied to current acceptance state. |
| `P2-13` | `PARTIAL` | `H` | Default repo gates no longer run live provider/device proof, but live lanes still sit in the same script namespace without hard tenant/device guardrails. |

## Highest-risk current seams

1. MCP confidentiality is improved but not finished.
   Source: `server/src/mcp/official-server.ts:302-317,408-417`, `server/src/mcp/auth.ts:127-138`, `server/src/mcp/resources.ts:207-248`

2. Default chat mutation still has a parallel writer.
   Source: `server/src/agents/executor.ts:606-684,756-808,879-931`

3. Undo truth improved, but lifecycle semantics are still muddy.
   Source: `server/src/mcp/state.ts:1493-1527,1574-1698,1705-1836`

4. Reactive proposals can persist forever without a worker lifecycle.
   Source: `server/src/kernel/install-reactive-runtime.ts:89-146`, `server/src/index.ts:121-126`

5. Release evidence is still not store-grade proof.
   Source: `android/app/build.gradle:89-132`, `scripts/quality/check-android-release-artifacts.sh:18-33,36-93`, `scripts/quality/check-release-readiness.mjs:53-75`

## Next-wave task queue

1. `A` auth and MCP containment
   Files: `server/src/mcp/{auth,official-server,resources}.ts`, `server/src/index.ts`, `server/test/{mcp-official-security,ingress-security}.ts`
   Deliverables: bind MCP domain/principal scope to authenticated server state, hide global private indexes by default, fail startup on non-loopback bind without auth, force `LIFEOS_LOCAL_DEV` to loopback-only.
   Checks: existing MCP/ingress suites plus a new self-asserted-scope denial test and non-loopback boot-refusal test.

2. `B` one policy machine and one writer
   Files: `server/src/agents/executor.ts`, `server/src/mcp/policy.ts`, `server/src/mcp/tools.ts`, shared policy/receipt contracts
   Deliverables: remove direct `createRecord`/`updateRecord`/`archiveRecord` paths from chat executor, route local authority through one canonical operation boundary, collapse boolean/meta policy into `deny | clarify | review | execute`.
   Checks: static import boundary for server ingresses, chat/MCP/app ingress parity suite, review-zero-write tests.

3. `C` durability, idempotency, and HTTP bounds
   Files: `server/src/{index,conversations}.ts`, `server/src/kernel/install-reactive-runtime.ts`, shared persistence modules
   Deliverables: restart-durable idempotency store, shared atomic/quarantine pattern for every server-side state file or SQLite migration, request timeout/header limit handling.
   Checks: restart replay, corrupt-file, concurrent-writer, chunked slow-body, and crash-recovery tests.

4. `D` truthful undo and lifecycle states
   Files: `server/src/mcp/state.ts`, `server/src/providers/undo.ts`, workflow compensation paths
   Deliverables: canonical undo state machine (`undo_pending`, `undone`, `undo_failed`), no overloaded `cancelled`, and no direct local rollback after a provider action without canonical receipt.
   Checks: Notion/Sheets create/update/archive undo contract suite, workflow compensation truth table, replay/idempotency suite.

5. `E` reactive runtime lifecycle
   Files: `server/src/kernel/{install-reactive-runtime,reactive-outbox,reactive-proposal-executor,operation-observer}.ts`
   Deliverables: startup drain, periodic/event wake, lease, retry/backoff, dead-letter, and approval resume path.
   Checks: restart/eventual-drain proof, one-active-lease proof, review-resume proof, degraded-health proof.

6. `F` retrieval privacy and provider load controls
   Files: `server/src/agents/retrieval.ts`, provider pull clients
   Deliverables: timeout/circuit/cache/source-budget rules, explicit freshness semantics, and larger dataset/load tests.
   Checks: `server/test/retrieval-contract.ts` expansion, provider timeout tests, large-row pagination/load tests.

7. `H` repo truth, release truth, and dependency hygiene
   Files: `README.md`, `FEATURES.md`, `docs/REPOSITORY-AUDIT.md`, `scripts/quality/check-lifeos-completion-audit.mjs`, `scripts/quality/check-android-release-artifacts.sh`, `tests/helpers/memory-db.ts`, `tsconfig.json`, `spikes/**`
   Deliverables: regenerate current docs from `61cb98b`, make completion audit fail on open P0/P1, isolate or exclude broken spikes from repo typecheck, move persistence-sensitive tests to real SQLite, require release signing by default in release-proof lanes, and address current production `npm audit` findings.
   Checks: `npm run typecheck`, `npm run phase9:check:completion-audit`, `npm run phase8:check:android-release-signed`, `npm audit --omit=dev --json`.

## Required rerun gate before another acceptance pass

```sh
npm run config:validate
npm run check:kernel-boundaries
npm run check:operation-boundary
npm run typecheck
npm run test
npm run test:server:direct
npm run phase4:check:mcp
npm run phase8:check:release-readiness
NPM_CONFIG_CACHE=/tmp/wonderfood-npm-cache npm run doctor
```

Do not call V1 accepted until all are true:

- zero `OPEN` or `PARTIAL` P0/P1 rows
- release-readiness passes with current signed artifacts
- docs/evidence authority is regenerated from the current commit
- repo-wide typecheck is green without spike bleed or stale proof shortcuts

Until then, the system remains **pre-V1 hardening**.
