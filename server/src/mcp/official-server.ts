import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import {
  CallToolRequestSchema,
  ErrorCode,
  ListResourcesRequestSchema,
  ListToolsRequestSchema,
  McpError,
  ReadResourceRequestSchema,
} from '@modelcontextprotocol/sdk/types.js';
import { isMcpToolAuthorized } from './auth';
import { isMcpToolAllowed, isMcpToolReadOnly } from './policy';
import { listMcpResources, readMcpResource, resolveResourceMimeType } from './resources';
import { callMcpTool, listMcpTools } from './tools';
import { isAllowedMcpOrigin, isMcpProtocolVersion } from './protocol-compat';
import { validateArgsForTool } from './server';

const MCP_SERVER_NAME = 'wonderfood-lifeos-server';
const MCP_SERVER_VERSION = '1.0.0';

type HeaderMap = Record<string, string | string[] | undefined>;

function writeJson(res: any, payload: unknown, status = 200) {
  res.writeHead(status, { 'content-type': 'application/json' });
  res.end(JSON.stringify(payload));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function createOfficialMcpServer(headers: HeaderMap) {
  const server = new Server(
    { name: MCP_SERVER_NAME, version: MCP_SERVER_VERSION },
    { capabilities: { tools: {}, resources: {} } },
  );

  server.setRequestHandler(ListToolsRequestSchema, async () => ({
    tools: listMcpTools() as never,
  }));

  server.setRequestHandler(ListResourcesRequestSchema, async () => ({
    resources: listMcpResources(),
  }));

  server.setRequestHandler(ReadResourceRequestSchema, async (request) => {
    const uri = request.params.uri;
    return {
      contents: [{
        uri,
        mimeType: resolveResourceMimeType(uri),
        text: readMcpResource(uri),
      }],
    };
  });

  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const toolName = request.params.name;
    if (!isMcpToolAllowed(toolName)) {
      throw new McpError(ErrorCode.MethodNotFound, `Unknown tool: ${toolName}`);
    }
    const args = request.params.arguments ?? {};
    const validationErrors = validateArgsForTool(toolName, args);
    if (validationErrors.length > 0) {
      throw new McpError(ErrorCode.InvalidParams, `Invalid arguments: ${validationErrors.join('; ')}`);
    }
    if (!isMcpToolReadOnly(toolName) && !isMcpToolAuthorized(headers)) {
      throw new McpError(ErrorCode.InvalidRequest, 'Unauthorized');
    }
    const result = await callMcpTool(toolName, isRecord(args) ? args : {});
    return {
      content: [{
        type: 'text',
        text: JSON.stringify(result.json),
      }],
    };
  });

  return server;
}

export async function handleMcpRequest(req: any, res: any): Promise<boolean> {
  if (!req.url?.startsWith('/mcp')) return false;
  if (req.method !== 'POST') {
    writeJson(res, { jsonrpc: '2.0', error: { code: -405, message: 'Method not allowed. Use POST.' } }, 405);
    return true;
  }

  const origin = Array.isArray(req.headers?.origin) ? req.headers.origin[0] : req.headers?.origin;
  const configuredOrigins = String(process.env.LIFEOS_MCP_ALLOWED_ORIGINS ?? '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
  if (!isAllowedMcpOrigin(origin, configuredOrigins)) {
    writeJson(res, { jsonrpc: '2.0', error: { code: -32002, message: 'Origin not allowed' } }, 403);
    return true;
  }

  const protocolHeader = Array.isArray(req.headers?.['mcp-protocol-version'])
    ? req.headers['mcp-protocol-version'][0]
    : req.headers?.['mcp-protocol-version'];
  if (protocolHeader !== undefined && !isMcpProtocolVersion(protocolHeader)) {
    writeJson(res, { jsonrpc: '2.0', error: { code: -32602, message: 'Unsupported MCP-Protocol-Version' } }, 400);
    return true;
  }

  const transport = new StreamableHTTPServerTransport({
    sessionIdGenerator: undefined,
    enableJsonResponse: true,
  });
  const server = createOfficialMcpServer(req.headers ?? {});
  try {
    await server.connect(transport);
    await transport.handleRequest(req, res);
    return true;
  } finally {
    await server.close().catch(() => undefined);
  }
}
