import { SQLiteDatabase } from 'expo-sqlite';

export type OutboxStatus = 'pending' | 'inflight' | 'failed' | 'done';

export type OutboxEvent = {
  id: string;
  action_key: string;
  domain: string;
  payload_json: string;
  status: OutboxStatus;
  attempts: number;
  last_error: string | null;
  created_at: string;
  updated_at: string;
};

export async function enqueueOutboxEvent(
  db: SQLiteDatabase,
  event: Omit<OutboxEvent, 'status' | 'attempts' | 'last_error' | 'created_at' | 'updated_at'>
): Promise<OutboxEvent> {
  const now = new Date().toISOString();
  const payload = event.payload_json;
  const status: OutboxStatus = 'pending';
  await db.runAsync(
    `
      INSERT INTO outbox_events (id, action_key, domain, payload_json, status, attempts, last_error, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, 0, NULL, ?, ?)
    `,
    [event.id, event.action_key, event.domain, payload, status, now, now]
  );
  return {
    ...event,
    status,
    attempts: 0,
    last_error: null,
    created_at: now,
    updated_at: now,
  };
}

export async function listOutboxEvents(db: SQLiteDatabase, status?: OutboxStatus): Promise<OutboxEvent[]> {
  if (status) {
    return db.getAllAsync<OutboxEvent>('SELECT * FROM outbox_events WHERE status = ? ORDER BY updated_at ASC', [status]);
  }
  return db.getAllAsync<OutboxEvent>('SELECT * FROM outbox_events ORDER BY updated_at ASC');
}

export async function markOutboxEvent(
  db: SQLiteDatabase,
  id: string,
  update: {
    status?: OutboxStatus;
    last_error?: string | null;
    attemptsDelta?: number;
  }
): Promise<void> {
  const now = new Date().toISOString();
  if (update.attemptsDelta !== undefined && update.attemptsDelta !== 0) {
    await db.runAsync(
      `UPDATE outbox_events SET attempts = attempts + ?, status = ?, last_error = ?, updated_at = ? WHERE id = ?`,
      [
        update.attemptsDelta,
        update.status ?? 'pending',
        update.last_error ?? null,
        now,
        id,
      ]
    );
    return;
  }

  const fields = [
    update.status !== undefined ? 'status = ?' : '',
    update.last_error !== undefined ? 'last_error = ?' : '',
  ].filter(Boolean).join(', ');

  if (!fields) return;

  const values: (string | number | null)[] = [];
  if (update.status !== undefined) values.push(update.status);
  if (update.last_error !== undefined) values.push(update.last_error);
  values.push(now);
  values.push(id);
  await db.runAsync(`UPDATE outbox_events SET ${fields}, updated_at = ? WHERE id = ?`, values);
}

export async function deleteOutboxEvent(db: SQLiteDatabase, id: string): Promise<void> {
  await db.runAsync(`DELETE FROM outbox_events WHERE id = ?`, [id]);
}
