import assert from 'node:assert/strict';
import { createReactiveCycleObserver } from '../src/kernel/reactive-observer';
import { createReactiveReceiptStore } from '../src/kernel/reactive-receipts';
import { createReactiveOutboxStore, enqueueReactiveProposals } from '../src/kernel/reactive-outbox';
import { setOperationCommitObserver } from '../src/kernel/operation-observer';
import { createRecordWithAction, deleteRecord, listRecords } from '../src/mcp/state';
import type { AppPackageV2 } from '../src/kernel/package';

const appPackage: AppPackageV2 = {
  schemaVersion: 'wonder.app-package.v2', id: 'observer-proof', version: '1.0.0',
  collections: { recipe: { id: 'recipe', fields: { status: { type: 'text' } } } },
  queries: { open: { from: 'recipe', where: { op: 'eq', field: 'properties.status', value: 'open' } } },
  views: {}, rules: [{
    id: 'review-enter', trigger: { kind: 'query_transition', query: 'open', transition: 'enter' },
    effect: { kind: 'propose_operation', operation: 'request_review' }, mode: 'suggest', maxRunsPerEvent: 1,
  }], capabilities: [], acceptanceTests: [],
};
let store = createReactiveReceiptStore();
let outbox = createReactiveOutboxStore();
const proposals: string[] = [];
const observer = createReactiveCycleObserver({
  package: appPackage,
  getRows: () => listRecords({ domain: 'food', collection: 'recipe' }) as unknown as Record<string, unknown>[],
  getReceiptStore: () => store,
  setReceiptStore: (next) => { store = next; },
  commitCycle: ({ receipt, cycle, event }) => {
    outbox = enqueueReactiveProposals(outbox, { cycle, event, proposalIds: receipt.newProposalIds, now: '2026-07-23T00:00:00.000Z' });
    store = receipt.store;
    proposals.push(...receipt.newProposalIds);
  },
});
setOperationCommitObserver(observer);
const result = createRecordWithAction({
  actionId: 'reactive-observer-action', actor: 'test', domain: 'food', tool: 'create_record', risk: 'low', command: 'create',
  record: { id: 'reactive-observer-record', domain: 'food', collection: 'recipe', title: 'Observer', properties: { status: 'open' }, relations: [], source: { provider: 'user', external_id: 'reactive-observer-record', url: null, observed_at: new Date().toISOString(), content_hash: null }, archived_at: null },
});
setOperationCommitObserver(null);
assert.equal(result.replayed, false);
assert.equal(proposals.length, 1);
assert.equal(Object.keys(store.cycles).length, 1);
assert.equal(Object.keys(outbox.items).length, 1);
assert.equal(outbox.items[proposals[0]].status, 'pending');
deleteRecord('reactive-observer-record');
console.log('reactive-observer: passed');
