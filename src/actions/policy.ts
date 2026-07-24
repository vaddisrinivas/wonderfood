export type ActionRisk = 'low' | 'standard' | 'sensitive' | 'irreversible' | 'restricted';

export type PolicyDecision = {
  allowed: boolean;
  requiresClarification: boolean;
  clarifyingQuestion?: string;
  reason: string;
  risk: ActionRisk;
  confidence: 'high' | 'medium' | 'low';
};

const WRITE_VERBS_RE = /\b(add|create|archive|update|delete|remove|order|buy|purchase)\b/i;
const FOOD_SUBJECT_RE = /\b(meal|recipe|shopping|item|inventory|record)\b/i;
const BLOCKED_TOOL_RE = /\b(delete|destroy|credential|private|payment|message|message.send|billing|export|purge)\b/i;

export function evaluateCommandPolicy(input: { domain: string; tool: string; command: string; actor?: string }) {
  const command = (input.command || '').trim();
  const isWrite = WRITE_VERBS_RE.test(command);
  const hasAmbiguousWrite = isWrite && !FOOD_SUBJECT_RE.test(command);

  if (BLOCKED_TOOL_RE.test(input.tool) || BLOCKED_TOOL_RE.test(command)) {
    return {
      allowed: false,
      requiresClarification: false,
      reason: 'Tool or command is restricted for safety policy.',
      risk: 'restricted' as ActionRisk,
      confidence: 'high' as const,
    } satisfies PolicyDecision;
  }

  if (hasAmbiguousWrite) {
    return {
      allowed: false,
      requiresClarification: true,
      reason: 'Write command missing target subject for safe policy.',
      clarifyingQuestion: 'Which exact Food record should I act on? (meal, recipe, shopping list, inventory item, or record id)',
      risk: 'standard' as ActionRisk,
      confidence: 'medium' as const,
    } satisfies PolicyDecision;
  }

  const isRiskyWrite = /(destroy|delete|archive|purchase|buy|update|remove|message)/i.test(command);

  return {
    allowed: true,
    requiresClarification: false,
    reason: `${input.domain} command accepted under domain policy.`,
    risk: (isRiskyWrite ? 'standard' : 'low') as ActionRisk,
    confidence: 'high' as const,
  } satisfies PolicyDecision;
}
