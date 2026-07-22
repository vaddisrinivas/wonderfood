import { createHash } from 'node:crypto';
import { callMcpTool, } from '../src/mcp/tools';
import { pullSheetsRecordsLive } from '../src/providers/sheets/pull';
import { writeSheetsRecord } from '../src/providers/sheets/push';

type MockCall = {
  url: string;
  method: string;
  body: string;
};

type LiveSheetsState = {
  spreadsheetId: string;
  sheetName: string;
  rows: string[][];
};

function parseRangeRow(range: string) {
  const match = /!A([0-9]{1,})/.exec(range);
  if (!match) {
    return null;
  }
  const row = Number.parseInt(match[1], 10);
  return Number.isNaN(row) || row <= 0 ? null : row;
}

function createHashDigest(values: string[]) {
  return createHash('sha256').update(JSON.stringify(values)).digest('hex');
}

function withMockSheetsFetch(state: LiveSheetsState, onCalls: (calls: MockCall[]) => void) {
  const calls: MockCall[] = [];
  const originalFetch = globalThis.fetch;
  let nextRequestId = 1;

  globalThis.fetch = (async (input: string | URL, init: RequestInit = {}) => {
    const url = typeof input === 'string' ? input : input.toString();
    const method = (init.method || 'GET').toUpperCase();
    const body = typeof init.body === 'string' ? init.body : '';
    calls.push({ url, method, body });

    const isMetadata = /\/spreadsheets\/[^/]+\/?$/.test(url);
    if (method === 'GET' && isMetadata) {
      return new Response(JSON.stringify({
        spreadsheetId: state.spreadsheetId,
        properties: { title: 'LifeOS Runtime Workbook' },
        sheets: [{ properties: { title: state.sheetName, gridProperties: { columnCount: 26, rowCount: 100 } } }],
      }), { status: 200 });
    }

    if (method === 'GET' && url.includes('/values:batchGet')) {
      return new Response(JSON.stringify({
        valueRanges: [{ range: `${state.sheetName}!A:Z`, values: state.rows }],
      }), { status: 200 });
    }

    if (method === 'POST' && url.includes('/values:batchUpdate')) {
      const payload = (() => {
        try {
          return JSON.parse(body);
        } catch {
          return null;
        }
      })();
      const updates = Array.isArray(payload?.data) ? payload.data : [];
      for (const update of updates) {
        const range = String(update?.range ?? '');
        const row = parseRangeRow(range);
        if (!row) {
          return new Response(JSON.stringify({ error: 'invalid range' }), { status: 400 });
        }
        const values = Array.isArray(update?.values?.[0]) ? update.values[0] : [];
        while (state.rows.length < row) {
          state.rows.push([]);
        }
        state.rows[row - 1] = values;
      }
      return new Response(JSON.stringify({ responses: [{ updatedRange: `${state.sheetName}!A${nextRequestId + 1}` }] }), { status: 200 });
    }

    return new Response(JSON.stringify({ error: `unexpected endpoint ${method} ${url}` }), { status: 500 });
  }) as typeof globalThis.fetch;

  return {
    calls,
    finalize() {
      onCalls(calls);
      globalThis.fetch = originalFetch;
    },
  };
}

