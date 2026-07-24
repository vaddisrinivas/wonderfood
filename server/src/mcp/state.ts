import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { basename, dirname, join } from 'node:path';
import { getDomainManifest, loadCatalog } from '@/src/domain/catalog';
import { getWorkflowCheckpoint, WorkflowRunCheckpoint } from '../workflows/checkpoint';
import { executeQuery, QueryPredicate, QuerySort } from '../kernel/query';
import { notifyOperationCommit } from '../kernel/operation-observer';

type ActionRisk = 'low' | 'standard' | 'sensitive' | 'irreversible' | 'restricted';

type RecordProvider = 'notion' | 'google_sheets' | 'sqlite' | 'postgres' | 'web' | 'user';

export type RecordSource = {
  provider: RecordProvider;
  external_id: string;
  url: string | null;
  observed_at: string;
  content_hash: string | null;
};

export type CanonicalRelation = {
  name: string;
  target_id: string;
};

export type McpRecord = {
  id: string;
  domain: string;
  collection: string;
  title: string;
  properties: Record<string, unknown>;
  relations: CanonicalRelation[];
  source: RecordSource;
  archived_at: string | null;
  created_at: string;
  updated_at: string;
  /** Monotonic canonical revision. Legacy records may omit it until rewritten. */
  revision?: number;
};

type ActionState = {
  status: 'queued' | 'running' | 'completed' | 'failed' | 'cancelled';
};

export type ActionEvent = {
  schema_version: 'lifeos.action-event.v1';
  id: string;
  actor: string;
  domain: string;
  tool: string;
  risk: ActionRisk;
  status: ActionState['status'];
  record_ids: string[];
  before_json: unknown | null;
  after_json: unknown | null;
  undo_payload_json: unknown | null;
  idempotency_key: string | null;
  created_at: string;
  updated_at: string;
  undo_deadline_at: string | null;
  conversation_id: string | null;
  source_ids: string[];
  command: string;
  /** Causal links shared by action, operation, effect and verification records. */
  operation_id: string;
  cause_id: string;
  expected_revision: number | null;
  verification_json?: unknown | null;
};

type PersistedStore = {
  version: 1;
  updated_at: string;
  records: Record<string, McpRecord>;
  actions: Record<string, ActionEvent>;
};

type PersistOptions = {
  persist?: boolean;
};

export type WorkflowStep = {
  id: string;
  action?: string;
  tool?: string;
  skill?: string;
  input?: Record<string, unknown>;
  input_from?: string[];
  output?: string;
  required?: boolean;
  [key: string]: unknown;
};

export type WorkflowDocument = {
  schema_version: 'lifeos.workflow.v1';
  id: string;
  domain: string;
  label: string;
  trigger?: Record<string, unknown>;
  steps: WorkflowStep[];
  write_policy: string;
  [key: string]: unknown;
};

const MCP_STATE_PATH = process.env.LIFEOS_MCP_STATE_PATH ?? join(process.cwd(), 'server-data', 'mcp-runtime.json');
const ACTION_TTL_MS = 24 * 60 * 60 * 1000;
const WORKFLOW_DIR = join(process.cwd(), 'packages', 'domain-config', 'workflows');

let store: PersistedStore = loadStore();
let workflowCache: WorkflowDocument[] | null = null;

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function ensureDir(path: string) {
  if (!existsSync(dirname(path))) {
    mkdirSync(dirname(path), { recursive: true });
  }
}

function nowIso(): string {
  return new Date().toISOString();
}

function nowDeadlineIso(): string {
  return new Date(Date.now() + ACTION_TTL_MS).toISOString();
}

function isValidStore(value: unknown): value is PersistedStore {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const row = value as Record<string, unknown>;
  return (
    row.version === 1 &&
    typeof row.updated_at === 'string' &&
    isObject(row.records) &&
    isObject(row.actions)
  );
}

function loadStore(): PersistedStore {
  if (!existsSync(MCP_STATE_PATH)) {
    return {
      version: 1,
      updated_at: nowIso(),
      records: {},
      actions: {},
    };
  }

  try {
    const raw = readFileSync(MCP_STATE_PATH, 'utf-8');
    const parsed = JSON.parse(raw) as unknown;
    if (!isValidStore(parsed)) {
      return {
        version: 1,
        updated_at: nowIso(),
        records: {},
        actions: {},
      };
    }
    return {
      version: 1,
      updated_at: String(parsed.updated_at),
      records: parsed.records as Record<string, McpRecord>,
      actions: Object.fromEntries(
        Object.entries(parsed.actions as Record<string, ActionEvent>).map(([id, action]) => [id, {
          ...action,
          operation_id: action.operation_id || `${action.id || id}:operation`,
          cause_id: action.cause_id || action.id || id,
          expected_revision: typeof action.expected_revision === 'number' ? action.expected_revision : null,
          verification_json: action.verification_json ?? null,
        }]),
      ),
    };
  } catch {
    return {
      version: 1,
      updated_at: nowIso(),
      records: {},
      actions: {},
    };
  }
}

function persistStore() {
  ensureDir(MCP_STATE_PATH);
  store.updated_at = nowIso();
  writeFileSync(MCP_STATE_PATH, JSON.stringify(store, null, 2), 'utf-8');
}

function getSupportedProviders(): RecordProvider[] {
  return ['notion', 'google_sheets', 'sqlite', 'postgres', 'web', 'user'];
}

function parseRecordManifest(domain: string) {
  const catalog = loadCatalog();
  const manifest = getDomainManifest(catalog.catalog.domains, domain);
  if (!manifest) {
    throw new Error(`Unknown domain: ${domain}`);
  }
  return manifest;
}

