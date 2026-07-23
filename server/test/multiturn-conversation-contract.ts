import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

function ensure(condition: boolean, message: string) {
  if (!condition) throw new Error(message);
}

(async () => {
  const tempDir = mkdtempSync(join(tmpdir(), 'lifeos-multiturn-'));
  const storagePath = join(tempDir, 'conversations.json');
  process.env.LIFEOS_CHAT_CONVERSATIONS_PATH = storagePath;

  try {
    const conversations = await import('../src/conversations');
    const { buildConversationContext } = await import('../src/chat');
    const conversation = conversations.ensureConversation('multiturn-contract', 'food', 'Follow-up test');
    conversations.appendServerMessage(conversation.id, {
      id: 'user-1',
      role: 'user',
      text: 'What can I make with yogurt?',
    });
    conversations.appendServerMessage(conversation.id, {
      id: 'assistant-1',
      role: 'assistant',
      text: 'Here is the answer.',
      answer: {
        title: 'Yogurt ideas',
        intro: 'Make a breakfast bowl.',
        rows: [{ meal: 'Breakfast bowl', use: 'Yogurt and fruit', next: 'Use the yogurt today' }],
        citations: [{ label: 'Pantry yogurt', detail: 'notion', href: 'wonderfood://notion/yogurt', tone: 'moss' }],
      },
    });
    conversations.setConversationResponseId(conversation.id, 'resp-persisted-1');

    const loaded = conversations.getConversation(conversation.id);
    ensure(loaded?.last_response_id === 'resp-persisted-1', 'Expected response pointer in loaded conversation');
    const context = buildConversationContext(loaded);
    ensure(context.includes('USER: What can I make with yogurt?'), 'Expected prior user turn in model context');
    ensure(context.includes('Make a breakfast bowl.'), 'Expected structured assistant answer in model context');
    ensure(context.includes('Sources: Pantry yogurt'), 'Expected prior source handle in model context');

    const persisted = JSON.parse(readFileSync(storagePath, 'utf-8')) as {
      conversations?: Array<{ id?: string; last_response_id?: string }>;
    };
    ensure(persisted.conversations?.some((row) => row.id === conversation.id && row.last_response_id === 'resp-persisted-1') === true, 'Expected response pointer on disk for restart recovery');

    console.log('PASS server/test/multiturn-conversation-contract.ts');
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
})().catch((error) => {
  process.exitCode = 1;
  throw error;
});
