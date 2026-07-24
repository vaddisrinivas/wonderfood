import {
  CanonicalRecord,
  CanonicalProvenance,
  RecordProvider,
  validateCanonicalRecord,
} from '@/src/domain/runtime';
import { getDomainManifest, loadCatalog, DomainManifest, DomainId } from '@/src/domain/catalog';
import type { SQLiteDatabase } from 'expo-sqlite';
import { applyOperation } from '@/src/ops/apply';
import type { OperationActor, OperationOrigin } from '@/src/ops/operation';

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
  revision: number;
  schema_version: string;
  deleted: number;
  privacy: CanonicalRecord['privacy'];
  provenance_json: string | null;
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

function operationId(parts: string[]) {
  const safe = parts.map((part) => part.replace(/[^A-Za-z0-9_-]/g, '-')).join('-');
  return `op-${safe}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

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
  input: Omit<CanonicalRecord, 'domain' | 'relations' | 'revision' | 'schema_version' | 'deleted' | 'privacy' | 'provenance'> & {
    id: string;
    relations?: CanonicalRelationInput[];
    source: CanonicalRecord['source'];
    created_at?: string;
    updated_at?: string;
    revision?: number;
    schema_version?: string;
    deleted?: boolean;
    privacy?: CanonicalRecord['privacy'];
    provenance?: CanonicalProvenance | null;
    operation_origin?: OperationOrigin;
    operation_actor?: OperationActor;
    idempotency_key?: string;
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

  const current = await getRecord(db, validated.id);
  const result = await applyOperation(db, manifest, {
    op_id: operationId([validated.id]),
    kind: current ? 'update' : 'create',
    domain: manifest.id,
    collection: validated.collection,
    record_id: validated.id,
    expected_revision: current?.revision,
    record: validated,
    actor: input.operation_actor ?? validated.provenance?.actor ?? actorForProvider(validated.source.provider),
    origin: input.operation_origin ?? originForProvider(validated.source.provider),
    idempotency_key: input.idempotency_key,
    confidence: validated.provenance?.confidence ?? null,
    evidence: validated.provenance?.evidence ?? [],
    reason: validated.provenance?.reason ?? `Upsert ${validated.title}`,
  });
  if (result.status === 'rejected') {
    throw new Error(`Record operation rejected: ${result.reject_reason}`);
  }
  const saved = result.record ?? await getRecord(db, validated.id);
  if (!saved) throw new Error(`Record operation did not return ${validated.id}`);
  return saved;
}

export async function archiveRecord(db: SQLiteDatabase, id: string): Promise<void> {
  const current = await getRecord(db, id);
  if (!current) return;
  const catalog = loadCatalog();
  const manifest = current.domain === catalog.activeManifest.id
    ? catalog.activeManifest
    : getDomainManifest(catalog.catalog.domains, current.domain) ?? catalog.activeManifest;
  const result = await applyOperation(db, manifest, {
    op_id: operationId([id, 'archive']),
    kind: 'archive',
    domain: manifest.id,
    collection: current.collection,
    record_id: id,
    expected_revision: current.revision,
    actor: 'user',
    origin: 'manual',
    reason: `Archive ${current.title}`,
  });
  if (result.status === 'rejected') {
    throw new Error(`Archive operation rejected: ${result.reject_reason}`);
  }
}

export async function restoreRecord(db: SQLiteDatabase, id: string): Promise<void> {
  const current = await getRecord(db, id);
  if (!current) return;
  const catalog = loadCatalog();
  const manifest = current.domain === catalog.activeManifest.id
    ? catalog.activeManifest
    : getDomainManifest(catalog.catalog.domains, current.domain) ?? catalog.activeManifest;
  const result = await applyOperation(db, manifest, {
    op_id: operationId([id, 'restore']),
    kind: 'restore',
    domain: manifest.id,
    collection: current.collection,
    record_id: id,
    expected_revision: current.revision,
    actor: 'user',
    origin: 'manual',
    reason: `Restore ${current.title}`,
  });
  if (result.status === 'rejected') {
    throw new Error(`Restore operation rejected: ${result.reject_reason}`);
  }
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
    revision: Number(row.revision) || 1,
    schema_version: row.schema_version || '1.0.0',
    deleted: Boolean(row.deleted),
    privacy: row.privacy === 'private' || row.privacy === 'shared' || row.privacy === 'personal' ? row.privacy : 'personal',
    provenance: parseProvenance(row.provenance_json),
  };
}

function parseProvenance(value: string | null): CanonicalProvenance | null {
  if (!value) return null;
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' ? parsed as CanonicalProvenance : null;
  } catch {
    return null;
  }
}

function actorForProvider(provider: RecordProvider): OperationActor {
  if (provider === 'notion' || provider === 'google_sheets' || provider === 'postgres') return 'sync';
  if (provider === 'web') return 'api';
  return 'user';
}

function originForProvider(provider: RecordProvider): OperationOrigin {
  if (provider === 'notion' || provider === 'google_sheets' || provider === 'postgres') return 'sync';
  if (provider === 'web') return 'import';
  return 'manual';
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
