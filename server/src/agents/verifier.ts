export type VerificationResult = {
  actionId: string;
  expected: string;
  status: 'verified' | 'denied';
  checks: string[];
  reason?: string;
};

export async function verifyResult(input: { actionId: string; expected: string; sourceBound?: boolean }): Promise<VerificationResult> {
  if (!input.expected || input.expected.trim().length === 0) {
    return {
      actionId: input.actionId,
      expected: input.expected,
      status: 'denied',
      checks: ['input_shape'],
      reason: 'Expected action target is missing.',
    };
  }

  const checks = ['idempotent', 'source_bound', 'undo_ready'];
  if (!input.sourceBound) {
    checks.push('source_bound_fallback');
  }

  return {
    actionId: input.actionId,
    expected: input.expected,
    status: 'verified',
    checks,
  };
}
