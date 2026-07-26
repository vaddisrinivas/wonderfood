import { authorizeMcpRequest, isMcpToolAuthorized, parseMcpScope, type HeaderMap, type McpScope } from './auth';
import { isMcpToolAllowed, isMcpToolReadOnly } from './policy';
import { listMcpResources, readMcpResource, resolveResourceMimeType } from './resources';
import { callMcpTool, listMcpTools } from './tools';
import { isAllowedMcpOrigin, isMcpProtocolVersion, negotiateMcpProtocolVersion } from './protocol-compat';
import { validateArgsForTool } from './server';

const MCP_SERVER_NAME = 'wonderfood-lifeos-server';
const MCP_SERVER_VERSION = '1.0.0';
const MCP_BODY_LIMIT_BYTES = 256 * 1024;
const GLOBAL_SCOPE_SAFE_URIS = new Set([
  'wonderfood://agent-registry-v1',
  'wonderfood://schema/command.v1',
  'wonderfood://schema/action-event.v1',
  'wonderfood://schema/undo-v1',
  'wonderfood://schema/workflow.v1',
  'wonderfood://schema/domain-catalog-v1',
  'wonderfood://schema/domain.v1',
  'wonderfood://schema/proposal-package-v1',
  'wonderfood://schema/command-envelope-v1',
  'wonderfood://contract/app-command',
]);

