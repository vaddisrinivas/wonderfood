import type { OperationCommitEvent } from './operation-observer';
import type { ReactiveCycleProposal, ReactiveCycleResult } from './reactive-cycle';
import { createOperationProposalIdempotencyKey } from './rules';

export const REACTIVE_OUTBOX_SCHEMA_VERSION = 'wonder.reactive-outbox.v1' as const;

export type ReactiveOutboxStatus = 'pending' | 'running' | 'acked' | 'failed';

export type ReactiveOutboxItem = Readonly<{
  proposalId: string;
  cycleId: string;
  eventId: string;
  actionId: string;
  operationId: string;
  causeId: string;
  domain: string;
  status: ReactiveOutboxStatus;
  attempts: number;
  createdAt: string;
  updatedAt: string;
  nextAttemptAt: string;
  proposal: ReactiveCycleProposal;
  lastError?: string;
}>;

export type ReactiveOutboxStore = Readonly<{
  schemaVersion: typeof REACTIVE_OUTBOX_SCHEMA_VERSION;
  items: Readonly<Record<string, ReactiveOutboxItem>>;
}>;

export type ReactiveOutboxExecutionResult = Readonly<
  | { ok: true }
  | { ok: false; error: string }
>;

export type ReactiveOutboxDrainResult = Readonly<{
  store: ReactiveOutboxStore;
  attempted: readonly string[];
  acked: readonly string[];
  failed: readonly { proposalId: string; error: string }[];
}>;

export function createReactiveOutboxStore(): ReactiveOutboxStore {
  return immutable({
    schemaVersion: REACTIVE_OUTBOX_SCHEMA_VERSION,
    items: {},
  });
}

export function enqueueReactiveProposals(
  store: ReactiveOutboxStore,
  input: {
    cycle: ReactiveCycleResult;
    event: OperationCommitEvent;
    proposalIds: readonly string[];
    now?: string;
  },
): ReactiveOutboxStore {
  const now = input.now ?? new Date().toISOString();
  const proposalIds = new Set(input.proposalIds);
  const proposals = new Map(input.cycle.proposals.map((proposal) => [proposal.id, proposal]));
  const items: Record<string, ReactiveOutboxItem> = { ...store.items };

  for (const proposalId of [...proposalIds].sort()) {
    if (items[proposalId]) continue;
    const proposal = proposals.get(proposalId);
    if (!proposal) throw new Error(`Reactive proposal ${proposalId} missing from cycle ${input.cycle.cycleId}.`);
    items[proposalId] = immutable({
      proposalId,
      cycleId: input.cycle.cycleId,
      eventId: proposal.eventId,
      actionId: input.event.actionId,
      operationId: input.event.operationId,
      causeId: input.event.causeId,
      domain: input.event.domain,
      status: 'pending',
      attempts: 0,
      createdAt: now,
      updatedAt: now,
      nextAttemptAt: now,
      proposal,
    });
  }

  return immutable({
    schemaVersion: REACTIVE_OUTBOX_SCHEMA_VERSION,
    items: sortRecord(items),
  });
}

export function listRunnableReactiveOutboxItems(store: ReactiveOutboxStore, now = new Date().toISOString()): ReactiveOutboxItem[] {
  const nowMs = Date.parse(now);
  return Object.values(store.items)
    .filter((item) => item.status === 'pending' && Date.parse(item.nextAttemptAt) <= nowMs)
    .sort((left, right) => left.createdAt.localeCompare(right.createdAt) || left.proposalId.localeCompare(right.proposalId));
}

export function markReactiveOutboxRunning(store: ReactiveOutboxStore, proposalId: string, now = new Date().toISOString()): ReactiveOutboxStore {
  const item = requiredItem(store, proposalId);
  return updateItem(store, proposalId, {
    ...item,
    status: 'running',
    updatedAt: now,
  });
}

export function markReactiveOutboxAcked(store: ReactiveOutboxStore, proposalId: string, now = new Date().toISOString()): ReactiveOutboxStore {
  const item = requiredItem(store, proposalId);
  return updateItem(store, proposalId, {
    ...item,
    status: 'acked',
    updatedAt: now,
  });
}

