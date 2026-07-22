import { toCitationsFromSnapshots } from './provenance';
import { runChatOrchestrator } from './agents/orchestrator';
import { ensureCitations } from '@/src/chat/citations';
import { getConversation } from './conversations';

export type ChatCitationTone = 'moss' | 'blue' | 'amber';

export type ChatStructuredAnswer = {
  title: string;
  intro: string;
  rows: Array<{ meal: string; use: string; next: string }>;
  citations: Array<{ label: string; detail: string; href: string; tone: ChatCitationTone }>;
};

export type ServerChatMessage = {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  answer?: ChatStructuredAnswer;
};

export type ServerChatResponse = {
  conversation_id: string;
  messages: ServerChatMessage[];
  thread: { id: string; title: string; detail: string };
  agent_handoffs?: Array<{ role: string; status: 'ok' | 'blocked'; reason?: string }>;
  warnings?: string[];
  run?: {
    id: string;
    status: 'running' | 'completed' | 'canceled' | 'failed';
    needs_retry: boolean;
    aborted: boolean;
    previous_response_id?: string;
  };
  action?: {
    receipt: {
      id: string;
      actor: string;
      domain: string;
      tool: string;
      status: 'queued' | 'running' | 'completed' | 'failed' | 'cancelled';
      record_ids: string[];
      created_at: string;
      updated_at: string;
      undo_deadline_at?: string;
    };
    verification: {
      actionId: string;
      expected: string;
      status: 'verified' | 'denied';
      checks: string[];
      reason?: string;
    } | null;
  };
  provenance?: {
    sources: ChatStructuredAnswer['citations'];
    generated_at: string;
  };
};

const fallbackRows = [
  { meal: 'Tonight', use: 'Tandoori chicken', next: 'Pair with quick yogurt side.' },
  { meal: 'Tomorrow', use: 'Green dal', next: 'Pair with remaining spinach.' },
  { meal: 'Breakfast', use: 'Yogurt bowl', next: 'Finish before Friday.' },
];

function makeFallbackAnswer(text: string): ChatStructuredAnswer {
  const lower = text.toLowerCase();
  const focus = lower.includes('shop') || lower.includes('buy')
    ? 'A focused grocery pass'
    : lower.includes('yogurt') || lower.includes('expire')
      ? 'Use yogurt first'
      : 'A practical kitchen next step';

  return {
    title: focus,
    intro: 'I used the active Food context and available sources to keep this practical.',
    rows: lower.includes('shop') || lower.includes('buy')
      ? [
          { meal: 'Produce', use: 'Coriander, lemons', next: 'Supports tonight and green dal.' },
          { meal: 'Dairy', use: 'Greek yogurt', next: 'Finish before Friday.' },
          { meal: 'Pantry', use: 'Naan', next: 'Add only if needed.' },
        ]
      : fallbackRows,
    citations: [
      { label: 'App snapshot', detail: 'Local canonical graph', href: 'wonderfood://app/snapshot', tone: 'moss' },
      { label: 'LifeOS Notion', detail: 'Template + relations', href: 'https://app.notion.com', tone: 'blue' },
      { label: 'LifeOS Sheets', detail: 'Projection and formulas', href: 'https://docs.google.com/spreadsheets', tone: 'amber' },
    ],
  };
}

function normalizeAnswerFromModel(input: { inputText: string; modelText: string }): ChatStructuredAnswer {
  const hasStructuredHint = /\{\s*"title"\s*:/m.test(input.modelText);
  if (!hasStructuredHint) {
    const fallback = makeFallbackAnswer(input.inputText);
    return { ...fallback, intro: `${fallback.intro} Model reply appended. ${input.modelText}` };
  }

  try {
    const parsed = JSON.parse(input.modelText);
    if (parsed && typeof parsed === 'object' && Array.isArray(parsed.rows)) {
      return {
        title: String(parsed.title ?? 'LifeOS response'),
        intro: String(parsed.intro ?? ''),
        rows: parsed.rows.slice(0, 3),
        citations: Array.isArray(parsed.citations) ? ensureCitations(parsed.citations) : toCitationsFromSnapshots([]),
      };
    }
  } catch {
    // fall back to heuristics
  }

  const fallback = makeFallbackAnswer(input.inputText);
  return {
    ...fallback,
    intro: `${fallback.intro} ${input.modelText.slice(0, 180)}`,
  };
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
}): Promise<ServerChatResponse> {
  const domain = input.domainId || 'food';
  const conversation = getConversation(input.conversationId);
  const threadContext = conversation
    ? conversation.messages
        .slice(-6)
        .map((message: { role: 'assistant' | 'user'; text?: string; body?: string }) => `${message.role.toUpperCase()}: ${message.text ?? message.body ?? ''}`)
        .join('\n')
    : '';

  const orchestrated = await runChatOrchestrator({
    conversationId: input.conversationId,
    domain,
    message: input.message,
    actor: 'hearth',
    commandHint: input.message,
    runId: input.runId,
    signal: input.signal,
    previousResponseId: input.previousResponseId,
  });

  const needsRetry = orchestrated.ai.status !== 'ok' && !orchestrated.ai.source?.startsWith('openai-fetch-aborted');
  const isCanceled = orchestrated.ai.source === 'openai-fetch-aborted';
  const status = isCanceled ? 'canceled' : orchestrated.ai.status === 'ok' ? 'completed' : 'failed';
  const inputText = threadContext ? `${input.message}\n${threadContext}` : input.message;
  const modelAnswer = orchestrated.ai.text
    ? normalizeAnswerFromModel({
    inputText,
    modelText: orchestrated.ai.text,
  })
    : makeFallbackAnswer(inputText);

  const provenance = orchestrated.provenance;
  const citations = toCitationsFromSnapshots(orchestrated.retrieval.snapshots);
  if (citations.length > 0) {
    modelAnswer.citations = citations;
  }

  const assistantMessage: ServerChatMessage = {
    id: `server-${Date.now()}-asst`,
    role: 'assistant',
    text: orchestrated.status === 'clarification'
      ? orchestrated.clarifyingQuestion || 'I need one short clarification before I can write.'
      : `I processed: ${input.message}`,
    answer: modelAnswer,
  };

  const warnings: string[] = [];
  if (!orchestrated.policy.allowed) {
    warnings.push(orchestrated.policy.reason);
    if (orchestrated.clarifyingQuestion) {
      warnings.push(orchestrated.clarifyingQuestion);
    }
  }
  if (orchestrated.ai.status !== 'ok') {
    warnings.push(`AI status: ${orchestrated.ai.status}. ${orchestrated.ai.text}`);
  }
  if (input.retryOfMessageId) {
    warnings.push(`Retried from user message: ${input.retryOfMessageId}`);
  }

  return {
    conversation_id: input.conversationId,
    messages: [assistantMessage],
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
    action: orchestrated.action
      ? {
          receipt: {
            id: orchestrated.action.receipt.id,
            actor: orchestrated.action.receipt.actor,
            domain: orchestrated.action.receipt.domain,
            tool: orchestrated.action.receipt.tool,
            status: orchestrated.action.receipt.status,
            record_ids: orchestrated.action.receipt.record_ids,
            created_at: orchestrated.action.receipt.created_at,
            updated_at: orchestrated.action.receipt.updated_at,
            undo_deadline_at: orchestrated.action.receipt.undo_deadline_at,
          },
          verification: orchestrated.action.verification,
        }
      : undefined,
    provenance: {
      sources: provenance.sources,
      generated_at: provenance.generated_at,
    },
  };
}

export { makeFallbackAnswer };
