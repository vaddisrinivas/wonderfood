import { evaluateMcpPolicy } from './policy';
import { readMcpResource } from './resources';
import {
  WorkflowDocument,
  createActionEvent,
  createRecord,
  findActionByIdempotencyKey,
  findRecord,
  findWorkflow,
  getActionEvent,
  listRecords,
  markActionCompleted,
  markActionFailed,
  runUndo,
  updateRecord,
  archiveRecord,
  listWorkflows,
  McpRecord,
} from './state';

export type McpToolDefinition = {
  name: string;
  description: string;
  inputSchema: Record<string, unknown>;
};

export type ToolResult = {
  json: unknown;
  reviewOnly: boolean;
  safety: string;
};

export type ValidationResult = {
  valid: boolean;
  errors: string[];
  reviewOnly: boolean;
  nextStep: string;
};

const ACTION_TOOLS = {
  status: 'wonderfood.status',
  getResource: 'wonderfood.get_resource',
  validateCommandEnvelope: 'wonderfood.validate_command_envelope',
  proposeAppLink: 'wonderfood.propose_app_link',
  wrapProposalPackage: 'wonderfood.wrap_proposal_package',
};

const RECORD_TOOLS = {
  searchRecords: 'wonderfood.search_records',
  readRecord: 'wonderfood.read_record',
  createRecord: 'wonderfood.create_record',
  updateRecord: 'wonderfood.update_record',
  archiveRecord: 'wonderfood.archive_record',
  runWorkflow: 'wonderfood.run_workflow',
  undoAction: 'wonderfood.undo_action',
};

const MAX_ACTIONS_PER_REQUEST = 12;
const DATA_HOME = ['local_sqlite', 'notion', 'google_sheets', 'postgres'];
const SCHEME_HTTPS = 'https';
const SCHEME_WONDERFOOD = 'wonderfood';
const MAX_RECORD_SEARCH_RESULTS = 200;
const WORKFLOW_MAX_STEPS = 30;
const PROTOCOL_VERSION = '2026-03-11';

function makeText(value: unknown) {
  return typeof value === 'string' ? value.trim() : '';
}

function ensureDomain(raw: unknown): string {
  const value = makeText(raw);
  return value.length > 0 ? value : 'food';
}

