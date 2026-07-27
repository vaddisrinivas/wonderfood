import type { SQLiteDatabase } from 'expo-sqlite';

import { buildAppPackageFromManifest } from '@/src/domain/app-package-bridge';
import { loadCatalog, setActivePackageOverride } from '@/src/domain/catalog';
import type { AppPackage, AppPackageContractLock, AppPackageNativeCapability, AppPackageV2, AppPackageV3 } from '@/packages/shared/contracts/package';

type AppPackageRow = {
  package_key: string;
  payload_json: string;
};

type AppPackageStateRow = {
  active_package_key: string | null;
  previous_package_key: string | null;
};

type ReceiptAction = 'bootstrap' | 'activate' | 'rollback';

export type AppPackageReceiptEvidence = {
  requestHash?: string;
  packageHash?: string;
  approvalHash?: string;
  approvedBy?: string;
};

export async function bootstrapAppPackageRegistry(db: SQLiteDatabase): Promise<AppPackage> {
  const manifest = loadCatalog().activeManifest;
  const bundledPackage = buildAppPackageFromManifest(manifest).package;
  const active = await getActiveAppPackage(db);
  if (active) {
    if (shouldRefreshBundledPackage(active, bundledPackage)) {
      return activateAppPackage(db, bundledPackage, 'bootstrap', { packageHash: bundledPackage.version });
    }
    setActivePackageOverride(active);
    return active;
  }
  const installedCount = await getInstalledPackageCount(db);
  if (installedCount > 0) {
    throw new Error('app_package_active_missing');
  }

  await activateAppPackage(db, bundledPackage, 'bootstrap');
  setActivePackageOverride(bundledPackage);
  return bundledPackage;
}

export async function getActiveAppPackage(db: SQLiteDatabase): Promise<AppPackage | null> {
  const state = await getPackageState(db);
  if (!state?.active_package_key) return null;
  const appPackage = await getPackageByKey(db, state.active_package_key);
  if (appPackage) setActivePackageOverride(appPackage);
  return appPackage;
}

export async function activateAppPackage(
  db: SQLiteDatabase,
  appPackage: AppPackage,
  action: ReceiptAction = 'activate',
  evidence: AppPackageReceiptEvidence = {},
): Promise<AppPackage> {
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
    await insertReceipt(db, action, key, previous?.active_package_key ?? null, now, evidence);
  });

  setActivePackageOverride(appPackage);
  return appPackage;
}

export async function rollbackAppPackage(db: SQLiteDatabase): Promise<AppPackage | null> {
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
    await insertReceipt(db, 'rollback', state.previous_package_key, state.active_package_key, now, {});
  });

  setActivePackageOverride(previousPackage);
  return previousPackage;
}

function packageKey(appPackage: AppPackage): string {
  return `${appPackage.id}@${appPackage.version}`;
}

