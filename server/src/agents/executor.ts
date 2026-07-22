import { loadCatalog } from '@/src/domain/catalog';
import { ActionRisk, evaluateCommandPolicy, PolicyDecision } from '@/src/actions/policy';
import {
  ActionEvent,
  archiveRecord,
  createActionEvent,
  createRecord,
  findActionByIdempotencyKey,
  findRecord,
  listRecords,
  markActionCompleted,
  markActionFailed,
  updateRecord,
} from '../mcp/state';

export type AgentStep = {
  id: string;
  action: string;
  required: boolean;
};

export type ActionStatus = 'queued' | 'running' | 'completed' | 'failed' | 'cancelled';

export type ActionReceipt = {
  id: string;
  actor: string;
  domain: string;
  tool: string;
  status: ActionStatus;
  record_ids: string[];
  created_at: string;
  updated_at: string;
  idempotency_key?: string;
  undo_deadline_at?: string;
};

type ParsedIntent = {
  type: 'create' | 'update' | 'archive' | 'noop';
  collection?: string;
  recordId?: string;
  title?: string;
  patch?: Record<string, unknown>;
  needsClarification: boolean;
  reason?: string;
};

const ACTIONS_BY_ALIAS = [
  { key: 'inventory', aliases: ['inventory', 'inventories', 'stock'] },
  { key: 'ingredient', aliases: ['ingredient', 'ingredients'] },
  { key: 'recipe', aliases: ['recipe', 'recipes'] },
  { key: 'meal_plan', aliases: ['meal plan', 'meal_plan', 'mealplan', 'meal'] },
  { key: 'meal_log', aliases: ['meal log', 'meal_log'] },
  { key: 'shopping_item', aliases: ['shopping item', 'shopping items', 'item', 'items'] },
  { key: 'purchase', aliases: ['purchase', 'purchases'] },
  { key: 'purchase_line', aliases: ['purchase line', 'purchase_line'] },
  { key: 'store', aliases: ['store', 'stores', 'market'] },
  { key: 'preference', aliases: ['preference', 'preferences'] },
  { key: 'nutrition_observation', aliases: ['nutrition observation', 'observation', 'nutrition'] },
  { key: 'source_record', aliases: ['source record', 'source_record', 'source'] },
] as const;

const ACTION_TOOL_BY_INTENT: Record<ParsedIntent['type'], string> = {
  create: 'wonderfood.create_record',
  update: 'wonderfood.update_record',
  archive: 'wonderfood.archive_record',
  noop: 'chat_reply',
};

function toReceipt(action: ActionEvent): ActionReceipt {
  return {
    id: action.id,
    actor: action.actor,
    domain: action.domain,
    tool: action.tool,
    status: action.status,
    record_ids: action.record_ids,
    created_at: action.created_at,
    updated_at: action.updated_at,
    idempotency_key: action.idempotency_key ?? undefined,
    undo_deadline_at: action.undo_deadline_at ?? undefined,
  };
}

function buildFailureReceipt(input: {
  actionId: string;
  actor: string;
  domain: string;
  tool: string;
  now: string;
  idempotencyKey?: string;
  command: string;
  reason?: string;
  records?: string[];
}): ActionReceipt {
  const event = createActionEvent({
    id: input.actionId,
    actor: input.actor,
    domain: input.domain,
    tool: input.tool,
    risk: 'restricted',
    recordIds: input.records ?? [],
    idempotencyKey: input.idempotencyKey,
    command: input.command,
    before: null,
    after: null,
    undoPayload: null,
  });
  const failed = markActionFailed(event.id, input.reason);
  return toReceipt(failed ?? event);
}

function normalizeCollectionFromCatalog(input: string) {
  const lower = input.toLowerCase().trim();
  const knownCatalog = loadCatalog().catalog.domains.find((entry) => entry.id === 'food')?.collections ?? [];

  for (const entry of ACTIONS_BY_ALIAS) {
    for (const alias of entry.aliases) {
      if (lower === alias || lower.startsWith(`${alias} `)) {
        return entry.key;
      }
    }
  }

  return knownCatalog.find((collection) => lower === collection || lower.startsWith(`${collection} `));
}

