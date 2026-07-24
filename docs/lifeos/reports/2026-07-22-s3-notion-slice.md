# Phase 5 — Notion adapter slice handoff (S3)

## Sources
- `server/src/providers/notion/pull.ts`
- `server/src/providers/notion/push.ts`
- `scripts/quality/check-phase5-notion-adapter.ts`
- `app/build/evidence/phase5-notion-adapter/phase5-notion-adapter-proof.json`
- `docs/lifeos/implementation-sub-agents-live-slice-manifest.md`
- `docs/lifeos/implementation-sub-agent-kickoff-2026-07-22.md`

## Files changed
- `server/src/providers/notion/pull.ts`
  - `readRecordDomain` now resolves `LifeOS Domain`, `LifeOS Collection`, `Domain`, `Collection`, and lowercase variants from incoming Notion records.
  - Notion source snapshot metadata now preserves fallback domain/collection values from existing properties.
- `server/src/providers/notion/push.ts`
  - `normalizeNotionRecordPayload` now writes canonical `LifeOS Domain`/`LifeOS Collection` keys with fallback behavior for raw `Domain`/`Collection` payloads.

## Checks run
- `npm run config:validate`
- `npm run typecheck`
- `npm run doctor` (via `NPM_CONFIG_CACHE=/tmp/wonderfood-npm-cache npm run doctor`)
- `npm run phase5:check:notion-adapter`
- `npm run phase4:check:mcp-workflow-replay`
- `npm run phase4:check:mcp-workflow-replay:http`
- `npm run phase6:check:sheets-adapter`
- `npm run export:web`
- `npm run export:android`

## Blockers
- Live Notion auth and end-to-end OAuth-backed mutation loop remain unimplemented.
- Webhook replay fixture persistence and conflict-safe idempotent re-sync remain pending.

## Confidence
- 74

## Gaps
- Missing authoritative Notion credential integration and live smoke tests against disposable templates.
- `notionFetch` still requires manual provider-specific property typing for Domain/Collection fields and could still mismatch real database schema.
- No MCP policy matrix proof specifically for Notion write/update actions under ambiguous intent.
