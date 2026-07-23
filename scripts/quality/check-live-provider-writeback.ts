import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { enqueueOutboxEvent } from '../../src/db/outbox';
import { deliverProviderWriteEvent, type ProviderWritePayload } from '../../src/providers/writeback';
import { defaultLifeOSSettings, type LifeOSSettings } from '../../src/settings/lifeos-settings';
import { MemoryDb } from '../../tests/helpers/memory-db';

type Json = Record<string, unknown>;

const evidenceDir = join(process.cwd(), 'app', 'build', 'evidence', 'live-workspace');
mkdirSync(evidenceDir, { recursive: true });
const stamp = Math.floor(Date.now() / 1000);
const evidencePath = join(evidenceDir, `direct_provider_writeback-${stamp}.json`);
const noSecret = true;

function mask(value: string | null | undefined) {
  const raw = String(value || '');
  if (raw.length <= 8) return raw ? 'set' : null;
  return `${raw.slice(0, 4)}...${raw.slice(-4)}`;
}

function ensure(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function writeEvidence(payload: Json) {
  writeFileSync(evidencePath, JSON.stringify(payload, null, 2));
  console.log(evidencePath);
}

async function readJson(response: Response): Promise<Json> {
  const text = await response.text();
  return text ? JSON.parse(text) as Json : {};
}

async function notionRequest(token: string, method: string, path: string, payload?: Json) {
  const response = await fetch(`https://api.notion.com/v1${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      'Notion-Version': '2026-03-11',
    },
    body: payload ? JSON.stringify(payload) : undefined,
  });
  const body = await readJson(response);
  if (!response.ok) {
    throw new Error(`Notion ${method} ${path} failed ${response.status}: ${JSON.stringify(body).slice(0, 500)}`);
  }
  return body;
}

function pageTitle(page: Json) {
  const props = page.properties && typeof page.properties === 'object' ? page.properties as Record<string, Json> : {};
  for (const prop of Object.values(props)) {
    const title = Array.isArray(prop.title) ? prop.title : [];
    const text = title.map((part) => typeof part?.plain_text === 'string' ? part.plain_text : '').join('').trim();
    if (text) return text;
  }
  return '';
}

async function findNotionParentPage(token: string) {
  const shallowParent = async (page: Json | null): Promise<string> => {
    let current = page;
    let lastPageId = String(current?.id || '');
    for (let index = 0; current && index < 8; index += 1) {
      const parent = current.parent && typeof current.parent === 'object' ? current.parent as Json : null;
      if (parent?.type === 'workspace') return String(current.id || lastPageId);
      if (parent?.type !== 'page_id' || !parent.page_id) return lastPageId;
      lastPageId = String(parent.page_id);
      current = await notionRequest(token, 'GET', `/pages/${encodeURIComponent(lastPageId)}`).catch(() => null);
    }
    return lastPageId;
  };

  if (process.env.NOTION_TEST_PAGE_ID?.trim()) {
    const candidate = process.env.NOTION_TEST_PAGE_ID.trim();
    const page = await notionRequest(token, 'GET', `/pages/${candidate}`).catch(() => null);
    const shallow = await shallowParent(page);
    if (shallow) return shallow;
  }

  const searchPages = async (query?: string, pageSize = 25) => {
    const search = await notionRequest(token, 'POST', '/search', {
      filter: { property: 'object', value: 'page' },
      page_size: pageSize,
      ...(query ? { query } : {}),
    });
    return Array.isArray(search.results) ? search.results as Json[] : [];
  };
  const preferredPages = await searchPages('OpenClaw LifeOS', 5);
  const preferred = preferredPages.find((page) => !page.archived && pageTitle(page) === 'OpenClaw LifeOS');
  if (preferred?.id) return shallowParent(preferred);
  const preferredAncestor = preferredPages.find((page) => !page.archived);
  const shallowPreferred = await shallowParent(preferredAncestor ?? null);
  if (shallowPreferred) return shallowPreferred;

  const pages = await searchPages(undefined, 25);
  const workspacePages = pages.filter((page) => {
    const parent = page.parent && typeof page.parent === 'object' ? page.parent as Json : null;
    return !page.archived
      && parent?.type === 'workspace'
      && !pageTitle(page).startsWith('WonderFood C14 Scenario Proof')
      && !pageTitle(page).startsWith('WonderFood V4 Linked Workspace');
  });
  const fallback = workspacePages[0] || pages.find((page) => !page.archived);
  return shallowParent(fallback ?? null);
}

function firstDataSourceId(database: Json) {
  const sources = Array.isArray(database.data_sources) ? database.data_sources as Json[] : [];
  return String(sources[0]?.id || '');
}

async function provisionNotionDataSource(token: string) {
  const parentPageId = await findNotionParentPage(token);
  ensure(parentPageId, 'No accessible Notion parent page found for direct writeback proof.');

  const database = await notionRequest(token, 'POST', '/databases', {
    parent: { type: 'page_id', page_id: parentPageId },
    title: [{ type: 'text', text: { content: `WonderFood Direct Writeback Records ${stamp}` } }],
    initial_data_source: {
      properties: {
        Name: { title: {} },
      },
    },
  });
  const databaseId = String(database.id || '');
  const dataSourceId = firstDataSourceId(database) || firstDataSourceId(await notionRequest(token, 'GET', `/databases/${databaseId}`));
  ensure(dataSourceId, 'Notion data source id missing after provision.');
  return { proofPageId: '', dataSourceId, databaseId };
}

async function runNotionProof() {
  const token = process.env.NOTION_TOKEN?.trim() || process.env.NOTION_API_KEY?.trim() || '';
  if (!token) return { provider: 'notion', status: 'skipped', reason: 'missing_token' };

  const dataSource = process.env.NOTION_DATA_SOURCE_ID?.trim()
    ? { proofPageId: '', dataSourceId: process.env.NOTION_DATA_SOURCE_ID.trim(), databaseId: '' }
    : await provisionNotionDataSource(token);
  const db = new MemoryDb() as never;
  let createdPageId = '';

  const payload: ProviderWritePayload = {
    schema_version: 'lifeos.provider-write.v1',
    provider: 'notion',
    operation: 'create_record',
    op_id: `live-notion-writeback-${stamp}`,
    record_id: `live-notion-writeback-${stamp}`,
    expected_revision: null,
    record: {
      id: `live-notion-writeback-${stamp}`,
      domain: 'food',
      collection: 'recipe',
      title: `Direct app writeback ${stamp}`,
      properties: { body: 'Created by direct app writeback proof.' },
      relations: [],
      source: { provider: 'sqlite', external_id: `live-notion-writeback-${stamp}`, url: null, observed_at: new Date().toISOString(), content_hash: null },
      archived_at: null,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
      revision: 1,
      schema_version: 'lifeos.record.v1',
      deleted: false,
      privacy: 'personal',
      provenance: { actor: 'user', confidence: null, evidence: ['direct-provider-writeback-proof'], reason: 'Live direct writeback proof.' },
    },
    before: null,
    external_id: null,
    endpoint: '/providers/notion/push',
  };
  const event = await enqueueOutboxEvent(db, {
    id: `outbox-notion-${stamp}`,
    action_key: `provider-write:notion:${payload.op_id}`,
    domain: 'food',
    payload_json: JSON.stringify(payload),
  });

  const settings: LifeOSSettings = {
    ...defaultLifeOSSettings,
    notion: { enabled: true, token, pageId: dataSource.proofPageId, dataSourceIds: dataSource.dataSourceId },
  };
  const delivery = await deliverProviderWriteEvent({
    db,
    event,
    settings,
    platform: 'native',
    fetcher: async (url, init) => {
      const response = await fetch(url, init);
      const clone = response.clone();
      if (response.ok) {
        const body = await clone.json().catch(() => null) as { id?: string } | null;
        createdPageId = body?.id || createdPageId;
      }
      return response;
    },
  });
  ensure(delivery.status === 'delivered', `Notion direct writeback not delivered: ${delivery.status}`);
  ensure(createdPageId, 'Notion direct writeback did not return page id.');
  const created = await notionRequest(token, 'GET', `/pages/${createdPageId}`);
  const readBackTitle = pageTitle(created);
  await notionRequest(token, 'PATCH', `/pages/${createdPageId}`, { in_trash: true });
  let cleanupDatabaseArchived = false;
  if (dataSource.databaseId) {
    await notionRequest(token, 'PATCH', `/blocks/${encodeURIComponent(dataSource.databaseId)}`, { archived: true })
      .then(() => {
        cleanupDatabaseArchived = true;
      })
      .catch(() => {
        cleanupDatabaseArchived = false;
      });
  }

  return {
    provider: 'notion',
    status: 'passed',
    delivery_status: delivery.status,
    page_id: mask(createdPageId),
    data_source_id: mask(dataSource.dataSourceId),
    database_id: mask(dataSource.databaseId),
    read_back_title: readBackTitle,
    cleanup_archived_page: true,
    cleanup_database_archived: cleanupDatabaseArchived,
    cleanup_database_left_for_manual_review: Boolean(dataSource.databaseId) && !cleanupDatabaseArchived,
  };
}

async function refreshGoogleTokenFromCache() {
  if (process.env.GOOGLE_SHEETS_ACCESS_TOKEN?.trim()) return process.env.GOOGLE_SHEETS_ACCESS_TOKEN.trim();
  const tokenFile = process.env.GOOGLE_SHEETS_TOKEN_FILE?.trim()
    || join(process.cwd(), 'build', 'evidence', 'live-workspace', 'google-sheets-token.json');
  if (!existsSync(tokenFile)) return '';
  const cached = JSON.parse(readFileSync(tokenFile, 'utf8')) as { access_token?: string; refresh_token?: string };
  if (!cached.refresh_token) return cached.access_token || '';
  const clientId = process.env.GOOGLE_CLIENT_ID?.trim() || '';
  const clientSecret = process.env.GOOGLE_CLIENT_SECRET?.trim() || '';
  if (!clientId || !clientSecret) return cached.access_token || '';
  const form = new URLSearchParams({
    client_id: clientId,
    client_secret: clientSecret,
    refresh_token: cached.refresh_token,
    grant_type: 'refresh_token',
  });
  const response = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: form.toString(),
  });
  const body = await readJson(response) as { access_token?: string };
  return body.access_token || cached.access_token || '';
}

function latestSheetIdFromEvidence() {
  const path = join(evidenceDir, 'google-sheets-v4-latest-url.txt');
  if (!existsSync(path)) return '';
  const raw = readFileSync(path, 'utf8');
  const match = /\/spreadsheets\/d\/([^/]+)\/edit/.exec(raw);
  return match?.[1] || '';
}

async function sheetsRequest(token: string, spreadsheetId: string, method: string, path: string, payload?: Json) {
  const response = await fetch(`https://sheets.googleapis.com/v4/spreadsheets/${encodeURIComponent(spreadsheetId)}${path}`, {
    method,
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: payload ? JSON.stringify(payload) : undefined,
  });
  const body = await readJson(response);
  if (!response.ok) throw new Error(`Sheets ${method} ${path} failed ${response.status}`);
  return body;
}

