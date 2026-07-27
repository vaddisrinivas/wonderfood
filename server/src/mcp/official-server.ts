import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { authorizeMcpRequest, type HeaderMap } from '../security/auth';
import { createWonderMcpSdkServer } from './sdk-server';

const MCP_BODY_LIMIT_BYTES = 256 * 1024;
const LOCAL_ORIGINS = new Set(['http://localhost', 'http://127.0.0.1', 'http://[::1]']);

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

function writeJsonRpcError(res: any, status: number, code: number, message: string) {
  writeJson(res, { jsonrpc: '2.0', error: { code, message } }, status);
}

function firstHeaderValue(headers: HeaderMap, name: string): string | undefined {
  const raw = headers[name];
  if (Array.isArray(raw)) {
    return typeof raw[0] === 'string' ? raw[0] : undefined;
  }
  return typeof raw === 'string' ? raw : undefined;
}

function isAllowedOrigin(origin: unknown, configured: readonly string[] = []): boolean {
  if (origin === undefined || origin === null || origin === '') return true;
  if (typeof origin !== 'string') return false;
  const allowed = configured.length > 0 ? configured : [...LOCAL_ORIGINS];
  return allowed.includes(origin);
}

function parseContentLength(headers: HeaderMap): number | null {
  const value = firstHeaderValue(headers, 'content-length');
  if (value === undefined) {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : NaN;
}

async function readBoundedJson(req: any, maxBytes: number): Promise<unknown> {
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

  const raw = Buffer.concat(chunks).toString('utf-8');
  if (!raw.trim()) {
    throw new Error('Empty request body');
  }
  return JSON.parse(raw) as unknown;
}

function normalizeLegacyAcceptHeader(req: any) {
  const accept = firstHeaderValue(req.headers ?? {}, 'accept');
  if (accept === undefined || accept.trim() === '' || accept.trim().toLowerCase() === 'application/json') {
    req.headers.accept = 'application/json, text/event-stream';
    if (Array.isArray(req.rawHeaders)) {
      const index = req.rawHeaders.findIndex((entry: unknown) => String(entry).toLowerCase() === 'accept');
      if (index >= 0 && index + 1 < req.rawHeaders.length) {
        req.rawHeaders[index + 1] = 'application/json, text/event-stream';
      } else {
        req.rawHeaders.push('accept', 'application/json, text/event-stream');
      }
    }
  }
}

export async function handleMcpRequest(req: any, res: any): Promise<boolean> {
  if (!req.url?.startsWith('/mcp')) {
    return false;
  }

  const origin = firstHeaderValue(req.headers ?? {}, 'origin');
  const configuredOrigins = String(process.env.LIFEOS_MCP_ALLOWED_ORIGINS ?? '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
  if (!isAllowedOrigin(origin, configuredOrigins)) {
    writeJsonRpcError(res, 403, -32002, 'Origin not allowed');
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
    normalizeLegacyAcceptHeader(req);
    const parsedBody = req.method === 'POST'
      ? await readBoundedJson(req, MCP_BODY_LIMIT_BYTES)
      : undefined;
    const scope = auth.mcpScope ?? {
      domains: new Set<string>(),
      principal: auth.principalId,
      allowAllDomains: false,
    };
    const server = createWonderMcpSdkServer(scope);
    const transport = new StreamableHTTPServerTransport({
      sessionIdGenerator: undefined,
      enableJsonResponse: true,
    });
    await server.connect(transport);
    await transport.handleRequest(req, res, parsedBody);
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
