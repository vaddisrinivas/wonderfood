import { describe, expect, it } from 'vitest';

import { buildPackageInstallPreview } from '@/packages/shared/contracts/package-install';
import { activateAppPackage, getActiveAppPackage } from '@/src/db/app-package-registry';
import {
  BUNDLED_DEMO_PACKAGE_URL,
  BUNDLED_UTOPIA_REGISTRY_URL,
  createPackageInstallFetcher,
  fetchPackageInstallCandidate,
  fetchRegistryManifest,
} from '@/src/domain/package-install';
import { loadAppPackage } from '@/src/domain/package-loader';
import { MemoryDb } from '@/tests/helpers/memory-db';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const fixtureDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../fixtures/package-install');
const validPackage = JSON.parse(readFileSync(path.join(fixtureDir, 'valid-package.json'), 'utf8'));

describe('package install launcher flow', () => {
  it('keeps install-from-link review-gated before activation', async () => {
    const db = new MemoryDb() as any;
    const fetcher = createPackageInstallFetcher(async (url) => {
      if (url !== 'https://example.com/apps/demo.package.json') return { ok: false, status: 404 };
      return jsonResponse(validPackage);
    });

    const candidate = await fetchPackageInstallCandidate('https://example.com/apps/demo.package.json', fetcher);
    expect(candidate.preview.status).toBe('ready_for_review');
    expect(candidate.preview.trust.status).toBe('checksum_missing');
    expect(candidate.preview.approvalRequired).toBe(true);
    expect(await getActiveAppPackage(db)).toBeNull();

    await activateAppPackage(db, candidate.packageJson);
    expect((await getActiveAppPackage(reopen(db) as any))?.id).toBe('demo.shelf');
  });

  it('loads registry choices and installs bundled demo through existing activation path', async () => {
    const db = new MemoryDb() as any;
    const fetcher = createPackageInstallFetcher(async () => {
      throw new Error('remote_fetch_forbidden');
    });
    const registry = await fetchRegistryManifest(BUNDLED_UTOPIA_REGISTRY_URL, fetcher);

    expect(registry.packages.map((item) => item.url)).toEqual([BUNDLED_DEMO_PACKAGE_URL]);
    const candidate = await fetchPackageInstallCandidate(registry.packages[0].url, fetcher, {
      registryPackage: registry.packages[0],
    });

    expect(candidate.preview.trust.status).toBe('checksum_verified');
    await activateAppPackage(db, candidate.packageJson);

    const active = await getActiveAppPackage(reopen(db) as any);
    expect(active?.id).toBe(registry.packages[0].id);
    expect(loadAppPackage(active).activeManifest.label).toBe(registry.packages[0].name);
  });

  it('blocks invalid preview before activation', async () => {
    const preview = buildPackageInstallPreview({ schemaVersion: 'wonder.app-package.v2' }, {
      sourceUrl: 'https://example.com/apps/bad.package.json',
    });
    const db = new MemoryDb() as any;

    expect(preview.status).toBe('blocked');
    await expect(activateAppPackage(db, { schemaVersion: 'wonder.app-package.v2' })).rejects.toThrow(/app_package_invalid/);
    expect(await getActiveAppPackage(db)).toBeNull();
  });

  it('fails bad URLs safely before fetch', async () => {
    let called = false;
    const fetcher = createPackageInstallFetcher(async () => {
      called = true;
      return jsonResponse(validPackage);
    });

    await expect(fetchPackageInstallCandidate('ftp://example.com/app.package.json', fetcher)).rejects.toThrow('install_url_must_be_https');
    expect(called).toBe(false);
  });
});

function jsonResponse(value: unknown) {
  return {
    ok: true,
    status: 200,
    headers: {
      get: (name: string) => name.toLowerCase() === 'content-type' ? 'application/json' : null,
    },
    json: async () => value,
  };
}

function reopen(db: MemoryDb): MemoryDb {
  const next = new MemoryDb();
  next.appPackages = new Map(db.appPackages);
  next.appPackageState = db.appPackageState ? { ...db.appPackageState } : null;
  next.appPackageReceipts = db.appPackageReceipts.map((row) => ({ ...row }));
  return next;
}
