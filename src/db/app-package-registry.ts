import type { SQLiteDatabase } from 'expo-sqlite';
import jsonPatch from 'fast-json-patch';
import type { Operation as JsonPatchOperation } from 'fast-json-patch';

import { buildAppPackageFromManifest } from '@/src/domain/app-package-bridge';
import { canonicalJson, sha256Canonical } from '@/src/domain/canonical-json';
import { getBundledDomainManifest, setActivePackageOverride } from '@/src/domain/catalog';
import { loadAppPackage } from '@/src/domain/package-loader';
import {
  DEFAULT_APP_INSTALLATION_ID,
  DEFAULT_WORKSPACE_ID,
  parseAppInstallation,
  type AppInstallation as LocalAppInstallation,
  type AppInstallationId,
  type AppInstallationStatus,
  type WorkspaceId,
} from '@/packages/shared/contracts/app-installation';
import {
  collectAppPackageValidationIssues,
  formatAppPackageValidationIssues,
  type AppPackage,
  type AppPackageV2,
  type AppPackageV3,
} from '@/packages/shared/contracts/package';
import { isAllowedAppPackagePatchPath } from '@/packages/shared/contracts/package-change';
import {
  assertPackageInstallApprovalMatchesPreview,
  hashPackageInstallApprovalReceipt,
  type PackageInstallApprovalReceipt,
  type PackageInstallPreview,
} from '@/packages/shared/contracts/package-install';

type AppPackageRow = {
  package_key: string;
  payload_json: string;
};

type AppPackageStateRow = {
  active_package_key: string | null;
  previous_package_key: string | null;
};

