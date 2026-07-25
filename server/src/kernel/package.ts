import { validateJsonSchema } from './validation';
import { appPackageSchema } from './package-schema';
import type { 
  AppPackageV2,
  CollectionSpec,
  ComputedFieldSpec,
  FieldType,
  OperationTemplate,
  PackagePresentationSpec,
  PackageSurfaceSpec,
  PackageValidation,
  RuleSpec,
  ViewSpec,
} from '@/packages/shared/contracts/package';
import type { QueryPredicate, QuerySort } from '@/packages/shared/contracts/query';

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

export { AppPackageV2, CollectionSpec, ComputedFieldSpec, FieldType, OperationTemplate, PackagePresentationSpec, PackageSurfaceSpec, PackageValidation, RuleSpec, ViewSpec };
export type { QueryPredicate, QuerySort } from '@/packages/shared/contracts/query';

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
      const item = surface as unknown as {
        id?: unknown;
        label?: unknown;
        collections?: unknown;
      };
      if (!text(item?.id)) errors.push('presentation surface id is required');
      if (!text(item?.label)) errors.push(`presentation surface ${item?.id ?? '<unknown>'} label is required`);
      if (!Array.isArray(item?.collections)) errors.push(`presentation surface ${item?.id ?? '<unknown>'} collections must be an array`);
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
    const item = field as unknown as ComputedFieldSpec & { id?: unknown; collection?: unknown };
    if (!text(item?.id)) errors.push('computed field id is required');
    if (!text(item?.collection)) errors.push(`computed field ${item?.id ?? '<unknown>'} collection is required`);
    if (!Array.isArray(item?.dependsOn)) errors.push(`computed field ${item?.id ?? '<unknown>'} dependsOn must be an array`);
    if (item?.expression === undefined) errors.push(`computed field ${item?.id ?? '<unknown>'} expression is required`);
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

export function operationTemplateName(input: OperationTemplate): string {
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
