# LifeOS MCP contracts

## MCP JSON-RPC request shape

- URL: `/mcp` (POST)
- Envelope: `{ jsonrpc: '2.0', id: <number|string>, method: 'initialize' | 'tools/list' | 'tools/call', params: {...} }`
- `tools/call` input: `{ name: 'tool_name', arguments: { ... } }`
- `id` is echoed on all JSON-RPC responses; response and replay requests must keep idempotent `action_id` + `idempotency_key`.

## Error behavior

- Unknown tools/methods return JSON-RPC errors with `code -32601` (method/tool not found) or `-32602` (invalid params) and no tool payload.
- JSON decode/type failures return non-200 only for transport failures; protocol errors stay in JSON-RPC envelope.

## Tool result envelope

Every `tools/call` response payload is a deterministic object (`tool result`) and may be wrapped in SSE for streamable HTTP.

### Common read/write outputs

- `reviewOnly` (boolean)
- `safety` (enum)
- `json` (tool payload)
- Optional write envelope fields:
  - `undo_token` (string, equals action id)
  - `receipts[]`
  - `review_flags`
  - `source_snapshot`
- `undo_token` should be present for all successful write/undo completions and equal `json.action.id`.
- `review_flags` must be present for all write/undo completions and include:
  - `policy_reviewed`
  - `replay_recoverable`
  - `cancellation_safe`

### Required write fields for create/update/archive/run_workflow/undo

- `reviewOnly: false`
- `safety: 'write'` (unless provider is unconfigured/restricted)
- `json.action.id` (stable when `action_id` is provided)
- `undo_token === json.action.id`
- `receipts[0].action_id === json.action.id`
- `receipts[0].undo_token === json.action.id`
- `review_flags.policy_reviewed === true`
- `review_flags.replay_recoverable` boolean
- `review_flags.cancellation_safe === true`
- `source_snapshot` includes canonical provenance and provider context

### Replay behavior

- Idempotent calls using same `idempotency_key` + `action_id` return `json.replayed === true`.
- Replay keeps `json.action.id` stable.
- Replay returns same deterministic `source_snapshot`.
- `undo_action` replay must return a complete `undo_result` with `undoResult.success === true` and deterministic replay semantics.

### Provider mismatch behavior

- Unconfigured provider paths return:
  - `reviewOnly: true`
  - `json.allowed === false`
  - `requiredConfig` list (`NOTION_TOKEN`, `NOTION_DATA_SOURCE_ID`, `GOOGLE_SHEETS_ACCESS_TOKEN`, `GOOGLE_SHEETS_SPREADSHEET_ID`)
  - `json.source_snapshot.provider` set to requested provider

### Policy behavior

- Blocked policy operations return:
  - `reviewOnly: true`
  - `json.allowed === false`
  - `json.policy` + reason/clarification fields

### MCP HTTP transport

- Streamable HTTP is required on the same `/mcp` endpoint when client sends `Accept: text/event-stream`.
- SSE should include `data:` JSON envelopes for tool/resource responses.
- `methods` and `tools/call` must work in both JSON and stream modes.
- `initialize`, `tools/list`, `tools/call`, and `notifications/initialized` are required in both transport modes.
