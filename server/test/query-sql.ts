import assert from 'node:assert/strict';
import { compileQueryToSql } from '../src/kernel/query-sql';

const compiled = compileQueryToSql({
  from: 'records',
  where: { op: 'and', args: [
    { op: 'eq', field: 'domain', value: 'decision-ledger' },
    { op: 'contains', field: 'properties.title', value: 'rotate' },
  ] },
  orderBy: [{ field: 'updated_at', direction: 'desc' }],
  limit: 20,
  offset: 5,
});
assert.equal(compiled.sql, 'SELECT * FROM "records" WHERE ("domain" = ? AND json_extract("properties", \'$.title\') LIKE ? ESCAPE \'\\\' COLLATE NOCASE) ORDER BY "updated_at" DESC, "id" ASC LIMIT 20 OFFSET 5');
assert.deepEqual(compiled.params, ['decision-ledger', '%rotate%']);

const offsetWithoutLimit = compileQueryToSql({
  from: 'records',
  orderBy: [{ field: 'id' }],
  offset: 5,
});
assert.equal(
  offsetWithoutLimit.sql,
  'SELECT * FROM "records" ORDER BY "id" ASC LIMIT -1 OFFSET 5',
);

assert.throws(() => compileQueryToSql({ from: 'records;drop', where: { op: 'eq', field: 'title', value: 'x' } }), /invalid_collection/);
assert.throws(() => compileQueryToSql({ from: 'records', where: { op: 'eq', field: 'raw_sql', value: 'x' } }), /unsupported_query_field/);

const nullEq = compileQueryToSql({
  from: 'records',
  where: { op: 'eq', field: 'properties.deleted_at', value: null },
});
assert.equal(nullEq.sql, 'SELECT * FROM "records" WHERE json_extract("properties", \'$.deleted_at\') IS NULL');
assert.deepEqual(nullEq.params, []);

const nullNeq = compileQueryToSql({
  from: 'records',
  where: { op: 'neq', field: 'properties.deleted_at', value: null },
});
assert.equal(nullNeq.sql, 'SELECT * FROM "records" WHERE json_extract("properties", \'$.deleted_at\') IS NOT NULL');
assert.deepEqual(nullNeq.params, []);

const escapedLike = compileQueryToSql({
  from: 'records',
  where: { op: 'contains', field: 'properties.title', value: '100%_done\\now' },
});
assert.equal(escapedLike.sql, 'SELECT * FROM "records" WHERE json_extract("properties", \'$.title\') LIKE ? ESCAPE \'\\\' COLLATE NOCASE');
assert.deepEqual(escapedLike.params, ['%100\\%\\_done\\\\now%']);

const stableOrder = compileQueryToSql({
  from: 'records',
  orderBy: [{ field: 'updated_at', direction: 'desc' }],
});
assert.equal(stableOrder.sql, 'SELECT * FROM "records" ORDER BY "updated_at" DESC, "id" ASC');
console.log('query-sql: passed');
