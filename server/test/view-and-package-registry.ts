import assert from 'node:assert/strict';
import { executeQuery } from '../src/kernel/query';
import { renderView } from '../src/kernel/view';
import { PackageRegistry } from '../src/kernel/package-registry';
import type { ViewSpec } from '../src/kernel/package';

const board: ViewSpec = { id: 'board', query: 'all', mode: 'board', fields: ['id', 'state'], groupBy: 'state' };
const pkg = {
  schemaVersion: 'wonder.app-package.v2', id: 'ledger', version: '1.0.0',
  collections: { decisions: { id: 'decisions', fields: { state: { type: 'text' } } } },
  queries: { all: { from: 'decisions' } },
  views: { board },
  rules: [], capabilities: [], acceptanceTests: [],
};
const registry = new PackageRegistry();
assert.equal(registry.preview(pkg).valid, true);
registry.activate(pkg);
assert.equal(registry.getActive()?.id, 'ledger');
registry.activate({ ...pkg, version: '2.0.0' });
assert.equal(registry.rollback()?.version, '1.0.0');

const result = executeQuery([{ id: 'a', state: 'open' }, { id: 'b', state: 'closed' }], { from: 'decisions', provenance: 'ledger.all.v1' });
const model = renderView(pkg.views.board, result);
assert.deepEqual(model.groups, { open: [{ id: 'a', state: 'open' }], closed: [{ id: 'b', state: 'closed' }] });
assert.equal(model.provenance, 'ledger.all.v1');
console.log('view-and-package-registry: passed');
