import { toCitationsFromSnapshots } from './provenance';
import { runChatOrchestrator } from './agents/orchestrator';
import { ensureCitations } from '@/src/chat/citations';
import { getConversation } from './chat-storage';
import { ActionEvent } from './mcp/state';

export type ChatCitationTone = 'moss' | 'blue' | 'amber';

export type ChatStructuredAnswer = {
  title: string;
  intro: string;
  rows: Array<{ meal: string; use: string; next: string }>;
  citations: Array<{ label: string; detail: string; href: string; tone: ChatCitationTone }>;
};

type RawMessage = {
  id?: unknown;
  text?: unknown;
  role?: unknown;
};

export type ChatMessageInput = {
  id?: string;
  text: string;
};

export type ChatSendRequest = {
  thread_id?: string;
  conversation_id?: string;
  message?: string | RawMessage;
  plan_hint?: string;
  domain_id?: string;
  idempotency_key?: string;
  previous_response_id?: string;
  retry_of?: string;
  mode?: 'send' | 'stream' | 'preview';
  preview?: boolean;
};

export type ChatSendMode = 'send' | 'stream' | 'preview';

export type NormalizedChatSend = {
  threadId: string;
  message: ChatMessageInput;
  domainId: string;
  idempotencyKey: string;
  previousResponseId?: string;
  retryOfMessageId?: string;
  planHint: string;
  mode: ChatSendMode;
  preview: boolean;
  userMessageId: string;
};

function normalizeMessage(input: unknown): string {
  if (typeof input === 'string') {
    return input;
  }
  if (input && typeof input === 'object') {
    const raw = input as RawMessage;
    if (typeof raw.text === 'string') {
      return raw.text;
    }
  }
  return '';
}

function normalizeMessageId(input: unknown): string | undefined {
  return typeof input === 'string' && input.trim() ? input.trim() : undefined;
}

export function normalizeChatSendRequest(payload: ChatSendRequest): NormalizedChatSend {
  const threadId = normalizeMessageId(payload.thread_id) || normalizeMessageId(payload.conversation_id) || `server-${Date.now()}`;

  const messageText = normalizeMessage(payload.message);
  const text = messageText.trim();
  if (!text) {
    throw new Error('message is required');
  }

  const userMessageId = normalizeMessageId(typeof payload.message === 'object' ? payload.message?.id : undefined) || `user-${Date.now()}`;

  const idempotencyKey =
    normalizeMessageId(payload.idempotency_key) || `${threadId}:${userMessageId}`;

  const mode =
    payload.mode === 'stream' || payload.mode === 'preview'
      ? payload.mode
      : payload.mode === 'send'
        ? payload.mode
        : 'send';

  const preview = payload.preview === true || mode === 'preview';

  const planHint =
    typeof payload.plan_hint === 'string' && payload.plan_hint.trim().length > 0
      ? payload.plan_hint.trim()
      : text;

  const domainId =
    typeof payload.domain_id === 'string' && payload.domain_id.trim().length > 0
      ? payload.domain_id.trim()
      : 'food';

  const previousResponseId =
    typeof payload.previous_response_id === 'string' && payload.previous_response_id.trim().length > 0
      ? payload.previous_response_id
      : undefined;

  const retryOfMessageId =
    typeof payload.retry_of === 'string' && payload.retry_of.trim().length > 0
      ? payload.retry_of.trim()
      : undefined;

  return {
    threadId,
    message: { id: userMessageId, text },
    domainId,
    idempotencyKey,
    previousResponseId,
    retryOfMessageId,
    planHint,
    mode,
    preview,
    userMessageId,
  };
}

type ChatActionVerification = {
  actionId: string;
  expected: string;
  status: 'verified' | 'denied';
  checks: string[];
  reason?: string;
};

export type ChatActionReceipt = Pick<
  ActionEvent,
  | 'schema_version'
  | 'id'
  | 'actor'
  | 'domain'
  | 'tool'
  | 'risk'
  | 'status'
  | 'record_ids'
  | 'idempotency_key'
  | 'created_at'
  | 'updated_at'
  | 'undo_deadline_at'
  | 'conversation_id'
  | 'source_ids'
> & {
  source_citations?: ChatStructuredAnswer['citations'];
};

export type ChatActionHint = {
  receipt: ChatActionReceipt;
  verification: ChatActionVerification | null;
  source_citations: ChatStructuredAnswer['citations'];
};

export type ServerChatMessage = {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  answer?: ChatStructuredAnswer;
  actionReceipt?: ChatActionReceipt;
};

export type ServerChatResponse = {
  conversation_id: string;
  messages: ServerChatMessage[];
  thread: {
    id: string;
    title: string;
    detail: string;
  };
  agent_handoffs?: Array<{ role: string; status: 'ok' | 'blocked'; reason?: string }>;
  warnings?: string[];
  run?: {
    id: string;
    status: 'running' | 'completed' | 'canceled' | 'failed';
    needs_retry: boolean;
    aborted: boolean;
    previous_response_id?: string;
  };
  action?: ChatActionHint;
  action_hints?: string[];
  provenance?: {
    sources: ChatStructuredAnswer['citations'];
    generated_at: string;
  };
};

