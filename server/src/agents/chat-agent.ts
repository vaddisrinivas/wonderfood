import { createHash } from 'node:crypto';
import { openai } from '@ai-sdk/openai';
import { ToolLoopAgent, tool, zodSchema } from 'ai';
import { z } from 'zod';

import {
  LOCAL_QUERY_HARD_MAX_ROWS,
  LOCAL_QUERY_MAX_PROJECTED_FIELDS,
  LOCAL_QUERY_SCHEMA_VERSION,
} from '../types/local-query';

const DEFAULT_CHAT_MODEL = 'gpt-4.1-mini';

const fieldName = z.string().min(1).regex(/^[A-Za-z_][A-Za-z0-9_.-]*$/);
const toolQueryDirection = z.enum(['asc', 'desc']);

type PredicateSchema = z.ZodTypeAny;
const localQueryWhere: PredicateSchema = z.lazy(() => z.discriminatedUnion('op', [
  z.object({
    op: z.literal('and'),
    args: z.array(localQueryWhere).min(1),
  }),
  z.object({
    op: z.literal('or'),
    args: z.array(localQueryWhere).min(1),
  }),
  z.object({
    op: z.literal('not'),
    arg: localQueryWhere,
  }),
  z.object({
    op: z.literal('exists'),
    field: fieldName,
  }),
  z.object({
    op: z.enum(['eq', 'neq', 'gt', 'gte', 'lt', 'lte']),
    field: fieldName,
    value: z.unknown(),
  }),
  z.object({
    op: z.enum(['contains', 'starts_with']),
    field: fieldName,
    value: z.string(),
  }),
]));

const localQueryInputSchema = z.object({
  schemaVersion: z.literal(LOCAL_QUERY_SCHEMA_VERSION),
  purpose: z.string().min(1).max(240),
  requestedFields: z.array(fieldName).min(1).max(LOCAL_QUERY_MAX_PROJECTED_FIELDS).superRefine((values, context) => {
    const unique = new Set(values);
    if (unique.size !== values.length) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'requestedFields must be unique',
      });
    }
  }),
  maxRows: z.number().int().min(1).max(LOCAL_QUERY_HARD_MAX_ROWS).optional(),
  query: z.object({
    from: z.string().regex(/^[A-Za-z_][A-Za-z0-9_-]*$/),
    where: localQueryWhere,
    orderBy: z.array(z.object({
      field: fieldName,
      direction: toolQueryDirection.optional(),
    })).optional(),
    limit: z.number().int().min(1).max(LOCAL_QUERY_HARD_MAX_ROWS).optional(),
    offset: z.number().int().min(0).optional(),
    project: z.array(fieldName).min(1).max(LOCAL_QUERY_MAX_PROJECTED_FIELDS).optional(),
  }),
});

export const localQuery = tool({
  description:
    'Request bounded rows from local rows only. Use only when the model needs concrete local record evidence.',
  inputSchema: zodSchema(localQueryInputSchema),
});

export const chatAgent = new ToolLoopAgent({
  model: openai.chat(process.env.OPENAI_MODEL?.trim() || DEFAULT_CHAT_MODEL),
  tools: { localQuery },
});

export type ChatAgentSource = {
  url: string;
  title: string;
};

export type ChatAgentToolCall = {
  toolName: string;
  toolCallId: string;
  input: unknown;
};

export type ChatAgentResult = {
  status: 'ok' | 'tool-calls' | 'aborted' | 'disabled' | 'error';
  source: 'ai-sdk' | 'openai-provider-missing';
  text: string;
  responseId?: string;
  conversationId?: string;
  webCitations: ChatAgentSource[];
  toolCalls: ChatAgentToolCall[];
  duplicateToolCallIds: string[];
};

function toToolCallPayload(step: {
  toolCalls?: Array<{
    toolCallId?: string;
    toolName?: string;
    input?: unknown;
    inputText?: unknown;
  }>;
}): ChatAgentToolCall[] {
  if (!step.toolCalls?.length) {
    return [];
  }
  return step.toolCalls.map((toolCall, index) => ({
    toolName: typeof toolCall.toolName === 'string' ? toolCall.toolName : 'tool-calls',
    toolCallId: typeof toolCall.toolCallId === 'string' ? toolCall.toolCallId : `tool-call-${index}`,
    input: toolCall.input ?? toolCall.inputText,
  }));
}

function summarizeSources(sources: Array<{
  url?: unknown;
  title?: unknown;
}>): ChatAgentSource[] {
  const out: ChatAgentSource[] = [];
  for (const source of sources) {
    if (typeof source?.url !== 'string' || !/^https?:\/\//i.test(source.url)) {
      continue;
    }
    out.push({
      url: source.url,
      title: typeof source.title === 'string' ? source.title : source.url,
    });
  }
  const deduped = new Set<string>();
  return out.filter((entry) => {
    if (deduped.has(entry.url)) {
      return false;
    }
    deduped.add(entry.url);
    return true;
  });
}

