import { basename, dirname, existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { getDomainManifest, loadCatalog } from '@/src/domain/catalog';

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
};

type PersistedStore = {
  version: 1;
  updated_at: string;
  records: Record<string, McpRecord>;
  actions: Record<string, ActionEvent>;
};

export type WorkflowStep = {
  id: string;
  action?: string;
  tool?: string;
  required?: boolean;
  [key: string]: unknown;
};

export type WorkflowDocument = {
  schema_version: 'lifeos.workflow.v1';
  id: string;
  domain: string;
  label: string;
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
      actions: parsed.actions as Record<string, ActionEvent>,
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
  };
}

function upsertRecord(record: McpRecord) {
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
      archived_at: next.archived_at ?? exists.archived_at,
    };
  } else {
    store.records[next.id] = {
      ...next,
      id: next.id,
      created_at: nowIso(),
      updated_at: nowIso(),
    };
  }

  persistStore();
  return { ...store.records[next.id] };
}

function removeRecord(id: string): boolean {
  if (!store.records[id]) {
    return false;
  }
  delete store.records[id];
  persistStore();
  return true;
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

  return typeof input.limit === 'number' && input.limit > 0 ? items.slice(0, input.limit) : items;
}

export function findRecord(id: string) {
  const record = store.records[id];
  return record ? { ...record } : null;
}

export function createRecord(input: Omit<McpRecord, 'created_at' | 'updated_at'> & { now?: string }) {
  const now = input.now ?? nowIso();
  const base = normalizeRecord(input as Omit<McpRecord, 'created_at' | 'updated_at'> & Partial<McpRecord>);
  const next = {
    ...base,
    created_at: now,
    updated_at: now,
  };
  return upsertRecord(next);
}

export function updateRecord(id: string, patch: Partial<McpRecord>) {
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
  const record = upsertRecord(merged);
  return {
    before,
    after: record,
  };
}

export function archiveRecord(id: string) {
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
  };
  persistStore();

  return {
    before,
    after: { ...store.records[id] },
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
}): ActionEvent {
  const now = nowIso();
  const idempotencyKey = input.idempotencyKey?.trim() || null;

  if (idempotencyKey) {
    const existing = findActionByIdempotencyKey(idempotencyKey);
    if (existing) {
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
  };

  store.actions[event.id] = event;
  persistStore();
  return cloneActionEvent(event);
}

export function markActionCompleted(id: string, command?: string, after?: unknown) {
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
  persistStore();
  return cloneActionEvent(store.actions[id]);
}

export function markActionFailed(id: string, reason?: string) {
  const existing = store.actions[id];
  if (!existing) {
    return null;
  }

  store.actions[id] = {
    ...existing,
    status: 'failed',
    updated_at: nowIso(),
  };
  persistStore();
  return { ...cloneActionEvent(store.actions[id]), reason };
}

function isUndoWindowOpen(deadlineAt: string | null) {
  if (!deadlineAt) {
    return false;
  }
  return Date.parse(deadlineAt) > Date.now();
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
    const deleted = removeRecord(recordId);
    if (!deleted) {
      return { success: false, action: cloneActionEvent(action), message: 'Created record was already missing.' };
    }
  } else if (operation === 'restore_record' || operation === 'restore_after_update' || operation === 'restore_after_archive') {
    if (!record || typeof record !== 'object') {
      return { success: false, action: cloneActionEvent(action), message: 'Undo payload missing prior record.' };
    }
    store.records[record.id] = record as McpRecord;
    persistStore();
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

