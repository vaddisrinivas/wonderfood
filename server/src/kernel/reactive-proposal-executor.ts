import { createActionEvent, findActionByIdempotencyKey } from '../mcp/state';
import { previewReactiveProposalCommand } from './reactive-proposal-command';
import type { ReactiveOutboxExecutionResult, ReactiveOutboxItem } from './reactive-outbox';

export type ReactiveProposalExecutionReceipt = Readonly<{
  actionId: string;
  idempotencyKey: string;
  replayed: boolean;
  status: 'queued' | 'running' | 'completed' | 'failed' | 'cancelled';
}>;

export type ReactiveProposalExecutionResult = ReactiveOutboxExecutionResult & Readonly<{
  receipt?: ReactiveProposalExecutionReceipt;
}>;

export function executeReactiveProposal(
  item: ReactiveOutboxItem,
  input: { actor?: string } = {},
): ReactiveProposalExecutionResult {
  const envelope = item.proposal.envelope;
  const actor = input.actor?.trim() || 'reactive-runtime';
  const actionId = `reactive-proposal:${item.proposalId.replace(/[^A-Za-z0-9_.:-]/g, '_')}`;
  if (envelope.schemaVersion !== 'wonder.operation-proposal.v1') {
    return { ok: false, error: 'proposal_envelope_invalid' };
  }
  if (envelope.proposalId !== item.proposalId || envelope.operation !== item.proposal.operation) {
    return { ok: false, error: 'proposal_envelope_mismatch' };
  }
  if (!envelope.authorization.allowed || !envelope.dryRun.ok) {
    return { ok: false, error: 'proposal_policy_blocked' };
  }
  const commandPreview = previewReactiveProposalCommand({
    operationTemplate: envelope.operationTemplate,
    domain: item.domain,
    idempotencyKey: envelope.idempotencyKey,
    actionId,
    actor,
  });

  const existing = findActionByIdempotencyKey(envelope.idempotencyKey);
  if (existing) {
    return {
      ok: true,
      receipt: {
        actionId: existing.id,
        idempotencyKey: envelope.idempotencyKey,
        replayed: true,
        status: existing.status,
      },
    };
  }

  const action = createActionEvent({
    id: actionId,
    actor,
    domain: item.domain,
    tool: envelope.operation,
    risk: envelope.authorization.risk === 'restricted' ? 'sensitive' : envelope.authorization.risk,
    recordIds: [],
    idempotencyKey: envelope.idempotencyKey,
    command: JSON.stringify({
      kind: 'reactive_proposal',
      proposalId: item.proposalId,
      operation: envelope.operation,
      operationTemplate: envelope.operationTemplate,
      ruleId: envelope.ruleId,
      eventId: envelope.eventId,
      review: envelope.review,
      authorization: envelope.authorization,
      dryRun: envelope.dryRun,
      commandPreview,
    }),
    before: null,
    after: { proposal: envelope, policy: envelope.authorization, dryRun: envelope.dryRun, commandPreview },
    undoPayload: null,
    status: 'queued',
    operationId: `proposal:${item.proposalId}:operation`,
    causeId: envelope.causeId,
  });

  return {
    ok: true,
    receipt: {
      actionId: action.id,
      idempotencyKey: envelope.idempotencyKey,
      replayed: false,
      status: action.status,
    },
  };
}
