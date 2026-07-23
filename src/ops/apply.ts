import type { SQLiteDatabase } from 'expo-sqlite';

import type { DomainManifest } from '@/src/domain/catalog';
import type { CanonicalProvenance, CanonicalRecord, CanonicalRelation, RecordProvider } from '@/src/domain/runtime';
import { validateCanonicalRecord } from '@/src/domain/runtime';
import { computeInverse } from '@/src/ops/inverse';
import type { Operation, OperationResult } from '@/src/ops/operation';

type SqlRecordRow = {
  id: string;
  domain: string;
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
  privacy: 'private' | 'personal' | 'shared';
  provenance_json: string | null;
};

type OperationRow = {
  op_id: string;
  after_json: string | null;
  status: string;
};

type SqlRelationRow = {
  name: string;
  target_id: string;
};

function nowIso() {
  return new Date().toISOString();
}

function safeJson(value: unknown) {
  return JSON.stringify(value ?? null);
}

function parseProperties(value: string) {
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed as Record<string, unknown> : {};
  } catch {
    return {};
  }
}

function parseProvenance(value: string | null): CanonicalProvenance | null {
  if (!value) return null;
  try {
    const parsed = JSON.parse(value) as CanonicalProvenance;
    return parsed && typeof parsed === 'object' ? parsed : null;
  } catch {
    return null;
  }
}

async function readRecord(db: SQLiteDatabase, id: string): Promise<CanonicalRecord | null> {
  const row = await db.getFirstAsync<SqlRecordRow>('SELECT * FROM records WHERE id = ?', [id]);
  if (!row) return null;
  const relations = await db.getAllAsync<SqlRelationRow>('SELECT name, target_id FROM record_relations WHERE from_id = ?', [id]);
  return {
    id: row.id,
    domain: row.domain,
    collection: row.collection,
    title: row.title,
    properties: parseProperties(row.properties),
    relations: relations.map((relation) => ({ name: relation.name, target_id: relation.target_id })),
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
    revision: row.revision,
    schema_version: row.schema_version,
    deleted: Boolean(row.deleted),
    privacy: row.privacy,
    provenance: parseProvenance(row.provenance_json),
  };
}

function mergePatch(base: Record<string, unknown>, patch: Record<string, unknown> | undefined) {
  const next = { ...base };
  for (const [key, value] of Object.entries(patch ?? {})) {
    if (value === null) delete next[key];
    else next[key] = value;
  }
  return next;
}

function mergeRelations(current: CanonicalRelation[], op: Operation) {
  const byKey = new Map(current.map((relation) => [`${relation.name}:${relation.target_id}`, relation]));
  for (const relation of op.relations_remove ?? []) {
    byKey.delete(`${relation.name}:${relation.target_id}`);
  }
  for (const relation of op.relations_add ?? []) {
    byKey.set(`${relation.name}:${relation.target_id}`, relation);
  }
  return Array.from(byKey.values());
}

function provenanceFor(op: Operation): CanonicalProvenance {
  return {
    actor: op.actor,
    confidence: typeof op.confidence === 'number' ? op.confidence : null,
    evidence: op.evidence ?? [],
    reason: op.reason ?? null,
  };
}

function buildNextRecord(manifest: DomainManifest, op: Operation, current: CanonicalRecord | null): CanonicalRecord {
  const at = nowIso();
  const base = current ?? {
    id: op.record_id,
    domain: manifest.id,
    collection: op.collection,
    title: String(op.record?.title ?? op.changes?.title ?? 'Untitled'),
    properties: {},
    relations: [],
    source: op.record?.source ?? {
      provider: 'sqlite' as const,
      external_id: op.record_id,
      url: null,
      observed_at: at,
      content_hash: null,
    },
    archived_at: null,
    created_at: at,
    updated_at: at,
    revision: 0,
    schema_version: manifest.schema_version ?? '1.0.0',
    deleted: false,
    privacy: 'personal' as const,
    provenance: null,
  };
  const recordPatch = op.record ?? {};
  const properties = recordPatch.properties && typeof recordPatch.properties === 'object' && !Array.isArray(recordPatch.properties)
    ? recordPatch.properties as Record<string, unknown>
    : mergePatch(base.properties, op.changes);
  const archivedAt = op.kind === 'archive' || op.kind === 'delete'
    ? (typeof recordPatch.archived_at === 'string' ? recordPatch.archived_at : at)
    : op.kind === 'restore'
      ? null
      : recordPatch.archived_at !== undefined
        ? recordPatch.archived_at ?? null
        : base.archived_at;
  return validateCanonicalRecord({
    ...base,
    ...recordPatch,
    id: op.record_id,
    domain: manifest.id,
    collection: op.collection,
    title: String(recordPatch.title ?? properties.title ?? base.title),
    properties,
    relations: mergeRelations(Array.isArray(recordPatch.relations) ? recordPatch.relations : base.relations, op),
    source: recordPatch.source ?? base.source,
    archived_at: archivedAt,
    deleted: op.kind === 'delete' ? true : op.kind === 'restore' ? false : recordPatch.deleted ?? base.deleted,
    privacy: recordPatch.privacy ?? base.privacy,
    provenance: provenanceFor(op),
    revision: base.revision + 1,
    schema_version: recordPatch.schema_version ?? base.schema_version ?? manifest.schema_version ?? '1.0.0',
    created_at: base.created_at || at,
    updated_at: at,
  }, manifest.id, manifest, 'operation');
}

