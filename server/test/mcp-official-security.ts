import { createServer } from 'node:http';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const port = 8904;
const base = `http://127.0.0.1:${port}`;
const token = 'mcp-official-security-token';
const stateDir = mkdtempSync(join(tmpdir(), 'wonderfood-mcp-security-'));

process.env.LIFEOS_MCP_STATE_PATH = join(stateDir, 'mcp-runtime.json');
delete process.env.LIFEOS_LOCAL_DEV;
delete process.env.LIFEOS_SERVER_TOKEN;
delete process.env.LIFEOS_MCP_TOKEN;

const { handleMcpRequest } = await import('../src/mcp/official-server');

function ensure(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(message);
  }
}

async function readJson(response: Response) {
  return (await response.json()) as {
    jsonrpc?: string;
    result?: Record<string, unknown>;
    error?: { code?: number; message?: string };
  };
}

async function postMcp(body: unknown, headers: Record<string, string> = {}) {
  return fetch(`${base}/mcp`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      accept: 'application/json',
      ...headers,
    },
    body: JSON.stringify(body),
  });
}

const httpServer = createServer(async (req, res) => {
  if (req.url?.startsWith('/mcp')) {
    await handleMcpRequest(req, res);
    return;
  }
  res.writeHead(404, { 'content-type': 'application/json' });
  res.end(JSON.stringify({ error: 'not_found' }));
});

await new Promise<void>((resolve, reject) => {
  httpServer.listen(port, '127.0.0.1', () => resolve());
  httpServer.on('error', reject);
});

try {
  const initializeBody = {
    jsonrpc: '2.0',
    id: 1,
    method: 'initialize',
    params: {
      protocolVersion: '2026-03-11',
      capabilities: {},
      clientInfo: { name: 'mcp-security-test', version: '1' },
    },
  };

  const failClosed = await postMcp(initializeBody);
  const failClosedBody = await readJson(failClosed);
  ensure(failClosed.status === 503, `official MCP should fail closed without configured token, got ${failClosed.status}`);
  ensure(String(failClosedBody.error?.message).includes('not configured'), 'MCP fail-closed response should explain missing token');

  process.env.LIFEOS_SERVER_TOKEN = token;

  const missingToken = await postMcp(initializeBody);
  const missingTokenBody = await readJson(missingToken);
  ensure(missingToken.status === 401, `official MCP should reject missing bearer token, got ${missingToken.status}`);
  ensure(String(missingTokenBody.error?.message).includes('Missing mcp bearer token'), 'missing MCP token response should be explicit');

  const wrongToken = await postMcp(initializeBody, { authorization: 'Bearer wrong-token' });
  const wrongTokenBody = await readJson(wrongToken);
  ensure(wrongToken.status === 401, `official MCP should reject wrong bearer token, got ${wrongToken.status}`);
  ensure(String(wrongTokenBody.error?.message).includes('Invalid mcp bearer token'), 'wrong MCP token response should be explicit');

  const initialize = await postMcp(initializeBody, { authorization: `Bearer ${token}` });
  const initializeResult = await readJson(initialize);
  ensure(initialize.status === 200, `authorized initialize should succeed, got ${initialize.status}`);
  ensure(typeof initializeResult.result?.protocolVersion === 'string', 'authorized initialize should return protocolVersion');

  const scopedList = await postMcp(
    {
      jsonrpc: '2.0',
      id: 2,
      method: 'resources/list',
      params: {},
    },
    {
      authorization: `Bearer ${token}`,
      'x-lifeos-domain-scope': 'food',
    },
  );
  const scopedListBody = await readJson(scopedList);
  const scopedResources = Array.isArray(scopedListBody.result?.resources)
    ? scopedListBody.result?.resources as Array<{ uri?: unknown }>
    : [];
  const scopedUris = scopedResources.map((resource) => String(resource.uri));
  ensure(scopedList.status === 200, `scoped resources/list should succeed, got ${scopedList.status}`);
  ensure(scopedUris.includes('wonderfood://manifest/food'), 'food scope should retain food manifest');
  ensure(!scopedUris.includes('wonderfood://manifest/health'), 'food scope should hide health manifest');
  ensure(!scopedUris.includes('wonderfood://domain/health'), 'food scope should hide health domain resource');

  const scopedRead = await postMcp(
    {
      jsonrpc: '2.0',
      id: 3,
      method: 'resources/read',
      params: { uri: 'wonderfood://manifest/health' },
    },
    {
      authorization: `Bearer ${token}`,
      'x-lifeos-domain-scope': 'food',
    },
  );
  const scopedReadBody = await readJson(scopedRead);
  ensure(
    scopedRead.status === 400 || scopedRead.status === 200,
    `scoped resources/read should return JSON-RPC error envelope, got HTTP ${scopedRead.status}`,
  );
  ensure(Boolean(scopedReadBody.error), 'cross-tenant resources/read should fail');
  ensure(String(scopedReadBody.error?.message).includes('not readable'), 'cross-tenant resources/read should explain scope denial');

  const oversizedMcp = await fetch(`${base}/mcp`, {
    method: 'POST',
    headers: {
      authorization: `Bearer ${token}`,
      'content-type': 'application/json',
      accept: 'application/json',
    },
    body: JSON.stringify({
      jsonrpc: '2.0',
      id: 4,
      method: 'initialize',
      params: {
        protocolVersion: '2026-03-11',
        blob: 'x'.repeat((260 * 1024)),
      },
    }),
  });
  const oversizedMcpBody = await readJson(oversizedMcp);
  ensure(oversizedMcp.status === 413, `official MCP should reject oversized body, got ${oversizedMcp.status}`);
  ensure(String(oversizedMcpBody.error?.message).includes('Limit is'), 'oversized MCP response should mention the byte limit');

  console.log('PASS server/test/mcp-official-security.ts');
} finally {
  await new Promise<void>((resolve) => httpServer.close(() => resolve()));
  rmSync(stateDir, { recursive: true, force: true });
}
