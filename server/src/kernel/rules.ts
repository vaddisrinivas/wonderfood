import { evaluateExpression } from './expression';
import { RuleSpec } from './package';

export type RuleContext = {
  event: { kind: 'operation' | 'schedule' | 'query_transition'; id: string; transition?: 'enter' | 'leave' | 'change' };
  data: unknown;
  packageVersion: string;
  causeId: string;
  depth: number;
};

export type OperationProposal = {
  ruleId: string;
  operation: string;
  mode: RuleSpec['mode'];
  causeId: string;
  packageVersion: string;
  depth: number;
};

export type OperationProposalEnvelope = Readonly<{
  schemaVersion: 'wonder.operation-proposal.v1';
  proposalId: string;
  operation: string;
  mode: RuleSpec['mode'];
  ruleId: string;
  packageId: string;
  packageVersion: string;
  eventId: string;
  causeId: string;
  depth: number;
  idempotencyKey: string;
  review: {
    required: boolean;
    reason: 'suggest_mode' | 'automatic_mode';
  };
  evidence: {
    queryId?: string;
    transition?: 'enter' | 'leave' | 'change';
    beforeHash?: string;
    afterHash?: string;
  };
}>;

export function evaluateRules(rules: readonly RuleSpec[], context: RuleContext): OperationProposal[] {
  if (context.depth > 32) throw new Error('rule_depth_exceeded');
  const proposals: OperationProposal[] = [];
  for (const rule of rules) {
    if (rule.trigger.kind !== context.event.kind) continue;
    if (rule.trigger.kind === 'query_transition' && rule.trigger.query !== context.event.id) continue;
    if (rule.trigger.kind === 'query_transition' && rule.trigger.transition && rule.trigger.transition !== context.event.transition) continue;
    if (rule.when && !Boolean(evaluateExpression(context.data, rule.when))) continue;
    const max = Math.min(rule.maxRunsPerEvent, 64);
    for (let count = 0; count < max; count += 1) {
      proposals.push({
        ruleId: rule.id,
        operation: rule.effect.operation,
        mode: rule.mode,
        causeId: context.causeId,
        packageVersion: context.packageVersion,
        depth: context.depth,
      });
    }
  }
  return proposals;
}
