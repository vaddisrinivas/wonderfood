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

const UI_COMPONENT_KINDS = new Set(['recordList', 'metric', 'action', 'text']);
const UI_ACTION_KINDS = new Set(['open_url', 'propose']);
const UI_ACTION_TOOL_PATTERN = /^[A-Za-z_][A-Za-z0-9_.:-]*$/;

function isTextArray(value: unknown, path: string): string[] {
  if (!Array.isArray(value)) throw new Error(`${path} must be an array`);
  const out: string[] = [];
  for (const [index, item] of value.entries()) {
    if (!text(item)) throw new Error(`Expected non-empty string at ${path}[${index}]`);
    out.push(item);
  }
  return out;
}

function isUiAction(value: unknown, path: string): { command: string; tool: string } {
  if (!object(value)) throw new Error(`${path} must be an object`);
  const action = value as Record<string, unknown>;
  if (!text(action.kind) || !UI_ACTION_KINDS.has(action.kind)) throw new Error(`${path}.kind must be one of open_url|propose`);
  if (action.kind === 'open_url' && !text(action.url)) throw new Error(`${path}.url required for open_url actions`);
  if (action.kind === 'propose' && !text(action.tool) && !text(action.command)) {
    throw new Error(`${path}.tool or ${path}.command required for propose actions`);
  }
  const command = text(action.command) ? action.command : text(action.tool) ? String(action.tool) : '';
  return {
    command,
    tool: action.kind === 'propose' && text(action.tool) ? String(action.tool) : command,
  };
}

