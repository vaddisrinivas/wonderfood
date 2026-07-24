# P0-01R Baseline Current

## Baseline snapshot
- Task: `P0-01R Commit current baseline and deletion map`
- Timestamp (UTC): 2026-07-24T21:43:14Z
- Base branch: `main`
- Working branch: `orchestry/tsk_33mRJlQ/p0-01r-commit-current-baseline-and-delet`
- HEAD: `71af6d27389eb5ceea591d803e4f5f1b369667c2`
- Commit short: `71af6d2`
- Commit tree: `0bd718c58c88d01ab50bc64b26405becea986ea7`
- Working tree: clean (`git status --short` empty)
- Diff-to-base: none in working tree at capture time

## Versions
- Node: `v26.5.0`
- npm: `11.17.0`
- Expo CLI: `57.0.10`
- App package: `wonderfood-lifeos@1.0.0`
- Server package: `wonderfood-lifeos-server@0.1.0`
- Expo SDK: `~57.0.8`
- React: `19.2.3`
- Lockfile hashes:
  - `package-lock.json`: `3d8d91bab08ba674de94005f63ea095c814f1bc762ede1c5c0e6c93ba5b9d9f4`
  - `server/package-lock.json`: `99c0917acfc7a388ebc224cf3abf372dc29352b650bb19459a35fccb73e4a056`

## Dependency runtime state
- `node_modules`: missing
- `server/node_modules`: missing
- `./server/node_modules/.bin/tsx`: missing
- `./node_modules/.bin/tsx`: missing
- Checks were still runnable through existing toolchain (global `expo`, `tsx` in command context) where applicable.

## Stores and filesystem state
### SQLite/db
- Canonical migration file: `src/db/migrations.ts`
- `DATABASE_VERSION = 6`
- Managed tables include `records`, `record_relations`, `outbox_events`, `operations`.

### Production-style persistence paths (current)
- `server/src/mcp/state.ts`
  - `MCP_STATE_PATH` default: `${process.cwd()}/server-data/mcp-runtime.json`
- `server/src/kernel/install-reactive-runtime.ts`
  - `reactive-receipts.json`
  - `reactive-runtime.json`
  - `package-registry.json`
  - all under `server-data/`
- `server/src/health/snapshots.ts`
  - `server-data/health-connect-snapshots.json`
- `server/src/providers/webhooks/notion.ts`
  - `server-data/notion-webhook-replay.json`
- `server/src/providers/webhooks/sheets.ts`
  - `server-data/sheets-webhook-replay.json`

## Routes
### Expo routes (`app/`)
- `app/(tabs)/_layout.tsx`
- `app/(tabs)/chat.tsx`
- `app/(tabs)/food.tsx`
- `app/(tabs)/index.tsx`
- `app/(tabs)/settings.tsx`
- `app/(tabs)/sources.tsx`
- `app/+html.tsx`
- `app/+not-found.tsx`
- `app/_layout.tsx`
- `app/capture.tsx`
- `app/collection/[id].tsx`
- `app/config.tsx`
- `app/health-diagnostics.tsx`
- `app/record/[id].tsx`
- `app/search.tsx`
- `app/system.tsx`

### Server routes (`server/src/index.ts`)
- `/mcp` (prefix)
- `/health`
- `/providers/status`
- `/health/connect` family (`snapshots`, `export`, `snapshot`)
- `/providers/notion` family (`webhook`, `discovery`, `pull`, `push`)
- `/providers/sheets` family (`webhook`, `health`, `pull`, `push`, `sync`)
- `/chat/threads` (list)
- `/chat/threads/:id` (read)
- `/chat/run`
- `/chat/send`
- `/chat/send/stream`
- `/chat/stop`
- `/chat/retry`
- `/chat/action`
- `/chat/undo`

## Production imports
- `server/src/index.ts` imports: `createServer`, `handleServerChat`, `handleMcpRequest`, `ProviderOperation`, `discoverNotionDataSources`, `readNotionConfig`, `pullNotionRecords*`, `normalize*Webhook*`, `writeNotionRecord`, `checkSheetsHealth`, `readSheetsConfig`, `writeSheetsRecord`, `pullSheetsRecords*`, `syncSheetsFromWebhook`, `syncNotionFromWebhook`, chat persistence (`ensureConversation`, `appendServerMessage`, `upsertConversation`, `listConversations`, `getConversation`, `setConversationResponseId`), `ChatStreamEvent`, `getActionEvent`, `runUndo`, `installReactiveRuntime`, health snapshot helpers.
- `server/src/index.ts` directly composes production endpoints and runs `installReactiveRuntime()` at boot.
- `server/src/mcp/state.ts` is imported by `server/src/agents` (`command-processor`, `executor`, `retrieval`, `verifier`) and `server/src/mcp`/`server/src/kernel` modules.

## Deletion map status
- Legacy filesystem JSON state usage remains in production paths listed under Stores; these are **active dependencies** and should be included in replacement work, not yet removed.
- Custom/protocol boundary replacement is therefore still in-progress and not yet finalized.

## Exact checks
| Command | Status | Failure reason |
|---|---|---|
| `npm run config:validate` | PASS | -- |
| `npm run typecheck` | PASS | -- |
| `npm run doctor` | PASS | -- |
| `npm run export:web` | PASS | -- |
| `npm run export:android` | PASS | -- |
| `npm run phase3:check:chat-undo` | **FAIL** | `./server/node_modules/.bin/tsx: No such file or directory` |
| `npm run phase3:check:chat-send` | **FAIL** | `./server/node_modules/.bin/tsx: No such file or directory` |
| `npm run phase3:check:chat-rollback-idempotency` | **FAIL** | `./server/node_modules/.bin/tsx: No such file or directory` |

Missing server dependencies are recorded; no repair/fix attempted in this task.