function ensure(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

(async () => {
  process.env.GOOGLE_SHEETS_ACCESS_TOKEN = 'test-token';
  process.env.GOOGLE_SHEETS_SPREADSHEET_ID = 'sheet-test-1';
  process.env.GOOGLE_SHEETS_DATA_SOURCE_ID = 'phase6-data-source';

  const state: LiveSheetsState = {
    spreadsheetId: process.env.GOOGLE_SHEETS_SPREADSHEET_ID,
    sheetName: 'LifeOS Runtime',
    rows: [
      ['id', 'title', 'domain', 'collection', 'properties', 'archived', 'version', 'updated_at', 'source', 'external_id', 'legacy_note'],
      ['sheet-phase6-adapter', 'Phase 6 Contract', 'food', 'recipe', '{"legacy":"preserved"}', 'false', '1', '2026-01-01T00:00:00.000Z', '', 'sheet-phase6-adapter', 'legacy'],
    ],
  };

  const contract = withMockSheetsFetch(state, () => {});
  const pullResult = await pullSheetsRecordsLive();
  ensure(pullResult.status === 'ready', 'Expected pull to be ready');
  ensure(Array.isArray(pullResult.source_snapshots), 'Expected source_snapshots to exist');
  ensure(pullResult.source_snapshots[0]?.provider_fields?.legacy_note === 'legacy', 'Expected provider-owned unsupported fields to pass through');

  const firstSnapshot = pullResult.source_snapshots[0];
  const expectedDigest = createHashDigest(state.rows[1]);
  ensure(firstSnapshot.value_digest === expectedDigest, 'Expected canonical value digest hash to match row digest');
  ensure((firstSnapshot as { data_source_id?: string }).data_source_id === process.env.GOOGLE_SHEETS_DATA_SOURCE_ID, 'Expected pull source snapshot to include data_source_id');

  const batchGetCalls = contract.calls.filter((entry) => entry.method === 'GET' && entry.url.includes('/values:batchGet')).length;
  ensure(batchGetCalls === 1, `Expected one batchGet call, got ${batchGetCalls}`);

  const writeResult = await writeSheetsRecord({
    operation: 'update_record',
    record: {
      id: 'sheet-phase6-adapter',
      domain: 'food',
      collection: 'recipe',
      title: 'Phase 6 Updated',
      properties: { legacy: 'preserved' },
      archived: false,
      externalId: 'sheet-phase6-adapter',
    },
  });
  ensure(writeResult.ok, `Expected write to succeed: ${writeResult.error}`);
  ensure(writeResult.source_snapshot?.provider_fields?.legacy_note === 'legacy', 'Expected update write to preserve unsupported fields');
  ensure(typeof writeResult.source_snapshot?.range === 'string' && writeResult.source_snapshot.range.includes('!A'), 'Expected update write source snapshot range');
  ensure(!writeResult.noChange, 'Expected write to detect mutation');
  const batchUpdateCalls = contract.calls.filter((entry) => entry.method === 'POST' && entry.url.includes('/values:batchUpdate')).length;
  ensure(batchUpdateCalls === 1, `Expected one batchUpdate call, got ${batchUpdateCalls}`);

  const noChangeCallState = withMockSheetsFetch(state, () => {});
  const noChangeState = await writeSheetsRecord({
    operation: 'update_record',
    record: {
      id: 'sheet-phase6-adapter',
      domain: 'food',
      collection: 'recipe',
      title: 'Phase 6 Updated',
      properties: { legacy: 'preserved' },
      archived: false,
      externalId: 'sheet-phase6-adapter',
    },
  });
  ensure(noChangeState.ok, `Expected no-change write to succeed: ${noChangeState.error}`);
  ensure(noChangeState.noChange === true, 'Expected no-change write branch');
  ensure(noChangeState.source_snapshot?.noChange === true, 'Expected no-change source snapshot marker');
  const noChangeBatchUpdate = noChangeCallState.calls.filter((entry) => entry.method === 'POST' && entry.url.includes('/values:batchUpdate')).length;
  ensure(noChangeBatchUpdate === 0, `Expected no batchUpdate on no-change write, got ${noChangeBatchUpdate}`);
  noChangeCallState.finalize();

  const pullContractIdem = await pullSheetsRecordsLive();
  const rowDigestAfter = createHashDigest(state.rows[1]);
  ensure(
    pullContractIdem.source_snapshots[0]?.revision === rowDigestAfter,
    'Expected pull revision to match persisted row hash for parity',
  );

  const canonicalResult = await pullSheetsRecordsLive();
  ensure(
    canonicalResult.source_snapshots[0]?.value_digest === canonicalResult.source_snapshots[0]?.revision,
    'Expected revision and value digest parity in source snapshot',
  );

  const createMcpResult = await callMcpTool('wonderfood.create_record', {
    actor: 'hearth',
    domain: 'food',
    collection: 'recipe',
    data_home: 'google_sheets',
    id: 'sheet-phase6-nochange',
    title: 'MCP no-change guard',
    properties: { legacy: 'guard' },
  });
  const createdId = (createMcpResult.json?.record as { id?: string })?.id;
  ensure(typeof createdId === 'string' && createdId.length > 0, 'Expected MCP create_record to return new record');

  const mcpUpdateNoChange = await callMcpTool('wonderfood.update_record', {
    actor: 'hearth',
    id: createdId,
    data_home: 'google_sheets',
    patch: {
      title: 'MCP no-change guard',
      properties: { legacy: 'guard' },
    },
  });
  ensure(!('undo_token' in mcpUpdateNoChange), 'Expected no undo token on no-change MCP update');
  ensure(!mcpUpdateNoChange.receipts, 'Expected no undo receipts on no-change MCP update');

  contract.finalize();
  console.log('PASS server/test/sheets-adapter-contract.ts');
  return;
})().catch((error) => {
  process.exitCode = 1;
  throw error;
});
