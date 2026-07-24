import assert from 'node:assert/strict';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { drainReactiveOutbox } from '../src/kernel/reactive-outbox';
import { createReactiveOutboxStore, enqueueReactiveProposals } from '../src/kernel/reactive-outbox';
import type { ReactiveCycleResult } from '../src/kernel/reactive-cycle';
import type { OperationCommitEvent } from '../src/kernel/operation-observer';
import { createOperationProposalIdempotencyKey } from '../src/kernel/rules';

const dir = mkdtempSync(join(tmpdir(), 'wonderfood-reactive-proposal-'));
process.env.LIFEOS_MCP_STATE_PATH = join(dir, 'mcp-runtime.json');
const { executeReactiveProposal } = await import('../src/kernel/reactive-proposal-executor');
const { createRecord, findActionByIdempotencyKey, findRecord, getActionEvent } = await import('../src/mcp/state');
const proposalEvent = { kind: 'query_transition' as const, id: 'review:enter', queryId: 'review', transition: 'enter' as const };
const operationTemplate = { kind: 'custom' as const, tool: 'request_review' };
const authorization = {
  policyId: 'wonder.reactive-proposal-policy' as const,
  policyVersion: 'v1' as const,
  allowed: true,
  risk: 'standard' as const,
  reviewRequired: true,
  requiredCapability: 'reactive:propose:custom',
  capabilityPresent: false,
  providerAuthority: {
    targetProvider: 'user',
    authorityProvider: 'notion',
    allowed: true,
    requiredCapability: null,
    capabilityPresent: true,
    reason: 'provider_authority_ok',
  },
  reason: 'suggest_mode_requires_review',
};
const dryRun = {
  ok: true,
  effect: 'queue_review_action' as const,
  executable: false,
  reason: 'proposal_can_be_queued',
};
const proposalEvidence = {
  queryId: 'review',
  transition: 'enter' as const,
  beforeHash: 'before-hash',
  afterHash: 'after-hash',
  querySpecHash: 'sha256:query',
  packageHash: 'sha256:package',
  evaluatorVersion: 'wonder.query-evaluator.v1',
};
const proposalIdempotencyKey = createOperationProposalIdempotencyKey({
  packageId: 'food',
  packageVersion: '1.0.0',
  ruleId: 'review-rule',
  event: proposalEvent,
  causeId: 'source-cause',
  operationTemplate,
  evidence: proposalEvidence,
});

const event: OperationCommitEvent = {
  actionId: 'source-action',
  operationId: 'source-operation',
  causeId: 'source-cause',
  domain: 'food',
  recordId: 'source-record',
  before: null,
  after: { id: 'source-record' },
};
const cycle: ReactiveCycleResult = {
  cycleId: 'proposal-cycle',
  transitions: [],
  queryHashes: { review: { before: 'before-hash', after: 'after-hash' } },
  proposals: [{
    id: 'proposal-ledger',
    eventId: 'review:enter',
    event: proposalEvent,
    ruleId: 'review-rule',
    operation: 'request_review',
    operationTemplate,
    mode: 'suggest',
    causeId: 'source-cause',
    packageVersion: '1.0.0',
    depth: 0,
    envelope: {
      schemaVersion: 'wonder.operation-proposal.v1',
      proposalId: 'proposal-ledger',
      operation: 'request_review',
      operationTemplate,
      mode: 'suggest',
      ruleId: 'review-rule',
      packageId: 'food',
      packageVersion: '1.0.0',
      eventId: 'review:enter',
      event: proposalEvent,
      causeId: 'source-cause',
      depth: 0,
      idempotencyKey: proposalIdempotencyKey,
      review: { required: true, reason: 'suggest_mode', policyId: authorization.policyId, policyVersion: authorization.policyVersion },
      authorization,
      dryRun,
      evidence: proposalEvidence,
    },
  }],
};

const queued = enqueueReactiveProposals(createReactiveOutboxStore(), {
  cycle,
  event,
  proposalIds: ['proposal-ledger'],
  now: '2026-07-23T00:00:00.000Z',
});
assert.equal(queued.items['proposal-ledger'].domain, 'food');

const drained = await drainReactiveOutbox({
  store: queued,
  now: '2026-07-23T00:00:00.000Z',
  executeProposal: (item) => executeReactiveProposal(item, { actor: 'test-reactive' }),
});
assert.deepEqual(drained.acked, ['proposal-ledger']);

const action = findActionByIdempotencyKey(proposalIdempotencyKey);
assert.ok(action);
assert.equal(action.id, 'reactive-proposal:proposal-ledger');
assert.equal(action.actor, 'test-reactive');
assert.equal(action.domain, 'food');
assert.equal(action.tool, 'request_review');
assert.equal(action.status, 'queued');
assert.equal(action.cause_id, 'source-cause');
assert.deepEqual(action.record_ids, []);
assert.equal((action.after_json as { proposal?: { proposalId?: string } }).proposal?.proposalId, 'proposal-ledger');
assert.equal((action.after_json as { policy?: { policyId?: string } }).policy?.policyId, authorization.policyId);
assert.equal((action.after_json as { dryRun?: { ok?: boolean } }).dryRun?.ok, true);
assert.deepEqual((action.after_json as { commandPreview?: { ok?: boolean; reason?: string } }).commandPreview, {
  ok: false,
  reason: 'custom_operation_requires_review',
  recordIds: [],
});
assert.equal(getActionEvent(action.id)?.id, action.id);