function normalizeRecord(
  record: Omit<McpRecord, 'created_at' | 'updated_at' | 'relations' | 'source' | 'archived_at'> & Partial<McpRecord>,
): McpRecord {
  if (!record.id || typeof record.id !== 'string') {
    throw new Error('record.id is required');
  }
  const id = record.id.trim();
  if (!id) {
    throw new Error('record.id cannot be empty');
  }

  if (!record.domain || typeof record.domain !== 'string') {
    throw new Error('record.domain is required');
  }
  const domain = record.domain.trim();
  if (!domain) {
    throw new Error('record.domain cannot be empty');
  }

  if (!record.collection || typeof record.collection !== 'string') {
    throw new Error('record.collection is required');
  }
  const collection = record.collection.trim();
  if (!collection) {
    throw new Error('record.collection cannot be empty');
  }

  const manifest = parseRecordManifest(domain);
  if (!manifest.collections.includes(collection)) {
    throw new Error(`collection ${collection} not in domain manifest`);
  }

  const parsedRelations = Array.isArray(record.relations)
    ? record.relations
        .filter(
          (relation): relation is CanonicalRelation =>
            Boolean(relation && typeof relation.name === 'string' && relation.name.trim() && typeof relation.target_id === 'string' && relation.target_id.trim()),
        )
        .map((relation) => ({
          name: relation.name.trim(),
          target_id: relation.target_id.trim(),
        }))
    : [];

  const seen = new Set<string>();
  const dedupedRelations = parsedRelations.filter((relation) => {
    const key = `${relation.name}:${relation.target_id}`;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });

  const relationFromManifest = dedupedRelations.filter((relation) => {
    const edge = manifest.relations.find((item) => item.name === relation.name && item.from === collection);
    return !edge || edge.to === '*' || manifest.collections.includes(edge.to);
  });

  const sourceProvider = typeof record.source?.provider === 'string' ? record.source.provider : 'user';
  const provider =
    (getSupportedProviders() as string[]).includes(sourceProvider) ? (sourceProvider as RecordProvider) : 'user';

  const source: RecordSource = record.source
    ? {
        provider,
        external_id:
          typeof record.source.external_id === 'string' && record.source.external_id.trim().length > 0
            ? record.source.external_id
            : `${domain}:${collection}:${id}`,
        url: typeof record.source.url === 'string' ? record.source.url : null,
        observed_at:
          typeof record.source.observed_at === 'string' && record.source.observed_at.trim().length > 0
            ? record.source.observed_at
            : nowIso(),
        content_hash:
          typeof record.source.content_hash === 'string' && record.source.content_hash.length > 0
            ? record.source.content_hash
            : null,
      }
    : {
        provider: 'user',
        external_id: `${domain}:${collection}:${id}`,
        url: null,
        observed_at: nowIso(),
        content_hash: null,
      };

  return {
    id,
    domain,
    collection,
    title: typeof record.title === 'string' && record.title.trim().length > 0 ? record.title.trim() : id,
    properties: typeof record.properties === 'object' && record.properties !== null ? record.properties : {},
    relations: relationFromManifest,
    source,
    archived_at: typeof record.archived_at === 'string' && record.archived_at.trim().length > 0 ? record.archived_at : null,
    created_at: record.created_at ?? nowIso(),
    updated_at: record.updated_at ?? nowIso(),
    revision: typeof record.revision === 'number' && Number.isInteger(record.revision) && record.revision >= 0 ? record.revision : 1,
  };
}

function upsertRecord(record: McpRecord, options: PersistOptions = {}) {
  const next = normalizeRecord(record);
  const exists = store.records[next.id];

  if (exists) {
    const createdAt = exists.created_at;
    store.records[next.id] = {
      ...next,
      id: next.id,
      title: next.title || exists.title,
      created_at: createdAt,
      updated_at: nowIso(),
      revision: (exists.revision ?? 0) + 1,
      archived_at: next.archived_at ?? exists.archived_at,
    };
  } else {
    store.records[next.id] = {
      ...next,
      id: next.id,
      created_at: nowIso(),
      updated_at: nowIso(),
      revision: typeof next.revision === 'number' && next.revision > 0 ? next.revision : 1,
    };
  }

  if (options.persist !== false) {
    persistStore();
  }
  return { ...store.records[next.id] };
}

export function deleteRecord(id: string, options: PersistOptions = {}) {
  if (!store.records[id]) {
    return false;
  }
  delete store.records[id];
  if (options.persist !== false) {
    persistStore();
  }
  return true;
}

export function restoreRecord(record: McpRecord) {
  const normalized = normalizeRecord({
    ...record,
    created_at: record.created_at,
    updated_at: record.updated_at,
  });
  const existing = store.records[normalized.id];
  store.records[normalized.id] = {
    ...normalized,
    id: normalized.id,
    title: normalized.title || (existing ? existing.title : normalized.id),
    created_at: record.created_at,
    updated_at: record.updated_at,
  };
  persistStore();
  return { ...store.records[normalized.id] };
}

function isWorkflowFileName(value: string) {
  return value.endsWith('.v1.json');
}

function workflowPathFromId(id: string) {
  return join(WORKFLOW_DIR, `${id.replace(/_/g, '-')}.v1.json`);
}

