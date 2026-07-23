import type { SQLiteDatabase } from 'expo-sqlite';

import type { CanonicalRecord } from '@/src/domain/runtime';
import { enqueueOutboxEvent, getOutboxEventByActionKey, markOutboxEvent, type OutboxEvent } from '@/src/db/outbox';
import type { DirectSyncProvider } from '@/src/providers/provider-local-copy';
import type { LifeOSSettings } from '@/src/settings/lifeos-settings';

export type ProviderWriteOperation = 'create_record' | 'update_record' | 'archive_record' | 'restore_record';

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

export type ProviderWriteDeliveryResult =
  | { status: 'delivered'; event_id: string; provider: DirectSyncProvider; statusCode: number }
  | { status: 'blocked'; event_id: string; provider?: DirectSyncProvider; reason: string }
  | { status: 'failed'; event_id: string; provider: DirectSyncProvider; statusCode: number; reason: string };

type FetchLike = (url: string, init: {
  method: string;
  headers: Record<string, string>;
  body?: string;
}) => Promise<{ ok: boolean; status: number; text: () => Promise<string> }>;

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
  if (row.kind === 'restore') return 'restore_record';
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

function parsePayload(event: OutboxEvent): ProviderWritePayload | null {
  try {
    const parsed = JSON.parse(event.payload_json) as ProviderWritePayload;
    return parsed?.schema_version === 'lifeos.provider-write.v1' ? parsed : null;
  } catch {
    return null;
  }
}

function recordTitle(record: CanonicalRecord | null) {
  return record?.title?.trim() || record?.id || 'LifeOS record';
}

function notionProperties(record: CanonicalRecord | null) {
  return {
    Name: {
      title: [{ type: 'text', text: { content: recordTitle(record) } }],
    },
  };
}

function firstDataSourceId(settings: LifeOSSettings) {
  return settings.notion.dataSourceIds.split(',').map((id) => id.trim()).filter(Boolean)[0] ?? '';
}

function buildNotionRequest(payload: ProviderWritePayload, settings: LifeOSSettings) {
  if (!settings.notion.enabled || !settings.notion.token.trim()) return { blocked: 'Notion token is missing.' };
  const dataSourceId = firstDataSourceId(settings);
  if (payload.operation === 'create_record') {
    if (!dataSourceId) return { blocked: 'Notion data source ID is missing.' };
    return {
      url: 'https://api.notion.com/v1/pages',
      init: {
        method: 'POST',
        headers: {
          authorization: `Bearer ${settings.notion.token.trim()}`,
          'content-type': 'application/json',
          'notion-version': '2026-03-11',
        },
        body: JSON.stringify({
          parent: { data_source_id: dataSourceId },
          properties: notionProperties(payload.record),
        }),
      },
    };
  }
  const pageId = payload.external_id?.trim();
  if (!pageId) return { blocked: 'Notion page ID is missing for update/archive.' };
  const body = payload.operation === 'archive_record'
    ? { in_trash: true }
    : payload.operation === 'restore_record'
      ? { in_trash: false, properties: notionProperties(payload.record) }
      : { properties: notionProperties(payload.record) };
  return {
    url: `https://api.notion.com/v1/pages/${encodeURIComponent(pageId)}`,
    init: {
      method: 'PATCH',
      headers: {
        authorization: `Bearer ${settings.notion.token.trim()}`,
        'content-type': 'application/json',
        'notion-version': '2026-03-11',
      },
      body: JSON.stringify(body),
    },
  };
}

function sheetRow(payload: ProviderWritePayload) {
  const record = payload.record ?? payload.before;
  const archived = payload.operation === 'archive_record'
    ? 'true'
    : payload.operation === 'restore_record'
      ? 'false'
      : String(record?.archived_at ? true : record?.deleted ?? false);
  return [
    payload.record_id,
    recordTitle(record),
    record?.domain ?? '',
    record?.collection ?? '',
    JSON.stringify(record?.properties ?? {}),
    archived,
    String(record?.revision ?? ''),
    new Date().toISOString(),
    payload.op_id,
  ];
}

function buildSheetsRequest(payload: ProviderWritePayload, settings: LifeOSSettings) {
  if (!settings.sheets.enabled || !settings.sheets.token.trim()) return { blocked: 'Google Sheets token is missing.' };
  if (!settings.sheets.workbookId.trim()) return { blocked: 'Google Sheets workbook ID is missing.' };
  const sheetName = settings.sheets.sheetName.trim() || 'LifeOS Canonical';
  const range = encodeURIComponent(`${sheetName}!A:I`);
  return {
    url: `https://sheets.googleapis.com/v4/spreadsheets/${encodeURIComponent(settings.sheets.workbookId.trim())}/values/${range}:append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS`,
    init: {
      method: 'POST',
      headers: {
        authorization: `Bearer ${settings.sheets.token.trim()}`,
        'content-type': 'application/json',
      },
      body: JSON.stringify({ values: [sheetRow(payload)] }),
    },
  };
}

export async function deliverProviderWriteEvent(input: {
  db: SQLiteDatabase;
  event: OutboxEvent;
  settings: LifeOSSettings;
  fetcher?: FetchLike;
  platform?: 'native' | 'web' | 'node';
}): Promise<ProviderWriteDeliveryResult> {
  const payload = parsePayload(input.event);
  if (!payload) return { status: 'blocked', event_id: input.event.id, reason: 'unsupported_outbox_payload' };
  if (input.platform === 'web') {
    return { status: 'blocked', event_id: input.event.id, provider: payload.provider, reason: 'Direct provider writes are blocked by browser CORS; use native app delivery.' };
  }
  const request = payload.provider === 'notion'
    ? buildNotionRequest(payload, input.settings)
    : buildSheetsRequest(payload, input.settings);
  if ('blocked' in request) {
    return { status: 'blocked', event_id: input.event.id, provider: payload.provider, reason: request.blocked ?? 'provider_config_missing' };
  }
  const fetcher = input.fetcher ?? fetch;
  const response = await fetcher(request.url, request.init);
  if (response.ok) {
    await markOutboxEvent(input.db, input.event.id, { status: 'done', last_error: null });
    return { status: 'delivered', event_id: input.event.id, provider: payload.provider, statusCode: response.status };
  }
  const reason = (await response.text().catch(() => '')).slice(0, 240) || `HTTP ${response.status}`;
  await markOutboxEvent(input.db, input.event.id, { status: 'failed', last_error: reason, attemptsDelta: 1 });
  return { status: 'failed', event_id: input.event.id, provider: payload.provider, statusCode: response.status, reason };
}
