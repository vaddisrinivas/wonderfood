import { createActionReceipt } from '../actions';

export async function executeCommand(input: {
  actionId: string;
  actor: string;
  domain: string;
  tool: string;
  record_ids: string[];
}) {
  const receipt = createActionReceipt({
    id: input.actionId,
    actor: input.actor,
    domain: input.domain,
    tool: input.tool,
    record_ids: input.record_ids,
  });

  return {
    receipt,
    state: 'accepted',
  };
}