function parseWorkflowDocument(raw: unknown, fallbackDomain: string): WorkflowDocument | null {
  if (!isObject(raw)) {
    return null;
  }

  const candidate = raw as {
    schema_version?: string;
    id?: unknown;
    domain?: unknown;
    label?: unknown;
    trigger?: unknown;
    steps?: unknown;
    write_policy?: unknown;
  };

  if (candidate.schema_version !== 'lifeos.workflow.v1') {
    return null;
  }
  if (typeof candidate.id !== 'string' || candidate.id.trim().length === 0) {
    return null;
  }
  if (!Array.isArray(candidate.steps)) {
    return null;
  }
  if (typeof candidate.label !== 'string' || candidate.label.trim().length === 0) {
    return null;
  }
  if (typeof candidate.write_policy !== 'string' || candidate.write_policy.trim().length === 0) {
    return null;
  }

  const steps = candidate.steps
    .map((step) => {
      if (!isObject(step) || typeof step.id !== 'string' || step.id.trim().length === 0) {
        return null;
      }
      const parsed: WorkflowStep = {
        id: step.id.trim(),
      };
      if (typeof (step as { tool?: unknown }).tool === 'string') {
        parsed.tool = String((step as { tool: string }).tool);
      }
      if (typeof (step as { action?: unknown }).action === 'string') {
        parsed.action = String((step as { action: string }).action);
      }
      if (typeof (step as { skill?: unknown }).skill === 'string') {
        parsed.skill = String((step as { skill: string }).skill);
      }
      if (typeof (step as { input?: unknown }).input === 'object' && (step as { input: unknown }).input !== null) {
        parsed.input = (step as { input: Record<string, unknown> }).input;
      }
      if (Array.isArray((step as { input_from?: unknown }).input_from)) {
        parsed.input_from = (step as { input_from: unknown[] }).input_from
          .filter((id): id is string => typeof id === 'string' && id.trim().length > 0)
          .map((id) => id.trim());
      }
      if (typeof (step as { output?: unknown }).output === 'string') {
        parsed.output = String((step as { output: string }).output);
      }
      if (typeof (step as { required?: unknown }).required === 'boolean') {
        parsed.required = (step as { required: boolean }).required;
      }
      return parsed;
    })
    .filter((step): step is WorkflowStep => step !== null);

  if (steps.length === 0) {
    return null;
  }

  return {
    schema_version: 'lifeos.workflow.v1',
    id: candidate.id.trim(),
    domain:
      typeof candidate.domain === 'string' && candidate.domain.trim().length > 0 ? candidate.domain.trim() : fallbackDomain,
    label: candidate.label.trim(),
    ...(isObject(candidate.trigger) ? { trigger: { ...candidate.trigger } } : {}),
    steps,
    write_policy: candidate.write_policy.trim(),
  };
}

function loadCatalogWorkflows(): WorkflowDocument[] {
  if (workflowCache) {
    return [...workflowCache];
  }

  const catalog = loadCatalog();
  const workflowById = new Map<string, string>();

  for (const entry of catalog.catalog.domains) {
    const manifest = getDomainManifest(catalog.catalog.domains, entry.id);
    if (!manifest) {
      continue;
    }
    for (const workflowId of manifest.workflows) {
      if (typeof workflowId === 'string' && workflowId.trim().length > 0) {
        workflowById.set(workflowId.trim(), entry.id);
      }
    }
  }

  const entries = existsSync(WORKFLOW_DIR) ? readdirSync(WORKFLOW_DIR, { withFileTypes: true }) : [];
  const loaded: WorkflowDocument[] = [];
  const seen = new Set<string>();

  for (const entry of entries) {
    if (!entry.isFile()) {
      continue;
    }
    if (!isWorkflowFileName(entry.name)) {
      continue;
    }
    const workflowId = basename(entry.name, '.v1.json').replace(/-/g, '_');
    if (!workflowById.has(workflowId) || seen.has(workflowId)) {
      continue;
    }

    const filePath = join(WORKFLOW_DIR, entry.name);
    try {
      const raw = readFileSync(filePath, 'utf-8');
      const parsed = parseWorkflowDocument(JSON.parse(raw), workflowById.get(workflowId) ?? 'food');
      if (!parsed) {
        continue;
      }
      loaded.push(parsed);
      seen.add(workflowId);
    } catch {
      continue;
    }
  }

  workflowCache = loaded;
  return [...loaded];
}

function cloneActionEvent(action: ActionEvent): ActionEvent {
  return {
    ...action,
    operation_id: action.operation_id || `${action.id}:operation`,
    cause_id: action.cause_id || action.id,
    expected_revision: typeof action.expected_revision === 'number' ? action.expected_revision : null,
    verification_json: action.verification_json ?? null,
    record_ids: [...action.record_ids],
    source_ids: [...action.source_ids],
  };
}

export function listRecords(input: {
  domain?: string;
  collection?: string;
  includeArchived?: boolean;
  query?: string;
  limit?: number;
  offset?: number;
  where?: QueryPredicate;
  orderBy?: QuerySort[];
}) {
  const items = Object.values(store.records)
    .filter((record) => {
      if (input.domain && record.domain !== input.domain) {
        return false;
      }
      if (!input.includeArchived && record.archived_at) {
        return false;
      }
      if (input.collection && record.collection !== input.collection) {
        return false;
      }
      return true;
    })
    .filter((record) => {
      const query = (input.query ?? '').trim().toLowerCase();
      if (!query) {
        return true;
      }
      const payload = JSON.stringify({
        id: record.id,
        title: record.title,
        properties: record.properties,
      }).toLowerCase();
      return payload.includes(query);
    })
    .sort((a, b) => b.updated_at.localeCompare(a.updated_at));

  const result = executeQuery(items as unknown as Record<string, unknown>[], {
    from: 'records',
    where: input.where,
    orderBy: input.orderBy,
    limit: input.limit,
    offset: input.offset,
    getField: (row, field) => {
      if (field.startsWith('properties.')) return row.properties && typeof row.properties === 'object'
        ? (row.properties as Record<string, unknown>)[field.slice('properties.'.length)]
        : undefined;
      return row[field];
    },
  });
  return result.rows as unknown as McpRecord[];
}

