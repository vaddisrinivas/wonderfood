type OpenAIStatus = 'disabled' | 'ok' | 'error';

export type OpenAIResponse = {
  status: OpenAIStatus;
  text: string;
  model: string;
  source: string;
  aborted?: boolean;
  responseId?: string;
  conversationId?: string;
  raw?: unknown;
};

type OpenAIInput = {
  prompt: string;
  model?: string;
  timeoutMs?: number;
  signal?: AbortSignal;
  previousResponseId?: string;
};

type OpenAIStreamInput = OpenAIInput & {
  onToken?: (token: string) => void;
  maxTokens?: number;
};

const DEFAULT_MODEL = 'gpt-4.1-mini';

function configuredModel(inputModel?: string): string {
  return inputModel?.trim() || process.env.OPENAI_MODEL?.trim() || DEFAULT_MODEL;
}

function configuredTimeout(inputTimeout: number | undefined, stream: boolean): number {
  if (typeof inputTimeout === 'number' && Number.isFinite(inputTimeout) && inputTimeout > 0) {
    return inputTimeout;
  }
  const configured = Number(process.env.OPENAI_TIMEOUT_MS);
  return Number.isFinite(configured) && configured > 0 ? configured : stream ? 8000 : 4500;
}

export async function callOpenAI(input: OpenAIInput): Promise<OpenAIResponse> {
  const key = process.env.OPENAI_API_KEY;
  const model = configuredModel(input.model);
  if (!key) {
    return {
      status: 'disabled',
      model,
      source: 'offline-fallback',
      text: 'Live model unavailable: OPENAI_API_KEY is not configured.',
    };
  }

  const controller = new AbortController();
  const timeoutTimer = setTimeout(() => controller.abort(), configuredTimeout(input.timeoutMs, false));
  const signal = input.signal ? mergeAbortSignals(controller.signal, input.signal) : controller.signal;

  try {
    const response = await fetch('https://api.openai.com/v1/responses', {
      method: 'POST',
      signal,
      headers: {
        Authorization: `Bearer ${key}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model,
        input: input.prompt,
        ...(input.previousResponseId
          ? { previous_response_id: input.previousResponseId }
          : {}),
        store: true,
      }),
    });

    clearTimeout(timeoutTimer);
    if (!response.ok) {
      const body = await response.text().catch(() => '');
      return {
        status: 'error',
        model,
        source: 'openai-http-error',
        text: `OpenAI responses API returned ${response.status}: ${body.slice(0, 140)}`,
      };
    }

    const payload = (await response.json()) as {
      id?: string;
      output_text?: string;
      output?: Array<{ text?: string; content?: unknown[] }>;
      conversation?: { id?: string };
      status?: string;
    } & Record<string, unknown>;
    const fallbackFromOutput = Array.isArray(payload.output)
      ? payload.output
          .map((entry) => {
            if (typeof entry?.text === 'string') {
              return entry.text;
            }
            if (Array.isArray(entry?.content)) {
              return entry.content
                .map((chunk: unknown) =>
                  typeof chunk === 'object' &&
                  chunk !== null &&
                  typeof (chunk as { text?: unknown }).text === 'string'
                    ? String((chunk as { text: unknown }).text)
                    : '',
                )
                .join('');
            }
            return '';
          })
          .join('')
      : '';
    const answer = typeof payload.output_text === 'string' && payload.output_text.length > 0
      ? payload.output_text
      : fallbackFromOutput;
    if (!answer) {
      return {
        status: 'error',
        model,
        source: 'openai-output-empty',
        text: `OpenAI responses API returned no text for request.`,
        responseId: payload.id,
        conversationId: payload.conversation?.id ?? undefined,
        raw: payload,
      };
    }
    return {
      status: 'ok',
      model,
      source: 'openai',
      responseId: payload.id,
      conversationId: payload.conversation?.id ?? undefined,
      text: answer,
      raw: payload,
    };
  } catch (error) {
    clearTimeout(timeoutTimer);
    if (error instanceof DOMException && error.name === 'AbortError') {
      return {
        status: 'error',
        model,
        source: 'openai-fetch-aborted',
        aborted: true,
        text: 'Request was cancelled.',
      };
    }
    return {
      status: 'error',
      model,
      source: 'openai-fetch-failed',
      text: error instanceof Error ? error.message : 'OpenAI request failed.',
    };
  }
}

export async function callOpenAIStream(input: OpenAIStreamInput): Promise<OpenAIResponse> {
  const key = process.env.OPENAI_API_KEY;
  const model = configuredModel(input.model);
  if (!key) {
    return {
      status: 'disabled',
      model,
      source: 'offline-fallback',
      text: 'Live model unavailable: OPENAI_API_KEY is not configured.',
    };
  }

  const controller = new AbortController();
  const timeoutTimer = setTimeout(() => controller.abort(), configuredTimeout(input.timeoutMs, true));
  const signal = input.signal ? mergeAbortSignals(controller.signal, input.signal) : controller.signal;
  const chunks: string[] = [];
  let responseId: string | undefined;
  let conversationId: string | undefined;

  try {
    const response = await fetch('https://api.openai.com/v1/responses', {
      method: 'POST',
      signal,
      headers: {
        Authorization: `Bearer ${key}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model,
        input: input.prompt,
        ...(input.previousResponseId
          ? { previous_response_id: input.previousResponseId }
          : {}),
        stream: true,
        store: true,
        max_output_tokens: input.maxTokens,
      }),
    });

    clearTimeout(timeoutTimer);

    if (!response.ok) {
      const body = await response.text().catch(() => '');
      return {
        status: 'error',
        model,
        source: 'openai-http-error',
        text: `OpenAI responses API returned ${response.status}: ${body.slice(0, 140)}`,
      };
    }

    if (!response.body) {
      return {
        status: 'error',
        model,
        source: 'openai-stream-body-missing',
        text: 'OpenAI stream response had no body.',
      };
    }

    const decoder = new TextDecoder();
    const reader = response.body.getReader();
    let rawBuffer = '';
    const extractText = (payload: unknown): string => {
      if (!payload || typeof payload !== 'object') {
        return '';
      }
      const object = payload as Record<string, unknown>;
      if (typeof object.output_text === 'string') {
        return object.output_text;
      }
      if (typeof object.delta === 'string') {
        return object.delta;
      }
      if (typeof object.delta === 'object' && object.delta && typeof (object.delta as Record<string, unknown>).text === 'string') {
        return (object.delta as Record<string, unknown>).text as string;
      }
      if (typeof object.text === 'string') {
        return object.text;
      }
      const output = object.output;
      if (Array.isArray(output)) {
        const lines = output
          .map((item) => {
            if (!item || typeof item !== 'object') {
              return '';
            }
            const entry = item as Record<string, unknown>;
            if (typeof entry.text === 'string') {
              return entry.text;
            }
            return '';
          })
          .filter(Boolean);
        if (lines.length) {
          return lines.join('');
        }
      }
      return '';
    };

    while (true) {
      const next = await reader.read();
      if (next.done) {
        break;
      }
      rawBuffer += decoder.decode(next.value, { stream: true });
      const frames = rawBuffer.split('\n\n');
      rawBuffer = frames.pop() ?? '';
      for (const frame of frames) {
        const trimmed = frame.trim();
        if (!trimmed) {
          continue;
        }
        const lines = trimmed
          .split('\n')
          .map((line) => line.trim())
          .filter((line) => line.startsWith('data:'));
        for (const line of lines) {
          const dataText = line.replace(/^data:\s*/, '');
          if (!dataText || dataText === '[DONE]') {
            continue;
          }
          let payload: unknown;
          try {
            payload = JSON.parse(dataText);
          } catch {
            continue;
          }
          if (!responseId && typeof payload === 'object' && payload && 'id' in payload) {
            const maybe = payload as { id?: unknown; conversation?: { id?: unknown } };
            if (typeof maybe.id === 'string') {
              responseId = maybe.id;
            }
            if (!conversationId && typeof maybe.conversation?.id === 'string') {
              conversationId = maybe.conversation?.id;
            }
          }
          const text = extractText(payload);
          if (text) {
            chunks.push(text);
            if (input.onToken) {
              input.onToken(text);
            }
          }
        }
      }
    }
    const answerText = chunks.join('').trim();
    if (!answerText) {
      return {
        status: 'error',
        model,
        source: 'openai-stream-no-text',
        text: 'OpenAI stream returned no text.',
        responseId,
        conversationId,
      };
    }

    return {
      status: 'ok',
      model,
      source: 'openai-stream',
      text: answerText,
      responseId,
      conversationId,
      raw: { chunks },
    };
  } catch (error) {
    clearTimeout(timeoutTimer);
    if (error instanceof DOMException && error.name === 'AbortError') {
      return {
        status: 'error',
        model,
        source: 'openai-fetch-aborted',
        aborted: true,
        text: 'Request was cancelled.',
      };
    }
    return {
      status: 'error',
      model,
      source: 'openai-fetch-failed',
      text: error instanceof Error ? error.message : 'OpenAI streaming request failed.',
    };
  }
}

function mergeAbortSignals(a: AbortSignal, b: AbortSignal): AbortSignal {
  if (a.aborted || b.aborted) {
    const aborted = new AbortController();
    aborted.abort();
    return aborted.signal;
  }
  const merged = new AbortController();
  const abort = () => merged.abort();
  a.addEventListener('abort', abort);
  b.addEventListener('abort', abort);
  return merged.signal;
}
