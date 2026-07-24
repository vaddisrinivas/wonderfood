import { validateComputedFieldGraph } from './computed-fields';
import { type AppPackageV2, type PackageValidation, validateAppPackage } from './package';
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { z } from 'zod';

const PACKAGE_REGISTRY_SCHEMA_VERSION = 'wonder.package-registry.v1' as const;

export type PackageRegistryReceipt = Readonly<{
  id: string;
  action: 'activate' | 'rollback';
  packageKey: string | null;
  previousPackageKey: string | null;
  createdAt: string;
}>;

type PackageRegistryStore = Readonly<{
  schemaVersion: typeof PACKAGE_REGISTRY_SCHEMA_VERSION;
  activeKey: string | null;
  previousKey: string | null;
  packages: Readonly<Record<string, AppPackageV2>>;
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
}).strict();

const packageRegistryStoreSchema = z.object({
  schemaVersion: z.literal(PACKAGE_REGISTRY_SCHEMA_VERSION),
  activeKey: z.string().min(1).nullable(),
  previousKey: z.string().min(1).nullable(),
  packages: z.record(z.string(), z.unknown()),
  receipts: z.array(packageRegistryReceiptSchema),
}).strict();

export class PackageRegistry {
  private active: AppPackageV2 | null = null;
  private previous: AppPackageV2 | null = null;
  private packages = new Map<string, AppPackageV2>();
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

  activate(input: unknown): AppPackageV2 {
    const result = this.preview(input);
    if (!result.valid) throw new Error(`package_invalid:${result.errors.join('|')}`);
    this.previous = this.active;
    this.active = result.package;
    this.packages.set(packageKey(result.package), result.package);
    this.receipts.push(this.receipt('activate', packageKey(result.package), this.previous ? packageKey(this.previous) : null));
    this.persist();
    return this.active;
  }

  rollback(): AppPackageV2 | null {
    const current = this.active;
    this.active = this.previous;
    this.previous = current;
    this.receipts.push(this.receipt('rollback', this.active ? packageKey(this.active) : null, this.previous ? packageKey(this.previous) : null));
    this.persist();
    return this.active;
  }

  getActive(): AppPackageV2 | null {
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

  private receipt(action: PackageRegistryReceipt['action'], key: string | null, previousKey: string | null): PackageRegistryReceipt {
    return {
      id: `package:${action}:${key ?? 'none'}:${this.receipts.length + 1}`,
      action,
      packageKey: key,
      previousPackageKey: previousKey,
      createdAt: this.now(),
    };
  }
}

function packageKey(pkg: AppPackageV2): string {
  return `${pkg.id}@${pkg.version}`;
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
  const packages: Record<string, AppPackageV2> = {};
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
