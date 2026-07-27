import { authorizeMcpRequest, type HeaderMap, type McpScope } from '../security/auth';
import { isMcpToolAllowed } from '../security/policy';
import { describeMcpResourceAuthorization, listMcpResources, readMcpResource, resolveResourceMimeType } from './resources';
import { callMcpTool, listMcpTools } from './tools';
import { isAllowedMcpOrigin, isMcpProtocolVersion, negotiateMcpProtocolVersion } from './protocol-compat';
import { validateArgsForTool } from '../tools/tool-validation';
import { findRecord, findWorkflow, getActionEvent } from './state';
import { listConversations } from '../conversations';

const MCP_SERVER_NAME = 'wonderfood-lifeos-server';
const MCP_SERVER_VERSION = '1.0.0';
const MCP_BODY_LIMIT_BYTES = 256 * 1024;

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

function hasDomainAccess(scope: McpScope, domain: string): boolean {
  return scope.allowAllDomains || scope.domains.has(domain);
}

function filterScopedJson(uri: string, text: string, scope: McpScope): string {
  if (scope.allowAllDomains || scope.domains.size === 0) {
    return text;
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
    uri === 'wonderfood://domain-catalog'
    || uri === 'wonderfood://lifeos/domain-catalog-v1'
  ) {
    const payload = JSON.parse(text) as {
      active_domain_id?: unknown;
      domains?: Array<{ id?: unknown }>;
      shell?: { tabs?: unknown[] } & Record<string, unknown>;
    } & Record<string, unknown>;
    const allDomainIds = new Set(
      Array.isArray(payload.domains)
        ? payload.domains
            .map((entry) => typeof entry.id === 'string' ? entry.id.trim().toLowerCase() : '')
            .filter(Boolean)
        : [],
    );
    const domains = Array.isArray(payload.domains)
      ? payload.domains.filter((entry) => {
          const domain = typeof entry.id === 'string' ? entry.id.trim().toLowerCase() : '';
          return domain.length > 0 && scope.domains.has(domain);
        })
      : [];
    const activeDomainId = typeof payload.active_domain_id === 'string'
      && scope.domains.has(payload.active_domain_id.trim().toLowerCase())
      ? payload.active_domain_id
      : typeof domains[0]?.id === 'string' ? domains[0].id : null;
    const shell = payload.shell && typeof payload.shell === 'object'
      ? {
          ...payload.shell,
          tabs: Array.isArray(payload.shell.tabs)
            ? payload.shell.tabs.filter((tab) => {
                const normalized = typeof tab === 'string' ? tab.trim().toLowerCase() : '';
                return !allDomainIds.has(normalized) || scope.domains.has(normalized);
              })
            : payload.shell.tabs,
        }
      : payload.shell;
    return JSON.stringify({ ...payload, active_domain_id: activeDomainId, shell, domains }, null, 2);
  }

  if (uri === 'wonderfood://agent-registry-v1') {
    const payload = JSON.parse(text) as {
      agents?: Array<{
        domains?: unknown[];
        capabilities?: Array<Record<string, unknown>>;
      } & Record<string, unknown>>;
    } & Record<string, unknown>;
    const agents = Array.isArray(payload.agents)
      ? payload.agents.map((agent) => ({
          ...agent,
          domains: Array.isArray(agent.domains)
            ? [...new Set(agent.domains.flatMap((domain) => {
                if (domain === '*') return [...scope.domains];
                const normalized = typeof domain === 'string' ? domain.trim().toLowerCase() : '';
                return normalized && scope.domains.has(normalized) ? [normalized] : [];
              }))]
            : [],
          capabilities: Array.isArray(agent.capabilities)
            ? agent.capabilities.flatMap((capability) => {
                const domain = typeof capability.domain === 'string'
                  ? capability.domain.trim().toLowerCase()
                  : '';
                if (domain === '*') {
                  return [...scope.domains].map((allowedDomain) => ({ ...capability, domain: allowedDomain }));
                }
                return domain && scope.domains.has(domain) ? [{ ...capability, domain }] : [];
              })
            : [],
        }))
      : [];
    return JSON.stringify({ ...payload, agents }, null, 2);
  }

  return text;
}

function canReadScopedResource(uri: string, scope: McpScope): boolean {
  try {
    const access = describeMcpResourceAuthorization(uri);
    if (access.kind === 'safe-global') {
      return true;
    }
    if (access.kind === 'global-index') {
      return scope.allowAllDomains || scope.domains.size > 0;
    }
    return hasDomainAccess(scope, access.domain);
  } catch {
    return false;
  }
}