type AppInstallationRow = {
  installation_id: string;
  workspace_id: string;
  app_name: string;
  status: AppInstallationStatus;
  created_at: string;
  updated_at: string;
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

export type ApprovedPackageInstallRequest = Readonly<{
  packageJson: unknown;
  preview: PackageInstallPreview;
  approval: PackageInstallApprovalReceipt;
  installationId?: string;
  workspaceId?: WorkspaceId;
  now?: string;
}>;

export async function bootstrapAppPackageRegistry(db: SQLiteDatabase): Promise<AppPackage>;
export async function bootstrapAppPackageRegistry(db: SQLiteDatabase, installationId: AppInstallationId): Promise<AppPackage>;
export async function bootstrapAppPackageRegistry(
  db: SQLiteDatabase,
  installationId: AppInstallationId = DEFAULT_APP_INSTALLATION_ID,
): Promise<AppPackage> {
  const scopedInstallationId = normalizeInstallationId(installationId);
  await ensureDefaultAppInstallation(db);
  const manifest = getBundledDomainManifest();
  const bundledPackage = loadAppPackage(buildAppPackageFromManifest(manifest).package).activePackage;
  const active = await getActiveAppPackage(db, scopedInstallationId);
  if (active) {
    if (shouldRefreshBundledPackage(active, bundledPackage)) {
      return activateAppPackage(db, scopedInstallationId, bundledPackage, 'bootstrap', { packageHash: bundledPackage.version });
    }
    setActivePackageOverride(active);
    return active;
  }
  const installedCount = await getInstalledPackageCount(db);
  if (installedCount > 0) {
    throw new Error('app_package_active_missing');
  }

  await activateAppPackage(db, scopedInstallationId, bundledPackage, 'bootstrap');
  setActivePackageOverride(bundledPackage);
  return bundledPackage;
}

export async function getActiveAppPackage(db: SQLiteDatabase): Promise<AppPackage | null>;
export async function getActiveAppPackage(db: SQLiteDatabase, installationId: AppInstallationId): Promise<AppPackage | null>;
export async function getActiveAppPackage(
  db: SQLiteDatabase,
  installationId: AppInstallationId = DEFAULT_APP_INSTALLATION_ID,
): Promise<AppPackage | null> {
  const state = await getPackageState(db, normalizeInstallationId(installationId));
  if (!state?.active_package_key) return null;
  const appPackage = await getPackageByKey(db, state.active_package_key);
  if (appPackage) setActivePackageOverride(appPackage);
  return appPackage;
}

export async function activateAppPackage(
  db: SQLiteDatabase,
  candidate: unknown,
  action?: ReceiptAction,
  evidence?: AppPackageReceiptEvidence,
): Promise<AppPackage>;
export async function activateAppPackage(
  db: SQLiteDatabase,
  installationId: AppInstallationId,
  candidate: unknown,
  action?: ReceiptAction,
  evidence?: AppPackageReceiptEvidence,
): Promise<AppPackage>;
export async function activateAppPackage(
  db: SQLiteDatabase,
  installationIdOrCandidate: AppInstallationId | unknown,
  candidateOrAction?: unknown | ReceiptAction,
  actionOrEvidence: ReceiptAction | AppPackageReceiptEvidence = 'activate',
  maybeEvidence: AppPackageReceiptEvidence = {},
): Promise<AppPackage> {
  const { installationId, candidate, action, evidence } = normalizeActivateArgs(
    installationIdOrCandidate,
    candidateOrAction,
    actionOrEvidence,
    maybeEvidence,
  );
  await ensureDefaultAppInstallation(db);
  await assertAppInstallationExists(db, installationId);
  const appPackage = loadAppPackage(candidate).activePackage;
  const now = new Date().toISOString();
  const key = packageKey(appPackage);
  const previous = await getPackageState(db, installationId);

  await db.withTransactionAsync(async () => {
    await storeAppPackage(db, appPackage, now);
    await db.runAsync(
      `INSERT OR REPLACE INTO app_installation_package_state
        (installation_id, active_package_key, previous_package_key, updated_at)
        VALUES ($installation_id, $active_package_key, $previous_package_key, $updated_at)`,
      {
        $installation_id: installationId,
        $active_package_key: key,
        $previous_package_key: previous?.active_package_key ?? null,
        $updated_at: now,
      },
    );
    if (installationId === DEFAULT_APP_INSTALLATION_ID) {
      await writeLegacyDefaultPackageState(db, key, previous?.active_package_key ?? null, now);
    }
    await insertReceipt(db, action, key, previous?.active_package_key ?? null, now, evidence, installationId);
  });

  setActivePackageOverride(appPackage);
  return appPackage;
}

export async function installApprovedAppPackage(
  db: SQLiteDatabase,
  request: ApprovedPackageInstallRequest,
): Promise<LocalAppInstallation> {
  assertPackageInstallApprovalMatchesPreview(request.approval, request.preview);
  const appPackage = loadAppPackage(request.packageJson).activePackage;
  const packageHash = hashValue(appPackage);
  if (
    appPackage.id !== request.preview.packageId
    || appPackage.version !== request.preview.version
    || packageHash !== request.approval.checksum
  ) {
    throw new Error('package_install_payload_mismatch');
  }

  const now = request.now ?? new Date().toISOString();
  if (Number.isNaN(Date.parse(now))) throw new Error('package_install_time_invalid');
  const key = packageKey(appPackage);
  const installationId = normalizeInstallationId(request.installationId ?? createInstallationId());
  const workspaceId = normalizeWorkspaceId(request.workspaceId ?? DEFAULT_WORKSPACE_ID);
  await ensureWorkspace(db, workspaceId, now);
  const approvalHash = hashPackageInstallApprovalReceipt(request.approval);
  const installation = buildAppInstallation({
    preview: request.preview,
    installationId,
    workspaceId,
    now,
  });
  const previous = await getPackageState(db, installation.id);

  await db.withTransactionAsync(async () => {
    await storeAppPackage(db, appPackage, now);
    await db.runAsync(
      `INSERT INTO app_installations
        (id, workspace_id, label, status, created_at, updated_at)
        VALUES ($id, $workspace_id, $label, $status, $created_at, $updated_at)`,
      {
        $id: installation.id,
        $workspace_id: installation.workspaceId,
        $label: installation.label,
        $status: installation.status,
        $created_at: installation.createdAt,
        $updated_at: installation.updatedAt,
      },
    );
    await db.runAsync(
      `INSERT OR REPLACE INTO app_installation_package_state
        (installation_id, active_package_key, previous_package_key, updated_at)
        VALUES ($installation_id, $active_package_key, $previous_package_key, $updated_at)`,
      {
        $installation_id: installation.id,
        $active_package_key: key,
        $previous_package_key: previous?.active_package_key ?? null,
        $updated_at: now,
      },
    );
    if (installation.id === DEFAULT_APP_INSTALLATION_ID) {
      await writeLegacyDefaultPackageState(db, key, previous?.active_package_key ?? null, now);
    }
    await insertReceipt(db, 'activate', key, previous?.active_package_key ?? null, now, {
      packageHash,
      approvalHash,
      approvedBy: request.approval.approvedBy,
    }, installation.id);
  });

  setActivePackageOverride(appPackage);
  return installation;
}

export async function getActiveAppInstallation(db: SQLiteDatabase): Promise<LocalAppInstallation | null> {
  const row = await db.getFirstAsync<AppInstallationRow>(
    `SELECT installation_id, workspace_id, app_name, status, created_at, updated_at
      FROM app_installations WHERE status = 'active' ORDER BY updated_at DESC LIMIT 1`,
  );
  return row ? localAppInstallationFromRow(row) : null;
}

export async function getPackageInstallAppInstallation(
  db: SQLiteDatabase,
  installationId: string,
): Promise<LocalAppInstallation | null> {
  return getAppInstallation(db, installationId);
}

export async function createAppInstallation(
  db: SQLiteDatabase,
  input: {
    id: AppInstallationId;
    workspaceId?: WorkspaceId;
    label: string;
    status?: AppInstallationStatus;
    now?: string;
  },
): Promise<LocalAppInstallation> {
  const now = input.now ?? new Date().toISOString();
  const installation = parseAppInstallation({
    id: normalizeInstallationId(input.id),
    workspaceId: normalizeWorkspaceId(input.workspaceId ?? DEFAULT_WORKSPACE_ID),
    label: input.label,
    status: input.status ?? 'active',
    createdAt: now,
    updatedAt: now,
  });
  await ensureWorkspace(db, installation.workspaceId, now);
  await db.runAsync(
    `INSERT OR REPLACE INTO app_installations
      (installation_id, workspace_id, app_name, status, launch_path, created_at, updated_at)
      VALUES ($installation_id, $workspace_id, $app_name, $status, $launch_path, $created_at, $updated_at)`,
    {
      $installation_id: installation.id,
      $workspace_id: installation.workspaceId,
      $app_name: installation.label,
      $status: installation.status,
      $launch_path: `/apps/${encodeURIComponent(installation.id)}`,
      $created_at: installation.createdAt,
      $updated_at: installation.updatedAt,
    },
  );
  return installation;
}

export async function listAppInstallations(
  db: SQLiteDatabase,
  workspaceId: WorkspaceId = DEFAULT_WORKSPACE_ID,
): Promise<LocalAppInstallation[]> {
  const rows = await db.getAllAsync<AppInstallationRow>(
    `SELECT installation_id, workspace_id, app_name, status, created_at, updated_at
      FROM app_installations
      WHERE workspace_id = $workspace_id
      ORDER BY created_at ASC, installation_id ASC`,
    { $workspace_id: normalizeWorkspaceId(workspaceId) },
  );
  return rows.map(localAppInstallationFromRow);
}

export async function getAppInstallation(
  db: SQLiteDatabase,
  installationId: AppInstallationId,
): Promise<LocalAppInstallation | null> {
  const row = await db.getFirstAsync<AppInstallationRow>(
    `SELECT installation_id, workspace_id, app_name, status, created_at, updated_at
      FROM app_installations WHERE installation_id = $installation_id`,
    { $installation_id: normalizeInstallationId(installationId) },
  );
  return row ? localAppInstallationFromRow(row) : null;
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
    const next = loadAppPackage(applyPackagePatch(active, request.patch)).activePackage;
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

export async function rollbackAppPackage(db: SQLiteDatabase): Promise<AppPackage | null>;
export async function rollbackAppPackage(db: SQLiteDatabase, installationId: AppInstallationId): Promise<AppPackage | null>;
export async function rollbackAppPackage(
  db: SQLiteDatabase,
  installationId: AppInstallationId = DEFAULT_APP_INSTALLATION_ID,
): Promise<AppPackage | null> {
  const scopedInstallationId = normalizeInstallationId(installationId);
  const state = await getPackageState(db, scopedInstallationId);
  if (!state?.previous_package_key) return null;
  const previousPackage = await getPackageByKey(db, state.previous_package_key);
  if (!previousPackage) return null;

  const now = new Date().toISOString();
  await db.withTransactionAsync(async () => {
    await db.runAsync(
      `INSERT OR REPLACE INTO app_installation_package_state
        (installation_id, active_package_key, previous_package_key, updated_at)
        VALUES ($installation_id, $active_package_key, NULL, $updated_at)`,
      {
        $installation_id: scopedInstallationId,
        $active_package_key: state.previous_package_key,
        $updated_at: now,
      },
    );
    if (scopedInstallationId === DEFAULT_APP_INSTALLATION_ID) {
      await writeLegacyDefaultPackageState(db, state.previous_package_key, null, now);
    }
    await insertReceipt(db, 'rollback', state.previous_package_key, state.active_package_key, now, {}, scopedInstallationId);
  });

  setActivePackageOverride(previousPackage);
  return previousPackage;
}

function packageKey(appPackage: AppPackage): string {
  return `${appPackage.id}@${appPackage.version}`;
}

async function storeAppPackage(db: SQLiteDatabase, appPackage: AppPackage, now: string): Promise<void> {
  await db.runAsync(
    `INSERT OR REPLACE INTO app_packages
      (package_key, package_id, version, payload_json, created_at, updated_at)
      VALUES ($package_key, $package_id, $version, $payload_json, $created_at, $updated_at)`,
    {
      $package_key: packageKey(appPackage),
      $package_id: appPackage.id,
      $version: appPackage.version,
      $payload_json: JSON.stringify(appPackage),
      $created_at: now,
      $updated_at: now,
    },
  );
}

function buildAppInstallation(input: {
  preview: PackageInstallPreview;
  installationId: string;
  workspaceId: WorkspaceId;
  now: string;
}): LocalAppInstallation {
  const installationId = input.installationId.trim();
  if (!installationId) throw new Error('package_install_installation_id_required');
  return parseAppInstallation({
    id: installationId,
    workspaceId: input.workspaceId,
    label: input.preview.appName,
    status: 'active',
    createdAt: input.now,
    updatedAt: input.now,
  });
}

function createInstallationId(): string {
  const randomUUID = globalThis.crypto?.randomUUID?.bind(globalThis.crypto);
  if (randomUUID) return `inst_${randomUUID()}`;
  return `inst_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;
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

function localAppInstallationFromRow(row: AppInstallationRow): LocalAppInstallation {
  return parseAppInstallation({
    id: row.installation_id,
    workspaceId: row.workspace_id,
    label: row.app_name,
    status: row.status,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  });
}

function normalizeInstallationId(value: AppInstallationId): AppInstallationId {
  const id = String(value ?? '').trim();
  if (!id) throw new Error('app_installation_id_required');
  return id;
}

function normalizeWorkspaceId(value: WorkspaceId): WorkspaceId {
  const id = String(value ?? '').trim();
  if (!id) throw new Error('workspace_id_required');
  return id;
}

function normalizeActivateArgs(
  installationIdOrCandidate: AppInstallationId | unknown,
  candidateOrAction: unknown | ReceiptAction,
  actionOrEvidence: ReceiptAction | AppPackageReceiptEvidence,
  maybeEvidence: AppPackageReceiptEvidence,
): {
  installationId: AppInstallationId;
  candidate: unknown;
  action: ReceiptAction;
  evidence: AppPackageReceiptEvidence;
} {
  if (typeof installationIdOrCandidate === 'string' && !isReceiptAction(candidateOrAction)) {
    return {
      installationId: normalizeInstallationId(installationIdOrCandidate),
      candidate: candidateOrAction,
      action: isReceiptAction(actionOrEvidence) ? actionOrEvidence : 'activate',
      evidence: isReceiptAction(actionOrEvidence) ? maybeEvidence : actionOrEvidence,
    };
  }

  return {
    installationId: DEFAULT_APP_INSTALLATION_ID,
    candidate: installationIdOrCandidate,
    action: isReceiptAction(candidateOrAction) ? candidateOrAction : 'activate',
    evidence: isReceiptAction(actionOrEvidence) ? maybeEvidence : actionOrEvidence,
  };
}

function isReceiptAction(value: unknown): value is ReceiptAction {
  return value === 'bootstrap' || value === 'activate' || value === 'rollback';
}

async function ensureDefaultWorkspace(db: SQLiteDatabase): Promise<void> {
  await ensureWorkspace(db, DEFAULT_WORKSPACE_ID, new Date().toISOString());
}

async function ensureDefaultAppInstallation(db: SQLiteDatabase): Promise<void> {
  const now = new Date().toISOString();
  await ensureWorkspace(db, DEFAULT_WORKSPACE_ID, now);
  const existing = await getAppInstallation(db, DEFAULT_APP_INSTALLATION_ID);
  if (existing) return;
  await createAppInstallation(db, {
    id: DEFAULT_APP_INSTALLATION_ID,
    workspaceId: DEFAULT_WORKSPACE_ID,
    label: 'Default app',
    now,
  });
}

async function ensureWorkspace(db: SQLiteDatabase, workspaceId: WorkspaceId, now: string): Promise<void> {
  await db.runAsync(
    `INSERT OR IGNORE INTO workspaces
      (id, label, created_at, updated_at)
      VALUES ($id, $label, $created_at, $updated_at)`,
    {
      $id: normalizeWorkspaceId(workspaceId),
      $label: workspaceId === DEFAULT_WORKSPACE_ID ? 'Default workspace' : workspaceId,
      $created_at: now,
      $updated_at: now,
    },
  );
}

async function assertAppInstallationExists(db: SQLiteDatabase, installationId: AppInstallationId): Promise<void> {
  const row = await getAppInstallation(db, installationId);
  if (!row) throw new Error(`app_installation_not_found:${installationId}`);
}

async function getPackageState(
  db: SQLiteDatabase,
  installationId: AppInstallationId = DEFAULT_APP_INSTALLATION_ID,
): Promise<AppPackageStateRow | null> {
  const scoped = await db.getFirstAsync<AppPackageStateRow>(
    `SELECT active_package_key, previous_package_key FROM app_installation_package_state WHERE installation_id = $installation_id`,
    { $installation_id: normalizeInstallationId(installationId) },
  );
  if (scoped || installationId !== DEFAULT_APP_INSTALLATION_ID) return scoped;
  return getLegacyPackageState(db);
}

async function getLegacyPackageState(db: SQLiteDatabase): Promise<AppPackageStateRow | null> {
  return db.getFirstAsync<AppPackageStateRow>(
    `SELECT active_package_key, previous_package_key FROM app_package_state WHERE id = 'default'`,
  );
}

async function writeLegacyDefaultPackageState(
  db: SQLiteDatabase,
  activePackageKey: string | null,
  previousPackageKey: string | null,
  now: string,
): Promise<void> {
  await db.runAsync(
    `INSERT OR REPLACE INTO app_package_state
      (id, active_package_key, previous_package_key, updated_at)
      VALUES ('default', $active_package_key, $previous_package_key, $updated_at)`,
    {
      $active_package_key: activePackageKey,
      $previous_package_key: previousPackageKey,
      $updated_at: now,
    },
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
  const appPackage = loadAppPackage(parsed).activePackage;
  if (packageKey(appPackage) !== row.package_key) {
    throw new Error(`app_package_key_mismatch:${row.package_key}`);
  }
  return appPackage;
}

async function insertReceipt(
  db: SQLiteDatabase,
  action: ReceiptAction,
  packageKeyValue: string | null,
  previousPackageKey: string | null,
  now: string,
  evidence: AppPackageReceiptEvidence,
  installationId: string = DEFAULT_APP_INSTALLATION_ID,
): Promise<void> {
  await db.runAsync(
    `INSERT INTO app_package_receipts
      (id, action, package_key, previous_package_key, created_at, request_hash, package_hash, approval_hash, approved_by)
      VALUES ($id, $action, $package_key, $previous_package_key, $created_at, $request_hash, $package_hash, $approval_hash, $approved_by)`,
    {
      $id: `app-package:${normalizeInstallationId(installationId)}:${action}:${packageKeyValue ?? 'none'}:${now}`,
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