async function insertOperation(db: SQLiteDatabase, op: Operation, before: CanonicalRecord | null, after: CanonicalRecord | null, status: 'applied' | 'rejected', reason?: string) {
  await db.runAsync(
    `INSERT INTO operations (
      op_id, kind, domain, collection, record_id, expected_revision, result_revision,
      actor, origin, idempotency_key, changes_json, before_json, after_json, inverse_op_id,
      status, reject_reason, created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      op.op_id,
      op.kind,
      op.domain,
      op.collection,
      op.record_id,
      op.expected_revision ?? null,
      after?.revision ?? null,
      op.actor,
      op.origin,
      op.idempotency_key ?? null,
      safeJson(op),
      before ? safeJson(before) : null,
      after ? safeJson(after) : null,
      after ? `${op.op_id}:undo` : null,
      status,
      reason ?? null,
      nowIso(),
    ]
  );
}

export async function applyOperation(db: SQLiteDatabase, manifest: DomainManifest, op: Operation): Promise<OperationResult> {
  if (op.idempotency_key) {
    const duplicate = await db.getFirstAsync<OperationRow>('SELECT op_id, after_json, status FROM operations WHERE idempotency_key = ?', [op.idempotency_key]);
    if (duplicate?.status === 'applied' || duplicate?.status === 'undone') {
      return {
        status: 'duplicate',
        op_id: duplicate.op_id,
        record: duplicate.after_json ? JSON.parse(duplicate.after_json) as CanonicalRecord : undefined,
      };
    }
  }

  const current = await readRecord(db, op.record_id);
  if (current && op.kind !== 'create' && op.expected_revision == null) {
    const rejectReason = 'expected_revision_required';
    await insertOperation(db, op, current, null, 'rejected', rejectReason);
    return { status: 'rejected', op_id: op.op_id, reject_reason: rejectReason };
  }
  if (current && op.expected_revision != null && op.expected_revision !== current.revision) {
    const rejectReason = 'revision_conflict';
    await insertOperation(db, op, current, null, 'rejected', rejectReason);
    return { status: 'rejected', op_id: op.op_id, reject_reason: rejectReason };
  }

  let next: CanonicalRecord;
  try {
    next = buildNextRecord(manifest, op, current);
  } catch (error) {
    const rejectReason = error instanceof Error ? error.message : 'validation_failed';
    await insertOperation(db, op, current, null, 'rejected', rejectReason);
    return { status: 'rejected', op_id: op.op_id, reject_reason: rejectReason };
  }
  const inverse = computeInverse(current, op, next);

  await db.withTransactionAsync(async () => {
    await db.runAsync(
      `INSERT INTO records (
        id, domain, collection, title, properties, source_provider, source_external_id, source_url,
        source_observed_at, source_content_hash, archived_at, created_at, updated_at,
        revision, schema_version, deleted, privacy, provenance_json
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
        updated_at = excluded.updated_at,
        revision = excluded.revision,
        schema_version = excluded.schema_version,
        deleted = excluded.deleted,
        privacy = excluded.privacy,
        provenance_json = excluded.provenance_json`,
      [
        next.id,
        next.domain,
        next.collection,
        next.title,
        safeJson(next.properties),
        next.source.provider,
        next.source.external_id,
        next.source.url,
        next.source.observed_at,
        next.source.content_hash,
        next.archived_at,
        next.created_at,
        next.updated_at,
        next.revision,
        next.schema_version,
        next.deleted ? 1 : 0,
        next.privacy,
        next.provenance ? safeJson(next.provenance) : null,
      ]
    );
    await db.runAsync('DELETE FROM record_relations WHERE from_id = ?', [next.id]);
    for (const relation of next.relations) {
      const targetParts = relation.target_id.split(':');
      await db.runAsync(
        `INSERT INTO record_relations (
          from_id, collection, name, target_id, target_domain, target_collection, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?)`,
        [
          next.id,
          next.collection,
          relation.name,
          relation.target_id,
          targetParts[0] || next.domain,
          targetParts.length > 1 ? targetParts[1] : next.collection,
          nowIso(),
        ]
      );
    }
    await insertOperation(db, op, current, next, 'applied');
  });

  return { status: 'applied', op_id: op.op_id, record: next, inverse };
}
