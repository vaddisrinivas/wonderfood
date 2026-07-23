import { createServer } from 'http';
import { handleServerChat, normalizeChatSendRequest, type ChatSendRequest } from './chat';
import { type NormalizedChatSend } from './chat';
import { handleMcpRequest } from './mcp/server';
import { ProviderOperation } from './providers/contracts';
import { discoverNotionDataSources } from './providers/notion/discovery';
import { readNotionConfig } from './providers/notion/client';
import { pullNotionRecords, pullNotionRecordsLive } from './providers/notion/pull';
import {
  normalizeWebhookBody,
  normalizeWebhookEvent,
  verifyNotionWebhookSignature,
} from './providers/notion/webhook';
import { getWebhookReplayState } from './providers/webhooks/notion';
import {
  normalizeSheetsWebhookEvent,
  getWebhookReplayState as getSheetsWebhookReplayState,
} from './providers/webhooks/sheets';
import { writeNotionRecord } from './providers/notion/push';
import { checkSheetsHealth } from './providers/sheets/health';
import { readSheetsConfig } from './providers/sheets/client';
import { writeSheetsRecord } from './providers/sheets/push';
import { pullSheetsRecords, pullSheetsRecordsLive } from './providers/sheets/pull';
import { syncSheetsFromWebhook } from './providers/sync/sheets';
import { syncNotionFromWebhook } from './providers/sync/notion';
import {
  ensureConversation,
  appendServerMessage,
  upsertConversation,
  listConversations,
  getConversation,
  setConversationResponseId,
} from './chat-storage';
import { ChatStreamEvent } from './responses';
import { getActionEvent, runUndo } from './mcp/state';
import {
  deleteHealthSnapshot,
  exportHealthSnapshots,
  listHealthSnapshots,
  saveHealthSnapshot,
} from './health/snapshots';

const port = Number(process.env.PORT ?? '8787');
const host = process.env.LIFEOS_SERVER_HOST?.trim() || '127.0.0.1';
const idempotencyCache = new Map<string, { messageId: string; runId: string; conversationId: string }>();
const runStatus = new Map<string, { status: 'running' | 'completed' | 'cancelled' | 'failed'; controller: AbortController; conversationId: string }>();
const runByConversation = new Map<string, string>();
const previousResponseByConversation = new Map<string, string>();

const DEFAULT_AUTH_TOKEN = process.env.LIFEOS_SERVER_TOKEN;
const CORS_ORIGINS = new Set(
  (process.env.LIFEOS_CORS_ORIGINS ?? 'http://localhost:8094,http://127.0.0.1:8094,http://localhost:8093,http://127.0.0.1:8093')
    .split(',')
    .map((origin: string) => origin.trim())
    .filter(Boolean),
);

function applyCors(req: any, res: any) {
  const origin = typeof req.headers?.origin === 'string' ? req.headers.origin : '';
  if (origin && CORS_ORIGINS.has(origin)) {
    res.setHeader('access-control-allow-origin', origin);
    res.setHeader('vary', 'origin');
  }
  res.setHeader('access-control-allow-headers', 'content-type, authorization');
  res.setHeader('access-control-allow-methods', 'DELETE, GET, POST, OPTIONS');
}

function json(value: unknown) {
  return JSON.stringify(value);
}

function setJson(res: any, status: number, body: unknown) {
  res.statusCode = status;
  res.setHeader('content-type', 'application/json');
  res.end(json(body));
}

function unauthorized(res: any, message: string) {
  setJson(res, 401, { status: 'error', message });
}

function badRequest(res: any, message: string) {
  setJson(res, 400, { status: 'error', message });
}

function notFound(res: any, message: string) {
  setJson(res, 404, { status: 'error', message });
}

function ok(res: any, body: unknown) {
  setJson(res, 200, body);
}

async function readJsonBody(req: any): Promise<Record<string, unknown>> {
  const chunks: string[] = [];
  for await (const chunk of req) {
    chunks.push(typeof chunk === 'string' ? chunk : chunk.toString());
  }
  const raw = chunks.join('');
  if (!raw.trim()) {
    return {};
  }
  return JSON.parse(raw) as Record<string, unknown>;
}

function getPath(rawUrl: string | undefined) {
  if (!rawUrl) return '/';
  return rawUrl.split('?')[0];
}

function assertAuth(req: any, res: any) {
  const auth = req.headers?.authorization;
  const token = DEFAULT_AUTH_TOKEN;
  if (!token || auth === `Bearer ${token}`) {
    return true;
  }
  unauthorized(res, 'Invalid server token');
  return false;
}

function notionWebhooksEnabled() {
  return process.env.LIFEOS_NOTION_WEBHOOKS_ENABLED?.trim().toLowerCase() === 'true';
}

function parseNumber(raw: unknown, fallback: number) {
  if (typeof raw !== 'string') {
    return fallback;
  }
  const value = Number(raw);
  return Number.isFinite(value) ? value : fallback;
}