function ensureId(raw: unknown): string {
  const value = makeText(raw);
  if (!value) {
    return `mcp-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;
  }
  return value;
}

function ensureActor(raw: unknown): string {
  const value = makeText(raw);
  return value.length > 0 ? value : 'hearth';
}

function ensureConversationId(raw: unknown): string | null {
  const value = makeText(raw);
  return value.length > 0 ? value : null;
}

function ensureCollection(raw: unknown): string {
  const value = makeText(raw);
  if (!value) {
    throw new Error('collection is required');
  }
  return value;
}

function ensureTool(raw: unknown) {
  return makeText(raw);
}

function normalizeList(raw: unknown): string[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw
    .map((value) => makeText(value))
    .filter((value): value is string => value.length > 0);
}

function normalizeQuery(raw: unknown): string {
  const value = makeText(raw);
  return value.length > 0 ? value : '';
}

function safeJsonText(raw: unknown) {
  if (!raw) return '';
  if (typeof raw === 'string') return raw;
  return JSON.stringify(raw);
}

function actionToolPolicy(input: { tool: string; domain: string; command: string; actor: string }) {
  return evaluateMcpPolicy(input);
}

function makeActionId(prefix: string) {
  return `${prefix}:${Date.now()}:${Math.random().toString(16).slice(2, 9)}`;
}

function makeActionEvent(input: {
  tool: string;
  domain: string;
  actor: string;
  recordIds: string[];
  command: string;
  risk: 'low' | 'standard' | 'sensitive' | 'irreversible' | 'restricted';
  idempotencyKey?: string;
  sourceIds?: string[];
  conversationId?: string | null;
  before?: unknown;
  after?: unknown;
  undoPayload?: unknown;
}) {
  const actionId = makeActionId(input.tool);
  return {
    id: actionId,
    event: createActionEvent({
      id: actionId,
      actor: input.actor,
      domain: input.domain,
      tool: input.tool,
      risk: input.risk,
      recordIds: input.recordIds,
      idempotencyKey: input.idempotencyKey,
      command: input.command,
      before: input.before,
      after: input.after,
      undoPayload: input.undoPayload,
      sourceIds: input.sourceIds,
      conversationId: input.conversationId,
    }),
  };
}

function getOrCreateActionFromPolicy(input: {
  tool: string;
  domain: string;
  actor: string;
  command: string;
  conversationId?: string | null;
  policy: ReturnType<typeof evaluateMcpPolicy>;
  idempotencyKey?: string;
  recordIds: string[];
  after?: unknown;
  before?: unknown;
  undoPayload?: unknown;
}): ToolResult {
  if (!input.policy.allowed) {
    return {
      reviewOnly: true,
      safety: input.policy.safety,
      json: {
        allowed: false,
        policy: input.policy,
      },
    };
  }
  if (input.policy.requiresClarification) {
    return {
      reviewOnly: true,
      safety: input.policy.safety,
      json: {
        allowed: false,
        requiresClarification: true,
        clarifyingQuestion: input.policy.clarifyingQuestion,
        policy: input.policy,
      },
    };
  }

  const { event } = makeActionEvent({
    tool: input.tool,
    domain: input.domain,
    actor: input.actor,
    risk: input.policy.risk,
    recordIds: input.recordIds,
    command: input.command,
    idempotencyKey: input.idempotencyKey,
    before: input.before,
    after: input.after,
    undoPayload: input.undoPayload,
    conversationId: input.conversationId,
  });
  return {
    reviewOnly: false,
    safety: input.policy.safety,
    json: {
      action: event,
    },
  };
}

function resolveToolResult(result: unknown, reviewOnly = false, safety = 'read-only'): ToolResult {
  return {
    reviewOnly,
    safety,
    json: result,
  };
}

function parseWorkflowStepInput(step: Record<string, unknown>): Record<string, unknown> {
  const tool = ensureTool(step.tool);
  const action = ensureTool(step.action);
  const normalizedTool = tool || action;
  return {
    tool: normalizedTool,
    required: typeof step.required === 'boolean' ? step.required : true,
    input: isObject(step.input) ? (step.input as Record<string, unknown>) : {},
    ...step,
  };
}

function normalizeWorkflowToolName(raw: string): string {
  if (raw === 'search') {
    return 'search_records';
  }
  if (raw === 'read') {
    return 'read_record';
  }
  return raw;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function runWorkflowStep(
  toolInput: Record<string, unknown>,
  actor: string,
  workflow: WorkflowDocument,
  context: {
    seenWorkflows: Set<string>;
    visitedRecords: Set<string>;
  },
): { status: 'ok' | 'failed' | 'skipped'; tool: string; stepResult: unknown; changedRecords: string[] } {
  const tool = normalizeWorkflowToolName(ensureTool(toolInput.tool));
  if (!tool) {
    return {
      status: 'failed',
      tool: '',
      stepResult: { error: 'tool is required' },
      changedRecords: [],
    };
  }
  const input = isObject(toolInput.input) ? toolInput.input : {};

  const domain = ensureDomain(input.domain);
  const commandText = `${tool} ${safeJsonText(input)}`;
  const policy = evaluateMcpPolicy({
    tool: `wonderfood.${tool}`,
    domain,
    command: commandText,
    actor,
  });
  if (!policy.allowed || policy.requiresClarification) {
    return {
      status: policy.requiresClarification ? 'failed' : 'skipped',
      tool,
      stepResult: { policy },
      changedRecords: [],
    };
  }

  if (tool === 'search_records') {
    const query = normalizeQuery((input as { query?: unknown }).query);
    const collection = ensureCollection((input as { collection?: unknown }).collection);
    const records = listRecords({
      domain,
      collection,
      includeArchived: Boolean((input as { includeArchived?: unknown }).includeArchived),
      query,
      limit: Math.min(
        Number((input as { limit?: unknown }).limit) || MAX_RECORD_SEARCH_RESULTS,
        MAX_RECORD_SEARCH_RESULTS,
      ),
    });
    return {
      status: 'ok',
      tool,
      stepResult: { count: records.length },
      changedRecords: [],
    };
  }

  if (tool === 'read_record') {
    const id = ensureId((input as { id?: unknown }).id);
    const record = findRecord(id);
    return {
      status: record ? 'ok' : 'failed',
      tool,
      stepResult: record ? { record } : { error: 'record_not_found', id },
      changedRecords: [],
    };
  }

  if (tool === 'create_record' || tool === 'run_workflow') {
    if (tool === 'run_workflow') {
      const workflowId = makeText(input.workflow || input.id);
      if (!workflowId) {
        return { status: 'failed', tool, stepResult: { error: 'workflow id required' }, changedRecords: [] };
      }
      const nextWorkflow = findWorkflow(workflowId);
      if (!nextWorkflow || context.seenWorkflows.has(workflowId)) {
        return {
          status: 'failed',
          tool,
          stepResult: { error: 'workflow_not_found_or_recursive', workflowId },
          changedRecords: [],
        };
      }
      return runWorkflow(nextWorkflow, actor, context);
    }

    const recInput = input.record ?? input;
    const collection = ensureCollection((recInput as { collection?: unknown }).collection);
    const title = makeText((recInput as { title?: unknown }).title);
    const properties = isObject((recInput as { properties?: unknown }).properties)
      ? ((recInput as { properties: Record<string, unknown> }).properties)
      : {};
    const relations = Array.isArray((recInput as { relations?: unknown }).relations)
      ? (recInput as { relations: unknown[] }).relations
      : [];
    const record = createRecord({
      id: makeText((recInput as { id?: unknown }).id),
      domain,
      collection,
      title,
      properties,
      relations: isObject(relations[0] as object) ? (relations as McpRecord['relations']) : [],
      source: {
        provider: 'sqlite',
        external_id: makeText((recInput as { external_id?: unknown }).external_id),
        url: null,
        observed_at: new Date().toISOString(),
        content_hash: null,
      },
      archived_at: null,
    } as Omit<McpRecord, 'created_at' | 'updated_at'>);
    return {
      status: 'ok',
      tool,
      stepResult: { id: record.id },
      changedRecords: [record.id],
    };
  }

  if (tool === 'update_record') {
    const id = makeText((input as { id?: unknown }).id);
    const patch = isObject((input as { patch?: unknown }).patch)
      ? ((input as { patch: Record<string, unknown> }).patch)
      : {};
    const updated = updateRecord(id, patch as Partial<McpRecord>);
    if (!updated) {
      return { status: 'failed', tool, stepResult: { error: 'record_not_found', id }, changedRecords: [] };
    }
    return { status: 'ok', tool, stepResult: updated.after, changedRecords: [updated.after.id] };
  }

  if (tool === 'archive_record') {
    const id = makeText((input as { id?: unknown }).id);
    const result = archiveRecord(id);
    if (!result) {
      return { status: 'failed', tool, stepResult: { error: 'record_not_found', id }, changedRecords: [] };
    }
    return { status: 'ok', tool, stepResult: result.after, changedRecords: [result.after.id] };
  }

  return {
    status: 'skipped',
    tool,
    stepResult: { skipped: true, tool },
    changedRecords: [],
  };
}

function runWorkflow(workflow: WorkflowDocument, actor: string, context: { seenWorkflows: Set<string>; visitedRecords: Set<string> }) {
  context.seenWorkflows.add(workflow.id);
  const stepReports: Array<{
    id: string;
    tool: string;
    status: 'ok' | 'failed' | 'skipped';
    result: unknown;
  }> = [];
  const changed: string[] = [];

  const steps = workflow.steps.slice(0, WORKFLOW_MAX_STEPS);
  for (const step of steps) {
    const parsed = parseWorkflowStepInput(step as Record<string, unknown>);
    const resolved = runWorkflowStep(parsed, actor, workflow, context);
    stepReports.push({ id: parsed.id, tool: parsed.tool, status: resolved.status, result: resolved.stepResult });
    for (const id of resolved.changedRecords) {
      changed.push(id);
      context.visitedRecords.add(id);
    }

    if (resolved.status === 'failed' && parsed.required !== false) {
      return {
        status: 'failed',
        details: stepReports,
        changedRecords: changed,
      };
    }
  }

  return {
    status: 'ok',
    details: stepReports,
    changedRecords: changed,
  };
}

function createRunWorkflowAction({
  workflow,
  domain,
  actor,
  command,
  idempotencyKey,
  conversationId,
}: {
  workflow: WorkflowDocument;
  domain: string;
  actor: string;
  command: string;
  idempotencyKey?: string;
  conversationId?: string | null;
}) {
  const existing = idempotencyKey ? findActionByIdempotencyKey(idempotencyKey) : null;
  if (existing) {
    return existing;
  }

  const action = createActionEvent({
    id: makeActionId(`workflow:${workflow.id}`),
    actor,
    domain,
    tool: RECORD_TOOLS.runWorkflow,
    risk: 'standard',
    recordIds: [],
    idempotencyKey,
    command,
    before: {
      workflow: workflow.id,
      steps: workflow.steps.length,
    },
    conversationId,
  });
  return action;
}

export function listMcpTools(): McpToolDefinition[] {
  const toolSet = [
    {
      name: ACTION_TOOLS.status,
      description: 'Show WonderFood MCP capabilities, resources, and safety posture.',
      inputSchema: {
        type: 'object',
        additionalProperties: false,
        properties: {},
      },
    },
    {
      name: ACTION_TOOLS.getResource,
      description: 'Read a WonderFood skill, schema, or app command contract resource.',
      inputSchema: {
        type: 'object',
        additionalProperties: false,
        required: ['uri'],
        properties: {
          uri: { type: 'string' },
        },
      },
    },
    {
      name: ACTION_TOOLS.validateCommandEnvelope,
      description: 'Validate a command envelope shape and policy context.',
      inputSchema: {
        type: 'object',
        additionalProperties: false,
        required: ['envelope'],
        properties: {
          envelope: { type: 'object', additionalProperties: true },
        },
      },
    },
    {
      name: ACTION_TOOLS.proposeAppLink,
      description: 'Create a review-only WonderFood action link for one or more supported app actions.',
      inputSchema: {
        type: 'object',
        additionalProperties: false,
        required: ['requestId', 'actions'],
        properties: {
          requestId: { type: 'string', minLength: 1 },
          actions: {
            type: 'array',
            minItems: 1,
            maxItems: MAX_ACTIONS_PER_REQUEST,
            items: {
              type: 'object',
              additionalProperties: true,
              required: ['type'],
            },
          },
          scheme: { type: 'string', enum: [SCHEME_HTTPS, SCHEME_WONDERFOOD] },
          actor: { type: 'string', minLength: 1 },
        },
      },
    },
    {
      name: ACTION_TOOLS.wrapProposalPackage,
      description: 'Wrap a validated command envelope in proposal-package format.',
      inputSchema: {
        type: 'object',
        additionalProperties: false,
        required: ['proposal_id', 'producer', 'command_envelope'],
        properties: {
          proposal_id: { type: 'string', minLength: 1 },
          producer: { type: 'string', minLength: 1 },
          command_envelope: { type: 'object', additionalProperties: true },
        },
      },
    },
    {
      name: RECORD_TOOLS.searchRecords,
      description: 'Search canonical records by collection/query.',
      inputSchema: {
        type: 'object',
        additionalProperties: false,
        required: ['domain', 'collection'],
        properties: {
          domain: { type: 'string', minLength: 1 },
          collection: { type: 'string', minLength: 1 },
          query: { type: 'string' },
          includeArchived: { type: 'boolean' },
          limit: { type: 'number', minimum: 1, maximum: MAX_RECORD_SEARCH_RESULTS },
        },
      },
    },
    {
      name: RECORD_TOOLS.readRecord,
      description: 'Read one canonical record by id.',
      inputSchema: {
        type: 'object',
        additionalProperties: false,
        required: ['id'],
        properties: {
          id: { type: 'string', minLength: 1 },
        },
      },
    },
    {
      name: RECORD_TOOLS.createRecord,
      description: 'Create one canonical record.',
      inputSchema: {
        type: 'object',
        additionalProperties: false,
        required: ['domain', 'collection'],
        properties: {
          actor: { type: 'string' },
          domain: { type: 'string', minLength: 1 },
          collection: { type: 'string', minLength: 1 },
          id: { type: 'string' },
          title: { type: 'string' },
          properties: { type: 'object' },
          relations: { type: 'array' },
          source: { type: 'object' },
          idempotency_key: { type: 'string' },
          action_id: { type: 'string' },
          conversation_id: { type: 'string' },
        },
      },
    },
    {
      name: RECORD_TOOLS.updateRecord,
      description: 'Update one canonical record.',
      inputSchema: {
        type: 'object',
        additionalProperties: false,
        required: ['id', 'patch'],
        properties: {
          actor: { type: 'string' },
          id: { type: 'string', minLength: 1 },
          patch: { type: 'object', additionalProperties: true },
          domain: { type: 'string', minLength: 1 },
          idempotency_key: { type: 'string' },
          action_id: { type: 'string' },
          conversation_id: { type: 'string' },
        },
      },
    },
    {
      name: RECORD_TOOLS.archiveRecord,
      description: 'Archive one canonical record.',
      inputSchema: {
        type: 'object',
        additionalProperties: false,
        required: ['id'],
        properties: {
          actor: { type: 'string' },
          id: { type: 'string', minLength: 1 },
          domain: { type: 'string', minLength: 1 },
          idempotency_key: { type: 'string' },
          action_id: { type: 'string' },
          conversation_id: { type: 'string' },
        },
      },
    },
    {
      name: RECORD_TOOLS.runWorkflow,
      description: 'Run a declared workflow with checkpoints.',
      inputSchema: {
        type: 'object',
        additionalProperties: false,
        required: ['workflow'],
        properties: {
          actor: { type: 'string' },
          workflow: { type: 'string', minLength: 1 },
          domain: { type: 'string', minLength: 1 },
          idempotency_key: { type: 'string' },
          action_id: { type: 'string' },
          conversation_id: { type: 'string' },
        },
      },
    },
    {
      name: RECORD_TOOLS.undoAction,
      description: 'Undo a completed reversible action.',
      inputSchema: {
        type: 'object',
        additionalProperties: false,
        required: ['actionId'],
        properties: {
          actor: { type: 'string' },
          actionId: { type: 'string', minLength: 1 },
          idempotency_key: { type: 'string' },
          action_id: { type: 'string' },
        },
      },
    },
  ];

  return toolSet;
}

export function callMcpTool(name: string, args: Record<string, unknown>): ToolResult {
  const typedArgs = args;

  if (name === ACTION_TOOLS.status) {
    return resolveToolResult({
      server: {
        name: 'wonderfood-lifeos-server',
        version: '1.0.0',
        protocolVersion: PROTOCOL_VERSION,
      },
      safety: 'review-only; no direct writes; generated links/packages must be accepted in app',
      dataHomes: DATA_HOME,
      tools: listMcpTools().map((tool) => tool.name),
      resources: ['wonderfood://domain-catalog', 'wonderfood://lifeos/domain-catalog-v1'],
    });
  }

  if (name === ACTION_TOOLS.getResource) {
    const uri = makeText(typedArgs.uri);
    if (!uri) {
      throw new Error('uri is required');
    }
    return resolveToolResult({ uri, text: readMcpResource(uri) }, false, 'read-only');
  }

  if (name === ACTION_TOOLS.validateCommandEnvelope) {
    const envelope = typedArgs.envelope;
    if (!envelope || !isObject(envelope)) {
      throw new Error('envelope must be an object');
    }
    return resolveToolResult(validateCommandEnvelopeLite(envelope), true, 'validation-only');
  }

  if (name === ACTION_TOOLS.proposeAppLink) {
    const requestId = makeText(typedArgs.requestId);
    const actor = ensureActor(typedArgs.actor);
    const actionsRaw = isObject(typedArgs.actions) || Array.isArray((typedArgs as { actions?: unknown }).actions)
      ? (typedArgs.actions as unknown[])
      : [];
    const scheme = makeText(typedArgs.scheme) || SCHEME_HTTPS;
    const actions = normalizeList(actionsRaw);
    if (!requestId) {
      throw new Error('requestId is required');
    }
    if (actions.length === 0) {
      throw new Error('actions must be a non-empty list');
    }
    if (actions.length > MAX_ACTIONS_PER_REQUEST) {
      throw new Error(`At most ${MAX_ACTIONS_PER_REQUEST} actions are supported per request`);
    }
    const cleanActions = actions.map((entry) => {
      if (!entry || typeof entry !== 'object') {
        throw new Error('each action must be an object');
      }
      const candidate = { ...entry } as Record<string, unknown>;
      const type = makeText(candidate.type);
      if (!type) {
        throw new Error('each action must include type');
      }
      candidate.type = type;
      if (!candidate.type.includes('.')) {
        candidate.type = `inventory.${candidate.type}`;
      }
      return candidate;
    });

    const base = scheme === SCHEME_WONDERFOOD ? 'wonderfood://action' : 'https://wonderfood.app/action';
    const query = new URLSearchParams({ requestId, actor });
    if (cleanActions.length === 1) {
      const action = cleanActions[0];
      for (const [key, value] of Object.entries(action)) {
        if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
          query.set(key, String(value));
        }
      }
    } else {
      query.set('actions', JSON.stringify(cleanActions));
    }

    return resolveToolResult({
      reviewOnly: true,
      url: `${base}?${query.toString()}`,
      actionCount: cleanActions.length,
      actor,
    }, true, 'review-only');
  }

  if (name === ACTION_TOOLS.wrapProposalPackage) {
    const proposalId = makeText(typedArgs.proposal_id);
    const producer = makeText(typedArgs.producer);
    const commandEnvelope = typedArgs.command_envelope;
    if (!proposalId || !producer) {
      throw new Error('proposal_id and producer are required');
    }
    if (!commandEnvelope || !isObject(commandEnvelope)) {
      throw new Error('command_envelope must be an object');
    }
    const validation = validateCommandEnvelopeLite(commandEnvelope);
    if (!validation.valid) {
      return resolveToolResult({
        valid: false,
        errors: validation.errors,
        reviewOnly: true,
      }, true, 'invalid-envelope');
    }

    const now = new Date();
    const packagePayload = {
      schema_version: 'wf.proposal-package.v1',
      proposal_id: proposalId,
      origin: { kind: 'gpt_skill', producer },
      created_at: now.toISOString(),
      expires_at: new Date(now.getTime() + 24 * 60 * 60 * 1000).toISOString(),
      command_envelope: commandEnvelope,
      signature: null,
    };

    return resolveToolResult({ valid: true, reviewOnly: true, package: packagePayload }, true, 'review-only');
  }

  if (name === RECORD_TOOLS.searchRecords) {
    const domain = ensureDomain(typedArgs.domain);
    const collection = ensureCollection(typedArgs.collection);
    const query = normalizeQuery(typedArgs.query);
    const limit = typeof typedArgs.limit === 'number' && typedArgs.limit > 0
      ? typedArgs.limit
      : MAX_RECORD_SEARCH_RESULTS;

    const records = listRecords({
      domain,
      collection,
      query,
      includeArchived: Boolean(typedArgs.includeArchived),
      limit: Math.min(limit, MAX_RECORD_SEARCH_RESULTS),
    });
    return resolveToolResult(
      {
        count: records.length,
        domain,
        collection,
        includeArchived: Boolean(typedArgs.includeArchived),
        records,
      },
      true,
      'read-only',
    );
  }

  if (name === RECORD_TOOLS.readRecord) {
    const id = makeText(typedArgs.id);
    if (!id) {
      throw new Error('id is required');
    }
    const record = findRecord(id);
    if (!record) {
      throw new Error(`record ${id} not found`);
    }
    return resolveToolResult({ record }, true, 'read-only');
  }

  if (name === RECORD_TOOLS.createRecord) {
    const actor = ensureActor(typedArgs.actor);
    const domain = ensureDomain(typedArgs.domain);
    const collection = ensureCollection(typedArgs.collection);
    const idempotencyKey = makeText(typedArgs.idempotency_key) || undefined;
    const actionId = makeText(typedArgs.action_id) || makeActionId('create_record');
    const conversationId = ensureConversationId(typedArgs.conversation_id);

    const recordInput = typedArgs.record ?? typedArgs;
    const title = makeText(recordInput.title);
    const properties = isObject((recordInput as { properties?: unknown }).properties)
      ? ((recordInput as { properties: Record<string, unknown> }).properties)
      : {};
    const relations = Array.isArray((recordInput as { relations?: unknown }).relations)
      ? ((recordInput as { relations: unknown[] }).relations as McpRecord['relations'])
      : [];

    const policy = actionToolPolicy({
      tool: name,
      domain,
      command: `create ${collection} ${title || 'record'}`,
      actor,
    });
    if (!policy.allowed || policy.requiresClarification) {
      return resolveToolResult({ allowed: false, policy }, true, policy.safety);
    }

    const existing = idempotencyKey ? findActionByIdempotencyKey(idempotencyKey) : null;
    if (existing && existing.status === 'completed') {
      const replayRecord = existing.record_ids[0] ? findRecord(existing.record_ids[0]) : null;
      return resolveToolResult({
        action: existing,
        replayed: true,
        record: replayRecord,
      }, false, existing.status === 'failed' ? 'review-only' : 'write');
    }

    const created = createRecord({
      id: ensureId(recordInput.id || actionId),
      domain,
      collection,
      title,
      properties,
      relations,
      source: {
        provider: 'sqlite',
        external_id: makeText((recordInput as { external_id?: unknown }).external_id),
        url: null,
        observed_at: new Date().toISOString(),
        content_hash: null,
      },
      archived_at: null,
    } as Omit<McpRecord, 'created_at' | 'updated_at'>);
    const action = createActionEvent({
      id: existing?.id ?? actionId,
      actor,
      domain,
      tool: name,
      risk: policy.risk,
      recordIds: [created.id],
      idempotencyKey,
      command: `create_record:${collection}`,
      after: created,
      undoPayload: { operation: 'delete_record', record_id: created.id },
      sourceIds: existing ? existing.source_ids : [],
      conversationId,
    });
    const completed = markActionCompleted(action.id, action.command, {
      record: created,
    });
    return resolveToolResult({ action: completed || action, record: created }, false, 'write');
  }

  if (name === RECORD_TOOLS.updateRecord) {
    const actor = ensureActor(typedArgs.actor);
    const id = makeText(typedArgs.id);
    const patch = isObject(typedArgs.patch) ? (typedArgs.patch as Record<string, unknown>) : null;
    if (!id || !patch) {
      throw new Error('id and patch are required');
    }
    const existing = findRecord(id);
    if (!existing) {
      throw new Error(`record ${id} not found`);
    }
    const domain = ensureDomain(typedArgs.domain || existing.domain);
    const idempotencyKey = makeText(typedArgs.idempotency_key) || undefined;
    const actionId = makeText(typedArgs.action_id) || makeActionId(`update:${id}`);
    const conversationId = ensureConversationId(typedArgs.conversation_id);
    const policy = actionToolPolicy({
      tool: name,
      domain,
      command: `update ${existing.collection} ${id}`,
      actor,
    });
    const preCheck = getOrCreateActionFromPolicy({
      tool: name,
      domain,
      actor,
      command: `update_record:${id}`,
      policy,
      idempotencyKey,
      recordIds: [id],
      before: existing,
      conversationId,
    });
    if (preCheck.reviewOnly) {
      return preCheck;
    }
    const repeated = idempotencyKey ? findActionByIdempotencyKey(idempotencyKey) : null;
    if (repeated && repeated.status === 'completed') {
      const replay = findRecord(id);
      return resolveToolResult({ action: repeated, replayed: true, record: replay }, false, 'write');
    }

    const updated = updateRecord(id, patch as Partial<McpRecord>);
    if (!updated) {
      markActionFailed(preCheck.json?.action?.id || actionId);
      throw new Error(`record ${id} not found`);
    }
    const action = repeated ?? createActionEvent({
      id: actionId,
      actor,
      domain,
      tool: name,
      risk: policy.risk,
      recordIds: [updated.after.id],
      idempotencyKey,
      command: `update_record:${id}`,
      before: updated.before,
      after: updated.after,
      undoPayload: { operation: 'restore_after_update', before: updated.before },
      conversationId,
    });
    const completed = markActionCompleted((repeated ?? action).id, action.command, { record: updated.after });
    return resolveToolResult({ action: completed || action, record: updated.after }, false, 'write');
  }

  if (name === RECORD_TOOLS.archiveRecord) {
    const actor = ensureActor(typedArgs.actor);
    const id = makeText(typedArgs.id);
    if (!id) {
      throw new Error('id is required');
    }
    const existing = findRecord(id);
    if (!existing) {
      throw new Error(`record ${id} not found`);
    }
    const domain = ensureDomain(typedArgs.domain || existing.domain);
    const idempotencyKey = makeText(typedArgs.idempotency_key) || undefined;
    const actionId = makeText(typedArgs.action_id) || makeActionId(`archive:${id}`);
    const conversationId = ensureConversationId(typedArgs.conversation_id);
    const policy = actionToolPolicy({
      tool: name,
      domain,
      command: `archive ${existing.collection} ${id}`,
      actor,
    });
    if (!policy.allowed || policy.requiresClarification) {
      return getOrCreateActionFromPolicy({
        tool: name,
        domain,
        actor,
        command: `archive_record:${id}`,
        policy,
        idempotencyKey,
        recordIds: [id],
        before: existing,
        conversationId,
      });
    }

    const repeated = idempotencyKey ? findActionByIdempotencyKey(idempotencyKey) : null;
    if (repeated && repeated.status === 'completed') {
      return resolveToolResult({ action: repeated, replayed: true, record: findRecord(id) }, false, 'write');
    }

    const archived = archiveRecord(id);
    if (!archived) {
      throw new Error(`record ${id} not found`);
    }
    const action = repeated ?? createActionEvent({
      id: actionId,
      actor,
      domain,
      tool: name,
      risk: policy.risk,
      recordIds: [archived.after.id],
      idempotencyKey,
      command: `archive_record:${id}`,
      before: archived.before,
      after: archived.after,
      undoPayload: { operation: 'restore_after_archive', record: archived.before },
      conversationId,
    });
    const completed = markActionCompleted(action.id, action.command, { record: archived.after });
    return resolveToolResult({ action: completed || action, record: archived.after }, false, 'write');
  }

  if (name === RECORD_TOOLS.runWorkflow) {
    const actor = ensureActor(typedArgs.actor);
    const workflowId = makeText(typedArgs.workflow);
    if (!workflowId) {
      throw new Error('workflow is required');
    }
    const domain = ensureDomain(typedArgs.domain);
    const idempotencyKey = makeText(typedArgs.idempotency_key) || undefined;
    const actionId = makeText(typedArgs.action_id) || makeActionId(`workflow:${workflowId}`);
    const conversationId = ensureConversationId(typedArgs.conversation_id);
    const policy = actionToolPolicy({
      tool: name,
      domain,
      command: `run_workflow ${workflowId}`,
      actor,
    });
    const preexisting = idempotencyKey ? findActionByIdempotencyKey(idempotencyKey) : null;
    if (preexisting && preexisting.status === 'completed') {
      return resolveToolResult({
        action: preexisting,
        replayed: true,
      }, false, 'write');
    }
    if (!policy.allowed || policy.requiresClarification) {
      return resolveToolResult({
        allowed: false,
        requiresClarification: true,
        policy,
        message: 'workflow run blocked by policy',
      }, true, policy.safety);
    }

    const workflow = findWorkflow(workflowId);
    if (!workflow) {
      return resolveToolResult({
        found: false,
        status: 'failed',
        message: `workflow not found: ${workflowId}`,
        workflows: listWorkflows().map((entry) => entry.id),
      }, false, 'review-only');
    }

    const action = preexisting
      || createRunWorkflowAction({
        workflow,
        domain,
        actor,
        command: `run_workflow:${workflowId}`,
        idempotencyKey,
        conversationId,
      });
    if (action.status === 'completed') {
      return resolveToolResult({ action, replayed: true }, false, 'write');
    }

    const execution = runWorkflow(workflow, actor, { seenWorkflows: new Set([workflowId]), visitedRecords: new Set() });
    const recordIds = execution.changedRecords;
    if (execution.status !== 'ok') {
      const failed = markActionFailed(action.id, 'workflow failed');
      return resolveToolResult({
        status: 'failed',
        message: `workflow ${workflowId} failed`,
        action: failed,
        details: execution.details,
      }, false, 'review-only');
    }
    const completed = markActionCompleted(action.id, action.command, {
      workflow: workflowId,
      steps: execution.details,
      changed_records: recordIds,
    });
    return resolveToolResult({
      status: 'completed',
      action: completed || action,
      workflow: workflowId,
      changed_records: recordIds,
    }, false, 'write');
  }

  if (name === RECORD_TOOLS.undoAction) {
    const actor = ensureActor(typedArgs.actor);
    const actionId = makeText(typedArgs.actionId);
    if (!actionId) {
      throw new Error('actionId is required');
    }
    const idempotencyKey = makeText(typedArgs.idempotency_key) || undefined;
    const action = getActionEvent(actionId);
    if (!action) {
      throw new Error(`action not found: ${actionId}`);
    }
    const policy = actionToolPolicy({
      tool: name,
      domain: action.domain,
      command: `undo ${actionId}`,
      actor,
    });
    if (!policy.allowed || policy.requiresClarification) {
      return resolveToolResult({ allowed: false, policy }, true, policy.safety);
    }

    const existing = idempotencyKey ? findActionByIdempotencyKey(idempotencyKey) : null;
    if (existing) {
      return resolveToolResult({ action: existing, replayed: true }, false, 'write');
    }

    const undoPayload = { actionId, actor };
    const undoAction = createActionEvent({
      id: makeActionId(`undo:${actionId}`),
      actor,
      domain: action.domain,
      tool: name,
      risk: action.risk,
      recordIds: action.record_ids,
      idempotencyKey,
      command: `undo_action:${actionId}`,
      before: action,
      undoPayload,
      conversationId: action.conversation_id,
    });

    const undoResult = runUndo(actionId);
    if (!undoResult.success) {
      markActionFailed(undoAction.id, undoResult.message);
      return resolveToolResult({ status: 'failed', action: action, undoResult }, false, 'review-only');
    }

    const completed = markActionCompleted(undoAction.id, undoAction.command, { undoResult });
    return resolveToolResult({ status: 'completed', action: completed || undoAction, undoResult }, false, 'write');
  }

  throw new Error(`Unknown tool: ${name}`);
}

export function validateCommandEnvelopeLite(raw: unknown): ValidationResult {
  const envelope = isObject(raw) ? (raw as Record<string, unknown>) : {};
  const required = [
    'schema_version',
    'catalog_version',
    'skill_id',
    'skill_version',
    'envelope_id',
    'idempotency_key',
    'status',
    'evidence',
    'commands',
    'confidence',
    'confirmation',
    'warnings',
    'unsupported',
  ];
  const errors: string[] = [];

  if (!isObject(raw)) {
    return {
      valid: false,
      errors: ['envelope must be an object'],
      reviewOnly: true,
      nextStep: 'Share this package into LifeOS; user review is required before writes.',
    };
  }

  for (const key of required) {
    if (!(key in envelope)) {
      errors.push(`missing ${key}`);
    }
  }

  if (envelope.schema_version !== 'wf.ai.command-envelope.v1') {
    errors.push('schema_version must be wf.ai.command-envelope.v1');
  }
  if (envelope.catalog_version !== 'wf.ai.skill-catalog.v1') {
    errors.push('catalog_version must be wf.ai.skill-catalog.v1');
  }
  if (typeof envelope.skill_version !== 'string' || envelope.skill_version !== '1.0.0') {
    errors.push('skill_version must be 1.0.0');
  }
  if (!Array.isArray(envelope.evidence)) {
    errors.push('evidence must be an array');
  }
  if (!Array.isArray(envelope.commands)) {
    errors.push('commands must be an array');
  }
  if (typeof envelope.status !== 'string' || !['commands', 'needs_confirmation', 'needs_clarification', 'unsupported'].includes(envelope.status)) {
    errors.push('status invalid');
  }
  if (typeof envelope.idempotency_key !== 'string' || envelope.idempotency_key.trim().length === 0) {
    errors.push('idempotency_key required');
  }

  return {
    valid: errors.length === 0,
    errors,
    reviewOnly: true,
    nextStep: 'Share this package into LifeOS; user review is required before writes.',
  };
}