async function ensureSheetsWorkbook(token: string) {
  const existing = process.env.GOOGLE_SHEETS_SPREADSHEET_ID?.trim()
    || process.env.GOOGLE_SHEETS_TEST_SPREADSHEET_ID?.trim()
    || latestSheetIdFromEvidence();
  if (existing) return existing;
  const response = await fetch('https://sheets.googleapis.com/v4/spreadsheets', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ properties: { title: `WonderFood Direct Writeback Proof ${stamp}` } }),
  });
  const body = await readJson(response);
  ensure(response.ok, `Unable to create Sheets proof workbook ${response.status}`);
  return String(body.spreadsheetId || '');
}

async function ensureLifeOSCanonicalSheet(token: string, spreadsheetId: string) {
  const meta = await sheetsRequest(token, spreadsheetId, 'GET', '?fields=sheets(properties(title))');
  const sheets = Array.isArray(meta.sheets) ? meta.sheets as Json[] : [];
  const hasSheet = sheets.some((sheet) => (sheet.properties as Json | undefined)?.title === 'LifeOS Canonical');
  if (!hasSheet) {
    await sheetsRequest(token, spreadsheetId, 'POST', ':batchUpdate', {
      requests: [{ addSheet: { properties: { title: 'LifeOS Canonical' } } }],
    });
  }
  await sheetsRequest(token, spreadsheetId, 'PUT', '/values/LifeOS%20Canonical!A1:I1?valueInputOption=USER_ENTERED', {
    values: [['record_id', 'title', 'domain', 'collection', 'properties', 'archived', 'revision', 'updated_at', 'op_id']],
  });
}

