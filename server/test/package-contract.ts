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
  presentation: {
    label: 'Decisions',
    homeSurface: 'decisions.inbox',
    surfaces: [{ id: 'decisions.inbox', label: 'Inbox', icon: 'inbox', collections: ['decisions'] }],
    visualIdentity: { domain: { icon: 'inbox', accent: 'blue' } },
    dashboardBlocks: [{ id: 'open', title: 'Open decisions', query: { collections: ['decisions'] } }],
    render: { default_title: 'Decision inbox' },
    richDetailSchema: 'schemas/decision-detail.v1.schema.json',
    providerTemplateFields: { required: ['id', 'title'] },
    sourceSchemaVersion: 'lifeos.domain.v1',
  },
}).valid, true);
assert.equal(validateAppPackage({
  ...pkg,
  presentation: {
    label: 'Food shell',
    homeSurface: 'decisions.inbox',
    surfaces: [{ id: 'decisions.inbox', label: 'Inbox', collections: ['decisions'] }],
    visualIdentity: { domain: { icon: 'inbox', accent: 'blue' } },
    ui: {
      schemaVersion: 'wonder.ui.v1',
      openUrlAllowlist: ['https://wonder.example', 'http://localhost:3000'],
      components: [
        {
          kind: 'action',
          id: 'open-home',
          title: 'Open docs',
          action: { kind: 'open_url', url: 'https://wonder.example/docs' },
        },
        {
          kind: 'action',
          id: 'propose-action',
          title: 'Propose update',
          action: { kind: 'propose', tool: 'local_query' },
        },
      ],
      screens: {
        home: {
          title: 'Home',
          subtitle: 'Primary',
          components: [
            { kind: 'text', id: 'tip', title: 'Tip', subtitle: 'Welcome to Food.' },
            { kind: 'recordList', id: 'records', title: 'Recent', query: { collections: ['decisions'], limit: 3 } },
          ],
        },
      },
      defaultScreen: 'home',
    },
  },
}).valid, true);
assert.equal(validateAppPackage({
  ...pkg,
  presentation: {
    label: 'Bad',
    surfaces: [{ id: 'bad', label: 'Bad', collections: [], script: 'bad' }],
  },
}).valid, false);
assert.equal(validateAppPackage({
  ...pkg,
  presentation: {
    label: 'Bad',
    surfaces: [{ id: 'inbox', label: 'Inbox', collections: ['decisions'] }],
    ui: { schemaVersion: 'wonder.ui.v1', components: [{ kind: 'action', id: 'bad-open', title: 'Bad', action: { kind: 'open_url' } }] },
  },
}).valid, false);
assert.equal(validateAppPackage({
  ...pkg,
  presentation: {
    label: 'Bad',
    surfaces: [{ id: 'inbox', label: 'Inbox', collections: ['decisions'] }],
    ui: { schemaVersion: 'wonder.ui.v1', components: [{ kind: 'action', id: 'bad-propose', title: 'Bad', action: { kind: 'propose' } }] },
  },
}).valid, false);
assert.equal(validateAppPackage({
  ...pkg,
  presentation: {
    label: 'Bad',
    surfaces: [{ id: 'inbox', label: 'Inbox', collections: ['decisions'] }],
    ui: { schemaVersion: 'wonder.ui.v1', openUrlAllowlist: [''], components: [] },
  },
}).valid, false);
assert.equal(validateAppPackage({
  ...pkg,
  presentation: {
    label: 'Bad',
    homeSurface: 'missing',
    surfaces: [{ id: 'inbox', label: 'Inbox', collections: ['decisions'] }],
  },
}).valid, false);
assert.equal(validateAppPackage({
  ...pkg,
  presentation: {
    label: 'Bad',
    surfaces: [{ id: 'inbox', label: 'Inbox', collections: ['ghosts'] }],
  },
}).valid, false);
assert.equal(validateAppPackage({
  ...pkg,
  presentation: {
    label: 'Bad',
    surfaces: [
      { id: 'inbox', label: 'Inbox', collections: ['decisions'] },
      { id: 'inbox', label: 'Inbox duplicate', collections: ['decisions'] },
    ],
  },
}).valid, false);
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
