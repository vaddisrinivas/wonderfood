import type { OperationTemplate } from './package';

export const REACTIVE_PROPOSAL_POLICY_ID = 'wonder.reactive-proposal-policy' as const;
export const REACTIVE_PROPOSAL_POLICY_VERSION = 'v1' as const;

export type ReactiveProposalRisk = 'low' | 'standard' | 'sensitive' | 'restricted';

export type ReactiveProposalPolicyResult = Readonly<{
  policyId: typeof REACTIVE_PROPOSAL_POLICY_ID;
  policyVersion: typeof REACTIVE_PROPOSAL_POLICY_VERSION;
  allowed: boolean;
  risk: ReactiveProposalRisk;
  reviewRequired: boolean;
  requiredCapability: string;
  capabilityPresent: boolean;
  reason: string;
}>;

export type ReactiveProposalDryRun = Readonly<{
  ok: boolean;
  effect: 'queue_review_action';
  executable: boolean;
  reason: string;
}>;

export function evaluateReactiveProposalPolicy(input: {
  operationTemplate: OperationTemplate;
  requestedMode: 'suggest' | 'automatic';
  capabilities: readonly string[];
}): ReactiveProposalPolicyResult {
  const risk = operationRisk(input.operationTemplate);
  const requiredCapability = requiredCapabilityFor(input.operationTemplate, input.requestedMode);
  const capabilityPresent = input.capabilities.includes(requiredCapability);
  const automaticAllowed = input.requestedMode === 'automatic'
    && capabilityPresent
    && risk === 'low'
    && input.operationTemplate.kind !== 'custom';
  const blocked = risk === 'restricted';
  return {
    policyId: REACTIVE_PROPOSAL_POLICY_ID,
    policyVersion: REACTIVE_PROPOSAL_POLICY_VERSION,
    allowed: !blocked,
    risk,
    reviewRequired: !automaticAllowed,
    requiredCapability,
    capabilityPresent,
    reason: blocked
      ? 'operation_template_restricted'
      : automaticAllowed
        ? 'automatic_policy_authorized'
        : input.requestedMode === 'automatic'
          ? 'automatic_requires_policy_or_review'
          : 'suggest_mode_requires_review',
  };
}

export function dryRunReactiveProposal(input: {
  operationTemplate: OperationTemplate;
  policy: ReactiveProposalPolicyResult;
}): ReactiveProposalDryRun {
  return {
    ok: input.policy.allowed,
    effect: 'queue_review_action',
    executable: input.policy.allowed && input.operationTemplate.kind !== 'custom',
    reason: input.policy.allowed ? 'proposal_can_be_queued' : input.policy.reason,
  };
}

function requiredCapabilityFor(operationTemplate: OperationTemplate, requestedMode: 'suggest' | 'automatic'): string {
  return requestedMode === 'automatic'
    ? `reactive:auto:${operationTemplate.kind}`
    : `reactive:propose:${operationTemplate.kind}`;
}

function operationRisk(operationTemplate: OperationTemplate): ReactiveProposalRisk {
  if (operationTemplate.kind === 'custom') return 'standard';
  if (operationTemplate.kind === 'create_record') return 'low';
  if (operationTemplate.kind === 'update_record') return 'standard';
  if (operationTemplate.kind === 'archive_record' || operationTemplate.kind === 'restore_record') return 'sensitive';
  return 'restricted';
}