function parseCollectionHint(input: string): { collection?: string; rest: string } {
  const lower = input.toLowerCase();

  for (const entry of ACTIONS_BY_ALIAS) {
    const alias = entry.aliases.find((item) => lower === item || lower.startsWith(`${item} `));
    if (!alias) {
      continue;
    }
    return {
      collection: entry.key,
      rest: input.slice(alias.length).trim(),
    };
  }

  const known = normalizeCollectionFromCatalog(input);
  if (!known) {
    return { rest: input.trim() };
  }

  const escaped = known.replace(/_/g, '\\_');
  return {
    collection: known,
    rest: input.replace(new RegExp(`^${escaped}\\s+`, 'i'), '').trim(),
  };
}

function resolveRecordFromHint(input: string, domain: string, collection?: string): { recordId?: string; needsClarification: boolean; reason?: string } {
  const trimmed = input.trim();
  if (!trimmed) {
    return { needsClarification: true, reason: 'Missing record target.' };
  }

  if (trimmed.startsWith('id:')) {
    const candidate = trimmed.replace(/^id:\s*/, '').trim();
    if (!candidate) {
      return { needsClarification: true, reason: 'Record id was empty.' };
    }
    const record = findRecord(candidate);
    if (!record) {
      return { needsClarification: true, reason: `No record found for id ${candidate}.` };
    }
    if (collection && record.collection !== collection) {
      return { needsClarification: true, reason: `Found ${candidate}, but it is not a ${collection}.` };
    }
    return { recordId: record.id, needsClarification: false };
  }

  const exact = listRecords({
    domain,
    collection,
    includeArchived: false,
    query: trimmed,
    limit: 20,
  }).filter((record) => record.title.toLowerCase() === trimmed.toLowerCase());

  if (exact.length === 1) {
    return { recordId: exact[0].id, needsClarification: false };
  }
  if (exact.length > 1) {
    return { needsClarification: true, reason: 'I found multiple matching records. Please include a full title or id.' };
  }

  const fuzzy = listRecords({
    domain,
    collection,
    includeArchived: false,
    query: trimmed,
    limit: 20,
  });
  if (fuzzy.length === 1) {
    return { recordId: fuzzy[0].id, needsClarification: false };
  }
  if (fuzzy.length > 1) {
    return { needsClarification: true, reason: 'I found multiple matching records. Please include a full title or id.' };
  }

  return { needsClarification: true, reason: 'No matching record found.' };
}

function parseCreateIntent(commandText: string): ParsedIntent {
  const body = commandText.replace(/^(?:add|create|log|track)\s+/i, '').trim();
  if (!body) {
    return { type: 'create', needsClarification: true, reason: 'Missing create target.' };
  }

  const { collection, rest } = parseCollectionHint(body);
  if (!collection) {
    return {
      type: 'create',
      needsClarification: true,
      reason: 'I need one specific target such as "create recipe …" or "create shopping item …".',
    };
  }

  if (!rest) {
    return {
      type: 'create',
      collection,
      needsClarification: true,
      reason: `I can create a ${collection}, but I need a title.`,
    };
  }

  return {
    type: 'create',
    collection,
    title: rest,
    needsClarification: false,
  };
}

function parseUpdateIntent(commandText: string): ParsedIntent {
  const body = commandText.replace(/^(?:update|edit|change|rename)\s+/i, '').trim();
  if (!body) {
    return { type: 'update', needsClarification: true, reason: 'Missing update target.' };
  }

  const { collection, rest } = parseCollectionHint(body);
  const [targetText, ...restParts] = rest.split(/\s+to\s+/i);
  const patchText = (restParts.join(' to ') || '').trim();

  if (!targetText) {
    return { type: 'update', collection, needsClarification: true, reason: 'Missing record to update.' };
  }

  const target = resolveRecordFromHint(targetText, 'food', collection);
  if (target.needsClarification) {
    return {
      type: 'update',
      collection,
      needsClarification: true,
      reason: target.reason,
    };
  }

  const title = patchText || targetText;
  return {
    type: 'update',
    collection,
    recordId: target.recordId,
    title,
    patch: { title },
    needsClarification: false,
  };
}

