# W1-MCP Isolated official MCP SDK parity proof

## Scope
- Task: `W1-MCP Isolated official MCP SDK parity proof`
- Branch base: `main`
- Base SHA: `5f9b091c9667593f74e383554e59eb562211c4dd`
- Current SHA: `b9406364098906b334450bec0272bffd4d71e973`
- Current tree: `7e3572e20d5f744a3012f36ca7f2caa8f06453ba`
- Spike artifact SHA: `4ebbe8247edf0534d8bc4601150972d4ae00a6bd`
- Path scope: `spikes/mcp-sdk/**`

### Merge evidence
- Merge-base: `5f9b091c9667593f74e383554e59eb562211c4dd`
- Diff-tree (artifact commit): `spikes/mcp-sdk/REPORT.md`, `spikes/mcp-sdk/package-lock.json`, `spikes/mcp-sdk/package.json`, `spikes/mcp-sdk/src/server.ts`, `spikes/mcp-sdk/tests/parity.test.ts`, `spikes/mcp-sdk/tsconfig.json`

## Spike implementation
- Pin SDK: `@modelcontextprotocol/sdk@1.29.0`
- Server:
  - `McpServer`
  - `StreamableHTTPServerTransport` (stateless)
  - `registerTool` (read + mutation-like)
  - `registerResource`
  - `registerPrompt`
  - JSON response mode enabled
- Client:
  - official `Client`
  - official `StreamableHTTPClientTransport`
  - `initialize`, `listTools`, `listResources`, `listPrompts`, `callTool`
  - bearer header auth fixture

## Checks
- `npm --prefix spikes/mcp-sdk run typecheck` (PASS)
- `npm --prefix spikes/mcp-sdk test` (PASS)

## Behavioral proof
- Stateless Streamable HTTP path is implemented with `sessionIdGenerator: undefined`.
- `MCP` initialize/list/call sequence succeeds using official client transport and MCP protocol methods.
- Bearer auth rejection path returns `401` with `WWW-Authenticate: Bearer` before request parsing for unauthorized traffic.
- Authorized raw request receives `200` and `application/json` with streamable JSON response.
- Proposal-like mutation tool returns JSON payload `{reviewOnly: true, mode: 'proposal_only', ...}` and never mutates `McpServer` state.

## Diff-tree files
- `spikes/mcp-sdk/package.json`
- `spikes/mcp-sdk/package-lock.json`
- `spikes/mcp-sdk/tsconfig.json`
- `spikes/mcp-sdk/src/server.ts`
- `spikes/mcp-sdk/tests/parity.test.ts`
- `spikes/mcp-sdk/REPORT.md`

## Deletion inventory (production)
Target for next production-replacement task:
- `server/src/mcp/server.ts`
  - custom JSON-RPC parsing/dispatch:
    - `readJsonRequest`
    - `writeJson`
    - `writeSse`
    - message routing for `initialize`, `tools/list`, `resources/list`, `tools/call`, `resources/read`, `prompts/list`
  - request handling uses `handleMcpRequest`/`handleMcpRequestList`
- `server/src/mcp/protocol-compat.ts`
  - protocol-version compatibility helper, origin policy, custom negotiation
- `server/src/mcp/state.ts`
  - MCP-owned durable state and sessionful action/actionEvent machinery
- `server/src/mcp/auth.ts`
  - ad-hoc bearer validation helper

## Remaining scope
- Keep this spike read-only and isolated (`spikes/mcp-sdk` only).
- Production MCP replacement remains in later `P0-03`-class tasks with route/middleware migration in `server/src/index.ts`.
- No production imports/edits were made in this task.

## Blockers
- None for this isolated parity proof.
- Must not bypass Spark; if Spark unavailable, task blocked per policy.

## Merge risk
- Low: isolated spike, no production mutations.
- Merge conditions: all listed checks pass and no task policy conflict.
- Ready to merge: **TRUE**
