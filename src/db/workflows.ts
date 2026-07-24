import { SQLiteDatabase } from 'expo-sqlite';

export type WorkflowRunStatus = 'running' | 'completed' | 'cancelled' | 'failed';

export type WorkflowRunRow = {
  id: string;
  domain: string;
  workflow_id: string;
  inputs_json: string | null;
  status: WorkflowRunStatus;
  payload_json: string | null;
  created_at: string;
  updated_at: string;
};

export async function createWorkflowRun(
  db: SQLiteDatabase,
  input: {
    id: string;
    domain: string;
    workflow_id: string;
    inputs?: unknown;
    status?: WorkflowRunStatus;
    payload?: unknown;
  },
): Promise<WorkflowRunRow> {
  const now = new Date().toISOString();
  await db.runAsync(
    `
      INSERT INTO workflow_runs (
        id, domain, workflow_id, inputs_json, status, payload_json, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `,
    [
      input.id,
      input.domain,
      input.workflow_id,
      input.inputs === undefined ? null : JSON.stringify(input.inputs),
      input.status ?? 'running',
      input.payload === undefined ? null : JSON.stringify(input.payload),
      now,
      now,
    ],
  );

  return {
    id: input.id,
    domain: input.domain,
    workflow_id: input.workflow_id,
    inputs_json: input.inputs === undefined ? null : JSON.stringify(input.inputs),
    status: input.status ?? 'running',
    payload_json: input.payload === undefined ? null : JSON.stringify(input.payload),
    created_at: now,
    updated_at: now,
  };
}

export async function updateWorkflowRun(
  db: SQLiteDatabase,
  id: string,
  input: {
    status?: WorkflowRunStatus;
    payload?: unknown;
  },
): Promise<void> {
  const sets: string[] = [];
  const values: (string | null)[] = [];

  if (input.status) {
    sets.push('status = ?');
    values.push(input.status);
  }
  if (input.payload !== undefined) {
    sets.push('payload_json = ?');
    values.push(JSON.stringify(input.payload));
  }

  if (sets.length === 0) return;
  sets.push('updated_at = ?');
  values.push(new Date().toISOString());
  values.push(id);

  await db.runAsync(`UPDATE workflow_runs SET ${sets.join(', ')} WHERE id = ?`, values);
}

export async function getWorkflowRun(db: SQLiteDatabase, id: string): Promise<WorkflowRunRow | null> {
  return db.getFirstAsync<WorkflowRunRow>('SELECT * FROM workflow_runs WHERE id = ?', [id]);
}

export async function getWorkflowRunsForDomain(
  db: SQLiteDatabase,
  domain: string,
): Promise<WorkflowRunRow[]> {
  return db.getAllAsync<WorkflowRunRow>(
    'SELECT * FROM workflow_runs WHERE domain = ? ORDER BY updated_at DESC',
    [domain],
  );
}
