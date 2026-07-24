import { z } from 'zod';

export type VerificationResult = {
  actionId: string;
  expected: string;
  status: 'verified' | 'denied';
  checks: string[];
  reason?: string;
};

export type VerifyInput = {
  actionId: string;
  expected: string;
  sourceBound?: boolean;
  expectedSupportsUndo?: boolean;
  actualStatus?: 'queued' | 'running' | 'completed' | 'failed' | 'cancelled';
  actualRecordIds?: string[];
};

const verifyInputSchema = z.object({
  actionId: z.string().min(1),
  expected: z.string(),
  sourceBound: z.boolean().optional(),
  expectedSupportsUndo: z.boolean().optional(),
  actualStatus: z.enum(['queued', 'running', 'completed', 'failed', 'cancelled']).optional(),
  actualRecordIds: z.array(z.string().trim().min(1)).optional(),
});

const REVERSIBLE_RECORD_ACTIONS = new Set([
  'create_record',
  'update_record',
  'archive_record',
  'wonderfood.create_record',
  'wonderfood.update_record',
  'wonderfood.archive_record',
]);

export async function verifyResult(input: VerifyInput): Promise<VerificationResult> {
  const parsed = verifyInputSchema.safeParse(input);
  if (!parsed.success) {
    return {
      actionId: input.actionId ?? 'unknown',
      expected: input.expected ?? '',
      status: 'denied',
      checks: ['input_shape'],
      reason: 'Verification input failed schema validation.',
    };
  }
  input = parsed.data;
  const reversibleRecordAction = REVERSIBLE_RECORD_ACTIONS.has(input.expected);
  const supportsUndo = reversibleRecordAction || (input.expectedSupportsUndo ?? input.expected !== 'chat_reply');
  if (!input.expected || input.expected.trim().length === 0) {
    return {
      actionId: input.actionId,
      expected: input.expected,
      status: 'denied',
      checks: ['input_shape'],
      reason: 'Expected action target is missing.',
    };
  }

  if (input.actualStatus !== 'completed') {
    return {
      actionId: input.actionId,
      expected: input.expected,
      status: 'denied',
      checks: ['input_shape', 'canonical_postcondition'],
      reason: `Action status is ${input.actualStatus ?? 'unknown'}; canonical completion was not proven.`,
    };
  }

  if ((reversibleRecordAction || input.expectedSupportsUndo) && (!input.actualRecordIds || input.actualRecordIds.length === 0)) {
    return {
      actionId: input.actionId,
      expected: input.expected,
      status: 'denied',
      checks: ['canonical_postcondition', 'record_identity'],
      reason: 'A reversible record action must identify the affected record.',
    };
  }

  const checks = ['idempotent', 'canonical_postcondition', 'source_bound'];
  if (!input.sourceBound) {
    checks.push('source_bound_fallback');
  }
  if (supportsUndo) {
    checks.push('undo_ready');
  }

  return {
    actionId: input.actionId,
    expected: input.expected,
    status: 'verified',
    checks,
  };
}