function parseStructuredModel(inputText: string, modelText: string): ChatStructuredAnswer | undefined {
  if (!modelText) {
    return undefined;
  }

  const hasStructuredHint = /\{\s*"title"\s*:/m.test(modelText);
  if (!hasStructuredHint) {
    return undefined;
  }

  try {
    const parsed = JSON.parse(modelText);
    if (
      parsed &&
      typeof parsed === 'object' &&
      Array.isArray(parsed.rows)
    ) {
      return {
        title: String(parsed.title ?? 'LifeOS response'),
        intro: String(parsed.intro ?? ''),
        rows: parsed.rows.slice(0, 3),
        citations: Array.isArray(parsed.citations) ? ensureCitations(parsed.citations) : toCitationsFromSnapshots([]),
      };
    }
  } catch {
    // ignore non-JSON model output
  }

  return undefined;
}

export async function handleServerChat(input: {
  conversationId: string;
  message: string;
  threadTitle?: string;
  idempotencyKey?: string;
  domainId?: string;
  runId?: string;
  signal?: AbortSignal;
  previousResponseId?: string;
  retryOfMessageId?: string;
  stream?: boolean;
  onModelToken?: (token: string) => void;
  planHint?: string;
  preview?: boolean;
}): Promise<ServerChatResponse> {
  const domain = input.domainId || 'food';
  const conversation = getConversation(input.conversationId);
  const threadContext = conversation
    ? conversation.messages
      .slice(-6)
      .map((message: { role: 'assistant' | 'user'; text?: string; body?: string }) =>
        `${message.role.toUpperCase()}: ${message.text ?? message.body ?? ''}`,
      )
      .join('\n')
    : '';

  const orchestrated = await runChatOrchestrator({
    conversationId: input.conversationId,
    domain,
    message: input.message,
    actor: 'hearth',
    commandHint: input.planHint,
    runId: input.runId,
    signal: input.signal,
    previousResponseId: input.previousResponseId,
    stream: input.stream,
    onModelToken: input.onModelToken,
    preview: input.preview,
  });

  const needsRetry = orchestrated.ai.status !== 'ok' && orchestrated.ai.source !== 'openai-fetch-aborted';
  const isCanceled = orchestrated.ai.source === 'openai-fetch-aborted';
  const status = isCanceled ? 'canceled' : orchestrated.ai.status === 'ok' ? 'completed' : 'failed';
  const inputText = threadContext ? `${input.message}\n${threadContext}` : input.message;
  const sourceSnapshots = orchestrated.retrieval.snapshots;
  const sourceCitations = toCitationsFromSnapshots(sourceSnapshots);

  const warnings: string[] = [];
  if (!orchestrated.policy.allowed) {
    warnings.push(orchestrated.policy.reason);
    if (orchestrated.clarifyingQuestion) {
      warnings.push(orchestrated.clarifyingQuestion);
    }
  }
  if (orchestrated.ai.status !== 'ok') {
    warnings.push(`AI ${orchestrated.ai.status}: ${orchestrated.ai.text}`);
  }
  if (input.preview) {
    warnings.push('Preview mode; actions are not committed.');
  }
  if (input.retryOfMessageId) {
    warnings.push(`Retried from user message: ${input.retryOfMessageId}`);
  }

  const clarifyingText =
    orchestrated.clarifyingQuestion || 'I need one short clarification before I can write.';
  const policyBlockedText = orchestrated.policy.reason || 'Action is not allowed in this mode.';

  const finalText =
    orchestrated.status === 'clarification'
      ? clarifyingText
      : orchestrated.policy.allowed
        ? orchestrated.ai.text || policyBlockedText
        : policyBlockedText;

  const modelAnswer =
    orchestrated.policy.allowed && orchestrated.ai.status === 'ok'
      ? parseStructuredModel(inputText, orchestrated.ai.text)
      : undefined;
  if (modelAnswer && sourceCitations.length > 0) {
    modelAnswer.citations = ensureCitations(sourceCitations);
  }

  const actionReceipt = orchestrated.action
    ? ({
      schema_version: orchestrated.action.receipt.schema_version,
      id: orchestrated.action.receipt.id,
      actor: orchestrated.action.receipt.actor,
      domain: orchestrated.action.receipt.domain,
      tool: orchestrated.action.receipt.tool,
      risk: orchestrated.action.receipt.risk,
      status: orchestrated.action.receipt.status,
      record_ids: orchestrated.action.receipt.record_ids,
      idempotency_key: orchestrated.action.receipt.idempotency_key,
      created_at: orchestrated.action.receipt.created_at,
      updated_at: orchestrated.action.receipt.updated_at,
      undo_deadline_at: orchestrated.action.receipt.undo_deadline_at,
      conversation_id: orchestrated.action.receipt.conversation_id,
      source_ids: orchestrated.action.receipt.source_ids ?? [],
      source_citations: sourceCitations,
    } as ChatActionReceipt)
    : undefined;

  const responseMessage: ServerChatMessage = {
    id: `server-${Date.now()}-asst`,
    role: 'assistant',
    text: finalText,
    answer: orchestrated.status === 'clarification'
      ? {
          title: 'Clarification required',
          intro: clarifyingText,
          rows: [],
          citations: sourceCitations,
        }
      : modelAnswer,
    actionReceipt,
  };

  const actionHints =
    sourceCitations.length > 0
      ? sourceCitations.map((source) => source.label)
      : ['no source citations'];

  return {
    conversation_id: input.conversationId,
    messages: [responseMessage],
    thread: {
      id: input.conversationId,
      title: input.threadTitle || 'Food context',
      detail: 'live response channel',
    },
    agent_handoffs: orchestrated.roles,
    warnings,
    run: {
      id: orchestrated.runId,
      status,
      needs_retry: needsRetry,
      aborted: isCanceled,
      previous_response_id: orchestrated.ai.responseId,
    },
    action: actionReceipt
      ? {
        receipt: actionReceipt,
        verification: orchestrated.action?.verification ?? null,
        source_citations: sourceCitations,
      }
      : undefined,
    action_hints: actionHints,
    provenance: {
      sources: sourceCitations,
      generated_at: new Date().toISOString(),
    },
  };
}