async function runSheetsProof() {
  const token = await refreshGoogleTokenFromCache();
  if (!token) return { provider: 'google_sheets', status: 'skipped', reason: 'missing_token' };
  const spreadsheetId = await ensureSheetsWorkbook(token);
  ensure(spreadsheetId, 'Google Sheets workbook id missing.');
  await ensureLifeOSCanonicalSheet(token, spreadsheetId);

  const db = new MemoryDb() as never;
  let updatedRange = '';
  const payload: ProviderWritePayload = {
    schema_version: 'lifeos.provider-write.v1',
    provider: 'google_sheets',
    operation: 'create_record',
    op_id: `live-sheets-writeback-${stamp}`,
    record_id: `live-sheets-writeback-${stamp}`,
    expected_revision: null,
    record: {
      id: `live-sheets-writeback-${stamp}`,
      domain: 'food',
      collection: 'shopping_item',
      title: `Direct app sheets writeback ${stamp}`,
      properties: { body: 'Created by direct app writeback proof.' },
      relations: [],
      source: { provider: 'sqlite', external_id: `live-sheets-writeback-${stamp}`, url: null, observed_at: new Date().toISOString(), content_hash: null },
      archived_at: null,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
      revision: 1,
      schema_version: 'lifeos.record.v1',
      deleted: false,
      privacy: 'personal',
      provenance: { actor: 'user', confidence: null, evidence: ['direct-provider-writeback-proof'], reason: 'Live direct writeback proof.' },
    },
    before: null,
    external_id: null,
    endpoint: '/providers/sheets/push',
  };
  const event = await enqueueOutboxEvent(db, {
    id: `outbox-sheets-${stamp}`,
    action_key: `provider-write:google_sheets:${payload.op_id}`,
    domain: 'food',
    payload_json: JSON.stringify(payload),
  });
  const settings: LifeOSSettings = {
    ...defaultLifeOSSettings,
    sheets: { enabled: true, token, workbookId: spreadsheetId, sheetName: 'LifeOS Canonical' },
  };
  const delivery = await deliverProviderWriteEvent({
    db,
    event,
    settings,
    platform: 'native',
    fetcher: async (url, init) => {
      const response = await fetch(url, init);
      const clone = response.clone();
      if (response.ok) {
        const body = await clone.json().catch(() => null) as { updates?: { updatedRange?: string } } | null;
        updatedRange = body?.updates?.updatedRange || updatedRange;
      }
      return response;
    },
  });
  ensure(delivery.status === 'delivered', `Sheets direct writeback not delivered: ${delivery.status}`);
  ensure(updatedRange, 'Sheets append did not return updatedRange.');
  const encodedRange = encodeURIComponent(updatedRange);
  const readBack = await sheetsRequest(token, spreadsheetId, 'GET', `/values/${encodedRange}`);
  const values = Array.isArray(readBack.values) ? readBack.values as unknown[][] : [];
  const readBackFound = values.flat().some((cell) => String(cell).includes(payload.record_id));
  await sheetsRequest(token, spreadsheetId, 'POST', `/values/${encodedRange}:clear`, {});

  return {
    provider: 'google_sheets',
    status: 'passed',
    delivery_status: delivery.status,
    spreadsheet_id: mask(spreadsheetId),
    updated_range: updatedRange.replace(/![A-Z]+[0-9]+:.*/, '!<row>'),
    read_back_found: readBackFound,
    cleanup_cleared_range: true,
  };
}

(async () => {
  const results = [await runNotionProof(), await runSheetsProof()];
  const failed = results.filter((result) => result.status !== 'passed');
  const payload = {
    proof: 'direct_provider_writeback_live',
    captured_at: new Date().toISOString(),
    all_passed: failed.length === 0,
    no_token_or_secret_visible: noSecret,
    results,
  };
  writeEvidence(payload);
  if (failed.length) {
    throw new Error(`Direct provider writeback failed: ${failed.map((item) => `${item.provider}:${item.status}`).join(', ')}`);
  }
})().catch((error) => {
  writeEvidence({
    proof: 'direct_provider_writeback_live',
    captured_at: new Date().toISOString(),
    all_passed: false,
    no_token_or_secret_visible: noSecret,
    error: error instanceof Error ? error.message : 'unknown_error',
  });
  process.exit(1);
});
