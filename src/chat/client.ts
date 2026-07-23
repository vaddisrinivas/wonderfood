import { loadCatalog } from '@/src/domain/catalog';
import { listConversations, getConversation, createConversation, upsertConversation, appendMessage } from '@/src/db/conversations';
import { SQLiteDatabase } from 'expo-sqlite';
import { ChatAnswer, ChatMessage, ChatSendInput, ChatSendResult, ChatThread } from '@/src/chat/types';
import { sendDirectModelMessage } from '@/src/chat/direct-provider';
import { AiProviderProfile, loadLifeOSSettings, usableAiProfiles } from '@/src/settings/lifeos-settings';
import { listRecordsForDomain } from '@/src/db/records';
import type { CanonicalRecord } from '@/src/domain/runtime';
// Keep citations user-controlled to avoid fabricated defaults when model fallback is in effect.

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

function publicEnv(name: string) {
  const env = typeof process !== 'undefined' ? process.env : undefined;
  const value = env?.[name];
  return typeof value === 'string' ? value.trim() : '';
}

export async function resolveChatServerConfig(input?: { serverUrl?: string; serverToken?: string }) {
  return {
    serverUrl: input?.serverUrl?.trim() || publicEnv('EXPO_PUBLIC_LIFEOS_SERVER_URL'),
    serverToken: input?.serverToken?.trim() || publicEnv('EXPO_PUBLIC_LIFEOS_SERVER_TOKEN'),
  };
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

export function makeWelcomeAnswer(records: CanonicalRecord[], domainLabel = 'Food'): ChatAnswer {
  const selectedRecords = records.slice(0, 4);
  const rows = selectedRecords.map((record) => {
    const detail = readFoodDetail(record);
    const available = detail.ingredients.filter((item) => item.state === 'available').slice(0, 3).map((item) => item.name).join(', ') || 'not captured';
    const open = detail.ingredients.filter((item) => item.state === 'needed' || item.state === 'shopping').slice(0, 3).map((item) => item.name).join(', ') || 'none visible';
    return { cells: [record.title, available, open] };
  });

  return {
    title: `${domainLabel} source briefing`,
    intro: selectedRecords.length
      ? `I loaded ${selectedRecords.length} source-backed ${domainLabel} records. Ask a follow-up and I will keep using this thread context.`
      : `Connect Notion, Sheets, SQLite or another source, then I will answer from exact ${domainLabel} records.`,
    columns: rows.length ? ['Record', 'Available', 'Needed / shopping'] : undefined,
    rows,
    sourceCards: selectedRecords.map(recordToSourceCard),
    recordCards: selectedRecords.map(recordToAnswerCard),
    citations: selectedRecords.map((record, index) => ({
      label: `${record.source.provider} · ${record.title}`,
      detail: record.source.external_id || record.collection,
      href: record.source.url || `wonderfood://record/${record.id}`,
      tone: (['moss', 'blue', 'amber', 'plum'] as const)[index % 4],
    })),
  };
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
  const settings = await loadLifeOSSettings();
  const { serverUrl: configuredServerUrl, serverToken: configuredServerToken } = await resolveChatServerConfig(input);
  const directProfiles = usableAiProfiles(settings);

  if (!db) {
    if (!configuredServerUrl && directProfiles.length) {
      const direct = await tryDirectProviders({
        profiles: directProfiles,
        domainId,
        messages: [{ role: 'user', content: text }],
      });
      if (direct.text) {
        return {
          mode: 'direct',
          conversationId,
          warnings: direct.warnings,
          thread: {
            id: conversationId,
            title: text.slice(0, 36) || 'Conversation',
            detail: direct.provider || 'Direct provider',
            messages: [
              { id: userId, role: 'user', text },
              { id: nowId('asst'), role: 'assistant', text: direct.text },
            ],
          },
        };
      }
    }

    if (!configuredServerUrl) {
      return {
        mode: 'offline',
        conversationId,
        thread: {
          id: conversationId,
          title: 'Food context · local briefing',
          detail: 'Local source answer',
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
        warnings: ['No local database in this environment; using a local structured answer.'],
      };
    }

    const serverEndpoint = configuredServerUrl;
    const serverResult = input.onModelToken
      ? await sendToServerStream({
          conversationId,
          text,
          domainId,
          userId,
          token: configuredServerToken,
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
          token: configuredServerToken,
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
          title: 'Food context · local briefing',
          detail: 'Local source answer',
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
  const sourceRecords = await listRecordsForDomain(db, domainId).catch(() => [] as CanonicalRecord[]);

  if (!configuredServerUrl && directProfiles.length) {
    const direct = await tryDirectProviders({
      profiles: directProfiles,
      domainId,
      records: sourceRecords,
      messages: messagesSoFar.slice(-20).map((message) => ({
        role: message.role,
        content: message.body,
      })),
    });
    if (direct.text) {
      const answerId = nowId('asst');
      const answer = makeDirectAnswer(direct.text, direct.provider, sourceRecords);
      await appendMessage(db, {
        id: answerId,
        conversation_id: conversationId,
        role: 'assistant',
        sort_index: nextSortIndex + 1,
        body: direct.text,
        answer_payload: { answer },
      });
      const updated = await getConversation(db, conversationId);
      return {
        mode: 'direct',
        conversationId,
        warnings: direct.warnings,
        thread: {
          id: conversationId,
          title: updated?.title || 'Conversation',
          detail: direct.provider || 'Direct provider',
          messages: updated?.messages.map(dbMessageToChat) ?? [
            ...messagesSoFar.map(dbMessageToChat),
            { id: answerId, role: 'assistant', text: direct.text, answer },
          ],
        },
      };
    }
    offlineWarnings.push(...direct.warnings);
  }

  if (!configuredServerUrl) {
    const answer = makeOfflineAnswer(text, sourceRecords);
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
      token: configuredServerToken,
      baseUrl: configuredServerUrl,
      retryOf,
      onToken: input.onModelToken,
    })
    : await sendToServer({
      conversationId,
      text,
      domainId,
      userId,
      limit: 1,
      token: configuredServerToken,
      baseUrl: configuredServerUrl,
      retryOf,
    });

  if (!serverResult) {
    const fallback = makeOfflineAnswer(text, sourceRecords);
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
      warnings: ['Live endpoint unavailable; using a local source-backed answer.'],
      thread: {
        id: conversationId,
        title: threadAfterUser?.title || 'Conversation',
        detail: threadAfterUser?.detail || 'Local source answer',
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

  const fallback = makeOfflineAnswer(text, sourceRecords);
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

async function tryDirectProviders(input: {
  profiles: AiProviderProfile[];
  domainId: string;
  messages: Array<{ role: 'user' | 'assistant'; content: string }>;
  records?: CanonicalRecord[];
}): Promise<{ text: string; provider?: string; warnings: string[] }> {
  const warnings: string[] = [];
  const sourceContext = buildDirectSourceContext(input.records ?? []);
  const system = {
    role: 'system' as const,
    content:
      `You are Hearth, the LifeOS assistant for the ${input.domainId} domain. ` +
      'Use the conversation context, state uncertainty plainly, never invent source claims, and keep answers useful and concise. ' +
      'When records or source excerpts are absent, say what information is missing. ' +
      'When a table would help, use a compact Markdown table.' +
      sourceContext,
  };

  for (const profile of input.profiles) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 45000);
    try {
      const result = await sendDirectModelMessage({
        profile,
        signal: controller.signal,
        messages: [system, ...input.messages],
      });
      return { text: result.text, provider: result.provider, warnings };
    } catch (error) {
      warnings.push(error instanceof Error ? error.message : `${profile.id} provider failed.`);
    } finally {
      clearTimeout(timeout);
    }
  }

  return { text: '', warnings };
}

function buildDirectSourceContext(records: CanonicalRecord[]): string {
  const relevant = records.slice(0, 8);
  if (!relevant.length) {
    return '\n\nNo local source records are currently loaded for this domain.';
  }
  const lines = relevant.map((record, index) => {
    const body = Object.entries(record.properties)
      .slice(0, 8)
      .map(([key, value]) => `${key}: ${String(value)}`)
      .join('; ');
    return `${index + 1}. ${record.title} [${record.source.provider}:${record.source.external_id}] ${body}`;
  });
  return `\n\nLocal LifeOS source excerpts:\n${lines.join('\n')}`;
}

function makeDirectAnswer(text: string, provider: string | undefined, records: CanonicalRecord[]): ChatAnswer {
  const table = parseMarkdownTable(text);
  const intro = table.intro || text;
  const selectedRecords = pickRelevantRecords(text, records);
  return {
    title: provider ? `Answer from ${provider}` : 'LifeOS answer',
    intro,
    columns: table.columns,
    rows: table.rows,
    sourceCards: selectedRecords.map(recordToSourceCard),
    recordCards: selectedRecords.map(recordToAnswerCard),
    citations: selectedRecords.slice(0, 5).map((record, index) => ({
      label: `${record.source.provider} · ${record.title}`,
      detail: record.source.external_id || record.collection,
      href: record.source.url || `wonderfood://record/${record.id}`,
      tone: (['moss', 'blue', 'amber', 'plum'] as const)[index % 4],
    })),
  };
}

function parseMarkdownTable(text: string): { intro: string; columns?: string[]; rows: Array<{ cells: string[] }> } {
  const lines = text.split('\n').map((line) => line.trim()).filter(Boolean);
  const tableStart = lines.findIndex((line, index) => {
    const next = lines[index + 1] ?? '';
    return line.includes('|') && /^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?$/.test(next);
  });
  if (tableStart < 0) return { intro: text.trim(), rows: [] };

  const parseRow = (line: string) =>
    line
      .replace(/^\|/, '')
      .replace(/\|$/, '')
      .split('|')
      .map((cell) => cell.trim())
      .filter(Boolean);

  const columns = parseRow(lines[tableStart]);
  const rows: Array<{ cells: string[] }> = [];
  for (const line of lines.slice(tableStart + 2)) {
    if (!line.includes('|')) break;
    const cells = parseRow(line);
    if (cells.length) rows.push({ cells });
  }

  const intro = lines.slice(0, tableStart).join('\n') || text.replace(/\|.*\|/g, '').trim();
  return { intro, columns, rows };
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

function makeOfflineAnswer(input: string, records: CanonicalRecord[] = []): ChatAnswer {
  const lower = input.toLowerCase();
  const isShopping = lower.includes('shop') || lower.includes('buy') || lower.includes('grocery');
  const isRecipe = lower.includes('recipe') || lower.includes('cook') || lower.includes('ingredient') || lower.includes('nutrition');
  const selectedRecords = pickRelevantRecords(input, records);
  const rows = selectedRecords.slice(0, 5).map((record) => {
    const detail = readFoodDetail(record);
    const missing = detail.ingredients.filter((item) => item.state === 'needed' || item.state === 'shopping').map((item) => item.name).join(', ') || 'none visible';
    const available = detail.ingredients.filter((item) => item.state === 'available').map((item) => item.name).join(', ') || 'not captured';
    return {
      cells: [
        record.title,
        available,
        missing,
      ],
    };
  });

  return {
    title: selectedRecords.length ? `Local ${isShopping ? 'shopping' : isRecipe ? 'food planning' : 'Food'} answer` : `Local ${isShopping ? 'shopping' : isRecipe ? 'recipe' : 'food'} answer`,
    intro: selectedRecords.length
      ? buildOfflineIntro({ isShopping, isRecipe, count: selectedRecords.length })
      : 'No matching local Food records were loaded. Connect Notion/Sheets or add records, then ask again.',
    columns: rows.length ? ['Record', 'Available', 'Missing / shopping'] : undefined,
    rows,
    sourceCards: selectedRecords.map(recordToSourceCard),
    recordCards: selectedRecords.map(recordToAnswerCard),
    citations: selectedRecords.slice(0, 5).map((record, index) => ({
      label: `${record.source.provider} · ${record.title}`,
      detail: record.source.external_id || record.collection,
      href: record.source.url || `wonderfood://record/${record.id}`,
      tone: (['moss', 'blue', 'amber', 'plum'] as const)[index % 4],
    })),
  };
}

function pickRelevantRecords(query: string, records: CanonicalRecord[]) {
  const lower = query.toLowerCase();
  const tokens = lower
    .split(/[^a-z0-9]+/)
    .filter((token) => token.length > 2)
    .filter((token) => !['what', 'with', 'from', 'should', 'could', 'would', 'about', 'today', 'tonight'].includes(token));
  const scored = records.map((record) => {
    const text = `${record.title} ${record.collection} ${JSON.stringify(record.properties)}`.toLowerCase();
    const detail = readFoodDetail(record);
    const hasNeeded = detail.ingredients.some((item) => item.state === 'needed' || item.state === 'shopping');
    const hasAvailable = detail.ingredients.some((item) => item.state === 'available');
    const score = tokens.reduce((sum, token) => {
      if (record.title.toLowerCase().includes(token)) return sum + 5;
      if (record.collection.toLowerCase().includes(token)) return sum + 3;
      return sum + (text.includes(token) ? 2 : 0);
    }, 0)
      + (/recipe|cook|dinner|meal|ingredient|nutrition/.test(lower) && ['recipe', 'meal_plan', 'meal_log'].includes(record.collection) ? 3 : 0)
      + (/shop|buy|grocery|missing/.test(lower) && ['shopping_item', 'purchase'].includes(record.collection) ? 4 : 0)
      + (/what.*have|available|pantry|kitchen/.test(lower) && hasAvailable ? 2 : 0)
      + (/missing|need|shop|buy/.test(lower) && hasNeeded ? 2 : 0)
      + (/soon|expire|use/.test(lower) && /use|soon|expire|days/i.test(`${record.properties.status} ${record.properties.meta}`) ? 3 : 0);
    return { record, score };
  });
  const ranked = scored
    .filter((item) => item.score > 0)
    .sort((left, right) => right.score - left.score)
    .map((item) => item.record);
  return (ranked.length ? ranked : records).slice(0, 4);
}

function buildOfflineIntro(input: { isShopping: boolean; isRecipe: boolean; count: number }) {
  if (input.isShopping) {
    return `I found ${input.count} local Food record${input.count === 1 ? '' : 's'} that explain what to buy and why.`;
  }
  if (input.isRecipe) {
    return `I found ${input.count} local Food record${input.count === 1 ? '' : 's'} with ingredients, availability, and next cooking steps.`;
  }
  return `I found ${input.count} local Food record${input.count === 1 ? '' : 's'} tied to this question.`;
}

type FoodDetail = {
  nutrition: Array<[string, string]>;
  ingredients: Array<{ name: string; amount: string; state: string }>;
  instructions: string[];
  logs: Array<[string, string]>;
  variations: string[];
};

function readFoodDetail(record: CanonicalRecord): FoodDetail {
  const raw = record.properties.food_detail;
  if (raw && typeof raw === 'object') {
    const detail = raw as Partial<FoodDetail>;
    return {
      nutrition: Array.isArray(detail.nutrition) ? detail.nutrition : [],
      ingredients: Array.isArray(detail.ingredients) ? detail.ingredients : [],
      instructions: Array.isArray(detail.instructions) ? detail.instructions : [],
      logs: Array.isArray(detail.logs) ? detail.logs : [],
      variations: Array.isArray(detail.variations) ? detail.variations : [],
    };
  }
  return { nutrition: [], ingredients: [], instructions: [], logs: [], variations: [] };
}

function recordToAnswerCard(record: CanonicalRecord): NonNullable<ChatAnswer['recordCards']>[number] {
  const detail = readFoodDetail(record);
  const bullets = [
    detail.nutrition.slice(0, 3).map(([label, value]) => `${label}: ${value}`).join(' · '),
    detail.ingredients.filter((item) => item.state === 'available').slice(0, 3).map((item) => item.name).join(', '),
    detail.instructions[0],
  ].filter(Boolean);
  return {
    id: record.id,
    title: record.title,
    collection: record.collection,
    status: String(record.properties.status ?? 'Active'),
    detail: String(record.properties.meta ?? record.properties.body ?? record.collection),
    source: `${record.source.provider} · ${record.source.external_id}`,
    bullets,
  };
}

function recordToSourceCard(record: CanonicalRecord): NonNullable<ChatAnswer['sourceCards']>[number] {
  const detail = readFoodDetail(record);
  const fields = Object.entries(record.properties)
    .filter(([key, value]) => typeof value === 'string' && value.trim().length > 0 && !['tone', 'food_detail'].includes(key))
    .slice(0, 4)
    .map(([key]) => key);
  const quote = [
    String(record.properties.body ?? '').trim(),
    String(record.properties.meta ?? '').trim(),
    detail.ingredients.length
      ? `Ingredients: ${detail.ingredients.slice(0, 5).map((item) => `${item.name} (${item.state})`).join(', ')}`
      : '',
    detail.instructions[0] ? `Next step: ${detail.instructions[0]}` : '',
  ].filter(Boolean).join('\n');
  return {
    id: record.id,
    label: record.title,
    detail: `${record.collection} · ${record.source.provider}${record.source.external_id ? ` · ${record.source.external_id}` : ''}`,
    quote: quote || `Source row: ${record.title}`,
    href: record.source.url || `wonderfood://record/${record.id}`,
    tone: record.source.provider === 'notion' ? 'moss' : record.source.provider === 'google_sheets' ? 'blue' : 'amber',
    fields,
  };
}
