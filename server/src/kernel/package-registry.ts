import { validateComputedFieldGraph } from './computed-fields';
import { type AppPackage, type PackageValidation, validateAppPackage } from './package';
import { isAllowedAppPackagePatchPath } from '@/packages/shared/contracts/package-change';
import { sha256Canonical } from '@/src/domain/canonical-json';
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import jsonPatch from 'fast-json-patch';
import type { Operation } from 'fast-json-patch';
import { z } from 'zod';

const PACKAGE_REGISTRY_SCHEMA_VERSION = 'wonder.package-registry.v1' as const;

export type PackageRegistryReceipt = Readonly<{
  id: string;
  action: 'activate' | 'rollback';
  packageKey: string | null;
  previousPackageKey: string | null;
  createdAt: string;
  requestHash?: string;
  packageHash?: string;
  approvalHash?: string;
  approvedBy?: string;
}>;

export type PackageChangeRequest = Readonly<{
  patch: readonly Operation[];
  basePackageKey?: string | null;
  requestedBy?: string;
}>;

export type PackageChangeApprovalReceipt = Readonly<{
  schemaVersion: 'wonder.package-change-approval.v1';
  approved: true;
  requestHash: string;
  packageHash: string;
  approvedBy: string;
  approvedAt: string;
}>;

export type PackageChangePreview = Readonly<{
  status: 'valid' | 'invalid';
  requestHash: string;
  packageHash: string | null;
  basePackageKey: string | null;
  package: AppPackage | null;
  validation: PackageValidation;
}>;

type PackageRegistryStore = Readonly<{
  schemaVersion: typeof PACKAGE_REGISTRY_SCHEMA_VERSION;
  activeKey: string | null;
  previousKey: string | null;
  packages: Readonly<Record<string, AppPackage>>;
  receipts: readonly PackageRegistryReceipt[];
}>;

type PackageRegistryOptions = {
  path?: string;
  now?: () => string;
};

const packageRegistryReceiptSchema = z.object({
  id: z.string().min(1),
  action: z.enum(['activate', 'rollback']),
  packageKey: z.string().min(1).nullable(),
  previousPackageKey: z.string().min(1).nullable(),
  createdAt: z.string().refine((value) => !Number.isNaN(Date.parse(value)), 'invalid timestamp'),
  requestHash: z.string().min(1).optional(),
  packageHash: z.string().min(1).optional(),
  approvalHash: z.string().min(1).optional(),
  approvedBy: z.string().min(1).optional(),
}).strict();

const packageRegistryStoreSchema = z.object({
  schemaVersion: z.literal(PACKAGE_REGISTRY_SCHEMA_VERSION),
  activeKey: z.string().min(1).nullable(),
  previousKey: z.string().min(1).nullable(),
  packages: z.record(z.string(), z.unknown()),
  receipts: z.array(packageRegistryReceiptSchema),
}).strict();

export class PackageRegistry {
  private active: AppPackage | null = null;
  private previous: AppPackage | null = null;
  private packages = new Map<string, AppPackage>();
  private receipts: PackageRegistryReceipt[] = [];
  private readonly path?: string;
  private readonly now: () => string;

  constructor(options: PackageRegistryOptions = {}) {
    this.path = options.path;
    this.now = options.now ?? (() => new Date().toISOString());
    if (this.path) {
      this.load(this.path);
    }
  }

  preview(input: unknown): PackageValidation {
    const result = validateAppPackage(input);
    if (!result.valid) return result;
    try {
      validateComputedFieldGraph({
        specs: result.package.computedFields ?? [],
        collections: Object.keys(result.package.collections),
      });
      return result;
    } catch (error) {
      return {
        valid: false,
        errors: [error instanceof Error ? error.message : 'computed_field_graph_invalid'],
      };
    }
  }

  previewChange(request: PackageChangeRequest): PackageChangePreview {
    const base = this.active;
    if (!base) throw new Error('package_change_no_active_package');
    validatePackageChangeRequest(request, base);
    const requestHash = hashValue(normalizePackageChangeRequest(request));
    const next = applyPackagePatch(base, request.patch);
    const validation = this.preview(next);
    return {
      status: validation.valid ? 'valid' : 'invalid',
      requestHash,
      packageHash: validation.valid ? hashValue(validation.package) : null,
      basePackageKey: packageKey(base),
      package: validation.valid ? validation.package : null,
      validation,
    };
  }

