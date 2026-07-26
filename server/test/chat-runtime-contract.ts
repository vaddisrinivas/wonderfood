import assert from 'node:assert/strict';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

process.env.LIFEOS_MCP_STATE_PATH = join(mkdtempSync(join(tmpdir(), 'wonderfood-chat-runtime-')), 'mcp-runtime.json');

const { runChatRuntime } = await import('../src/chat-runtime');

const result = await runChatRuntime({
  conversationId: 'chat-runtime-contract',
  domain: 'food',
  message: 'What should I cook tonight?',
  actor: 'hearth',
  preview: true,
});

assert.equal(result.status, 'ok');
assert.deepEqual(result.roles, [{ role: 'chat_runtime', status: 'ok' }]);
assert.equal(result.action, undefined, 'preview read should not write');
assert.equal(result.retrieval.domain, 'food');
assert.ok(result.runId.startsWith('chat:'));

console.log('chat-runtime-contract: passed');
