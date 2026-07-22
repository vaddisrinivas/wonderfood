import { SQLiteDatabase } from 'expo-sqlite';

export type UndoRecord = {
  id: string;
  action_id: string;
  payload_json: string;
  expires_at: string | null;
  created_at: string;
};

export async function createUndoEvent(
  db: SQLiteDatabase,
  input: {
    id: string;
    action_id: string;
    payload: unknown;
    expires_at?: string | null;
  }
): Promise<void> {
  const now = new Date().toISOString();
  await db.runAsync(
    `
      INSERT OR REPLACE INTO undo_events (id, action_id, payload_json, expires_at, created_at)
      VALUES (?, ?, ?, ?, ?)
    `,
    [input.id, input.action_id, JSON.stringify(input.payload), input.expires_at ?? null, now]
  );
}

export async function getUndoForAction(db: SQLiteDatabase, actionId: string): Promise<UndoRecord | null> {
  return db.getFirstAsync<UndoRecord>('SELECT * FROM undo_events WHERE action_id = ?', [actionId]);
}

export async function listUndoEvents(db: SQLiteDatabase): Promise<UndoRecord[]> {
  return db.getAllAsync<UndoRecord>('SELECT * FROM undo_events ORDER BY created_at DESC');
}

export async function removeUndoEvent(db: SQLiteDatabase, id: string): Promise<void> {
  await db.runAsync('DELETE FROM undo_events WHERE id = ?', [id]);
}
