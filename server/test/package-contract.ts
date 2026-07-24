import assert from 'node:assert/strict';
import { validateAppPackage } from '../src/kernel/package';

const pkg = {
  schemaVersion: 'wonder.app-package.v2',
  id: 'decision-ledger',
  version: '1.0.0',
  collections: { decisions: { id: 'decisions', fields: { state: { type: 'text', indexed: true } } } },
  queries: { open: { from: 'decisions', where: { op: 'eq', field: 'state', value: 'open' } } },
  views: { inbox: { id: 'inbox', query: 'open', mode: 'table', fields: ['state'] } },
  rules: [{ id: 'require-approval', trigger: { kind: 'operation' }, effect: { kind: 'propose_operation', operation: 'approve' }, mode: 'suggest', maxRunsPerEvent: 1 }],
  capabilities: [],
  acceptanceTests: ['decision-approval-invariant'],
};

assert.equal(validateAppPackage(pkg).valid, true);
assert.equal(validateAppPackage({
  ...pkg,
  rules: [{
    id: 'typed-update',
    trigger: { kind: 'operation' },
    effect: { kind: 'propose_operation', operation: { kind: 'update_record', collection: 'decisions', recordId: 'decision-a', expectedRevision: 3, changes: { state: 'review' } } },
    mode: 'suggest',
    maxRunsPerEvent: 1,
  }],
}).valid, true);
assert.equal(validateAppPackage({ ...pkg, javascript: 'bad' }).valid, false);
assert.equal(validateAppPackage({ ...pkg, views: { inbox: { id: 'wrong', query: '', mode: 'table', fields: [] } } }).valid, false);
assert.equal(validateAppPackage({ ...pkg, queries: { inbox: { from: 'records', limit: -1 } } }).valid, false);
assert.equal(validateAppPackage({
  ...pkg,
  views: { inbox: { id: 'inbox', query: 'missing', mode: 'table', fields: ['state'] } },
}).valid, false);
assert.equal(validateAppPackage({
  ...pkg,
  queries: { open: { from: 'ghosts' } },
}).valid, false);
assert.equal(validateAppPackage({
  ...pkg,
  views: { inbox: { id: 'inbox', query: 'open', mode: 'table', fields: ['state'], layout: { code: 'bad' } } },
}).valid, false);
assert.equal(validateAppPackage({
  ...pkg,
  views: { inbox: { id: 'inbox', query: 'open', mode: 'table', fields: ['state'], layout: { columns: 12 } } },
}).valid, false);
assert.equal(validateAppPackage({
  ...pkg,
  rules: [{ id: 'loose', script: 'bad' }],
}).valid, false);
assert.equal(validateAppPackage({
  ...pkg,
  rules: [{ id: 'bad-rule', trigger: { kind: 'query_transition', query: 'missing' }, effect: { kind: 'propose_operation', operation: 'approve' }, mode: 'suggest', maxRunsPerEvent: 1 }],
}).valid, false);
assert.equal(validateAppPackage({
  ...pkg,
  rules: [{ id: 'too-many', trigger: { kind: 'operation' }, effect: { kind: 'propose_operation', operation: 'approve' }, mode: 'suggest', maxRunsPerEvent: 65 }],
}).valid, false);
assert.equal(validateAppPackage({
  ...pkg,
  rules: [{ id: 'bad-template', trigger: { kind: 'operation' }, effect: { kind: 'propose_operation', operation: { kind: 'create_record', collection: 'missing' } }, mode: 'suggest', maxRunsPerEvent: 1 }],
}).valid, false);
assert.equal(validateAppPackage({
  ...pkg,
  capabilities: ['mcp-tool:ok', 'mcp-tool:ok'],
}).valid, false);
assert.equal(validateAppPackage({
  ...pkg,
  acceptanceTests: ['bad test name'],
}).valid, false);
console.log('package-contract: passed');
