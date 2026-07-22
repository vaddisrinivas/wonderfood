export type AgentRoleId = 'orchestrator' | 'retrieval' | 'domain' | 'planner' | 'executor' | 'verifier';

export type AgentManifest = {
  role: AgentRoleId;
  allowedDomains: string[];
  concurrency: number;
  timeoutMs: number;
};

export const AGENTS: Record<AgentRoleId, AgentManifest> = {
  orchestrator: { role: 'orchestrator', allowedDomains: ['food'], concurrency: 2, timeoutMs: 10000 },
  retrieval: { role: 'retrieval', allowedDomains: ['food'], concurrency: 4, timeoutMs: 3000 },
  domain: { role: 'domain', allowedDomains: ['food'], concurrency: 2, timeoutMs: 4000 },
  planner: { role: 'planner', allowedDomains: ['food'], concurrency: 2, timeoutMs: 4000 },
  executor: { role: 'executor', allowedDomains: ['food'], concurrency: 3, timeoutMs: 4000 },
  verifier: { role: 'verifier', allowedDomains: ['food'], concurrency: 1, timeoutMs: 3000 },
};

export function agentManifest(role: AgentRoleId) {
  return AGENTS[role];
}
