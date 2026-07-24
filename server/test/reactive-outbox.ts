import assert from 'node:assert/strict';

import {
  createReactiveOutboxStore,
  enqueueReactiveProposals,
  listRunnableReactiveOutboxItems,
  markReactiveOutboxAcked,
  drainReactiveOutbox,
  markReactiveOutboxFailed,
  markReactiveOutboxRunning,
  parseReactiveOutboxStore,
  serializeReactiveOutboxStore,
} from '../src/kernel/reactive-outbox';
import type { ReactiveCycleResult } from '../src/kernel/reactive-cycle';
import type { OperationCommitEvent } from '../src/kernel/operation-observer';

const now = '2026-07-23T00:00:00.000Z';
const event: OperationCommitEvent = {
  actionId: 'action-a',
  operationId: 'operation-a',
  causeId: 'cause-a',
  domain: 'food',
  recordId: 'record-a',
  before: null,
  after: { id: 'record-a' },
};
const cycle: ReactiveCycleResult = {
  cycleId: 'cycle-a',
  transitions: [],
  queryHashes: { open: { before: 'before-hash', after: 'after-hash' } },
  proposals: [{
    id: 'proposal-a',
    eventId: 'open:enter',
    ruleId: 'rule-a',
    operation: 'request_review',
    mode: 'suggest',
    causeId: 'cause-a',
    packageVersion: '1.0.0',
    depth: 0,
    envelope: {
      schemaVersion: 'wonder.operation-proposal.v1',
      proposalId: 'proposal-a',
      operation: 'request_review',
      mode: 'suggest',
      ruleId: 'rule-a',
      packageId: 'package-a',
      packageVersion: '1.0.0',
      eventId: 'open:enter',
      causeId: 'cause-a',
      depth: 0,
      idempotencyKey: 'proposal-a:idempotency',
      review: { required: true, reason: 'suggest_mode' },
      evidence: { queryId: 'open', transition: 'enter', beforeHash: 'before-hash', afterHash: 'after-hash' },
    },
  }],
};

const empty = createReactiveOutboxStore();
const queued = enqueueReactiveProposals(empty, { cycle, event, proposalIds: ['proposal-a'], now });
assert.equal(Object.keys(queued.items).length, 1);
assert.equal(queued.items['proposal-a'].status, 'pending');
assert.equal(queued.items['proposal-a'].attempts, 0);
assert.equal(queued.items['proposal-a'].proposal.envelope.schemaVersion, 'wonder.operation-proposal.v1');
assert.equal(queued.items['proposal-a'].proposal.envelope.idempotencyKey, 'proposal-a:idempotency');
assert.deepEqual(listRunnableReactiveOutboxItems(queued, now).map((item) => item.proposalId), ['proposal-a']);

const replay = enqueueReactiveProposals(queued, { cycle, event, proposalIds: ['proposal-a'], now });
assert.deepEqual(replay, queued, 'replaying the same proposal must not duplicate outbox work');

const running = markReactiveOutboxRunning(queued, 'proposal-a', '2026-07-23T00:00:01.000Z');
assert.equal(running.items['proposal-a'].status, 'running');
assert.deepEqual(listRunnableReactiveOutboxItems(running, '2026-07-23T00:00:01.000Z'), []);

const failed = markReactiveOutboxFailed(running, 'proposal-a', {
  error: 'temporary outage',
  now: '2026-07-23T00:00:02.000Z',
  retryDelayMs: 1_000,
});
assert.equal(failed.items['proposal-a'].status, 'pending');
assert.equal(failed.items['proposal-a'].attempts, 1);
assert.equal(listRunnableReactiveOutboxItems(failed, '2026-07-23T00:00:02.500Z').length, 0);
assert.equal(listRunnableReactiveOutboxItems(failed, '2026-07-23T00:00:03.000Z').length, 1);

const acked = markReactiveOutboxAcked(failed, 'proposal-a', '2026-07-23T00:00:04.000Z');
assert.equal(acked.items['proposal-a'].status, 'acked');
assert.deepEqual(listRunnableReactiveOutboxItems(acked, '2026-07-23T00:00:05.000Z'), []);

const restored = parseReactiveOutboxStore(serializeReactiveOutboxStore(failed));
assert.deepEqual(restored, failed, 'outbox must survive JSON persistence');
assert.throws(
  () => parseReactiveOutboxStore(JSON.stringify({
    ...failed,
    items: {
      ...failed.items,
      'proposal-a': {
        ...failed.items['proposal-a'],
        proposal: { ...failed.items['proposal-a'].proposal, envelope: undefined },
      },
    },
  })),
  /missing its proposal envelope/,
);
assert.throws(
  () => enqueueReactiveProposals(empty, { cycle, event, proposalIds: ['missing-proposal'], now }),
  /missing from cycle/,
);

const drainChanges: string[] = [];
const drained = await drainReactiveOutbox({
  store: queued,
  now,
  retryDelayMs: 1_000,
  executeProposal: (item) => item.proposal.operation === 'request_review'
    ? { ok: true }
    : { ok: false, error: 'unexpected operation' },
  onStoreChange: (store) => drainChanges.push(store.items['proposal-a'].status),
});
assert.deepEqual(drained.attempted, ['proposal-a']);
assert.deepEqual(drained.acked, ['proposal-a']);
assert.equal(drained.store.items['proposal-a'].status, 'acked');
assert.deepEqual(drainChanges, ['running', 'acked']);

const failing = await drainReactiveOutbox({
  store: queued,
  now,
  retryDelayMs: 1_000,
  executeProposal: () => ({ ok: false, error: 'kernel rejected proposal' }),
});
assert.deepEqual(failing.failed, [{ proposalId: 'proposal-a', error: 'kernel rejected proposal' }]);
assert.equal(failing.store.items['proposal-a'].status, 'pending');
assert.equal(failing.store.items['proposal-a'].attempts, 1);

console.log('reactive-outbox: passed');
