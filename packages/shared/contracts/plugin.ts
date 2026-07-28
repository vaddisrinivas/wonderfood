import { sha256Canonical } from './canonical-json';

export const PLUGIN_SCHEMA_VERSION = 'utopia.plugin.v1' as const;
export const PLUGIN_LOCK_SCHEMA_VERSION = 'utopia.plugin-lock.v1' as const;

export type PluginClass = 'runtime' | 'build' | 'server' | 'specialized';

export type PluginRuntimeTarget = string;

export type PluginFallback = {
  kind: 'render';
  text: string;
  widget?: string;
};

export type PluginManifest = {
  schemaVersion: typeof PLUGIN_SCHEMA_VERSION;
  id: string;
  version: string;
  pluginClass: PluginClass;
  runtimeTargets: PluginRuntimeTarget[];
  provides: {
    widgets: string[];
    tools: string[];
    dataSources: string[];
    backgroundTasks: string[];
  };
  permissions: string[];
  packageDependencies: string[];
  fallback?: PluginFallback;
};

export type PluginLock = {
  schemaVersion: typeof PLUGIN_LOCK_SCHEMA_VERSION;
  id: string;
  version: string;
  checksum: string;
  capabilities: string[];
};

export type PluginCompatibilityStatus =
  | 'compatible'
  | 'compatible_with_fallback'
  | 'requires_new_build'
  | 'unsupported';

export type PluginCompatibilityRequest = {
  runtimeTarget: PluginRuntimeTarget;
  requiredCapabilities: readonly string[];
  optionalCapabilities?: readonly string[];
  serverAvailable?: boolean;
  allowFallback?: boolean;
};

export type PluginCompatibilityResult = {
  status: PluginCompatibilityStatus;
  reason: string;
  missingCapabilities: string[];
  lock: PluginLock;
  manifest: PluginManifest;
  fallback?: PluginFallback;
};

export type PluginValidation =
  | { valid: true; manifest: PluginManifest; lock: PluginLock }
  | { valid: false; errors: string[] };

export function collectPluginValidationErrors(input: unknown): string[] {
  if (!isRecord(input)) return ['plugin must be an object'];

  const errors: string[] = [];
  const raw = input as Partial<PluginManifest> & { pluginClass?: unknown };

  if (raw.schemaVersion !== PLUGIN_SCHEMA_VERSION) {
    errors.push(`schemaVersion must be ${PLUGIN_SCHEMA_VERSION}`);
  }
  if (!isNonEmptyString(raw.id)) {
    errors.push('id is required');
  }
  if (!isNonEmptyString(raw.version)) {
    errors.push('version is required');
  }
  if (!isPluginClass(raw.pluginClass)) {
    errors.push('pluginClass must be runtime|build|server|specialized');
  }
  if (!isStringArray(raw.runtimeTargets) || raw.runtimeTargets.length === 0) {
    errors.push('runtimeTargets must be a non-empty array');
  }
  if (!isPluginProvides(raw.provides)) {
    errors.push('provides must include widgets, tools, dataSources, and backgroundTasks');
  }
  if (!isStringArray(raw.permissions)) {
    errors.push('permissions must be an array');
  }
  if (!isStringArray(raw.packageDependencies)) {
    errors.push('packageDependencies must be an array');
  }
  if (raw.fallback !== undefined) {
    if (!isPluginFallback(raw.fallback)) {
      errors.push('fallback must be a render fallback object');
    }
  }

  if (isPluginProvides(raw.provides)) {
    const rawCapabilities = [
      ...raw.provides.widgets.map((widget) => `widget:${widget}`),
      ...raw.provides.tools.map((tool) => `tool:${tool}`),
      ...raw.provides.dataSources.map((dataSource) => `data-source:${dataSource}`),
      ...raw.provides.backgroundTasks.map((task) => `background-task:${task}`),
      ...((isStringArray(raw.permissions) ? raw.permissions : [])).map((permission) => `permission:${permission}`),
    ];
    const duplicateCapabilities = findDuplicates(rawCapabilities);
    if (duplicateCapabilities.length) {
      errors.push(`duplicate plugin capabilities: ${duplicateCapabilities.join(', ')}`);
    }
  }

  for (const tool of isPluginProvides(raw.provides) ? raw.provides.tools : []) {
    if (typeof tool === 'string' && /^record[.:]/.test(tool.trim())) {
      errors.push('plugins cannot write records directly');
      break;
    }
  }

  return errors;
}

export function validatePluginManifest(input: unknown): PluginValidation {
  const errors = collectPluginValidationErrors(input);
  if (errors.length) return { valid: false, errors };
  const manifest = input as PluginManifest;
  return { valid: true, manifest, lock: lockPluginManifest(manifest) };
}

