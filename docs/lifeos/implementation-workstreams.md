# LifeOS workstream contracts

## Workstream A: Canonical data (`@/src/db`, `@/src/domain`)
- Owner: A1
- Files owned:
  - `src/domain/catalog.ts`
  - `src/domain/runtime.ts`
  - `src/db/provider.tsx`
  - `src/db/migrations.ts`
  - `src/db/records.ts`
  - `src/db/conversations.ts`
  - `src/db/sources.ts`
  - `src/db/outbox.ts`
  - `src/db/actions.ts`
  - `src/db/undo.ts`
  - `src/db/seed.ts`
- Forbidden:
  - Do not edit legacy provider/agent/domain code in `app` state screens.
  - Do not introduce provider keys or secrets in Expo runtime.
- Input contracts:
  - Uses domain catalog + manifest data from `packages/domain-config`.
  - All writes call schema validation before commit.
- Output contracts:
  - Canonical records and message tables persisted in SQLite.
  - Recovery export from active DB path.
- Acceptance:
  - Migration replay to latest + rollback helper.
  - Seeded deterministic dev data only when DB empty and `__DEV__`.
  - No synchronous heavy DB calls in render paths.
- File ownership:
  - All files in `src/db/*` and `src/domain/*`.
- Required docs:
  - `docs/lifeos/expo-implementation-plan.md`
  - `docs/lifeos/product-pass.md`
  - `docs/lifeos/adr-0001-architecture.md`
- Input contracts:
  - `packages/domain-config/*`
  - `src/domain/runtime.ts` canonical record/conversation/receipt contracts
- Output contracts:
  - Deterministic row mappings
  - DB versioned export + rollback + seed evidence
- Mandatory report:
  - Files changed + checks run
  - Evidence + blockers
  - Assumptions + gaps

## Workstream B: Chat + conversations (`@/src/chat`, future `server/*`)
- Owner: B1
- Files owned:
  - `src/db/conversations.ts`
  - future `server/src/*` chat modules
- Forbidden:
  - No model or policy keys in Expo bundle.
- Outputs:
  - persisted conversation history + structured message envelopes.
- Gates:
  - idempotent write path and rollback of failed writes.
- Mandatory report:
  - Conversation table evidence and fallback checks with app UI handoff tests.

## Workstream C: Notion adapter
- Owner: C1
- Files owned:
  - placeholder in future `server/src/providers/notion/*`
- Forbidden:
  - no raw webhook payload-as-record use.

## Workstream D: Google Sheets adapter
- Owner: D1
- Files owned:
  - placeholder in future `server/src/providers/sheets/*`
- Forbidden:
  - never fetch without explicit scope and stale URL checks.

## Workstream E: MCP + action engine
- Owner: E1
- Files owned:
  - future `server/src/mcp/*`
  - `src/db/actions.ts`
  - `src/db/undo.ts`
- Output:
  - one typed contract per action/citation/undo row.

## Workstream F: Expo UI
- Owner: G1
- Files owned:
  - `app/*`
  - `src/components/*`
  - `src/data/*`
- Forbidden:
  - no hard-coded domain branching in tabs/screens beyond fallback demo states.
- Output:
  - screens consume domain/runtime repos and SQLite adapters.

## Workstream G: Android + Health Connect
- Owner: H1
- Files owned:
  - `android/*` future manifests and Gradle wrapper once added back.
- Blocked until runtime gates complete.
