import type { SQLiteDatabase } from 'expo-sqlite';

import { buildAppPackageFromManifest } from '@/src/domain/app-package-bridge';
import { loadCatalog, setActivePackageOverride } from '@/src/domain/catalog';
import type { AppPackageV2 } from '@/server/src/kernel/package';

type AppPackageRow = {
  package_key: string;
  payload_json: string;
};

type AppPackageStateRow = {
  active_package_key: string | null;
  previous_package_key: string | null;
};

type ReceiptAction = 'bootstrap' | 'activate' | 'rollback';

export async function bootstrapAppPackageRegistry(db: SQLiteDatabase): Promise<AppPackageV2> {
  const active = await getActiveAppPackage(db);
  if (active) {
    setActivePackageOverride(active);
    return active;
  }
  const installedCount = await getInstalledPackageCount(db);
  if (installedCount > 0) {
    throw new Error('app_package_active_missing');
  }

  const manifest = loadCatalog().activeManifest;
  const appPackage = buildAppPackageFromManifest(manifest).package;
  await activateAppPackage(db, appPackage, 'bootstrap');
  setActivePackageOverride(appPackage);
  return appPackage;
}

export async function getActiveAppPackage(db: SQLiteDatabase): Promise<AppPackageV2 | null> {
  const state = await getPackageState(db);
  if (!state?.active_package_key) return null;
  const appPackage = await getPackageByKey(db, state.active_package_key);
  if (appPackage) setActivePackageOverride(appPackage);
  return appPackage;
}

export async function activateAppPackage(
  db: SQLiteDatabase,
  appPackage: AppPackageV2,
  action: ReceiptAction = 'activate',
): Promise<AppPackageV2> {
  assertAppPackageShape(appPackage);
  const now = new Date().toISOString();
  const key = packageKey(appPackage);
  const previous = await getPackageState(db);

  await db.withTransactionAsync(async () => {
    await db.runAsync(
      `INSERT OR REPLACE INTO app_packages
        (package_key, package_id, version, payload_json, created_at, updated_at)
        VALUES ($package_key, $package_id, $version, $payload_json, $created_at, $updated_at)`,
      {
        $package_key: key,
        $package_id: appPackage.id,
        $version: appPackage.version,
        $payload_json: JSON.stringify(appPackage),
        $created_at: now,
        $updated_at: now,
      },
    );
    await db.runAsync(
      `INSERT OR REPLACE INTO app_package_state
        (id, active_package_key, previous_package_key, updated_at)
        VALUES ('default', $active_package_key, $previous_package_key, $updated_at)`,
      {
        $active_package_key: key,
        $previous_package_key: previous?.active_package_key ?? null,
        $updated_at: now,
      },
    );
    await insertReceipt(db, action, key, previous?.active_package_key ?? null, now);
  });

  setActivePackageOverride(appPackage);
  return appPackage;
}

export async function rollbackAppPackage(db: SQLiteDatabase): Promise<AppPackageV2 | null> {
  const state = await getPackageState(db);
  if (!state?.previous_package_key) return null;
  const previousPackage = await getPackageByKey(db, state.previous_package_key);
  if (!previousPackage) return null;

  const now = new Date().toISOString();
  await db.withTransactionAsync(async () => {
    await db.runAsync(
      `INSERT OR REPLACE INTO app_package_state
        (id, active_package_key, previous_package_key, updated_at)
        VALUES ('default', $active_package_key, NULL, $updated_at)`,
      {
        $active_package_key: state.previous_package_key,
        $updated_at: now,
      },
    );
    await insertReceipt(db, 'rollback', state.previous_package_key, state.active_package_key, now);
  });

  setActivePackageOverride(previousPackage);
  return previousPackage;
}

function packageKey(appPackage: AppPackageV2): string {
  return `${appPackage.id}@${appPackage.version}`;
}

