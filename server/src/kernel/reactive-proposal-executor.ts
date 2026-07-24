import { createHash } from 'node:crypto';

import {
  archiveRecordWithAction,
  attachActionVerification,
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

export type ReactiveProposalApprovalReceipt = Readonly<{
  schemaVersion: 'wonder.reactive-proposal-approval.v1';
  approver: string;
  authority: string;
  proposalId: string;
  proposalHash: string;
  operationTemplateHash: string;
  approvedAt: string;
  expiresAt?: string;
  revoked?: boolean;
}>;

export function executeReactiveProposal(
  item: ReactiveOutboxItem,
  input: { actor?: string; approval?: ReactiveProposalApprovalReceipt } = {},
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
    const verification = isVerificationReceipt(existing.verification_json) ? existing.verification_json : undefined;
    if (isVerificationBoundToProposal(verification, item, existing.operation_id)) {
      return {
        ok: true,
        receipt: {
          actionId: existing.id,
          idempotencyKey: envelope.idempotencyKey,
          replayed: true,
          status: existing.status,
          verification,
        },
      };
    }
    const refreshed = verifyForAction({ item, actionId: existing.id, operationId: existing.operation_id });
    if (!refreshed.ok) {
      return { ok: false, error: refreshed.reason };
    }
    const verifiedAction = attachActionVerification(existing.id, refreshed) ?? existing;
    return {
      ok: true,
      receipt: {
        actionId: verifiedAction.id,
        idempotencyKey: envelope.idempotencyKey,
        replayed: true,
        status: verifiedAction.status,
        verification: refreshed,
      },
    };
  }

  const approval = input.approval ? validateApproval(input.approval, item) : { ok: false as const, error: 'proposal_approval_required' };
  if (input.approval && !approval.ok) return { ok: false, error: approval.error };

  if (commandPreview.ok && (!envelope.review.required || approval.ok)) {
    const targetProvider = envelope.authorization.providerAuthority.targetProvider;
    if (!isLocalWriteProvider(targetProvider)) {
      return { ok: false, error: 'provider_writeback_verification_missing' };
    }
    const write = executeApprovedCommand({
      item,
      actionId,
      actor,
      commandPreview,
      approval: input.approval,
    });
    if (!write.verification?.ok) {
      return { ok: false, error: write.verification?.reason ?? 'proposal_verification_failed' };
    }
    if (write.action.status !== 'completed') {
      return { ok: false, error: write.action.command || 'proposal_execution_failed' };
    }
    const verifiedAction = attachActionVerification(write.action.id, write.verification) ?? write.action;
    return {
      ok: true,
      receipt: {
        actionId: verifiedAction.id,
        idempotencyKey: envelope.idempotencyKey,
        replayed: write.replayed,
        status: verifiedAction.status,
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

function isLocalWriteProvider(provider: string): boolean {
  return provider === 'user' || provider === 'sqlite' || provider === 'local_sqlite';
}

function executeApprovedCommand(input: {
  item: ReactiveOutboxItem;
  actionId: string;
  actor: string;
  commandPreview: Extract<ReturnType<typeof previewReactiveProposalCommand>, { ok: true }>;
  approval?: ReactiveProposalApprovalReceipt;
}): ReturnType<typeof createRecordWithAction> & { verification?: ReactiveProposalVerificationReceipt } {
  const envelope = input.item.proposal.envelope;
  const template = envelope.operationTemplate;
  const command = JSON.stringify({
    kind: 'reactive_proposal_execute',
    proposalId: input.item.proposalId,
    operationTemplate: template,
    commandPreview: input.commandPreview,
    authorization: envelope.authorization,
    approval: input.approval ?? null,
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
    return { ...write, verification: verifyForAction({ item: input.item, actionId: write.action.id, operationId: write.action.operation_id, record: write.record }) };
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
    return { ...write, verification: verifyForAction({ item: input.item, actionId: write.action.id, operationId: write.action.operation_id, beforeRevision }) };
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
    return { ...failed, verification: verifyForAction({ item: input.item, actionId: failed.action.id, operationId: failed.action.operation_id }) };
  }
  const existing = findRecord(template.recordId);
  const beforeRevision = existing?.revision;
  const write = archiveRecordWithAction({
    actionId: input.actionId,
    actor: input.actor,
    domain: String(input.commandPreview.args.domain),
    tool: input.commandPreview.tool,
    risk,
    command,
    id: template.recordId,
    expectedRevision: template.expectedRevision,
    idempotencyKey: envelope.idempotencyKey,
    operationId: `proposal:${input.item.proposalId}:operation`,
    causeId: envelope.causeId,
  });
  return { ...write, verification: verifyForAction({ item: input.item, actionId: write.action.id, operationId: write.action.operation_id, beforeRevision }) };
}

function verifyForAction(input: {
  item: ReactiveOutboxItem;
  actionId: string;
  operationId: string;
  beforeRevision?: number;
  record?: ReturnType<typeof findRecord>;
}): ReactiveProposalVerificationReceipt {
  const template = input.item.proposal.envelope.operationTemplate;
  const record = input.record ?? (template.kind === 'custom' ? null
    : template.kind === 'create_record' ? findRecord(template.recordId ?? '')
    : findRecord(template.recordId));
  return verifyReactiveProposalPostcondition({
    operationTemplate: template,
    record,
    beforeRevision: input.beforeRevision,
    actionId: input.actionId,
    operationId: input.operationId,
    proposalId: input.item.proposalId,
    operationTemplateHash: hashValue(template),
  });
}

function validateApproval(approval: ReactiveProposalApprovalReceipt | undefined, item: ReactiveOutboxItem): { ok: true } | { ok: false; error: string } {
  if (!approval) return { ok: false, error: 'proposal_approval_required' };
  if (approval.schemaVersion !== 'wonder.reactive-proposal-approval.v1') return { ok: false, error: 'proposal_approval_invalid' };
  if (approval.revoked === true) return { ok: false, error: 'proposal_approval_revoked' };
  if (approval.expiresAt && Date.parse(approval.expiresAt) <= Date.now()) return { ok: false, error: 'proposal_approval_expired' };
  if (!approval.approver.trim() || !approval.authority.trim()) return { ok: false, error: 'proposal_approval_invalid' };
  if (approval.proposalId !== item.proposalId) return { ok: false, error: 'proposal_approval_mismatch' };
  if (approval.proposalHash !== hashValue(item.proposal.envelope)) return { ok: false, error: 'proposal_approval_mismatch' };
  if (approval.operationTemplateHash !== hashValue(item.proposal.envelope.operationTemplate)) return { ok: false, error: 'proposal_approval_mismatch' };
  return { ok: true };
}

function isVerificationBoundToProposal(
  verification: ReactiveProposalVerificationReceipt | undefined,
  item: ReactiveOutboxItem,
  operationId: string,
): verification is ReactiveProposalVerificationReceipt {
  return Boolean(verification?.ok)
    && verification?.proposalId === item.proposalId
    && verification?.operationId === operationId
    && verification?.operationTemplateHash === hashValue(item.proposal.envelope.operationTemplate);
}

function isVerificationReceipt(value: unknown): value is ReactiveProposalVerificationReceipt {
  return Boolean(value)
    && typeof value === 'object'
    && !Array.isArray(value)
    && (value as { verifierVersion?: unknown }).verifierVersion === 'wonder.reactive-proposal-verifier.v1'
    && typeof (value as { ok?: unknown }).ok === 'boolean'
    && typeof (value as { actionId?: unknown }).actionId === 'string'
    && typeof (value as { operationId?: unknown }).operationId === 'string'
    && typeof (value as { proposalId?: unknown }).proposalId === 'string'
    && typeof (value as { operationTemplateHash?: unknown }).operationTemplateHash === 'string';
}

function hashValue(value: unknown): string {
  return `sha256:${createHash('sha256').update(stableJson(value)).digest('hex')}`;
}

function stableJson(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value as Record<string, unknown>)
      .filter((key) => (value as Record<string, unknown>)[key] !== undefined)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stableJson((value as Record<string, unknown>)[key])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value) ?? 'null';
}
