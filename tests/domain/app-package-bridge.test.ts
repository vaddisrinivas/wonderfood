import { describe, expect, it } from 'vitest';

import { validateAppPackage } from '@/server/src/kernel/package';
import { evaluatePackage } from '@/server/src/kernel/runtime';
import { buildAppPackageFromManifest } from '@/src/domain/app-package-bridge';
import { loadCatalog } from '@/src/domain/catalog';

describe('domain manifest to app package bridge', () => {
  it('maps every manifest collection and surface into valid package contracts', () => {
    const manifest = loadCatalog().activeManifest;
    const bridged = buildAppPackageFromManifest(manifest, { version: '1.0.0-test' });
    const validation = validateAppPackage(bridged.package);

    expect(validation.valid).toBe(true);
    expect(Object.keys(bridged.package.collections).sort()).toEqual([...manifest.collections].sort());

    for (const surface of manifest.surfaces) {
      const view = bridged.package.views[surface.id];
      expect(view).toBeDefined();
      expect(bridged.package.queries[view.query]).toBeDefined();
      expect(bridged.package.queries[view.query].from).toBe('records');
    }
  });

  it('executes a manifest surface through the bounded query and view contracts', () => {
    const manifest = loadCatalog().activeManifest;
    const bridged = buildAppPackageFromManifest(manifest);
    const surface = manifest.surfaces[0];
    const view = bridged.package.views[surface.id];
    const records = [
      {
        id: 'matching-record',
        title: 'Matching record',
        collection: surface.collections[0] ?? manifest.collections[0],
        updated_at: '2026-07-23T00:00:00.000Z',
        properties: {},
      },
      {
        id: 'outside-surface',
        title: 'Outside surface',
        collection: manifest.collections.at(-1) ?? manifest.collections[0],
        updated_at: '2026-07-22T00:00:00.000Z',
        properties: {},
      },
    ];

    const runtime = evaluatePackage({ package: bridged.package, collections: { records } });
    const result = runtime.queries[view.query];
    const rendered = runtime.views[view.id];

    expect(result.rows).toHaveLength(surface.collections.length ? 1 : 2);
    expect(result.rows.every((record) => !surface.collections.length || surface.collections.includes(String(record.collection)))).toBe(true);
    expect(rendered.rows.every((row) => Object.keys(row).every((field) => view.fields.includes(field)))).toBe(true);
    expect(rendered.provenance).toBe(`${bridged.package.id}@${bridged.package.version}/query:${view.query}`);
  });

  it('refuses to silently translate legacy regex dashboard matching', () => {
    const manifest = loadCatalog().activeManifest;
    const bridged = buildAppPackageFromManifest(manifest);
    const regexBlocks = (manifest.dashboard_blocks ?? []).filter((block) => block.query.match);

    expect(regexBlocks.length).toBeGreaterThan(0);
    for (const block of regexBlocks) {
      expect(bridged.warnings).toContain(`dashboard_block_match_not_translated:${block.id}`);
      expect(bridged.package.queries[`dashboard:${block.id}`]).toBeUndefined();
      expect(bridged.package.views[block.id]).toBeUndefined();
    }
  });
});
