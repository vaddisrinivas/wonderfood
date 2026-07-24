export type ServerApprovalRecord = {
  schemaVersion: 'wonder.remote.approval.v1';
  approvalId: string;
  actor: string;
};

export function isWonderApproval(value: unknown): value is ServerApprovalRecord {
  return !!(
    value &&
    typeof value === 'object' &&
    (value as Record<string, unknown>).schemaVersion === 'wonder.remote.approval.v1' &&
    typeof (value as Record<string, unknown>).approvalId === 'string' &&
    typeof (value as Record<string, unknown>).actor === 'string'
  );
}

export function separateApprovalFromSdkToolOutput(payload: {
  toolOutput?: Record<string, unknown>;
  approval?: Record<string, unknown>;
}) {
  return {
    toolOutput: payload.toolOutput,
    approval: isWonderApproval(payload.approval ?? null) ? payload.approval : undefined,
    hasApproval: Boolean(payload.approval && isWonderApproval(payload.approval)),
  };
}
