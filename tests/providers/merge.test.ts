import { describe, expect, it } from 'vitest';

import { loadCatalog } from '@/src/domain/catalog';
import type { CanonicalRecord } from '@/src/domain/runtime';
import { upsertRecord, getRecord } from '@/src/db/records';
import { mergeRemoteRecord } from '@/src/providers/merge';
import { MemoryDb } from '../helpers/memory-db';

const manifest = loadCatalog().activeManifest;
const now = '2026-07-23T00:00:00.000Z';

function clone(record: CanonicalRecord): CanonicalRecord {
  return JSON.parse(JSON.stringify(record)) as CanonicalRecord;
}

describe('mergeRemoteRecord', () => {
  it('auto-applies disjoint remote changes through operations', async () => {
    const db = new MemoryDb() as any;
    const base = await upsertRecord(db, manifest, {
      id: 'merge-test',
      title: 'Merge test',
      collection: 'inventory',
      properties: { body: 'Base', aisle: 'Dairy', quantity: 2 },
      relations: [],
      source: { provider: 'sqlite', external_id: 'merge-test', url: null, observed_at: now, content_hash: null },
      archived_at: null,
      created_at: now,
      updated_at: now,
    });
    const local = await upsertRecord(db, manifest, { ...base, properties: { ...base.properties, body: 'Local body' } });
    const remote = clone(base);
    remote.properties = { ...remote.properties, aisle: 'Cold case' };
    remote.source = { provider: 'notion', external_id: 'merge-page', url: 'https://notion.so/merge-page', observed_at: now, content_hash: 'remote-1' };

    const result = await mergeRemoteRecord({ db, manifest, provider: 'notion', externalId: 'merge-page', base, local, remote });
    const saved = await getRecord(db, 'merge-test');

    expect(result.status).toBe('applied');
    expect(saved?.properties.body).toBe('Local body');
    expect(saved?.properties.aisle).toBe('Cold case');
    expect(db.conflicts.size).toBe(0);
  });

  it('creates a Needs Review conflict for overlapping high-risk fields', async () => {
    const db = new MemoryDb() as any;
    const base = await upsertRecord(db, manifest, {
      id: 'merge-conflict',
      title: 'Merge conflict',
      collection: 'inventory',
      properties: { body: 'Base', quantity: 2 },
      relations: [],
      source: { provider: 'sqlite', external_id: 'merge-conflict', url: null, observed_at: now, content_hash: null },
      archived_at: null,
      created_at: now,
      updated_at: now,
    });
    const local = await upsertRecord(db, manifest, { ...base, properties: { ...base.properties, quantity: 1 } });
    const remote = clone(base);
    remote.properties = { ...remote.properties, quantity: 4 };
    remote.source = { provider: 'google_sheets', external_id: 'row-2', url: 'https://docs.google.com/spreadsheets/d/x/edit', observed_at: now, content_hash: 'remote-2' };

    const result = await mergeRemoteRecord({ db, manifest, provider: 'google_sheets', externalId: 'row-2', base, local, remote });
    const saved = await getRecord(db, 'merge-conflict');

    expect(result.status).toBe('needs_review');
    expect(saved?.properties.quantity).toBe(1);
    expect(db.conflicts.size).toBe(1);
    const conflictRow = Array.from((db as MemoryDb).conflicts.values())[0];
    expect(conflictRow.fields_json).toContain('quantity');
  });
});