function isUiComponent(value: unknown, path: string, packageCollections: Record<string, unknown>, packageViews: Record<string, unknown>): { hasQuery: boolean } {
  if (!object(value)) throw new Error(`${path} must be an object`);
  const component = value as Record<string, unknown>;
  if (!text(component.kind) || !UI_COMPONENT_KINDS.has(component.kind)) throw new Error(`${path}.kind is invalid`);
  if (component.kind === 'action' && !text(component.id)) throw new Error(`${path}.id required for action components`);

  if (component.view !== undefined && !text(component.view)) throw new Error(`${path}.view must be text`);
  if (typeof component.view === 'string' && component.view && !Object.hasOwn(packageViews, component.view)) {
    throw new Error(`${path}.view must reference an existing view`);
  }

  if (component.tone !== undefined && !['neutral', 'moss', 'amber', 'plum', 'blue'].includes(String(component.tone))) {
    throw new Error(`${path}.tone is invalid`);
  }
  if (component.action !== undefined) {
    const { tool, command } = isUiAction(component.action, `${path}.action`);
    if (command && !UI_ACTION_TOOL_PATTERN.test(command)) {
      throw new Error(`${path}.action.command invalid`);
    }
    if (tool && !UI_ACTION_TOOL_PATTERN.test(tool)) {
      throw new Error(`${path}.action.tool invalid`);
    }
  }

  const query = component.query;
  if (query === undefined) {
    return { hasQuery: false };
  }
  if (!object(query)) throw new Error(`${path}.query must be an object`);
  const rawQuery = query as Record<string, unknown>;
  if (rawQuery.collections !== undefined) {
    const collections = isTextArray(rawQuery.collections, `${path}.query.collections`);
    for (const collection of collections) {
      if (!Object.hasOwn(packageCollections, collection)) throw new Error(`${path}.query.collections references missing collection ${collection}`);
    }
  }
  if (rawQuery.limit !== undefined && (!Number.isInteger(rawQuery.limit) || rawQuery.limit < 1 || rawQuery.limit > 20)) {
    throw new Error(`${path}.query.limit must be 1..20`);
  }
  if (rawQuery.match !== undefined && !text(rawQuery.match)) throw new Error(`${path}.query.match must be text`);
  if (text(rawQuery.match)) {
    try {
      new RegExp(rawQuery.match as string);
    } catch {
      throw new Error(`${path}.query.match is invalid regular expression`);
    }
  }
  return { hasQuery: true };
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
    const surfaceIds = new Set<string>();
    for (const surface of presentation.surfaces ?? []) {
      const item = surface as unknown as {
        id?: unknown;
        label?: unknown;
        collections?: unknown;
      };
      if (!text(item?.id)) errors.push('presentation surface id is required');
      else if (surfaceIds.has(item.id)) errors.push(`presentation surface ${item.id} is duplicated`);
      else surfaceIds.add(item.id);
      if (!text(item?.label)) errors.push(`presentation surface ${item?.id ?? '<unknown>'} label is required`);
      if (!Array.isArray(item?.collections)) {
        errors.push(`presentation surface ${item?.id ?? '<unknown>'} collections must be an array`);
      } else {
        for (const collection of item.collections) {
          if (!text(collection)) errors.push(`presentation surface ${item?.id ?? '<unknown>'} collection id is invalid`);
          else if (!value.collections?.[collection]) errors.push(`presentation surface ${item?.id ?? '<unknown>'} references missing collection ${collection}`);
        }
      }
    }
    if (presentation.homeSurface !== undefined && (!text(presentation.homeSurface) || !surfaceIds.has(presentation.homeSurface))) {
      errors.push(`presentation homeSurface references missing surface ${String(presentation.homeSurface)}`);
    }

    const ui = presentation.ui;
    if (ui !== undefined) {
      if (!object(ui)) {
        errors.push('presentation ui must be an object');
      } else {
        if (ui.schemaVersion !== undefined && ui.schemaVersion !== 'wonder.ui.v1') {
          errors.push('presentation ui.schemaVersion must be wonder.ui.v1');
        }
        if (ui.openUrlAllowlist !== undefined && !Array.isArray(ui.openUrlAllowlist)) {
          errors.push('presentation ui.openUrlAllowlist must be an array');
        }
        if (Array.isArray(ui.openUrlAllowlist)) {
          for (const [index, allowlistItem] of ui.openUrlAllowlist.entries()) {
            if (!text(allowlistItem)) errors.push(`presentation ui.openUrlAllowlist[${index}] must be a non-empty string`);
          }
        }
        const screens = ui.screens;
        if (screens !== undefined && !object(screens)) {
          errors.push('presentation ui.screens must be an object');
        }
        const screenIds = new Set<string>();
        if (object(screens)) {
          for (const [screenId, screenValue] of Object.entries(screens)) {
            if (!text(screenId)) {
              errors.push('presentation ui screen id must be non-empty');
              continue;
            }
            if (screenIds.has(screenId)) {
              errors.push(`presentation ui screen ${screenId} is duplicated`);
            } else {
              screenIds.add(screenId);
            }
            if (!object(screenValue)) {
              errors.push(`presentation ui screen ${screenId} must be an object`);
            } else {
              if (screenValue.components !== undefined && !Array.isArray(screenValue.components)) {
                errors.push(`presentation ui screen ${screenId}.components must be an array`);
              }
              if (Array.isArray(screenValue.components)) {
                for (const [index, rawComponent] of screenValue.components.entries()) {
                  try {
                    isUiComponent(rawComponent, `presentation.ui.screens.${screenId}.components[${index}]`, value.collections ?? {}, value.views ?? {});
                  } catch (error) {
                    if (error instanceof Error) {
                      errors.push(error.message);
                    } else {
                      errors.push(`presentation.ui.screens.${screenId}.components[${index}] invalid`);
                    }
                  }
                }
              }
            }
          }
        }
        if (ui.defaultScreen !== undefined && !text(ui.defaultScreen)) {
          errors.push('presentation ui.defaultScreen must be a non-empty string');
        }
        if (ui.defaultScreen !== undefined) {
          if (!object(screens)) {
            errors.push('presentation ui.defaultScreen requires screens');
          } else if (!Object.hasOwn(screens, ui.defaultScreen)) {
            errors.push(`presentation ui.defaultScreen references missing screen ${String(ui.defaultScreen)}`);
          }
        }

        if (ui.components !== undefined && !Array.isArray(ui.components)) {
          errors.push('presentation ui.components must be an array');
        }
        if (Array.isArray(ui.components)) {
          for (const [index, rawComponent] of ui.components.entries()) {
            try {
              isUiComponent(rawComponent, `presentation.ui.components[${index}]`, value.collections ?? {}, value.views ?? {});
            } catch (error) {
              if (error instanceof Error) {
                errors.push(error.message);
              } else {
                errors.push(`presentation.ui.components[${index}] invalid`);
              }
            }
          }
        }

        if (ui.components === undefined && !screens) {
          errors.push('presentation ui requires components or screens');
        }
      }
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
