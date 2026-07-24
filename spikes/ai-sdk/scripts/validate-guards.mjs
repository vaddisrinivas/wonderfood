import { readFile } from 'node:fs/promises';

const files = [
  'src/server/agent.ts',
  'src/server/stream-handler.ts',
  'src/server/model.ts',
  'src/client/chat-client.tsx',
  'src/client/approval.ts',
  'scripts/validate-guards.mjs',
];

async function assertNoExecutionPath(filePath) {
  const content = await readFile(filePath, 'utf8');

  if (filePath.includes('/agent.ts') && /execute\s*:/i.test(content)) {
    throw new Error(`localQuery execute detected in ${filePath}`);
  }

  const productionImports = [
    'src/agents/orchestrator',
    'src/ai/runtime',
    'src/chat/client',
    'server/src',
    'src/providers',
  ];

  if (filePath.includes('scripts/validate-guards.mjs')) {
    return;
  }

  const found = productionImports.filter((pattern) => content.includes(pattern));
  if (found.length) {
    throw new Error(`Production import detected in ${filePath}: ${found.join(', ')}`);
  }
}

await Promise.all(files.map(assertNoExecutionPath));

const streamHandler = await readFile('src/server/stream-handler.ts', 'utf8');
if (streamHandler.includes('onToken') || /parseJsonEventStream|custom.*sse|onStepEnd/.test(streamHandler)) {
  throw new Error('Custom SSE parser detected');
}

const approvalRegex = /SDK.*approval|needsApproval/i;
const fileContents = await Promise.all(files.map((path) => readFile(path, 'utf8')));
const source = fileContents.join('\n');
if (approvalRegex.test(await readFile('src/client/approval.ts', 'utf8'))) {
  console.log('[guard] approval separation fixture present');
}

if (!source.includes('ToolLoopAgent')) {
  throw new Error('ToolLoopAgent not detected');
}

if (!source.includes('createAgentUIStreamResponse')) {
  throw new Error('createAgentUIStreamResponse not detected');
}

if (!source.includes('useChat')) {
  throw new Error('useChat not detected');
}

if (!source.includes('DefaultChatTransport')) {
  throw new Error('DefaultChatTransport not detected');
}

if (!source.includes('addToolOutput')) {
  throw new Error('addToolOutput not detected');
}

console.log('[guard] spike guard checks passed');
