import catalogJson from '@/packages/domain-config/domain-catalog.v1.json';
import foodManifestJson from '@/packages/domain-config/domains/food.v1.json';
import healthManifestJson from '@/packages/domain-config/domains/health.v1.json';
import plantsManifestJson from '@/packages/domain-config/domains/plants.v1.json';

export type CatalogSchemaVersion = 'lifeos.domain-catalog.v1';
export type DomainSchemaVersion = 'lifeos.domain.v1';
export type DomainStatus = 'active' | 'ready' | 'preview' | 'disabled';

export type DomainId = string;
export type CollectionId = string;

export type Surface = {
  id: string;
  label: string;
  views?: string[];
  collections: string[];
};

export type DashboardBlockKind = 'spotlight' | 'metric' | 'list' | 'action';
export type DashboardBlockTone = 'neutral' | 'moss' | 'amber' | 'plum' | 'blue';

export type DashboardBlock = {
  id: string;
  surface: string;
  title: string;
  subtitle?: string;
  kind: DashboardBlockKind;
  tone: DashboardBlockTone;
  query: {
    collections?: string[];
    match?: string;
    limit?: number;
  };
  href: string;
};

export type ManifestRelation = {
  from: string;
  to: string;
  name: string;
};

export interface DomainManifest {
  schema_version: DomainSchemaVersion;
  id: DomainId;
  label: string;
  home_surface?: string;
  surfaces: Surface[];
  collections: CollectionId[];
  relations: ManifestRelation[];
  skills: string[];
  workflows: string[];
  data_homes: string[];
  dashboard_blocks?: DashboardBlock[];
  rich_detail_schema?: string;
  provider_template_fields?: {
    required?: string[];
    rich_detail_json?: string[];
    relations_json?: string[];
  };
  mcp: {
    resources: string[];
    tools: string[];
  };
}

export interface DomainCatalogEntry {
  id: DomainId;
  label: string;
  icon: string;
  status: DomainStatus;
  manifest: string;
  skill: string;
  summary: string;
}

export interface DomainCatalog {
  schema_version: CatalogSchemaVersion;
  shell_version: string;
  active_domain_id: DomainId;
  shell: {
    tabs: string[];
    global_actions: string[];
    action_policy: string;
  };
  domains: DomainCatalogEntry[];
}

export interface ParsedCatalog {
  catalog: DomainCatalog;
  activeDomainId: DomainId;
  activeDomain?: DomainCatalogEntry;
  activeManifest: DomainManifest;
  domainsById: Record<DomainId, DomainCatalogEntry>;
}

let parsedCatalogCache: ParsedCatalog | null = null;
let activeDomainOverride: string | null = null;

