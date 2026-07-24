import assert from 'node:assert/strict';
import { executeQuery } from '../src/kernel/query';
import { evaluateExpression } from '../src/kernel/expression';

const rows = [
  { id: 'b', state: 'open', score: 2, owner: { name: 'Bo' } },
  { id: 'a', state: 'closed', score: 5, owner: { name: 'Al' } },
  { id: 'c', state: 'open', score: 8, owner: { name: 'Cy' } },
];

const result = executeQuery(rows, {
  from: 'decisions',
  where: { op: 'eq', field: 'state', value: 'open' },
  orderBy: [{ field: 'score', direction: 'desc' }],
  project: ['id', 'score'],
  provenance: 'decision-ledger.open.v1',
});

assert.deepEqual(result.rows, [{ id: 'c', score: 8 }, { id: 'b', score: 2 }]);
assert.equal(result.total, 2);
assert.equal(result.provenance, 'decision-ledger.open.v1');
assert.equal(result.resultHash, executeQuery(rows, {
  from: 'decisions',
  where: { op: 'eq', field: 'state', value: 'open' },
  orderBy: [{ field: 'score', direction: 'desc' }],
  project: ['id', 'score'],
}).resultHash);
assert.equal(evaluateExpression({ decision: { risk: 4, title: 'Rotate key' } }, {
  and: [
    { ">=": [{ var: 'decision.risk' }, 3] },
    { '==': [{ var: 'decision.title' }, 'Rotate key'] },
  ],
}), true);
console.log('query-kernel: passed');
