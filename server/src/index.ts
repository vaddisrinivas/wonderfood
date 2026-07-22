import { createServer } from 'http';
import { handleServerChat } from './chat';
import { ServerChatMessage } from './chat';
import {
  ensureConversation,
  appendServerMessage,
  upsertConversation,
  listConversations,
  getConversation,
} from './conversations';

const port = Number(process.env.PORT ?? '8787');
const idempotencyCache = new Map<string, { messageId: string; runId: string; conversationId: string }>();
const runStatus = new Map<string, { status: 'running' | 'completed' | 'cancelled' | 'failed'; controller: AbortController; conversationId: string }>();
const runByConversation = new Map<string, string>();
const previousResponseByConversation = new Map<string, string>();

const DEFAULT_AUTH_TOKEN = process.env.LIFEOS_SERVER_TOKEN;

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

function parseNumber(raw: unknown, fallback: number) {
  if (typeof raw !== 'string') {
    return fallback;
  }
  const value = Number(raw);
  return Number.isFinite(value) ? value : fallback;
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
  const path = getPath(req.url);

  if (req.method === 'GET' && path === '/health') {
    ok(res, { status: 'ok', service: 'wonderfood-lifeos-server' });
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

  if (req.method === 'POST' && path === '/chat/send') {
    if (!assertAuth(req, res)) {
      return;
    }

    let parsed: {
      conversation_id?: string;
      message?: { text?: string; id?: string };
      domain_id?: string;
      idempotency_key?: string;
      previous_response_id?: string;
      retry_of?: string;
    };
    try {
      parsed = (await readJsonBody(req)) as typeof parsed;
    } catch {
      badRequest(res, 'Invalid JSON');
      return;
    }

    const conversationId = parsed.conversation_id || `server-${Date.now()}`;
    const text = typeof parsed.message?.text === 'string' ? parsed.message.text : '';
    const userMessageId = typeof parsed.message?.id === 'string' ? parsed.message.id : undefined;
    if (!text.trim()) {
      badRequest(res, 'message.text is required');
      return;
    }
    const conversation = ensureConversation(conversationId, parsed.domain_id || 'food', text.slice(0, 80));
    upsertConversation({
      id: conversation.id,
      domain: conversation.domain,
      title: conversation.title,
      detail: conversation.detail,
    });
    const runId = `run-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const idempotencyKey = parsed.idempotency_key || `${conversationId}:${text}`;
    const previousResponseId =
      typeof parsed.previous_response_id === 'string' && parsed.previous_response_id.trim()
        ? parsed.previous_response_id
        : previousResponseByConversation.get(conversationId);
    const existing = idempotencyCache.get(idempotencyKey);
    if (existing) {
      const prior = getConversation(existing.conversationId)?.messages.find((item) => item.id === existing.messageId);
      if (prior) {
        const thread = getConversation(conversation.id);
        ok(res, {
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
            status: 'completed',
            needs_retry: false,
            aborted: false,
          },
          warnings: ['Idempotency key replayed; returned prior answer.'],
        });
        return;
      }
    }

    const retryOfMessageId =
      typeof parsed.retry_of === 'string' && parsed.retry_of.trim() ? parsed.retry_of.trim() : undefined;
    const existingMessage = retryOfMessageId
      ? getConversation(conversation.id)?.messages.find((message) => message.id === retryOfMessageId)
      : null;
    const runMessageText =
      typeof (existingMessage as { body?: string } | null)?.body === 'string'
        ? ((existingMessage as { body?: string } | null)!.body as string)
        : text;

    const response = await runServerChat({
      conversationId,
      message: runMessageText,
      threadTitle: conversation.title,
      detail: conversation.detail,
      idempotencyKey,
      domainId: conversation.domain,
      runId,
      previousResponseId,
      retryOfMessageId,
      userMessageId: userMessageId || `user-${Date.now()}`,
      appendUserMessage: !retryOfMessageId,
    });

    ok(res, response);
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

  badRequest(res, 'Unsupported method');
});

server.listen(port, '127.0.0.1', () => {
  console.log(`[server] listening on http://127.0.0.1:${port}`);
});

export { server };
