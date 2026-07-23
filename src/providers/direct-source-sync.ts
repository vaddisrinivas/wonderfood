import { Platform } from 'react-native';
import { SQLiteDatabase } from 'expo-sqlite';

import { DomainManifest } from '@/src/domain/catalog';
import { CanonicalRecord, RecordProvider } from '@/src/domain/runtime';
import { upsertRecord } from '@/src/db/records';
import { linkSnapshotToRecord, upsertProviderLink, upsertSourceSnapshot } from '@/src/db/sources';
import { LifeOSSettings } from '@/src/settings/lifeos-settings';

export type DirectSyncProvider = Extract<RecordProvider, 'notion' | 'google_sheets'>;

export type DirectSyncReceipt = {
  provider: DirectSyncProvider;
  status: 'synced' | 'blocked' | 'error';
  message: string;
  records: number;
  snapshots: number;
  observedAt: string;
};

type NotionPage = {
  id?: string;
  url?: string;
  created_time?: string;
  last_edited_time?: string;
  properties?: Record<string, unknown>;
};

type SheetValuesResponse = {
  values?: unknown[][];
};

const NOTION_VERSION = '2026-03-11';

export async function syncConfiguredSources(input: {
  db: SQLiteDatabase | null;
  manifest: DomainManifest;
  settings: LifeOSSettings;
}): Promise<DirectSyncReceipt[]> {
  const receipts: DirectSyncReceipt[] = [];
  if (input.settings.notion.enabled) {
    receipts.push(await syncNotionDirect(input));
  }
  if (input.settings.sheets.enabled) {
    receipts.push(await syncSheetsDirect(input));
  }
  if (!receipts.length) {
    receipts.push({
      provider: 'notion',
      status: 'blocked',
      message: 'Enable Notion or Sheets in Connections first.',
      records: 0,
      snapshots: 0,
      observedAt: new Date().toISOString(),
    });
  }
  return receipts;
}

export async function syncNotionDirect(input: {
  db: SQLiteDatabase | null;
  manifest: DomainManifest;
  settings: LifeOSSettings;
}): Promise<DirectSyncReceipt> {
  const observedAt = new Date().toISOString();
  const config = input.settings.notion;
  if (!input.db) return blocked('notion', 'Local graph is not ready yet.', observedAt);
  if (!config.enabled || !config.token.trim()) return blocked('notion', 'Notion is not enabled or token is missing.', observedAt);
  const dataSourceIds = config.dataSourceIds.split(',').map((id) => id.trim()).filter(Boolean);
  if (!dataSourceIds.length) return blocked('notion', 'Add at least one Notion data source ID in Connections.', observedAt);
  if (Platform.OS === 'web') {
    return blocked('notion', 'Direct Notion sync is available in the Android and iOS app. Browser CORS blocks direct Notion API calls.', observedAt);
  }

  try {
    const records: CanonicalRecord[] = [];
    const snapshots: Array<{ id: string; provider: DirectSyncProvider; externalId: string; payload: unknown; checksum: string }> = [];
    for (const dataSourceId of dataSourceIds) {
      const response = await fetch(`https://api.notion.com/v1/data_sources/${encodeURIComponent(dataSourceId)}/query`, {
        method: 'POST',
        headers: {
          authorization: `Bearer ${config.token}`,
          'content-type': 'application/json',
          'notion-version': NOTION_VERSION,
        },
        body: JSON.stringify({ page_size: 100 }),
      });
      if (!response.ok) {
        const detail = await response.text().catch(() => '');
        return error('notion', `Notion pull failed ${response.status}${detail ? `: ${detail.slice(0, 160)}` : ''}`, observedAt);
      }
      const payload = (await response.json()) as { results?: NotionPage[] };
      for (const page of payload.results ?? []) {
        const record = notionPageToRecord(page, input.manifest, observedAt);
        records.push(record);
        snapshots.push({
          id: `notion-snapshot-${safeId(page.id || record.id)}`,
          provider: 'notion',
          externalId: page.id || record.id,
          payload: page,
          checksum: checksum(JSON.stringify(page)),
        });
      }
    }
    await applyRecords(input.db, input.manifest, records, snapshots, {
      provider: 'notion',
      externalId: dataSourceIds.join(','),
      name: 'Notion',
      workspace: config.pageId || dataSourceIds[0] || 'Notion workspace',
      url: config.pageId ? `https://www.notion.so/${config.pageId.replace(/-/g, '')}` : 'https://www.notion.so',
      observedAt,
    });
    return {
      provider: 'notion',
      status: 'synced',
      message: `Pulled ${records.length} Notion records into ${input.manifest.label}.`,
      records: records.length,
      snapshots: snapshots.length,
      observedAt,
    };
  } catch (err) {
    return error('notion', err instanceof Error ? err.message : 'Notion sync failed.', observedAt);
  }
}

