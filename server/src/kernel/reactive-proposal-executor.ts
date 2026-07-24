import {
  archiveRecordWithAction,
  createActionEvent,
  createRecordWithAction,
  findActionByIdempotencyKey,
  findRecord,
  updateRecordWithAction,
} from '../mcp/state';
import { previewReactiveProposalCommand } from './reactive-proposal-command';
import { verifyReactiveProposalPostcondition, type ReactiveProposalVerificationReceipt } from './reactive-proposal-verification';
import type { ReactiveOutboxExecutionResult, ReactiveOutboxItem } from './reactive-outbox';

export type ReactiveProposalExecutionReceipt = Readonly<{
  actionId: string;
  idempotencyKey: string;
  replayed: boolean;
  status: 'queued' | 'running' | 'completed' | 'failed' | 'cancelled';
  verification?: ReactiveProposalVerificationReceipt;
}>;

export type ReactiveProposalExecutionResult = ReactiveOutboxExecutionResult & Readonly<{
  receipt?: ReactiveProposalExecutionReceipt;
}>;

export function executeReactiveProposal(
  item: ReactiveOutboxItem,
  input: { actor?: string; approved?: boolean } = {},
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
  if (existing?.status === 'completed') {
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

  if (commandPreview.ok && (!envelope.review.required || input.approved === true)) {
    const write = executeApprovedCommand({
      item,
      actionId,
      actor,
      commandPreview,
    });
    if (write.action.status !== 'completed') {
      return { ok: false, error: write.action.command || 'proposal_execution_failed' };
    }
    if (!write.verification?.ok) {
      return { ok: false, error: write.verification?.reason ?? 'proposal_verification_failed' };
    }
    return {
      ok: true,
      receipt: {
        actionId: write.action.id,
        idempotencyKey: envelope.idempotencyKey,
        replayed: write.replayed,
        status: write.action.status,
        verification: write.verification,
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

function executeApprovedCommand(input: {
  item: ReactiveOutboxItem;
  actionId: string;
  actor: string;
  commandPreview: Extract<ReturnType<typeof previewReactiveProposalCommand>, { ok: true }>;
}): ReturnType<typeof createRecordWithAction> & { verification?: ReactiveProposalVerificationReceipt } {
  const envelope = input.item.proposal.envelope;
  const template = envelope.operationTemplate;
  const command = JSON.stringify({
    kind: 'reactive_proposal_execute',
    proposalId: input.item.proposalId,
    operationTemplate: template,
    commandPreview: input.commandPreview,
    authorization: envelope.authorization,
    approved: envelope.review.required,
  });
  const risk = envelope.authorization.risk === 'restricted' ? 'sensitive' : envelope.authorization.risk;
  if (template.kind === 'create_record') {
    const recordId = String(input.commandPreview.args.id);
    const write = createRecordWithAction({
      actionId: input.actionId,
      actor: input.actor,
      domain: String(input.commandPreview.args.domain),
      tool: input.commandPreview.tool,
      risk,
      command,
      record: {
        id: recordId,
        domain: String(input.commandPreview.args.domain),
        collection: String(input.commandPreview.args.collection),
        title: typeof template.properties?.title === 'string' ? template.properties.title : recordId,
        properties: template.properties ?? {},
        relations: [],
        source: {
          provider: 'user',
          external_id: recordId,
          url: null,
          observed_at: new Date().toISOString(),
          content_hash: envelope.evidence.afterHash ?? null,
        },
        archived_at: null,
      },
      idempotencyKey: envelope.idempotencyKey,
      before: { proposal: envelope, commandPreview: input.commandPreview },
      undoPayload: { operation: 'delete_record', record_id: recordId },
    });
    return { ...write, verification: verifyReactiveProposalPostcondition({ operationTemplate: template, record: findRecord(recordId) }) };
  }
  if (template.kind === 'update_record') {
    const existing = findRecord(template.recordId);
    const beforeRevision = existing?.revision;
    const write = updateRecordWithAction({
      actionId: input.actionId,
      actor: input.actor,
      domain: String(input.commandPreview.args.domain),
      tool: input.commandPreview.tool,
      risk,
      command,
      id: template.recordId,
      patch: {
        properties: {
          ...(existing?.properties ?? {}),
          ...template.changes,
        },
      },
      expectedRevision: template.expectedRevision,
      idempotencyKey: envelope.idempotencyKey,
      operationId: `proposal:${input.item.proposalId}:operation`,
      causeId: envelope.causeId,
    });
    return { ...write, verification: verifyReactiveProposalPostcondition({ operationTemplate: template, record: findRecord(template.recordId), beforeRevision }) };
  }
  if (template.kind !== 'archive_record') {
    const failed = {
      action: createActionEvent({
        id: input.actionId,
        actor: input.actor,
        domain: input.item.domain,
        tool: envelope.operation,
        risk,
        recordIds: [],
        idempotencyKey: envelope.idempotencyKey,
        command: 'proposal_execution_template_unsupported',
        before: { proposal: envelope },
        after: null,
        undoPayload: null,
        status: 'failed',
        operationId: `proposal:${input.item.proposalId}:operation`,
        causeId: envelope.causeId,
      }),
      replayed: false,
    };
    return { ...failed, verification: verifyReactiveProposalPostcondition({ operationTemplate: template, record: null }) };
  }
  const write = archiveRecordWithAction({
    actionId: input.actionId,
    actor: input.actor,
    domain: String(input.commandPreview.args.domain),
    tool: input.commandPreview.tool,
    risk,
    command,
    id: template.recordId,
    idempotencyKey: envelope.idempotencyKey,
  });
  return { ...write, verification: verifyReactiveProposalPostcondition({ operationTemplate: template, record: findRecord(template.recordId) }) };
}
