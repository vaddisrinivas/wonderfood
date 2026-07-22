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

export async function callOpenAI(input: OpenAIInput): Promise<OpenAIResponse> {
  const key = process.env.OPENAI_API_KEY;
  if (!key) {
    return {
      status: 'disabled',
      model: input.model ?? 'gpt-4.1-mini',
      source: 'offline-fallback',
      text: 'OPENAI_API_KEY is missing in server environment. Using offline draft.',
    };
  }

  const controller = new AbortController();
  const timeoutTimer = setTimeout(() => controller.abort(), input.timeoutMs ?? 4500);
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
        model: input.model ?? 'gpt-4.1-mini',
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
        model: input.model ?? 'gpt-4.1-mini',
        source: 'openai-http-error',
        text: `OpenAI responses API returned ${response.status}: ${body.slice(0, 140)}`,
      };
    }

    const payload = (await response.json()) as {
      id?: string;
      output_text?: string;
      conversation?: { id?: string };
      status?: string;
    } & Record<string, unknown>;
    const answer = typeof payload.output_text === 'string' ? payload.output_text : 'I can use the current Food context once a model stream is available.';
    return {
      status: 'ok',
      model: input.model ?? 'gpt-4.1-mini',
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
        model: input.model ?? 'gpt-4.1-mini',
        source: 'openai-fetch-aborted',
        aborted: true,
        text: 'Request was cancelled.',
      };
    }
    return {
      status: 'error',
      model: input.model ?? 'gpt-4.1-mini',
      source: 'openai-fetch-failed',
      text: error instanceof Error ? error.message : 'OpenAI request failed.',
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
