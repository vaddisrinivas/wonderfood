# Explicit sub-agent activation: Notion/Sheets continuation

Date: 2026-07-22
Lead: `lead`
Mode: one-slice-at-a-time merge; explicit cross-slice handoff required.

## Launch order

1. `S5` (Bounded MCP/action/workflow parity) unblocks adapter action-shape dependencies.
2. `S3` (Notion adapter + webhooks) using stabilized action model.
3. `S4` (Google Sheets adapter) using parity contracts from `S5` and `S3`.

## Slice S5 (owner: Bernoulli, id `019f8b54-ccf9-7760-89a3-d64335fa2514`)

- Scope: Phases 4,7
- Files:
  - `server/src/mcp/{server.ts,tools.ts,resources.ts,auth.ts,policy.ts,state.ts}`
  - `server/src/workflows/{checkpoint.ts,compensation.ts,runner.ts}`
  - `src/actions/{engine.ts,policy.ts,undo.ts}`
- Input contracts:
  - `packages/domain-config/schemas/{command,agent-handoff,action-event,undo}.v1.schema.json`
  - `server/src/agents/*` action envelope expectations
  - `/chat/send` response/action shape and receipt semantics
- Output contracts:
  - single typed action model across App + MCP + chat
  - replayable workflow execution with compensation
  - idempotency, deny paths, and policy telemetry parity
- Forbidden:
  - schema drift across App/MCP/adapter channels
  - free-form action payloads
  - irreversible actions in user-facing mutable paths
- Required gates:
  - `npm run config:validate`
  - `npm run typecheck`
  - `npm run phase4:check:mcp-workflow-replay`
  - `npm run phase4:check:mcp-workflow-replay:http`
- Mandatory handoff report:
  - sources, files changed, checks run, blockers, confidence, gaps

## Slice S3 (owner: Rawls, id `019f8b54-bdb3-78c3-a633-364a4872e6ba`)

- Scope: Phase 5
- Files:
  - `server/src/providers/notion/{client.ts,discovery.ts,projection.ts,pull.ts,push.ts,webhook.ts,citations.ts}`
- Input contracts:
  - Notion API `2026-03-11`
  - `data_source_id` first-class mapping
  - canonical record schemas from `packages/domain-config` and `src/domain/runtime.ts`
- Output contracts:
  - typed pull/push with provider snapshots + command receipts
  - webhook processing is refetch-and-verify, never canonicalized directly from payload
  - immutable citation handles where available
- Forbidden:
  - page/database fallback that bypasses `data_source_id`
  - secret usage in `app/` or `src/`
  - fabricating fake citation source text
- Required gates:
  - `npm run config:validate`
  - `npm run typecheck`
  - `npm run phase5:check:notion-adapter`
- Mandatory handoff report:
  - sources, files changed, checks run, blockers, confidence, gaps

## Slice S4 (owner: Bacon, id `019f8b54-c49b-74a3-93ca-dd9e199e49f9`)

- Scope: Phase 6
- Files:
  - `server/src/providers/sheets/{client.ts,workbook.ts,projection.ts,pull.ts,push.ts,health.ts}`
- Input contracts:
  - Sheets v4 batch read/write semantics
  - action/undo schema contracts for cell-level reversibility
- Output contracts:
  - deterministic `updated_range` + prior-values snapshots
  - formula/preserved-field safety
  - domain command receipts compatible with chat/MCP
- Forbidden:
  - v3 endpoint usage
  - append operations without persisted updated range
  - broad Drive scope
- Required gates:
  - `npm run config:validate`
  - `npm run typecheck`
  - `npm run phase6:check:sheets-adapter`
- Mandatory handoff report:
  - sources, files changed, checks run, blockers, confidence, gaps

## Merge rule

- No file edits outside the active slice ownership block.
- No new slice can merge without explicit lead acknowledgment and pass evidence.
- Sensitive/irreversible actions remain server-only.
- Evidence path is mandatory for acceptance handoff (`app/build/evidence/*`).
