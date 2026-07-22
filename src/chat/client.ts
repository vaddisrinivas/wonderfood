import { loadCatalog } from '@/src/domain/catalog';
import { listConversations, getConversation, createConversation, upsertConversation, appendMessage } from '@/src/db/conversations';
import { SQLiteDatabase } from 'expo-sqlite';
import { ChatAnswer, ChatMessage, ChatSendInput, ChatSendResult, ChatThread } from '@/src/chat/types';
// keep citations user-controlled to avoid fabricated defaults when model fallback is in effect

export const chatServerConfig = {
  url: process.env.EXPO_PUBLIC_LIFEOS_SERVER_URL?.trim() ?? '',
  token: process.env.EXPO_PUBLIC_LIFEOS_SERVER_TOKEN?.trim() ?? '',
};

export type ServerResponseMessage = {
  id: string;
  role: 'assistant' | 'user';
  text: string;
  answer?: ChatAnswer;
  actionReceipt?: ChatMessage['actionReceipt'];
};

export type ServerChatResponse = {
  conversation_id: string;
  messages: ServerResponseMessage[];
  action?: {
    receipt: ChatMessage['actionReceipt'];
    verification?: {
      actionId: string;
      expected: string;
      status: 'verified' | 'denied';
      checks: string[];
      reason?: string;
    } | null;
  };
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

export type ServerUndoResponse = {
  status: 'completed' | 'failed';
  action_id: string;
  action?: Record<string, unknown>;
  undo_result?: {
    success: boolean;
    message: string;
    actor?: string;
    idempotency_key?: string;
    replayed?: boolean;
  };
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
    actionReceipt:
      parsed && typeof parsed === 'object' && parsed !== null && 'actionReceipt' in parsed
        ? parsed.actionReceipt
        : undefined,
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

    const serverEndpoint = chatServerConfig.url;
    const serverResult = input.onModelToken
      ? await sendToServerStream({
          conversationId,
          text,
          domainId,
          userId,
          token: chatServerConfig.token,
          baseUrl: serverEndpoint,
          retryOf,
          onToken: input.onModelToken,
        })
      : await sendToServer({
          conversationId,
          text,
          domainId,
          userId,
          limit: 4,
          token: chatServerConfig.token,
          baseUrl: serverEndpoint,
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
    const latestActionReceipt = serverResult.action?.receipt;

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
            actionReceipt: latestActionReceipt,
          },
        ],
      },
      action: latestActionReceipt
        ? {
            receipt: latestActionReceipt,
          }
        : undefined,
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

  const serverResult = input.onModelToken
    ? await sendToServerStream({
      conversationId,
      text,
      domainId,
      userId,
      token: input.serverToken ?? chatServerConfig.token,
      baseUrl: input.serverUrl ?? chatServerConfig.url,
      retryOf,
      onToken: input.onModelToken,
    })
    : await sendToServer({
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
  const latestActionReceipt = serverResult.action?.receipt;

  await appendMessage(db, {
    id: latest.id,
    conversation_id: conversationId,
    role: latest.role,
    sort_index: nextSortIndex + 1,
    body: latest.text ?? fallback.intro,
    answer_payload: latest.answer
      ? {
          answer: latest.answer,
          citations: latest.answer?.citations ?? [],
          ...(latestActionReceipt ? { actionReceipt: latestActionReceipt } : {}),
        }
      : latestActionReceipt
        ? { actionReceipt: latestActionReceipt }
        : undefined,
  });

  const updated = await getConversation(db, conversationId);
  const messagesForThread =
    updated?.messages.map((message) => {
      const parsed = dbMessageToChat(message);
      return message.id === latest.id ? { ...parsed, actionReceipt: latestActionReceipt ?? parsed.actionReceipt } : parsed;
    }) ?? [
      ...messagesSoFar.map(dbMessageToChat),
      {
        id: latest.id,
        role: latest.role,
        text: latest.text,
        answer: latest.answer,
        actionReceipt: latestActionReceipt,
      },
    ];
  return {
    mode: 'server',
    conversationId,
    serverRunId: serverResult.run?.id,
    retryable: serverResult.run?.needs_retry ?? false,
    warnings: serverResult.warnings,
    action: latestActionReceipt ? { receipt: latestActionReceipt } : undefined,
    thread: {
      id: conversationId,
      title: updated?.title || 'Conversation',
      detail: updated?.detail || 'Server-backed',
      messages: messagesForThread,
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

async function sendToServerStream(payload: {
  conversationId: string;
  text: string;
  domainId: string;
  userId: string;
  baseUrl: string;
  token?: string;
  retryOf?: string;
  onToken?: (token: string) => void;
}): Promise<ServerChatResponse | null> {
  if (!payload.baseUrl) {
    return null;
  }

  const endpoint = payload.baseUrl.replace(/\/$/, '');
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 12000);

  try {
    const response = await fetch(`${endpoint}/chat/send/stream`, {
      method: 'POST',
      signal: controller.signal,
      headers: {
        accept: 'text/event-stream',
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

    if (!response.ok || !response.body) {
      return null;
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let result: ServerChatResponse | null = null;

    while (true) {
      const read = await reader.read();
      if (read.done) {
        break;
      }
      buffer += decoder.decode(read.value, { stream: true });
      const frames = buffer.split('\n\n');
      buffer = frames.pop() ?? '';

        for (const rawFrame of frames) {
          const lines = rawFrame
            .split('\n')
            .map((line) => line.trim())
            .filter((line) => line.startsWith('data:'));
        for (const line of lines) {
          const dataText = line.replace(/^data:\s*/, '');
          if (!dataText) {
            continue;
          }
          try {
            const event = JSON.parse(dataText) as {
              type: string;
              response?: ServerChatResponse;
              conversation_id?: string;
              messages?: ServerResponseMessage[];
              thread?: ServerChatResponse['thread'];
              run?: ServerChatResponse['run'];
              warnings?: string[];
              delta?: string;
              error?: string;
            };
            if (event.type === 'token' && event.delta) {
              payload.onToken?.(event.delta);
              continue;
            }
            if (event.type === 'run.end' && event.response) {
              result = event.response;
              continue;
            }
            if (event.type === 'cache' && event.response) {
              result = event.response;
              continue;
            }
            if (event.type === 'cache' && !event.response) {
              result = {
                conversation_id: event.conversation_id || payload.conversationId,
                messages: event.messages || [],
                thread: event.thread,
                run: event.run,
                warnings: event.warnings,
              };
              continue;
            }
            if (event.type === 'error') {
              if (event.error) {
                return null;
              }
            }
          } catch {
            continue;
          }
        }
      }
    }

    if (buffer) {
      const lines = buffer
        .split('\n')
        .map((line) => line.trim())
        .filter((line) => line.startsWith('data:'));
        for (const line of lines) {
          const dataText = line.replace(/^data:\s*/, '');
          if (!dataText) continue;
          try {
            const event = JSON.parse(dataText) as {
              type: string;
              response?: ServerChatResponse;
              conversation_id?: string;
              messages?: ServerResponseMessage[];
              thread?: ServerChatResponse['thread'];
              run?: ServerChatResponse['run'];
              warnings?: string[];
              delta?: string;
            };
            if (event.type === 'token' && event.delta) {
              payload.onToken?.(event.delta);
            }
            if (event.type === 'run.end' && event.response) {
              result = event.response;
              continue;
            }
            if (event.type === 'cache' && event.response) {
              result = event.response;
              continue;
            }
            if (event.type === 'cache' && !event.response) {
              result = {
                conversation_id: event.conversation_id || payload.conversationId,
                messages: event.messages || [],
                thread: event.thread,
                run: event.run,
                warnings: event.warnings,
              };
            }
          } catch {
            continue;
          }
      }
    }

    return result;
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

export async function undoServerAction(input: {
  actionId: string;
  baseUrl: string;
  token?: string;
  idempotencyKey?: string;
  actor?: string;
}): Promise<ServerUndoResponse | null> {
  if (!input.baseUrl) {
    return null;
  }

  const endpoint = input.baseUrl.replace(/\/$/, '');
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 2500);

  try {
    const response = await fetch(`${endpoint}/chat/undo`, {
      method: 'POST',
      signal: controller.signal,
      headers: {
        'content-type': 'application/json',
        ...(input.token ? { authorization: `Bearer ${input.token}` } : {}),
      },
      body: JSON.stringify({
        action_id: input.actionId,
        actor: input.actor,
        ...(input.idempotencyKey ? { idempotency_key: input.idempotencyKey } : {}),
      }),
    });

    if (!response.ok) {
      return null;
    }
    return (await response.json()) as ServerUndoResponse;
  } catch {
    return null;
  } finally {
    clearTimeout(timeout);
  }
}

function makeOfflineAnswer(input: string): ChatAnswer {
  const lower = input.toLowerCase();
  const isShopping = lower.includes('shop') || lower.includes('buy') || lower.includes('grocery');
  const hasConstraintWords = lower.includes('yogurt') || lower.includes('expire');
  const kind = isShopping ? 'request' : hasConstraintWords ? 'check' : 'request';

  return {
    title: `Offline ${kind}`,
    intro: 'Live model unavailable. Response is local and not source-grounded.',
    rows: [],
    citations: [],
  };
}
