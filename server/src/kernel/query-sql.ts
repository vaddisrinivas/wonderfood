import { QueryPredicate, QuerySort, QuerySpec } from './query';

export type CompiledQuery = { sql: string; params: unknown[] };

function identifier(value: string, label: string): string {
  if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(value)) throw new Error(`invalid_${label}`);
  return `"${value}"`;
}

function fieldSql(field: string): string {
  if (field === 'id' || field === 'title' || field === 'domain' || field === 'collection' || field === 'updated_at') return identifier(field, 'field');
  if (field.startsWith('properties.')) {
    const path = field.slice('properties.'.length);
    if (!/^[A-Za-z_][A-Za-z0-9_.]*$/.test(path)) throw new Error('invalid_property_path');
    return `json_extract("properties", '$.${path}')`;
  }
  throw new Error('unsupported_query_field');
}

function predicateSql(predicate: QueryPredicate, params: unknown[]): string {
  switch (predicate.op) {
    case 'and': return `(${predicate.args.map((arg) => predicateSql(arg, params)).join(' AND ') || '1=1'})`;
    case 'or': return `(${predicate.args.map((arg) => predicateSql(arg, params)).join(' OR ') || '0=1'})`;
    case 'not': return `(NOT ${predicateSql(predicate.arg, params)})`;
    case 'exists': return `${fieldSql(predicate.field)} IS ${predicate.value === false ? 'NULL' : 'NOT NULL'}`;
    case 'eq': params.push(predicate.value); return `${fieldSql(predicate.field)} = ?`;
    case 'neq': params.push(predicate.value); return `${fieldSql(predicate.field)} <> ?`;
    case 'gt': params.push(predicate.value); return `${fieldSql(predicate.field)} > ?`;
    case 'gte': params.push(predicate.value); return `${fieldSql(predicate.field)} >= ?`;
    case 'lt': params.push(predicate.value); return `${fieldSql(predicate.field)} < ?`;
    case 'lte': params.push(predicate.value); return `${fieldSql(predicate.field)} <= ?`;
    case 'contains': params.push(`%${predicate.value}%`); return `${fieldSql(predicate.field)} LIKE ? COLLATE NOCASE`;
    case 'starts_with': params.push(`${predicate.value}%`); return `${fieldSql(predicate.field)} LIKE ? COLLATE NOCASE`;
  }
}

export function compileQueryToSql<T extends Record<string, unknown>>(spec: QuerySpec<T>): CompiledQuery {
  const params: unknown[] = [];
  const from = identifier(spec.from, 'collection');
  const where = spec.where ? ` WHERE ${predicateSql(spec.where, params)}` : '';
  const order = spec.orderBy?.length
    ? ` ORDER BY ${spec.orderBy.map((sort: QuerySort) => `${fieldSql(sort.field)} ${sort.direction === 'desc' ? 'DESC' : 'ASC'}`).join(', ')}`
    : '';
  // SQLite requires LIMIT before OFFSET. -1 means unbounded.
  const limit = spec.limit === undefined
    ? (spec.offset === undefined ? '' : ' LIMIT -1')
    : ` LIMIT ${Math.max(0, Math.floor(spec.limit))}`;
  const offset = spec.offset === undefined ? '' : ` OFFSET ${Math.max(0, Math.floor(spec.offset))}`;
  return { sql: `SELECT * FROM ${from}${where}${order}${limit}${offset}`, params };
}
