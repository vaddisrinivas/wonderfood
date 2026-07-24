import assert from 'node:assert/strict';

import { verifyReactiveProposalPostcondition } from '../src/kernel/reactive-proposal-verification';
import type { McpRecord } from '../src/mcp/state';

const record: McpRecord = {
  id: 'verify-record',
  domain: 'food',
  collection: 'recipe',
  title: 'Verify',
  properties: { status: 'done' },
  relations: [],
  source: { provider: 'user', external_id: 'verify-record', url: null, observed_at: new Date().toISOString(), content_hash: null },
  archived_at: null,
  created_at: new Date().toISOString(),
  updated_at: new Date().toISOString(),
  revision: 2,
};

assert.equal(verifyReactiveProposalPostcondition({
  operationTemplate: { kind: 'create_record', collection: 'recipe', recordId: record.id, properties: { status: 'done' } },
  record,
}).ok, true);
assert.equal(verifyReactiveProposalPostcondition({
  operationTemplate: { kind: 'update_record', recordId: record.id, changes: { status: 'done' } },
  record,
  beforeRevision: 1,
}).ok, true);
assert.equal(verifyReactiveProposalPostcondition({
  operationTemplate: { kind: 'update_record', recordId: record.id, changes: { status: 'missing' } },
  record,
  beforeRevision: 1,
}).ok, false);
assert.equal(verifyReactiveProposalPostcondition({
  operationTemplate: { kind: 'archive_record', recordId: record.id },
  record: { ...record, archived_at: new Date().toISOString() },
}).reason, 'canonical_archive_verified');

console.log('reactive-proposal-verification: passed');