function readScopedResource(uri: string, scope: McpScope): string {
  if (!canReadScopedResource(uri, scope)) {
    throw new Error(`Resource not readable for scoped domains: ${uri}`);
  }
  const text = uri === 'wonderfood://conversations'
    ? JSON.stringify({ type: 'conversation-index', threads: listConversations(scope.principal ?? undefined) }, null, 2)
    : readMcpResource(uri);
  return filterScopedJson(uri, text, scope);
}

type ToolScopeDecision =
  | { ok: true }
  | { ok: false; message: string };

function normalizedDomain(value: unknown): string | null {
  return typeof value === 'string' && value.trim().length > 0
    ? value.trim().toLowerCase()
    : null;
}

function authorizeToolDomain(scope: McpScope, domain: string | null, toolName: string): ToolScopeDecision {
  if (!domain) {
    return { ok: true };
  }
  return hasDomainAccess(scope, domain)
    ? { ok: true }
    : { ok: false, message: `Tool ${toolName} target is not authorized for the trusted MCP domain scope` };
}

function authorizeScopedToolCall(toolName: string, args: Record<string, unknown>, scope: McpScope): ToolScopeDecision {
  if (toolName === 'wonderfood.get_resource') {
    const uri = typeof args.uri === 'string' ? args.uri : '';
    return uri && canReadScopedResource(uri, scope)
      ? { ok: true }
      : { ok: false, message: `Tool ${toolName} is not authorized for resource ${uri || '<missing>'}` };
  }

  if (toolName === 'wonderfood.search_records' || toolName === 'wonderfood.create_record') {
    return authorizeToolDomain(scope, normalizedDomain(args.domain), toolName);
  }

  if (
    toolName === 'wonderfood.read_record'
    || toolName === 'wonderfood.update_record'
    || toolName === 'wonderfood.archive_record'
  ) {
    const id = typeof args.id === 'string' ? args.id.trim() : '';
    const record = id ? findRecord(id) : null;
    const actual = record ? normalizedDomain(record.domain) : null;
    const claimed = normalizedDomain(args.domain);
    const actualDecision = authorizeToolDomain(scope, actual, toolName);
    if (!actualDecision.ok) return actualDecision;
    return authorizeToolDomain(scope, claimed, toolName);
  }

  if (toolName === 'wonderfood.run_workflow') {
    const workflowId = typeof args.workflow === 'string' ? args.workflow.trim() : '';
    const workflow = workflowId ? findWorkflow(workflowId) : null;
    const actualDecision = authorizeToolDomain(scope, normalizedDomain(workflow?.domain), toolName);
    if (!actualDecision.ok) return actualDecision;
    return authorizeToolDomain(scope, normalizedDomain(args.domain), toolName);
  }

  if (toolName === 'wonderfood.undo_action') {
    const actionId = typeof args.actionId === 'string' ? args.actionId.trim() : '';
    const action = actionId ? getActionEvent(actionId) : null;
    return authorizeToolDomain(scope, normalizedDomain(action?.domain), toolName);
  }

  return { ok: true };
}

const PRINCIPAL_BOUND_TOOLS = new Set([
  'wonderfood.propose_app_link',
  'wonderfood.create_record',
  'wonderfood.update_record',
  'wonderfood.archive_record',
  'wonderfood.run_workflow',
  'wonderfood.undo_action',
]);

function bindTrustedPrincipal(
  toolName: string,
  args: Record<string, unknown>,
  scope: McpScope,
): Record<string, unknown> {
  if (!PRINCIPAL_BOUND_TOOLS.has(toolName) || !scope.principal) {
    return args;
  }
  return { ...args, actor: scope.principal };
}

async function responseFor(request: JsonRpcRequest, scope: McpScope): Promise<McpResponse> {
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

    const typedArgs = isRecord(args) ? args : {};
    const scopeDecision = authorizeScopedToolCall(toolName, typedArgs, scope);
    if (!scopeDecision.ok) {
      return {
        jsonrpc: '2.0',
        id: requestId,
        error: { code: -32001, message: scopeDecision.message },
      };
    }

    try {
      if (toolName === 'wonderfood.get_resource') {
        const uri = String(typedArgs.uri);
        return {
          jsonrpc: '2.0',
          id: requestId,
          result: wrapToolResult({ uri, text: readScopedResource(uri, scope) }),
        };
      }
      const result = await callMcpTool(toolName, bindTrustedPrincipal(toolName, typedArgs, scope));
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
  const scope = auth.mcpScope ?? { domains: new Set(), principal: auth.principalId, allowAllDomains: false };

  try {
    const body = await readJsonRequest(req);
    const acceptsStream = wantsEventStream(req.headers?.accept);

    if (Array.isArray(body)) {
      const responses = await Promise.all(body.map((entry) => responseFor(entry, scope)));
      if (acceptsStream) {
        writeSse(res, responses);
      } else {
        writeJson(res, responses);
      }
      return true;
    }

    const response = await responseFor(body, scope);
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
