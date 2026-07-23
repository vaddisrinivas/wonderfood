import type {
  ConfigConflict,
  ConfigSource,
  ConfigSnapshot,
  ConfigValidationError,
  ControlPlaneState,
} from '@/src/config/types';

export type ConfigDocument = Record<string, unknown>;

export type ConfigSnapshotInput = {
  source: ConfigSource;
  snapshot: ConfigSnapshot;
};

export type ConfigProposal = {
  id: string;
  sources: string[];
  document: ConfigDocument;
  mode: ControlPlaneState['mode'];
  conflicts: ConfigConflict[];
  errors: ConfigValidationError[];
  previous?: ControlPlaneState;
  created_at: string;
};

export type ConfigApplyResult =
  | { ok: true; state: ControlPlaneState; undo: ConfigUndoReceipt }
  | { ok: false; errors: ConfigValidationError[]; conflicts: ConfigConflict[] };

export type ConfigUndoReceipt = {
  proposal_id: string;
  previous?: ControlPlaneState;
  created_at: string;
};

export function validateConfigSnapshot(snapshot: ConfigSnapshot): { ok: true; document: ConfigDocument } | { ok: false; error: ConfigValidationError } {
  const parsed = parseConfigDocument(snapshot.raw);
  if (!parsed.ok) return parsed;
  if (Object.keys(parsed.document).length === 0) {
    return {
      ok: false,
      error: {
        code: 'CONFIG_EMPTY',
        message: 'Config source is empty.',
      },
    };
  }
  return parsed;
}

export function buildConfigProposal(input: {
  snapshots: ConfigSnapshotInput[];
  previous?: ControlPlaneState;
  now?: string;
}): ConfigProposal {
  const createdAt = input.now ?? new Date().toISOString();
  const errors: ConfigValidationError[] = [];
  const conflicts: ConfigConflict[] = [];
  let merged: ConfigDocument = { ...(input.previous?.manifests ?? {}) };
  let mode: ControlPlaneState['mode'] = 'additive';

  const ordered = [...input.snapshots].sort((left, right) => left.source.precedence - right.source.precedence);
  for (const item of ordered) {
    if (!item.source.enabled) continue;
    const validation = validateConfigSnapshot(item.snapshot);
    if (!validation.ok) {
      errors.push({ ...validation.error, path: item.source.id });
      continue;
    }
    if (containsDestructiveIntent(validation.document)) {
      mode = 'migration_required';
      conflicts.push({
        id: conflictId(item.source.id, 'destructive-change'),
        key: 'migration',
        sources: [item.source.id],
        reason: 'Config source requested a rename, delete, or remove action. Additive mode cannot apply it automatically.',
        status: 'needs_review',
        created_at: createdAt,
      });
      continue;
    }
    const result = mergeAdditive(merged, validation.document, item.source.id, createdAt);
    merged = result.document;
    conflicts.push(...result.conflicts);
  }

  if (conflicts.length > 0) mode = 'migration_required';
  return {
    id: `config-proposal-${contentHash(JSON.stringify({ merged, createdAt }))}`,
    sources: ordered.filter((item) => item.source.enabled).map((item) => item.source.id),
    document: merged,
    mode,
    conflicts,
    errors,
    previous: input.previous,
    created_at: createdAt,
  };
}

export function applyConfigProposal(proposal: ConfigProposal): ConfigApplyResult {
  if (proposal.errors.length > 0 || proposal.conflicts.length > 0) {
    return {
      ok: false,
      errors: proposal.errors,
      conflicts: proposal.conflicts,
    };
  }
  const state: ControlPlaneState = {
    sources: proposal.previous?.sources ?? [],
    snapshots: proposal.previous?.snapshots ?? [],
    conflicts: [],
    errors: [],
    manifests: proposal.document,
    applied_at: proposal.created_at,
    last_good_hash: contentHash(JSON.stringify(proposal.document)),
    mode: proposal.mode,
  };
  return {
    ok: true,
    state,
    undo: {
      proposal_id: proposal.id,
      previous: proposal.previous,
      created_at: proposal.created_at,
    },
  };
}

export function undoConfigProposal(receipt: ConfigUndoReceipt): ControlPlaneState {
  return receipt.previous ?? {
    sources: [],
    snapshots: [],
    conflicts: [],
    errors: [],
    manifests: {},
    mode: 'additive',
  };
}

