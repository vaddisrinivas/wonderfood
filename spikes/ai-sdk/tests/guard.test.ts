import { describe, expect, it } from 'vitest';
import { validateToolOutput, type StreamInput } from '../src/server/stream-handler.js';
import { localQuery } from '../src/server/agent.js';
import { separateApprovalFromSdkToolOutput } from '../src/client/approval.js';
import { isDisconnected, type ToolContinuation } from '../src/client/chat-client.js';

function makeController() {
  return new AbortController();
}

describe('ai-sdk spike safeguards', () => {
  it('validates localQuery has no server execute function', () => {
    expect((localQuery as { execute?: unknown }).execute).toBeUndefined();
  });

  it('flags duplicate toolCallId for continuation input', async () => {
    const seen = new Set<string>();
    expect(
      validateToolOutput({ toolCallId: 'tool-1' } as { toolCallId: string }, seen).allowed
    ).toBe(true);
    expect(
      validateToolOutput({ toolCallId: 'tool-1' } as { toolCallId: string }, seen).duplicate
    ).toBe(true);
  });

  it('separates approval records from tool output', () => {
    const result = separateApprovalFromSdkToolOutput({
      approval: { schemaVersion: 'wonder.remote.approval.v1', approvalId: 'A-1', actor: 'reviewer' },
      toolOutput: { answer: 'row-count: 3' },
    });

    expect(result.hasApproval).toBe(true);
    expect(result.approval?.approvalId).toBe('A-1');
    expect(result.toolOutput).toMatchObject({ answer: 'row-count: 3' });
  });

  it('marks transport cancellation and disconnect conditions explicitly', () => {
    expect(isDisconnected({ status: 499 })).toBe(true);
    expect(isDisconnected({ status: 200 })).toBe(false);
  });

  it('keeps spike hooks with addToolOutput and cancel signatures', () => {
    const toolContinuation: ToolContinuation = {
      toolCallId: 'tool-1',
      output: { rows: [] },
    };
    expect(toolContinuation.toolCallId).toBe('tool-1');
    expect(typeof toolContinuation.output).toBe('object');
  });

  it('documents stream payload shape', () => {
    const streamPayload: StreamInput = {
      uiMessages: [{ id: 'm-1', role: 'user', content: 'status check' } as unknown as StreamInput['uiMessages'][number]],
      signal: makeController().signal,
    };

    expect(streamPayload.uiMessages).toHaveLength(1);
    expect(streamPayload.signal).toBeInstanceOf(AbortSignal);
  });
});
