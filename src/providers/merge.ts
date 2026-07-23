import type { SQLiteDatabase } from 'expo-sqlite';

import type { DomainManifest } from '@/src/domain/catalog';
import type { CanonicalRecord } from '@/src/domain/runtime';
import { applyOperation } from '@/src/ops/apply';
import type { OperationResult } from '@/src/ops/operation';

type ProviderName = CanonicalRecord['source']['provider'];

export type SyncMergeResult =
  | { status: 'no_change'; changedFields: string[] }
  | { status: 'applied'; changedFields: string[]; operation: OperationResult }
  | { status: 'needs_review'; changedFields: string[]; conflictId: string };

const highRiskHints = [
  'quantity',
  'amount',
  'price',
  'total',
  'subtotal',
  'tax',
  'cost',
  'money',
  'archive',
  'deleted',
  'date',
  'schedule',
  'planned',
  'relation',
];

function stable(value: unknown) {
  return JSON.stringify(value ?? null);
}

function same(left: unknown, right: unknown) {
  return stable(left) === stable(right);
}

function fieldIsHighRisk(field: string) {
  const normalized = field.toLowerCase();
  return highRiskHints.some((hint) => normalized.includes(hint));
}

function materialFields(record: CanonicalRecord): Record<string, unknown> {
  return {
    title: record.title,
    archived_at: record.archived_at,
    deleted: record.deleted,
    privacy: record.privacy,
    relations: record.relations,
    ...record.properties,
  };
}

function mergeRemoteIntoLocal(base: CanonicalRecord | null, local: CanonicalRecord, remote: CanonicalRecord) {
  const baseFields: Record<string, unknown> = base ? materialFields(base) : {};
  const localFields = materialFields(local);
  const remoteFields = materialFields(remote);
  const keys = Array.from(new Set([
    ...Object.keys(baseFields),
    ...Object.keys(localFields),
    ...Object.keys(remoteFields),
  ])).sort();

  const changedFields: string[] = [];
  const conflictFields: string[] = [];
  const next: CanonicalRecord = {
    ...local,
    properties: { ...local.properties },
    relations: [...local.relations],
  };

  for (const field of keys) {
    const baseValue = baseFields[field];
    const localValue = localFields[field];
    const remoteValue = remoteFields[field];
    const localChanged = !same(localValue, baseValue);
    const remoteChanged = !same(remoteValue, baseValue);
    if (!remoteChanged) continue;
    changedFields.push(field);
    if (localChanged && !same(localValue, remoteValue)) {
      conflictFields.push(field);
      continue;
    }
    if (field === 'title') next.title = String(remoteValue ?? local.title);
    else if (field === 'archived_at') next.archived_at = typeof remoteValue === 'string' ? remoteValue : null;
    else if (field === 'deleted') next.deleted = remoteValue === true;
    else if (field === 'privacy') next.privacy = remoteValue === 'private' || remoteValue === 'shared' ? remoteValue : 'personal';
    else if (field === 'relations' && Array.isArray(remoteValue)) next.relations = remoteValue as CanonicalRecord['relations'];
    else if (remoteValue === undefined || remoteValue === null) delete next.properties[field];
    else next.properties[field] = remoteValue;
  }

  const highRiskConflict = conflictFields.some(fieldIsHighRisk);
  return {
    changedFields,
    conflictFields,
    shouldReview: conflictFields.length > 0 || highRiskConflict,
    next,
  };
}

function conflictId(provider: ProviderName, externalId: string, recordId: string, fields: string[]) {
  return `conflict-${provider}-${externalId}-${recordId}-${fields.join('-')}`.replace(/[^A-Za-z0-9_-]/g, '-').slice(0, 180);
}

export async function mergeRemoteRecord(input: {
  db: SQLiteDatabase;
  manifest: DomainManifest;
  provider: ProviderName;
  externalId: string;
  base: CanonicalRecord | null;
  local: CanonicalRecord;
  remote: CanonicalRecord;
}): Promise<SyncMergeResult> {
  const plan = mergeRemoteIntoLocal(input.base, input.local, input.remote);
  if (!plan.changedFields.length) {
    return { status: 'no_change', changedFields: [] };
  }
  if (plan.shouldReview) {
    const id = conflictId(input.provider, input.externalId, input.local.id, plan.conflictFields);
    await input.db.runAsync(
      `INSERT INTO sync_conflicts (
        id, domain, collection, record_id, provider, external_id, fields_json,
        base_json, local_json, remote_json, status, resolution_op_id, created_at, resolved_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        id,
        input.manifest.id,
        input.local.collection,
        input.local.id,
        input.provider,
        input.externalId,
        JSON.stringify(plan.conflictFields),
        input.base ? JSON.stringify(input.base) : null,
        JSON.stringify(input.local),
        JSON.stringify(input.remote),
        'needs_review',
        null,
        new Date().toISOString(),
        null,
      ],
    );
    return { status: 'needs_review', changedFields: plan.changedFields, conflictId: id };
  }

  const operation = await applyOperation(input.db, input.manifest, {
    op_id: `sync-merge-${input.provider}-${input.externalId}-${Date.now().toString(36)}`,
    kind: 'update',
    domain: input.manifest.id,
    collection: input.local.collection,
    record_id: input.local.id,
    expected_revision: input.local.revision,
    record: plan.next,
    actor: 'sync',
    origin: 'sync',
    idempotency_key: `sync-merge:${input.provider}:${input.externalId}:${input.local.id}:${stable(plan.changedFields)}`,
    evidence: [input.remote.source.external_id],
    reason: 'Merged remote provider changes into local record.',
  });
  if (operation.status === 'applied' || operation.status === 'duplicate') {
    return { status: 'applied', changedFields: plan.changedFields, operation };
  }
  return { status: 'needs_review', changedFields: plan.changedFields, conflictId: `operation-${operation.op_id}` };
}
