import { describe, expect, it } from 'vitest';

import { loadCatalog } from '@/src/domain/catalog';
import { applyOperation } from '@/src/ops/apply';
import { undoOperation } from '@/src/ops/undo';
import { enqueueProviderWriteForOperation, type ProviderWritePayload } from '@/src/providers/writeback';
import { MemoryDb } from '../helpers/memory-db';

const manifest = loadCatalog().activeManifest;
const observedAt = '2026-07-23T00:00:00.000Z';

async function createRecord(db: MemoryDb, opId = 'writeback-create') {
  return applyOperation(db as any, manifest, {
    op_id: opId,
    kind: 'create',
    domain: manifest.id,
    collection: 'inventory',
    record_id: 'writeback-yogurt',
    record: {
      title: 'Writeback yogurt',
      properties: { body: 'Greek yogurt', quantity: 2 },
      relations: [],
      source: { provider: 'sqlite', external_id: 'writeback-yogurt', url: null, observed_at: observedAt, content_hash: null },
      archived_at: null,
    },
    actor: 'user',
    origin: 'manual',
  });
}

function payload(row: Record<string, unknown> | undefined) {
  expect(row).toBeTruthy();
  return JSON.parse(String(row!.payload_json)) as ProviderWritePayload;
}

describe('enqueueProviderWriteForOperation', () => {
  it('queues duplicate-safe Notion create payloads from applied operations', async () => {
    const db = new MemoryDb();
    await createRecord(db);

    const first = await enqueueProviderWriteForOperation({ db: db as any, provider: 'notion', opId: 'writeback-create' });
    const duplicate = await enqueueProviderWriteForOperation({ db: db as any, provider: 'notion', opId: 'writeback-create' });
    const outboxRow = Array.from(db.outbox.values())[0];
    const body = payload(outboxRow);

    expect(first.status).toBe('queued');
    expect(duplicate.status).toBe('duplicate');
    expect(db.outbox.size).toBe(1);
    expect(body.schema_version).toBe('lifeos.provider-write.v1');
    expect(body.provider).toBe('notion');
    expect(body.operation).toBe('create_record');
    expect(body.endpoint).toBe('/providers/notion/push');
    expect(body.record?.properties.quantity).toBe(2);
  });

  it('queues Sheets update payloads and queues Undo as the inverse update', async () => {
    const db = new MemoryDb();
    const created = await createRecord(db, 'writeback-undo-create');
    const updated = await applyOperation(db as any, manifest, {
      op_id: 'writeback-update',
      kind: 'update',
      domain: manifest.id,
      collection: 'inventory',
      record_id: 'writeback-yogurt',
      expected_revision: created.record!.revision,
      changes: { quantity: 4 },
      actor: 'user',
      origin: 'manual',
    });
    const updateQueued = await enqueueProviderWriteForOperation({ db: db as any, provider: 'google_sheets', opId: 'writeback-update' });
    const undone = await undoOperation(db as any, manifest, 'writeback-update');
    const undoQueued = await enqueueProviderWriteForOperation({ db: db as any, provider: 'google_sheets', opId: undone.op_id });
    const rows = Array.from(db.outbox.values()).map(payload);

    expect(updated.status).toBe('applied');
    expect(updateQueued.status).toBe('queued');
    expect(undone.status).toBe('applied');
    expect(undoQueued.status).toBe('queued');
    expect(rows).toHaveLength(2);
    expect(rows[0].operation).toBe('update_record');
    expect(rows[0].record?.properties.quantity).toBe(4);
    expect(rows[1].operation).toBe('update_record');
    expect(rows[1].record?.properties.quantity).toBe(2);
    expect(rows[1].endpoint).toBe('/providers/sheets/push');
  });

  it('queues archive provider payloads from archive operations', async () => {
    const db = new MemoryDb();
    const created = await createRecord(db, 'writeback-archive-create');
    await applyOperation(db as any, manifest, {
      op_id: 'writeback-archive',
      kind: 'archive',
      domain: manifest.id,
      collection: 'inventory',
      record_id: 'writeback-yogurt',
      expected_revision: created.record!.revision,
      actor: 'user',
      origin: 'manual',
    });

    const queued = await enqueueProviderWriteForOperation({ db: db as any, provider: 'notion', opId: 'writeback-archive' });
    const body = payload(Array.from(db.outbox.values())[0]);

    expect(queued.status).toBe('queued');
    expect(body.operation).toBe('archive_record');
    expect(body.record?.archived_at).toBeTruthy();
  });
});
