import { describe, expect, it } from 'vitest';

import {
  activateAppPackage,
  activateApprovedAppPackageChange,
  bootstrapAppPackageRegistry,
  getActiveAppPackage,
  previewAppPackageChange,
  rollbackAppPackage,
  type AppPackageChangeRequest,
} from '@/src/db/app-package-registry';
import { buildAppPackageFromManifest } from '@/src/domain/app-package-bridge';
import { loadCatalog, setActivePackageOverride } from '@/src/domain/catalog';
import { buildSafePackageChangeRequest } from '@/src/domain/package-change-templates';
import { MemoryDb } from '@/tests/helpers/memory-db';
import type { AppPackage } from '@/packages/shared/contracts/package';

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
    const nextPackage: AppPackage = {
      ...bootstrapped,
      id: 'runtime-food',
      version: '2.0.0',
      presentation: {
        ...basePresentation!,
        label: 'Runtime Food',
        surfaces: basePresentation!.surfaces,
      },
    };

    await activateAppPackage(db, nextPackage, 'activate', {
      requestHash: 'sha256:request',
      packageHash: 'sha256:package',
      approvalHash: 'sha256:approval',
      approvedBy: 'test-user',
    });
    const reopened = reopen(db) as any;
    const active = await getActiveAppPackage(reopened);
    expect(active?.id).toBe('runtime-food');

    await bootstrapAppPackageRegistry(reopened);
    expect(loadCatalog().activeManifest.label).toBe('Runtime Food');

    const rolledBack = await rollbackAppPackage(reopened);
    expect(rolledBack?.id).toBe('food');
    expect(loadCatalog().activeManifest.id).toBe('food');
    expect(reopened.appPackageReceipts.map((row: any) => row.action)).toEqual(['bootstrap', 'activate', 'rollback']);
    expect(reopened.appPackageReceipts[1]).toMatchObject({
      request_hash: 'sha256:request',
      package_hash: 'sha256:package',
      approval_hash: 'sha256:approval',
      approved_by: 'test-user',
    });
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
    const activePackage: AppPackage = {
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

  it('previews and applies hash-bound package diffs through durable approval receipts', async () => {
    setActivePackageOverride(null);
    const db = new MemoryDb() as any;
    const bootstrapped = await bootstrapAppPackageRegistry(db);
    const request: AppPackageChangeRequest = {
      basePackageKey: `${bootstrapped.id}@${bootstrapped.version}`,
      requestedBy: 'test-package-editor',
      patch: [
        { op: 'replace', path: '/version', value: `${bootstrapped.version}+ai.test` },
        {
          op: 'add',
          path: '/collections/ai_notes',
          value: {
            id: 'ai_notes',
            fields: {
              id: { type: 'text', required: true, indexed: true },
              title: { type: 'text', required: true, indexed: true },
              body: { type: 'text' },
              updated_at: { type: 'timestamp', indexed: true },
            },
          },
        },
        { op: 'add', path: '/queries/ai_notes', value: { from: 'ai_notes', limit: 12 } },
        { op: 'add', path: '/views/ai_notes', value: { id: 'ai_notes', query: 'ai_notes', mode: 'list', fields: ['title', 'body'] } },
        { op: 'add', path: '/presentation/surfaces/-', value: { id: 'ai_notes', label: 'AI Notes', collections: ['ai_notes'], views: ['ai_notes'] } },
      ],
    };

    const preview = await previewAppPackageChange(db, request);
    expect(preview.status).toBe('valid');
    expect(preview.requestHash).toMatch(/^sha256:[a-f0-9]{64}$/);
    expect(preview.packageHash).toMatch(/^sha256:[a-f0-9]{64}$/);
    expect(preview.package?.collections.ai_notes.id).toBe('ai_notes');

    await expect(activateApprovedAppPackageChange(db, request, {
      schemaVersion: 'wonder.package-change-approval.v1',
      approved: true,
      requestHash: preview.requestHash,
      packageHash: 'sha256:0000000000000000000000000000000000000000000000000000000000000000',
      approvedBy: 'tester',
      approvedAt: '2026-07-27T00:00:00.000Z',
    })).rejects.toThrow(/package_change_approval_mismatch/);

    const applied = await activateApprovedAppPackageChange(db, request, {
      schemaVersion: 'wonder.package-change-approval.v1',
      approved: true,
      requestHash: preview.requestHash,
      packageHash: preview.packageHash!,
      approvedBy: 'tester',
      approvedAt: '2026-07-27T00:00:00.000Z',
    });

    expect(applied.collections.ai_notes.id).toBe('ai_notes');
    expect(db.appPackageReceipts.at(-1)).toMatchObject({
      action: 'activate',
      request_hash: preview.requestHash,
      package_hash: preview.packageHash,
      approved_by: 'tester',
    });
  });

  it('blocks package diffs that try to change native dependency pins', async () => {
    setActivePackageOverride(null);
    const db = new MemoryDb() as any;
    await bootstrapAppPackageRegistry(db);

    await expect(previewAppPackageChange(db, {
      patch: [{ op: 'add', path: '/dependencyPins/-', value: { package: 'unsafe-native-package', version: '*' } }],
      requestedBy: 'test-package-editor',
    })).rejects.toThrow(/package_change_path_forbidden/);
  });

  it('fails closed when native capability lock content is forged', async () => {
    const db = new MemoryDb() as any;
    const pkg = buildAppPackageFromManifest(loadCatalog().activeManifest, { version: 'forged-native-lock' }).package;
    if (pkg.schemaVersion !== 'wonder.app-package.v3') throw new Error('expected V3 package');

    await expect(activateAppPackage(db, {
      ...pkg,
      nativeCapabilities: {
        ...pkg.nativeCapabilities,
        permissions: [
          ...(pkg.nativeCapabilities.permissions ?? []),
          {
            id: 'forged-camera',
            platform: 'android',
            permission: 'android.permission.CAMERA',
            reason: 'Forged runtime permission.',
            required: true,
          },
        ],
      },
    })).rejects.toThrow(/contractLock.nativeCapabilities must match nativeCapabilities/);

    await expect(activateAppPackage(db, {
      ...pkg,
      contractLock: {
        ...pkg.contractLock,
        checksum: 'sha256:0000000000000000000000000000000000000000000000000000000000000000',
      },
    })).rejects.toThrow(/contractLock.checksum mismatch/);
  });

  it('builds safe package-edit templates for table, theme, workflow, and rich widget prompts', async () => {
    setActivePackageOverride(null);
    const db = new MemoryDb() as any;
    const active = await bootstrapAppPackageRegistry(db);

    const table = await previewAppPackageChange(db, buildSafePackageChangeRequest(active, 'add freezer ideas table'));
    expect(table.status).toBe('valid');
    expect(table.package?.collections.ai_freezer_ideas.id).toBe('ai_freezer_ideas');
    expect(table.package?.presentation?.ui?.screens?.ai_freezer_ideas.title).toBe('Freezer Ideas');

    const theme = await previewAppPackageChange(db, buildSafePackageChangeRequest(active, 'make theme cuter cards'));
    expect(theme.status).toBe('valid');
    expect(theme.package?.presentation?.visualIdentity?.density).toBe('compact-cute');

    const workflow = await previewAppPackageChange(db, buildSafePackageChangeRequest(active, 'when pantry expires suggest dinner'));
    expect(workflow.status).toBe('valid');
    expect(workflow.package?.rules.some((rule) => rule.id === 'ai_pantry_expires_dinner_workflow_rule')).toBe(true);

    const form = await previewAppPackageChange(db, buildSafePackageChangeRequest(active, 'add vendor intake form'));
    expect(form.status).toBe('valid');
    expect(form.package?.collections.ai_vendor_intake.fields.answers.type).toBe('json');
    expect(form.package?.presentation?.ui?.screens?.ai_vendor_intake.components?.[0].widget).toBe('formCard');

    const board = await previewAppPackageChange(db, buildSafePackageChangeRequest(active, 'create catering kanban board'));
    expect(board.status).toBe('valid');
    expect(board.package?.views.ai_catering.mode).toBe('board');
    expect(board.package?.presentation?.ui?.screens?.ai_catering.components?.[0].widget).toBe('kanbanBoard');

    const poll = await previewAppPackageChange(db, buildSafePackageChangeRequest(active, 'add family dinner poll'));
    expect(poll.status).toBe('valid');
    expect(poll.package?.collections.ai_family_dinner.fields.options.type).toBe('json');
    expect(poll.package?.presentation?.ui?.screens?.ai_family_dinner.components?.[0].widget).toBe('pollCard');

    const calendar = await previewAppPackageChange(db, buildSafePackageChangeRequest(active, 'create meal prep calendar'));
    expect(calendar.status).toBe('valid');
    expect(calendar.package?.views.ai_meal_prep.mode).toBe('calendar');
    expect(calendar.package?.presentation?.ui?.screens?.ai_meal_prep.components?.[0].widget).toBe('calendarBlock');

    const media = await previewAppPackageChange(db, buildSafePackageChangeRequest(active, 'add youtube recipe video page'));
    expect(media.status).toBe('valid');
    expect(media.package?.collections.ai_recipe.fields.media.type).toBe('json');
    expect(media.package?.presentation?.ui?.screens?.ai_recipe.components?.[0].widget).toBe('mediaBlock');

    const link = await previewAppPackageChange(db, buildSafePackageChangeRequest(active, 'add recipe bookmark link preview'));
    expect(link.status).toBe('valid');
    expect(link.package?.collections.ai_recipe.fields.preview.type).toBe('json');
    expect(link.package?.presentation?.ui?.screens?.ai_recipe.components?.[0].widget).toBe('linkPreview');

    const map = await previewAppPackageChange(db, buildSafePackageChangeRequest(active, 'create grocery store map'));
    expect(map.status).toBe('valid');
    expect(map.package?.collections.ai_grocery_store.fields.location.type).toBe('json');
    expect(map.package?.presentation?.ui?.screens?.ai_grocery_store.components?.[0].widget).toBe('mapBlock');

    const chart = await previewAppPackageChange(db, buildSafePackageChangeRequest(active, 'add spending analytics chart'));
    expect(chart.status).toBe('valid');
    expect(chart.package?.views.ai_spending.mode).toBe('chart');
    expect(chart.package?.presentation?.ui?.screens?.ai_spending.components?.[0].widget).toBe('chartBlock');
  });
});

function reopen(source: MemoryDb): MemoryDb {
  const db = new MemoryDb();
  db.appPackages = new Map(source.appPackages);
  db.appPackageState = source.appPackageState ? { ...source.appPackageState } : null;
  db.appPackageReceipts = source.appPackageReceipts.map((row) => ({ ...row }));
  return db;
}
