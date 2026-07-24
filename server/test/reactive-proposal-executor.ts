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
const { findActionByIdempotencyKey, getActionEvent } = await import('../src/mcp/state');
const proposalEvent = { kind: 'query_transition' as const, id: 'review:enter', queryId: 'review', transition: 'enter' as const };
const operationTemplate = { kind: 'custom' as const, tool: 'request_review' };
const proposalEvidence = { queryId: 'review', transition: 'enter' as const, beforeHash: 'before-hash', afterHash: 'after-hash' };
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
      review: { required: true, reason: 'suggest_mode' },
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
assert.equal(getActionEvent(action.id)?.id, action.id);

const replay = executeReactiveProposal(queued.items['proposal-ledger'], { actor: 'ignored' });
assert.equal(replay.ok, true);
assert.equal(replay.receipt?.replayed, true);
assert.equal(replay.receipt?.actionId, action.id);

console.log('reactive-proposal-executor: passed');
