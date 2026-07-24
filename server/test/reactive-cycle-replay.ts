import assert from 'node:assert/strict';

import type { AppPackageV2 } from '../src/kernel/package';
import { runReactiveCycle } from '../src/kernel/reactive-cycle';

const pkg: AppPackageV2 = {
  schemaVersion: 'wonder.app-package.v2',
  id: 'decision-ledger',
  version: '1.0.0',
  collections: {
    decisions: {
      id: 'decisions',
      fields: {
        status: { type: 'text', indexed: true },
        risk: { type: 'number', indexed: true },
      },
    },
  },
  queries: {
    'high-risk-open': {
      from: 'decisions',
      where: {
        op: 'and',
        args: [
          { op: 'eq', field: 'status', value: 'open' },
          { op: 'gte', field: 'risk', value: 3 },
        ],
      },
      orderBy: [{ field: 'id', direction: 'asc' }],
    },
  },
  views: {},
  rules: [{
    id: 'review-new-high-risk',
    trigger: { kind: 'query_transition', query: 'high-risk-open', transition: 'enter' },
    when: { '>': [{ var: 'query.after.total' }, { var: 'query.before.total' }] },
    effect: { kind: 'propose_operation', operation: 'request_review' },
    mode: 'suggest',
    maxRunsPerEvent: 4,
  }],
  capabilities: [],
  acceptanceTests: ['reactive-cycle-replay'],
};

const before = [
  { id: 'decision-a', collection: 'decisions', status: 'closed', risk: 4 },
  { id: 'unrelated', collection: 'notes', status: 'open', risk: 5 },
];
const after = [
  { id: 'decision-a', collection: 'decisions', status: 'open', risk: 4 },
  { id: 'unrelated', collection: 'notes', status: 'open', risk: 5 },
];
const input = {
  package: pkg,
  beforeRows: before,
  afterRows: after,
  event: { kind: 'operation' as const, id: 'op-open-decision-a' },
  causeId: 'action-open-decision-a',
};

const first = runReactiveCycle(input);
const replay = runReactiveCycle({
  ...input,
  beforeRows: [...before].reverse(),
  afterRows: [...after].reverse(),
});

assert.deepEqual(replay, first);
assert.equal(first.transitions.length, 1);
assert.deepEqual(first.transitions[0], {
  kind: 'query_transition',
  id: 'high-risk-open',
  transition: 'enter',
  addedIds: ['decision-a'],
  removedIds: [],
  changedIds: [],
});
assert.equal(first.proposals.length, 1, 'identical maxRuns proposals must dedupe');
assert.equal(first.proposals[0].operation, 'request_review');
assert.equal(first.proposals[0].causeId, 'action-open-decision-a');

const noChange = runReactiveCycle({
  ...input,
  beforeRows: after,
  afterRows: after,
});
assert.equal(noChange.transitions.length, 0);
assert.equal(noChange.proposals.length, 0);

const computedPackage: AppPackageV2 = {
  ...pkg,
  id: 'computed-decision-ledger',
  computedFields: [{
    id: 'needs_review',
    collection: 'decisions',
    dependsOn: [],
    expression: {
      and: [
        { '==': [{ var: 'record.status' }, 'open'] },
        { '>=': [{ var: 'record.risk' }, 3] },
      ],
    },
  }],
  queries: {
    'computed-review-queue': {
      from: 'decisions',
      where: { op: 'eq', field: 'needs_review', value: true },
      orderBy: [{ field: 'id', direction: 'asc' }],
    },
  },
  rules: [{
    id: 'review-computed-risk',
    trigger: { kind: 'query_transition', query: 'computed-review-queue', transition: 'enter' },
    effect: { kind: 'propose_operation', operation: 'request_computed_review' },
    mode: 'suggest',
    maxRunsPerEvent: 1,
  }],
};
const computedBefore = [
  Object.freeze({ id: 'decision-computed', collection: 'decisions', status: 'closed', risk: 4 }),
];
const computedAfter = [
  Object.freeze({ id: 'decision-computed', collection: 'decisions', status: 'open', risk: 4 }),
];
const computedCycle = runReactiveCycle({
  package: computedPackage,
  beforeRows: computedBefore,
  afterRows: computedAfter,
  event: { kind: 'operation', id: 'op-computed' },
  causeId: 'action-computed',
});
assert.equal(computedCycle.transitions[0]?.id, 'computed-review-queue');
assert.equal(computedCycle.transitions[0]?.transition, 'enter');
assert.equal(computedCycle.proposals[0]?.operation, 'request_computed_review');
assert.deepEqual(computedBefore[0], {
  id: 'decision-computed',
  collection: 'decisions',
  status: 'closed',
  risk: 4,
});
assert.deepEqual(computedAfter[0], {
  id: 'decision-computed',
  collection: 'decisions',
  status: 'open',
  risk: 4,
});

console.log('reactive-cycle-replay: passed');
