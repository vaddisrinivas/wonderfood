import { createHash } from 'node:crypto';
import { evaluateExpression } from './expression';
import { normalizeOperationTemplate, operationTemplateName, type OperationTemplate, type RuleSpec } from './package';
import type { ReactiveProposalDryRun, ReactiveProposalPolicyResult } from './reactive-proposal-policy';

export type RuleContext = {
  event: ProposalEvent;
  data: unknown;
  packageVersion: string;
  causeId: string;
  depth: number;
};

export type ProposalEvent = Readonly<{
  kind: 'operation' | 'schedule' | 'query_transition';
  id: string;
  queryId?: string;
  transition?: 'enter' | 'leave' | 'change';
}>;

export type OperationProposal = {
  ruleId: string;
  operation: string;
  operationTemplate: OperationTemplate;
  mode: RuleSpec['mode'];
  causeId: string;
  packageVersion: string;
  depth: number;
  event: ProposalEvent;
};

export type OperationProposalEnvelope = Readonly<{
  schemaVersion: 'wonder.operation-proposal.v1';
  proposalId: string;
  operation: string;
  operationTemplate: OperationTemplate;
  mode: RuleSpec['mode'];
  ruleId: string;
  packageId: string;
  packageVersion: string;
  eventId: string;
  event: ProposalEvent;
  causeId: string;
  depth: number;
  idempotencyKey: string;
  review: {
    required: boolean;
    reason: 'suggest_mode' | 'policy_required' | 'policy_authorized';
    policyId: string;
    policyVersion: string;
  };
  authorization: ReactiveProposalPolicyResult;
  dryRun: ReactiveProposalDryRun;
  evidence: {
    queryId?: string;
    transition?: 'enter' | 'leave' | 'change';
    beforeHash?: string;
    afterHash?: string;
    querySpecHash?: string;
    packageHash?: string;
    evaluatorVersion?: string;
  };
}>;

export function createOperationProposalIdempotencyKey(input: {
  packageId: string;
  packageVersion: string;
  ruleId: string;
  event: ProposalEvent;
  causeId: string;
  operationTemplate: OperationTemplate;
  evidence?: unknown;
}): string {
  return `reactive:${createHash('sha256').update(stableJson({
    schemaVersion: 'wonder.operation-proposal.v1',
    ...input,
  })).digest('hex')}`;
}

export function evaluateRules(rules: readonly RuleSpec[], context: RuleContext): OperationProposal[] {
  if (context.depth > 32) throw new Error('rule_depth_exceeded');
  const proposals: OperationProposal[] = [];
  for (const rule of rules) {
    if (rule.trigger.kind !== context.event.kind) continue;
    if (rule.trigger.kind === 'query_transition' && rule.trigger.query !== (context.event.queryId ?? context.event.id)) continue;
    if (rule.trigger.kind === 'query_transition' && rule.trigger.transition && rule.trigger.transition !== context.event.transition) continue;
    if (rule.when && !Boolean(evaluateExpression(context.data, rule.when))) continue;
    const operationTemplate = normalizeOperationTemplate(rule.effect.operation);
    const max = Math.min(rule.maxRunsPerEvent, 64);
    for (let count = 0; count < max; count += 1) {
      proposals.push({
        ruleId: rule.id,
        operation: operationTemplateName(operationTemplate),
        operationTemplate,
        mode: rule.mode,
        causeId: context.causeId,
        packageVersion: context.packageVersion,
        depth: context.depth,
        event: context.event,
      });
    }
  }
  return proposals;
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
