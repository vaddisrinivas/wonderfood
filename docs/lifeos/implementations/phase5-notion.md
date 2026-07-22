# Phase 5 Notion adapter slice (implementation notes)

## Scope
- Provider write path: `server/src/providers/notion/*`
- Webhook ingestion: `server/src/providers/webhooks/notion.ts`
- Sync bridge: `server/src/providers/sync/notion.ts`
- Notion quality checks: `scripts/quality/check-phase5-notion-*`
- Domain provider config: `packages/domain-config/providers/notion/*`
- Tests: `server/test/notion/unit-notion-adapter.ts`, `server/test/notion/contract-notion-webhook.ts`

## Inputs
- Typed MCP command + resolved `data_home`.
- Notion payload with `page_id` or `external_id` identity.
- Env-driven credentials only (`NOTION_TOKEN`, `NOTION_DATA_SOURCE_ID`, optional `NOTION_WEBHOOK_SIGNING_SECRET`).

## Outputs/contracts
- Adapter write result includes:
  - `success`
  - `source_snapshot` with `provider`, `data_source_id`, `page_id`, `block_id`, `range`
  - `provider_record_id`
  - `action_receipt` (for successful MCP-notion writes)
- Unsupported fields preserved into `provider_snapshot`/`source_snapshot.unsupported`.
- Writes use Notion `data_source_id` parent and `Notion-Version: 2026-03-11`.

## Anti-pattern checks
- No page/database fallbacks.
- No direct canonical writes from webhook payload.
- No synthetic/fake writes in tests.
- Undo depends on persisted provider delta; no duplicate fake `undo_ready`.

## Evidence
- `app/build/evidence/phase5-notion-adapter/phase5-notion-adapter-proof.json`
- `app/build/evidence/phase5-notion-adapter/phase5-notion-unit-proof.json`
- `app/build/evidence/phase5-notion-adapter/phase5-notion-contract-proof.json`

## Acceptance
- `npm run test:server:notion`
- `npm run test:contract:notion`
- `npm run phase5:check`
- plus global gates in root `package.json` (`config:validate`, `typecheck`, `doctor`, `export:web`, `export:android`).
