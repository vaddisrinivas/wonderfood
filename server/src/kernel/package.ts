import { QueryPredicate, QuerySort } from './query';
import { Expression } from './expression';
import { validateJsonSchema } from './validation';
import { appPackageSchema } from './package-schema';
import type { ComputedFieldSpec } from './computed-fields';

export type FieldType = 'text' | 'number' | 'boolean' | 'timestamp' | 'json';

export type CollectionSpec = {
  id: string;
  fields: Record<string, { type: FieldType; required?: boolean; indexed?: boolean }>;
};

export type ViewSpec = {
  id: string;
  query: string;
  mode: 'list' | 'board' | 'table' | 'calendar' | 'timeline' | 'chart';
  fields: string[];
  groupBy?: string;
  layout?: Record<string, unknown>;
};

export type RuleSpec = {
  id: string;
  trigger: {
    kind: 'operation' | 'schedule' | 'query_transition';
    query?: string;
    transition?: 'enter' | 'leave' | 'change';
  };
  when?: Expression;
  effect: { kind: 'propose_operation'; operation: string };
  mode: 'suggest' | 'automatic';
  maxRunsPerEvent: number;
};

export type AppPackageV2 = {
  schemaVersion: 'wonder.app-package.v2';
  id: string;
  version: string;
  collections: Record<string, CollectionSpec>;
  queries: Record<string, { from: string; where?: QueryPredicate; orderBy?: QuerySort[]; limit?: number }>;
  views: Record<string, ViewSpec>;
  computedFields?: ComputedFieldSpec[];
  rules: RuleSpec[];
  capabilities: string[];
  acceptanceTests: string[];
};

export type PackageValidation = { valid: true; package: AppPackageV2 } | { valid: false; errors: string[] };

function text(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function hasExecutableCode(value: unknown): boolean {
  if (Array.isArray(value)) return value.some(hasExecutableCode);
  if (!value || typeof value !== 'object') return false;
  return Object.entries(value as Record<string, unknown>).some(([key, child]) => key === 'code' || key === 'javascript' || key === 'script' || hasExecutableCode(child));
}

export function validateAppPackage(input: unknown): PackageValidation {
  const errors: string[] = [];
  if (!input || typeof input !== 'object') return { valid: false, errors: ['package must be an object'] };
  const schemaResult = validateJsonSchema(appPackageSchema, input);
  if (!schemaResult.valid) errors.push(...schemaResult.errors.map((error) => `schema:${error}`));
  const value = input as Partial<AppPackageV2>;
  if (value.schemaVersion !== 'wonder.app-package.v2') errors.push('schemaVersion must be wonder.app-package.v2');
  if (!text(value.id)) errors.push('id is required');
  if (!text(value.version)) errors.push('version is required');
  if (!value.collections || typeof value.collections !== 'object') errors.push('collections are required');
  if (!value.queries || typeof value.queries !== 'object') errors.push('queries are required');
  if (!value.views || typeof value.views !== 'object') errors.push('views are required');
  if (!Array.isArray(value.rules)) errors.push('rules must be an array');
  if (value.computedFields !== undefined && !Array.isArray(value.computedFields)) errors.push('computedFields must be an array');
  if (!Array.isArray(value.capabilities)) errors.push('capabilities must be an array');
  if (!Array.isArray(value.acceptanceTests)) errors.push('acceptanceTests must be an array');
  if (hasExecutableCode(input)) errors.push('executable package code is forbidden');

  for (const [id, collection] of Object.entries(value.collections ?? {})) {
    if (!text(collection?.id) || collection.id !== id) errors.push(`collection ${id} must have matching id`);
    if (!collection?.fields || typeof collection.fields !== 'object') errors.push(`collection ${id} fields are required`);
  }
  for (const [id, query] of Object.entries(value.queries ?? {})) {
    if (!text(query?.from)) errors.push(`query ${id} must declare from`);
  }
  for (const [id, view] of Object.entries(value.views ?? {})) {
    if (!text(view?.id) || view.id !== id) errors.push(`view ${id} must have matching id`);
    if (!text(view?.query)) errors.push(`view ${id} must reference a query`);
  }
  for (const rule of value.rules ?? []) {
    if (!text(rule?.id)) errors.push('rule id is required');
    if (!rule?.trigger?.kind) errors.push(`rule ${rule?.id ?? '<unknown>'} trigger is required`);
    if (!rule?.effect?.operation) errors.push(`rule ${rule?.id ?? '<unknown>'} operation is required`);
    if (!Number.isInteger(rule?.maxRunsPerEvent) || (rule?.maxRunsPerEvent ?? 0) < 1) errors.push(`rule ${rule?.id ?? '<unknown>'} maxRunsPerEvent must be positive`);
  }
  for (const field of value.computedFields ?? []) {
    if (!text(field?.id)) errors.push('computed field id is required');
    if (!text(field?.collection)) errors.push(`computed field ${field?.id ?? '<unknown>'} collection is required`);
    if (!Array.isArray(field?.dependsOn)) errors.push(`computed field ${field?.id ?? '<unknown>'} dependsOn must be an array`);
    if (field?.expression === undefined) errors.push(`computed field ${field?.id ?? '<unknown>'} expression is required`);
  }

  return errors.length ? { valid: false, errors } : { valid: true, package: value as AppPackageV2 };
}
