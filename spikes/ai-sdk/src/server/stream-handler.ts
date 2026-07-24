import { createAgentUIStreamResponse, type UIMessage } from 'ai';
import { agent } from './agent.js';

export type StreamInput = {
  uiMessages: Array<UIMessage>;
  signal?: AbortSignal;
};

export type ToolOutputEnvelope = {
  toolCallId: string;
  type: 'tool-call';
  input?: Record<string, unknown>;
  toolName?: string;
};

export async function createSpikeAgentStream(input: StreamInput) {
  const response = await createAgentUIStreamResponse({
    agent,
    uiMessages: input.uiMessages,
    headers: {
      'content-type': 'application/json',
    },
    abortSignal: input.signal,
  });

  return response;
}

export function validateToolOutput(toolCall: { toolCallId: string }, knownIds: Set<string>) {
  const alreadySeen = knownIds.has(toolCall.toolCallId);
  knownIds.add(toolCall.toolCallId);
  return {
    allowed: !alreadySeen,
    duplicate: alreadySeen,
    duplicateCount: knownIds.size,
  };
}
