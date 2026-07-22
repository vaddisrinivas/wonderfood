# LifeOS live slice manifests (2026-07-22)

Use this file for phase-boundary execution. No file outside a slice ownership block may be edited without explicit lead re-route.

## Spawned slices

## Explicit phase bindings

- `S3` (`019f8b54-bdb3-78c3-a633-364a4872e6ba`, `Rawls`) — Notion adapter (Phase 5)
  - Contracts: live auth, webhook intake, `data_source_id` mapping, exact citation projection, provider-owned field preservation
  - Acceptance: `npm run phase5:check:notion-adapter`, `npm run config:validate`, `npm run typecheck`
  - Required artifacts: `server/src/providers/notion/*`, evidence in `app/build/evidence/phase5-notion-adapter`
- `S4` (`019f8b54-c49b-74a3-93ca-dd9e199e49f9`, `Bacon`) — Google Sheets adapter (Phase 6)
  - Contracts: v4 batch read/write, deterministic undo snapshots, formula preservation, health checks
  - Acceptance: `npm run phase6:check:sheets-adapter`, `npm run config:validate`, `npm run typecheck`
  - Required artifacts: `server/src/providers/sheets/*`, evidence in `app/build/evidence/phase6-sheets-adapter`
- `S5` (`019f8b54-ccf9-7760-89a3-d64335fa2514`, `Bernoulli`) — MCP parity and workflows (Phases 4,7)
  - Contracts: one action model across app/chat/MCP, checkpointed cancellation, compensation, policy deny traces
  - Acceptance: `npm run phase4:check:mcp-workflow-replay`, `npm run phase4:check:mcp-workflow-replay:http`, `npm run typecheck`
  - Required artifacts: `server/src/mcp/*`, `server/src/workflows/*`, `src/actions/*`, evidence in `app/build/evidence/phase4-mcp-workflow-replay*`
- `S6` (`019f8b54-d45c-7063-b4f9-c80ef4d06c02`, `Kierkegaard`) — Expo UI and generic renderer hardening (Phases 2,9)
  - Contracts: domain-agnostic surfaces, responsive QA breakpoints, accessibility baseline, no direct network from UI
  - Acceptance: responsive + a11y checks, `npm run export:web`, `npm run export:android`, app evidence screenshots
- `S7` (`019f8b59-3924-7113-aee3-00d13ce1d1e0`, `Dewey`) — Android + EAS + Health Connect (Phase 8)
  - Contracts: emulator pass, permission-safe Health Connect path, offline surface, APK/AAB production draft gate
  - Acceptance: android export/build evidence and emulator QA before device gate
- `S2` (`019f8b59-4024-7040-a63f-e0f853719981`, `Heisenberg`) — Chat + Responses continuation support (Phase 3 overlap)
  - Contracts: streaming + citations + retry/cancel + undo parity with `S5` action receipts
  - Acceptance: `npm run phase3:check:chat-undo`, cancellation/retry checks, citation exactness evidence

> Merge rule for explicit bindings: no cross-slice edit unless both slice owners and lead sign off the same file in one handoff block.

### S3 — Notion adapter
- **Owner:** `lead/S3`
- **Phase:** 5
- **Status:** ACTIVE
- **File ownership:**
  - `server/src/providers/notion/client.ts`
  - `server/src/providers/notion/discovery.ts`
  - `server/src/providers/notion/projection.ts`
  - `server/src/providers/notion/pull.ts`
  - `server/src/providers/notion/push.ts`
  - `server/src/providers/notion/webhook.ts`
  - `server/src/providers/notion/citations.ts`
- **Required docs:**
  - `docs/lifeos/expo-implementation-plan.md`
  - `docs/lifeos/product-pass.md`
- **Input contracts:**
  - `NOTION_API_VERSION=2026-03-11`
  - `data_source_id` lookup-first mapping
  - `packages/domain-config/schemas/*.schema.json`
  - canonical domain record schema from `src/domain/runtime.ts`
- **Output contracts:**
  - typed pull/push returns `DomainRecord[]` + snapshot rows only
  - webhook path never writes from untrusted payload
  - immutable source-citation handles in canonical record projection
  - provider-owned fields preserved
- **Acceptance gates:**
  - `npm run phase5:check:notion-adapter`
  - `npm run config:validate`
  - `npm run typecheck`
  - disposable template round-trip + webhook dedupe smoke
- **Forbidden/anti-patterns:**
  - page/database-only fallback paths
  - secret usage outside server provider code
  - any overwrite from webhook payloads
  - fake source fabrication
- **Required handoff report:**
  - sources, files changed, checks, blocker(s), confidence, gaps

### S4 — Google Sheets adapter
- **Owner:** `lead/S4`
- **Phase:** 6
- **Status:** ACTIVE
- **File ownership:**
  - `server/src/providers/sheets/client.ts`
  - `server/src/providers/sheets/workbook.ts`
  - `server/src/providers/sheets/projection.ts`
  - `server/src/providers/sheets/pull.ts`
  - `server/src/providers/sheets/push.ts`
  - `server/src/providers/sheets/health.ts`
- **Required docs:**
  - `docs/lifeos/expo-implementation-plan.md`
  - `docs/lifeos/product-pass.md`
- **Input contracts:**
  - Sheets v4 batch API semantics
  - undo snapshot + updated-range capture requirements
  - domain command receipts from `packages/domain-config/schemas`
- **Output contracts:**
  - deterministic read/write/append with prior range snapshots
  - formula and managed column preservation
  - exact cell-level command receipts and domain mapping
- **Acceptance gates:**
  - `npm run phase6:check:sheets-adapter`
  - `npm run config:validate`
  - `npm run typecheck`
  - disposable workbook mutation/undo loop
- **Forbidden/anti-patterns:**
  - v3 endpoint usage
  - append without persisted updated range
  - broad Drive scopes in runtime
  - schema drift from domain action/undo payloads
- **Required handoff report:**
  - sources, files changed, checks, blocker(s), confidence, gaps

### S5 — MCP + action + workflows
- **Owner:** `lead/S5`
- **Phase:** 4 and 7
- **Status:** ACTIVE
- **File ownership:**
  - `server/src/mcp/*`
  - `server/src/workflows/*`
  - `src/actions/*`
- **Required docs:**
  - `docs/lifeos/expo-implementation-plan.md`
  - `docs/lifeos/product-pass.md`
- **Input contracts:**
  - command/agent-handoff/undo schemas in `packages/domain-config/schemas`
  - chat action envelope in `server/src/agents/*`
- **Output contracts:**
  - one typed action model across app/chat/MCP
  - replayable + compensating workflow execution
  - exact policy-deny/permission traceability
- **Acceptance gates:**
  - `npm run phase4:check:mcp-workflow-replay`
  - `npm run phase4:check:mcp-workflow-replay:http`
  - cross-channel action parity checks
- **Forbidden/anti-patterns:**
  - free-form action payloads
  - irreversible actions in mutable UI-facing paths
  - schema drift between MCP and app clients
- **Required handoff report:**
  - sources, files changed, checks, blocker(s), confidence, gaps

## Lead rules
- One slice at a time in merge order.
- No file outside ownership blocks from that slice unless lead explicitly amends this manifest.
- No merge without updated evidence paths and passing required checks.