function parseBooleanString(raw: unknown, fallback = false) {
  if (typeof raw !== 'string') {
    return fallback;
  }
  const normalized = raw.trim().toLowerCase();
  if (normalized === 'true' || normalized === '1' || normalized === 'yes') {
    return true;
  }
  if (normalized === 'false' || normalized === '0' || normalized === 'no') {
    return false;
  }
  return fallback;
}

function parseProviderOperation(raw: unknown): ProviderOperation | null {
  if (typeof raw !== 'string') {
    return null;
  }
  if (raw === 'create_record' || raw === 'update_record' || raw === 'archive_record') {
    return raw;
  }
  return null;
}

async function readRawBody(req: any): Promise<string> {
  const chunks: string[] = [];
  for await (const chunk of req) {
    chunks.push(typeof chunk === 'string' ? chunk : chunk.toString());
  }
  return chunks.join('');
}

function sendStopReply(res: any, id: string, status: 'running' | 'completed' | 'cancelled' | 'failed') {
  const run = runStatus.get(id);
  if (!run) {
    setJson(res, 404, { status: 'error', message: 'run_id not found' });
    return;
  }
  ok(res, { run_id: id, status, conversation_id: run.conversationId, run_status: run.status });
}

type ChatRunResponse = Awaited<ReturnType<typeof handleServerChat>>;
type StreamToken = (chunk: string) => void;

function sendStreamEvent(res: any, event: ChatStreamEvent) {
  res.write(`data: ${JSON.stringify(event)}\n\n`);
}

async function parseChatSend(req: any): Promise<NormalizedChatSend> {
  let payload: ChatSendRequest;
  try {
    payload = (await readJsonBody(req)) as ChatSendRequest;
  } catch {
    throw new Error('Invalid JSON');
  }
  return normalizeChatSendRequest(payload);
}

function getRunMessageText(
  thread: ReturnType<typeof getConversation>,
  retryOfMessageId: string | undefined,
  fallbackText: string,
) {
  if (!retryOfMessageId || !thread) {
    return fallbackText;
  }

  const target = thread.messages.find((message) => message.id === retryOfMessageId);
  if (!target) {
    return fallbackText;
  }

  return target.text;
}

async function runServerChat(params: {
  conversationId: string;
  message: string;
  threadTitle: string;
  detail: string;
  idempotencyKey: string;
  domainId: string;
  runId: string;
  previousResponseId?: string;
  retryOfMessageId?: string;
  userMessageId: string;
  appendUserMessage?: boolean;
  stream?: boolean;
  onModelToken?: StreamToken;
  planHint?: string;
  preview?: boolean;
}): Promise<ChatRunResponse> {
  const controller = new AbortController();
  const { conversationId, runId } = params;

  runStatus.set(runId, {
    status: 'running',
    controller,
    conversationId,
  });
  runByConversation.set(conversationId, runId);

  const shouldAppendUser = params.appendUserMessage !== false;
  if (shouldAppendUser) {
    appendServerMessage(conversationId, {
      id: params.userMessageId,
      role: 'user',
      text: params.message,
    });
  }

  let response: ChatRunResponse | null = null;

  try {
    response = await handleServerChat({
      conversationId,
      message: params.message,
      threadTitle: params.threadTitle,
      idempotencyKey: params.idempotencyKey,
      domainId: params.domainId,
      runId,
      signal: controller.signal,
      previousResponseId: params.previousResponseId,
      retryOfMessageId: params.retryOfMessageId,
      stream: params.stream,
      planHint: params.planHint,
      onModelToken: params.onModelToken,
      preview: params.preview,
    });
  } catch {
    response = {
      conversation_id: conversationId,
      messages: [
        {
          id: `server-${Date.now()}-asst`,
          role: 'assistant',
          text: 'I could not complete this response.',
          answer: undefined,
        },
      ],
      thread: {
        id: conversationId,
        title: params.threadTitle,
        detail: params.detail,
      },
      warnings: ['Server runtime failed.'],
      run: { id: runId, status: 'failed', needs_retry: true, aborted: false },
    };
  }

  if (!response) {
    response = {
      conversation_id: conversationId,
      messages: [],
      thread: {
        id: conversationId,
        title: params.threadTitle,
        detail: params.detail,
      },
      warnings: ['Server runtime produced no response.'],
      run: { id: runId, status: 'failed', needs_retry: true, aborted: false },
    };
  }

  const terminalRunStatus = response.run?.status
    ? response.run.status === 'canceled'
      ? 'cancelled'
      : response.run.status === 'completed'
        ? 'completed'
        : 'failed'
    : response.messages.length
      ? 'completed'
      : 'failed';

  runStatus.set(runId, {
    status: terminalRunStatus,
    controller,
    conversationId,
  });

  for (const message of response.messages) {
    appendServerMessage(conversationId, message);
    idempotencyCache.set(params.idempotencyKey, {
      messageId: message.id,
      runId,
      conversationId,
    });
  }

  if (response.run?.previous_response_id) {
    previousResponseByConversation.set(conversationId, response.run.previous_response_id);
    setConversationResponseId(conversationId, response.run.previous_response_id);
  }

  runByConversation.delete(conversationId);

  response.thread = {
    id: conversationId,
    title: params.threadTitle,
    detail: params.detail,
  };

  return response;
}