export async function syncSheetsDirect(input: {
  db: SQLiteDatabase | null;
  manifest: DomainManifest;
  settings: LifeOSSettings;
}): Promise<DirectSyncReceipt> {
  const observedAt = new Date().toISOString();
  const config = input.settings.sheets;
  if (!input.db) return blocked('google_sheets', 'Local graph is not ready yet.', observedAt);
  if (!config.enabled || !config.token.trim()) return blocked('google_sheets', 'Google Sheets is not enabled or token is missing.', observedAt);
  if (!config.workbookId.trim()) return blocked('google_sheets', 'Add a Google Sheets workbook ID in Connections.', observedAt);

  try {
    const range = encodeURIComponent(config.sheetName || 'LifeOS Canonical');
    const response = await fetch(`https://sheets.googleapis.com/v4/spreadsheets/${encodeURIComponent(config.workbookId)}/values/${range}`, {
      headers: { authorization: `Bearer ${config.token}` },
    });
    if (!response.ok) {
      const detail = await response.text().catch(() => '');
      return error('google_sheets', `Sheets pull failed ${response.status}${detail ? `: ${detail.slice(0, 160)}` : ''}`, observedAt);
    }
    const payload = (await response.json()) as SheetValuesResponse;
    const records = sheetValuesToRecords(payload.values ?? [], input.manifest, config.workbookId, observedAt);
    const snapshots = records.map((record) => ({
      id: `sheets-snapshot-${safeId(record.source.external_id)}`,
      provider: 'google_sheets' as const,
      externalId: record.source.external_id,
      payload: record.properties,
      checksum: checksum(JSON.stringify(record.properties)),
    }));
    await applyRecords(input.db, input.manifest, records, snapshots, {
      provider: 'google_sheets',
      externalId: config.workbookId,
      name: 'Google Sheets',
      workspace: config.sheetName || 'LifeOS Canonical',
      url: `https://docs.google.com/spreadsheets/d/${config.workbookId}/edit`,
      observedAt,
    });
    return {
      provider: 'google_sheets',
      status: 'synced',
      message: `Pulled ${records.length} Sheets rows into ${input.manifest.label}.`,
      records: records.length,
      snapshots: snapshots.length,
      observedAt,
    };
  } catch (err) {
    return error('google_sheets', err instanceof Error ? err.message : 'Sheets sync failed.', observedAt);
  }
}

function notionPageToRecord(page: NotionPage, manifest: DomainManifest, observedAt: string): CanonicalRecord {
  const props = page.properties ?? {};
  const id = `notion-${safeId(page.id || String(Date.now()))}`;
  const title = pickTitle(props) || 'Notion record';
  const collection = pickCollection(props, manifest);
  return {
    id,
    domain: manifest.id,
    collection,
    title,
    properties: {
      status: readPlainProperty(props.Status) || readPlainProperty(props.status) || 'Active',
      meta: readPlainProperty(props.Meta) || readPlainProperty(props.Type) || collection,
      body: readPlainProperty(props.Body) || readPlainProperty(props.Notes) || readPlainProperty(props.Description) || title,
      source: 'Notion',
      raw: props,
    },
    relations: [],
    source: {
      provider: 'notion',
      external_id: page.id || id,
      url: page.url || null,
      observed_at: observedAt,
      content_hash: checksum(JSON.stringify(page)),
    },
    archived_at: null,
    created_at: page.created_time || observedAt,
    updated_at: page.last_edited_time || observedAt,
  };
}

function sheetValuesToRecords(values: unknown[][], manifest: DomainManifest, workbookId: string, observedAt: string): CanonicalRecord[] {
  const [headerRow, ...rows] = values;
  const headers = (headerRow ?? []).map((value) => String(value || '').trim());
  if (!headers.length) return [];
  return rows
    .map((row, index) => {
      const object = Object.fromEntries(headers.map((header, headerIndex) => [header, row[headerIndex] ?? '']));
      const idRaw = stringField(object, ['id', 'record_id', 'Record ID']) || `${workbookId}-${index + 2}`;
      const title = stringField(object, ['title', 'Title', 'name', 'Name']) || `Sheet row ${index + 2}`;
      const collection = normalizeCollection(stringField(object, ['collection', 'Collection']) || '', manifest);
      return {
        id: `sheets-${safeId(idRaw)}`,
        domain: manifest.id,
        collection,
        title,
        properties: {
          status: stringField(object, ['status', 'Status']) || 'Active',
          meta: stringField(object, ['meta', 'Meta', 'type', 'Type']) || collection,
          body: stringField(object, ['body', 'Body', 'notes', 'Notes', 'description', 'Description']) || title,
          source: 'Google Sheets',
          row: index + 2,
          raw: object,
        },
        relations: [],
        source: {
          provider: 'google_sheets' as const,
          external_id: idRaw,
          url: `https://docs.google.com/spreadsheets/d/${workbookId}/edit`,
          observed_at: observedAt,
          content_hash: checksum(JSON.stringify(object)),
        },
        archived_at: null,
        created_at: observedAt,
        updated_at: observedAt,
      } satisfies CanonicalRecord;
    })
    .filter((record) => record.title.trim().length > 0);
}

