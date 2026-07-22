import { loadCatalog } from '@/src/domain/catalog';
import { ActionRisk, PolicyDecision, evaluateCommandPolicy } from '@/src/actions/policy';
import {
  ActionEvent,
  ActionWriteResult,
  archiveRecordWithAction,
  createActionEvent,
  createRecordWithAction,
  findActionByIdempotencyKey,
  findRecord,
  getActionEvent,
  markActionCompleted,
  markActionFailed,
  updateRecordWithAction,
} from '../mcp/state';
import {
  ActionReceipt,
  ActionStatus,
  CommandPolicyDecision,
  CommandReceipt,
  InversePlanStep,
  ParsedCommandIntent,
} from '../types/command';
import { createHash } from 'node:crypto';

export type { ActionReceipt, ActionStatus, CommandReceipt, CommandPolicyDecision, InversePlanStep, ParsedCommandIntent };

export type AgentStep = {
  id: string;
  action: string;
  required: boolean;
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

const ACTION_TOOL_BY_INTENT: Record<ParsedCommandIntent['type'], string> = {
  create: 'wonderfood.create_record',
  update: 'wonderfood.update_record',
  archive: 'wonderfood.archive_record',
  noop: 'chat_reply',
};

function normalizeId(value: unknown) {
  return typeof value === 'string' ? value.trim() : '';
}

function makeActionId(prefix: string, seed: unknown) {
  return `${prefix}:${hashSeed(seed).slice(0, 16)}`;
}

function stringifyForHash(value: unknown): string {
  if (value === null || value === undefined) {
    return String(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map((entry) => stringifyForHash(entry)).join(',')}]`;
  }
  if (typeof value === 'object') {
    return `{${Object.keys(value)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stringifyForHash((value as Record<string, unknown>)[key])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value);
}

function hashSeed(value: unknown): string {
  return createHash('sha256').update(stringifyForHash(value)).digest('hex');
}

function deterministicRecordId(input: {
  operation: string;
  domain: string;
  collection: string;
  title: string;
  sourceHome: string;
  payload?: unknown;
}) {
  return `lifeos-${input.operation}-${hashSeed({
    operation: input.operation,
    domain: input.domain,
    collection: input.collection,
    title: input.title,
    sourceHome: input.sourceHome,
    payload: input.payload,
  }).slice(0, 18)}`;
}

function normalizeCollectionFromCatalog(input: string) {
  const lower = input.toLowerCase().trim();
  const { activeManifest } = loadCatalog();
  const knownCatalog = activeManifest.collections;

  for (const entry of ACTIONS_BY_ALIAS) {
    for (const alias of entry.aliases) {
      if (lower === alias || lower.startsWith(`${alias} `)) {
        return entry.key;
      }
    }
  }

  return knownCatalog.find((collection: string) => lower === collection || lower.startsWith(`${collection} `));
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
}): CommandReceipt {
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
  const failed = markActionFailed(event.id, input.reason, { persist: false }) ?? event;
  return toReceipt(failed);
}

function buildInversePlan(input: {
  operation?: string;
  record_id?: string;
  before?: unknown;
  record?: unknown;
  checkpoint_run_id?: string;
  changed_records?: unknown;
}): InversePlanStep[] {
  const operation = normalizeId(input.operation);
  if (operation === 'delete_record') {
    const target = normalizeId(input.record_id);
    return target ? [{ operation: 'delete_record', target_type: 'record', target_id: target }] : [];
  }
  if (operation === 'restore_after_update' || operation === 'restore_after_archive' || operation === 'restore_record') {
    const recordCandidate = input.before ?? input.record;
    const record =
      recordCandidate && typeof recordCandidate === 'object' && 'id' in recordCandidate
        ? (recordCandidate as { id?: unknown })
        : null;
    const target = normalizeId(input.record_id) || normalizeId(record?.id);
    if (!target) {
      return [];
    }
    return [
      {
        operation,
        target_type: 'record',
        target_id: target,
        metadata: {
          record_restore: Boolean(recordCandidate),
        },
      },
    ];
  }
  if (operation === 'undo_workflow_checkpoint') {
    const checkpointRunId = normalizeId(input.checkpoint_run_id);
    if (!checkpointRunId) {
      return [];
    }
    const changedRecords =
      Array.isArray(input.changed_records)
        ? input.changed_records.filter((entry) => typeof entry === 'string' && entry.trim().length > 0)
        : [];
    return [
      {
        operation,
        target_type: 'workflow',
        target_id: checkpointRunId,
        metadata: {
          changed_records: changedRecords,
        },
      },
    ];
  }

  return [];
}

function toReceipt(action: ActionEvent): CommandReceipt {
  const payload = action.undo_payload_json as {
    operation?: string;
    record_id?: string;
    before?: unknown;
    record?: unknown;
    checkpoint_run_id?: string;
    changed_records?: string[];
  } | null;

  const inversePlan =
    payload && typeof payload === 'object' ? buildInversePlan(payload) : [];

  return {
    id: action.id,
    actor: action.actor,
    domain: action.domain,
    tool: action.tool,
    status: action.status,
    record_ids: [...action.record_ids],
    created_at: action.created_at,
    updated_at: action.updated_at,
    idempotency_key: action.idempotency_key ?? undefined,
    undo_deadline_at: action.undo_deadline_at ?? undefined,
    undo_token: action.id,
    inverse_plan: inversePlan,
  };
}

function toPolicyReason(input: PolicyDecision): string {
  if (input.requiresClarification) {
    return input.clarifyingQuestion ?? input.reason;
  }
  if (!input.allowed) {
    return input.reason;
  }
  return 'ok';
}

export function parseCommandIntent(commandText: string): ParsedCommandIntent {
  const command = normalizeId(commandText);
  if (!command) {
    return { type: 'noop', needsClarification: true, reason: 'No command provided.' };
  }

  if (/^(?:add|create|log|track)\s+/i.test(command)) {
    const body = command.replace(/^(?:add|create|log|track)\s+/i, '').trim();
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

  if (/^(?:update|edit|change|rename)\s+/i.test(command)) {
    const body = command.replace(/^(?:update|edit|change|rename)\s+/i, '').trim();
    if (!body) {
      return { type: 'update', needsClarification: true, reason: 'Missing update target.' };
    }
    const { collection, rest } = parseCollectionHint(body);
    const [targetText, ...restParts] = rest.split(/\s+to\s+/i);
    const patchText = (restParts.join(' to ') || '').trim();
    if (!targetText) {
      return {
        type: 'update',
        collection,
        needsClarification: true,
        reason: 'Missing record to update.',
      };
    }

    const candidate = resolveRecordByHint(targetText, 'food', collection);
    if (candidate.needsClarification) {
      return {
        type: 'update',
        collection,
        needsClarification: true,
        reason: candidate.reason,
      };
    }

    const title = patchText || targetText;
    return {
      type: 'update',
      collection,
      recordId: candidate.recordId,
      title,
      patch: { title },
      needsClarification: false,
    };
  }

  if (/^(?:archive|remove|delete|cancel)\s+/i.test(command)) {
    const body = command.replace(/^(?:archive|remove|delete|cancel)\s+/i, '').trim();
    if (!body) {
      return { type: 'archive', needsClarification: true, reason: 'Missing archive target.' };
    }
    const { collection, rest } = parseCollectionHint(body);
    const candidate = resolveRecordByHint(rest, 'food', collection);
    if (candidate.needsClarification) {
      return {
        type: 'archive',
        collection,
        needsClarification: true,
        reason: candidate.reason,
      };
    }
    return {
      type: 'archive',
      collection,
      recordId: candidate.recordId,
      needsClarification: false,
    };
  }

  return {
    type: 'noop',
    needsClarification: true,
    reason: 'No supported mutating verb found.',
  };
}

function resolveRecordByHint(
  input: string,
  domain: string,
  collection?: string,
): { recordId?: string; needsClarification: boolean; reason?: string } {
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

  const exact = findMatchingRecords({ domain, collection, query: trimmed, exact: true });
  if (exact.length === 1) {
    return { recordId: exact[0], needsClarification: false };
  }
  if (exact.length > 1) {
    return { needsClarification: true, reason: 'I found multiple matching records. Please include a full title or id.' };
  }

  const fuzzy = findMatchingRecords({ domain, collection, query: trimmed, exact: false });
  if (fuzzy.length === 1) {
    return { recordId: fuzzy[0], needsClarification: false };
  }
  if (fuzzy.length > 1) {
    return { needsClarification: true, reason: 'I found multiple matching records. Please include a full title or id.' };
  }

  return { needsClarification: true, reason: 'No matching record found.' };
}

function findMatchingRecords(input: { domain: string; collection?: string; query: string; exact: boolean }) {
  if (input.exact) {
    const records = findAllRecords({
      domain: input.domain,
      collection: input.collection,
      includeArchived: false,
      query: input.query,
      limit: 20,
    }).filter((record) => record.title.toLowerCase() === input.query.toLowerCase());
    return records.map((record) => record.id);
  }

  const records = findAllRecords({
    domain: input.domain,
    collection: input.collection,
    includeArchived: false,
    query: input.query,
    limit: 20,
  });
  return records.map((record) => record.id);
}

import { McpRecord, listRecords } from '../mcp/state';

function findAllRecords(input: {
  domain: string;
  collection?: string;
  includeArchived?: boolean;
  query: string;
  limit?: number;
}) {
  return listRecords(input);
}

export type ParsedCommandExecutionInput = {
  actionId: string;
  actor: string;
  domain: string;
  tool: string;
  command: string;
  idempotencyKey?: string;
  conversationId?: string | null;
  step?: AgentStep;
  recordId?: string;
  source?: McpRecord['source'];
  intent?: ParsedCommandIntent;
  patch?: Record<string, unknown>;
};

export type ParsedCommandExecutionResult = {
  state: ActionStatus;
  receipt: CommandReceipt;
  action: ActionEvent;
  step?: AgentStep;
  replayed: boolean;
};

export function executeCommandFromIntent(input: ParsedCommandExecutionInput): ParsedCommandExecutionResult {
  const now = new Date().toISOString();
  const actionId = input.actionId || makeActionId('command', `${input.actor}:${input.domain}:${input.tool}:${hashSeed(input.command)}:${input.conversationId || ''}`);
  const actionTool = input.tool || 'chat_execute_command';
  const commandText = normalizeId(input.command);
  const domain = input.domain || 'food';
  const idempotencyKey = normalizeId(input.idempotencyKey) || actionId;

  const replayedAction = getActionEvent(input.actionId) ?? findActionByIdempotencyKey(idempotencyKey);
  if (replayedAction) {
    return {
      state: replayedAction.status,
      receipt: toReceipt(replayedAction),
      action: replayedAction,
      step: input.step,
      replayed: true,
    };
  }

  const policy: CommandPolicyDecision = evaluateCommandPolicy({
    domain,
    tool: actionTool,
    command: commandText,
    actor: input.actor,
  }) as CommandPolicyDecision;

  if (!policy.allowed || policy.requiresClarification) {
    const reason = toPolicyReason(policy);
    const failed = buildFailureReceipt({
      actionId,
      actor: input.actor,
      domain,
      tool: actionTool,
      now,
      idempotencyKey,
      command: commandText,
      reason,
    });
    return {
      state: failed.status,
      receipt: failed,
      action: createActionEvent({
        id: actionId,
        actor: input.actor,
        domain,
        tool: actionTool,
        risk: policy.risk as ActionRisk,
        recordIds: [],
        idempotencyKey,
        command: commandText,
        before: null,
        after: null,
        undoPayload: null,
      }),
      step: input.step,
      replayed: false,
    };
  }

  const intent = input.intent ?? parseCommandIntent(commandText);
  if (intent.needsClarification) {
    const failed = buildFailureReceipt({
      actionId,
      actor: input.actor,
      domain,
      tool: actionTool,
      now,
      idempotencyKey,
      command: commandText,
      reason: intent.reason,
    });
    return {
      state: failed.status,
      receipt: failed,
      action: createActionEvent({
        id: actionId,
        actor: input.actor,
        domain,
        tool: actionTool,
        risk: policy.risk as ActionRisk,
        recordIds: [],
        idempotencyKey,
        command: commandText,
        before: null,
        after: null,
        undoPayload: null,
      }),
      step: input.step,
      replayed: false,
    };
  }

  const actionToolByIntent = ACTION_TOOL_BY_INTENT[intent.type] ?? actionTool;
  const inverseSeed =
    intent.type === 'create'
      ? {
          actionId,
          actor: input.actor,
          domain,
          tool: actionToolByIntent,
          risk: policy.risk as ActionRisk,
          command: commandText,
          idempotencyKey,
          collection: intent.collection ?? 'recipe',
          title: intent.title ?? '',
          source: input.source,
          recordId: input.recordId,
          conversationId: input.conversationId,
        }
      : intent.type === 'update'
        ? {
            actionId,
            actor: input.actor,
            domain,
            tool: actionToolByIntent,
            risk: policy.risk as ActionRisk,
            command: commandText,
            idempotencyKey,
            id: intent.recordId,
            patch: input.patch ?? intent.patch ?? {},
            source: input.source,
            conversationId: input.conversationId,
          }
        : {
            actionId,
            actor: input.actor,
            domain,
            tool: actionToolByIntent,
            risk: policy.risk as ActionRisk,
            command: commandText,
            idempotencyKey,
            id: intent.recordId,
            source: input.source,
            conversationId: input.conversationId,
          };

  let writeResult: ActionWriteResult | null = null;

  if (intent.type === 'create') {
    if (!inverseSeed.collection) {
      const failed = buildFailureReceipt({
        actionId,
        actor: input.actor,
        domain,
        tool: actionToolByIntent,
        now,
        idempotencyKey,
        command: commandText,
        reason: 'I need one specific collection to create a record.',
        records: [],
      });
      return {
        state: failed.status,
        receipt: failed,
        action: createActionEvent({
          id: actionId,
          actor: input.actor,
          domain,
          tool: actionToolByIntent,
          risk: policy.risk as ActionRisk,
          recordIds: [],
          idempotencyKey,
          command: commandText,
          before: null,
          after: null,
          undoPayload: null,
        }),
        step: input.step,
        replayed: false,
      };
    }

    writeResult = createRecordWithAction({
      actionId,
      actor: input.actor,
      domain,
      tool: actionToolByIntent,
      risk: policy.risk as ActionRisk,
      command: commandText,
      idempotencyKey,
      record: {
        id:
          input.recordId
          || deterministicRecordId({
            operation: 'command-create',
            domain,
            collection: inverseSeed.collection,
            title: inverseSeed.title,
            sourceHome: 'user',
            payload: {
              actor: input.actor,
              conversationId: input.conversationId,
              tool: actionToolByIntent,
              command: commandText,
            },
          }),
        domain,
        collection: inverseSeed.collection,
        title: inverseSeed.title,
        properties: {},
        relations: [],
        source: inverseSeed.source || {
          provider: 'user',
          external_id: actionId,
          url: null,
          observed_at: now,
          content_hash: null,
        },
        archived_at: null,
      },
      sourceIds: [],
      conversationId: input.conversationId ?? null,
      before: null,
      undoPayload: {
        operation: 'delete_record',
        record_id: input.recordId
          || deterministicRecordId({
            operation: 'command-create',
            domain,
            collection: inverseSeed.collection,
            title: inverseSeed.title,
            sourceHome: 'user',
            payload: {
              actor: input.actor,
              conversationId: input.conversationId,
              tool: actionToolByIntent,
              command: commandText,
            },
          }),
      },
    });
  }

  if (intent.type === 'update') {
    if (!inverseSeed.id) {
      const failed = buildFailureReceipt({
        actionId,
        actor: input.actor,
        domain,
        tool: actionToolByIntent,
        now,
        idempotencyKey,
        command: commandText,
        reason: `Could not locate ${intent.collection ?? 'record'} target for update.`,
      });
      return {
        state: failed.status,
        receipt: failed,
          action: createActionEvent({
            id: actionId,
            actor: input.actor,
            domain,
            tool: actionToolByIntent,
            risk: policy.risk as ActionRisk,
            recordIds: intent.recordId && inverseSeed.id ? [inverseSeed.id] : [],
          idempotencyKey,
          command: commandText,
          before: null,
          after: null,
          undoPayload: null,
        }),
        step: input.step,
        replayed: false,
      };
    }

    writeResult = updateRecordWithAction({
      actionId,
      actor: input.actor,
      domain,
      tool: actionToolByIntent,
      risk: policy.risk as ActionRisk,
      command: commandText,
      id: inverseSeed.id,
      patch: inverseSeed.patch ?? {},
      idempotencyKey,
      source: inverseSeed.source,
      sourceIds: [],
      conversationId: input.conversationId ?? null,
      undoPayload: {
        operation: 'restore_after_update',
        before: null,
        record_id: inverseSeed.id,
      },
    });
  }

  if (intent.type === 'archive') {
    if (!inverseSeed.id) {
      const failed = buildFailureReceipt({
        actionId,
        actor: input.actor,
        domain,
        tool: actionToolByIntent,
        now,
        idempotencyKey,
        command: commandText,
        reason: `Could not locate ${intent.collection ?? 'record'} target for archive.`,
      });
      return {
        state: failed.status,
        receipt: failed,
        action: createActionEvent({
          id: actionId,
          actor: input.actor,
          domain,
          tool: actionToolByIntent,
          risk: policy.risk as ActionRisk,
          recordIds: inverseSeed.id ? [inverseSeed.id] : [],
          idempotencyKey,
          command: commandText,
          before: null,
          after: null,
          undoPayload: null,
        }),
        step: input.step,
        replayed: false,
      };
    }

    writeResult = archiveRecordWithAction({
      actionId,
      actor: input.actor,
      domain,
      tool: actionToolByIntent,
      risk: policy.risk as ActionRisk,
      command: commandText,
      id: inverseSeed.id,
      idempotencyKey,
      source: inverseSeed.source,
      sourceIds: [],
      conversationId: input.conversationId ?? null,
      undoPayload: {
        operation: 'restore_after_archive',
        record_id: inverseSeed.id,
      },
    });
  }

  if (!writeResult || !writeResult.action) {
    const failed = buildFailureReceipt({
      actionId,
      actor: input.actor,
      domain,
      tool: actionTool,
      now,
      idempotencyKey,
      command: commandText,
      reason: 'Mutating command execution failed before mutation.',
    });
    return {
      state: failed.status,
      receipt: failed,
      action: createActionEvent({
        id: actionId,
        actor: input.actor,
        domain,
        tool: actionTool,
        risk: policy.risk as ActionRisk,
        recordIds: [],
        idempotencyKey,
        command: commandText,
        before: null,
        after: null,
        undoPayload: null,
      }),
      step: input.step,
      replayed: false,
    };
  }

  const completed = markActionCompleted(writeResult.action.id, writeResult.action.command, {
    ...(writeResult.record ? { record: writeResult.record } : null),
  });
  const receipt = toReceipt(completed ?? writeResult.action);

  return {
    state: receipt.status,
    receipt,
    action: completed ?? writeResult.action,
    step: input.step,
    replayed: writeResult.replayed,
  };
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
  source?: McpRecord['source'];
}) {
  const result = executeCommandFromIntent({
    actionId: input.actionId,
    actor: input.actor,
    domain: input.domain,
    tool: input.tool,
    command: input.commandText,
    idempotencyKey: input.idempotencyKey,
    conversationId: input.conversationId,
    step: input.step,
    source: input.source,
    intent: parseCommandIntent(input.commandText),
  });

  return {
    state: result.state,
    receipt: result.receipt,
    step: result.step,
  };
}
