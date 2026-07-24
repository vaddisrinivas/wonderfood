import assert from 'node:assert/strict';
import { verifyResult } from '../src/agents/verifier';
import { createActionEvent, createRecordWithAction, deleteRecord, markActionFailed } from '../src/mcp/state';

const incomplete = await verifyResult({
  actionId: 'a1',
  expected: 'update_record',
  actualStatus: 'completed',
  actualRecordIds: ['r1'],
});
assert.equal(incomplete.status, 'denied');
assert.match(incomplete.reason ?? '', /Canonical action event was not found/);

const failedSeed = createActionEvent({
  id: 'canonical-verification-failed',
  actor: 'test',
  domain: 'food',
  tool: 'wonderfood.update_record',
  risk: 'standard',
  recordIds: ['canonical-verification-record'],
  command: 'update record',
}, { persist: false });
markActionFailed(failedSeed.id, 'test failure', { persist: false });
const canonicalFailed = await verifyResult({
  actionId: failedSeed.id,
  expected: 'wonderfood.update_record',
  actualStatus: 'completed',
  actualRecordIds: ['canonical-verification-record'],
});
assert.equal(canonicalFailed.status, 'denied');
assert.match(canonicalFailed.reason ?? '', /Action status is failed/);

const noRecordSeed = createActionEvent({
  id: 'canonical-verification-no-record',
  actor: 'test',
  domain: 'food',
  tool: 'wonderfood.update_record',
  risk: 'standard',
  recordIds: [],
  command: 'update record',
  undoPayload: { operation: 'restore_after_update' },
  status: 'completed',
}, { persist: false });
const missingIdentity = await verifyResult({
  actionId: noRecordSeed.id,
  expected: 'wonderfood.update_record',
  expectedSupportsUndo: true,
  actualStatus: 'completed',
  actualRecordIds: [],
});
assert.equal(missingIdentity.status, 'denied');

const cannotDisableCanonicalIdentity = await verifyResult({
  actionId: noRecordSeed.id,
  expected: 'wonderfood.update_record',
  expectedSupportsUndo: false,
  actualStatus: 'completed',
  actualRecordIds: [],
});
assert.equal(cannotDisableCanonicalIdentity.status, 'denied');
assert.ok(cannotDisableCanonicalIdentity.checks.includes('record_identity'));

deleteRecord('canonical-verification-record');
const write = createRecordWithAction({
  actionId: 'canonical-verification-completed',
  actor: 'test',
  domain: 'food',
  tool: 'wonderfood.update_record',
  risk: 'standard',
  command: 'update record',
  sourceIds: ['source-1'],
  record: {
    id: 'canonical-verification-record',
    domain: 'food',
    collection: 'ingredient',
    title: 'Verifier Tomato',
    properties: {},
    relations: [],
    source: {
      provider: 'user',
      external_id: 'canonical-verification-record',
      url: null,
      observed_at: new Date().toISOString(),
      content_hash: null,
    },
    archived_at: null,
  },
});
const verified = await verifyResult({
  actionId: write.action.id,
  expected: 'wonderfood.update_record',
  expectedSupportsUndo: true,
  actualStatus: 'failed',
  actualRecordIds: [],
  sourceBound: false,
});
assert.equal(verified.status, 'verified');
assert.ok(verified.checks.includes('canonical_postcondition'));
assert.ok(!verified.checks.includes('source_bound_fallback'));

deleteRecord('canonical-verification-record');
const missingAfterCommit = await verifyResult({
  actionId: write.action.id,
  expected: 'wonderfood.update_record',
  actualStatus: 'completed',
  actualRecordIds: [write.record?.id ?? 'missing'],
});
assert.equal(missingAfterCommit.status, 'denied');
assert.match(missingAfterCommit.reason ?? '', /was not found in canonical state/);
console.log('canonical-verification: passed');
