# ADR-0001: Production shell architecture for LifeOS Expo replacement

## Status
Accepted for Expo baseline; remaining gate items in Phase 3+.

## Context
- Android Native Kotlin implementation was replaced by an Expo shell.
- Product contracts require static web + Android-first delivery with shared canonical semantics and runtime parity.
- No secrets or provider credentials may ship in Expo.

## Decision
1. Keep Expo web output static (`expo export --platform web --output-dir dist/web`).
2. Persist a local canonical graph in Expo SQLite with atomic writes, deterministic IDs, and typed validation before write.
3. Keep all secrets, OAuth, providers, AI model calls, webhooks, MCP and action policy in a separate server package (`server/`).
4. Route all live chat through OpenAI Responses + Conversations; no second chat history in the app.
5. Enforce one authoritative provider per household; Notion remains the current canonical first home.
6. Use one shared catalog/schema/skill/tool contract across app/chat/MCP.

## Server host decision
- Pending explicit confirmation from infra. `server/` contract package and deployment target remain an open action item for Phase 3.

## Consequences
- Expo code only contains UI, local canonical store operations, and transport adapters to server endpoints.
- Database migration and seed process must be deterministic in dev and recoverable by schema dump (`exportRecoverySnapshot`).
- Any provider-specific field not in canonical schema is preserved in source snapshots only.
