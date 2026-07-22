import { callMcpTool, listMcpTools } from './tools';
import { listMcpResources, readMcpResource, resolveResourceMimeType } from './resources';
import { isMcpToolAllowed, isMcpToolReadOnly } from './policy';
import { isMcpToolAuthorized } from './auth';

const MCP_PROTOCOL_VERSION = '2026-03-11';
const MCP_SERVER_NAME = 'wonderfood-lifeos-server';
const MCP_SERVER_VERSION = '1.0.0';

export type JsonRpcRequest = {
  jsonrpc?: string;
  id?: string | number | null;
  method?: string;
  params?: Record<string, unknown>;
};

type McpResponse = {
  jsonrpc: '2.0';
  id: string | number | null;
  result?: Record<string, unknown> | unknown;
  error?: {
    code: number;
    message: string;
  };
};

type HeaderMap = Record<string, string | string[] | undefined>;

function writeJson(res: any, payload: unknown, status = 200) {
  res.writeHead(status, {
    'content-type': 'application/json',
  });
  res.end(JSON.stringify(payload));
}

function writeSse(res: any, payloads: McpResponse[]) {
  res.writeHead(200, {
    'content-type': 'text/event-stream',
    'cache-control': 'no-cache',
    connection: 'keep-alive',
  });
  for (const payload of payloads) {
    res.write(`data: ${JSON.stringify(payload)}\n\n`);
  }
  res.end();
}

function wrapToolResult(result: unknown) {
  return {
    content: [
      {
        type: 'text',
        text: JSON.stringify(result),
      },
    ],
  };
}

async function readJsonRequest(req: any): Promise<JsonRpcRequest | JsonRpcRequest[]> {
  const chunks: string[] = [];
  for await (const chunk of req) {
    chunks.push(typeof chunk === 'string' ? chunk : chunk.toString());
  }
  const raw = chunks.join('');
  if (!raw.trim()) {
    throw new Error('Empty request body');
  }
  const payload = JSON.parse(raw) as unknown;
  if (Array.isArray(payload)) {
    return payload as JsonRpcRequest[];
  }
  if (payload && typeof payload === 'object') {
    return payload as JsonRpcRequest;
  }
  throw new Error('Invalid JSON-RPC payload');
}

function responseFor(request: JsonRpcRequest, headers: HeaderMap): McpResponse {
  const requestId = request.id ?? null;
  const method = request.method;
  const params = request.params ?? {};

  if (typeof method !== 'string' || !method) {
    return { jsonrpc: '2.0', id: requestId, error: { code: -32600, message: 'Invalid request' } };
  }

  if (method === 'notifications/initialized') {
    return { jsonrpc: '2.0', id: requestId };
  }

  if (method === 'initialize') {
    return {
      jsonrpc: '2.0',
      id: requestId,
      result: {
        protocolVersion: MCP_PROTOCOL_VERSION,
        capabilities: { tools: {}, resources: {} },
        serverInfo: { name: MCP_SERVER_NAME, version: MCP_SERVER_VERSION },
      },
    };
  }

  if (method === 'tools/list') {
    return {
      jsonrpc: '2.0',
      id: requestId,
      result: { tools: listMcpTools() },
    };
  }

  if (method === 'resources/list') {
    return {
      jsonrpc: '2.0',
      id: requestId,
      result: { resources: listMcpResources() },
    };
  }

  if (method === 'resources/read') {
    const uri = typeof params.uri === 'string' ? params.uri : '';
    if (!uri) {
      return { jsonrpc: '2.0', id: requestId, error: { code: -32602, message: 'uri is required' } };
    }
    try {
      const text = readMcpResource(uri);
      return {
        jsonrpc: '2.0',
        id: requestId,
        result: {
          contents: [
            {
              uri,
              mimeType: resolveResourceMimeType(uri),
              text,
            },
          ],
        },
      };
    } catch (error) {
      return {
        jsonrpc: '2.0',
        id: requestId,
        error: { code: -32602, message: (error as Error).message },
      };
    }
  }

  if (method === 'tools/call') {
    const toolName = typeof params.name === 'string' ? params.name : '';
    if (!toolName) {
      return { jsonrpc: '2.0', id: requestId, error: { code: -32602, message: 'name is required' } };
    }
    if (!isMcpToolAllowed(toolName)) {
      return { jsonrpc: '2.0', id: requestId, error: { code: -32601, message: `Unknown tool: ${toolName}` } };
    }
    if (!isMcpToolReadOnly(toolName) && !isMcpToolAuthorized(headers)) {
      return { jsonrpc: '2.0', id: requestId, error: { code: -32001, message: 'Unauthorized' } };
    }

    try {
      const args = (params.arguments as Record<string, unknown>) ?? {};
      const result = callMcpTool(toolName, args);
      return {
        jsonrpc: '2.0',
        id: requestId,
        result: wrapToolResult(result.json),
      };
    } catch (error) {
      return { jsonrpc: '2.0', id: requestId, error: { code: -32602, message: (error as Error).message } };
    }
  }

  return { jsonrpc: '2.0', id: requestId, error: { code: -32601, message: `Method not found: ${method}` } };
}

export async function handleMcpRequest(req: any, res: any): Promise<boolean> {
  if (!req.url?.startsWith('/mcp')) {
    return false;
  }

  if (req.method !== 'POST') {
    writeJson(
      res,
      { jsonrpc: '2.0', error: { code: -405, message: 'Method not allowed. Use POST.' } },
      405,
    );
    return true;
  }

  try {
    const body = await readJsonRequest(req);
    const acceptsStream = String(req.headers?.accept || '').includes('text/event-stream');

    if (Array.isArray(body)) {
      const responses = body.map((entry) => responseFor(entry, req.headers)).filter((response) => response !== null);
      if (acceptsStream) {
        writeSse(res, responses);
      } else {
        writeJson(res, responses);
      }
      return true;
    }

    const response = responseFor(body, req.headers);
    if (!response.result && response.error === undefined) {
      return writeJson(res, {}, 202), true;
    }
    if (acceptsStream) {
      writeSse(res, [response]);
      return true;
    }
    writeJson(res, response, response.error ? 400 : 200);
    return true;
  } catch (error) {
    writeJson(
      res,
      { jsonrpc: '2.0', error: { code: -32700, message: (error as Error).message } },
      400,
    );
    return true;
  }
}
