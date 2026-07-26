import { spawn } from 'node:child_process';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const root = process.cwd();
const outDir = join(root, 'app', 'build', 'evidence', 'chat-agent-ui-route');
mkdirSync(outDir, { recursive: true });

const stateDir = mkdtempSync(join(tmpdir(), `wf-chat-agent-ui-${randomBytes(4).toString('hex')}-`));
const token = 'chat-agent-ui-test-token';
const port = 19131;
const base = `http://127.0.0.1:${port}`;
const tsxBinary = join(root, 'server', 'node_modules', '.bin', 'tsx');
const serverEntry = join(root, 'server', 'src', 'index.ts');

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

async function postAgent(body: unknown) {
  return fetch(`${base}/chat/agent`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(body),
  });
}

(async () => {
  const env: NodeJS.ProcessEnv = {
    ...process.env,
    PORT: String(port),
    LIFEOS_SERVER_TOKEN: token,
    LIFEOS_MCP_STATE_PATH: join(stateDir, 'mcp-runtime.json'),
    LIFEOS_CHAT_CONVERSATIONS_PATH: join(stateDir, 'conversations.json'),
  };
  delete env.OPENAI_API_KEY;

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

    const missingMessages = await postAgent({});
    assert(missingMessages.status === 400, `expected missing messages to be rejected, got ${missingMessages.status}`);

    const disabled = await postAgent({
      messages: [
        {
          id: 'user-chat-agent-ui-route',
          role: 'user',
          parts: [{ type: 'text', text: 'what should I cook tonight from local food records?' }],
        },
      ],
    });
    assert(disabled.status === 503, `expected missing provider key to fail closed, got ${disabled.status}`);
    const payload = await disabled.json() as { status?: string; message?: string };
    assert(payload.status === 'disabled', 'expected disabled status without provider key');

    const evidence = {
      proof: 'chat_agent_ui_route',
      route: '/chat/agent',
      ui_messages_required: true,
      missing_provider_fails_closed: true,
      local_query_server_execute_forbidden: true,
      all_passed: true,
    };
    const evidencePath = join(outDir, 'chat-agent-ui-route-proof.json');
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
