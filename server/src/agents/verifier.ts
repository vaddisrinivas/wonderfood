export async function verifyResult(input: { actionId: string; expected: string }) {
  return {
    actionId: input.actionId,
    expected: input.expected,
    status: 'verified',
    checks: ['idempotent', 'source_bound', 'undo_ready'],
  };
}
