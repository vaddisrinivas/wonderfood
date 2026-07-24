import { ToolLoopAgent, tool, zodSchema } from 'ai';
import { z } from 'zod';
import { spikeOpenAIModel } from './model.js';

export type LocalQueryParams = {
  from: string;
  limit: number;
  queryText: string;
};

export const localQuery = tool({
  description: 'Request a bounded query from the local device using local data only.',
  inputSchema: zodSchema(
    z.object({
      from: z.string().min(1),
      limit: z.number().int().min(1).max(25),
    queryText: z.string().min(1).max(280),
    })
  ),
});

export const approval = {
  required: false,
  type: 'tool-call' as const,
};

export const agent = new ToolLoopAgent({
  model: spikeOpenAIModel,
  tools: { localQuery },
});

export const isApprovalMessage = (value: unknown): value is { schemaVersion: string; approvalId: string } => {
  return Boolean(
    value &&
      typeof value === 'object' &&
      'schemaVersion' in value &&
      'approvalId' in value
  );
};