  activateApprovedChange(request: PackageChangeRequest, approval: PackageChangeApprovalReceipt): AppPackage {
    const preview = this.previewChange(request);
    if (preview.status !== 'valid' || !preview.packageHash || !preview.package) {
      const errors = preview.validation.valid ? ['package_change_invalid'] : preview.validation.errors;
      throw new Error(`package_change_invalid:${errors.join('|')}`);
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
    return this.activateInternal(preview.package, {
      requestHash: preview.requestHash,
      packageHash: preview.packageHash,
      approvalHash: hashValue(approval),
      approvedBy: approval.approvedBy.trim(),
    });
  }

  activate(input: unknown): AppPackage {
    const result = this.preview(input);
    if (!result.valid) throw new Error(`package_invalid:${result.errors.join('|')}`);
    return this.activateInternal(result.package, {});
  }

  private activateInternal(pkg: AppPackage, evidence: Pick<PackageRegistryReceipt, 'requestHash' | 'packageHash' | 'approvalHash' | 'approvedBy'>): AppPackage {
    this.previous = this.active;
    this.active = pkg;
    this.packages.set(packageKey(pkg), pkg);
    this.receipts.push(this.receipt('activate', packageKey(pkg), this.previous ? packageKey(this.previous) : null, evidence));
    this.persist();
    return this.active;
  }

  rollback(): AppPackage | null {
    const current = this.active;
    this.active = this.previous;
    this.previous = current;
    this.receipts.push(this.receipt('rollback', this.active ? packageKey(this.active) : null, this.previous ? packageKey(this.previous) : null, {}));
    this.persist();
    return this.active;
  }

  getActive(): AppPackage | null {
    return this.active;
  }

  getReceipts(): readonly PackageRegistryReceipt[] {
    return [...this.receipts];
  }

  private load(path: string): void {
    if (!existsSync(path)) return;
    const parsed = parsePackageRegistryStore(readFileSync(path, 'utf8'));
    this.packages = new Map(Object.entries(parsed.packages));
    this.active = parsed.activeKey ? this.packages.get(parsed.activeKey) ?? null : null;
    this.previous = parsed.previousKey ? this.packages.get(parsed.previousKey) ?? null : null;
    this.receipts = [...parsed.receipts];
  }

  private persist(): void {
    if (!this.path) return;
    const store: PackageRegistryStore = {
      schemaVersion: PACKAGE_REGISTRY_SCHEMA_VERSION,
      activeKey: this.active ? packageKey(this.active) : null,
      previousKey: this.previous ? packageKey(this.previous) : null,
      packages: Object.fromEntries([...this.packages.entries()].sort(([left], [right]) => left.localeCompare(right))),
      receipts: [...this.receipts],
    };
    mkdirSync(dirname(this.path), { recursive: true });
    const tempPath = `${this.path}.tmp-${process.pid}`;
    writeFileSync(tempPath, JSON.stringify(store, null, 2), 'utf8');
    renameSync(tempPath, this.path);
  }

  private receipt(
    action: PackageRegistryReceipt['action'],
    key: string | null,
    previousKey: string | null,
    evidence: Pick<PackageRegistryReceipt, 'requestHash' | 'packageHash' | 'approvalHash' | 'approvedBy'>,
  ): PackageRegistryReceipt {
    return {
      id: `package:${action}:${key ?? 'none'}:${this.receipts.length + 1}`,
      action,
      packageKey: key,
      previousPackageKey: previousKey,
      createdAt: this.now(),
      ...(evidence.requestHash ? { requestHash: evidence.requestHash } : {}),
      ...(evidence.packageHash ? { packageHash: evidence.packageHash } : {}),
      ...(evidence.approvalHash ? { approvalHash: evidence.approvalHash } : {}),
      ...(evidence.approvedBy ? { approvedBy: evidence.approvedBy } : {}),
    };
  }
}

function packageKey(pkg: AppPackage): string {
  return `${pkg.id}@${pkg.version}`;
}

function normalizePackageChangeRequest(request: PackageChangeRequest): PackageChangeRequest {
  return {
    basePackageKey: request.basePackageKey ?? null,
    requestedBy: request.requestedBy?.trim() || 'package-builder',
    patch: request.patch,
  };
}

function validatePackageChangeRequest(request: PackageChangeRequest, active: AppPackage): void {
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

function applyPackagePatch(base: AppPackage, patch: readonly Operation[]): AppPackage {
  const clone = JSON.parse(JSON.stringify(base)) as AppPackage;
  const result = jsonPatch.applyPatch(clone, [...patch], true, false);
  return result.newDocument as AppPackage;
}

function hashValue(value: unknown): string {
  return sha256Canonical(value);
}

function parsePackageRegistryStore(serialized: string): PackageRegistryStore {
  let value: unknown;
  try {
    value = JSON.parse(serialized);
  } catch {
    throw new Error('package_registry_invalid_json');
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('package_registry_invalid');
  const row = packageRegistryStoreSchema.safeParse(value);
  if (!row.success) throw new Error('package_registry_schema_invalid');
  const packages: Record<string, AppPackage> = {};
  for (const [key, pkg] of Object.entries(row.data.packages)) {
    const validation = validateAppPackage(pkg);
    if (!validation.valid) throw new Error(`package_registry_package_invalid:${key}:${validation.errors.join('|')}`);
    if (packageKey(validation.package) !== key) throw new Error(`package_registry_package_key_mismatch:${key}`);
    packages[key] = validation.package;
  }
  const activeKey = row.data.activeKey;
  const previousKey = row.data.previousKey;
  if (activeKey && !packages[activeKey]) throw new Error(`package_registry_active_missing:${activeKey}`);
  if (previousKey && !packages[previousKey]) throw new Error(`package_registry_previous_missing:${previousKey}`);
  return {
    schemaVersion: PACKAGE_REGISTRY_SCHEMA_VERSION,
    activeKey,
    previousKey,
    packages,
    receipts: row.data.receipts,
  };
}