export function findRecord(id: string) {
  const record = store.records[id];
  return record ? { ...record } : null;
}

export type ProviderCanonicalRecordInput = {
  provider: Extract<RecordProvider, 'notion' | 'google_sheets'>;
  id: string;
  domain: string;
  collection: string;
  title: string;
  properties: Record<string, unknown>;
  relations?: CanonicalRelation[];
  archived?: boolean;
  externalId?: string | null;
  url?: string | null;
  observedAt?: string | null;
  contentHash?: string | null;
};

export type ProviderCanonicalApplyResult = {
  applied: boolean;
  record: McpRecord | null;
  reason?: string;
};

/** Apply a provider pull only after the provider adapter has passed its authority checks. */
export function upsertProviderCanonicalRecord(input: ProviderCanonicalRecordInput): ProviderCanonicalApplyResult {
  const authority = process.env.LIFEOS_AUTHORITY_PROVIDER?.trim() || 'notion';
  if (authority !== input.provider) {
    return {
      applied: false,
      record: null,
      reason: `${input.provider} is a projection; configured authority is ${authority}.`,
    };
  }

  const now = nowIso();
  const existing = store.records[input.id];
  const record = upsertRecord({
    id: input.id,
    domain: input.domain,
    collection: input.collection,
    title: input.title,
    properties: input.properties,
    relations: input.relations ?? [],
    source: {
      provider: input.provider,
      external_id: input.externalId?.trim() || input.id,
      url: input.url ?? null,
      observed_at: input.observedAt?.trim() || now,
      content_hash: input.contentHash ?? null,
    },
    archived_at: input.archived ? now : null,
    created_at: existing?.created_at ?? now,
    updated_at: now,
  });
  return { applied: true, record };
}

export function createRecord(input: Omit<McpRecord, 'created_at' | 'updated_at'> & { now?: string }, options: PersistOptions = {}) {
  const now = input.now ?? nowIso();
  const base = normalizeRecord(input as Omit<McpRecord, 'created_at' | 'updated_at'> & Partial<McpRecord>);
  const next = {
    ...base,
    created_at: now,
    updated_at: now,
  };
  return upsertRecord(next, options);
}

export function updateRecord(id: string, patch: Partial<McpRecord>, options: PersistOptions = {}) {
  const existing = store.records[id];
  if (!existing) {
    return null;
  }

  const merged: McpRecord = {
    ...existing,
    ...patch,
    id: existing.id,
    domain: existing.domain,
    updated_at: nowIso(),
  };
  const before = { ...existing };
  const record = upsertRecord(merged, options);
  return {
    before,
    after: record,
  };
}

export function archiveRecord(id: string, options: PersistOptions = {}) {
  const existing = store.records[id];
  if (!existing) {
    return null;
  }

  if (existing.archived_at) {
    return {
      before: { ...existing },
      after: { ...existing },
    };
  }

  const before = { ...existing };
  const archivedAt = nowIso();
  store.records[id] = {
    ...existing,
    archived_at: archivedAt,
    updated_at: archivedAt,
    revision: (existing.revision ?? 0) + 1,
  };
  if (options.persist !== false) {
    persistStore();
  }

  return {
    before,
    after: { ...store.records[id] },
  };
}

export type ActionWriteResult = {
  action: ActionEvent;
  record?: McpRecord;
  replayed: boolean;
};

function resolveIdempotencyKey(input?: string) {
  return typeof input === 'string' ? input.trim() : '';
}

export function createRecordWithAction(input: {
  actionId: string;
  actor: string;
  domain: string;
  tool: string;
  risk: ActionRisk;
  command: string;
  record: Omit<McpRecord, 'created_at' | 'updated_at'>;
  idempotencyKey?: string;
  sourceIds?: string[];
  conversationId?: string | null;
  before?: unknown;
  undoPayload?: unknown;
  operationId?: string;
  causeId?: string;
  expectedRevision?: number;
}): ActionWriteResult {
  const idempotencyKey = resolveIdempotencyKey(input.idempotencyKey);
  const existing = idempotencyKey ? findActionByIdempotencyKey(idempotencyKey) : null;
  if (existing && existing.status === 'completed') {
    const replayedRecordId = existing.record_ids[0];
    return {
      action: existing,
      record: replayedRecordId ? findRecord(replayedRecordId) ?? undefined : undefined,
      replayed: true,
    };
  }

  const effectiveIdempotencyKey = existing && existing.status !== 'completed' && existing.id !== input.actionId ? undefined : idempotencyKey;
  const record = createRecord({
    ...input.record,
  }, { persist: false });
  const actionSeed = createActionEvent(
    {
      id: input.actionId,
      actor: input.actor,
      domain: input.domain,
      tool: input.tool,
      risk: input.risk,
      recordIds: [record.id],
      idempotencyKey: effectiveIdempotencyKey,
      command: input.command,
      before: input.before,
      after: record,
      undoPayload: input.undoPayload ?? { operation: 'delete_record', record_id: record.id, record },
      sourceIds: input.sourceIds,
      conversationId: input.conversationId ?? null,
      operationId: input.operationId,
      causeId: input.causeId,
      expectedRevision: input.expectedRevision,
    },
    { persist: false },
  );
  const action = markActionCompleted(actionSeed.id, actionSeed.command, { record }, { persist: false }) ?? actionSeed;
  persistStore();
  notifyOperationCommit({
    actionId: action.id,
    operationId: action.operation_id,
    causeId: action.cause_id,
    domain: action.domain,
    recordId: record.id,
    before: input.before ?? null,
    after: record,
  });
  return {
    action,
    record,
    replayed: false,
  };
}

