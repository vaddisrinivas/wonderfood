import type { SQLiteDatabase } from 'expo-sqlite';

import type { CanonicalRecord } from '@/src/domain/runtime';
import { enqueueOutboxEvent, getOutboxEventByActionKey, type OutboxEvent } from '@/src/db/outbox';
import type { DirectSyncProvider } from '@/src/providers/provider-local-copy';

export type ProviderWriteOperation = 'create_record' | 'update_record' | 'archive_record';

export type ProviderWritePayload = {
  schema_version: 'lifeos.provider-write.v1';
  provider: DirectSyncProvider;
  operation: ProviderWriteOperation;
  op_id: string;
  record_id: string;
  expected_revision: number | null;
  record: CanonicalRecord | null;
  before: CanonicalRecord | null;
  external_id: string | null;
  endpoint: '/providers/notion/push' | '/providers/sheets/push';
};

export type ProviderWritebackResult =
  | { status: 'queued'; event: OutboxEvent; payload: ProviderWritePayload }
  | { status: 'duplicate'; event: OutboxEvent; payload: ProviderWritePayload }
  | { status: 'rejected'; op_id: string; reject_reason: string };

type OperationRow = {
  op_id: string;
  kind: string;
  domain: string;
  collection: string;
  record_id: string;
  expected_revision: number | null;
  result_revision: number | null;
  before_json: string | null;
  after_json: string | null;
  status: string;
};

function safeId(value: string) {
  return value.replace(/[^A-Za-z0-9_-]/g, '-').slice(0, 180);
}

function parseRecord(value: string | null): CanonicalRecord | null {
  if (!value) return null;
  try {
    const parsed = JSON.parse(value) as CanonicalRecord;
    return parsed && typeof parsed === 'object' ? parsed : null;
  } catch {
    return null;
  }
}

function endpointFor(provider: DirectSyncProvider): ProviderWritePayload['endpoint'] {
  return provider === 'google_sheets' ? '/providers/sheets/push' : '/providers/notion/push';
}

function externalIdFor(provider: DirectSyncProvider, record: CanonicalRecord | null) {
  if (!record) return null;
  return record.source.provider === provider ? record.source.external_id : record.id;
}

function providerOperation(row: OperationRow, after: CanonicalRecord | null): ProviderWriteOperation {
  if (row.kind === 'archive' || row.kind === 'delete' || after?.deleted || after?.archived_at) return 'archive_record';
  if (!row.before_json) return 'create_record';
  return 'update_record';
}

export async function enqueueProviderWriteForOperation(input: {
  db: SQLiteDatabase;
  provider: DirectSyncProvider;
  opId: string;
}): Promise<ProviderWritebackResult> {
  const row = await input.db.getFirstAsync<OperationRow>('SELECT * FROM operations WHERE op_id = ?', [input.opId]);
  if (!row) return { status: 'rejected', op_id: input.opId, reject_reason: 'operation_not_found' };
  if (row.status !== 'applied') return { status: 'rejected', op_id: input.opId, reject_reason: `operation_not_applied:${row.status}` };

  const before = parseRecord(row.before_json);
  const after = parseRecord(row.after_json);
  if (!after && !before) return { status: 'rejected', op_id: input.opId, reject_reason: 'operation_has_no_record_image' };

  const record = after ?? before;
  const payload: ProviderWritePayload = {
    schema_version: 'lifeos.provider-write.v1',
    provider: input.provider,
    operation: providerOperation(row, after),
    op_id: row.op_id,
    record_id: row.record_id,
    expected_revision: row.expected_revision ?? null,
    record: after,
    before,
    external_id: externalIdFor(input.provider, record),
    endpoint: endpointFor(input.provider),
  };
  const actionKey = `provider-write:${input.provider}:${row.op_id}`;
  const duplicate = await getOutboxEventByActionKey(input.db, actionKey);
  if (duplicate) return { status: 'duplicate', event: duplicate, payload };

  const event = await enqueueOutboxEvent(input.db, {
    id: `provider-write-${safeId(input.provider)}-${safeId(row.op_id)}`,
    action_key: actionKey,
    domain: row.domain,
    payload_json: JSON.stringify(payload),
  });
  return { status: 'queued', event, payload };
}
