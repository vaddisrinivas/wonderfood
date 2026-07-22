import { createActionReceipt, ActionReceipt } from '../actions';

export type AgentStep = {
  id: string;
  action: string;
  required: boolean;
};

export type ActionStatus = ActionReceipt['status'];

export async function executeCommand(input: {
  actionId: string;
  actor: string;
  domain: string;
  tool: string;
  record_ids: string[];
  step?: AgentStep;
}): Promise<{
  state: ActionStatus;
  receipt: ReturnType<typeof createActionReceipt>;
  step: AgentStep | undefined;
}> {
  const receipt = createActionReceipt({
    id: input.actionId,
    actor: input.actor,
    domain: input.domain,
    tool: input.tool,
    record_ids: input.record_ids,
  });

  return {
    receipt,
    state: 'queued',
    step: input.step,
  };
}
