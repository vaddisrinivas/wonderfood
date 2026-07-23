import {
  CanonicalRecord,
  RecordProvider,
  validateCanonicalRecord,
} from '@/src/domain/runtime';
import { loadCatalog, DomainManifest, DomainId } from '@/src/domain/catalog';
import { SQLiteDatabase } from 'expo-sqlite';

type SqlRecordRow = {
  id: string;
  domain: DomainId;
  collection: string;
  title: string;
  properties: string;
  source_provider: RecordProvider;
  source_external_id: string;
  source_url: string | null;
  source_observed_at: string;
  source_content_hash: string | null;
  archived_at: string | null;
  created_at: string;
  updated_at: string;
};

type SqlRelationRow = {
  from_id: string;
  collection: string;
  name: string;
  target_id: string;
  target_domain: string;
  target_collection: string;
  created_at: string;
};

type CanonicalRelationInput = Pick<SqlRelationRow, 'name' | 'target_id'>;

export async function getRecord(db: SQLiteDatabase, id: string): Promise<CanonicalRecord | null> {
  const row = await db.getFirstAsync<SqlRecordRow>(`SELECT * FROM records WHERE id = ?`, [id]);
  if (!row) return null;
  const [record, relations] = await Promise.all([
    inflateRecord(row),
    getRelationsForRecord(db, id),
  ]);
  return { ...record, relations };
}

export async function getRecordsByIds(db: SQLiteDatabase, ids: string[]): Promise<CanonicalRecord[]> {
  const uniqueIds = Array.from(new Set(ids.map((id) => id.trim()).filter(Boolean)));
  if (!uniqueIds.length) return [];
  const placeholders = uniqueIds.map(() => '?').join(', ');
  const rows = await db.getAllAsync<SqlRecordRow>(
    `SELECT * FROM records WHERE id IN (${placeholders}) AND archived_at IS NULL`,
    uniqueIds
  );
  return Promise.all(rows.map(async (row) => ({
    ...(await inflateRecord(row)),
    relations: await getRelationsForRecord(db, row.id),
  })));
}

export async function listRecordsForDomain(
  db: SQLiteDatabase,
  domainId: DomainId,
  collection?: string
): Promise<CanonicalRecord[]> {
  const where = collection ? 'domain = ? AND collection = ? AND archived_at IS NULL' : 'domain = ? AND archived_at IS NULL';
  const params = collection ? [domainId, collection] : [domainId];
  const rows = await db.getAllAsync<SqlRecordRow>(`SELECT * FROM records WHERE ${where} ORDER BY updated_at DESC`, params);
  return Promise.all(rows.map(async (row) => ({
    ...(await inflateRecord(row)),
    relations: await getRelationsForRecord(db, row.id),
  })));
}

export async function listRecordsByCollections(
  db: SQLiteDatabase,
  domainId: DomainId,
  collections: string[]
): Promise<CanonicalRecord[]> {
  if (collections.length === 0) return [];
  const placeholders = collections.map(() => '?').join(', ');
  const rows = await db.getAllAsync<SqlRecordRow>(
    `SELECT * FROM records WHERE domain = ? AND collection IN (${placeholders}) AND archived_at IS NULL ORDER BY updated_at DESC`,
    [domainId, ...collections]
  );
  return Promise.all(rows.map(async (row) => ({
    ...(await inflateRecord(row)),
    relations: await getRelationsForRecord(db, row.id),
  })));
}

