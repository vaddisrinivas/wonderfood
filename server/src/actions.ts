export type ActionReceipt = {
  id: string;
  actor: string;
  domain: string;
  tool: string;
  status: 'queued' | 'running' | 'completed' | 'failed' | 'cancelled';
  record_ids: string[];
  created_at: string;
  updated_at: string;
  idempotency_key?: string;
  undo_deadline_at?: string;
};

const ACTION_TTL_MS = 24 * 60 * 60 * 1000;
const actions = new Map<string, ActionReceipt>();

export function createActionReceipt(input: Omit<ActionReceipt, 'status' | 'created_at' | 'updated_at'> & { now?: string }) {
  const now = input.now ?? new Date().toISOString();
  const receipt: ActionReceipt = {
    ...input,
    status: 'queued',
    created_at: now,
    updated_at: now,
    undo_deadline_at: new Date(Date.now() + ACTION_TTL_MS).toISOString(),
  };
  actions.set(receipt.id, receipt);
  return receipt;
}

export function markActionCompleted(id: string) {
  const existing = actions.get(id);
  if (!existing) {
    return null;
  }

  const updated: ActionReceipt = {
    ...existing,
    status: 'completed',
    updated_at: new Date().toISOString(),
  };
  actions.set(id, updated);
  return updated;
}

export function getActionReceipt(id: string) {
  return actions.get(id) ?? null;
}
