import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import {
  createAppInstallation,
  getActiveAppPackage,
  getAppInstallation,
  listAppInstallations,
  activateAppPackage,
  rollbackAppPackage,
} from '@/src/db/app-package-registry';
import {
  DATABASE_VERSION,
  rollbackDatabase,
  runMigrations,
} from '@/src/db/migrations';
import { DEFAULT_WORKSPACE_ID } from '@/packages/shared/contracts/app-installation';
import type { AppPackage } from '@/packages/shared/contracts/package';
import { setActivePackageOverride } from '@/src/domain/catalog';
import { NodeSqliteDb } from '@/tests/helpers/node-sqlite-db';

type Db = NodeSqliteDb & {
  getFirstAsync<T>(sql: string, params?: unknown): Promise<T | null>;
  getAllAsync<T>(sql: string, params?: unknown): Promise<T[]>;
  runAsync(sql: string, params?: unknown): Promise<unknown>;
};

const referencePackage = JSON.parse(
  readFileSync(resolve('tests/fixtures/app-packages/reference-app/compiled/reference-app-1.0.0.package.json'), 'utf8'),
) as AppPackage;

const referenceUpgrade = JSON.parse(
  readFileSync(resolve('tests/fixtures/app-packages/reference-app/compiled/reference-app-1.1.0.package.json'), 'utf8'),
) as AppPackage;

const legacyPackageKey = `${referencePackage.id}@${referencePackage.version}`;
setActivePackageOverride(referencePackage);

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

async function withDb<T>(fn: (db: Db) => Promise<T>): Promise<T> {
  const db = new NodeSqliteDb() as Db;
  try {
    return await fn(db);
  } finally {
    db.close();
  }
}

async function proveFreshDbDefaults(): Promise<void> {
  await withDb(async (db) => {
    await runMigrations(db as never);
    const workspace = await db.getFirstAsync<{ id: string }>('SELECT id FROM workspaces WHERE id = $id', {
      $id: DEFAULT_WORKSPACE_ID,
    });
    const defaultInstall = await getAppInstallation(db as never, 'default');
    assert(workspace?.id === DEFAULT_WORKSPACE_ID, 'fresh DB missing default workspace');
    assert(defaultInstall?.id === 'default', 'fresh DB missing default app installation');
  });
}

async function proveLegacySingletonMigration(): Promise<void> {
  await withDb(async (db) => {
    await runMigrations(db as never);
    await rollbackDatabase(db as never, 7);
    await db.runAsync(
      `INSERT OR REPLACE INTO app_packages
        (package_key, package_id, version, payload_json, created_at, updated_at)
        VALUES ($package_key, $package_id, $version, $payload_json, $created_at, $updated_at)`,
      {
        $package_key: legacyPackageKey,
        $package_id: referencePackage.id,
        $version: referencePackage.version,
        $payload_json: JSON.stringify(referencePackage),
        $created_at: '2026-07-27T00:00:00.000Z',
        $updated_at: '2026-07-27T00:00:00.000Z',
      },
    );
    await db.runAsync(
      `INSERT OR REPLACE INTO app_package_state
        (id, active_package_key, previous_package_key, updated_at)
        VALUES ('default', $active_package_key, NULL, $updated_at)`,
      {
        $active_package_key: legacyPackageKey,
        $updated_at: '2026-07-27T00:00:00.000Z',
      },
    );

    await runMigrations(db as never);
    await runMigrations(db as never);

    const version = await db.getFirstAsync<{ user_version: number }>('PRAGMA user_version');
    const migratedRows = await db.getAllAsync<{ installation_id: string; active_package_key: string }>(
      'SELECT installation_id, active_package_key FROM app_installation_package_state',
    );
    assert(version?.user_version === DATABASE_VERSION, 'legacy migration did not reach current version');
    assert(migratedRows.length === 1, 'legacy singleton migration duplicated scoped state');
    assert(migratedRows[0].installation_id === 'default', 'legacy singleton migrated to wrong installation');
    assert(migratedRows[0].active_package_key === legacyPackageKey, 'legacy singleton active package not preserved');
  });
}

async function proveInstallationIsolation(): Promise<void> {
  await withDb(async (db) => {
    await runMigrations(db as never);
    await createAppInstallation(db as never, { id: 'install-a', label: 'Install A', now: '2026-07-27T01:00:00.000Z' });
    await createAppInstallation(db as never, { id: 'install-b', label: 'Install B', now: '2026-07-27T01:01:00.000Z' });

    await activateAppPackage(db as never, 'install-a', referencePackage);
    await activateAppPackage(db as never, 'install-b', referencePackage);
    assert((await getActiveAppPackage(db as never, 'install-a'))?.version === '1.0.0', 'install A did not activate v1');
    assert((await getActiveAppPackage(db as never, 'install-b'))?.version === '1.0.0', 'install B did not activate v1');

    await activateAppPackage(db as never, 'install-a', referenceUpgrade);
    assert((await getActiveAppPackage(db as never, 'install-a'))?.version === '1.1.0', 'install A did not upgrade');
    assert((await getActiveAppPackage(db as never, 'install-b'))?.version === '1.0.0', 'install A upgrade changed install B');

    await rollbackAppPackage(db as never, 'install-a');
    assert((await getActiveAppPackage(db as never, 'install-a'))?.version === '1.0.0', 'install A did not roll back');
    assert((await getActiveAppPackage(db as never, 'install-b'))?.version === '1.0.0', 'install A rollback changed install B');

    const installs = await listAppInstallations(db as never, DEFAULT_WORKSPACE_ID);
    const ids = installs.map((installation) => installation.id);
    assert(ids.includes('install-a') && ids.includes('install-b'), 'app library did not list both installations');
  });
}

async function main(): Promise<void> {
  await proveFreshDbDefaults();
  await proveLegacySingletonMigration();
  await proveInstallationIsolation();
  console.log('app installation foundation ok');
}

main().catch((error) => {
  console.error(error instanceof Error ? (error.stack ?? error.message) : String(error));
  process.exit(1);
});