export function updateRecordWithAction(input: {
  actionId: string;
  actor: string;
  domain: string;
  tool: string;
  risk: ActionRisk;
  command: string;
  id: string;
  patch: Partial<McpRecord>;
  idempotencyKey?: string;
  sourceIds?: string[];
  conversationId?: string | null;
  source?: McpRecord['source'];
  undoPayload?: {
    operation: string;
    before?: unknown;
    record_id?: string;
  };
  expectedRevision?: number;
  operationId?: string;
  causeId?: string;
}): ActionWriteResult {
  const idempotencyKey = resolveIdempotencyKey(input.idempotencyKey);
  const existing = idempotencyKey ? findActionByIdempotencyKey(idempotencyKey) : null;
  if (existing && existing.status === 'completed') {
    const replayedRecordId = existing.record_ids[0];
    return {
      action: existing,
      record: replayedRecordId ? findRecord(replayedRecordId) ?? undefined : undefined,
      replayed: true,
    };
  }

  const effectiveIdempotencyKey = existing && existing.status !== 'completed' && existing.id !== input.actionId ? undefined : idempotencyKey;
  const previous = findRecord(input.id);
  if (!previous) {
    return {
      action: markActionFailed(input.actionId, 'record not found', { persist: false }) ??
        createActionEvent(
          {
            id: input.actionId,
            actor: input.actor,
            domain: input.domain,
            tool: input.tool,
            risk: input.risk,
            recordIds: [input.id],
            idempotencyKey: effectiveIdempotencyKey,
            command: input.command,
            before: null,
            after: null,
            undoPayload: null,
            sourceIds: input.sourceIds,
            conversationId: input.conversationId ?? null,
          },
          { persist: false },
        ),
      replayed: false,
    };
  }

  if (input.expectedRevision !== undefined && (previous.revision ?? 0) !== input.expectedRevision) {
    const action = createActionEvent(
      {
        id: input.actionId,
        actor: input.actor,
        domain: input.domain,
        tool: input.tool,
        risk: input.risk,
        recordIds: [input.id],
        idempotencyKey: effectiveIdempotencyKey,
        command: input.command,
        before: previous,
        after: null,
        undoPayload: null,
        sourceIds: input.sourceIds,
        conversationId: input.conversationId ?? null,
        operationId: input.operationId,
        causeId: input.causeId,
        expectedRevision: input.expectedRevision,
        status: 'failed',
      },
      { persist: false },
    );
    const failed = markActionFailed(action.id, 'revision conflict', { persist: false }) ?? action;
    persistStore();
    return { action: failed, record: previous, replayed: false };
  }

  const updated = updateRecord(
    input.id,
    {
      ...input.patch,
      ...(input.source ? { source: input.source } : {}),
    },
    { persist: false },
  );
  if (!updated) {
    const action = createActionEvent(
      {
        id: input.actionId,
        actor: input.actor,
        domain: input.domain,
        tool: input.tool,
        risk: input.risk,
        recordIds: [input.id],
        idempotencyKey: effectiveIdempotencyKey,
        command: input.command,
        before: previous,
        after: null,
        undoPayload: null,
        sourceIds: input.sourceIds,
        conversationId: input.conversationId ?? null,
      },
      { persist: false },
    );
    const failure = markActionFailed(action.id, 'record could not be updated', { persist: false }) ?? action;
    persistStore();
    return { action: failure, replayed: false };
  }

  const undoPayload = input.undoPayload ?? {
    operation: 'restore_after_update',
    before: updated.before,
    record_id: updated.after.id,
  };

  const actionSeed = createActionEvent(
    {
      id: input.actionId,
      actor: input.actor,
      domain: input.domain,
      tool: input.tool,
      risk: input.risk,
      recordIds: [updated.after.id],
      idempotencyKey: effectiveIdempotencyKey,
      command: input.command,
      before: updated.before,
      after: updated.after,
      undoPayload,
      sourceIds: input.sourceIds,
      conversationId: input.conversationId ?? null,
      operationId: input.operationId,
      causeId: input.causeId,
      expectedRevision: input.expectedRevision,
    },
    { persist: false },
  );
  const action = markActionCompleted(actionSeed.id, actionSeed.command, { record: updated.after }, { persist: false }) ?? actionSeed;
  persistStore();
  notifyOperationCommit({
    actionId: action.id,
    operationId: action.operation_id,
    causeId: action.cause_id,
    domain: action.domain,
    recordId: updated.after.id,
    before: updated.before,
    after: updated.after,
  });
  return {
    action,
    record: updated.after,
    replayed: false,
  };
}

