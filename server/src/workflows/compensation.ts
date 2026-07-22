import { deleteRecord, restoreRecord } from '../mcp/state';
import { McpRecord } from '../mcp/state';

export type CompensationAction =
  | {
      action: 'delete_record';
      workflowRunId: string;
      recordId: string;
      record?: McpRecord;
    }
  | {
      action: 'restore_record';
      workflowRunId: string;
      recordId: string;
      record: McpRecord;
    };

export type WorkflowCompensationPlan = {
  workflowRunId: string;
  workflowId: string;
  actions: CompensationAction[];
};

export type WorkflowCompensationResult = {
  workflowRunId: string;
  applied: number;
  skipped: number;
  errors: Array<{ action: CompensationAction; error: string }>;
};

function normalizeString(input: unknown) {
  return typeof input === 'string' ? input.trim() : '';
}

type WorkflowStepResult = {
  id?: string;
  before?: McpRecord;
  after?: McpRecord;
  details?: unknown[];
};

type NormalizedWorkflowStep = {
  id: string;
  tool: string;
  status: string;
  result?: WorkflowStepResult;
};

function normalizeDetails(details: unknown): NormalizedWorkflowStep[] {
  if (!Array.isArray(details)) {
    return [];
  }

  const normalized: NormalizedWorkflowStep[] = [];

  for (const entry of details) {
    if (!entry || typeof entry !== 'object') {
      continue;
    }
    const record = entry as {
      id?: unknown;
      tool?: unknown;
      status?: unknown;
      result?: unknown;
    };
    if (typeof record.id !== 'string' || typeof record.tool !== 'string' || typeof record.status !== 'string') {
      continue;
    }

    normalized.push({
      id: record.id,
      tool: record.tool,
      status: record.status,
      result:
        record.result && typeof record.result === 'object'
          ? (record.result as WorkflowStepResult)
          : undefined,
    });
  }

  return normalized;
}

export function buildWorkflowCompensation(input: {
  workflowRunId: string;
  workflowId: string;
  changedRecords?: string[];
  details?: unknown;
}): WorkflowCompensationPlan {
  const actions: CompensationAction[] = [];
  const detailsEntries = normalizeDetails(Array.isArray(input.details) ? input.details : []);
  const visited = new Set<string>(normalizeUniqueIds(input.changedRecords));

  for (let index = detailsEntries.length - 1; index >= 0; index -= 1) {
    const step = detailsEntries[index];
    if (step.status !== 'ok') {
      continue;
    }

    if (step.tool === 'create_record') {
      const id = normalizeString(step.result?.id) || `${input.workflowId}:step-${index + 1}`;
      if (id && !visited.has(id)) {
        visited.add(id);
        actions.push({
          action: 'delete_record',
          workflowRunId: input.workflowRunId,
          recordId: id,
          ...(step.result?.after ? { record: step.result.after } : {}),
        });
      }
      continue;
    }

    if (step.tool === 'update_record' || step.tool === 'archive_record') {
      const before = step.result?.before;
      const id = before?.id || step.result?.after?.id;
      if (id && before && !visited.has(id)) {
        visited.add(id);
        actions.push({
          action: 'restore_record',
          workflowRunId: input.workflowRunId,
          recordId: id,
          record: before,
        });
      }
      continue;
    }

    if (step.tool === 'run_workflow' && Array.isArray(step.result?.details)) {
      const nested = buildWorkflowCompensation({
        workflowRunId: `${input.workflowRunId}:nested-${index + 1}`,
        workflowId: `${input.workflowId}.${step.id}`,
        details: step.result?.details || [],
      });
      for (const action of nested.actions) {
        if (visited.has(action.recordId)) {
          continue;
        }
        visited.add(action.recordId);
        actions.push(action);
      }
    }
  }

  return {
    workflowRunId: input.workflowRunId,
    workflowId: input.workflowId,
    actions,
  };
}

function normalizeUniqueIds(values: unknown[] = []) {
  const out: string[] = [];
  const seen = new Set<string>();

  for (const raw of values) {
    const value = normalizeString(raw);
    if (!value || seen.has(value)) {
      continue;
    }
    seen.add(value);
    out.push(value);
  }

  return out;
}

export function runWorkflowCompensation(plan: WorkflowCompensationPlan): WorkflowCompensationResult {
  const errors: Array<{ action: CompensationAction; error: string }> = [];
  let applied = 0;
  let skipped = 0;

  for (const action of plan.actions) {
    if (!action.recordId) {
      skipped += 1;
      continue;
    }

    try {
      if (action.action === 'delete_record') {
        const removed = deleteRecord(action.recordId);
        if (removed) {
          applied += 1;
        } else {
          skipped += 1;
        }
        continue;
      }

      if (action.action === 'restore_record') {
        restoreRecord(action.record);
        applied += 1;
      }
    } catch (error) {
      errors.push({
        action,
        error: error instanceof Error ? error.message : 'compensation failed',
      });
    }
  }

  return {
    workflowRunId: plan.workflowRunId,
    applied,
    skipped,
    errors,
  };
}