async function applyRecords(
  db: SQLiteDatabase,
  manifest: DomainManifest,
  records: CanonicalRecord[],
  snapshots: Array<{ id: string; provider: DirectSyncProvider; externalId: string; payload: unknown; checksum: string }>,
  link: { provider: DirectSyncProvider; externalId: string; name: string; workspace: string; url: string; observedAt: string },
) {
  const now = new Date().toISOString();
  await upsertProviderLink(db, {
    id: `${link.provider}:${safeId(link.externalId)}`,
    provider: link.provider,
    external_id: link.externalId,
    name: link.name,
    status: 'Synced',
    freshness: link.observedAt,
    workspace: link.workspace,
    url: link.url,
    created_at: now,
    updated_at: now,
  });
  for (const snapshot of snapshots) {
    await upsertSourceSnapshot(db, {
      id: snapshot.id,
      provider: snapshot.provider,
      external_id: snapshot.externalId,
      scope: manifest.id,
      observed_at: link.observedAt,
      payload_json: JSON.stringify(snapshot.payload),
      checksum: snapshot.checksum,
      created_at: now,
      updated_at: now,
    });
  }
  for (const record of records) {
    await upsertRecord(db, manifest, record);
    const snapshot = snapshots.find((item) => record.source.external_id === item.externalId);
    if (snapshot) await linkSnapshotToRecord(db, { snapshot_id: snapshot.id, record_id: record.id });
  }
}

function pickTitle(props: Record<string, unknown>): string {
  for (const key of ['Name', 'Title', 'title', 'name']) {
    const value = readPlainProperty(props[key]);
    if (value) return value;
  }
  for (const value of Object.values(props)) {
    const plain = readPlainProperty(value);
    if (plain) return plain;
  }
  return '';
}

function pickCollection(props: Record<string, unknown>, manifest: DomainManifest): string {
  return normalizeCollection(readPlainProperty(props.Collection) || readPlainProperty(props.collection) || '', manifest);
}

function normalizeCollection(value: string, manifest: DomainManifest): string {
  const normalized = value.trim().toLowerCase().replace(/\s+/g, '_').replace(/-/g, '_');
  return manifest.collections.includes(normalized) ? normalized : manifest.collections[0] || 'source_record';
}

function readPlainProperty(value: unknown): string {
  if (typeof value === 'string') return value.trim();
  if (!value || typeof value !== 'object') return '';
  const record = value as Record<string, unknown>;
  if (typeof record.plain_text === 'string') return record.plain_text.trim();
  if (typeof record.name === 'string') return record.name.trim();
  if (typeof record.content === 'string') return record.content.trim();
  if (typeof record.number === 'number') return String(record.number);
  if (typeof record.checkbox === 'boolean') return record.checkbox ? 'true' : 'false';
  if (Array.isArray(record.title)) return record.title.map(readPlainProperty).filter(Boolean).join(' ').trim();
  if (Array.isArray(record.rich_text)) return record.rich_text.map(readPlainProperty).filter(Boolean).join(' ').trim();
  if (record.select) return readPlainProperty(record.select);
  if (Array.isArray(record.multi_select)) return record.multi_select.map(readPlainProperty).filter(Boolean).join(', ');
  if (record.date && typeof record.date === 'object') {
    const date = record.date as Record<string, unknown>;
    return [date.start, date.end].filter((item) => typeof item === 'string').join(' to ');
  }
  if (record.formula && typeof record.formula === 'object') return readPlainProperty(record.formula);
  return '';
}

function stringField(object: Record<string, unknown>, keys: string[]): string {
  for (const key of keys) {
    const value = object[key];
    if (value !== undefined && value !== null && String(value).trim()) return String(value).trim();
  }
  return '';
}

function safeId(value: string): string {
  return value.trim().replace(/[^a-zA-Z0-9_-]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 120) || String(Date.now());
}

function checksum(value: string): string {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = ((hash << 5) - hash + value.charCodeAt(index)) | 0;
  }
  return `c${Math.abs(hash)}`;
}

function blocked(provider: DirectSyncProvider, message: string, observedAt: string): DirectSyncReceipt {
  return { provider, status: 'blocked', message, records: 0, snapshots: 0, observedAt };
}

function error(provider: DirectSyncProvider, message: string, observedAt: string): DirectSyncReceipt {
  return { provider, status: 'error', message, records: 0, snapshots: 0, observedAt };
}
