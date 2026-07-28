import type { AppPackage } from './package';

export const PACKAGE_AUTHORING_CHANGE_SCHEMA_VERSION = 'utopia.authoring-change.v1' as const;
export const PACKAGE_AUTHORING_EVALUATION_SCHEMA_VERSION = 'utopia.authoring-evaluation.v1' as const;
export const PACKAGE_AUTHORING_APPROVAL_SCHEMA_VERSION = 'utopia.authoring-approval.v1' as const;

export type PackageAuthoringPatchOp = 'add' | 'replace' | 'remove';

export type PackageAuthoringSourcePatch = Readonly<{
  op: PackageAuthoringPatchOp;
  path: string;
  value?: unknown;
}>;

export type PackageAuthoringChange = Readonly<{
  schemaVersion: typeof PACKAGE_AUTHORING_CHANGE_SCHEMA_VERSION;
  baseSourceRevision: string;
  intent: string;
  proposedBy: string;
  changes: PackageAuthoringSourcePatch[];
}>;

export type PackageAuthoringIssue = Readonly<{
  path: string;
  message: string;
}>;

export type PackageAuthoringEvaluation =
  | Readonly<{
      schemaVersion: typeof PACKAGE_AUTHORING_EVALUATION_SCHEMA_VERSION;
      valid: true;
      changeId: string;
      proposedBy: string;
      intent: string;
      baseSourceRevision: string;
      nextSourceRevision: string;
      packageChecksum: string;
      package: AppPackage;
      preview: unknown;
      diff: unknown[];
      requiresApproval: true;
      risk: string[];
    }>
  | Readonly<{
      schemaVersion: typeof PACKAGE_AUTHORING_EVALUATION_SCHEMA_VERSION;
      valid: false;
      changeId?: string;
      proposedBy?: string;
      intent?: string;
      baseSourceRevision?: string;
      errors: PackageAuthoringIssue[];
    }>;

export type PackageAuthoringApprovalReceipt = Readonly<{
  schemaVersion: typeof PACKAGE_AUTHORING_APPROVAL_SCHEMA_VERSION;
  changeId: string;
  approvedBy: string;
  proposedBy: string;
  approvedAt: string;
  baseSourceRevision: string;
  nextSourceRevision: string;
  packageChecksum: string;
  activationAllowed: true;
  rollbackSourceRevision: string;
}>;

export const PACKAGE_AUTHORING_ALLOWED_ROOTS = [
  'app',
  'collections',
  'queries',
  'screens',
  'rules',
  'workflows',
  'providers',
  'capabilities',
  'theme',
  'fixtures',
  'acceptance',
] as const;

const EXECUTABLE_SOURCE_PATTERN = /\.(?:cjs|cts|js|jsx|mjs|mts|sql|ts|tsx)$/i;

export function collectPackageAuthoringChangeIssues(change: unknown): PackageAuthoringIssue[] {
  const issues: PackageAuthoringIssue[] = [];
  if (!isRecord(change)) return [{ path: '', message: 'authoring change must be an object' }];
  if (change.schemaVersion !== PACKAGE_AUTHORING_CHANGE_SCHEMA_VERSION) {
    issues.push({ path: '/schemaVersion', message: `schemaVersion must be ${PACKAGE_AUTHORING_CHANGE_SCHEMA_VERSION}` });
  }
  if (!isText(change.baseSourceRevision) || !change.baseSourceRevision.startsWith('sha256:')) {
    issues.push({ path: '/baseSourceRevision', message: 'baseSourceRevision must be a sha256 revision' });
  }
  if (!isText(change.intent)) issues.push({ path: '/intent', message: 'intent is required' });
  if (!isText(change.proposedBy)) issues.push({ path: '/proposedBy', message: 'proposedBy is required' });
  if (!Array.isArray(change.changes) || change.changes.length === 0) {
    issues.push({ path: '/changes', message: 'changes must be a non-empty array' });
    return issues;
  }
  for (const [index, patch] of change.changes.entries()) {
    issues.push(...collectPatchIssues(patch, `/changes/${index}`));
  }
  return issues;
}

export function isPackageAuthoringChange(value: unknown): value is PackageAuthoringChange {
  return collectPackageAuthoringChangeIssues(value).length === 0;
}

export function normalizeAuthoringSourcePath(path: string): string[] {
  return path.split('/').filter(Boolean);
}

function collectPatchIssues(patch: unknown, path: string): PackageAuthoringIssue[] {
  const issues: PackageAuthoringIssue[] = [];
  if (!isRecord(patch)) return [{ path, message: 'change patch must be an object' }];
  if (!['add', 'replace', 'remove'].includes(String(patch.op))) {
    issues.push({ path: `${path}/op`, message: 'patch op must be add, replace, or remove' });
  }
  if (!isText(patch.path)) {
    issues.push({ path: `${path}/path`, message: 'patch path is required' });
    return issues;
  }
  const sourcePath = patch.path.trim();
  const parts = normalizeAuthoringSourcePath(sourcePath);
  const root = parts[0];
  if (sourcePath.startsWith('/') || sourcePath.includes('..') || sourcePath.includes('\\')) {
    issues.push({ path: `${path}/path`, message: 'patch path must be a relative package-source JSON path' });
  }
  if (!root || !PACKAGE_AUTHORING_ALLOWED_ROOTS.includes(root as never)) {
    issues.push({ path: `${path}/path`, message: `patch root is not package-source safe: ${root ?? ''}` });
  }
  if (EXECUTABLE_SOURCE_PATTERN.test(sourcePath)) {
    issues.push({ path: `${path}/path`, message: 'patch path cannot target executable code or SQL' });
  }
  if (sourcePath !== 'app.json' && !sourcePath.endsWith('.json')) {
    issues.push({ path: `${path}/path`, message: 'patch path must target a JSON source file' });
  }
  if (patch.op !== 'remove' && !Object.hasOwn(patch, 'value')) {
    issues.push({ path: `${path}/value`, message: 'add and replace patches require value' });
  }
  if (containsExecutablePayload(patch.value)) {
    issues.push({ path: `${path}/value`, message: 'patch value cannot include executable code or SQL markers' });
  }
  return issues;
}

function containsExecutablePayload(value: unknown): boolean {
  if (typeof value === 'string') return /\b(?:function|eval|import|require|select|insert|update|delete|drop|alter)\b/i.test(value);
  if (Array.isArray(value)) return value.some(containsExecutablePayload);
  if (!isRecord(value)) return false;
  return Object.entries(value).some(([key, child]) => EXECUTABLE_SOURCE_PATTERN.test(key) || containsExecutablePayload(child));
}

function isText(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}
