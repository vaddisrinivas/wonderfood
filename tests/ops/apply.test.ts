import { describe, expect, it } from 'vitest';

import { loadCatalog } from '@/src/domain/catalog';
import { applyOperation } from '@/src/ops/apply';
import { MemoryDb } from '../helpers/memory-db';

const manifest = loadCatalog().activeManifest;

describe('applyOperation', () => {
  it('applies creates, records one ledger row, and deduplicates idempotency keys', async () => {
    const db = new MemoryDb() as any;
    const first = await applyOperation(db, manifest, {
      op_id: 'create-yogurt',
      kind: 'create',
      domain: manifest.id,
      collection: 'inventory',
      record_id: 'test-yogurt',
      record: {
        title: 'Test yogurt',
        properties: { body: 'Greek yogurt', quantity: 2 },
        relations: [],
        source: { provider: 'sqlite', external_id: 'test-yogurt', url: null, observed_at: '2026-07-23T00:00:00.000Z', content_hash: null },
        archived_at: null,
      },
      actor: 'user',
      origin: 'manual',
      idempotency_key: 'idem-create-yogurt',
    });
    const duplicate = await applyOperation(db, manifest, {
      op_id: 'create-yogurt-again',
      kind: 'create',
      domain: manifest.id,
      collection: 'inventory',
      record_id: 'test-yogurt',
      record: {
        title: 'Different title',
        properties: { body: 'Should not write' },
        relations: [],
        source: { provider: 'sqlite', external_id: 'test-yogurt', url: null, observed_at: '2026-07-23T00:00:00.000Z', content_hash: null },
        archived_at: null,
      },
      actor: 'user',
      origin: 'manual',
      idempotency_key: 'idem-create-yogurt',
    });

    expect(first.status).toBe('applied');
    expect(first.record?.revision).toBe(1);
    expect(duplicate.status).toBe('duplicate');
    expect(db.records.get('test-yogurt')?.title).toBe('Test yogurt');
    expect(db.operations.size).toBe(1);
  });

  it('rejects stale revision without changing the record', async () => {
    const db = new MemoryDb() as any;
    await applyOperation(db, manifest, {
      op_id: 'create-dal',
      kind: 'create',
      domain: manifest.id,
      collection: 'meal_plan',
      record_id: 'test-dal',
      record: {
        title: 'Test dal',
        properties: { body: 'Original', quantity: 1 },
        relations: [],
        source: { provider: 'sqlite', external_id: 'test-dal', url: null, observed_at: '2026-07-23T00:00:00.000Z', content_hash: null },
        archived_at: null,
      },
      actor: 'user',
      origin: 'manual',
    });
    await applyOperation(db, manifest, {
      op_id: 'update-dal',
      kind: 'update',
      domain: manifest.id,
      collection: 'meal_plan',
      record_id: 'test-dal',
      expected_revision: 1,
      changes: { body: 'Updated once' },
      actor: 'user',
      origin: 'manual',
    });
    const stale = await applyOperation(db, manifest, {
      op_id: 'stale-dal',
      kind: 'update',
      domain: manifest.id,
      collection: 'meal_plan',
      record_id: 'test-dal',
      expected_revision: 1,
      changes: { body: 'Stale overwrite' },
      actor: 'user',
      origin: 'manual',
    });

    expect(stale.status).toBe('rejected');
    expect(stale.reject_reason).toBe('revision_conflict');
    expect(JSON.parse(db.records.get('test-dal')?.properties).body).toBe('Updated once');
  });

  it('rejects invalid records without partial record writes', async () => {
    const db = new MemoryDb() as any;
    const rejected = await applyOperation(db, manifest, {
      op_id: 'bad-collection',
      kind: 'create',
      domain: manifest.id,
      collection: 'not_a_collection',
      record_id: 'bad-record',
      record: {
        title: 'Bad',
        properties: {},
        relations: [],
        source: { provider: 'sqlite', external_id: 'bad-record', url: null, observed_at: '2026-07-23T00:00:00.000Z', content_hash: null },
        archived_at: null,
      },
      actor: 'user',
      origin: 'manual',
    });

    expect(rejected.status).toBe('rejected');
    expect(db.records.has('bad-record')).toBe(false);
    expect(db.operations.get('bad-collection')?.status).toBe('rejected');
  });
});