function shouldRefreshBundledPackage(active: AppPackage, bundledPackage: AppPackage): boolean {
  if (active.id !== bundledPackage.id) return false;
  if (active.version === bundledPackage.version) return false;
  const sourceSchemaVersion = active.presentation?.sourceSchemaVersion;
  return typeof sourceSchemaVersion === 'string' && sourceSchemaVersion.length > 0;
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

async function getPackageByKey(db: SQLiteDatabase, key: string): Promise<AppPackage | null> {
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
  evidence: AppPackageReceiptEvidence,
): Promise<void> {
  await db.runAsync(
    `INSERT INTO app_package_receipts
      (id, action, package_key, previous_package_key, created_at, request_hash, package_hash, approval_hash, approved_by)
      VALUES ($id, $action, $package_key, $previous_package_key, $created_at, $request_hash, $package_hash, $approval_hash, $approved_by)`,
    {
      $id: `app-package:${action}:${packageKeyValue ?? 'none'}:${now}`,
      $action: action,
      $package_key: packageKeyValue,
      $previous_package_key: previousPackageKey,
      $created_at: now,
      $request_hash: evidence.requestHash ?? null,
      $package_hash: evidence.packageHash ?? null,
      $approval_hash: evidence.approvalHash ?? null,
      $approved_by: evidence.approvedBy?.trim() || null,
    },
  );
}

function assertAppPackageShape(input: unknown): asserts input is AppPackage {
  const value = input as Partial<AppPackage>;
  if (value.schemaVersion === 'wonder.app-package.v2') {
    assertAppPackageShapeV2(value);
    return;
  }
  if (value.schemaVersion === 'wonder.app-package.v3') {
    assertAppPackageShapeV3(value);
    return;
  }
  throw new Error('app_package_invalid:schemaVersion must be wonder.app-package.v2 or wonder.app-package.v3');
}

function assertAppPackageShapeV2(input: unknown): asserts input is AppPackageV2 {
  const errors = collectAppPackageShapeErrors(input);
  if (errors.length) {
    throw new Error(`app_package_invalid:${errors.join('|')}`);
  }
}

function assertAppPackageShapeV3(input: unknown): asserts input is AppPackageV3 {
  const errors: string[] = [];
  const value = input as Partial<AppPackageV3>;
  if (!value.id || typeof value.id !== 'string') errors.push('id is required');
  if (!value.version || typeof value.version !== 'string') errors.push('version is required');
  if (!Array.isArray(value.dependencyPins)) errors.push('dependencyPins must be an array');
  else {
    for (const pin of value.dependencyPins) {
      if (!isAppPackageDependencyPin(pin)) errors.push('dependencyPins entries must include package and version');
    }
  }
  if (!isAppPackageNativeCapability(value.nativeCapabilities)) {
    errors.push('nativeCapabilities is required');
  }
  if (!isAppPackageContractLock(value.contractLock)) {
    errors.push('contractLock is required');
  }

  if (errors.length) {
    throw new Error(`app_package_invalid:${errors.join('|')}`);
  }
}

function collectAppPackageShapeErrors(input: unknown): string[] {
  const errors: string[] = [];
  if (!input || typeof input !== 'object' || Array.isArray(input)) return ['package must be an object'];
  const value = input as Partial<AppPackage>;
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

function isAppPackageDependencyPin(input: unknown): boolean {
  if (!input || typeof input !== 'object') return false;
  const pin = input as Partial<unknown> as { package?: unknown; version?: unknown };
  return typeof pin.package === 'string' && pin.package.trim().length > 0 && typeof pin.version === 'string' && pin.version.trim().length > 0;
}

function isAppPackageNativeCapability(input: unknown): input is AppPackageNativeCapability {
  if (!input || typeof input !== 'object') return false;
  const capability = input as Partial<AppPackageNativeCapability>;
  return capability.schemaVersion === 'wonder.app-package-native-capabilities.v1'
    && (capability.platform === 'expo' || capability.platform === 'android' || capability.platform === 'ios' || capability.platform === 'web')
    && Array.isArray(capability.packages)
    && capability.packages.every((item) => typeof item === 'string')
    && (
      capability.permissions === undefined
      || (
        Array.isArray(capability.permissions)
        && capability.permissions.every((item) => {
          if (typeof item === 'string') return item.trim().length > 0;
          if (!item || typeof item !== 'object' || Array.isArray(item)) return false;
          const permission = item as Record<string, unknown>;
          return typeof permission.id === 'string'
            && permission.id.trim().length > 0
            && (permission.platform === 'expo' || permission.platform === 'android' || permission.platform === 'ios' || permission.platform === 'web')
            && typeof permission.permission === 'string'
            && permission.permission.trim().length > 0
            && typeof permission.reason === 'string'
            && permission.reason.trim().length > 0
            && (permission.required === undefined || typeof permission.required === 'boolean')
            && (permission.prompt === undefined || typeof permission.prompt === 'string');
        })
      )
    );
}

function isAppPackageContractLock(input: unknown): input is AppPackageContractLock {
  if (!input || typeof input !== 'object') return false;
  const lock = input as Partial<AppPackageContractLock>;
  return lock.schemaVersion === 'wonder.package-contract-lock.v1'
    && typeof lock.algorithm === 'string'
    && lock.algorithm === 'sha256'
    && typeof lock.checksum === 'string'
    && /^sha256:[a-f0-9]{64}$/.test(lock.checksum)
    && typeof lock.pinnedAt === 'string'
    && !Number.isNaN(Date.parse(lock.pinnedAt))
    && Array.isArray(lock.dependencyPins)
    && lock.dependencyPins.every((pin) => isAppPackageDependencyPin(pin))
    && isAppPackageNativeCapability(lock.nativeCapabilities);
}
