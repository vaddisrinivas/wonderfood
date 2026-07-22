import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

type WorkflowCheckpointStepStatus = 'ok' | 'failed' | 'skipped' | 'cancelled';
type WorkflowCheckpointRunStatus = 'running' | 'completed' | 'failed' | 'cancelled' | 'compensated';

export type WorkflowStepCheckpoint = {
  id: string;
  tool: string;
  status: WorkflowCheckpointStepStatus;
  changed_records: string[];
  result?: unknown;
  error?: string;
  started_at: string;
  finished_at: string;
};

export type WorkflowRunCheckpoint = {
  run_id: string;
  workflow_id: string;
  domain: string;
  actor: string;
  status: WorkflowCheckpointRunStatus;
  started_at: string;
  updated_at: string;
  finished_at?: string;
  steps: WorkflowStepCheckpoint[];
  changed_records: string[];
  error?: string;
};

type StorePayload = {
  schema_version: 1;
  updated_at: string;
  runs: Record<string, WorkflowRunCheckpoint>;
};

const WORKFLOW_CHECKPOINT_PATH = process.env.LIFEOS_WORKFLOW_CHECKPOINT_PATH
  ?? join(process.cwd(), 'server-data', 'workflow-runs.json');
const STORE_VERSION = 1;

let loaded = false;
let store: StorePayload = {
  schema_version: STORE_VERSION,
  updated_at: nowIso(),
  runs: {},
};

function ensureDir(path: string) {
  if (!existsSync(dirname(path))) {
    mkdirSync(dirname(path), { recursive: true });
  }
}

function nowIso() {
  return new Date().toISOString();
}

function stableStringify(value: unknown): string {
  if (value === null || value === undefined) {
    return String(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map((entry) => stableStringify(entry)).join(',')}]`;
  }
  if (typeof value === 'object') {
    return `{${Object.keys(value)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stableStringify((value as Record<string, unknown>)[key])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value);
}

function hashValue(value: unknown): string {
  return createHash('sha256').update(stableStringify(value)).digest('hex');
}

function persist() {
  ensureDir(WORKFLOW_CHECKPOINT_PATH);
  store.updated_at = nowIso();
  writeFileSync(WORKFLOW_CHECKPOINT_PATH, JSON.stringify(store, null, 2), 'utf-8');
}

function isRunPayload(value: unknown): value is StorePayload {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const entry = value as {
    schema_version?: number;
    updated_at?: unknown;
    runs?: unknown;
  };
  return (
    (entry.schema_version === undefined || entry.schema_version === STORE_VERSION)
    && typeof entry.updated_at === 'string'
    && typeof entry.runs === 'object'
    && entry.runs !== null
  );
}

function load() {
  if (loaded) {
    return;
  }
  loaded = true;

  if (!existsSync(WORKFLOW_CHECKPOINT_PATH)) {
    return;
  }

  try {
    const raw = readFileSync(WORKFLOW_CHECKPOINT_PATH, 'utf-8');
    const parsed = JSON.parse(raw) as unknown;
    if (!isRunPayload(parsed)) {
      return;
    }
    store = {
      schema_version: STORE_VERSION,
      updated_at: typeof parsed.updated_at === 'string' ? parsed.updated_at : nowIso(),
      runs: (parsed.runs as Record<string, WorkflowRunCheckpoint>) || {},
    };
  } catch {
    return;
  }
}

function makeRunId(workflowId: string, actor: string, seed?: string) {
  const seeded = seed ? hashValue({ workflowId, actor, seed }).slice(0, 18) : createHash('sha256').update(`${Date.now()}:${Math.random()}`).digest('hex').slice(0, 18);
  return `${workflowId}:${actor}:${seeded}`;
}

export function startWorkflowCheckpoint(input: {
  workflowId: string;
  domain: string;
  actor: string;
  changedRecords?: string[];
  seed?: string;
}): string {
  load();
  const runId = makeRunId(input.workflowId, input.actor, input.seed);
  store.runs[runId] = {
    run_id: runId,
    workflow_id: input.workflowId,
    domain: input.domain,
    actor: input.actor,
    status: 'running',
    started_at: nowIso(),
    updated_at: nowIso(),
    steps: [],
    changed_records: [...(input.changedRecords ?? [])],
  };
  persist();
  return runId;
}

export function markWorkflowStep(input: {
  runId: string;
  id: string;
  tool: string;
  status: WorkflowCheckpointStepStatus;
  changedRecords?: string[];
  result?: unknown;
  error?: string;
  startedAt: string;
  finishedAt: string;
}) {
  load();
  const run = store.runs[input.runId];
  if (!run) {
    return;
  }

  run.steps.push({
    id: input.id,
    tool: input.tool,
    status: input.status,
    changed_records: input.changedRecords ?? [],
    result: input.result,
    error: input.error,
    started_at: input.startedAt,
    finished_at: input.finishedAt,
  });
  for (const id of input.changedRecords ?? []) {
    if (!run.changed_records.includes(id)) {
      run.changed_records.push(id);
    }
  }
  run.updated_at = nowIso();
  persist();
}

export function completeWorkflowCheckpoint(
  runId: string,
  opts: {
    status: 'completed' | 'failed' | 'cancelled';
    error?: string;
    changedRecords?: string[];
  },
): void {
  load();
  const run = store.runs[runId];
  if (!run) {
    return;
  }

  run.status = opts.status;
  run.updated_at = nowIso();
  run.finished_at = nowIso();
  run.error = opts.error;
  for (const id of opts.changedRecords ?? []) {
    if (!run.changed_records.includes(id)) {
      run.changed_records.push(id);
    }
  }
  persist();
}

export function finalizeWorkflowCompensated(runId: string, message?: string) {
  load();
  const run = store.runs[runId];
  if (!run) {
    return;
  }
  run.status = 'compensated';
  run.updated_at = nowIso();
  run.finished_at = nowIso();
  if (message) {
    run.error = message;
  }
  persist();
}

export function getWorkflowCheckpoint(runId: string): WorkflowRunCheckpoint | null {
  load();
  const run = store.runs[runId];
  return run ? { ...run, steps: [...run.steps], changed_records: [...run.changed_records] } : null;
}

export function listWorkflowCheckpoints() {
  load();
  return Object.values(store.runs).map((entry) => ({ ...entry, steps: [...entry.steps], changed_records: [...entry.changed_records] }));
}
