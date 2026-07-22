# Phase 5/6 sub-agent kickoff (spawned 2026-07-22)

Canonical slice manifest: `docs/lifeos/implementation-sub-agents-live-slice-manifest.md`
Detailed activation record: [`docs/lifeos/implementation-sub-agent-activation-2026-07-22.md`](docs/lifeos/implementation-sub-agent-activation-2026-07-22.md)

## Run activation (explicit multi-agent start)

- Status: `IN PROGRESS`
- Date: 2026-07-22
- Gate mode: one-phase-slice-at-a-time with explicit handoff gates.
- Merge policy: strict ownership only; no file edits outside the active slice owner block.
- Execution order for this run:
  1) S5 (workflow/action parity hardening that unblocks adapters)
  2) S3 (Notion adapter)
  3) S4 (Sheets adapter)
  4) S2 (chat-level undo/citation cross-check) only if adapter contracts change action shape
- Handoff template for each slice:
  - `sources`, `files changed`, `checks run`, `blockers`, `confidence (0-100)`, `gaps`
  - `evidence path` and `proof JSON/fixtures` in required directory

Owner: `lead`
Date: 2026-07-22

Spawning is active now, phase-gated, and file-isolated.

Explicit runtime bindings (this branch):
- `S3` / `lead/S3` (`Rawls`) → `019f8b54-bdb3-78c3-a633-364a4872e6ba`
- `S4` / `lead/S4` (`Bacon`) → `019f8b54-c49b-74a3-93ca-dd9e199e49f9`
- `S5` / `lead/S5` (`Bernoulli`) → `019f8b54-ccf9-7760-89a3-d64335fa2514`
- `S6` / `lead/S6` (`Kierkegaard`) → `019f8b54-d45c-7063-b4f9-c80ef4d06c02`
- `S7` / `lead/S7` (`Dewey`) → `019f8b59-3924-7113-aee3-00d13ce1d1e0`
- `S2` / `lead/S2` (`Heisenberg`) → `019f8b59-4024-7040-a63f-e0f853719981`

## Spawn rules (all agents)

- Typed contracts only: JSON schema IDs are the only cross-agent boundary.
- No overlapping file ownership unless explicitly approved by the lead.
- One phase-slice per PR.
- No direct provider calls from `src/` app code.
- Every handoff report must include:
  - `sources`
  - `files changed`
  - `checks run`
  - `blockers`
  - `confidence (0-100)`
  - `gaps`
- No secrets in `app/` or `src/` browser/runtime code.
- Evidence required: proof JSON, API fixtures, screenshot evidence where applicable.
- Live credentials for Notion/Sheets are only via `agent-env`.

## S3 — Notion adapter (Phase 5)

- Status: ACTIVE
- Spawned: 2026-07-22 00:00 UTC
- Owner: `S3`
- Files:
  - `server/src/providers/notion/client.ts`
  - `server/src/providers/notion/discovery.ts`
  - `server/src/providers/notion/projection.ts`
  - `server/src/providers/notion/pull.ts`
  - `server/src/providers/notion/push.ts`
  - `server/src/providers/notion/webhook.ts`
  - `server/src/providers/notion/citations.ts`
- Inputs:
  - `docs/lifeos/expo-implementation-plan.md` and `docs/lifeos/product-pass.md`
  - `NOTION_API_VERSION=2026-03-11`
  - `packages/domain-config/*`
  - domain-command/action/schemas from `packages/domain-config/schemas`
  - canonical record schema from `src/domain/runtime.ts`
- Output contracts:
  - Pull path emits typed `DomainRecord[]` plus provider snapshot rows
  - Push path emits typed command results + canonical mapping
  - Webhook path never treats webhook payload as canonical content
  - Citation IDs map to canonical LifeOS IDs and immutable source spans where possible
- Forbidden:
  - `NOTION_VERSION` fallback paths that use page/database ID without data source
  - no canonical writes from webhook payload
  - no credential usage outside `server/src/providers/notion/client.ts`
- Acceptance gates:
  - `npm run phase5:check:notion-adapter`
  - `npm run config:validate`
  - `npm run typecheck`
  - `npm run phase4:check:mcp-workflow-replay` (for action parity sanity)
  - Disposable Notion round trip fixture + webhook duplicate/reorder smoke