const server = createServer(async (req: any, res: any) => {
  applyCors(req, res);
  if (req.method === 'OPTIONS') {
    res.statusCode = 204;
    res.end();
    return;
  }

  const path = getPath(req.url);
  if (path.startsWith('/mcp')) {
    await handleMcpRequest(req, res);
    return;
  }

  if (req.method === 'GET' && path === '/health') {
    ok(res, { status: 'ok', service: 'wonderfood-lifeos-server' });
    return;
  }

  if (req.method === 'GET' && path === '/providers/status') {
    const notion = readNotionConfig();
    const sheets = readSheetsConfig();
    ok(res, {
      status: 'ok',
      authority: process.env.LIFEOS_AUTHORITY_PROVIDER?.trim() || 'notion',
      providers: {
        notion: {
          configured: Boolean(notion),
          data_source_id: notion?.dataSourceId || null,
          api_version: notion?.apiVersion || null,
          webhook_configured: Boolean(notion?.webhookSigningSecret),
        },
        google_sheets: {
          configured: Boolean(sheets),
          spreadsheet_id: sheets?.spreadsheetId || null,
          data_source_id: sheets?.dataSourceId || null,
          workbook_name: sheets?.workbookName || null,
        },
        openai: {
          configured: Boolean(process.env.OPENAI_API_KEY?.trim()),
          model: process.env.OPENAI_MODEL?.trim() || 'gpt-4.1-mini',
        },
      },
      secrets_exposed: false,
    });
    return;
  }

  if (path.startsWith('/health/connect')) {
    if (!assertAuth(req, res)) return;
    if (req.method === 'GET' && path === '/health/connect/snapshots') {
      ok(res, { status: 'ok', provider: 'health_connect', snapshots: listHealthSnapshots() });
      return;
    }
    if (req.method === 'GET' && path === '/health/connect/export') {
      res.setHeader('content-disposition', 'attachment; filename="lifeos-health-connect-export.json"');
      ok(res, {
        status: 'ok',
        provider: 'health_connect',
        exported_at: new Date().toISOString(),
        snapshots: exportHealthSnapshots(),
      });
      return;
    }
    if (req.method === 'POST' && path === '/health/connect/snapshot') {
      let payload: Record<string, unknown>;
      try {
        payload = await readJsonBody(req);
      } catch {
        badRequest(res, 'Invalid JSON');
        return;
      }
      const result = saveHealthSnapshot(payload as Parameters<typeof saveHealthSnapshot>[0]);
      if (!result.ok) {
        badRequest(res, result.message);
        return;
      }
      ok(res, result);
      return;
    }
    const snapshotMatch = path.match(/^\/health\/connect\/snapshot\/([^/]+)$/);
    if (req.method === 'DELETE' && snapshotMatch) {
      const result = deleteHealthSnapshot(decodeURIComponent(snapshotMatch[1]));
      if (!result.ok && result.status === 'not_found') {
        notFound(res, result.message);
        return;
      }
      if (!result.ok) {
        badRequest(res, result.message);
        return;
      }
      ok(res, result);
      return;
    }
    badRequest(res, 'Route not found');
    return;
  }

  if (path.startsWith('/providers/notion')) {
    if (path === '/providers/notion/webhook') {
      if (!notionWebhooksEnabled()) {
        notFound(res, 'Notion webhooks are disabled; use authenticated pull sync.');
        return;
      }
      if (req.method !== 'POST') {
        badRequest(res, 'Unsupported method');
        return;
      }
      const rawBody = await readRawBody(req);
      const parsed = normalizeWebhookBody(rawBody);
      if (!parsed) {
        badRequest(res, 'Invalid webhook JSON');
        return;
      }

      // Notion sends an unsigned one-time verification payload before event
      // delivery begins. Acknowledge it without echoing or persisting the
      // token; the operator must store it as the signing secret after copying
      // it from the Notion connection settings.
      const verificationToken =
        typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
          ? (parsed as Record<string, unknown>).verification_token
          : undefined;
      if (typeof verificationToken === 'string' && verificationToken.trim().length > 0) {
        ok(res, {
          status: 'verification_required',
          verification_token_present: true,
          signature_required_for_events: true,
        });
        return;
      }

      const signature = Array.isArray(req.headers['x-notion-signature'])
        ? req.headers['x-notion-signature'][0]
        : req.headers['x-notion-signature'];
      const webhookSignature = typeof signature === 'string' ? signature : undefined;

      if (!verifyNotionWebhookSignature(rawBody, webhookSignature)) {
        unauthorized(res, 'Invalid webhook signature');
        return;
      }

      const normalized = normalizeWebhookEvent(parsed);
      if (!normalized) {
        badRequest(res, 'Malformed webhook event');
        return;
      }

      // Webhooks are change signals, not canonical content. Reconcile by
      // refetching the configured data source before acknowledging ingress.
      const reconciliation = await syncNotionFromWebhook({ event: parsed });
      const replayAfterSync = getWebhookReplayState();
      const replayEntry = replayAfterSync.events.find((entry) => entry.event_id === reconciliation.eventId);

      ok(res, {
        status: reconciliation.status === 'duplicate' ? 'duplicate' : 'accepted',
        duplicate: reconciliation.status === 'duplicate',
        event_id: reconciliation.eventId || null,
        event_type: normalized.event_type || null,
        out_of_order: replayEntry?.out_of_order ?? false,
        replay_queue_size: replayAfterSync.events.length,
        duplicate_store: reconciliation.status === 'duplicate',
        data_source_id: reconciliation.dataSourceId || normalized.data_source_id || null,
        page_id: reconciliation.pageId || normalized.page_id || null,
        sync_status: reconciliation.status,
        sync_ok: reconciliation.ok && reconciliation.canonicalApplied !== false,
        sync_message: reconciliation.message,
        records_synced: reconciliation.records.length,
        canonical_applied: reconciliation.canonicalApplied ?? false,
        canonical_blocked_reason: reconciliation.canonicalBlockedReason || null,
        source_snapshot: reconciliation.sourceSnapshot,
        order_hint: {
          before: ((): string | undefined => {
            const hints = normalized.data && typeof normalized.data === 'object' && !Array.isArray(normalized.data) ? normalized.data : null;
            return typeof hints?.before === 'string' ? hints.before : undefined;
          })(),
          after: ((): string | undefined => {
            const hints = normalized.data && typeof normalized.data === 'object' && !Array.isArray(normalized.data) ? normalized.data : null;
            return typeof hints?.after === 'string' ? hints.after : undefined;
          })(),
        },
      });
      return;
    }

    if (!assertAuth(req, res)) {
      return;
    }

    const query = new URL(`http://127.0.0.1:${port}${req.url}`);
    if (req.method === 'GET' && path === '/providers/notion/discovery') {
      ok(res, discoverNotionDataSources(readNotionConfig() || undefined));
      return;
    }

    if (req.method === 'GET' && path === '/providers/notion/pull') {
      const domain = query.searchParams.get('domain');
      const collection = query.searchParams.get('collection');
      const limit = query.searchParams.get('limit');
      const live = parseBooleanString(query.searchParams.get('live'), false);
      const payload = {
        domain: domain ?? undefined,
        collection: collection ?? undefined,
        limit: limit ? parseNumber(limit, 50) : undefined,
      };
      if (live) {
        const result = await pullNotionRecordsLive(payload);
        ok(res, result);
        return;
      }
      ok(res, pullNotionRecords(payload));
      return;
    }

    if (req.method === 'POST' && path === '/providers/notion/pull') {
      let payload: { domain?: string; collection?: string; limit?: number; live?: boolean };
      try {
        payload = (await readJsonBody(req)) as typeof payload;
      } catch {
        badRequest(res, 'Invalid JSON');
        return;
      }
      if (payload.live) {
        const result = await pullNotionRecordsLive(payload);
        ok(res, result);
        return;
      }
      ok(res, pullNotionRecords(payload));
      return;
    }

    if (req.method === 'POST' && path === '/providers/notion/push') {
      let payload: {
        operation?: string;
        recordId?: string;
        pageId?: string;
        domain?: string;
        collection?: string;
        title?: string;
        properties?: Record<string, unknown>;
        archived?: boolean;
        externalId?: string;
      };
      try {
        payload = (await readJsonBody(req)) as typeof payload;
      } catch {
        badRequest(res, 'Invalid JSON');
        return;
      }
      const operation = parseProviderOperation(payload?.operation);
      if (!operation) {
        badRequest(res, 'operation must be create_record, update_record, or archive_record');
        return;
      }
      const recordId = typeof payload.recordId === 'string' ? payload.recordId.trim() : '';
      if (!recordId) {
        badRequest(res, 'recordId required');
        return;
      }
      const result = await writeNotionRecord({
        operation,
        recordId,
        pageId: typeof payload.pageId === 'string' && payload.pageId.trim() ? payload.pageId.trim() : undefined,
        domain: payload.domain ?? 'food',
        collection: payload.collection ?? 'recipe',
        title: payload.title,
        properties: payload.properties,
        archived: payload.archived,
        externalId: payload.externalId,
      });
      if (!result.ok) {
        badRequest(res, result.error || 'Notion write failed');
        return;
      }
      ok(res, result);
      return;
    }

    badRequest(res, 'Route not found');
    return;
  }

  if (path.startsWith('/providers/sheets')) {
    if (path === '/providers/sheets/webhook') {
      if (req.method !== 'POST') {
        badRequest(res, 'Unsupported method');
        return;
      }
      // Google Sheets has no native signed webhook envelope in this adapter;
      // require the same bearer boundary as other hosted provider writes.
      if (!assertAuth(req, res)) {
        return;
      }
      const rawBody = await readRawBody(req);
      let webhookPayload: unknown;
      try {
        webhookPayload = JSON.parse(rawBody);
      } catch {
        badRequest(res, 'Invalid JSON');
        return;
      }
      const normalized = normalizeSheetsWebhookEvent(webhookPayload);
      if (!normalized) {
        badRequest(res, 'Malformed webhook event');
        return;
      }
      // Sheets events are also change signals. Pull the authoritative range
      // before returning, while the persisted replay marker makes retries safe.
      const reconciliation = await syncSheetsFromWebhook({ event: normalized });
      const replayStore = getSheetsWebhookReplayState();
      ok(res, {
        status: reconciliation.status === 'duplicate' ? 'duplicate' : 'accepted',
        duplicate: reconciliation.status === 'duplicate',
        event_id: reconciliation.eventId || null,
        spreadsheet_id: reconciliation.spreadsheetId || normalized.spreadsheet_id || null,
        data_source_id: reconciliation.dataSourceId || normalized.data_source_id || null,
        range: reconciliation.range || normalized.range || null,
        row: reconciliation.row || normalized.row || null,
        out_of_order: replayStore.events.some((entry) => entry.event_id === reconciliation.eventId && entry.out_of_order),
        replay_queue_size: replayStore.events.length,
        sync_status: reconciliation.status,
        sync_ok: reconciliation.ok && reconciliation.canonicalApplied !== false,
        sync_message: reconciliation.message,
        records_synced: reconciliation.records.length,
        canonical_applied: reconciliation.canonicalApplied ?? false,
        canonical_blocked_reason: reconciliation.canonicalBlockedReason || null,
        source_snapshot: reconciliation.sourceSnapshot,
        order_hint: {
          before: normalized.before || undefined,
          after: normalized.after || undefined,
        },
      });
      return;
    }

    if (!assertAuth(req, res)) {
      return;
    }

    const query = new URL(`http://127.0.0.1:${port}${req.url}`);
    if (req.method === 'GET' && path === '/providers/sheets/health') {
      ok(res, checkSheetsHealth());
      return;
    }

    if (req.method === 'GET' && path === '/providers/sheets/pull') {
      const domain = query.searchParams.get('domain');
      const collection = query.searchParams.get('collection');
      const live = parseBooleanString(query.searchParams.get('live'), false);
      const payload = {
        domain: domain ?? undefined,
        collection: collection ?? undefined,
      };
      if (live) {
        const result = await pullSheetsRecordsLive(payload);
        ok(res, result);
        return;
      }
      ok(res, pullSheetsRecords(payload));
      return;
    }

    if (req.method === 'POST' && path === '/providers/sheets/pull') {
      let payload: { domain?: string; collection?: string; live?: boolean };
      try {
        payload = (await readJsonBody(req)) as typeof payload;
      } catch {
        badRequest(res, 'Invalid JSON');
        return;
      }
      if (payload.live) {
        const result = await pullSheetsRecordsLive(payload);
        ok(res, result);
        return;
      }
      ok(res, pullSheetsRecords(payload));
      return;
    }

    if (req.method === 'POST' && path === '/providers/sheets/push') {
      let payload: {
        operation?: string;
        id?: string;
        recordId?: string;
        domain?: string;
        collection?: string;
        title?: string;
        properties?: Record<string, unknown>;
        archived?: boolean;
        externalId?: string;
        source?: Record<string, unknown>;
        version?: number;
        expected_version?: number;
        expected_digest?: string;
      };
      try {
        payload = (await readJsonBody(req)) as typeof payload;
      } catch {
        badRequest(res, 'Invalid JSON');
        return;
      }
      const operation = parseProviderOperation(payload?.operation);
      if (!operation) {
        badRequest(res, 'operation must be create_record, update_record, or archive_record');
        return;
      }
      const recordId = typeof payload.recordId === 'string'
        ? payload.recordId.trim()
        : typeof payload.id === 'string'
          ? payload.id.trim()
          : '';
      if (!recordId) {
        badRequest(res, 'record id required');
        return;
      }
      const result = await writeSheetsRecord({
        operation,
        record: {
          id: recordId,
          domain: payload.domain ?? 'food',
          collection: payload.collection ?? 'recipe',
          title: payload.title ?? recordId,
          properties: payload.properties,
          archived: payload.archived,
          source: payload.source,
          externalId: payload.externalId,
          version: typeof payload.version === 'number' && Number.isFinite(payload.version) ? payload.version : undefined,
          expectedVersion: typeof payload.expected_version === 'number' && Number.isFinite(payload.expected_version)
            ? payload.expected_version
            : undefined,
          expectedDigest: typeof payload.expected_digest === 'string' ? payload.expected_digest : undefined,
        },
      });
      if (!result.ok) {
        if (result.conflict) {
          setJson(res, 409, {
            status: 'conflict',
            message: result.error || 'Sheets write conflict',
            conflict: result.conflict,
          });
          return;
        }
        badRequest(res, result.error || 'Sheets write failed');
        return;
      }
      ok(res, result);
      return;
    }

    if (req.method === 'POST' && path === '/providers/sheets/sync') {
      let payload: {
        event?: unknown;
        domain?: string;
        collection?: string;
        limit?: number;
      };
      try {
        payload = (await readJsonBody(req)) as typeof payload;
      } catch {
        badRequest(res, 'Invalid JSON');
        return;
      }
      const event = payload?.event as unknown;
      const sync = await syncSheetsFromWebhook({
        event,
        domain: payload.domain,
        collection: payload.collection,
        limit: payload.limit,
      });
      ok(res, sync);
      return;
    }

    badRequest(res, 'Route not found');
    return;
  }

  if (path === '/chat/threads' && req.method === 'GET') {
    const query = new URL(`http://127.0.0.1:${port}${req.url}`);
    const domain = query.searchParams.get('domain');
    const rows = listConversations();
    const filtered = domain ? rows.filter((row) => row.domain === domain) : rows;
    ok(res, {
      threads: filtered.map((thread) => ({
        id: thread.id,
        domain: thread.domain,
        title: thread.title,
        detail: thread.detail,
        updated_at: new Date().toISOString(),
      })),
    });
    return;
  }

  if (path === '/chat/run' && req.method === 'GET') {
    const query = new URL(`http://127.0.0.1:${port}${req.url}`);
    const conversationId = query.searchParams.get('conversation_id');
    if (!conversationId) {
      badRequest(res, 'conversation_id required');
      return;
    }
    const runId = runByConversation.get(conversationId);
    if (!runId) {
      ok(res, {
        conversation_id: conversationId,
        active: false,
        status: 'idle',
        run_id: null,
      });
      return;
    }
    const run = runStatus.get(runId);
    ok(res, {
      conversation_id: conversationId,
      active: true,
      status: run?.status ?? 'failed',
      run_id: runId,
    });
    return;
  }

  if (path.startsWith('/chat/threads/') && req.method === 'GET') {
    const parts = path.split('/');
    const threadId = parts[parts.length - 1];
    const thread = getConversation(threadId);
    if (!thread) {
      badRequest(res, 'thread not found');
      return;
    }
    ok(res, thread);
    return;
  }

  if (!path.startsWith('/chat')) {
    badRequest(res, 'Route not found');
    return;
  }

  if (req.method === 'POST' && path === '/chat/send/stream') {
    if (!assertAuth(req, res)) {
      return;
    }

    try {
      const parsed = await parseChatSend(req);
      const conversation = ensureConversation(
        parsed.threadId,
        parsed.domainId,
        parsed.message.text.slice(0, 80),
      );
      upsertConversation({
        id: conversation.id,
        domain: conversation.domain,
        title: conversation.title,
        detail: conversation.detail,
      });
      const runId = `run-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
      const idempotencyKey = parsed.idempotencyKey;
      const previousResponseId = parsed.previousResponseId
        ?? previousResponseByConversation.get(conversation.id)
        ?? conversation.last_response_id;
      const existing = idempotencyCache.get(idempotencyKey);
      if (existing) {
        const prior = getConversation(existing.conversationId)?.messages.find((item) => item.id === existing.messageId);
        if (prior) {
          const thread = getConversation(conversation.id);
          const cachedResponse: ChatRunResponse = {
            conversation_id: conversation.id,
            messages: [prior],
            thread: thread
              ? {
                  id: thread.id,
                  title: thread.title,
                  detail: thread.detail,
                }
              : {
                  id: conversation.id,
                  title: conversation.title,
                  detail: conversation.detail,
                },
            run: { id: existing.runId, status: 'completed' as const, needs_retry: false, aborted: false },
            warnings: ['Idempotency key replayed; returned prior answer.'],
          };
          res.writeHead(200, {
            'content-type': 'text/event-stream',
            'cache-control': 'no-cache',
            connection: 'keep-alive',
          });
          sendStreamEvent(res, {
            type: 'cache',
            conversation_id: conversation.id,
            response: cachedResponse,
          });
          res.end();
          return;
        }
      }

      const runByMessage = runByConversation.get(conversation.id);
      if (runByMessage) {
        const existingRun = runStatus.get(runByMessage);
        if (existingRun?.status === 'running') {
          res.writeHead(200, {
            'content-type': 'text/event-stream',
            'cache-control': 'no-cache',
            connection: 'keep-alive',
          });
          sendStreamEvent(res, {
            type: 'error',
            conversation_id: conversation.id,
            error: `A run is already active for conversation ${conversation.id}.`,
          });
          res.end();
          return;
        }
      }

      const runMessageText = getRunMessageText(
        getConversation(conversation.id),
        parsed.retryOfMessageId,
        parsed.message.text,
      );

      res.writeHead(200, {
        'content-type': 'text/event-stream',
        'cache-control': 'no-cache',
        connection: 'keep-alive',
      });
      sendStreamEvent(res, {
        type: 'run.start',
        run_id: runId,
        conversation_id: conversation.id,
        thread_id: conversation.id,
      });

      const finalResponse = await runServerChat({
        conversationId: conversation.id,
        message: runMessageText,
        threadTitle: conversation.title,
        detail: conversation.detail,
        idempotencyKey,
        domainId: conversation.domain,
        runId,
        previousResponseId,
        retryOfMessageId: parsed.retryOfMessageId,
        userMessageId: parsed.userMessageId || `user-${Date.now()}`,
        appendUserMessage: !parsed.retryOfMessageId,
        stream: true,
        onModelToken: (chunk) => {
          if (chunk) {
            sendStreamEvent(res, {
              type: 'token',
              run_id: runId,
              conversation_id: conversation.id,
              delta: chunk,
            });
          }
        },
        planHint: parsed.planHint,
        preview: parsed.preview,
      });

      if (!res.writableEnded) {
        sendStreamEvent(res, {
          type: 'run.end',
          run_id: runId,
          conversation_id: conversation.id,
          response: finalResponse ?? null,
        });
        res.end();
      }
    } catch (error) {
      badRequest(
        res,
        error instanceof Error && error.message === 'Invalid JSON'
          ? 'Invalid JSON'
          : error instanceof Error
            ? error.message
            : 'Invalid chat request',
      );
    }
    return;
  }

  if (req.method === 'POST' && path === '/chat/send') {
    if (!assertAuth(req, res)) {
      return;
    }

    try {
      const parsed = await parseChatSend(req);
      const conversation = ensureConversation(
        parsed.threadId,
        parsed.domainId,
        parsed.message.text.slice(0, 80),
      );
      upsertConversation({
        id: conversation.id,
        domain: conversation.domain,
        title: conversation.title,
        detail: conversation.detail,
      });
      const runId = `run-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
      const idempotencyKey = parsed.idempotencyKey;
      const previousResponseId = parsed.previousResponseId
        ?? previousResponseByConversation.get(conversation.id)
        ?? conversation.last_response_id;
      const existing = idempotencyCache.get(idempotencyKey);
      if (existing) {
        const prior = getConversation(existing.conversationId)?.messages.find((item) => item.id === existing.messageId);
        if (prior) {
          const thread = getConversation(conversation.id);
          const cachedResponse: ChatRunResponse = {
            conversation_id: conversation.id,
            messages: [prior],
            thread: thread
              ? {
                  id: thread.id,
                  title: thread.title,
                  detail: thread.detail,
                }
              : {
                  id: conversation.id,
                  title: conversation.title,
                  detail: conversation.detail,
                },
            run: {
              id: existing.runId,
              status: 'completed' as const,
              needs_retry: false,
              aborted: false,
            },
            warnings: ['Idempotency key replayed; returned prior answer.'],
          };
          ok(res, cachedResponse);
          return;
        }
      }

      const runMessageText = getRunMessageText(
        getConversation(conversation.id),
        parsed.retryOfMessageId,
        parsed.message.text,
      );

      const response = await runServerChat({
        conversationId: conversation.id,
        message: runMessageText,
        threadTitle: conversation.title,
        detail: conversation.detail,
        idempotencyKey,
        domainId: conversation.domain,
        runId,
        previousResponseId,
        retryOfMessageId: parsed.retryOfMessageId,
        userMessageId: parsed.userMessageId || `user-${Date.now()}`,
        appendUserMessage: !parsed.retryOfMessageId,
        planHint: parsed.planHint,
        preview: parsed.preview,
      });

      ok(res, response);
    } catch (error) {
      badRequest(
        res,
        error instanceof Error && error.message === 'Invalid JSON'
          ? 'Invalid JSON'
          : error instanceof Error
            ? error.message
            : 'Invalid chat request',
      );
    }
    return;
  }

  if (req.method === 'POST' && path === '/chat/stop') {
    if (!assertAuth(req, res)) {
      return;
    }

    let payload: { run_id?: string };
    try {
      payload = (await readJsonBody(req)) as typeof payload;
    } catch {
      badRequest(res, 'Invalid JSON');
      return;
    }

    if (!payload.run_id) {
      badRequest(res, 'run_id required');
      return;
    }

    const run = runStatus.get(payload.run_id);
    if (!run) {
      badRequest(res, 'Unknown run');
      return;
    }
    if (run.status === 'completed' || run.status === 'failed' || run.status === 'cancelled') {
      ok(res, { run_id: payload.run_id, status: run.status });
      return;
    }
    run.controller.abort();
    run.status = 'cancelled';
    sendStopReply(res, payload.run_id, 'cancelled');
    return;
  }

  if (req.method === 'POST' && path === '/chat/retry') {
    if (!assertAuth(req, res)) {
      return;
    }

    let payload: {
      conversation_id?: string;
      user_message_id?: string;
      idempotency_key?: string;
      previous_response_id?: string;
    };
    try {
      payload = await readJsonBody(req);
    } catch {
      badRequest(res, 'Invalid JSON');
      return;
    }

    if (!payload.conversation_id || !payload.user_message_id) {
      badRequest(res, 'conversation_id and user_message_id required');
      return;
    }

    const conversationId = payload.conversation_id;
    const userMessageId = payload.user_message_id;
    const thread = getConversation(payload.conversation_id);
    if (!thread) {
      badRequest(res, 'conversation not found');
      return;
    }
    const target = thread.messages.find((message) => message.id === payload.user_message_id && message.role === 'user') as
      | ({ text: string } & { id: string; role: 'user' | 'assistant' })
      | undefined;
    if (!target) {
      badRequest(res, 'target user message not found');
      return;
    }
    const idempotencyKey = payload.idempotency_key ?? `${conversationId}:${userMessageId}:retry`;
    const previousResponseId =
      typeof payload.previous_response_id === 'string' && payload.previous_response_id.trim()
        ? payload.previous_response_id
        : previousResponseByConversation.get(conversationId);

    const wrapped = await runServerChat({
      conversationId,
      message: target.text,
      threadTitle: thread.title,
      detail: thread.detail,
      idempotencyKey,
      domainId: thread.domain,
      runId: `run-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      previousResponseId,
      retryOfMessageId: userMessageId,
      userMessageId,
      appendUserMessage: false,
    });

    ok(res, wrapped);
    return;
  }

  if (req.method === 'POST' && path === '/chat/action') {
    if (!assertAuth(req, res)) {
      return;
    }

    let payload: { conversation_id?: string; action?: string; value?: string; domain_id?: string };
    try {
      payload = await readJsonBody(req);
    } catch {
      badRequest(res, 'Invalid JSON');
      return;
    }
    if (!payload?.conversation_id || !payload?.action) {
      badRequest(res, 'conversation_id and action required');
      return;
    }

    const thread = getConversation(payload.conversation_id);
    if (!thread) {
      badRequest(res, 'conversation not found');
      return;
    }

    const nextTitle =
      payload.action === 'rename' && typeof payload.value === 'string' && payload.value.trim()
        ? payload.value.slice(0, 80)
        : thread.title;
    const nextDetail =
      payload.action === 'pin'
        ? `${thread.detail} · pinned`
        : payload.action === 'archive'
          ? `${thread.detail} · archived`
          : thread.detail;

    const next = upsertConversation({
      id: thread.id,
      domain: payload.domain_id || thread.domain,
      title: nextTitle || thread.title,
      detail: nextDetail,
    });

    ok(res, {
      action: payload.action,
      status: 'ok',
      conversation: { id: next.id, title: next.title, detail: next.detail },
    });
    return;
  }

  if (req.method === 'POST' && path === '/chat/undo') {
    if (!assertAuth(req, res)) {
      return;
    }

    let payload: { action_id?: string; idempotency_key?: string; actor?: string };
    try {
      payload = await readJsonBody(req);
    } catch {
      badRequest(res, 'Invalid JSON');
      return;
    }

    const actionId = typeof payload.action_id === 'string' ? payload.action_id.trim() : '';
    if (!actionId) {
      badRequest(res, 'action_id required');
      return;
    }

    const action = getActionEvent(actionId);
    if (!action) {
      badRequest(res, 'action not found');
      return;
    }

    if (action.status === 'cancelled') {
      ok(res, {
        status: 'completed',
        action_id: actionId,
        action,
        undo_result: {
          success: true,
          message: 'Action already undone',
          replayed: true,
          actor: payload.actor?.trim() || 'hearth',
        },
      });
      return;
    }

    const result = runUndo(actionId);
    if (!result.success) {
      badRequest(res, result.message);
      return;
    }

    ok(res, {
      status: 'completed',
      action_id: actionId,
      action: result.action,
      undo_result: {
        success: result.success,
        message: result.message,
        actor: payload.actor?.trim() || 'hearth',
        idempotency_key: typeof payload.idempotency_key === 'string' ? payload.idempotency_key : undefined,
      },
    });
    return;
  }

  badRequest(res, 'Unsupported method');
});

server.listen(port, host, () => {
  const displayHost = host === '0.0.0.0' ? 'localhost' : host;
  console.log(`[server] listening on http://${displayHost}:${port}`);
});

export { server };
