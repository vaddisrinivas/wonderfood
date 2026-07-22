import { loadCatalog } from '@/src/domain/catalog';
import { listConversations, getConversation, createConversation, upsertConversation, appendMessage } from '@/src/db/conversations';
import { SQLiteDatabase } from 'expo-sqlite';
import { ChatAnswer, ChatMessage, ChatSendInput, ChatSendResult, ChatThread } from '@/src/chat/types';
import { ensureCitations, defaultCitations } from '@/src/chat/citations';

export const chatServerConfig = {
  url: process.env.EXPO_PUBLIC_LIFEOS_SERVER_URL?.trim() ?? '',
  token: process.env.EXPO_PUBLIC_LIFEOS_SERVER_TOKEN?.trim() ?? '',
};

export type ServerResponseMessage = {
  id: string;
  role: 'assistant' | 'user';
  text: string;
  answer?: ChatAnswer;
};

export type ServerChatResponse = {
  conversation_id: string;
  messages: ServerResponseMessage[];
  agent_handoffs?: Array<{
    role: string;
    status: 'ok' | 'blocked';
    reason?: string;
  }>;
  thread?: {
    title: string;
    detail: string;
  };
  warnings?: string[];
  run?: {
    id: string;
    status: 'running' | 'completed' | 'canceled' | 'failed';
    needs_retry: boolean;
    aborted: boolean;
    previous_response_id?: string;
  };
};

export type ServerControlResponse = {
  run_id?: string;
  status?: 'running' | 'completed' | 'cancelled' | 'failed';
  status_name?: string;
};

function nowId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
}

function dbMessageToChat(message: { id: string; role: 'assistant' | 'user'; body: string; answer_payload: string | null }) {
  const parsed = message.answer_payload ? safeJsonParse(message.answer_payload) : null;
  return {
    id: message.id,
    role: message.role,
    text: message.body,
    answer: parsed?.answer,
  } as ChatMessage;
}

function safeJsonParse(input: string) {
  try {
    return JSON.parse(input);
  } catch {
    return null;
  }
}

export async function listChatThreads(db: SQLiteDatabase | null): Promise<ChatThread[]> {
  if (!db) {
    return [];
  }

  const catalog = loadCatalog();
  const rows = await listConversations(db, catalog.activeDomainId);

  const threads: ChatThread[] = await Promise.all(
    rows.map(async (row) => {
      const envelope = await getConversation(db, row.id);
      const messages = envelope?.messages ? envelope.messages.map(dbMessageToChat) : [];
      return {
        id: row.id,
        title: row.title,
        detail: row.detail,
        messages,
      };
    })
  );

  return threads;
}

