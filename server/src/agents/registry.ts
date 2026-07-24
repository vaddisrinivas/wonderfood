import { loadCatalog } from '@/src/domain/catalog';

export type AgentRoleId = 'orchestrator' | 'retrieval' | 'domain' | 'planner' | 'executor' | 'verifier';

export type AgentManifest = {
  role: AgentRoleId;
  allowedDomains: string[];
  concurrency: number;
  timeoutMs: number;
};

type AgentRuntimeLimits = Omit<AgentManifest, 'role' | 'allowedDomains'>;

const ROLE_LIMITS: Record<AgentRoleId, AgentRuntimeLimits> = {
  orchestrator: { concurrency: 2, timeoutMs: 10000 },
  retrieval: { concurrency: 4, timeoutMs: 3000 },
  domain: { concurrency: 2, timeoutMs: 4000 },
  planner: { concurrency: 2, timeoutMs: 4000 },
  executor: { concurrency: 3, timeoutMs: 4000 },
  verifier: { concurrency: 1, timeoutMs: 3000 },
};

export function buildAgentRegistry(domainIds: readonly string[]): Record<AgentRoleId, AgentManifest> {
  const allowedDomains = [...new Set(domainIds.map((id) => id.trim()).filter(Boolean))];
  if (allowedDomains.length === 0) {
    throw new Error('Agent registry requires at least one configured domain.');
  }

  return Object.fromEntries(
    (Object.keys(ROLE_LIMITS) as AgentRoleId[]).map((role) => [
      role,
      {
        role,
        allowedDomains: [...allowedDomains],
        ...ROLE_LIMITS[role],
      },
    ]),
  ) as Record<AgentRoleId, AgentManifest>;
}

const configuredDomains = loadCatalog().catalog.domains
  .filter((domain) => domain.status !== 'disabled')
  .map((domain) => domain.id);

export const AGENTS = buildAgentRegistry(configuredDomains);

export function agentManifest(role: AgentRoleId) {
  return AGENTS[role];
}
