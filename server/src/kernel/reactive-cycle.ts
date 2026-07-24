import type { AppPackageV2 } from './package';
import { executeQuery, type QueryResult, type QuerySpec } from './query';
import { detectQueryTransitions, type QueryTransitionEvent } from './query-transition';
import { evaluateRules, type OperationProposal } from './rules';
import { applyComputedFieldsToRows, createComputedFieldEvaluationContext } from './computed-fields';

export type ReactiveCycleInput = {
  package: AppPackageV2;
  beforeRows: readonly Record<string, unknown>[];
  afterRows: readonly Record<string, unknown>[];
  event: { kind: 'operation' | 'schedule'; id: string };
  data?: unknown;
  causeId: string;
  depth?: number;
  budget?: {
    maxQueries?: number;
    maxRows?: number;
    maxTransitions?: number;
    maxProposals?: number;
  };
};

export type ReactiveCycleProposal = OperationProposal & {
  id: string;
  eventId: string;
};

export type ReactiveCycleResult = {
  cycleId: string;
  transitions: QueryTransitionEvent[];
  proposals: ReactiveCycleProposal[];
  queryHashes: Record<string, { before: string; after: string }>;
};

const DEFAULT_BUDGET = {
  maxQueries: 128,
  maxRows: 10_000,
  maxTransitions: 256,
  maxProposals: 256,
};

/**
 * Deterministic reactive pass. Queries observe immutable before/after rows;
 * rules can only return operation proposals. This function performs no writes.
 */
export function runReactiveCycle(input: ReactiveCycleInput): ReactiveCycleResult {
  const budget = { ...DEFAULT_BUDGET, ...(input.budget ?? {}) };
  const depth = input.depth ?? 0;
  if (depth > 32) throw new Error('reactive_cycle_depth_exceeded');
  if (input.beforeRows.length > budget.maxRows || input.afterRows.length > budget.maxRows) {
    throw new Error('reactive_cycle_row_budget_exceeded');
  }

  const queryEntries = Object.entries(input.package.queries).sort(([left], [right]) => left.localeCompare(right));
  if (queryEntries.length > budget.maxQueries) throw new Error('reactive_cycle_query_budget_exceeded');
  const computedBudget = {
    maxQueries: budget.maxQueries,
    maxRows: budget.maxRows,
    maxQueryEvaluations: budget.maxQueries * 2,
  };
  const beforeRows = applyComputedFieldsToRows(
    input.beforeRows,
    input.package.computedFields ?? [],
    input.package.queries,
    input.beforeRows,
    createComputedFieldEvaluationContext(computedBudget),
  );
  const afterRows = applyComputedFieldsToRows(
    input.afterRows,
    input.package.computedFields ?? [],
    input.package.queries,
    input.afterRows,
    createComputedFieldEvaluationContext(computedBudget),
  );

  const transitions: QueryTransitionEvent[] = [];
  const queryHashes: ReactiveCycleResult['queryHashes'] = {};
  const transitionResults = new Map<string, {
    before: QueryResult<Record<string, unknown>>;
    after: QueryResult<Record<string, unknown>>;
  }>();

  for (const [queryId, query] of queryEntries) {
    const before = executePackageQuery(beforeRows, queryId, query);
    const after = executePackageQuery(afterRows, queryId, query);
    queryHashes[queryId] = { before: before.resultHash, after: after.resultHash };
    transitionResults.set(queryId, { before, after });
    transitions.push(...detectQueryTransitions(queryId, before, after));
    if (transitions.length > budget.maxTransitions) {
      throw new Error('reactive_cycle_transition_budget_exceeded');
    }
  }

  const rawProposals: Array<OperationProposal & { eventId: string }> = [];
  rawProposals.push(...evaluateRules(input.package.rules, {
    event: input.event,
    data: input.data ?? {},
    packageVersion: input.package.version,
    causeId: input.causeId,
    depth,
  }).map((proposal) => ({ ...proposal, eventId: input.event.id })));
  if (rawProposals.length > budget.maxProposals) {
    throw new Error('reactive_cycle_proposal_budget_exceeded');
  }

  for (const transition of transitions) {
    const results = transitionResults.get(transition.id);
    if (!results) continue;
    const data = {
      event: transition,
      query: {
        id: transition.id,
        before: { total: results.before.total, resultHash: results.before.resultHash },
        after: { total: results.after.total, resultHash: results.after.resultHash },
      },
      input: input.data ?? {},
    };
    rawProposals.push(...evaluateRules(input.package.rules, {
      event: transition,
      data,
      packageVersion: input.package.version,
      causeId: input.causeId,
      depth,
    }).map((proposal) => ({
      ...proposal,
      eventId: `${transition.id}:${transition.transition}`,
    })));
    if (rawProposals.length > budget.maxProposals) {
      throw new Error('reactive_cycle_proposal_budget_exceeded');
    }
  }

  const proposals = dedupeProposals(rawProposals)
    .map((proposal) => ({
      ...proposal,
      id: stableId({
        packageId: input.package.id,
        packageVersion: input.package.version,
        causeId: input.causeId,
        eventId: proposal.eventId,
        ruleId: proposal.ruleId,
        operation: proposal.operation,
      }),
    }))
    .sort((left, right) => left.id.localeCompare(right.id));

  const cycleId = stableId({
    packageId: input.package.id,
    packageVersion: input.package.version,
    causeId: input.causeId,
    event: input.event,
    depth,
    queryHashes,
  });

  return { cycleId, transitions, proposals, queryHashes };
}

function executePackageQuery(
  rows: readonly Record<string, unknown>[],
  queryId: string,
  query: AppPackageV2['queries'][string],
) {
  const stableRows = [...rows].sort((left, right) => rowKey(left).localeCompare(rowKey(right)));
  const scopedRows = query.from === 'records'
    ? stableRows
    : stableRows.filter((row) => row.collection === query.from);
  const spec: QuerySpec<Record<string, unknown>> = {
    ...query,
    provenance: `${queryId}:${query.from}`,
  };
  return executeQuery(scopedRows, spec);
}

function rowKey(row: Record<string, unknown>) {
  return typeof row.id === 'string' ? row.id : stableJson(row);
}

function dedupeProposals<T extends OperationProposal & { eventId: string }>(proposals: T[]): T[] {
  const seen = new Set<string>();
  return proposals.filter((proposal) => {
    const key = stableJson({
      eventId: proposal.eventId,
      ruleId: proposal.ruleId,
      operation: proposal.operation,
      mode: proposal.mode,
      causeId: proposal.causeId,
      packageVersion: proposal.packageVersion,
      depth: proposal.depth,
    });
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function stableId(value: unknown) {
  const encoded = stableJson(value);
  let hash = 2166136261;
  for (let index = 0; index < encoded.length; index += 1) {
    hash ^= encoded.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return `reactive:${(hash >>> 0).toString(16).padStart(8, '0')}`;
}

function stableJson(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value as Record<string, unknown>)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stableJson((value as Record<string, unknown>)[key])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value) ?? 'null';
}
