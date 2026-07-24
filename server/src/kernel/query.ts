export type QueryValue = string | number | boolean | null;

export type QueryPredicate =
  | { op: 'and' | 'or'; args: QueryPredicate[] }
  | { op: 'not'; arg: QueryPredicate }
  | { op: 'eq' | 'neq' | 'gt' | 'gte' | 'lt' | 'lte'; field: string; value: QueryValue }
  | { op: 'contains' | 'starts_with'; field: string; value: string }
  | { op: 'exists'; field: string; value?: boolean };

export type QuerySort = { field: string; direction?: 'asc' | 'desc' };

export type QuerySpec<T extends Record<string, unknown> = Record<string, unknown>> = {
  from: string;
  where?: QueryPredicate;
  orderBy?: QuerySort[];
  limit?: number;
  offset?: number;
  project?: string[];
  provenance?: string;
  getField?: (row: T, field: string) => unknown;
};

export type QueryResult<T> = {
  rows: T[];
  total: number;
  offset: number;
  limit: number | null;
  resultHash: string;
  provenance?: string;
};

function readPath(value: unknown, path: string): unknown {
  return path.split('.').reduce<unknown>((current, segment) => {
    if (!current || typeof current !== 'object') return undefined;
    return (current as Record<string, unknown>)[segment];
  }, value);
}

function compare(a: unknown, b: unknown): number {
  if (a === b) return 0;
  if (a === null || a === undefined) return -1;
  if (b === null || b === undefined) return 1;
  if (typeof a === 'number' && typeof b === 'number') return a - b;
  return String(a).localeCompare(String(b));
}

export function matches<T extends Record<string, unknown>>(
  row: T,
  predicate: QueryPredicate,
  getField: (row: T, field: string) => unknown = readPath,
): boolean {
  switch (predicate.op) {
    case 'and': return predicate.args.every((arg) => matches(row, arg, getField));
    case 'or': return predicate.args.some((arg) => matches(row, arg, getField));
    case 'not': return !matches(row, predicate.arg, getField);
    case 'exists': return (getField(row, predicate.field) !== undefined) === (predicate.value ?? true);
    case 'eq': return getField(row, predicate.field) === predicate.value;
    case 'neq': return getField(row, predicate.field) !== predicate.value;
    case 'gt': return compare(getField(row, predicate.field), predicate.value) > 0;
    case 'gte': return compare(getField(row, predicate.field), predicate.value) >= 0;
    case 'lt': return compare(getField(row, predicate.field), predicate.value) < 0;
    case 'lte': return compare(getField(row, predicate.field), predicate.value) <= 0;
    case 'contains': return String(getField(row, predicate.field) ?? '').toLocaleLowerCase().includes(predicate.value.toLocaleLowerCase());
    case 'starts_with': return String(getField(row, predicate.field) ?? '').toLocaleLowerCase().startsWith(predicate.value.toLocaleLowerCase());
  }
}

function stableJson(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value as Record<string, unknown>).sort().map((key) => `${JSON.stringify(key)}:${stableJson((value as Record<string, unknown>)[key])}`).join(',')}}`;
  }
  return JSON.stringify(value) ?? 'null';
}

function hash(value: string): string {
  let result = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    result ^= value.charCodeAt(index);
    result = Math.imul(result, 16777619);
  }
  return `fnv1a:${(result >>> 0).toString(16).padStart(8, '0')}`;
}

export function executeQuery<T extends Record<string, unknown>>(rows: readonly T[], spec: QuerySpec<T>): QueryResult<T> {
  const getField = spec.getField ?? readPath;
  let selected = rows.filter((row) => !spec.where || matches(row, spec.where, getField));
  const total = selected.length;
  if (spec.orderBy?.length) {
    selected = [...selected].sort((left, right) => {
      for (const sort of spec.orderBy ?? []) {
        const result = compare(getField(left, sort.field), getField(right, sort.field));
        if (result !== 0) return sort.direction === 'desc' ? -result : result;
      }
      return 0;
    });
  }
  const offset = Math.max(0, spec.offset ?? 0);
  const limited = spec.limit === undefined ? selected.slice(offset) : selected.slice(offset, offset + Math.max(0, spec.limit));
  const projected = spec.project?.length
    ? limited.map((row) => Object.fromEntries(spec.project!.map((field) => [field, getField(row, field)])) as T)
    : limited;
  return {
    rows: projected,
    total,
    offset,
    limit: spec.limit ?? null,
    resultHash: hash(stableJson(projected)),
    provenance: spec.provenance,
  };
}
