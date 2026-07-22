import { AGENTS, AgentRoleId } from './registry';

export async function executeAgentRole(input: {
  role: AgentRoleId;
  domain: string;
  payload: Record<string, unknown>;
}) {
  const manifest = AGENTS[input.role];
  if (!manifest.allowedDomains.includes(input.domain)) {
    return {
      role: input.role,
      status: 'blocked',
      reason: `Domain ${input.domain} not allowed`,
    };
  }

  return {
    role: input.role,
    status: 'ok',
    payload: input.payload,
    concurrency: manifest.concurrency,
    timeoutMs: manifest.timeoutMs,
  };
}
