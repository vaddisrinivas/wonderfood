import { SQLiteDatabase } from 'expo-sqlite';

import { loadCatalog } from '@/src/domain/catalog';
import { upsertRecord } from '@/src/db/records';
import { createConversation, appendMessage } from '@/src/db/conversations';
import { createActionEvent } from '@/src/db/actions';
import { createUndoEvent } from '@/src/db/undo';
import { foodRecords } from '@/src/data/sample';

type SeedOptions = {
  seedInDev?: boolean;
};

export async function seedDatabase(db: SQLiteDatabase, options: SeedOptions = {}): Promise<void> {
  const catalog = loadCatalog();
  if (!options.seedInDev || !__DEV__) {
    return;
  }

  const existing = await db.getFirstAsync<{ total: number }>('SELECT COUNT(*) as total FROM records');
  if (existing?.total && existing.total > 0) return;

  const createdAt = new Date().toISOString();

  for (const sample of foodRecords) {
    const manifest = catalog.activeManifest;
    await upsertRecord(
      db,
      manifest,
      {
        id: sample.id,
        title: sample.title,
        collection: sample.meta.includes('Recipe') || sample.meta.includes('Dining')
          ? 'recipe'
          : sample.meta.includes('Shopping')
            ? 'shopping_item'
            : 'inventory',
        properties: {
          status: sample.status,
          tone: sample.tone,
          meta: sample.meta,
          body: sample.body,
          source: sample.source,
        },
        source: {
          provider: 'notion',
          external_id: `sample-${sample.id}`,
          url: null,
          observed_at: createdAt,
          content_hash: null,
        },
        archived_at: null,
        created_at: createdAt,
        updated_at: createdAt,
      }
    );
  }

  const threadId = 'thread-demo';
  const conversation = await createConversation(db, {
    id: threadId,
    domain: catalog.activeDomainId,
    title: 'Tonight’s dinner',
    detail: 'Food context demo',
  });
  await appendMessage(db, {
    id: `${threadId}-welcome`,
    conversation_id: conversation.id,
    role: 'assistant',
    sort_index: 0,
    body: 'I can help with kitchen-aware meal planning. Ask about pantry timing, dinner plans, and shopping.'
  });

  const actionId = 'seed-proof-action';
  await createActionEvent(db, {
    id: actionId,
    domain: catalog.activeDomainId,
    conversation_id: conversation.id,
    actor: 'system',
    tool: 'seed',
    record_ids: [threadId],
  });
  await createUndoEvent(db, {
    id: 'seed-proof-undo',
    action_id: actionId,
    payload: { label: 'seed-action', canUndo: true },
    expires_at: null,
  });
}