- Deliverables for closeout:
  - Evidence JSON under `app/build/evidence/phase5-notion-adapter/`
  - Updated source contract snapshots in `app/build/evidence`
- Lead review:
  - Confirm contract boundaries match `docs/lifeos/implementation-sub-agents.md`
  - Confirm no hardcoded or fake source data remains in live path

## S4 — Google Sheets adapter (Phase 6)

- Status: ACTIVE
- Spawned: 2026-07-22 00:00 UTC
- Owner: `S4`
- Files:
  - `server/src/providers/sheets/client.ts`
  - `server/src/providers/sheets/workbook.ts`
  - `server/src/providers/sheets/projection.ts`
  - `server/src/providers/sheets/pull.ts`
  - `server/src/providers/sheets/push.ts`
  - `server/src/providers/sheets/health.ts`
- Inputs:
  - `docs/lifeos/expo-implementation-plan.md` and `docs/lifeos/product-pass.md`
  - `data_home=google_sheets` routing in command contracts
  - canonical record/undo schemas
  - Google OAuth scope policy
- Output contracts:
  - Pull path reads canonical tab metadata and stable `lifeos_id` maps
  - Write path stores pre-write `ValueRange` for undo and captures append range
  - Health path emits required tab/column/version checks
- Forbidden:
  - v3 endpoints
  - append without persisted updated range
  - write operations without prior validation of tab schema
- Acceptance gates:
  - `npm run phase6:check:sheets-adapter`
  - `npm run config:validate`
  - `npm run typecheck`
  - disposable workbook round trip with idempotent re-sync
  - formula/managed-column preservation check on undo restore
- Deliverables for closeout:
  - Evidence JSON under `app/build/evidence/phase6-sheets-adapter/`
  - Workbook contract fixture and reconciliation report
- Lead review:
  - Confirm no broad Drive scope and no v3 call paths
  - Confirm formula preservation and prior-range snapshots remain stable on undo

## S5 — MCP + action engine + workflows (Phases 4,7)

- Status: ACTIVE
- Spawned: 2026-07-22 00:00 UTC
- Owner: `S5`
- Files:
  - `server/src/workflows/checkpoint.ts`
  - `server/src/workflows/compensation.ts`
  - `server/src/workflows/runner.ts`
  - `server/src/mcp/{server.ts,tools.ts,resources.ts,auth.ts,policy.ts}`
  - `src/actions/{engine.ts,policy.ts,undo.ts}`
  - `server/src/mcp/state.ts`
- Inputs:
  - action/receipt/undo schema contracts
  - server chat action envelope expectations
  - Notion/Sheets adapter contracts
- Output contracts:
  - single typed action envelope across App + MCP + Chat
  - checkpointed workflow execution with compensating rollback
  - consistent denial and policy telemetry
- Forbidden:
  - schema drift between command and tool surfaces
  - free-form action payloads
  - exposing irreversible actions in user path
- Acceptance gates:
  - `npm run phase4:check:mcp-workflow-replay`
  - `npm run phase4:check:mcp-workflow-replay-http`
  - action idempotency and receipt-shape parity checks
- Deliverables for closeout:
  - Evidence JSON under `app/build/evidence/phase4-mcp-workflow-replay*`
  - policy matrix evidence in test output

## S2 blocker handoff (Phase 3 overlap)

- Status: ACTIVE
- Owner: `S2`
- Files:
  - `server/src/agents/executor.ts`
  - `src/chat/*`
  - `server/src/chat.ts`
  - `server/src/agents/{registry,orchestrator,retrieval,planner,verifier}.ts`
- Blockers for S3/S4:
  - `undo_ready` must remain false for non-mutation outputs
  - chat streaming and exact citation surfaces must be callable by MCP parity assertions
  - ambiguous write prompts must carry no side effects
- Checks:
  - `npm run phase3:check:chat-undo`
  - chat stream/error-cancel smoke suite from existing phase-3 scripts

## Next lead handoff checkpoint

- Run all required gates after each agent PR merge:
  - `npm run config:validate`
  - `npm run typecheck`
  - `npm run doctor`
  - `npm run export:web`
  - `npm run export:android`
  - phase-specific acceptance script
- Merge rule:
  - accept adapter changes only after matching evidence proof path exists and passes.
