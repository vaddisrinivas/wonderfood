import { loadCatalog } from '@/src/domain/catalog';

export type McpToolKind = 'read' | 'write';

type McpPolicy = {
  allowed: boolean;
  risk: 'low' | 'standard' | 'sensitive' | 'irreversible' | 'restricted';
  requiresClarification: boolean;
  clarifyingQuestion?: string;
  reason: string;
  safety: 'read-only' | 'review-only' | 'review-required' | 'blocked';
};

const READ_TOOLS = new Set([
  'wonderfood.status',
  'wonderfood.get_resource',
  'wonderfood.validate_command_envelope',
  'wonderfood.search_records',
  'wonderfood.read_record',
]);

const REVIEW_TOOLS = new Set([
  'wonderfood.propose_app_link',
  'wonderfood.wrap_proposal_package',
]);

const WRITE_TOOLS = new Set([
  'wonderfood.create_record',
  'wonderfood.update_record',
  'wonderfood.archive_record',
  'wonderfood.run_workflow',
  'wonderfood.undo_action',
]);

const BLOCKED_ACTION_RE = /(delete|destroy|credential|private|payment|message|export|purge|billing|permission)/i;

export function getMcpToolKind(toolName: string): McpToolKind {
  if (WRITE_TOOLS.has(toolName) || REVIEW_TOOLS.has(toolName)) {
    return 'write';
  }
  return 'read';
}

export function isMcpToolReadOnly(toolName: string): boolean {
  return getMcpToolKind(toolName) === 'read';
}

export function isMcpToolAllowed(toolName: string): boolean {
  const toolSet = new Set([...Array.from(READ_TOOLS), ...Array.from(REVIEW_TOOLS), ...Array.from(WRITE_TOOLS)]);
  return toolSet.has(toolName);
}

function evaluateCommandRisk(command: string) {
  const normalized = command.toLowerCase();
  if (BLOCKED_ACTION_RE.test(normalized)) {
    return 'restricted';
  }
  if (/(delete|archive|cancel|remove|bill|export|transfer|message)/i.test(command)) {
    return 'standard';
  }
  return 'low';
}

export function evaluateMcpPolicy(input: {
  tool: string;
  domain?: string;
  command?: string;
  actor?: string;
}): McpPolicy {
  const command = (input.command ?? '').trim();
  const actor = (input.actor ?? 'unknown').trim();

  if (!isMcpToolAllowed(input.tool)) {
    return {
      allowed: false,
      risk: 'restricted',
      requiresClarification: false,
      reason: `Tool ${input.tool} is not registered for this MCP server.`,
      safety: 'blocked',
    };
  }

  if (input.domain && input.tool !== 'wonderfood.status') {
    try {
      const catalog = loadCatalog();
      if (!catalog.catalog.domains.some((entry) => entry.id === input.domain)) {
        return {
          allowed: false,
          risk: 'restricted',
          requiresClarification: false,
          reason: `Unknown domain ${input.domain} for MCP policy.`,
          safety: 'blocked',
        };
      }
    } catch {
      return {
        allowed: false,
        risk: 'restricted',
        requiresClarification: false,
        reason: 'Domain policy verification is unavailable.',
        safety: 'blocked',
      };
    }
  }

  if (getMcpToolKind(input.tool) === 'read') {
    return {
      allowed: true,
      risk: 'low',
      requiresClarification: false,
      reason: `Read operation ${input.tool} is allowed for ${actor}.`,
      safety: 'read-only',
    };
  }

  if (input.tool === 'wonderfood.undo_action') {
    if (!command || !command.trim()) {
      return {
        allowed: false,
        risk: 'standard',
        requiresClarification: true,
        reason: 'Undo requires an action id.',
        clarifyingQuestion: 'Please provide an action id to undo.',
        safety: 'review-required',
      };
    }
    return {
      allowed: true,
      risk: 'standard',
      requiresClarification: false,
      reason: `Undo is allowed for ${actor}.`,
      safety: 'review-only',
    };
  }

  const risk = evaluateCommandRisk(command);
  if (risk === 'restricted') {
    return {
      allowed: false,
      risk: 'restricted',
      requiresClarification: false,
      reason: 'Tool command was blocked by policy safeguards.',
      safety: 'blocked',
    };
  }

  if (risk === 'standard' && !command) {
    return {
      allowed: false,
      risk: 'standard',
      requiresClarification: true,
      reason: 'Unsafe write command is missing explicit request text.',
      clarifyingQuestion: 'Can you state the exact record subject and mutation in one sentence?',
      safety: 'review-required',
    };
  }

  return {
    allowed: true,
    risk,
    requiresClarification: false,
    reason: `Write operation ${input.tool} is allowed under MCP policy.`,
    safety: risk === 'low' ? 'review-only' : 'review-required',
  };
}

export function isReviewOnlyTool(toolName: string): boolean {
  return REVIEW_TOOLS.has(toolName);
}