export function archiveRecordWithAction(input: {
  actionId: string;
  actor: string;
  domain: string;
  tool: string;
  risk: ActionRisk;
  command: string;
  id: string;
  idempotencyKey?: string;
  sourceIds?: string[];
  conversationId?: string | null;
  source?: McpRecord['source'];
  expectedRevision?: number;
  operationId?: string;
  causeId?: string;
  undoPayload?: {
    operation: string;
    before?: unknown;
    record_id?: string;
  };
}): ActionWriteResult {
  const idempotencyKey = resolveIdempotencyKey(input.idempotencyKey);
  const existing = idempotencyKey ? findActionByIdempotencyKey(idempotencyKey) : null;
  if (existing && existing.status === 'completed') {
    const replayedRecordId = existing.record_ids[0];
    return {
      action: existing,
      record: replayedRecordId ? findRecord(replayedRecordId) ?? undefined : undefined,
      replayed: true,
    };
  }

  const effectiveIdempotencyKey = existing && existing.status !== 'completed' && existing.id !== input.actionId ? undefined : idempotencyKey;
  const previous = findRecord(input.id);
  if (!previous) {
    const action = createActionEvent(
      {
        id: input.actionId,
        actor: input.actor,
        domain: input.domain,
        tool: input.tool,
        risk: input.risk,
        recordIds: [input.id],
        idempotencyKey: effectiveIdempotencyKey,
        command: input.command,
        before: null,
        after: null,
        undoPayload: null,
        sourceIds: input.sourceIds,
        conversationId: input.conversationId ?? null,
        operationId: input.operationId,
        causeId: input.causeId,
        expectedRevision: input.expectedRevision,
        status: 'failed',
      },
      { persist: false },
    );
    const failure = markActionFailed(action.id, 'record not found', { persist: false }) ?? action;
    persistStore();
    return { action: failure, replayed: false };
  }

  if (input.expectedRevision !== undefined && (previous.revision ?? 0) !== input.expectedRevision) {
    const action = createActionEvent(
      {
        id: input.actionId,
        actor: input.actor,
        domain: input.domain,
        tool: input.tool,
        risk: input.risk,
        recordIds: [input.id],
        idempotencyKey: effectiveIdempotencyKey,
        command: input.command,
        before: previous,
        after: null,
        undoPayload: null,
        sourceIds: input.sourceIds,
        conversationId: input.conversationId ?? null,
        operationId: input.operationId,
        causeId: input.causeId,
        expectedRevision: input.expectedRevision,
        status: 'failed',
      },
      { persist: false },
    );
    const failed = markActionFailed(action.id, 'revision conflict', { persist: false }) ?? action;
    persistStore();
    return { action: failed, record: previous, replayed: false };
  }

  const archived = archiveRecord(input.id, { persist: false });
  if (!archived) throw new Error('archive precondition violated');

  const payload = input.undoPayload ?? {
    operation: 'restore_after_archive',
    before: archived.before,
    record_id: archived.after.id,
  };
  const resolvedAfter = input.source
    ? { ...archived.after, source: input.source }
    : archived.after;
  if (resolvedAfter !== archived.after) {
    const restored = restoreRecord(resolvedAfter);
    store.records[resolvedAfter.id] = restored;
  }

  const actionSeed = createActionEvent(
    {
      id: input.actionId,
      actor: input.actor,
      domain: input.domain,
      tool: input.tool,
      risk: input.risk,
      recordIds: [resolvedAfter.id],
      idempotencyKey: effectiveIdempotencyKey,
      command: input.command,
      before: archived.before,
      after: resolvedAfter,
      undoPayload: payload,
      sourceIds: input.sourceIds,
      conversationId: input.conversationId ?? null,
      operationId: input.operationId,
      causeId: input.causeId,
      expectedRevision: input.expectedRevision,
    },
    { persist: false },
  );

  const action = markActionCompleted(actionSeed.id, actionSeed.command, { record: resolvedAfter }, { persist: false }) ?? actionSeed;
  if (resolvedAfter !== archived.after) {
    store.records[resolvedAfter.id] = resolvedAfter;
  }
  persistStore();

  notifyOperationCommit({
    actionId: action.id,
    operationId: action.operation_id,
    causeId: action.cause_id,
    domain: action.domain,
    recordId: resolvedAfter.id,
    before: archived.before,
    after: resolvedAfter,
  });

  return {
    action,
    record: resolvedAfter,
    replayed: false,
  };
}

function readActions(): ActionEvent[] {
  return Object.values(store.actions);
}

export function getActionEvent(id: string) {
  const action = store.actions[id];
  return action ? cloneActionEvent(action) : null;
}

export function findActionByIdempotencyKey(idempotencyKey: string) {
  const events = readActions();
  return events.find((action) => action.idempotency_key && action.idempotency_key === idempotencyKey) || null;
}

export function createActionEvent(input: {
  id: string;
  actor: string;
  domain: string;
  tool: string;
  risk: ActionRisk;
  recordIds: string[];
  idempotencyKey?: string;
  command: string;
  before?: unknown;
  after?: unknown;
  undoPayload?: unknown;
  status?: ActionState['status'];
  sourceIds?: string[];
  conversationId?: string | null;
  operationId?: string;
  causeId?: string;
  expectedRevision?: number;
}, options: PersistOptions = {}): ActionEvent {
  const now = nowIso();
  const idempotencyKey = input.idempotencyKey?.trim() || null;

  if (idempotencyKey) {
    const existing = findActionByIdempotencyKey(idempotencyKey);
    if (existing) {
      if (existing.status !== 'completed' && existing.id === input.id) {
        store.actions[existing.id] = {
          ...store.actions[existing.id],
          actor: input.actor,
          domain: input.domain,
          tool: input.tool,
          risk: input.risk,
          status: input.status ?? existing.status,
          record_ids: input.recordIds,
          before_json: input.before ?? null,
          after_json: input.after ?? null,
          undo_payload_json: input.undoPayload ?? null,
          source_ids: input.sourceIds ?? [],
          conversation_id: input.conversationId ?? null,
          command: input.command,
          operation_id: input.operationId?.trim() || existing.operation_id,
          cause_id: input.causeId?.trim() || existing.cause_id,
          expected_revision: input.expectedRevision ?? null,
          updated_at: now,
        };
        if (options.persist !== false) {
          persistStore();
        }
        return cloneActionEvent(store.actions[existing.id]);
      }
      return existing;
    }
  }

  const event: ActionEvent = {
    schema_version: 'lifeos.action-event.v1',
    id: input.id,
    actor: input.actor,
    domain: input.domain,
    tool: input.tool,
    risk: input.risk,
    status: input.status ?? 'queued',
    record_ids: input.recordIds,
    before_json: input.before ?? null,
    after_json: input.after ?? null,
    undo_payload_json: input.undoPayload ?? null,
    idempotency_key: idempotencyKey,
    created_at: now,
    updated_at: now,
    undo_deadline_at: nowDeadlineIso(),
    conversation_id: input.conversationId ?? null,
    source_ids: input.sourceIds ?? [],
    command: input.command,
    operation_id: input.operationId?.trim() || `${input.id}:operation`,
    cause_id: input.causeId?.trim() || input.id,
    expected_revision: input.expectedRevision ?? null,
    verification_json: null,
  };

  store.actions[event.id] = event;
  if (options.persist !== false) {
    persistStore();
  }
  return cloneActionEvent(event);
}

