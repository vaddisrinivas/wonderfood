import { SQLiteDatabase } from 'expo-sqlite';

import {
  createWorkflowRun,
  getWorkflowRun,
  updateWorkflowRun,
  WorkflowRunRow,
  WorkflowRunStatus,
} from '@/src/db/workflows';

export type WorkflowStepStatus = 'pending' | 'running' | 'completed' | 'failed' | 'cancelled' | 'compensated';

export type WorkflowStepDefinition = {
  id: string;
  title: string;
  tool?: string;
  cancellable?: boolean;
  compensation_tool?: string;
};

export type WorkflowStepReceipt = {
  operation_ids?: string[];
  action_ids?: string[];
  source_ids?: string[];
  record_ids?: string[];
  message?: string;
  payload?: Record<string, unknown>;
};

export type WorkflowCheckpointStep = WorkflowStepDefinition & {
  status: WorkflowStepStatus;
  receipts: WorkflowStepReceipt[];
  started_at?: string;
  completed_at?: string;
  cancelled_at?: string;
  failed_at?: string;
  error?: string;
};

export type WorkflowCheckpointPayload = {
  schema_version: 'lifeos.workflow-run.v1';
  run_id: string;
  domain: string;
  workflow_id: string;
  cursor: number;
  resume_count: number;
  steps: WorkflowCheckpointStep[];
  completed_operation_ids: string[];
  completed_action_ids: string[];
  source_ids: string[];
  created_at: string;
  updated_at: string;
  cancelled_at?: string;
  cancel_reason?: string;
  resumed_at?: string;
  failure_reason?: string;
};

export type WorkflowRunSnapshot = {
  row: WorkflowRunRow;
  checkpoint: WorkflowCheckpointPayload;
};

export type WorkflowReceiptSummary = {
  run_id: string;
  workflow_id: string;
  status: WorkflowRunStatus;
  completed_steps: number;
  cancelled_steps: number;
  failed_steps: number;
  operation_ids: string[];
  action_ids: string[];
  source_ids: string[];
  record_ids: string[];
  receipts: WorkflowStepReceipt[];
};

const VERSION = 'lifeos.workflow-run.v1' as const;

export async function startWorkflowRun(input: {
  db: SQLiteDatabase;
  id: string;
  domain: string;
  workflowId: string;
  inputs?: Record<string, unknown>;
  steps: WorkflowStepDefinition[];
}): Promise<WorkflowRunSnapshot> {
  const now = new Date().toISOString();
  const checkpoint: WorkflowCheckpointPayload = {
    schema_version: VERSION,
    run_id: input.id,
    domain: input.domain,
    workflow_id: input.workflowId,
    cursor: 0,
    resume_count: 0,
    steps: input.steps.map((step) => ({
      ...step,
      cancellable: step.cancellable ?? true,
      status: 'pending',
      receipts: [],
    })),
    completed_operation_ids: [],
    completed_action_ids: [],
    source_ids: [],
    created_at: now,
    updated_at: now,
  };
  const row = await createWorkflowRun(input.db, {
    id: input.id,
    domain: input.domain,
    workflow_id: input.workflowId,
    inputs: input.inputs ?? {},
    status: 'running',
    payload: checkpoint,
  });
  return { row, checkpoint };
}

export async function getWorkflowRunSnapshot(
  db: SQLiteDatabase,
  runId: string,
): Promise<WorkflowRunSnapshot | null> {
  const row = await getWorkflowRun(db, runId);
  if (!row) return null;
  return { row, checkpoint: checkpointFromRow(row) };
}

export async function recordWorkflowStep(input: {
  db: SQLiteDatabase;
  runId: string;
  stepId: string;
  status: Exclude<WorkflowStepStatus, 'pending'>;
  receipt?: WorkflowStepReceipt;
  error?: string;
}): Promise<WorkflowRunSnapshot> {
  const snapshot = await requireSnapshot(input.db, input.runId);
  if (snapshot.row.status === 'completed' || snapshot.row.status === 'cancelled' || snapshot.row.status === 'failed') {
    throw new Error(`Workflow run is ${snapshot.row.status}; resume before recording more steps.`);
  }
  const checkpoint = cloneCheckpoint(snapshot.checkpoint);
  const step = checkpoint.steps.find((item) => item.id === input.stepId);
  if (!step) {
    throw new Error(`Unknown workflow step: ${input.stepId}`);
  }

  const now = new Date().toISOString();
  if (!step.started_at) step.started_at = now;
  step.status = input.status;
  if (input.receipt) {
    step.receipts.push(input.receipt);
    mergeUnique(checkpoint.completed_operation_ids, input.receipt.operation_ids);
    mergeUnique(checkpoint.completed_action_ids, input.receipt.action_ids);
    mergeUnique(checkpoint.source_ids, input.receipt.source_ids);
  }
  if (input.status === 'completed') step.completed_at = now;
  if (input.status === 'cancelled') step.cancelled_at = now;
  if (input.status === 'failed') {
    step.failed_at = now;
    step.error = input.error ?? input.receipt?.message ?? 'Workflow step failed.';
    checkpoint.failure_reason = step.error;
  }

  checkpoint.cursor = nextCursor(checkpoint.steps);
  checkpoint.updated_at = now;
  const status = workflowStatus(checkpoint.steps, input.status);
  await updateWorkflowRun(input.db, input.runId, { status, payload: checkpoint });
  return requireSnapshot(input.db, input.runId);
}

