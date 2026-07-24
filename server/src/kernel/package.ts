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

export type PackageSurfaceSpec = {
  id: string;
  label: string;
  icon?: string;
  imageUrl?: string;
  views?: string[];
  collections: string[];
};

export type PackagePresentationSpec = {
  label: string;
  homeSurface?: string;
  surfaces: PackageSurfaceSpec[];
  visualIdentity?: Record<string, unknown>;
  dashboardBlocks?: Record<string, unknown>[];
  render?: Record<string, unknown>;
  richDetailSchema?: string;
  providerTemplateFields?: Record<string, unknown>;
  sourceSchemaVersion?: string;
};

export type RuleSpec = {
  id: string;
  trigger: {
    kind: 'operation' | 'schedule' | 'query_transition';
    query?: string;
    transition?: 'enter' | 'leave' | 'change';
  };
  when?: Expression;
  effect: { kind: 'propose_operation'; operation: string | OperationTemplate };
  mode: 'suggest' | 'automatic';
  maxRunsPerEvent: number;
};

export type OperationTemplate = Readonly<
  | { kind: 'custom'; tool: string }
  | { kind: 'create_record'; domain?: string; collection: string; recordId?: string; properties?: Record<string, unknown> }
  | { kind: 'update_record'; domain?: string; collection?: string; recordId: string; expectedRevision?: number; changes: Record<string, unknown> }
  | { kind: 'archive_record'; domain?: string; collection?: string; recordId: string; expectedRevision?: number }
  | { kind: 'restore_record'; domain?: string; collection?: string; recordId: string; expectedRevision?: number }
>;

export type AppPackageV2 = {
  schemaVersion: 'wonder.app-package.v2';
  id: string;
  version: string;
  collections: Record<string, CollectionSpec>;
  queries: Record<string, { from: string; where?: QueryPredicate; orderBy?: QuerySort[]; limit?: number }>;
  views: Record<string, ViewSpec>;
  presentation?: PackagePresentationSpec;
  computedFields?: ComputedFieldSpec[];
  rules: RuleSpec[];
  capabilities: string[];
  acceptanceTests: string[];
};

export type PackageValidation = { valid: true; package: AppPackageV2 } | { valid: false; errors: string[] };

function text(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function name(value: unknown): value is string {
  return typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9_.:-]*$/.test(value);
}

function identifier(value: unknown): value is string {
  return typeof value === 'string' && /^[A-Za-z_][A-Za-z0-9_.:-]*$/.test(value);
}

function object(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
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
  if (value.presentation !== undefined && !object(value.presentation)) errors.push('presentation must be an object');
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
    else if (query.from !== 'records' && !value.collections?.[query.from]) {
      errors.push(`query ${id} references missing collection ${query.from}`);
    }
  }
  for (const [id, view] of Object.entries(value.views ?? {})) {
    if (!text(view?.id) || view.id !== id) errors.push(`view ${id} must have matching id`);
    if (!text(view?.query)) errors.push(`view ${id} must reference a query`);
    else if (!value.queries?.[view.query]) errors.push(`view ${id} references missing query ${view.query}`);
  }
  const presentation = value.presentation as Partial<PackagePresentationSpec> | undefined;
  if (presentation) {
    if (!text(presentation.label)) errors.push('presentation label is required');
    if (!Array.isArray(presentation.surfaces)) errors.push('presentation surfaces must be an array');
    for (const surface of presentation.surfaces ?? []) {
      if (!text(surface?.id)) errors.push('presentation surface id is required');
      if (!text(surface?.label)) errors.push(`presentation surface ${surface?.id ?? '<unknown>'} label is required`);
      if (!Array.isArray(surface?.collections)) errors.push(`presentation surface ${surface?.id ?? '<unknown>'} collections must be an array`);
    }
  }
  for (const rule of value.rules ?? []) {
    if (!identifier(rule?.id)) errors.push('rule id is required');
    if (!rule?.trigger?.kind) errors.push(`rule ${rule?.id ?? '<unknown>'} trigger is required`);
    if (rule?.trigger?.kind === 'query_transition' && (!text(rule.trigger.query) || !value.queries?.[rule.trigger.query])) {
      errors.push(`rule ${rule?.id ?? '<unknown>'} references missing query ${rule?.trigger?.query ?? '<missing>'}`);
    }
    if (!validateOperationTemplate(rule?.effect?.operation, value).valid) {
      errors.push(`rule ${rule?.id ?? '<unknown>'} operation is required`);
    }
    if (!Number.isInteger(rule?.maxRunsPerEvent) || (rule?.maxRunsPerEvent ?? 0) < 1) errors.push(`rule ${rule?.id ?? '<unknown>'} maxRunsPerEvent must be positive`);
    else if ((rule.maxRunsPerEvent ?? 0) > 64) errors.push(`rule ${rule?.id ?? '<unknown>'} maxRunsPerEvent must be <= 64`);
  }
  for (const field of value.computedFields ?? []) {
    if (!text(field?.id)) errors.push('computed field id is required');
    if (!text(field?.collection)) errors.push(`computed field ${field?.id ?? '<unknown>'} collection is required`);
    if (!Array.isArray(field?.dependsOn)) errors.push(`computed field ${field?.id ?? '<unknown>'} dependsOn must be an array`);
    if (field?.expression === undefined) errors.push(`computed field ${field?.id ?? '<unknown>'} expression is required`);
  }
  for (const capability of value.capabilities ?? []) {
    if (!name(capability)) errors.push(`capability invalid:${String(capability)}`);
  }
  for (const acceptanceTest of value.acceptanceTests ?? []) {
    if (!name(acceptanceTest)) errors.push(`acceptance test invalid:${String(acceptanceTest)}`);
  }

  return errors.length ? { valid: false, errors } : { valid: true, package: value as AppPackageV2 };
}

export function normalizeOperationTemplate(input: string | OperationTemplate): OperationTemplate {
  return typeof input === 'string' ? { kind: 'custom', tool: input } : input;
}

export function operationTemplateName(input: string | OperationTemplate): string {
  const template = normalizeOperationTemplate(input);
  return template.kind === 'custom' ? template.tool : template.kind;
}

function validateOperationTemplate(input: unknown, pkg: Partial<AppPackageV2>): { valid: true } | { valid: false } {
  if (identifier(input)) return { valid: true };
  if (!object(input) || typeof input.kind !== 'string') return { valid: false };
  if (input.kind === 'custom') return identifier(input.tool) ? { valid: true } : { valid: false };
  if (input.domain !== undefined && !text(input.domain)) return { valid: false };
  const expectedRevision = input.expectedRevision;
  if (expectedRevision !== undefined && (!Number.isInteger(expectedRevision) || typeof expectedRevision !== 'number' || expectedRevision < 0)) return { valid: false };
  if (input.kind === 'create_record') {
    if (!text(input.collection) || !pkg.collections?.[input.collection]) return { valid: false };
    if (input.recordId !== undefined && !text(input.recordId)) return { valid: false };
    if (input.properties !== undefined && !object(input.properties)) return { valid: false };
    return { valid: true };
  }
  if (input.kind === 'update_record') {
    if (!text(input.recordId) || !object(input.changes)) return { valid: false };
    if (input.collection !== undefined && !text(input.collection)) return { valid: false };
    return { valid: true };
  }
  if (input.kind === 'archive_record' || input.kind === 'restore_record') {
    if (!text(input.recordId)) return { valid: false };
    if (input.collection !== undefined && !text(input.collection)) return { valid: false };
    return { valid: true };
  }
  return { valid: false };
}