function parseArchiveIntent(commandText: string): ParsedIntent {
  const body = commandText.replace(/^(?:archive|remove|delete|cancel)\s+/i, '').trim();
  if (!body) {
    return { type: 'archive', needsClarification: true, reason: 'Missing archive target.' };
  }

  const { collection, rest } = parseCollectionHint(body);
  const record = resolveRecordFromHint(rest, 'food', collection);
  if (record.needsClarification) {
    return {
      type: 'archive',
      collection,
      needsClarification: true,
      reason: record.reason,
    };
  }

  return {
    type: 'archive',
    collection,
    recordId: record.recordId,
    needsClarification: false,
  };
}

function parseCommand(commandText: string): ParsedIntent {
  if (/^(?:add|create|log|track)\s+/i.test(commandText)) {
    return parseCreateIntent(commandText);
  }
  if (/^(?:update|edit|change|rename)\s+/i.test(commandText)) {
    return parseUpdateIntent(commandText);
  }
  if (/^(?:archive|remove|delete|cancel)\s+/i.test(commandText)) {
    return parseArchiveIntent(commandText);
  }

  return {
    type: 'noop',
    needsClarification: true,
    reason: 'No supported mutating verb found.',
  };
}

function createBaseAction(input: {
  actionId: string;
  actor: string;
  domain: string;
  tool: string;
  command: string;
  policy: PolicyDecision;
  idempotencyKey?: string;
  recordIds: string[];
  undoPayload?: unknown;
  before?: unknown;
  after?: unknown;
  conversationId?: string | null;
}) {
  return createActionEvent({
    id: input.actionId,
    actor: input.actor,
    domain: input.domain,
    tool: input.tool,
    risk: input.policy.risk as ActionRisk,
    recordIds: input.recordIds,
    idempotencyKey: input.idempotencyKey,
    command: input.command,
    before: input.before ?? null,
    after: input.after ?? null,
    undoPayload: input.undoPayload ?? null,
    conversationId: input.conversationId ?? null,
  });
}