export function markReactiveOutboxFailed(
  store: ReactiveOutboxStore,
  proposalId: string,
  input: { error: string; now?: string; retryDelayMs?: number },
): ReactiveOutboxStore {
  const now = input.now ?? new Date().toISOString();
  const retryDelayMs = input.retryDelayMs ?? 60_000;
  const item = requiredItem(store, proposalId);
  return updateItem(store, proposalId, {
    ...item,
    status: 'pending',
    attempts: item.attempts + 1,
    updatedAt: now,
    nextAttemptAt: new Date(Date.parse(now) + retryDelayMs).toISOString(),
    lastError: input.error,
  });
}

export async function drainReactiveOutbox(input: {
  store: ReactiveOutboxStore;
  executeProposal: (item: ReactiveOutboxItem) => Promise<ReactiveOutboxExecutionResult> | ReactiveOutboxExecutionResult;
  now?: string;
  maxItems?: number;
  retryDelayMs?: number;
  onStoreChange?: (store: ReactiveOutboxStore) => void;
}): Promise<ReactiveOutboxDrainResult> {
  const now = input.now ?? new Date().toISOString();
  const maxItems = input.maxItems ?? 16;
  let store = input.store;
  const attempted: string[] = [];
  const acked: string[] = [];
  const failed: { proposalId: string; error: string }[] = [];

  for (const item of listRunnableReactiveOutboxItems(store, now).slice(0, maxItems)) {
    attempted.push(item.proposalId);
    store = markReactiveOutboxRunning(store, item.proposalId, now);
    input.onStoreChange?.(store);
    try {
      const result = await input.executeProposal(store.items[item.proposalId]);
      if (result.ok) {
        store = markReactiveOutboxAcked(store, item.proposalId, new Date().toISOString());
        acked.push(item.proposalId);
      } else {
        store = markReactiveOutboxFailed(store, item.proposalId, {
          error: result.error,
          retryDelayMs: input.retryDelayMs,
        });
        failed.push({ proposalId: item.proposalId, error: result.error });
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      store = markReactiveOutboxFailed(store, item.proposalId, {
        error: message,
        retryDelayMs: input.retryDelayMs,
      });
      failed.push({ proposalId: item.proposalId, error: message });
    }
    input.onStoreChange?.(store);
  }

  return immutable({ store, attempted, acked, failed });
}

export function serializeReactiveOutboxStore(store: ReactiveOutboxStore): string {
  return JSON.stringify(store);
}

export function parseReactiveOutboxStore(serialized: string): ReactiveOutboxStore {
  let value: unknown;
  try {
    value = JSON.parse(serialized);
  } catch {
    throw new Error('Reactive outbox store is not valid JSON.');
  }
  if (!isObject(value) || value.schemaVersion !== REACTIVE_OUTBOX_SCHEMA_VERSION || !isObject(value.items)) {
    throw new Error('Reactive outbox store has an unsupported schema version.');
  }
  const items: Record<string, ReactiveOutboxItem> = {};
  for (const [proposalId, rawItem] of Object.entries(value.items)) {
    if (!isObject(rawItem) || rawItem.proposalId !== proposalId) {
      throw new Error(`Reactive outbox item ${proposalId} is invalid.`);
    }
    const item = rawItem as ReactiveOutboxItem;
    if (!isStatus(item.status) || !Number.isInteger(item.attempts) || item.attempts < 0) {
      throw new Error(`Reactive outbox item ${proposalId} has invalid retry state.`);
    }
    for (const field of ['cycleId', 'eventId', 'actionId', 'operationId', 'causeId', 'domain', 'createdAt', 'updatedAt', 'nextAttemptAt']) {
      if (typeof item[field as keyof ReactiveOutboxItem] !== 'string' || !String(item[field as keyof ReactiveOutboxItem]).trim()) {
        throw new Error(`Reactive outbox item ${proposalId} is missing ${field}.`);
      }
    }
    if (!isObject(item.proposal) || item.proposal.id !== proposalId) {
      throw new Error(`Reactive outbox item ${proposalId} is missing its proposal.`);
    }
    const envelope = item.proposal.envelope;
    if (!isObject(envelope) || envelope.schemaVersion !== 'wonder.operation-proposal.v1' || envelope.proposalId !== proposalId) {
      throw new Error(`Reactive outbox item ${proposalId} is missing its proposal envelope.`);
    }
    if (!isProposalEvent(item.proposal.event) || !isProposalEvent(envelope.event)) {
      throw new Error(`Reactive outbox item ${proposalId} has an invalid proposal event.`);
    }
    if (!isOperationTemplate(item.proposal.operationTemplate) || !isOperationTemplate(envelope.operationTemplate)) {
      throw new Error(`Reactive outbox item ${proposalId} has an invalid operation template.`);
    }
    if (
      envelope.operation !== item.proposal.operation
      || stableJson(envelope.operationTemplate) !== stableJson(item.proposal.operationTemplate)
      || envelope.ruleId !== item.proposal.ruleId
      || envelope.eventId !== item.proposal.eventId
      || envelope.eventId !== item.eventId
      || stableJson(envelope.event) !== stableJson(item.proposal.event)
      || envelope.causeId !== item.proposal.causeId
      || envelope.causeId !== item.causeId
      || envelope.packageVersion !== item.proposal.packageVersion
      || typeof envelope.packageId !== 'string'
      || !envelope.packageId.trim()
      || envelope.mode !== item.proposal.mode
      || envelope.depth !== item.proposal.depth
      || envelope.review?.required !== true
      || (envelope.review.reason !== 'suggest_mode' && envelope.review.reason !== 'policy_required')
    ) {
      throw new Error(`Reactive outbox item ${proposalId} has an inconsistent proposal envelope.`);
    }
    validateEnvelopeEvidence(proposalId, envelope);
    const expectedKey = createOperationProposalIdempotencyKey({
      packageId: envelope.packageId,
      packageVersion: envelope.packageVersion,
      ruleId: envelope.ruleId,
      event: envelope.event,
      causeId: envelope.causeId,
      operationTemplate: envelope.operationTemplate,
      evidence: envelope.evidence.beforeHash && envelope.evidence.afterHash
        ? {
          queryId: envelope.evidence.queryId,
          transition: envelope.evidence.transition,
          beforeHash: envelope.evidence.beforeHash,
          afterHash: envelope.evidence.afterHash,
          querySpecHash: envelope.evidence.querySpecHash,
          packageHash: envelope.evidence.packageHash,
          evaluatorVersion: envelope.evidence.evaluatorVersion,
        }
        : undefined,
    });
    if (envelope.idempotencyKey !== expectedKey) {
      throw new Error(`Reactive outbox item ${proposalId} has an invalid idempotency key.`);
    }
    items[proposalId] = immutable({ ...item });
  }
  return immutable({
    schemaVersion: REACTIVE_OUTBOX_SCHEMA_VERSION,
    items: sortRecord(items),
  });
}

function requiredItem(store: ReactiveOutboxStore, proposalId: string): ReactiveOutboxItem {
  const item = store.items[proposalId];
  if (!item) throw new Error(`Reactive outbox item ${proposalId} was not found.`);
  return item;
}

function updateItem(store: ReactiveOutboxStore, proposalId: string, item: ReactiveOutboxItem): ReactiveOutboxStore {
  return immutable({
    schemaVersion: REACTIVE_OUTBOX_SCHEMA_VERSION,
    items: sortRecord({
      ...store.items,
      [proposalId]: immutable(item),
    }),
  });
}

function isStatus(value: unknown): value is ReactiveOutboxStatus {
  return value === 'pending' || value === 'running' || value === 'acked' || value === 'failed';
}

function isObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function isProposalEvent(value: unknown): value is ReactiveCycleProposal['event'] {
  if (!isObject(value) || typeof value.id !== 'string' || !value.id.trim()) return false;
  if (value.kind !== 'operation' && value.kind !== 'schedule' && value.kind !== 'query_transition') return false;
  if (value.queryId !== undefined && (typeof value.queryId !== 'string' || !value.queryId.trim())) return false;
  if (value.transition !== undefined && value.transition !== 'enter' && value.transition !== 'leave' && value.transition !== 'change') return false;
  if (value.kind === 'query_transition' && (!value.queryId || !value.transition)) return false;
  if (value.kind !== 'query_transition' && (value.queryId !== undefined || value.transition !== undefined)) return false;
  return true;
}

function isOperationTemplate(value: unknown): value is ReactiveCycleProposal['operationTemplate'] {
  if (!isObject(value) || typeof value.kind !== 'string') return false;
  if (value.kind === 'custom') return typeof value.tool === 'string' && Boolean(value.tool.trim());
  if (value.kind === 'create_record') {
    return typeof value.collection === 'string' && Boolean(value.collection.trim())
      && (value.recordId === undefined || (typeof value.recordId === 'string' && Boolean(value.recordId.trim())))
      && (value.properties === undefined || isObject(value.properties));
  }
  if (value.kind === 'update_record') {
    return typeof value.recordId === 'string' && Boolean(value.recordId.trim())
      && isObject(value.changes)
      && (value.collection === undefined || (typeof value.collection === 'string' && Boolean(value.collection.trim())))
      && (value.expectedRevision === undefined || (typeof value.expectedRevision === 'number' && Number.isInteger(value.expectedRevision) && value.expectedRevision >= 0));
  }
  if (value.kind === 'archive_record' || value.kind === 'restore_record') {
    return typeof value.recordId === 'string' && Boolean(value.recordId.trim())
      && (value.collection === undefined || (typeof value.collection === 'string' && Boolean(value.collection.trim())))
      && (value.expectedRevision === undefined || (typeof value.expectedRevision === 'number' && Number.isInteger(value.expectedRevision) && value.expectedRevision >= 0));
  }
  return false;
}

function validateEnvelopeEvidence(proposalId: string, envelope: ReactiveCycleProposal['envelope']): void {
  if (!isObject(envelope.evidence)) throw new Error(`Reactive outbox item ${proposalId} has invalid evidence.`);
  if (envelope.event.kind === 'query_transition') {
    if (envelope.evidence.queryId !== envelope.event.queryId || envelope.evidence.transition !== envelope.event.transition) {
      throw new Error(`Reactive outbox item ${proposalId} has inconsistent query evidence.`);
    }
  } else if (envelope.evidence.queryId !== undefined || envelope.evidence.transition !== undefined) {
    throw new Error(`Reactive outbox item ${proposalId} has unexpected query evidence.`);
  }
  for (const field of ['beforeHash', 'afterHash']) {
    const value = envelope.evidence[field as 'beforeHash' | 'afterHash'];
    if (value !== undefined && (typeof value !== 'string' || !value.trim())) {
      throw new Error(`Reactive outbox item ${proposalId} has invalid evidence hash.`);
    }
  }
  if ((envelope.evidence.beforeHash === undefined) !== (envelope.evidence.afterHash === undefined)) {
    throw new Error(`Reactive outbox item ${proposalId} has incomplete evidence hashes.`);
  }
  const hasResultHashes = envelope.evidence.beforeHash !== undefined && envelope.evidence.afterHash !== undefined;
  for (const field of ['querySpecHash', 'packageHash', 'evaluatorVersion']) {
    const value = envelope.evidence[field as 'querySpecHash' | 'packageHash' | 'evaluatorVersion'];
    if (hasResultHashes && (typeof value !== 'string' || !value.trim())) {
      throw new Error(`Reactive outbox item ${proposalId} has incomplete evidence context.`);
    }
    if (!hasResultHashes && value !== undefined) {
      throw new Error(`Reactive outbox item ${proposalId} has unexpected evidence context.`);
    }
  }
}

function stableJson(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value as Record<string, unknown>)
      .filter((key) => (value as Record<string, unknown>)[key] !== undefined)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stableJson((value as Record<string, unknown>)[key])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value) ?? 'null';
}

function sortRecord<T>(value: Record<string, T>): Record<string, T> {
  return Object.fromEntries(Object.entries(value).sort(([left], [right]) => left.localeCompare(right)));
}

function immutable<T>(value: T): T {
  if (!value || typeof value !== 'object' || Object.isFrozen(value)) return value;
  for (const child of Object.values(value as Record<string, unknown>)) immutable(child);
  return Object.freeze(value);
}
