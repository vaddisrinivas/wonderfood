import { describe, expect, it } from 'vitest';

import { activateAppPackage, bootstrapAppPackageRegistry, getActiveAppPackage, rollbackAppPackage } from '@/src/db/app-package-registry';
import { buildAppPackageFromManifest } from '@/src/domain/app-package-bridge';
import { loadCatalog, setActivePackageOverride } from '@/src/domain/catalog';
import { MemoryDb } from '@/tests/helpers/memory-db';
import type { AppPackageV2 } from '@/server/src/kernel/package';

describe('app package SQLite registry', () => {
  it('bootstraps once, persists activation across reopen, and rolls back', async () => {
    setActivePackageOverride(null);
    const db = new MemoryDb() as any;
    const bootstrapped = await bootstrapAppPackageRegistry(db);

    expect(bootstrapped.id).toBe('food');
    expect(db.appPackages.size).toBe(1);
    expect(db.appPackageReceipts.map((row: any) => row.action)).toEqual(['bootstrap']);

    await bootstrapAppPackageRegistry(db);
    expect(db.appPackageReceipts.map((row: any) => row.action)).toEqual(['bootstrap']);

    const basePresentation = bootstrapped.presentation;
    expect(basePresentation).toBeDefined();
    const nextPackage: AppPackageV2 = {
      ...bootstrapped,
      id: 'runtime-food',
      version: '2.0.0',
      presentation: {
        ...basePresentation!,
        label: 'Runtime Food',
        surfaces: basePresentation!.surfaces,
      },
    };

    await activateAppPackage(db, nextPackage);
    const reopened = reopen(db) as any;
    const active = await getActiveAppPackage(reopened);
    expect(active?.id).toBe('runtime-food');

    await bootstrapAppPackageRegistry(reopened);
    expect(loadCatalog().activeManifest.label).toBe('Runtime Food');

    const rolledBack = await rollbackAppPackage(reopened);
    expect(rolledBack?.id).toBe('food');
    expect(loadCatalog().activeManifest.id).toBe('food');
    expect(reopened.appPackageReceipts.map((row: any) => row.action)).toEqual(['bootstrap', 'activate', 'rollback']);
  });

  it('fails closed for invalid package payloads', async () => {
    const db = new MemoryDb() as any;
    await expect(activateAppPackage(db, { schemaVersion: 'wonder.app-package.v2' } as any)).rejects.toThrow(/app_package_invalid/);
    expect(db.appPackageState).toBeNull();
  });

  it('does not silently bootstrap when installed packages exist without active state', async () => {
    const db = new MemoryDb() as any;
    const pkg = buildAppPackageFromManifest(loadCatalog().activeManifest).package;
    db.appPackages.set('food@1.0.0', {
      package_key: 'food@1.0.0',
      package_id: pkg.id,
      version: pkg.version,
      payload_json: JSON.stringify(pkg),
      created_at: '2026-07-24T00:00:00.000Z',
      updated_at: '2026-07-24T00:00:00.000Z',
    });

    await expect(bootstrapAppPackageRegistry(db)).rejects.toThrow(/app_package_active_missing/);
    expect(db.appPackageReceipts).toEqual([]);
  });

  it('uses bridged package presentation as catalog authority', async () => {
    const db = new MemoryDb() as any;
    const manifest = loadCatalog().activeManifest;
    const pkg = buildAppPackageFromManifest(manifest, { version: 'presentation-test' }).package;
    const activePackage: AppPackageV2 = {
      ...pkg,
      id: 'chef-lab',
      presentation: {
        ...pkg.presentation!,
        label: 'Chef Lab',
      },
    };
    await activateAppPackage(db, activePackage);

    const catalog = loadCatalog();
    expect(catalog.activeDomainId).toBe('chef-lab');
    expect(catalog.activeManifest.label).toBe('Chef Lab');
    expect(catalog.activeManifest.surfaces.length).toBeGreaterThan(0);
  });
});

function reopen(source: MemoryDb): MemoryDb {
  const db = new MemoryDb();
  db.appPackages = new Map(source.appPackages);
  db.appPackageState = source.appPackageState ? { ...source.appPackageState } : null;
  db.appPackageReceipts = source.appPackageReceipts.map((row) => ({ ...row }));
  return db;
}
