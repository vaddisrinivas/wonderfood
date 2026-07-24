import assert from 'node:assert/strict';
import { verifyResult } from '../src/agents/verifier';

const incomplete = await verifyResult({
  actionId: 'a1',
  expected: 'update_record',
  actualStatus: 'failed',
  actualRecordIds: ['r1'],
});
assert.equal(incomplete.status, 'denied');
assert.match(incomplete.reason ?? '', /canonical completion was not proven/);

const missingIdentity = await verifyResult({
  actionId: 'a2',
  expected: 'update_record',
  expectedSupportsUndo: true,
  actualStatus: 'completed',
  actualRecordIds: [],
});
assert.equal(missingIdentity.status, 'denied');

const cannotDisableCanonicalIdentity = await verifyResult({
  actionId: 'a2-disabled',
  expected: 'wonderfood.update_record',
  expectedSupportsUndo: false,
  actualStatus: 'completed',
  actualRecordIds: [],
});
assert.equal(cannotDisableCanonicalIdentity.status, 'denied');
assert.ok(cannotDisableCanonicalIdentity.checks.includes('record_identity'));

const verified = await verifyResult({
  actionId: 'a3',
  expected: 'update_record',
  expectedSupportsUndo: true,
  actualStatus: 'completed',
  actualRecordIds: ['r3'],
  sourceBound: true,
});
assert.equal(verified.status, 'verified');
assert.ok(verified.checks.includes('canonical_postcondition'));
console.log('canonical-verification: passed');