const blocked = executeReactiveProposal({
  ...queued.items['proposal-ledger'],
  proposal: {
    ...queued.items['proposal-ledger'].proposal,
    envelope: {
      ...queued.items['proposal-ledger'].proposal.envelope,
      authorization: { ...authorization, allowed: false, reason: 'operation_template_restricted' },
      dryRun: { ...dryRun, ok: false, reason: 'operation_template_restricted' },
    },
  },
});
assert.equal(blocked.ok, false);
assert.equal(blocked.error, 'proposal_policy_blocked');

const replay = executeReactiveProposal(queued.items['proposal-ledger'], { actor: 'ignored' });
assert.equal(replay.ok, true);
assert.equal(replay.receipt?.replayed, false);
assert.equal(replay.receipt?.actionId, action.id);

const autoEvent = { kind: 'operation' as const, id: 'auto-source-operation' };
const autoTemplate = { kind: 'create_record' as const, collection: 'recipe', recordId: 'auto-created-record', properties: { title: 'Auto created', status: 'queued' } };
const autoAuthorization = {
  policyId: 'wonder.reactive-proposal-policy' as const,
  policyVersion: 'v1' as const,
  allowed: true,
  risk: 'low' as const,
  reviewRequired: false,
  requiredCapability: 'reactive:auto:create_record',
  capabilityPresent: true,
  providerAuthority: {
    targetProvider: 'user',
    authorityProvider: 'notion',
    allowed: true,
    requiredCapability: null,
    capabilityPresent: true,
    reason: 'provider_authority_ok',
  },
  reason: 'automatic_policy_authorized',
};
const autoDryRun = {
  ok: true,
  effect: 'queue_review_action' as const,
  executable: true,
  reason: 'proposal_can_be_queued',
};
const autoKey = createOperationProposalIdempotencyKey({
  packageId: 'food',
  packageVersion: '1.0.0',
  ruleId: 'auto-create-rule',
  event: autoEvent,
  causeId: 'auto-cause',
  operationTemplate: autoTemplate,
});
const autoItem = {
  ...queued.items['proposal-ledger'],
  proposalId: 'auto-create-proposal',
  eventId: 'auto-source-operation',
  causeId: 'auto-cause',
  proposal: {
    ...queued.items['proposal-ledger'].proposal,
    id: 'auto-create-proposal',
    eventId: 'auto-source-operation',
    event: autoEvent,
    ruleId: 'auto-create-rule',
    operation: 'create_record',
    operationTemplate: autoTemplate,
    mode: 'automatic' as const,
    causeId: 'auto-cause',
    envelope: {
      ...queued.items['proposal-ledger'].proposal.envelope,
      proposalId: 'auto-create-proposal',
      operation: 'create_record',
      operationTemplate: autoTemplate,
      mode: 'automatic' as const,
      ruleId: 'auto-create-rule',
      eventId: 'auto-source-operation',
      event: autoEvent,
      causeId: 'auto-cause',
      idempotencyKey: autoKey,
      review: { required: false, reason: 'policy_authorized' as const, policyId: autoAuthorization.policyId, policyVersion: autoAuthorization.policyVersion },
      authorization: autoAuthorization,
      dryRun: autoDryRun,
      evidence: {},
    },
  },
};
const executed = executeReactiveProposal(autoItem, { actor: 'test-reactive' });
assert.equal(executed.ok, true);
assert.equal(executed.receipt?.status, 'completed');
assert.equal(executed.receipt?.verification?.reason, 'canonical_create_verified');
assert.equal(getActionEvent(executed.receipt?.actionId ?? '')?.verification_json && (getActionEvent(executed.receipt?.actionId ?? '')?.verification_json as { reason?: string }).reason, 'canonical_create_verified');
assert.equal(findRecord('auto-created-record')?.properties.status, 'queued');
const executedReplay = executeReactiveProposal(autoItem, { actor: 'ignored' });
assert.equal(executedReplay.ok, true);
assert.equal(executedReplay.receipt?.replayed, true);
assert.equal(executedReplay.receipt?.verification?.reason, 'canonical_create_verified');

