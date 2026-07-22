import { mkdtempSync, rmSync, mkdirSync, writeFileSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const root = process.cwd();
const outDir = join(root, 'app', 'build', 'evidence', 'phase4-mcp-workflow-replay');
mkdirSync(outDir, { recursive: true });

const stateDir = mkdtempSync(join(tmpdir(), `wf-replay-${randomBytes(4).toString('hex')}-`));
process.env.LIFEOS_MCP_STATE_PATH = join(stateDir, 'mcp-runtime.json');
process.env.LIFEOS_WORKFLOW_CHECKPOINT_PATH = join(stateDir, 'workflow-runs.json');

type ToolResult = {
  json: unknown;
  reviewOnly: boolean;
  safety: string;
};

type WorkflowActionAfter = {
  changed_records?: string[];
  checkpoint_run_id?: string;
};

type WorkflowResult = {
  status?: 'completed' | 'failed';
  action?: {
    id: string;
    after_json?: WorkflowActionAfter | { checkpoint_run_id?: string; changed_records?: string[] };
  };
  checkpoint?: {
    runId?: string;
  };
};

type UndoResult = {
  status?: 'completed' | 'failed';
  action?: {
    status?: 'cancelled' | 'completed' | 'failed';
    id?: string;
  };
  undoResult?: { success?: boolean; message?: string };
};

function fail(message: string): never {
  throw new Error(message);
}

function asWorkflowResult(input: ToolResult['json'], action: string): WorkflowResult {
  if (!input || typeof input !== 'object') {
    fail(`${action} returned non-object payload`);
  }
  return input as WorkflowResult;
}

function asUndoResult(input: ToolResult['json']): UndoResult {
  if (!input || typeof input !== 'object') {
    fail('undo_action returned non-object payload');
  }
  return input as UndoResult;
}

function asAfterJson(value: unknown): WorkflowActionAfter | null {
  if (!value || typeof value !== 'object') {
    return null;
  }
  return value as WorkflowActionAfter;
}

(async () => {
  const tools = await import('../../server/src/mcp/tools');
  const state = await import('../../server/src/mcp/state');
  const checkpoints = await import('../../server/src/workflows/checkpoint');

  const workflowId = 'phase4_replay_workflow';
  const actionId = `phase4-replay-${Date.now()}`;

  const runPayload = asWorkflowResult(
    tools.callMcpTool('wonderfood.run_workflow', {
      actor: 'hearth',
      workflow: workflowId,
      domain: 'food',
      action_id: actionId,
      idempotency_key: 'phase4_replay_probe_1',
    }).json,
    'run_workflow',
  );

  if (runPayload.status !== 'completed') {
    fail(`run_workflow did not complete: ${JSON.stringify(runPayload)}`);
  }
  if (!runPayload.action?.id) {
    fail('run_workflow did not return an action id');
  }
  const workflowAction = state.getActionEvent(runPayload.action.id);
  if (!workflowAction || workflowAction.status !== 'completed') {
    fail(`action ${runPayload.action.id} missing or incomplete`);
  }

  const changedRecords = runPayload.action.after_json?.changed_records ?? [];
  if (changedRecords.length !== 2) {
    fail(`expected 2 workflow-created records, got ${changedRecords.length}`);
  }
  for (const id of changedRecords) {
    if (!state.findRecord(id)) {
      fail(`expected record ${id} to exist before undo`);
    }
  }

  const checkpointRunId = runPayload.checkpoint?.runId
    ?? asAfterJson((workflowAction as { after_json?: unknown }).after_json)?.checkpoint_run_id
    ?? asAfterJson(runPayload.action.after_json)?.checkpoint_run_id;
  if (typeof checkpointRunId !== 'string' || !checkpointRunId.trim()) {
    fail('run_workflow did not return a checkpoint run id');
  }
  const checkpoint = checkpoints.getWorkflowCheckpoint(checkpointRunId);
  if (!checkpoint) {
    fail(`checkpoint ${checkpointRunId} missing`);
  }
  if (checkpoint.status !== 'completed') {
    fail(`checkpoint status expected completed, got ${checkpoint.status}`);
  }
  if (checkpoint.steps.length < 2) {
    fail(`expected checkpoint to contain at least two steps, got ${checkpoint.steps.length}`);
  }

  const undoPayload = asUndoResult(
    tools.callMcpTool('wonderfood.undo_action', {
      actor: 'hearth',
      actionId: runPayload.action.id,
      idempotency_key: 'phase4_replay_undo_probe_1',
    }).json,
  );
  if (undoPayload.status !== 'completed') {
    fail(`undo_action failed: ${undoPayload.undoResult?.message || undoPayload.action?.status || 'unknown reason'}`);
  }

  for (const id of changedRecords) {
    if (state.findRecord(id)) {
      fail(`record ${id} should have been undone`);
    }
  }

  const proof = {
    proof: 'phase4_mcp_workflow_replay',
    evidence: {
      workflow_id: workflowId,
      action_id: workflowAction.id,
      changed_records: changedRecords,
      checkpoint_run_id: checkpointRunId,
      checkpoint_steps: checkpoint.steps.length,
      checkpoint_status: checkpoint.status,
      undo_status: undoPayload.status,
      undo_action_status: undoPayload.action?.status,
      undo_success: undoPayload.undoResult?.success,
    },
    state_files: {
      mcp_runtime_path: process.env.LIFEOS_MCP_STATE_PATH,
      checkpoint_path: process.env.LIFEOS_WORKFLOW_CHECKPOINT_PATH,
    },
    all_passed: true,
  };

  const outPath = join(outDir, 'phase4-mcp-workflow-replay-proof.json');
  writeFileSync(outPath, JSON.stringify(proof, null, 2), 'utf-8');
  console.log(`PASS ${outPath}`);
  console.log(`created_records=${changedRecords.join(',')}`);
  process.stdout.write(JSON.stringify(proof, null, 2));
  rmSync(stateDir, { recursive: true, force: true });
})().catch((error) => {
  rmSync(stateDir, { recursive: true, force: true });
  if (error instanceof Error) {
    console.error(error.stack ?? error.message);
  } else {
    console.error(String(error));
  }
  process.exit(1);
});