export async function sendChatMessage(input: ChatSendInput): Promise<ChatSendResult> {
  const catalog = loadCatalog();
  const domainId = input.domainId || catalog.activeDomainId;
  const conversationId = input.conversationId ?? nowId('thread');
  const userId = nowId('msg');
  const text = input.text.trim();
  const db = input.db;
  const retryOf = input.retryOfMessageId?.trim();

  const offlineAnswer = makeOfflineAnswer(text);
  const offlineWarnings: string[] = [];

  if (!db) {
    if (!chatServerConfig.url) {
      return {
        mode: 'offline',
        conversationId,
        thread: {
          id: conversationId,
          title: 'Food context · local demo',
          detail: 'Offline fallback',
          messages: [
            { id: userId, role: 'user', text },
            {
              id: nowId('asst'),
              role: 'assistant',
              text: offlineAnswer.intro,
              answer: offlineAnswer,
            },
          ],
        },
        warnings: ['No local database in this environment; using offline structured draft only.'],
      };
    }

    const serverResult = await sendToServer({
      conversationId,
      text,
      domainId,
      userId,
      limit: 4,
      token: chatServerConfig.token,
      baseUrl: chatServerConfig.url,
      retryOf,
    });

    if (!serverResult) {
      return {
        mode: 'offline',
        conversationId,
        serverError: 'Server unavailable in this environment.',
        thread: {
          id: conversationId,
          title: 'Food context · local demo',
          detail: 'Server fallback',
          messages: [
            { id: userId, role: 'user', text },
            {
              id: nowId('asst'),
              role: 'assistant',
              text: offlineAnswer.intro,
              answer: offlineAnswer,
            },
          ],
        },
      };
    }

    const latest = serverResult.messages.at(-1) ?? {
      id: nowId('asst'),
      role: 'assistant',
      text: offlineAnswer.intro,
      answer: offlineAnswer,
    };

    return {
      mode: 'server',
      conversationId: serverResult.conversation_id,
      serverRunId: serverResult.run?.id,
      retryable: serverResult.run?.needs_retry ?? false,
      warnings: serverResult.warnings,
      thread: {
        id: conversationId,
        title: serverResult.thread?.title || 'Food context · active',
        detail: serverResult.thread?.detail || 'Live mode',
        messages: [
          { id: userId, role: 'user', text },
          {
            id: latest.id,
            role: latest.role,
            text: latest.text,
            answer: latest.answer,
          },
        ],
      },
    };
  }

  const existing = await getConversation(db, conversationId);
  if (!existing) {
    const title = text.slice(0, 40) || 'New conversation';
    const detail = catalog.activeDomainId === domainId ? 'Food context on' : 'Domain context on';
    await createConversation(db, {
      id: conversationId,
      domain: domainId,
      title: title.length > 36 ? `${title.slice(0, 34)}…` : title,
      detail,
    });
  } else if (existing.title === 'New conversation' && text.trim().length > 0) {
    await upsertConversation(db, {
      id: conversationId,
      domain: domainId,
      title: text.slice(0, 36) || 'New conversation',
      detail: existing.detail,
    });
  }

  const existingEnvelope = await getConversation(db, conversationId);
  const nextSortIndex = (existingEnvelope?.messages ?? []).length;

  await appendMessage(db, {
    id: userId,
    conversation_id: conversationId,
    role: 'user',
    sort_index: nextSortIndex,
    body: text,
  });

  const threadAfterUser = await getConversation(db, conversationId);
  const messagesSoFar = threadAfterUser?.messages ?? [];

  if (!input.serverUrl) {
    const answer = makeOfflineAnswer(text);
    const answerId = nowId('asst');
    await appendMessage(db, {
      id: answerId,
      conversation_id: conversationId,
      role: 'assistant',
      sort_index: nextSortIndex + 1,
      body: answer.intro,
      answer_payload: { answer },
    });

    return {
      mode: 'offline',
      conversationId,
      warnings: offlineWarnings,
      thread: {
        id: conversationId,
        title: threadAfterUser?.title || 'Conversation',
        detail: threadAfterUser?.detail || 'Local mode',
        messages: [
          ...messagesSoFar.map(dbMessageToChat),
          {
            id: answerId,
            role: 'assistant',
            text: answer.intro,
            answer,
          },
        ],
      },
    };
  }

  const serverResult = await sendToServer({
    conversationId,
    text,
    domainId,
    userId,
    limit: 1,
    token: input.serverToken ?? chatServerConfig.token,
    baseUrl: input.serverUrl ?? chatServerConfig.url,
    retryOf,
  });

  if (!serverResult) {
    const fallback = makeOfflineAnswer(text);
    const fallbackId = nowId('asst');
    await appendMessage(db, {
      id: fallbackId,
      conversation_id: conversationId,
      role: 'assistant',
      sort_index: nextSortIndex + 1,
      body: fallback.intro,
      answer_payload: { answer: fallback },
    });
    return {
      mode: 'offline',
      conversationId,
      serverError: 'Live endpoint unavailable; this answer is local and capped.',
      warnings: ['Live mode unavailable; using capped offline fallback.'],
      thread: {
        id: conversationId,
        title: threadAfterUser?.title || 'Conversation',
        detail: threadAfterUser?.detail || 'Fallback',
        messages: [
          ...messagesSoFar.map(dbMessageToChat),
          {
            id: fallbackId,
            role: 'assistant',
            text: fallback.intro,
            answer: fallback,
          },
        ],
      },
    };
  }

  const fallback = makeOfflineAnswer(text);
  const latest = serverResult.messages.at(-1) ?? {
    id: nowId('asst'),
    role: 'assistant',
    text: fallback.intro,
    answer: fallback,
  };

  await appendMessage(db, {
    id: latest.id,
    conversation_id: conversationId,
    role: latest.role,
    sort_index: nextSortIndex + 1,
    body: latest.text ?? fallback.intro,
    answer_payload: latest.answer ? { answer: latest.answer, citations: ensureCitations(latest.answer?.citations) } : undefined,
  });

  const updated = await getConversation(db, conversationId);
  return {
    mode: 'server',
    conversationId,
    serverRunId: serverResult.run?.id,
    retryable: serverResult.run?.needs_retry ?? false,
    warnings: serverResult.warnings,
    thread: {
      id: conversationId,
      title: updated?.title || 'Conversation',
      detail: updated?.detail || 'Server-backed',
      messages: updated?.messages ? updated.messages.map(dbMessageToChat) : [
        ...messagesSoFar.map(dbMessageToChat),
        {
          id: latest.id,
          role: latest.role,
          text: latest.text,
          answer: latest.answer,
        },
      ],
    },
  };
}