export function setActiveDomainOverride(domainId: string | null): void {
  activeDomainOverride = domainId?.trim() || null;
  parsedCatalogCache = null;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function assertCondition(condition: boolean, message: string): void {
  if (!condition) {
    throw new Error(`[domain-catalog] ${message}`);
  }
}

function parseString(value: unknown, path: string): string {
  assertCondition(typeof value === 'string' && value.trim().length > 0, `Expected non-empty string at ${path}`);
  return value as string;
}

function parseStringArray(value: unknown, path: string): string[] {
  assertCondition(Array.isArray(value), `Expected array at ${path}`);
  const values = value as unknown[];
  for (const [index, item] of values.entries()) {
    assertCondition(typeof item === 'string', `Expected string item at ${path}[${index}]`);
  }
  return values as string[];
}

function parseObjectArray(value: unknown, path: string): Record<string, unknown>[] {
  assertCondition(Array.isArray(value), `Expected array at ${path}`);
  const values = value as unknown[];
  for (const [index, item] of values.entries()) {
    assertCondition(isObject(item), `Expected object at ${path}[${index}]`);
  }
  return values as Record<string, unknown>[];
}

function parseOptionalStringArray(value: unknown, path: string): string[] | undefined {
  if (value === undefined) return undefined;
  return parseStringArray(value, path);
}

function parseDashboardBlocks(value: unknown, path: string): DashboardBlock[] | undefined {
  if (value === undefined) return undefined;
  const blocks = parseObjectArray(value, path);
  return blocks.map((block, index) => {
    const kind = block.kind;
    const tone = block.tone;
    const query = isObject(block.query) ? block.query : {};
    assertCondition(
      kind === 'spotlight' || kind === 'metric' || kind === 'list' || kind === 'action',
      `Invalid kind at ${path}[${index}].kind`
    );
    assertCondition(
      tone === 'neutral' || tone === 'moss' || tone === 'amber' || tone === 'plum' || tone === 'blue',
      `Invalid tone at ${path}[${index}].tone`
    );
    const parsedKind = kind as DashboardBlockKind;
    const parsedTone = tone as DashboardBlockTone;
    const limit = typeof query.limit === 'number' && Number.isFinite(query.limit)
      ? Math.max(0, Math.min(20, Math.floor(query.limit)))
      : undefined;
    return {
      id: parseString(block.id, `${path}[${index}].id`),
      surface: parseString(block.surface, `${path}[${index}].surface`),
      title: parseString(block.title, `${path}[${index}].title`),
      subtitle: typeof block.subtitle === 'string' ? block.subtitle : undefined,
      kind: parsedKind,
      tone: parsedTone,
      query: {
        collections: parseOptionalStringArray(query.collections, `${path}[${index}].query.collections`),
        match: typeof query.match === 'string' ? query.match : undefined,
        limit,
      },
      href: typeof block.href === 'string' && block.href.trim() ? block.href : '/config',
    };
  });
}

function parseDomainManifest(value: unknown, path: string): DomainManifest {
  assertCondition(isObject(value), `Expected object at ${path}`);
  const raw = value as Record<string, unknown>;
  assertCondition(raw.schema_version === 'lifeos.domain.v1', `Invalid schema_version at ${path}`);

  const surfaces = parseObjectArray(raw.surfaces, `${path}.surfaces`);
  const parsedSurfaces = surfaces.map((surface, index) => {
    assertCondition(isObject(surface), `Expected object at ${path}.surfaces[${index}]`);
    const s = surface as Record<string, unknown>;
    return {
      id: parseString(s.id, `${path}.surfaces[${index}].id`),
      label: parseString(s.label, `${path}.surfaces[${index}].label`),
      views: Array.isArray(s.views) ? (s.views as string[]) : undefined,
      collections: parseStringArray(s.collections, `${path}.surfaces[${index}].collections`),
    };
  });

  const relations = parseObjectArray(raw.relations, `${path}.relations`);
  const parsedRelations = relations.map((relation, index) => {
    assertCondition(isObject(relation), `Expected object at ${path}.relations[${index}]`);
    const rel = relation as Record<string, unknown>;
    return {
      from: parseString(rel.from, `${path}.relations[${index}].from`),
      to: parseString(rel.to, `${path}.relations[${index}].to`),
      name: parseString(rel.name, `${path}.relations[${index}].name`),
    };
  });

  const mcp = raw.mcp as Record<string, unknown>;
  assertCondition(isObject(mcp), `Expected mcp object at ${path}.mcp`);
  const providerTemplateFields = raw.provider_template_fields;
  const parsedProviderTemplateFields = isObject(providerTemplateFields) ? {
    required: Array.isArray(providerTemplateFields.required) ? (providerTemplateFields.required as string[]) : undefined,
    rich_detail_json: Array.isArray(providerTemplateFields.rich_detail_json) ? (providerTemplateFields.rich_detail_json as string[]) : undefined,
    relations_json: Array.isArray(providerTemplateFields.relations_json) ? (providerTemplateFields.relations_json as string[]) : undefined,
  } : undefined;

  return {
    schema_version: 'lifeos.domain.v1',
    id: parseString(raw.id, `${path}.id`),
    label: parseString(raw.label, `${path}.label`),
    home_surface: typeof raw.home_surface === 'string' ? raw.home_surface : undefined,
    surfaces: parsedSurfaces,
    collections: parseStringArray(raw.collections, `${path}.collections`),
    relations: parsedRelations,
    skills: parseStringArray(raw.skills, `${path}.skills`),
    workflows: parseStringArray(raw.workflows, `${path}.workflows`),
    data_homes: parseStringArray(raw.data_homes, `${path}.data_homes`),
    dashboard_blocks: parseDashboardBlocks(raw.dashboard_blocks, `${path}.dashboard_blocks`),
    rich_detail_schema: typeof raw.rich_detail_schema === 'string' ? raw.rich_detail_schema : undefined,
    provider_template_fields: parsedProviderTemplateFields,
    mcp: {
      resources: parseStringArray(mcp.resources, `${path}.mcp.resources`),
      tools: parseStringArray(mcp.tools, `${path}.mcp.tools`),
    },
  };
}

function parseCatalog(value: unknown): DomainCatalog {
  assertCondition(isObject(value), 'Expected object for catalog root');
  const raw = value as Record<string, unknown>;
  assertCondition(raw.schema_version === 'lifeos.domain-catalog.v1', 'Invalid catalog schema version');
  const shell = raw.shell as Record<string, unknown> | undefined;
  if (!isObject(shell)) {
    throw new Error('[domain-catalog] Expected shell object');
  }
  const domainsRaw = parseObjectArray(raw.domains, 'catalog.domains');

  const domains: DomainCatalogEntry[] = domainsRaw.map((domain, index) => {
    assertCondition(isObject(domain), `Expected object at catalog.domains[${index}]`);
    const entry = domain as Record<string, unknown>;
    assertCondition(typeof entry.id === 'string' && entry.id.length > 0, `Invalid id at catalog.domains[${index}]`);
    assertCondition(typeof entry.label === 'string' && entry.label.length > 0, `Invalid label at catalog.domains[${index}]`);
    assertCondition(typeof entry.icon === 'string' && entry.icon.length > 0, `Invalid icon at catalog.domains[${index}]`);
    assertCondition(typeof entry.status === 'string', `Invalid status at catalog.domains[${index}]`);
    assertCondition(typeof entry.summary === 'string' && entry.summary.length > 0, `Invalid summary at catalog.domains[${index}]`);

    return {
      id: String(entry.id),
      label: String(entry.label),
      icon: String(entry.icon),
      status: String(entry.status) as DomainStatus,
      manifest: parseString(entry.manifest, `catalog.domains[${index}].manifest`),
      skill: parseString(entry.skill, `catalog.domains[${index}].skill`),
      summary: String(entry.summary),
    };
  });

  return {
    schema_version: 'lifeos.domain-catalog.v1',
    shell_version: parseString(raw.shell_version, 'catalog.shell_version'),
    active_domain_id: parseString(raw.active_domain_id, 'catalog.active_domain_id'),
    shell: {
      tabs: parseStringArray(shell.tabs, 'catalog.shell.tabs'),
      global_actions: parseStringArray(shell.global_actions, 'catalog.shell.global_actions'),
      action_policy: parseString(shell.action_policy, 'catalog.shell.action_policy'),
    },
    domains,
  };
}

export function getDomainManifest(domains: DomainCatalog['domains'], id: string): DomainManifest | undefined {
  const entry = domains.find((domain) => domain.id === id);
  if (!entry) return undefined;
  try {
    return parseDomainManifest(loadManifestByPath(entry.manifest), `domain-manifest:${id}`);
  } catch {
    return undefined;
  }
}

export function getManifestPath(domains: DomainCatalog['domains'], id: string): string | undefined {
  return domains.find((domain) => domain.id === id)?.manifest;
}

export function getDomainManifestByPath(domains: DomainCatalog['domains'], manifestPath: string): DomainManifest | undefined {
  const entry = domains.find((domain) => domain.manifest === manifestPath);
  if (!entry) return undefined;
  return getDomainManifest(domains, entry.id);
}

export function getActiveManifestPath(): string {
  const { activeDomain } = loadCatalog();
  if (!activeDomain) {
    throw new Error('[domain-catalog] Missing active domain');
  }
  return activeDomain.manifest;
}

function loadManifestByPath(manifestPath: string): unknown {
  const manifestMap: Record<string, unknown> = {
    './domains/food.v1.json': foodManifestJson,
    './domains/health.v1.json': healthManifestJson,
    './domains/plants.v1.json': plantsManifestJson,
  };
  const manifest = manifestMap[manifestPath];
  if (manifest) {
    return manifest;
  }

  throw new Error(`[domain-catalog] Unsupported manifest path: ${manifestPath}`);
}

export function loadCatalog(): ParsedCatalog {
  if (parsedCatalogCache) {
    return parsedCatalogCache;
  }

  const catalog = parseCatalog(catalogJson);
  const domainsById = Object.fromEntries(catalog.domains.map((domain) => [domain.id, domain])) as Record<string, DomainCatalogEntry>;
  const activeDomainId = activeDomainOverride && domainsById[activeDomainOverride]
    ? activeDomainOverride
    : catalog.active_domain_id;
  const activeDomain = domainsById[activeDomainId];

  if (!activeDomain) {
    throw new Error(`[domain-catalog] Active domain does not exist: ${activeDomainId}`);
  }

  const activeManifest = parseDomainManifest(
    loadManifestByPath(activeDomain.manifest),
    `domain-manifest:${activeDomain.id}`
  );

  if (activeManifest.id !== activeDomain.id) {
    throw new Error(`[domain-catalog] Manifest id mismatch: ${activeManifest.id}`);
  }

  parsedCatalogCache = { catalog, activeDomainId, activeDomain, activeManifest, domainsById };
  return parsedCatalogCache;
}