function normalizeText(text: unknown): string {
  return typeof text === 'string' && text.trim() ? text : '';
}

function buildModelResult(options: {
  status: ChatAgentResult['status'];
  source: ChatAgentResult['source'];
  responseId?: string;
  conversationId?: string;
  text: string;
  sources: ChatAgentSource[];
  toolCalls: ChatAgentToolCall[];
}): ChatAgentResult {
  const seen = new Set<string>();
  const duplicates: string[] = [];
  const dedupedToolCalls = options.toolCalls.filter((entry) => {
    if (seen.has(entry.toolCallId)) {
      if (!duplicates.includes(entry.toolCallId)) {
        duplicates.push(entry.toolCallId);
      }
      return false;
    }
    seen.add(entry.toolCallId);
    return true;
  });

  if (!options.responseId && options.text) {
    const hash = createHash('sha256').update(options.text).digest('hex');
    options.responseId = `offline:${hash.slice(0, 24)}`;
  }

  return {
    status: options.status,
    source: options.source,
    text: options.text,
    responseId: options.responseId,
    conversationId: options.conversationId,
    webCitations: options.sources,
    toolCalls: dedupedToolCalls,
    duplicateToolCallIds: duplicates,
  };
}

export function assertServerExecuteGate(targetTool: { execute?: unknown }) {
  if (typeof targetTool.execute !== 'undefined') {
    throw new Error('tool execute handler is forbidden for localQuery server adapter');
  }
}

export async function runChatAgent(input: {
  prompt: string;
  stream?: boolean;
  onModelToken?: (token: string) => void;
  signal?: AbortSignal;
  previousResponseId?: string;
}): Promise<ChatAgentResult> {
  if (!process.env.OPENAI_API_KEY?.trim()) {
    return {
      status: 'disabled',
      source: 'openai-provider-missing',
      text: 'Live model unavailable: OPENAI_API_KEY is not configured.',
      toolCalls: [],
      webCitations: [],
      duplicateToolCallIds: [],
    };
  }

  assertServerExecuteGate(localQuery);

  try {
    if (input.stream) {
      const streamed = await chatAgent.stream({
        prompt: input.prompt,
        abortSignal: input.signal,
        ...(input.previousResponseId
          ? { providerOptions: { openai: { previousResponseId: input.previousResponseId } } }
          : {}),
      });
      const toolCallsFromSteps = await streamed.steps;
      const finalStep = await streamed.finalStep;
      let output = '';
      for await (const token of streamed.textStream) {
        if (input.onModelToken) {
          input.onModelToken(token);
        }
        output += token;
      }
      const response = await streamed.response;
      const citations = summarizeSources(await streamed.sources);
      const toolCalls = toolCallsFromSteps.flatMap((step) => toToolCallPayload(step));
      const status =
        finalStep.finishReason === 'tool-calls'
          ? 'tool-calls'
          : finalStep.finishReason === 'stop'
            ? 'ok'
            : finalStep.finishReason === 'error'
              ? 'error'
              : 'ok';

      return buildModelResult({
        status,
        source: 'ai-sdk',
        responseId: response.id,
        conversationId: undefined,
        text: normalizeText(output),
        sources: citations,
        toolCalls,
      });
    }

    const generated = await chatAgent.generate({
      prompt: input.prompt,
      abortSignal: input.signal,
      ...(input.previousResponseId
        ? { providerOptions: { openai: { previousResponseId: input.previousResponseId } } }
        : {}),
    });
    const finalStep = generated.finalStep;
    const response = await generated.response;
    const citations = summarizeSources(await generated.sources);
    const toolCalls = (await generated.steps).flatMap((step) => toToolCallPayload(step));
    const text = normalizeText(await generated.text);
    const status = finalStep.finishReason === 'tool-calls'
      ? 'tool-calls'
      : finalStep.finishReason === 'error'
        ? 'error'
        : 'ok';

    return buildModelResult({
      status,
      source: 'ai-sdk',
      responseId: response.id,
      conversationId: undefined,
      text,
      sources: citations,
      toolCalls,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      const result: ChatAgentResult = {
        status: 'aborted',
        source: 'ai-sdk',
        text: 'Request was cancelled.',
        toolCalls: [],
        webCitations: [],
        duplicateToolCallIds: [],
      };
      return result;
    }

    return {
      status: 'error',
      source: 'ai-sdk',
      text: error instanceof Error ? error.message : 'Model call failed.',
      toolCalls: [],
      webCitations: [],
      duplicateToolCallIds: [],
    };
  }
}