export function markActionCompleted(id: string, command?: string, after?: unknown, options: PersistOptions = {}) {
  const existing = store.actions[id];
  if (!existing) {
    return null;
  }

  store.actions[id] = {
    ...existing,
    status: 'completed',
    command: command ?? existing.command,
    after_json: after ?? existing.after_json,
    updated_at: nowIso(),
  };
  if (options.persist !== false) {
    persistStore();
  }
  return cloneActionEvent(store.actions[id]);
}

export function attachActionVerification(id: string, verification: unknown, options: PersistOptions = {}) {
  const existing = store.actions[id];
  if (!existing) {
    return null;
  }
  store.actions[id] = {
    ...existing,
    verification_json: verification,
    updated_at: nowIso(),
  };
  if (options.persist !== false) {
    persistStore();
  }
  return cloneActionEvent(store.actions[id]);
}

export function markActionFailed(id: string, reason?: string, options: PersistOptions = {}) {
  const existing = store.actions[id];
  if (!existing) {
    return null;
  }

  store.actions[id] = {
    ...existing,
    status: 'failed',
    updated_at: nowIso(),
  };
  if (options.persist !== false) {
    persistStore();
  }
  return { ...cloneActionEvent(store.actions[id]), reason };
}

function isUndoWindowOpen(deadlineAt: string | null) {
  if (!deadlineAt) {
    return false;
  }
  return Date.parse(deadlineAt) > Date.now();
}

type WorkflowCheckpointUndoResult = {
  applied: number;
  skipped: number;
  errors: string[];
};

function asText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

function toWorkflowUndoRecordId(raw: unknown): string {
  if (!raw || typeof raw !== 'object') {
    return '';
  }
  return asText((raw as { id?: unknown }).id);
}

function applyWorkflowCheckpointUndo(checkpoint: WorkflowRunCheckpoint): WorkflowCheckpointUndoResult {
  const result: WorkflowCheckpointUndoResult = {
    applied: 0,
    skipped: 0,
    errors: [],
  };
  const visited = new Set<string>();

  for (const step of [...checkpoint.steps].reverse()) {
    const stepResult = step.result && typeof step.result === 'object' ? (step.result as Record<string, unknown>) : null;
    const tool = asText(step.tool);
    if (tool === 'create_record') {
      const recordId = asText(stepResult?.id) || asText(stepResult?.after && (stepResult.after as { id?: unknown }).id) || asText(step.changed_records[0]);
      if (!recordId) {
        result.skipped += 1;
        continue;
      }
      if (visited.has(recordId)) {
        result.skipped += 1;
        continue;
      }
      visited.add(recordId);
      const deleted = deleteRecord(recordId);
      if (deleted) {
        result.applied += 1;
      } else {
        result.skipped += 1;
      }
      continue;
    }

    if (tool === 'update_record' || tool === 'archive_record') {
      const before = stepResult?.before;
      if (!before || typeof before !== 'object') {
        result.skipped += 1;
        continue;
      }
      const record = before as McpRecord;
      const recordId = asText(record.id);
      if (!recordId || visited.has(recordId)) {
        result.skipped += 1;
        continue;
      }
      visited.add(recordId);
      try {
        restoreRecord(record);
        result.applied += 1;
      } catch {
        result.errors.push(`failed to restore ${recordId}`);
      }
      continue;
    }

    if (tool === 'run_workflow') {
      // Nested workflows should already have emitted concrete child steps into the same checkpoint stream.
      if (Array.isArray(stepResult?.details)) {
        for (const nestedStep of (stepResult.details as unknown[]).slice().reverse()) {
          if (!nestedStep || typeof nestedStep !== 'object') {
            continue;
          }
          const entry = nestedStep as {
            tool?: unknown;
            result?: unknown;
          };
          const nestedTool = asText(entry.tool);
          const nestedResult = entry.result && typeof entry.result === 'object' ? (entry.result as Record<string, unknown>) : null;
          if (nestedTool === 'create_record') {
            const recordId = asText(nestedResult?.id) || toWorkflowUndoRecordId(nestedResult?.after);
            if (!recordId || visited.has(recordId)) {
              result.skipped += 1;
              continue;
            }
            visited.add(recordId);
            const nestedDeleted = deleteRecord(recordId);
            if (nestedDeleted) {
              result.applied += 1;
            } else {
              result.skipped += 1;
            }
            continue;
          }
          if (nestedTool === 'update_record' || nestedTool === 'archive_record') {
            const nestedBefore = nestedResult?.before;
            if (!nestedBefore || typeof nestedBefore !== 'object') {
              result.skipped += 1;
              continue;
            }
            const nestedRecord = nestedBefore as McpRecord;
            const nestedRecordId = asText(nestedRecord.id);
            if (!nestedRecordId || visited.has(nestedRecordId)) {
              result.skipped += 1;
              continue;
            }
            visited.add(nestedRecordId);
            try {
              restoreRecord(nestedRecord);
              result.applied += 1;
            } catch {
              result.errors.push(`failed to restore nested ${nestedRecordId}`);
            }
            continue;
          }
        }
      }
      continue;
    }

    if (tool !== 'search_records' && tool !== 'read_record' && tool !== '') {
      result.skipped += 1;
    }
  }

  return result;
}