async function getPackageState(db: SQLiteDatabase): Promise<AppPackageStateRow | null> {
  return db.getFirstAsync<AppPackageStateRow>(
    `SELECT active_package_key, previous_package_key FROM app_package_state WHERE id = 'default'`,
  );
}

async function getInstalledPackageCount(db: SQLiteDatabase): Promise<number> {
  const row = await db.getFirstAsync<{ count: number | string }>('SELECT COUNT(*) as count FROM app_packages');
  const count = typeof row?.count === 'number' ? row.count : Number.parseInt(String(row?.count ?? '0'), 10);
  return Number.isFinite(count) ? count : 0;
}

async function getPackageByKey(db: SQLiteDatabase, key: string): Promise<AppPackageV2 | null> {
  const row = await db.getFirstAsync<AppPackageRow>(
    'SELECT package_key, payload_json FROM app_packages WHERE package_key = $package_key',
    { $package_key: key },
  );
  if (!row) return null;

  let parsed: unknown;
  try {
    parsed = JSON.parse(row.payload_json);
  } catch {
    throw new Error(`app_package_invalid_json:${row.package_key}`);
  }
  assertAppPackageShape(parsed);
  if (packageKey(parsed) !== row.package_key) {
    throw new Error(`app_package_key_mismatch:${row.package_key}`);
  }
  return parsed;
}

async function insertReceipt(
  db: SQLiteDatabase,
  action: ReceiptAction,
  packageKeyValue: string | null,
  previousPackageKey: string | null,
  now: string,
): Promise<void> {
  await db.runAsync(
    `INSERT INTO app_package_receipts
      (id, action, package_key, previous_package_key, created_at)
      VALUES ($id, $action, $package_key, $previous_package_key, $created_at)`,
    {
      $id: `app-package:${action}:${packageKeyValue ?? 'none'}:${now}`,
      $action: action,
      $package_key: packageKeyValue,
      $previous_package_key: previousPackageKey,
      $created_at: now,
    },
  );
}

function assertAppPackageShape(input: unknown): asserts input is AppPackageV2 {
  const errors = collectAppPackageShapeErrors(input);
  if (errors.length) {
    throw new Error(`app_package_invalid:${errors.join('|')}`);
  }
}

function collectAppPackageShapeErrors(input: unknown): string[] {
  const errors: string[] = [];
  if (!input || typeof input !== 'object' || Array.isArray(input)) return ['package must be an object'];
  const value = input as Partial<AppPackageV2>;
  if (value.schemaVersion !== 'wonder.app-package.v2') errors.push('schemaVersion must be wonder.app-package.v2');
  if (!value.id || typeof value.id !== 'string') errors.push('id is required');
  if (!value.version || typeof value.version !== 'string') errors.push('version is required');
  if (!value.collections || typeof value.collections !== 'object') errors.push('collections are required');
  if (!value.queries || typeof value.queries !== 'object') errors.push('queries are required');
  if (!value.views || typeof value.views !== 'object') errors.push('views are required');
  if (!Array.isArray(value.rules)) errors.push('rules must be an array');
  if (!Array.isArray(value.capabilities)) errors.push('capabilities must be an array');
  if (!Array.isArray(value.acceptanceTests)) errors.push('acceptanceTests must be an array');

  for (const [id, collection] of Object.entries(value.collections ?? {})) {
    if (!collection || typeof collection !== 'object' || collection.id !== id) errors.push(`collection ${id} must have matching id`);
  }
  for (const [id, query] of Object.entries(value.queries ?? {})) {
    if (!query || typeof query !== 'object' || typeof query.from !== 'string') errors.push(`query ${id} must declare from`);
  }
  for (const [id, view] of Object.entries(value.views ?? {})) {
    if (!view || typeof view !== 'object' || view.id !== id) errors.push(`view ${id} must have matching id`);
    if (!view || typeof view !== 'object' || typeof view.query !== 'string') errors.push(`view ${id} must reference a query`);
  }
  return errors;
}
