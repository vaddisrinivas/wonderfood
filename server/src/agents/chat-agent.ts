import { openai, type OpenAIResponsesProviderOptions } from '@ai-sdk/openai';
import { ToolLoopAgent, jsonSchema, tool, zodSchema } from 'ai';
import { z } from 'zod';

import {
  type BoundLocalQueryRequest,
  type LocalQueryResult,
  localQueryRequestSchema,
  localQueryResultSchema,
  parseLocalQueryRequest,
  parseLocalQueryResult,
} from '../types/local-query';

const DEFAULT_CHAT_MODEL = 'gpt-5.4-mini';

const chatCallOptionsSchema = z.object({
  enableLocalQuery: z.boolean().default(false),
  enableWebSearch: z.boolean().default(false),
  previousResponseId: z.string().optional(),
});

type ChatCallOptions = z.infer<typeof chatCallOptionsSchema>;

const localQueryInputSchema = jsonSchema<BoundLocalQueryRequest>(
  localQueryRequestSchema as Parameters<typeof jsonSchema<BoundLocalQueryRequest>>[0],
  {
    validate(value) {
      const parsed = parseLocalQueryRequest(value);
      return parsed.ok
        ? { success: true, value: parsed.value }
        : { success: false, error: new Error(parsed.errors.join('; ')) };
    },
  },
);

const localQueryOutputSchema = jsonSchema<LocalQueryResult>(
  localQueryResultSchema as Parameters<typeof jsonSchema<LocalQueryResult>>[0],
  {
    validate(value) {
      const parsed = parseLocalQueryResult(value);
      return parsed.ok
        ? { success: true, value: parsed.value }
        : { success: false, error: new Error(parsed.errors.join('; ')) };
    },
  },
);

export const localQuery = tool({
  description:
    'Request bounded rows from local rows only. Use only when the model needs concrete local record evidence.',
  inputSchema: localQueryInputSchema,
  outputSchema: localQueryOutputSchema,
});

const chatTools = {
  localQuery,
  web_search: openai.tools.webSearch({ searchContextSize: 'medium' }),
};

export const chatAgent = new ToolLoopAgent<ChatCallOptions, typeof chatTools>({
  model: openai.responses(process.env.OPENAI_MODEL?.trim() || DEFAULT_CHAT_MODEL),
  tools: chatTools,
  callOptionsSchema: zodSchema(chatCallOptionsSchema),
  prepareCall: ({ options, ...rest }) => {
    const providerOptions: { openai: OpenAIResponsesProviderOptions } = {
      openai: {
        previousResponseId: options.previousResponseId,
        parallelToolCalls: false,
        store: true,
      },
    };
    return {
      ...rest,
      activeTools: [
        ...(options.enableLocalQuery ? ['localQuery' as const] : []),
        ...(options.enableWebSearch ? ['web_search' as const] : []),
      ],
      providerOptions,
    };
  },
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
  enableLocalQuery?: boolean;
  webSearch?: boolean;
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
        options: {
          enableLocalQuery: input.enableLocalQuery === true,
          enableWebSearch: input.webSearch === true,
          previousResponseId: input.previousResponseId,
        },
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
      options: {
        enableLocalQuery: input.enableLocalQuery === true,
        enableWebSearch: input.webSearch === true,
        previousResponseId: input.previousResponseId,
      },
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
