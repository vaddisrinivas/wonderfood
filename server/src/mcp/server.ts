import { callMcpTool, listMcpTools } from './tools';
import { listMcpResources, readMcpResource, resolveResourceMimeType } from './resources';
import { isMcpToolAllowed, isMcpToolReadOnly } from './policy';
import { isMcpToolAuthorized } from './auth';
import { isAllowedMcpOrigin, isMcpProtocolVersion, negotiateMcpProtocolVersion } from './protocol-compat';

const MCP_SERVER_NAME = 'wonderfood-lifeos-server';
const MCP_SERVER_VERSION = '1.0.0';

type JsonSchema = {
  type?: string;
  additionalProperties?: boolean;
  required?: string[];
  properties?: Record<string, JsonSchema>;
  enum?: unknown[];
  minLength?: number;
  maxLength?: number;
  minItems?: number;
  maxItems?: number;
  items?: JsonSchema;
};

type McpToolDefinition = {
  name: string;
  description: string;
  inputSchema: JsonSchema;
};

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

type ToolCallErrors = string[];

type ToolArgs = Record<string, unknown>;

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

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function normalizeToolDefinitionList(): Map<string, McpToolDefinition> {
  const tools = listMcpTools();
  const byName = new Map<string, McpToolDefinition>();
  for (const tool of tools) {
    byName.set(tool.name, tool);
  }
  return byName;
}

function asPath(path: string, key: string) {
  return path ? `${path}.${key}` : key;
}

function validateToolArguments(
  input: unknown,
  schema: JsonSchema,
  path = '',
  errors: ToolCallErrors,
): void {
  const currentType = schema.type;
  if (currentType === 'object') {
    if (!isRecord(input)) {
      errors.push(`${path || 'arguments'} must be an object`);
      return;
    }

    const value = input as ToolArgs;
    const properties = schema.properties ?? {};
    const required = schema.required ?? [];

    for (const key of required) {
      if (!(key in value)) {
        errors.push(`${asPath(path, key)} is required`);
      }
    }

    if (schema.additionalProperties === false) {
      for (const key of Object.keys(value)) {
        if (!(key in properties)) {
          errors.push(`unexpected property ${path ? `${path}.` : ''}${key}`);
        }
      }
    }

    for (const [key, childSchema] of Object.entries(properties)) {
      if (!(key in value)) {
        continue;
      }
      validateToolArguments((value as ToolArgs)[key], childSchema, asPath(path, key), errors);
    }
    return;
  }

  if (currentType === 'string') {
    if (typeof input !== 'string') {
      errors.push(`${path || 'arguments'} must be a string`);
      return;
    }
    if (typeof schema.minLength === 'number' && input.length < schema.minLength) {
      errors.push(`${path || 'arguments'} must be at least ${schema.minLength} chars`);
    }
    if (typeof schema.maxLength === 'number' && input.length > schema.maxLength) {
      errors.push(`${path || 'arguments'} must be at most ${schema.maxLength} chars`);
    }
    return;
  }

  if (currentType === 'number') {
    if (typeof input !== 'number' || Number.isNaN(input)) {
      errors.push(`${path || 'arguments'} must be a number`);
    }
    return;
  }

  if (currentType === 'boolean') {
    if (typeof input !== 'boolean') {
      errors.push(`${path || 'arguments'} must be a boolean`);
    }
    return;
  }

  if (currentType === 'array') {
    if (!Array.isArray(input)) {
      errors.push(`${path || 'arguments'} must be an array`);
      return;
    }
    if (typeof schema.minItems === 'number' && input.length < schema.minItems) {
      errors.push(`${path || 'arguments'} must include at least ${schema.minItems} items`);
    }
    if (typeof schema.maxItems === 'number' && input.length > schema.maxItems) {
      errors.push(`${path || 'arguments'} must include at most ${schema.maxItems} items`);
    }
    if (schema.items) {
      input.forEach((entry, index) => {
        validateToolArguments(entry, schema.items!, asPath(`${path || 'arguments'}`, String(index)), errors);
      });
    }
    return;
  }

  if (schema.enum && Array.isArray(schema.enum) && input !== undefined) {
    if (!schema.enum.includes(input)) {
      errors.push(`${path || 'arguments'} must be one of ${schema.enum.join(', ')}`);
    }
  }
}

function validateArgsForTool(toolName: string, args: unknown): ToolCallErrors {
  const tools = normalizeToolDefinitionList();
  const tool = tools.get(toolName);
  if (!tool) {
    return ['tool definition not found'];
  }

  const errors: ToolCallErrors = [];
  validateToolArguments(args, tool.inputSchema, '', errors);
  return errors;
}

async function responseFor(request: JsonRpcRequest, headers: HeaderMap): Promise<McpResponse> {
  const requestId = request.id ?? null;
  const method = request.method;
  const params = request.params && typeof request.params === 'object' && !Array.isArray(request.params)
    ? request.params
    : {};

  if (request.jsonrpc !== '2.0' || typeof method !== 'string' || !method) {
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
        protocolVersion: negotiateMcpProtocolVersion(params.protocolVersion),
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

    const args = params.arguments ?? {};
    const validationErrors = validateArgsForTool(toolName, args);
    if (validationErrors.length > 0) {
      return {
        jsonrpc: '2.0',
        id: requestId,
        error: {
          code: -32602,
          message: `Invalid arguments: ${validationErrors.join('; ')}`,
        },
      };
    }

    if (!isMcpToolReadOnly(toolName) && !isMcpToolAuthorized(headers)) {
      return { jsonrpc: '2.0', id: requestId, error: { code: -32001, message: 'Unauthorized' } };
    }

    try {
      const result = await callMcpTool(toolName, isRecord(args) ? args : {});
      return {
        jsonrpc: '2.0',
        id: requestId,
        result: wrapToolResult(result.json),
      };
    } catch (error) {
      return { jsonrpc: '2.0', id: requestId, error: { code: -32603, message: (error as Error).message } };
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

  try {
    const body = await readJsonRequest(req);
    const acceptsStream = String(req.headers?.accept || '').includes('text/event-stream');

    if (Array.isArray(body)) {
      const responses = await Promise.all(body.map((entry) => responseFor(entry, req.headers)));
      if (acceptsStream) {
        writeSse(res, responses);
      } else {
        writeJson(res, responses);
      }
      return true;
    }

    const response = await responseFor(body, req.headers);
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
