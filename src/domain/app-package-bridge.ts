import type { AppPackageV2, CollectionSpec } from '@/server/src/kernel/package';
import type { QueryPredicate } from '@/server/src/kernel/query';
import type { DashboardBlock, DomainManifest } from '@/src/domain/catalog';

const CORE_FIELDS: CollectionSpec['fields'] = {
  id: { type: 'text', required: true, indexed: true },
  title: { type: 'text', required: true, indexed: true },
  collection: { type: 'text', required: true, indexed: true },
  updated_at: { type: 'timestamp', required: true, indexed: true },
  properties: { type: 'json', required: true },
};

const DEFAULT_VIEW_FIELDS = ['id', 'title', 'collection', 'updated_at'];

export type AppPackageBridgeResult = {
  package: AppPackageV2;
  warnings: string[];
};

/**
 * Compatibility bridge from the compiled v1 domain manifest to the bounded
 * server package/query/view contracts. It deliberately does not translate
 * regex dashboard matching: widening a query silently would be unsafe.
 */
export function buildAppPackageFromManifest(
  manifest: DomainManifest,
  options: { version?: string } = {},
): AppPackageBridgeResult {
  const warnings: string[] = [];
  const collections = Object.fromEntries(
    manifest.collections.map((id) => [
      id,
      {
        id,
        fields: {
          ...CORE_FIELDS,
          ...providerFields(manifest),
        },
      } satisfies CollectionSpec,
    ]),
  );

  const queries: AppPackageV2['queries'] = {};
  const views: AppPackageV2['views'] = {};

  for (const surface of manifest.surfaces) {
    const queryId = `surface:${surface.id}`;
    const where = collectionPredicate(surface.collections);
    queries[queryId] = {
      from: 'records',
      ...(where ? { where } : {}),
      orderBy: [{ field: 'updated_at', direction: 'desc' }],
    };
    views[surface.id] = {
      id: surface.id,
      query: queryId,
      mode: 'list',
      fields: [...DEFAULT_VIEW_FIELDS],
    };
  }

  for (const block of manifest.dashboard_blocks ?? []) {
    addDashboardBlock(block, queries, views, warnings);
  }

  return {
    package: {
      schemaVersion: 'wonder.app-package.v2',
      id: manifest.id,
      version: options.version?.trim() || '1.0.0',
      collections,
      queries,
      views,
      presentation: {
        label: manifest.label,
        ...(manifest.home_surface ? { homeSurface: manifest.home_surface } : {}),
        surfaces: manifest.surfaces.map((surface) => ({
          id: surface.id,
          label: surface.label,
          ...(surface.icon ? { icon: surface.icon } : {}),
          ...(surface.image_url ? { imageUrl: surface.image_url } : {}),
          ...(surface.views ? { views: surface.views } : {}),
          collections: [...surface.collections],
        })),
        ...(manifest.visual_identity ? { visualIdentity: cleanJson(manifest.visual_identity) as Record<string, unknown> } : {}),
        ...(manifest.dashboard_blocks ? { dashboardBlocks: cleanJson(manifest.dashboard_blocks) as Record<string, unknown>[] } : {}),
        ...(manifest.render ? { render: cleanJson(manifest.render) as Record<string, unknown> } : {}),
        ...(manifest.rich_detail_schema ? { richDetailSchema: manifest.rich_detail_schema } : {}),
        ...(manifest.provider_template_fields ? { providerTemplateFields: cleanJson(manifest.provider_template_fields) as Record<string, unknown> } : {}),
        sourceSchemaVersion: manifest.schema_version,
      },
      rules: [],
      capabilities: [
        ...manifest.data_homes.map((home) => `data-home:${home}`),
        ...manifest.mcp.resources.map((resource) => `mcp-resource:${resource}`),
        ...manifest.mcp.tools.map((tool) => `mcp-tool:${tool}`),
      ],
      acceptanceTests: [
        'manifest-collections-map-to-package',
        'surface-queries-use-bounded-ast',
        'views-reference-existing-queries',
      ],
    },
    warnings,
  };
}

function cleanJson(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(cleanJson);
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>)
        .filter(([, child]) => child !== undefined)
        .map(([key, child]) => [key, cleanJson(child)]),
    );
  }
  return value;
}

function providerFields(manifest: DomainManifest): CollectionSpec['fields'] {
  const fields: CollectionSpec['fields'] = {};
  for (const field of manifest.provider_template_fields?.required ?? []) {
    if (field in CORE_FIELDS) continue;
    fields[field] = { type: 'text' };
  }
  for (const field of manifest.provider_template_fields?.rich_detail_json ?? []) {
    fields[field] = { type: 'json' };
  }
  for (const field of manifest.provider_template_fields?.relations_json ?? []) {
    fields[field] = { type: 'json' };
  }
  return fields;
}

function collectionPredicate(collections: string[]): QueryPredicate | undefined {
  if (collections.length === 0) {
    return undefined;
  }
  if (collections.length === 1) {
    return { op: 'eq', field: 'collection', value: collections[0] };
  }
  return {
    op: 'or',
    args: collections.map((collection) => ({ op: 'eq', field: 'collection', value: collection })),
  };
}

function addDashboardBlock(
  block: DashboardBlock,
  queries: AppPackageV2['queries'],
  views: AppPackageV2['views'],
  warnings: string[],
) {
  if (block.query.match) {
    warnings.push(`dashboard_block_match_not_translated:${block.id}`);
    return;
  }

  const queryId = `dashboard:${block.id}`;
  const collections = block.query.collections ?? [];
  const where = collectionPredicate(collections);
  queries[queryId] = {
    from: 'records',
    ...(where ? { where } : {}),
    orderBy: [{ field: 'updated_at', direction: 'desc' }],
    ...(block.query.limit === undefined ? {} : { limit: block.query.limit }),
  };
  views[block.id] = {
    id: block.id,
    query: queryId,
    mode: block.kind === 'metric' ? 'chart' : 'list',
    fields: [...DEFAULT_VIEW_FIELDS],
    layout: {
      size: block.size ?? 'standard',
      tone: block.tone,
      href: block.href,
    },
  };
}
