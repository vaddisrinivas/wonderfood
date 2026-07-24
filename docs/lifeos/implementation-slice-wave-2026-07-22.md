# LifeOS implementation: explicit phase-slice spawn (2026-07-22)

Status: LIVE start checkpoint for Notion/Sheets continuation.
Start time: 2026-07-22.
Lead: `lead`.

This wave is the explicit precondition before continuing Phase 5 + Phase 6 work.

## S3 — Notion adapter (Phase 5)

Owner: `Rawls` (`019f8b54-bdb3-78c3-a633-364a4872e6ba`).
Files: `server/src/providers/notion/*`.
Required docs: `docs/lifeos/expo-implementation-plan.md`, `docs/lifeos/product-pass.md`, `docs/lifeos/implementation-sub-agents-live-slice-manifest.md`.
Input contracts: `NOTION_API_VERSION=2026-03-11`, `packages/domain-config/*`, `data_source_id` map, `src/domain/runtime.ts`.
Output contracts: typed pull/push projection, webhook refetch-before-write, source snapshots, immutable citation handles, provider-owned field preservation.
Tests: `npm run phase5:check:notion-adapter`, `npm run config:validate`, `npm run typecheck`.
Forbidden: secret usage outside server, page/database fallback bypassing `data_source_id`, webhook payload writes, fake source fabrication.
Required handoff report: sources, files changed, checks, blockers, confidence, gaps.

## S4 — Google Sheets adapter (Phase 6)

Owner: `Bacon` (`019f8b54-c49b-74a3-93ca-dd9e199e49f9`).
Files: `server/src/providers/sheets/*`.
Required docs: `docs/lifeos/expo-implementation-plan.md`, `docs/lifeos/product-pass.md`, `docs/lifeos/implementation-sub-agents-live-slice-manifest.md`.
Input contracts: Sheets v4 batch operations, command/action schema, domain mapping.
Output contracts: deterministic pull/push, `updated_range` + previous-value snapshots for undo, formula/managed-column preservation.
Tests: `npm run phase6:check:sheets-adapter`, `npm run config:validate`, `npm run typecheck`.
Forbidden: v3 endpoints, broad Drive scopes, append without persisted updated range, mutable schema drift.
Required handoff report: sources, files changed, checks, blockers, confidence, gaps.

## S5 — MCP parity + action/workflow engine (Phases 4,7)

Owner: `Bernoulli` (`019f8b54-ccf9-7760-89a3-d64335fa2514`).
Files: `server/src/mcp/*`, `server/src/workflows/*`, `src/actions/*`.
Required docs: `docs/lifeos/expo-implementation-plan.md`, `docs/lifeos/product-pass.md`, `docs/lifeos/implementation-sub-agents-live-slice-manifest.md`.
Input contracts: command/agent-handoff/action schemas, chat/action receipt shape, policy matrix.
Output contracts: one action model across app/chat/MCP, workflow checkpoint/compensation parity, deny/audit traceability.
Tests: `npm run phase4:check:mcp-workflow-replay`, `npm run phase4:check:mcp-workflow-replay:http`, `npm run typecheck`.
Forbidden: schema drift between channels, irreversible action exposure, free-form action payloads.
Required handoff report: sources, files changed, checks, blockers, confidence, gaps.

## S2 — Chat continuation + internal agents (Phase 3 overlap)

Owner: `Heisenberg` (`019f8b59-4024-7040-a63f-e0f853719981`).
Files: `server/src/chat.ts`, `server/src/agents/*`, `src/chat/*`.
Required docs: `docs/lifeos/expo-implementation-plan.md`, `docs/lifeos/product-pass.md`, `docs/lifeos/implementation-sub-agents-live-slice-manifest.md`.
Input contracts: Responses+Conversations durability, source snapshots, citations schema.
Output contracts: streaming + retries/cancel, exact citeable claims, ambiguous-write clarification, undo-ready only for mutating actions.
Tests: `npm run phase3:check:chat-undo`, `npm run phase3:check:chat-send`, `npm run phase3:check:chat-rollback-idempotency`, `npm run typecheck`.
Forbidden: demo fallbacks in live response path, non-mutating action receipts, prompt-like source fabrication.
Required handoff report: sources, files changed, checks, blockers, confidence, gaps.

## Cross-slice merge rule

No file edits outside ownership blocks without explicit lead merge instruction.
No PR merge without evidence and required gates passing.
No sensitive/irreversible action surfaces in non-server paths.
