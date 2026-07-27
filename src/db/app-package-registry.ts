import type { SQLiteDatabase } from 'expo-sqlite';
import jsonPatch from 'fast-json-patch';
import type { Operation as JsonPatchOperation } from 'fast-json-patch';

import { buildAppPackageFromManifest } from '@/src/domain/app-package-bridge';
import { canonicalJson, sha256Canonical } from '@/src/domain/canonical-json';
import { getBundledDomainManifest, setActivePackageOverride } from '@/src/domain/catalog';
import {
  collectAppPackageValidationIssues,
  formatAppPackageValidationIssues,
  type AppPackage,
  type AppPackageV2,
  type AppPackageV3,
} from '@/packages/shared/contracts/package';
import { isAllowedAppPackagePatchPath } from '@/packages/shared/contracts/package-change';

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

export type AppPackageChangeRequest = Readonly<{
  patch: readonly JsonPatchOperation[];
  basePackageKey?: string | null;
  requestedBy?: string;
}>;

export type AppPackageChangeApprovalReceipt = Readonly<{
  schemaVersion: 'wonder.package-change-approval.v1';
  approved: true;
  requestHash: string;
  packageHash: string;
  approvedBy: string;
  approvedAt: string;
}>;

export type AppPackageChangePreview = Readonly<{
  status: 'valid' | 'invalid';
  requestHash: string;
  packageHash: string | null;
  basePackageKey: string | null;
  package: AppPackage | null;
  errors: string[];
}>;

export async function bootstrapAppPackageRegistry(db: SQLiteDatabase): Promise<AppPackage> {
  const manifest = getBundledDomainManifest();
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

export async function previewAppPackageChange(
  db: SQLiteDatabase,
  request: AppPackageChangeRequest,
): Promise<AppPackageChangePreview> {
  const active = await getActiveAppPackage(db);
  if (!active) throw new Error('package_change_no_active_package');
  validatePackageChangeRequest(request, active);

  const requestHash = hashValue(normalizePackageChangeRequest(request));
  const basePackageKey = packageKey(active);
  try {
    const next = applyPackagePatch(active, request.patch);
    assertAppPackageShape(next);
    return {
      status: 'valid',
      requestHash,
      packageHash: hashValue(next),
      basePackageKey,
      package: next,
      errors: [],
    };
  } catch (error) {
    return {
      status: 'invalid',
      requestHash,
      packageHash: null,
      basePackageKey,
      package: null,
      errors: [error instanceof Error ? error.message : 'package_change_invalid'],
    };
  }
}

export async function activateApprovedAppPackageChange(
  db: SQLiteDatabase,
  request: AppPackageChangeRequest,
  approval: AppPackageChangeApprovalReceipt,
): Promise<AppPackage> {
  const preview = await previewAppPackageChange(db, request);
  if (preview.status !== 'valid' || !preview.packageHash || !preview.package) {
    throw new Error(`package_change_invalid:${preview.errors.join('|') || 'package_change_invalid'}`);
  }
  if (
    approval.schemaVersion !== 'wonder.package-change-approval.v1'
    || approval.approved !== true
    || approval.requestHash !== preview.requestHash
    || approval.packageHash !== preview.packageHash
    || !approval.approvedBy?.trim()
    || Number.isNaN(Date.parse(approval.approvedAt))
  ) {
    throw new Error('package_change_approval_mismatch');
  }
  return activateAppPackage(db, preview.package, 'activate', {
    requestHash: preview.requestHash,
    packageHash: preview.packageHash,
    approvalHash: hashValue(approval),
    approvedBy: approval.approvedBy.trim(),
  });
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
  if (active.presentation?.sourceSchemaVersion === undefined) return false;
  if (active.version === bundledPackage.version) return false;
  const sourceSchemaVersion = active.presentation?.sourceSchemaVersion;
  return typeof sourceSchemaVersion === 'string' && sourceSchemaVersion.length > 0;
}

function normalizePackageChangeRequest(request: AppPackageChangeRequest): AppPackageChangeRequest {
  return {
    basePackageKey: request.basePackageKey ?? null,
    requestedBy: request.requestedBy?.trim() || 'mobile-package-builder',
    patch: request.patch,
  };
}

function validatePackageChangeRequest(request: AppPackageChangeRequest, active: AppPackage): void {
  if (!request || typeof request !== 'object' || Array.isArray(request)) throw new Error('package_change_request_invalid');
  if (request.basePackageKey && request.basePackageKey !== packageKey(active)) throw new Error('package_change_base_mismatch');
  if (!Array.isArray(request.patch) || request.patch.length < 1 || request.patch.length > 64) throw new Error('package_change_patch_invalid');
  for (const operation of request.patch) {
    if (!operation || typeof operation !== 'object' || typeof operation.path !== 'string') throw new Error('package_change_patch_invalid');
    if (!['add', 'replace', 'remove', 'move', 'copy', 'test'].includes(operation.op)) throw new Error('package_change_patch_op_invalid');
    if (!isAllowedAppPackagePatchPath(operation.path)) throw new Error(`package_change_path_forbidden:${operation.path}`);
    if ((operation.op === 'move' || operation.op === 'copy') && (!operation.from || !isAllowedAppPackagePatchPath(operation.from))) {
      throw new Error(`package_change_path_forbidden:${operation.from ?? '<missing>'}`);
    }
  }
}

function applyPackagePatch(base: AppPackage, patch: readonly JsonPatchOperation[]): AppPackage {
  const clone = JSON.parse(JSON.stringify(base)) as AppPackage;
  const result = jsonPatch.applyPatch(clone, [...patch], true, false);
  return result.newDocument as AppPackage;
}

function hashValue(value: unknown): string {
  return sha256Canonical(value);
}

function stableJson(value: unknown): string {
  return canonicalJson(value);
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
  const errors = formatAppPackageValidationIssues(collectAppPackageValidationIssues(input, 'wonder.app-package.v2'));
  if (errors.length) {
    throw new Error(`app_package_invalid:${errors.join('|')}`);
  }
}

function assertAppPackageShapeV3(input: unknown): asserts input is AppPackageV3 {
  const errors = formatAppPackageValidationIssues(collectAppPackageValidationIssues(input, 'wonder.app-package.v3'));
  if (errors.length) {
    throw new Error(`app_package_invalid:${errors.join('|')}`);
  }
}