function parseConfigDocument(raw: string): { ok: true; document: ConfigDocument } | { ok: false; error: ConfigValidationError } {
  const trimmed = raw.trim();
  if (!trimmed) {
    return { ok: false, error: { code: 'CONFIG_EMPTY', message: 'Config source is empty.' } };
  }
  try {
    const parsed = JSON.parse(trimmed) as unknown;
    if (isObject(parsed)) return { ok: true, document: parsed };
    return { ok: false, error: { code: 'CONFIG_NOT_OBJECT', message: 'Config root must be an object.' } };
  } catch {
    const yamlLite = parseYamlLite(trimmed);
    if (yamlLite.ok) return yamlLite;
    return {
      ok: false,
      error: {
        code: 'CONFIG_PARSE_FAILED',
        message: 'Config must be JSON or simple YAML-style key/value text.',
      },
    };
  }
}

function parseYamlLite(raw: string): { ok: true; document: ConfigDocument } | { ok: false } {
  const document: ConfigDocument = {};
  let activeListKey: string | null = null;
  for (const line of raw.split('\n')) {
    const clean = line.replace(/\s+#.*$/, '').trimEnd();
    if (!clean.trim()) continue;
    const listItem = clean.match(/^\s*-\s+(.+)$/);
    if (listItem && activeListKey) {
      const list = Array.isArray(document[activeListKey]) ? document[activeListKey] as unknown[] : [];
      list.push(parseScalar(listItem[1]));
      document[activeListKey] = list;
      continue;
    }
    const pair = clean.match(/^([A-Za-z0-9_.-]+):\s*(.*)$/);
    if (!pair) return { ok: false };
    const [, key, value] = pair;
    if (value === '') {
      document[key] = [];
      activeListKey = key;
      continue;
    }
    document[key] = parseScalar(value);
    activeListKey = null;
  }
  return { ok: true, document };
}

function parseScalar(value: string): unknown {
  const trimmed = value.trim();
  if (trimmed === 'true') return true;
  if (trimmed === 'false') return false;
  if (/^-?\d+(\.\d+)?$/.test(trimmed)) return Number(trimmed);
  if (trimmed.startsWith('[') && trimmed.endsWith(']')) {
    return trimmed.slice(1, -1).split(',').map((item) => parseScalar(item.trim())).filter((item) => item !== '');
  }
  return trimmed.replace(/^['"]|['"]$/g, '');
}

function mergeAdditive(base: ConfigDocument, incoming: ConfigDocument, sourceId: string, createdAt: string) {
  const conflicts: ConfigConflict[] = [];
  const document = { ...base };
  for (const [key, value] of Object.entries(incoming)) {
    if (!(key in document)) {
      document[key] = value;
      continue;
    }
    const current = document[key];
    if (isObject(current) && isObject(value)) {
      const result = mergeAdditive(current, value, sourceId, createdAt);
      document[key] = result.document;
      conflicts.push(...result.conflicts.map((conflict) => ({ ...conflict, key: `${key}.${conflict.key}` })));
      continue;
    }
    if (Array.isArray(current) && Array.isArray(value)) {
      document[key] = unionArray(current, value);
      continue;
    }
    if (JSON.stringify(current) === JSON.stringify(value)) continue;
    conflicts.push({
      id: conflictId(sourceId, key),
      key,
      sources: [sourceId],
      reason: `Config key "${key}" already has a different value.`,
      status: 'needs_review',
      created_at: createdAt,
    });
  }
  return { document, conflicts };
}

function unionArray(left: unknown[], right: unknown[]) {
  const seen = new Set(left.map((item) => JSON.stringify(item)));
  const output = [...left];
  for (const item of right) {
    const key = JSON.stringify(item);
    if (seen.has(key)) continue;
    seen.add(key);
    output.push(item);
  }
  return output;
}

function containsDestructiveIntent(document: ConfigDocument): boolean {
  return Object.keys(document).some((key) => ['delete', 'remove', 'rename', 'migrations'].includes(key));
}

function isObject(value: unknown): value is ConfigDocument {
  return typeof value === 'object' && value != null && !Array.isArray(value);
}

function conflictId(sourceId: string, key: string) {
  return `config-conflict-${contentHash(`${sourceId}:${key}`)}`;
}

function contentHash(raw: string): string {
  let hash = 2166136261;
  for (let index = 0; index < raw.length; index += 1) {
    hash ^= raw.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return `fnv1a:${(hash >>> 0).toString(16).padStart(8, '0')}`;
}
