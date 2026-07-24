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
assert.equal(validateAppPackage({ ...pkg, javascript: 'bad' }).valid, false);
assert.equal(validateAppPackage({ ...pkg, views: { inbox: { id: 'wrong', query: '', mode: 'table', fields: [] } } }).valid, false);
assert.equal(validateAppPackage({ ...pkg, queries: { inbox: { from: 'records', limit: -1 } } }).valid, false);
console.log('package-contract: passed');
