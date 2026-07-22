import { SQLiteDatabase } from 'expo-sqlite';

export type ActionStatus = 'queued' | 'running' | 'completed' | 'failed' | 'cancelled';

export type ActionEvent = {
  id: string;
  domain: string;
  conversation_id: string | null;
  actor: string;
  tool: string;
  record_ids: string | null;
  before_json: string | null;
  after_json: string | null;
  undo_payload_json: string | null;
  idempotency_key: string | null;
  status: ActionStatus;
  created_at: string;
  updated_at: string;
};

export type ActionWithUndo = ActionEvent & { undoPayload: unknown | null };

export async function createActionEvent(
  db: SQLiteDatabase,
  input: {
    id: string;
    domain: string;
    conversation_id: string | null;
    actor: string;
    tool: string;
    record_ids?: string[] | null;
    idempotency_key?: string | null;
  }
): Promise<ActionEvent> {
  const now = new Date().toISOString();
  const recordIds = input.record_ids ? JSON.stringify(input.record_ids) : null;
  await db.runAsync(
    `
      INSERT INTO action_events (
        id, domain, conversation_id, actor, tool, record_ids, before_json, after_json, undo_payload_json,
        idempotency_key, status, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, NULL, ?, 'queued', ?, ?)
    `,
    [
      input.id,
      input.domain,
      input.conversation_id,
      input.actor,
      input.tool,
      recordIds,
      input.idempotency_key ?? null,
      now,
      now,
    ]
  );

  return {
    id: input.id,
    domain: input.domain,
    conversation_id: input.conversation_id,
    actor: input.actor,
    tool: input.tool,
    record_ids: recordIds,
    before_json: null,
    after_json: null,
    undo_payload_json: null,
    idempotency_key: input.idempotency_key ?? null,
    status: 'queued',
    created_at: now,
    updated_at: now,
  };
}

export async function updateActionState(
  db: SQLiteDatabase,
  id: string,
  input: {
    status?: ActionStatus;
    before?: unknown;
    after?: unknown;
    undo_payload?: unknown;
  }
): Promise<void> {
  const now = new Date().toISOString();
  const sets: string[] = [];
  const values: (string | null)[] = [];

  if (input.status) {
    sets.push('status = ?');
    values.push(input.status);
  }
  if (input.before !== undefined) {
    sets.push('before_json = ?');
    values.push(JSON.stringify(input.before));
  }
  if (input.after !== undefined) {
    sets.push('after_json = ?');
    values.push(JSON.stringify(input.after));
  }
  if (input.undo_payload !== undefined) {
    sets.push('undo_payload_json = ?');
    values.push(JSON.stringify(input.undo_payload));
  }

  if (sets.length === 0) return;
  sets.push('updated_at = ?');
  values.push(now);
  values.push(id);

  await db.runAsync(`UPDATE action_events SET ${sets.join(', ')} WHERE id = ?`, values);
}

export async function getAction(db: SQLiteDatabase, id: string): Promise<ActionEvent | null> {
  return db.getFirstAsync<ActionEvent>('SELECT * FROM action_events WHERE id = ?', [id]);
}

export async function getActionsForDomain(db: SQLiteDatabase, domain: string): Promise<ActionEvent[]> {
  return db.getAllAsync<ActionEvent>(
    'SELECT * FROM action_events WHERE domain = ? ORDER BY created_at DESC',
    [domain]
  );
}