type JsonRpcRequest = {
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

class PayloadTooLargeError extends Error {
  readonly limitBytes: number;

  constructor(limitBytes: number) {
    super(`Request body too large. Limit is ${limitBytes} bytes.`);
    this.limitBytes = limitBytes;
  }
}

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

function writeJsonRpcError(res: any, status: number, code: number, message: string) {
  writeJson(res, { jsonrpc: '2.0', error: { code, message } }, status);
}

function wantsEventStream(acceptHeader: unknown): boolean {
  const normalized = String(acceptHeader || '').toLowerCase();
  return normalized.includes('text/event-stream') && !normalized.includes('application/json');
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

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function parseContentLength(headers: HeaderMap): number | null {
  const raw = headers['content-length'];
  const value = Array.isArray(raw) ? raw[0] : raw;
  if (value === undefined) {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : NaN;
}

async function readBoundedBody(req: any, maxBytes: number): Promise<string> {
  const contentLength = parseContentLength(req.headers ?? {});
  if (contentLength !== null) {
    if (!Number.isFinite(contentLength)) {
      throw new Error('Invalid Content-Length header');
    }
    if (contentLength > maxBytes) {
      throw new PayloadTooLargeError(maxBytes);
    }
  }

  const chunks: Buffer[] = [];
  let totalBytes = 0;
  for await (const chunk of req) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(String(chunk));
    totalBytes += buffer.byteLength;
    if (totalBytes > maxBytes) {
      req.destroy?.();
      throw new PayloadTooLargeError(maxBytes);
    }
    chunks.push(buffer);
  }
  return Buffer.concat(chunks).toString('utf-8');
}

async function readJsonRequest(req: any): Promise<JsonRpcRequest | JsonRpcRequest[]> {
  const raw = await readBoundedBody(req, MCP_BODY_LIMIT_BYTES);
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

function extractScopedDomain(value: unknown): string | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null;
  }
  const domain = (value as { domain?: unknown }).domain;
  return typeof domain === 'string' && domain.trim().length > 0 ? domain.trim().toLowerCase() : null;
}

function extractDomainFromUri(uri: string): string | null {
  const staticMatch = uri.match(/^wonderfood:\/\/(?:manifest\/|skill\/bundled-|domain\/|catalog\/domain\/)([^/]+)$/);
  if (staticMatch?.[1]) {
    return staticMatch[1].trim().toLowerCase();
  }
  return null;
}

function filterScopedJson(uri: string, text: string, scope: McpScope): string {
  if (scope.domains.size === 0) {
    return text;
  }

  if (uri === 'wonderfood://domain-catalog') {
    throw new Error(`Resource not readable for scoped domains: ${uri}`);
  }

  if (uri === 'wonderfood://records') {
    const payload = JSON.parse(text) as { records?: unknown[] };
    const records = Array.isArray(payload.records)
      ? payload.records.filter((record) => {
          const domain = extractScopedDomain(record);
          return domain !== null && scope.domains.has(domain);
        })
      : [];
    return JSON.stringify({ ...payload, count: records.length, records }, null, 2);
  }

  if (uri === 'wonderfood://actions') {
    const payload = JSON.parse(text) as { events?: unknown[] };
    const events = Array.isArray(payload.events)
      ? payload.events.filter((event) => {
          const domain = extractScopedDomain(event);
          return domain !== null && scope.domains.has(domain);
        })
      : [];
    return JSON.stringify({ ...payload, events }, null, 2);
  }

  if (uri === 'wonderfood://workflows') {
    const payload = JSON.parse(text) as { workflows?: unknown[] };
    const workflows = Array.isArray(payload.workflows)
      ? payload.workflows.filter((workflow) => {
          const domain = extractScopedDomain(workflow);
          return domain !== null && scope.domains.has(domain);
        })
      : [];
    return JSON.stringify({ ...payload, workflows }, null, 2);
  }

  if (uri === 'wonderfood://conversations') {
    const payload = JSON.parse(text) as { threads?: unknown[] };
    const threads = Array.isArray(payload.threads)
      ? payload.threads.filter((thread) => {
          const domain = extractScopedDomain(thread);
          return domain !== null && scope.domains.has(domain);
        })
      : [];
    return JSON.stringify({ ...payload, threads }, null, 2);
  }

  if (
    uri.startsWith('wonderfood://record/')
    || uri.startsWith('wonderfood://action/')
    || uri.startsWith('wonderfood://workflow/')
  ) {
    const payload = JSON.parse(text) as unknown;
    const domain = extractScopedDomain(payload);
    if (!domain || !scope.domains.has(domain)) {
      throw new Error(`Resource not readable for scoped domains: ${uri}`);
    }
    return text;
  }

  const explicitDomain = extractDomainFromUri(uri);
  if (explicitDomain && !scope.domains.has(explicitDomain)) {
    throw new Error(`Resource not readable for scoped domains: ${uri}`);
  }

  return text;
}

function canReadScopedResource(uri: string, scope: McpScope): boolean {
  if (scope.domains.size === 0) {
    return true;
  }

  if (GLOBAL_SCOPE_SAFE_URIS.has(uri)) {
    return true;
  }

  const explicitDomain = extractDomainFromUri(uri);
  if (explicitDomain) {
    return scope.domains.has(explicitDomain);
  }

  if (uri === 'wonderfood://domain-catalog') {
    return false;
  }

  try {
    filterScopedJson(uri, readMcpResource(uri), scope);
    return true;
  } catch {
    return false;
  }
}

function readScopedResource(uri: string, scope: McpScope): string {
  if (!canReadScopedResource(uri, scope)) {
    throw new Error(`Resource not readable for scoped domains: ${uri}`);
  }
  return filterScopedJson(uri, readMcpResource(uri), scope);
}

async function responseFor(request: JsonRpcRequest, headers: HeaderMap): Promise<McpResponse> {
  const scope = parseMcpScope(headers);
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
      result: { resources: listMcpResources().filter((resource) => canReadScopedResource(resource.uri, scope)) },
    };
  }

  if (method === 'resources/read') {
    const uri = typeof params.uri === 'string' ? params.uri : '';
    if (!uri) {
      return { jsonrpc: '2.0', id: requestId, error: { code: -32602, message: 'uri is required' } };
    }
    try {
      const text = readScopedResource(uri, scope);
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
    writeJsonRpcError(res, 405, -405, 'Method not allowed. Use POST.');
    return true;
  }

  const origin = Array.isArray(req.headers?.origin) ? req.headers.origin[0] : req.headers?.origin;
  const configuredOrigins = String(process.env.LIFEOS_MCP_ALLOWED_ORIGINS ?? '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
  if (!isAllowedMcpOrigin(origin, configuredOrigins)) {
    writeJsonRpcError(res, 403, -32002, 'Origin not allowed');
    return true;
  }

  const protocolHeader = Array.isArray(req.headers?.['mcp-protocol-version'])
    ? req.headers['mcp-protocol-version'][0]
    : req.headers?.['mcp-protocol-version'];
  if (protocolHeader !== undefined && !isMcpProtocolVersion(protocolHeader)) {
    writeJsonRpcError(res, 400, -32602, 'Unsupported MCP-Protocol-Version');
    return true;
  }

  const auth = authorizeMcpRequest(req.headers ?? {});
  if (!auth.ok) {
    writeJsonRpcError(
      res,
      auth.statusCode,
      auth.statusCode === 503 ? -32003 : -32001,
      auth.message,
    );
    return true;
  }

  try {
    const body = await readJsonRequest(req);
    const acceptsStream = wantsEventStream(req.headers?.accept);

    if (Array.isArray(body)) {
      const responses = await Promise.all(body.map((entry) => responseFor(entry, req.headers ?? {})));
      if (acceptsStream) {
        writeSse(res, responses);
      } else {
        writeJson(res, responses);
      }
      return true;
    }

    const response = await responseFor(body, req.headers ?? {});
    if (!response.result && response.error === undefined) {
      writeJson(res, {}, 202);
      return true;
    }
    if (acceptsStream) {
      writeSse(res, [response]);
      return true;
    }
    writeJson(res, response, response.error ? 400 : 200);
    return true;
  } catch (error) {
    if (error instanceof PayloadTooLargeError) {
      writeJsonRpcError(res, 413, -32004, error.message);
      return true;
    }
    if (error instanceof Error && error.message === 'Invalid Content-Length header') {
      writeJsonRpcError(res, 400, -32600, error.message);
      return true;
    }
    writeJsonRpcError(res, 400, -32700, (error as Error).message);
    return true;
  }
}