export async function executeCommand(input: {
  actionId: string;
  actor: string;
  domain: string;
  tool: string;
  commandText: string;
  record_ids: string[];
  conversationId?: string | null;
  step?: AgentStep;
  idempotencyKey?: string;
}): Promise<{
  state: ActionStatus;
  receipt: ActionReceipt;
  step: AgentStep | undefined;
}> {
  const command = input.commandText.trim();
  const now = new Date().toISOString();
  const existing = input.idempotencyKey ? findActionByIdempotencyKey(input.idempotencyKey) : null;

  if (existing) {
    return {
      state: existing.status,
      receipt: toReceipt(existing),
      step: input.step,
    };
  }

  const policy = evaluateCommandPolicy({
    domain: input.domain,
    tool: input.tool,
    command,
    actor: input.actor,
  });

  if (!policy.allowed) {
    const receipt = buildFailureReceipt({
      actionId: input.actionId,
      actor: input.actor,
      domain: input.domain,
      tool: input.tool,
      now,
      idempotencyKey: input.idempotencyKey,
      command,
      reason: policy.reason,
      records: input.record_ids,
    });
    return { state: receipt.status, receipt, step: input.step };
  }

  if (policy.requiresClarification) {
    const receipt = buildFailureReceipt({
      actionId: input.actionId,
      actor: input.actor,
      domain: input.domain,
      tool: input.tool,
      now,
      idempotencyKey: input.idempotencyKey,
      command,
      reason: policy.clarifyingQuestion || policy.reason,
      records: input.record_ids,
    });
    return { state: receipt.status, receipt, step: input.step };
  }

  const intent = parseCommand(command);
  if (intent.needsClarification) {
    const receipt = buildFailureReceipt({
      actionId: input.actionId,
      actor: input.actor,
      domain: input.domain,
      tool: input.tool,
      now,
      idempotencyKey: input.idempotencyKey,
      command,
      reason: intent.reason,
      records: input.record_ids,
    });
    return { state: receipt.status, receipt, step: input.step };
  }

  const domain = input.domain || 'food';
  const actionTool = ACTION_TOOL_BY_INTENT[intent.type] ?? input.tool;

  if (intent.type === 'create') {
    const collection = intent.collection ?? 'recipe';
    const title = intent.title?.trim() ?? '';
    if (!title) {
      const receipt = buildFailureReceipt({
        actionId: input.actionId,
        actor: input.actor,
        domain,
        tool: actionTool,
        now,
        idempotencyKey: input.idempotencyKey,
        command,
        reason: `I can create a ${collection}, but I need a title.`,
      });
      return { state: receipt.status, receipt, step: input.step };
    }

    const created = createRecord({
      id: `${domain}-${collection}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
      domain,
      collection,
      title,
      properties: {},
      relations: [],
      source: {
        provider: 'user',
        external_id: input.actionId,
        url: null,
        observed_at: now,
        content_hash: null,
      },
      archived_at: null,
    });

    const action = createBaseAction({
      actionId: input.actionId,
      actor: input.actor,
      domain,
      tool: actionTool,
      policy,
      idempotencyKey: input.idempotencyKey,
      command,
      recordIds: [created.id],
      before: null,
      after: created,
      undoPayload: {
        operation: 'delete_record',
        record_id: created.id,
        record: created,
      },
      conversationId: input.conversationId,
    });
    const completed = markActionCompleted(action.id, action.command, { record: created });
    const receipt = toReceipt(completed ?? action);
    return { state: receipt.status, receipt, step: input.step };
  }

  if (intent.type === 'update' && intent.recordId) {
    const target = findRecord(intent.recordId);
    if (!target) {
      const receipt = buildFailureReceipt({
        actionId: input.actionId,
        actor: input.actor,
        domain,
        tool: actionTool,
        now,
        idempotencyKey: input.idempotencyKey,
        command,
        reason: `Record ${intent.recordId} was not found before apply.`,
      });
      return { state: receipt.status, receipt, step: input.step };
    }

    const updated = updateRecord(intent.recordId, { title: intent.title ?? target.title });
    if (!updated) {
      const receipt = buildFailureReceipt({
        actionId: input.actionId,
        actor: input.actor,
        domain,
        tool: actionTool,
        now,
        idempotencyKey: input.idempotencyKey,
        command,
        reason: `Unable to update ${intent.recordId}.`,
      });
      return { state: receipt.status, receipt, step: input.step };
    }

    const action = createBaseAction({
      actionId: input.actionId,
      actor: input.actor,
      domain,
      tool: actionTool,
      policy,
      idempotencyKey: input.idempotencyKey,
      command,
      recordIds: [updated.after.id],
      before: updated.before,
      after: updated.after,
      undoPayload: {
        operation: 'restore_after_update',
        before: updated.before,
        record_id: updated.after.id,
      },
      conversationId: input.conversationId,
    });
    const completed = markActionCompleted(action.id, action.command, { record: updated.after });
    const receipt = toReceipt(completed ?? action);
    return { state: receipt.status, receipt, step: input.step };
  }

  if (intent.type === 'archive' && intent.recordId) {
    const archived = archiveRecord(intent.recordId);
    if (!archived) {
      const receipt = buildFailureReceipt({
        actionId: input.actionId,
        actor: input.actor,
        domain,
        tool: actionTool,
        now,
        idempotencyKey: input.idempotencyKey,
        command,
        reason: `Could not archive ${intent.recordId}.`,
      });
      return { state: receipt.status, receipt, step: input.step };
    }

    const action = createBaseAction({
      actionId: input.actionId,
      actor: input.actor,
      domain,
      tool: actionTool,
      policy,
      idempotencyKey: input.idempotencyKey,
      command,
      recordIds: [archived.after.id],
      before: archived.before,
      after: archived.after,
      undoPayload: {
        operation: 'restore_after_archive',
        before: archived.before,
        record_id: archived.after.id,
      },
      conversationId: input.conversationId,
    });
    const completed = markActionCompleted(action.id, action.command, { record: archived.after });
    const receipt = toReceipt(completed ?? action);
    return { state: receipt.status, receipt, step: input.step };
  }

  const receipt = buildFailureReceipt({
    actionId: input.actionId,
    actor: input.actor,
    domain,
    tool: actionTool,
    now,
    idempotencyKey: input.idempotencyKey,
    command,
    reason: 'No supported mutating intent resolved.',
    records: input.record_ids,
  });
  return { state: receipt.status, receipt, step: input.step };
}
