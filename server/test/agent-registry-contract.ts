import { loadCatalog } from '../../src/domain/catalog';
import {
  AGENTS,
  buildAgentRegistry,
  type AgentRoleId,
} from '../src/agents/registry';
import { executeAgentRole } from '../src/agents/orchestrator';

function ensure(condition: boolean, message: string) {
  if (!condition) throw new Error(message);
}

const roles: AgentRoleId[] = ['orchestrator', 'retrieval', 'domain', 'planner', 'executor', 'verifier'];
const configuredDomains = loadCatalog().catalog.domains
  .filter((domain) => domain.status !== 'disabled')
  .map((domain) => domain.id);

for (const role of roles) {
  ensure(
    configuredDomains.every((domain) => AGENTS[role].allowedDomains.includes(domain)),
    `Expected ${role} to allow every configured domain.`,
  );
  ensure(
    new Set(AGENTS[role].allowedDomains).size === AGENTS[role].allowedDomains.length,
    `Expected ${role} domain list to be deduplicated.`,
  );
}

const custom = buildAgentRegistry(['worlds', 'decisions', 'worlds', ' ']);
for (const role of roles) {
  ensure(
    custom[role].allowedDomains.join(',') === 'worlds,decisions',
    `Expected ${role} to use supplied domains without Food hardcoding.`,
  );
}

const previewDomain = configuredDomains.find((domain) => domain !== 'food');
ensure(Boolean(previewDomain), 'Expected at least one configured non-Food domain.');
const allowed = await executeAgentRole({
  role: 'retrieval',
  domain: previewDomain!,
  payload: { query: 'contract probe' },
});
ensure(allowed.status === 'ok', 'Expected configured non-Food domain to pass role authorization.');

const blocked = await executeAgentRole({
  role: 'retrieval',
  domain: 'not-configured',
  payload: { query: 'contract probe' },
});
ensure(blocked.status === 'blocked', 'Expected unknown domain to remain blocked.');

let emptyRejected = false;
try {
  buildAgentRegistry([]);
} catch {
  emptyRejected = true;
}
ensure(emptyRejected, 'Expected empty configured-domain set to be rejected.');

console.log('PASS server/test/agent-registry-contract.ts');
