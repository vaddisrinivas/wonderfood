import { DefaultChatTransport, type UIMessage } from 'ai';
import { useCallback } from 'react';
import { useChat } from '@ai-sdk/react';
import { createRequire } from 'node:module';

type FetchLike = typeof globalThis.fetch;

export type ToolContinuation = {
  toolCallId: string;
  output: { rows: Array<{ title: string; source: string }> };
};

const require = createRequire(import.meta.url);

const resolveFetch = () => {
  try {
    const expoFetchModule = require('expo/fetch');
    if (typeof expoFetchModule.fetch === 'function') {
      return expoFetchModule.fetch as FetchLike;
    }
  } catch {
    // In Node test/runtime where expo/fetch entrypoint is unavailable, fall back to global fetch.
  }

  return globalThis.fetch;
};

export const chatTransport = new DefaultChatTransport({
  api: '/api/chat',
  fetch: resolveFetch(),
});

export function useSpikeChat() {
  const chat = useChat<UIMessage>({
    transport: chatTransport,
  });

  const resumeWithToolOutput = useCallback(
    (toolOutput: ToolContinuation) =>
      chat.addToolOutput({
        tool: 'localQuery',
        toolCallId: toolOutput.toolCallId,
        output: toolOutput.output,
      }),
    [chat]
  );

  const cancelRequest = useCallback(() => chat.stop(), [chat]);

  const resumeStream = useCallback(() => chat.resumeStream(), [chat]);

  return {
    ...chat,
    resumeWithToolOutput,
    cancelRequest,
    resumeStream,
  };
}

export function isDisconnected(result: { error?: Error | string; status?: number }): boolean {
  if (result.error) {
    return `${result.error}`.includes('disconnected');
  }
  return result.status === 499 || result.status === 0;
}
