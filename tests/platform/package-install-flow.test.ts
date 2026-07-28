import { describe, expect, it } from 'vitest';

import {
  buildPackageInstallApprovalReceipt,
  buildPackageInstallPreview,
  type PackageInstallApprovalReceipt,
} from '@/packages/shared/contracts/package-install';
import {
  activateAppPackage,
  getActiveAppPackage,
  getAppInstallation,
  installApprovedAppPackage,
  listAppInstallations,
} from '@/src/db/app-package-registry';
import {
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
const registryFixture = JSON.parse(readFileSync(path.join(fixtureDir, 'registry.json'), 'utf8'));

describe('package install launcher flow', () => {
  it('keeps install-from-link review-gated before creating an installation', async () => {
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

    const approval = buildPackageInstallApprovalReceipt(candidate.preview, 'test-user', '2026-07-27T00:00:00.000Z');
    const installation = await installApprovedAppPackage(db, {
      packageJson: candidate.packageJson,
      preview: candidate.preview,
      approval,
      installationId: 'link-install-one',
      now: '2026-07-27T00:00:01.000Z',
    });

    expect(installation).toMatchObject({
      id: 'link-install-one',
      workspaceId: 'default-workspace',
      label: 'Demo Shelf',
      status: 'active',
    });
    const reopened = reopen(db) as any;
    expect((await getAppInstallation(reopened, 'link-install-one'))?.label).toBe('Demo Shelf');
    expect((await getActiveAppPackage(reopened, 'link-install-one'))?.id).toBe('demo.shelf');
  });

  it('loads registry choices and installs through app-installation activation', async () => {
    const db = new MemoryDb() as any;
    const registry = {
      ...registryFixture,
      packages: [
        {
          ...registryFixture.packages[0],
          checksum: buildPackageInstallPreview(validPackage, {
            sourceUrl: registryFixture.packages[0].url,
          }).trust.computedChecksum!,
        },
      ],
    };
    const fetcher = createPackageInstallFetcher(async (url) => {
      if (url === 'https://example.com/registry.json') return jsonResponse(registry);
      if (url === registry.packages[0].url) return jsonResponse(validPackage);
      return { ok: false, status: 404 };
    });
    const manifest = await fetchRegistryManifest('https://example.com/registry.json', fetcher);

    expect(manifest.packages.map((item) => item.url)).toEqual([registry.packages[0].url]);
    const candidate = await fetchPackageInstallCandidate(manifest.packages[0].url, fetcher, {
      registryPackage: manifest.packages[0],
    });

    expect(candidate.preview.trust.status).toBe('checksum_verified');
    const approval = buildPackageInstallApprovalReceipt(candidate.preview, 'test-user', '2026-07-27T00:00:00.000Z');
    const installation = await installApprovedAppPackage(db, {
      packageJson: candidate.packageJson,
      preview: candidate.preview,
      approval,
      installationId: 'registry-install-one',
      now: '2026-07-27T00:00:01.000Z',
    });

    const active = await getActiveAppPackage(reopen(db) as any, installation.id);
    expect(active?.id).toBe(manifest.packages[0].id);
    expect(loadAppPackage(active).activeManifest.label).toBe(manifest.packages[0].name);
  });

  it('blocks invalid preview before activation', async () => {
    const preview = buildPackageInstallPreview({ schemaVersion: 'wonder.app-package.v2' }, {
      sourceUrl: 'https://example.com/apps/bad.package.json',
    });
    const db = new MemoryDb() as any;

    expect(preview.status).toBe('blocked');
    await expect(activateAppPackage(db, { schemaVersion: 'wonder.app-package.v2' })).rejects.toThrow(/app_package_invalid/);
    await expect(installApprovedAppPackage(db, {
      packageJson: { schemaVersion: 'wonder.app-package.v2' },
      preview,
      approval: forgedApproval(),
      installationId: 'invalid-install',
    })).rejects.toThrow(/package_install_preview_blocked|app_package_invalid/);
    expect(await getActiveAppPackage(db)).toBeNull();
  });

  it('blocks checksum mismatch and creates a second installation for the same approved package', async () => {
    const db = new MemoryDb() as any;
    const mismatchPreview = buildPackageInstallPreview(validPackage, {
      sourceUrl: 'https://example.com/apps/demo.package.json',
      expectedChecksum: `sha256:${'0'.repeat(64)}`,
    });
    await expect(installApprovedAppPackage(db, {
      packageJson: validPackage,
      preview: mismatchPreview,
      approval: forgedApproval(),
      installationId: 'checksum-mismatch',
    })).rejects.toThrow('package_install_preview_blocked');

    const readyPreview = buildPackageInstallPreview(validPackage, {
      sourceUrl: 'https://example.com/apps/demo.package.json',
    });
    const approval = buildPackageInstallApprovalReceipt(readyPreview, 'test-user', '2026-07-27T00:00:00.000Z');
    const first = await installApprovedAppPackage(db, {
      packageJson: validPackage,
      preview: readyPreview,
      approval,
      installationId: 'same-app-one',
      now: '2026-07-27T00:00:01.000Z',
    });
    const second = await installApprovedAppPackage(db, {
      packageJson: validPackage,
      preview: readyPreview,
      approval,
      installationId: 'same-app-two',
      now: '2026-07-27T00:00:02.000Z',
    });

    expect(first.id).not.toBe(second.id);
    expect((await listAppInstallations(db)).map((item) => item.id)).toEqual(['same-app-one', 'same-app-two']);
    expect((await getActiveAppPackage(db, 'same-app-one'))?.id).toBe('demo.shelf');
    expect((await getActiveAppPackage(db, 'same-app-two'))?.id).toBe('demo.shelf');
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
  next.workspaces = new Map(db.workspaces);
  next.appInstallations = new Map(db.appInstallations);
  next.appInstallationPackageState = new Map(db.appInstallationPackageState);
  next.appPackages = new Map(db.appPackages);
  next.appPackageState = db.appPackageState ? { ...db.appPackageState } : null;
  next.appPackageReceipts = db.appPackageReceipts.map((row) => ({ ...row }));
  return next;
}

function forgedApproval(): PackageInstallApprovalReceipt {
  return {
    schemaVersion: 'utopia.install-approval.v1',
    approved: true,
    sourceUrl: 'https://example.com/apps/demo.package.json',
    packageId: 'demo.shelf',
    version: '1.0.0',
    checksum: `sha256:${'0'.repeat(64)}`,
    compatibility: { status: 'compatible', reasons: [] },
    previewHash: `sha256:${'0'.repeat(64)}`,
    approvedBy: 'test-user',
    approvedAt: '2026-07-27T00:00:00.000Z',
  };
}
