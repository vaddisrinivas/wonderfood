import { describe, expect, it } from 'vitest';

import {
  cancelWorkflowRun,
  getWorkflowReceiptSummary,
  recordWorkflowStep,
  resumeWorkflowRun,
  startWorkflowRun,
} from '@/src/workflows/runtime';
import { MemoryDb } from '../helpers/memory-db';

const steps = [
  { id: 'choose-dinner', title: 'Choose dinner', tool: 'food.plan_dinner' },
  { id: 'reserve-pantry', title: 'Reserve pantry', tool: 'food.reserve_pantry' },
  { id: 'build-shopping', title: 'Build shopping list', tool: 'food.build_shopping_list' },
];

describe('workflow runtime', () => {
  it('cancels a workflow without losing completed receipts', async () => {
    const db = new MemoryDb();
    await startWorkflowRun({
      db: db as any,
      id: 'weekly-food-reset-run',
      domain: 'food',
      workflowId: 'weekly-food-reset',
      inputs: { day: 'Thursday' },
      steps,
    });

    await recordWorkflowStep({
      db: db as any,
      runId: 'weekly-food-reset-run',
      stepId: 'choose-dinner',
      status: 'completed',
      receipt: {
        operation_ids: ['op-plan-green-dal'],
        action_ids: ['action-plan-green-dal'],
        source_ids: ['sqlite:meal-green-dal'],
        record_ids: ['meal-green-dal'],
        message: 'Dinner selected from pantry.',
      },
    });
    await recordWorkflowStep({
      db: db as any,
      runId: 'weekly-food-reset-run',
      stepId: 'reserve-pantry',
      status: 'completed',
      receipt: {
        operation_ids: ['op-reserve-yogurt'],
        action_ids: ['action-reserve-yogurt'],
        source_ids: ['sqlite:pantry-yogurt'],
        record_ids: ['pantry-yogurt'],
        message: 'Use-soon yogurt reserved.',
      },
    });

    const cancelled = await cancelWorkflowRun({
      db: db as any,
      runId: 'weekly-food-reset-run',
      reason: 'User cancelled before shopping write.',
    });
    const summary = await getWorkflowReceiptSummary(db as any, 'weekly-food-reset-run');

    expect(cancelled.row.status).toBe('cancelled');
    expect(cancelled.checkpoint.steps.map((step) => step.status)).toEqual([
      'completed',
      'completed',
      'cancelled',
    ]);
    expect(summary.completed_steps).toBe(2);
    expect(summary.cancelled_steps).toBe(1);
    expect(summary.operation_ids).toEqual(['op-plan-green-dal', 'op-reserve-yogurt']);
    expect(summary.action_ids).toEqual(['action-plan-green-dal', 'action-reserve-yogurt']);
    expect(summary.source_ids).toEqual(['sqlite:meal-green-dal', 'sqlite:pantry-yogurt']);
  });

  it('resumes a cancelled workflow and completes remaining work once', async () => {
    const db = new MemoryDb();
    await startWorkflowRun({
      db: db as any,
      id: 'resume-food-run',
      domain: 'food',
      workflowId: 'meal-plan-to-shopping',
      steps,
    });
    await recordWorkflowStep({
      db: db as any,
      runId: 'resume-food-run',
      stepId: 'choose-dinner',
      status: 'completed',
      receipt: {
        operation_ids: ['op-plan-green-dal'],
        action_ids: ['action-plan-green-dal'],
        record_ids: ['meal-green-dal'],
      },
    });
    await cancelWorkflowRun({
      db: db as any,
      runId: 'resume-food-run',
      reason: 'Pause before shopping.',
    });

    const resumed = await resumeWorkflowRun({ db: db as any, runId: 'resume-food-run' });
    expect(resumed.row.status).toBe('running');
    expect(resumed.checkpoint.resume_count).toBe(1);
    expect(resumed.checkpoint.steps.map((step) => step.status)).toEqual([
      'completed',
      'pending',
      'pending',
    ]);

    await recordWorkflowStep({
      db: db as any,
      runId: 'resume-food-run',
      stepId: 'reserve-pantry',
      status: 'completed',
      receipt: { operation_ids: ['op-reserve-yogurt'], record_ids: ['pantry-yogurt'] },
    });
    const completed = await recordWorkflowStep({
      db: db as any,
      runId: 'resume-food-run',
      stepId: 'build-shopping',
      status: 'completed',
      receipt: { operation_ids: ['op-buy-spinach'], record_ids: ['shopping-spinach'] },
    });
    const summary = await getWorkflowReceiptSummary(db as any, 'resume-food-run');

    expect(completed.row.status).toBe('completed');
    expect(summary.operation_ids).toEqual(['op-plan-green-dal', 'op-reserve-yogurt', 'op-buy-spinach']);
    expect(summary.record_ids).toEqual(['meal-green-dal', 'pantry-yogurt', 'shopping-spinach']);
    expect(new Set(summary.operation_ids).size).toBe(summary.operation_ids.length);
  });

  it('keeps completed workflows immutable when cancel or resume is requested later', async () => {
    const db = new MemoryDb();
    await startWorkflowRun({
      db: db as any,
      id: 'done-food-run',
      domain: 'food',
      workflowId: 'single-step',
      steps: [{ id: 'done', title: 'Done' }],
    });
    await recordWorkflowStep({
      db: db as any,
      runId: 'done-food-run',
      stepId: 'done',
      status: 'completed',
      receipt: { operation_ids: ['op-done'] },
    });

    const cancelled = await cancelWorkflowRun({
      db: db as any,
      runId: 'done-food-run',
      reason: 'Too late.',
    });
    const resumed = await resumeWorkflowRun({ db: db as any, runId: 'done-food-run' });

    expect(cancelled.row.status).toBe('completed');
    expect(resumed.row.status).toBe('completed');
    expect(resumed.checkpoint.steps[0]?.status).toBe('completed');
  });
});