export async function cancelWorkflowRun(input: {
  db: SQLiteDatabase;
  runId: string;
  reason: string;
}): Promise<WorkflowRunSnapshot> {
  const snapshot = await requireSnapshot(input.db, input.runId);
  if (snapshot.row.status === 'completed') return snapshot;

  const checkpoint = cloneCheckpoint(snapshot.checkpoint);
  const now = new Date().toISOString();
  for (const step of checkpoint.steps) {
    if (step.status === 'pending' || step.status === 'running') {
      step.status = step.cancellable === false ? 'failed' : 'cancelled';
      if (step.status === 'cancelled') step.cancelled_at = now;
      if (step.status === 'failed') {
        step.failed_at = now;
        step.error = 'Step is not cancellation safe.';
      }
    }
  }
  checkpoint.cancelled_at = now;
  checkpoint.cancel_reason = input.reason;
  checkpoint.updated_at = now;
  checkpoint.cursor = nextCursor(checkpoint.steps);

  const status = checkpoint.steps.some((step) => step.status === 'failed') ? 'failed' : 'cancelled';
  await updateWorkflowRun(input.db, input.runId, { status, payload: checkpoint });
  return requireSnapshot(input.db, input.runId);
}

export async function resumeWorkflowRun(input: {
  db: SQLiteDatabase;
  runId: string;
}): Promise<WorkflowRunSnapshot> {
  const snapshot = await requireSnapshot(input.db, input.runId);
  if (snapshot.row.status === 'completed') return snapshot;

  const checkpoint = cloneCheckpoint(snapshot.checkpoint);
  const now = new Date().toISOString();
  for (const step of checkpoint.steps) {
    if (step.status === 'cancelled' || step.status === 'failed' || step.status === 'running') {
      step.status = 'pending';
      delete step.cancelled_at;
      delete step.failed_at;
      delete step.error;
    }
  }
  checkpoint.resume_count += 1;
  checkpoint.resumed_at = now;
  checkpoint.updated_at = now;
  checkpoint.cursor = nextCursor(checkpoint.steps);
  delete checkpoint.cancelled_at;
  delete checkpoint.cancel_reason;
  delete checkpoint.failure_reason;

  await updateWorkflowRun(input.db, input.runId, { status: 'running', payload: checkpoint });
  return requireSnapshot(input.db, input.runId);
}

export async function getWorkflowReceiptSummary(
  db: SQLiteDatabase,
  runId: string,
): Promise<WorkflowReceiptSummary> {
  const snapshot = await requireSnapshot(db, runId);
  const receipts = snapshot.checkpoint.steps.flatMap((step) => step.receipts);
  return {
    run_id: runId,
    workflow_id: snapshot.row.workflow_id,
    status: snapshot.row.status,
    completed_steps: snapshot.checkpoint.steps.filter((step) => step.status === 'completed').length,
    cancelled_steps: snapshot.checkpoint.steps.filter((step) => step.status === 'cancelled').length,
    failed_steps: snapshot.checkpoint.steps.filter((step) => step.status === 'failed').length,
    operation_ids: unique(receipts.flatMap((receipt) => receipt.operation_ids ?? [])),
    action_ids: unique(receipts.flatMap((receipt) => receipt.action_ids ?? [])),
    source_ids: unique(receipts.flatMap((receipt) => receipt.source_ids ?? [])),
    record_ids: unique(receipts.flatMap((receipt) => receipt.record_ids ?? [])),
    receipts,
  };
}

async function requireSnapshot(db: SQLiteDatabase, runId: string): Promise<WorkflowRunSnapshot> {
  const snapshot = await getWorkflowRunSnapshot(db, runId);
  if (!snapshot) {
    throw new Error(`Unknown workflow run: ${runId}`);
  }
  return snapshot;
}

function checkpointFromRow(row: WorkflowRunRow): WorkflowCheckpointPayload {
  const parsed = row.payload_json ? safeParse(row.payload_json) : null;
  if (isCheckpointPayload(parsed)) {
    return parsed;
  }
  const now = row.updated_at || new Date().toISOString();
  return {
    schema_version: VERSION,
    run_id: row.id,
    domain: row.domain,
    workflow_id: row.workflow_id,
    cursor: 0,
    resume_count: 0,
    steps: [],
    completed_operation_ids: [],
    completed_action_ids: [],
    source_ids: [],
    created_at: row.created_at || now,
    updated_at: now,
  };
}

function isCheckpointPayload(value: unknown): value is WorkflowCheckpointPayload {
  if (!value || typeof value !== 'object') return false;
  const candidate = value as Partial<WorkflowCheckpointPayload>;
  return candidate.schema_version === VERSION
    && typeof candidate.run_id === 'string'
    && Array.isArray(candidate.steps)
    && Array.isArray(candidate.completed_operation_ids)
    && Array.isArray(candidate.completed_action_ids)
    && Array.isArray(candidate.source_ids);
}

function safeParse(raw: string) {
  try {
    return JSON.parse(raw) as unknown;
  } catch {
    return null;
  }
}

function cloneCheckpoint(checkpoint: WorkflowCheckpointPayload): WorkflowCheckpointPayload {
  return JSON.parse(JSON.stringify(checkpoint)) as WorkflowCheckpointPayload;
}

function workflowStatus(
  steps: WorkflowCheckpointStep[],
  latestStatus: WorkflowStepStatus,
): WorkflowRunStatus {
  if (latestStatus === 'failed') return 'failed';
  if (latestStatus === 'cancelled') return 'cancelled';
  return steps.length > 0 && steps.every((step) => step.status === 'completed') ? 'completed' : 'running';
}

function nextCursor(steps: WorkflowCheckpointStep[]) {
  const index = steps.findIndex((step) => step.status !== 'completed');
  return index === -1 ? steps.length : index;
}

function mergeUnique(target: string[], values?: string[]) {
  for (const value of values ?? []) {
    if (!target.includes(value)) target.push(value);
  }
}

function unique(values: string[]) {
  return Array.from(new Set(values));
}
