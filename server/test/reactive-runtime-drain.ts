import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { drainReactiveRuntimeOutbox } from '../src/kernel/install-reactive-runtime';
import { createReactiveOutboxStore, enqueueReactiveProposals } from '../src/kernel/reactive-outbox';
import { createReactiveReceiptStore } from '../src/kernel/reactive-receipts';
import type { ReactiveCycleResult } from '../src/kernel/reactive-cycle';
import type { OperationCommitEvent } from '../src/kernel/operation-observer';
import { createOperationProposalIdempotencyKey } from '../src/kernel/rules';

const runtimePath = join(mkdtempSync(join(tmpdir(), 'wonderfood-reactive-runtime-')), 'runtime.json');
const proposalEvent = { kind: 'query_transition' as const, id: 'runtime-query:enter', queryId: 'runtime-query', transition: 'enter' as const };
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
const proposalIdempotencyKey = createOperationProposalIdempotencyKey({
  packageId: 'runtime-package',
  packageVersion: '1.0.0',
  ruleId: 'runtime-rule',
  event: proposalEvent,
  causeId: 'runtime-cause',
  operationTemplate,
});
const event: OperationCommitEvent = {
  actionId: 'runtime-action',
  operationId: 'runtime-operation',
  causeId: 'runtime-cause',
  domain: 'food',
  recordId: 'runtime-record',
  before: null,
  after: { id: 'runtime-record' },
};
const cycle: ReactiveCycleResult = {
  cycleId: 'runtime-cycle',
  transitions: [],
  queryHashes: {},
  proposals: [{
    id: 'runtime-proposal',
    eventId: 'runtime-query:enter',
    event: proposalEvent,
    ruleId: 'runtime-rule',
    operation: 'request_review',
    operationTemplate,
    mode: 'suggest',
    causeId: 'runtime-cause',
    packageVersion: '1.0.0',
    depth: 0,
    envelope: {
      schemaVersion: 'wonder.operation-proposal.v1',
      proposalId: 'runtime-proposal',
      operation: 'request_review',
      operationTemplate,
      mode: 'suggest',
      ruleId: 'runtime-rule',
      packageId: 'runtime-package',
      packageVersion: '1.0.0',
      eventId: 'runtime-query:enter',
      event: proposalEvent,
      causeId: 'runtime-cause',
      depth: 0,
      idempotencyKey: proposalIdempotencyKey,
      review: { required: true, reason: 'suggest_mode', policyId: authorization.policyId, policyVersion: authorization.policyVersion },
      authorization,
      dryRun,
      evidence: { queryId: 'runtime-query', transition: 'enter' },
    },
  }],
};
const outbox = enqueueReactiveProposals(createReactiveOutboxStore(), {
  cycle,
  event,
  proposalIds: ['runtime-proposal'],
  now: '2026-07-23T00:00:00.000Z',
});
await import('node:fs').then(({ writeFileSync }) => writeFileSync(runtimePath, JSON.stringify({
  schemaVersion: 'wonder.reactive-runtime.v1',
  receipts: createReactiveReceiptStore(),
  outbox,
}, null, 2)));

const seen: string[] = [];
const result = await drainReactiveRuntimeOutbox({
  path: runtimePath,
  now: '2026-07-23T00:00:00.000Z',
  executeProposal: (item) => {
    seen.push(item.proposalId);
    return { ok: true };
  },
});
assert.deepEqual(seen, ['runtime-proposal']);
assert.deepEqual(result.acked, ['runtime-proposal']);

const persisted = JSON.parse(readFileSync(runtimePath, 'utf8'));
assert.equal(persisted.outbox.items['runtime-proposal'].status, 'acked');
assert.equal(persisted.outbox.items['runtime-proposal'].attempts, 0);

console.log('reactive-runtime-drain: passed');