export async function upsertRecord(
  db: SQLiteDatabase,
  manifest: DomainManifest,
  input: Omit<CanonicalRecord, 'domain' | 'relations'> & {
    id: string;
    relations?: CanonicalRelationInput[];
    source: CanonicalRecord['source'];
    created_at?: string;
    updated_at?: string;
  }
): Promise<CanonicalRecord> {
  const now = new Date().toISOString();
  const validated = validateCanonicalRecord(
    {
      ...input,
      domain: manifest.id,
      relations: input.relations ?? [],
      updated_at: input.updated_at ?? now,
      created_at: input.created_at ?? now,
    },
    manifest.id,
    manifest,
    'record'
  );

  const recordProperties = JSON.stringify(validated.properties ?? {});
  await db.withTransactionAsync(async () => {
    await db.runAsync(
      `
        INSERT INTO records (
          id, domain, collection, title, properties, source_provider, source_external_id, source_url,
          source_observed_at, source_content_hash, archived_at, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
          domain = excluded.domain,
          collection = excluded.collection,
          title = excluded.title,
          properties = excluded.properties,
          source_provider = excluded.source_provider,
          source_external_id = excluded.source_external_id,
          source_url = excluded.source_url,
          source_observed_at = excluded.source_observed_at,
          source_content_hash = excluded.source_content_hash,
          archived_at = excluded.archived_at,
          updated_at = excluded.updated_at
      `,
      [
        validated.id,
        validated.domain,
        validated.collection,
        validated.title,
        recordProperties,
        validated.source.provider,
        validated.source.external_id,
        validated.source.url ?? null,
        validated.source.observed_at,
        validated.source.content_hash ?? null,
        validated.archived_at,
        validated.created_at ?? now,
        validated.updated_at,
      ]
    );

    await db.runAsync(`DELETE FROM record_relations WHERE from_id = ?`, [validated.id]);
    for (const relation of validated.relations) {
      const target = relation.target_id.trim();
      const nowIso = new Date().toISOString();
      const targetParts = target.split(':');
      await db.runAsync(
        `
          INSERT INTO record_relations (
            from_id, collection, name, target_id, target_domain, target_collection, created_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?)
        `,
        [
          validated.id,
          validated.collection,
          relation.name,
          target,
          targetParts[0] || validated.domain,
          targetParts.length > 1 ? targetParts[1] : validated.collection,
          nowIso,
        ]
      );
    }
  });

  return getRecord(db, validated.id) as Promise<CanonicalRecord>;
}

export async function archiveRecord(db: SQLiteDatabase, id: string): Promise<void> {
  const now = new Date().toISOString();
  await db.runAsync(`UPDATE records SET archived_at = ?, updated_at = ? WHERE id = ?`, [now, now, id]);
}

async function inflateRecord(row: SqlRecordRow): Promise<CanonicalRecord> {
  let properties: Record<string, unknown> = {};
  try {
    const parsed = JSON.parse(row.properties);
    if (parsed && typeof parsed === 'object') {
      properties = parsed as Record<string, unknown>;
    }
  } catch {
    properties = {};
  }

  return {
    id: row.id,
    domain: row.domain,
    collection: row.collection,
    title: row.title,
    properties,
    relations: [],
    source: {
      provider: row.source_provider,
      external_id: row.source_external_id,
      url: row.source_url,
      observed_at: row.source_observed_at,
      content_hash: row.source_content_hash,
    },
    archived_at: row.archived_at,
    created_at: row.created_at,
    updated_at: row.updated_at,
  };
}

export async function ensureSeedCollectionCounts(
  db: SQLiteDatabase,
  domainId: DomainId
): Promise<Record<string, number>> {
  const rows = await db.getAllAsync<{ collection: string; total: number }>(
    'SELECT collection, COUNT(*) as total FROM records WHERE domain = ? AND archived_at IS NULL GROUP BY collection',
    [domainId]
  );
  return rows.reduce((acc, row) => {
    acc[row.collection] = row.total;
    return acc;
  }, {} as Record<string, number>);
}

async function getRelationsForRecord(db: SQLiteDatabase, id: string): Promise<CanonicalRecord['relations']> {
  const rows = await db.getAllAsync<SqlRelationRow>('SELECT name, target_id FROM record_relations WHERE from_id = ?', [id]);
  return rows.map((relation) => ({ name: relation.name, target_id: relation.target_id }));
}

export async function countRecords(db: SQLiteDatabase, domainId: DomainId): Promise<number> {
  const row = await db.getFirstAsync<{ total: number }>('SELECT COUNT(*) as total FROM records WHERE domain = ? AND archived_at IS NULL', [domainId]);
  return row?.total ?? 0;
}

export async function listActiveConversationsForRecordLinks(db: SQLiteDatabase, ids: string[]): Promise<Record<string, string[]>> {
  const byRecord: Record<string, string[]> = Object.fromEntries(ids.map((id) => [id, []]));
  if (ids.length === 0) return byRecord;

  const rows = await db.getAllAsync<{ from_id: string; conversation_id: string }>(
    `SELECT from_id, target_id as conversation_id FROM record_relations WHERE from_id IN (${ids.map(() => '?').join(', ')})`,
    ids
  );
  for (const row of rows) {
    if (row.conversation_id && byRecord[row.from_id]) {
      byRecord[row.from_id].push(row.conversation_id);
    }
  }
  return byRecord;
}

export function normalizeSeedRecord(record: CanonicalRecord) {
  const catalog = loadCatalog();
  return validateCanonicalRecord(record, catalog.activeDomainId, catalog.activeManifest);
}