export function listActionIds() {
  return Object.keys(store.actions);
}

export function runUndo(actionId: string): { success: boolean; action?: ActionEvent; message: string } {
  const action = store.actions[actionId];
  if (!action) {
    return {
      success: false,
      message: 'Action not found.',
    };
  }

  if (action.status !== 'completed') {
    return {
      success: false,
      action: cloneActionEvent(action),
      message: `Cannot undo action in status ${action.status}.`,
    };
  }

  if (!isUndoWindowOpen(action.undo_deadline_at)) {
    return { success: false, action: cloneActionEvent(action), message: 'Undo window has expired.' };
  }

  const payload = action.undo_payload_json as {
    operation?: string;
    before?: McpRecord | null;
    after?: McpRecord | null;
    record_id?: string;
    record?: McpRecord | null;
    target_id?: string;
    checkpoint_run_id?: unknown;
  } | null;
  if (!payload || typeof payload !== 'object') {
    return { success: false, action: cloneActionEvent(action), message: 'No reversible payload stored.' };
  }

  const record = payload.record || payload.before || payload.after || null;
  const operation = payload.operation?.trim();

  if (operation === 'delete_record') {
    const recordId = payload.record_id || payload.target_id;
    if (!recordId) {
      return { success: false, action: cloneActionEvent(action), message: 'Undo payload missing created record id.' };
    }
    const deleted = deleteRecord(recordId);
    if (!deleted) {
      return { success: false, action: cloneActionEvent(action), message: 'Created record was already missing.' };
    }
  } else if (operation === 'restore_record' || operation === 'restore_after_update' || operation === 'restore_after_archive') {
    if (!record || typeof record !== 'object') {
      return { success: false, action: cloneActionEvent(action), message: 'Undo payload missing prior record.' };
    }
    store.records[record.id] = record as McpRecord;
    persistStore();
  } else if (operation === 'undo_workflow_checkpoint') {
    const after = action.after_json && typeof action.after_json === 'object' ? (action.after_json as { checkpoint_run_id?: unknown }) : null;
    const checkpointRunId =
      asText(payload.checkpoint_run_id) ||
      asText((action.before_json as { checkpoint_run_id?: unknown })?.checkpoint_run_id) ||
      asText(after?.checkpoint_run_id);
    if (!checkpointRunId) {
      return { success: false, action: cloneActionEvent(action), message: 'Undo workflow payload missing checkpoint id.' };
    }

    const checkpoint = getWorkflowCheckpoint(checkpointRunId);
    if (!checkpoint) {
      return { success: false, action: cloneActionEvent(action), message: `Workflow checkpoint ${checkpointRunId} not found.` };
    }

    const undoResult = applyWorkflowCheckpointUndo(checkpoint);
    if (undoResult.errors.length > 0) {
      return {
        success: false,
        action: cloneActionEvent(action),
        message: `Undo workflow checkpoint failed: ${undoResult.errors.join('; ')}`,
      };
    }

    store.actions[actionId] = {
      ...action,
      status: 'cancelled',
      updated_at: nowIso(),
      after_json: {
        ...(typeof action.after_json === 'object' && action.after_json !== null ? (action.after_json as Record<string, unknown>) : {}),
        workflow_undo: {
          applied: undoResult.applied,
          skipped: undoResult.skipped,
          errors: undoResult.errors,
        },
      },
    };
    persistStore();
    return {
      success: true,
      action: cloneActionEvent(store.actions[actionId]),
      message: `Undo applied.${undoResult.applied > 0 ? ` ${undoResult.applied} step(s) reverted.` : ''}${undoResult.skipped > 0 ? ` ${undoResult.skipped} step(s) skipped.` : ''}`.trim(),
    };
  } else {
    return { success: false, action: cloneActionEvent(action), message: 'Unsupported undo payload.' };
  }

  store.actions[actionId] = {
    ...action,
    status: 'cancelled',
    updated_at: nowIso(),
  };
  persistStore();

  return { success: true, action: cloneActionEvent(store.actions[actionId]), message: 'Undo applied.' };
}

export function listWorkflows(): WorkflowDocument[] {
  return loadCatalogWorkflows();
}

export function findWorkflow(id: string) {
  const found = listWorkflows().find((entry) => entry.id === id);
  return found ? { ...found } : null;
}

export function listRecordUris(): string[] {
  return Object.keys(store.records).sort().map((id) => `wonderfood://record/${encodeURIComponent(id)}`);
}

export function listConversationUris(): string[] {
  return [];
}

export function listActionUris(): string[] {
  return Object.keys(store.actions).sort().map((id) => `wonderfood://action/${encodeURIComponent(id)}`);
}

export function listActionEvents(): ActionEvent[] {
  return Object.values(store.actions)
    .map(cloneActionEvent)
    .sort((a, b) => b.updated_at.localeCompare(a.updated_at));
}

export function touch() {
  persistStore();
}