export function pluginCapabilities(manifest: PluginManifest): string[] {
  const entries = [
    ...manifest.provides.widgets.map((widget) => `widget:${widget}`),
    ...manifest.provides.tools.map((tool) => `tool:${tool}`),
    ...manifest.provides.dataSources.map((dataSource) => `data-source:${dataSource}`),
    ...manifest.provides.backgroundTasks.map((task) => `background-task:${task}`),
    ...manifest.permissions.map((permission) => `permission:${permission}`),
  ];
  return [...new Set(entries)].sort();
}

export function lockPluginManifest(manifest: PluginManifest): PluginLock {
  const checksum = sha256Canonical({
    schemaVersion: manifest.schemaVersion,
    id: manifest.id,
    version: manifest.version,
    pluginClass: manifest.pluginClass,
    runtimeTargets: manifest.runtimeTargets,
    provides: manifest.provides,
    permissions: manifest.permissions,
    packageDependencies: manifest.packageDependencies,
    fallback: manifest.fallback,
  });
  return {
    schemaVersion: PLUGIN_LOCK_SCHEMA_VERSION,
    id: manifest.id,
    version: manifest.version,
    checksum,
    capabilities: pluginCapabilities(manifest),
  };
}

export function resolvePluginCompatibility(
  manifest: PluginManifest,
  request: PluginCompatibilityRequest,
): PluginCompatibilityResult {
  const lock = lockPluginManifest(manifest);
  const capabilities = new Set(lock.capabilities);
  const missingCapabilities = request.requiredCapabilities.filter((capability) => !capabilities.has(capability));
  const fallback = manifest.fallback;
  const canFallback = Boolean(fallback && (request.allowFallback ?? true));

  if (manifest.pluginClass === 'build') {
    return {
      status: 'requires_new_build',
      reason: 'plugin requires a new native build',
      missingCapabilities,
      lock,
      manifest,
      ...(fallback ? { fallback } : {}),
    };
  }

  if (manifest.pluginClass === 'server' && request.serverAvailable !== true) {
    return canFallback
      ? {
        status: 'compatible_with_fallback',
        reason: 'server plugin is unavailable in this runtime, using declared fallback',
        missingCapabilities,
        lock,
        manifest,
        fallback,
      }
      : {
        status: 'unsupported',
        reason: 'server plugin requires a trusted service boundary',
        missingCapabilities,
        lock,
        manifest,
      };
  }

  if (!manifest.runtimeTargets.includes(request.runtimeTarget)) {
    return canFallback
      ? {
        status: 'compatible_with_fallback',
        reason: `runtime target ${request.runtimeTarget} is not declared, using fallback`,
        missingCapabilities,
        lock,
        manifest,
        fallback,
      }
      : {
        status: 'unsupported',
        reason: `runtime target ${request.runtimeTarget} is not supported`,
        missingCapabilities,
        lock,
        manifest,
      };
  }

  if (missingCapabilities.length > 0) {
    return canFallback
      ? {
        status: 'compatible_with_fallback',
        reason: `missing capabilities: ${missingCapabilities.join(', ')}`,
        missingCapabilities,
        lock,
        manifest,
        fallback,
      }
      : {
        status: 'unsupported',
        reason: `missing capabilities: ${missingCapabilities.join(', ')}`,
        missingCapabilities,
        lock,
        manifest,
      };
  }

  return {
    status: 'compatible',
    reason: 'plugin and capabilities are compatible with the current runtime',
    missingCapabilities: [],
    lock,
    manifest,
  };
}

function isPluginProvides(value: unknown): value is PluginManifest['provides'] {
  if (!isRecord(value)) return false;
  return (
    isStringArray(value.widgets)
    && isStringArray(value.tools)
    && isStringArray(value.dataSources)
    && isStringArray(value.backgroundTasks)
  );
}

function isPluginFallback(value: unknown): value is PluginFallback {
  return isRecord(value)
    && value.kind === 'render'
    && isNonEmptyString(value.text)
    && (value.widget === undefined || isNonEmptyString(value.widget));
}

function isPluginClass(value: unknown): value is PluginClass {
  return value === 'runtime' || value === 'build' || value === 'server' || value === 'specialized';
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => isNonEmptyString(item));
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function findDuplicates(values: readonly string[]): string[] {
  const seen = new Set<string>();
  const duplicates = new Set<string>();
  for (const value of values) {
    if (seen.has(value)) {
      duplicates.add(value);
    } else {
      seen.add(value);
    }
  }
  return [...duplicates];
}
