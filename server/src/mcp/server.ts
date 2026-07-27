// Compatibility exports. The official SDK-compatible transport is the only
// MCP request path; keeping a second JSON-RPC implementation would duplicate
// authentication and scope enforcement.
export { handleMcpRequest } from './official-server';
export { validateArgsForTool } from '../tools/tool-validation';