async function sendToServer(payload: {
  conversationId: string;
  text: string;
  domainId: string;
  userId: string;
  limit: number;
  baseUrl: string;
  token?: string;
  retryOf?: string;
}): Promise<ServerChatResponse | null> {
  if (!payload.baseUrl) {
    return null;
  }

  const endpoint = payload.baseUrl.replace(/\/$/, '');
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 4500);

  try {
    const response = await fetch(`${endpoint}/chat/send`, {
      method: 'POST',
      signal: controller.signal,
      headers: {
        'content-type': 'application/json',
        ...(payload.token ? { authorization: `Bearer ${payload.token}` } : {}),
      },
      body: JSON.stringify({
        conversation_id: payload.conversationId,
        domain_id: payload.domainId,
        message: { id: payload.userId, role: 'user', text: payload.text },
        idempotency_key: `${payload.conversationId}:${payload.userId}`,
        ...(payload.retryOf ? { retry_of: payload.retryOf } : {}),
      }),
    });

    if (!response.ok) {
      return null;
    }

    const json = await response.json() as ServerChatResponse;
    return json;
  } catch {
    return null;
  } finally {
    clearTimeout(timeout);
  }
}

export async function stopServerRun(input: { runId: string; baseUrl: string; token?: string; }) {
  if (!input.baseUrl) {
    return null;
  }
  const endpoint = input.baseUrl.replace(/\/$/, '');
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 2500);

  try {
    const response = await fetch(`${endpoint}/chat/stop`, {
      method: 'POST',
      signal: controller.signal,
      headers: {
        'content-type': 'application/json',
        ...(input.token ? { authorization: `Bearer ${input.token}` } : {}),
      },
      body: JSON.stringify({ run_id: input.runId }),
    });

    if (!response.ok) {
      return null;
    }
    return (await response.json()) as ServerControlResponse;
  } catch {
    return null;
  } finally {
    clearTimeout(timeout);
  }
}

function makeOfflineAnswer(input: string): ChatAnswer {
  const lower = input.toLowerCase();
  const isShopping = lower.includes('shop') || lower.includes('buy') || lower.includes('grocery');
  const isYogurt = lower.includes('yogurt') || lower.includes('expire');

  return {
    title: isShopping ? 'A focused grocery pass' : isYogurt ? 'Use the yogurt first' : 'A practical kitchen next step',
    intro: 'Live model endpoint unavailable. This is a capped, typed local answer using the active Food context.',
    rows: isShopping
      ? [
          { meal: 'Produce', use: 'Coriander, lemons', next: 'Supports tonight and green dal.' },
          { meal: 'Dairy', use: 'Greek yogurt', next: 'Use now before Friday.' },
          { meal: 'Pantry', use: 'Naan', next: 'Add if you want a full meal.' },
        ]
      : [
          { meal: 'Tonight', use: 'Tandoori chicken', next: 'Pair with quick yogurt side.' },
          { meal: 'Tomorrow', use: 'Green dal', next: 'Use remaining spinach quickly.' },
          { meal: 'Breakfast', use: 'Yogurt bowl', next: 'Finish before Friday.' },
        ],
    citations: defaultCitations,
  };
}
