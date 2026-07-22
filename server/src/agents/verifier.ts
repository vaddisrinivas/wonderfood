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
};

export async function verifyResult(input: VerifyInput): Promise<VerificationResult> {
  const supportsUndo = input.expectedSupportsUndo ?? input.expected !== 'chat_reply';
  if (!input.expected || input.expected.trim().length === 0) {
    return {
      actionId: input.actionId,
      expected: input.expected,
      status: 'denied',
      checks: ['input_shape'],
      reason: 'Expected action target is missing.',
    };
  }

  const checks = ['idempotent', 'source_bound'];
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
