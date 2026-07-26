import { spawn } from 'node:child_process';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const root = process.cwd();
const outDir = join(root, 'app', 'build', 'evidence', 'package-builder-api');
mkdirSync(outDir, { recursive: true });

const stateDir = mkdtempSync(join(tmpdir(), `wf-package-builder-api-${randomBytes(4).toString('hex')}-`));
const token = 'package-builder-api-test-token';
const port = 19132;
const base = `http://127.0.0.1:${port}`;
const tsxBinary = join(root, 'server', 'node_modules', '.bin', 'tsx');
const serverEntry = join(root, 'server', 'src', 'index.ts');

const pkg = {
  schemaVersion: 'wonder.app-package.v2',
  id: 'demo-builder',
  version: '1.0.0',
  collections: {
    ideas: { id: 'ideas', fields: { title: { type: 'text', required: true }, state: { type: 'text' } } },
  },
  queries: {
    all: { from: 'ideas', orderBy: [{ field: 'title', direction: 'asc' }] },
  },
  views: {
    home: { id: 'home', query: 'all', mode: 'list', fields: ['title', 'state'] },
  },
  presentation: {
    label: 'Demo Builder',
    homeSurface: 'home',
    surfaces: [{ id: 'home', label: 'Home', collections: ['ideas'] }],
  },
  rules: [],
  capabilities: [],
  acceptanceTests: ['package-builder-api'],
};

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

async function waitForServerReady(): Promise<void> {
  for (let i = 0; i < 120; i += 1) {
    try {
      const response = await fetch(`${base}/health`, { method: 'GET' });
      if (response.ok) return;
    } catch {
      // retry
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`server did not become ready at ${base}/health`);
}

async function request(path: string, method: 'GET' | 'POST', body?: unknown) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: {
      'content-type': 'application/json',
      authorization: `Bearer ${token}`,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  const parsed = text ? JSON.parse(text) as Record<string, any> : {};
  return { response, parsed };
}

(async () => {
  const env: NodeJS.ProcessEnv = {
    ...process.env,
    PORT: String(port),
    LIFEOS_SERVER_TOKEN: token,
    LIFEOS_MCP_STATE_PATH: join(stateDir, 'mcp-runtime.json'),
    LIFEOS_CHAT_CONVERSATIONS_PATH: join(stateDir, 'conversations.json'),
    LIFEOS_PACKAGE_REGISTRY_PATH: join(stateDir, 'package-registry.json'),
    LIFEOS_REACTIVE_RUNTIME_PATH: join(stateDir, 'reactive-runtime.json'),
  };

  const server = spawn(tsxBinary, ['--tsconfig', join(root, 'tsconfig.json'), serverEntry], {
    cwd: root,
    env,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let serverStderr = '';
  server.stderr.on('data', (chunk) => {
    serverStderr += String(chunk);
  });

  try {
    await waitForServerReady();

    const activeBefore = await request('/packages/active', 'GET');
    assert(activeBefore.response.ok, `active package endpoint failed: ${activeBefore.response.status} ${JSON.stringify(activeBefore.parsed)}`);
    assert(activeBefore.parsed.active?.id === 'food', 'server should bootstrap manifest package');

    const invalid = await request('/packages/preview', 'POST', {
      package: {
        ...pkg,
        presentation: { label: 'Bad', surfaces: [{ id: 'bad', label: 'Bad', collections: ['ghosts'] }] },
      },
    });
    assert(invalid.response.ok, 'invalid preview should return 200 with invalid status');
    assert(invalid.parsed.status === 'invalid', 'bad package should preview invalid');

    const preview = await request('/packages/preview', 'POST', { package: pkg });
    assert(preview.response.ok, 'valid preview failed');
    assert(preview.parsed.status === 'valid', 'valid package should preview valid');

    const activated = await request('/packages/activate', 'POST', { package: pkg });
    assert(activated.response.ok, `activation failed: ${JSON.stringify(activated.parsed)}`);
    assert(activated.parsed.status === 'activated', 'package should activate');
    assert(activated.parsed.active?.id === 'demo-builder', 'activated package id mismatch');
    assert(activated.parsed.receipt?.action === 'activate', 'activation receipt missing');

    const activeAfter = await request('/packages/active', 'GET');
    assert(activeAfter.parsed.active?.id === 'demo-builder', 'active package not persisted');

    const rolledBack = await request('/packages/rollback', 'POST');
    assert(rolledBack.response.ok, 'rollback failed');
    assert(rolledBack.parsed.status === 'rolled_back', 'rollback status mismatch');
    assert(rolledBack.parsed.active?.id === 'food', 'rollback should restore bootstrapped food package');

    const evidence = {
      proof: 'package_builder_api',
      active_bootstrap: activeBefore.parsed.active?.id,
      invalid_preview_rejected: true,
      activation_receipt_action: activated.parsed.receipt?.action,
      rollback_active: rolledBack.parsed.active?.id,
      all_passed: true,
    };
    const evidencePath = join(outDir, 'package-builder-api-proof.json');
    writeFileSync(evidencePath, JSON.stringify(evidence, null, 2), 'utf8');
    console.log(`PASS ${evidencePath}`);
  } finally {
    server.kill('SIGTERM');
    rmSync(stateDir, { recursive: true, force: true });
  }

  if (serverStderr.includes('SyntaxError') || serverStderr.includes('Unhandled')) {
    throw new Error(`server stderr contained startup/runtime error: ${serverStderr.slice(0, 500)}`);
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