const updateSeed = createRecord({
  id: 'auto-update-record',
  domain: 'food',
  collection: 'recipe',
  title: 'Auto update seed',
  properties: { status: 'open' },
  relations: [],
  source: { provider: 'user', external_id: 'auto-update-record', url: null, observed_at: new Date().toISOString(), content_hash: null },
  archived_at: null,
}, { persist: false });
const updateEvent = { kind: 'operation' as const, id: 'auto-update-operation' };
const updateTemplate = { kind: 'update_record' as const, collection: 'recipe', recordId: 'auto-update-record', expectedRevision: updateSeed.revision, changes: { status: 'done' } };
const updateAuthorization = {
  ...autoAuthorization,
  risk: 'standard' as const,
  reviewRequired: true,
  requiredCapability: 'reactive:propose:update_record',
  capabilityPresent: false,
  reason: 'automatic_requires_policy_or_review',
};
const updateKey = createOperationProposalIdempotencyKey({
  packageId: 'food',
  packageVersion: '1.0.0',
  ruleId: 'auto-update-rule',
  event: updateEvent,
  causeId: 'update-cause',
  operationTemplate: updateTemplate,
});
const updateItem = {
  ...autoItem,
  proposalId: 'auto-update-proposal',
  eventId: 'auto-update-operation',
  causeId: 'update-cause',
  proposal: {
    ...autoItem.proposal,
    id: 'auto-update-proposal',
    eventId: 'auto-update-operation',
    event: updateEvent,
    ruleId: 'auto-update-rule',
    operation: 'update_record',
    operationTemplate: updateTemplate,
    causeId: 'update-cause',
    envelope: {
      ...autoItem.proposal.envelope,
      proposalId: 'auto-update-proposal',
      operation: 'update_record',
      operationTemplate: updateTemplate,
      ruleId: 'auto-update-rule',
      eventId: 'auto-update-operation',
      event: updateEvent,
      causeId: 'update-cause',
      idempotencyKey: updateKey,
      review: { required: true, reason: 'policy_required' as const, policyId: updateAuthorization.policyId, policyVersion: updateAuthorization.policyVersion },
      authorization: updateAuthorization,
    },
  },
};
assert.equal(executeReactiveProposal(updateItem).ok, true, 'review-required update should queue without approval');
assert.equal(findRecord('auto-update-record')?.properties.status, 'open');
const approvedUpdate = executeReactiveProposal(updateItem, { actor: 'approver', approved: true });
assert.equal(approvedUpdate.ok, true);
assert.equal(approvedUpdate.receipt?.status, 'completed');
assert.equal(approvedUpdate.receipt?.verification?.reason, 'canonical_update_verified');
assert.equal((getActionEvent(approvedUpdate.receipt?.actionId ?? '')?.verification_json as { reason?: string }).reason, 'canonical_update_verified');
assert.equal(findRecord('auto-update-record')?.properties.status, 'done');

const archiveSeed = createRecord({
  id: 'auto-archive-record',
  domain: 'food',
  collection: 'recipe',
  title: 'Auto archive seed',
  properties: { status: 'stale' },
  relations: [],
  source: { provider: 'user', external_id: 'auto-archive-record', url: null, observed_at: new Date().toISOString(), content_hash: null },
  archived_at: null,
}, { persist: false });
const archiveEvent = { kind: 'operation' as const, id: 'auto-archive-operation' };
const archiveTemplate = { kind: 'archive_record' as const, collection: 'recipe', recordId: archiveSeed.id };
const archiveAuthorization = {
  ...autoAuthorization,
  risk: 'sensitive' as const,
  reviewRequired: true,
  requiredCapability: 'reactive:propose:archive_record',
  capabilityPresent: false,
  reason: 'automatic_requires_policy_or_review',
};
const archiveKey = createOperationProposalIdempotencyKey({
  packageId: 'food',
  packageVersion: '1.0.0',
  ruleId: 'auto-archive-rule',
  event: archiveEvent,
  causeId: 'archive-cause',
  operationTemplate: archiveTemplate,
});
const archiveItem = {
  ...updateItem,
  proposalId: 'auto-archive-proposal',
  eventId: 'auto-archive-operation',
  causeId: 'archive-cause',
  proposal: {
    ...updateItem.proposal,
    id: 'auto-archive-proposal',
    eventId: 'auto-archive-operation',
    event: archiveEvent,
    ruleId: 'auto-archive-rule',
    operation: 'archive_record',
    operationTemplate: archiveTemplate,
    causeId: 'archive-cause',
    envelope: {
      ...updateItem.proposal.envelope,
      proposalId: 'auto-archive-proposal',
      operation: 'archive_record',
      operationTemplate: archiveTemplate,
      ruleId: 'auto-archive-rule',
      eventId: 'auto-archive-operation',
      event: archiveEvent,
      causeId: 'archive-cause',
      idempotencyKey: archiveKey,
      authorization: archiveAuthorization,
    },
  },
};
assert.equal(executeReactiveProposal(archiveItem).ok, true, 'review-required archive should queue without approval');
assert.equal(findRecord('auto-archive-record')?.archived_at, null);
const approvedArchive = executeReactiveProposal(archiveItem, { actor: 'approver', approved: true });
assert.equal(approvedArchive.ok, true);
assert.equal(approvedArchive.receipt?.status, 'completed');
assert.equal(approvedArchive.receipt?.verification?.reason, 'canonical_archive_verified');
assert.equal((getActionEvent(approvedArchive.receipt?.actionId ?? '')?.verification_json as { reason?: string }).reason, 'canonical_archive_verified');
assert.ok(findRecord('auto-archive-record')?.archived_at);

console.log('reactive-proposal-executor: passed');
