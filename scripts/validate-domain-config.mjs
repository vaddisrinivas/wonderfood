import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../packages/domain-config');
const read = (path) => JSON.parse(readFileSync(path, 'utf8'));
const walk = (dir) => readdirSync(dir).flatMap((name) => {
  const path = join(dir, name);
  return statSync(path).isDirectory() ? walk(path) : path.endsWith('.json') ? [path] : [];
});

for (const path of walk(root)) read(path);

const catalog = read(join(root, 'domain-catalog.v1.json'));
const active = catalog.domains.find((domain) => domain.id === catalog.active_domain_id);
if (!active?.manifest || !active?.skill) throw new Error('Active domain must reference a manifest and skill.');

const workflowIds = new Set(walk(join(root, 'workflows')).map((path) => read(path).id));
const manifests = new Map();
for (const domain of catalog.domains) {
  if (!domain.manifest || !domain.skill) throw new Error(`Domain ${domain.id} must reference a manifest and skill.`);
  const manifestPath = resolve(root, domain.manifest);
  const skillPath = resolve(root, domain.skill);
  if (!existsSync(manifestPath) || !existsSync(skillPath)) throw new Error(`Domain package is incomplete: ${domain.id}`);

  const manifest = read(manifestPath);
  if (manifest.id !== domain.id) throw new Error(`Manifest id mismatch for ${domain.id}: ${manifest.id}`);
  const collections = new Set(manifest.collections);
  for (const relation of manifest.relations) {
    if (!collections.has(relation.from) || (relation.to !== '*' && !collections.has(relation.to))) {
      throw new Error(`Unknown relation collection in ${domain.id}: ${relation.from} -> ${relation.to}`);
    }
  }
  for (const id of manifest.workflows) {
    if (!workflowIds.has(id)) throw new Error(`Missing workflow for ${domain.id}: ${id}`);
  }
  if (manifest.rich_detail_schema && !existsSync(resolve(root, manifest.rich_detail_schema))) {
    throw new Error(`Missing rich detail schema for ${domain.id}: ${manifest.rich_detail_schema}`);
  }
  manifests.set(domain.id, manifest);
}

const activeManifest = manifests.get(active.id);
if (!activeManifest) throw new Error(`Active domain manifest missing: ${active.id}`);

const registry = read(join(root, 'agents/registry.v1.json'));
const agentIds = new Set();
const operationKinds = new Set(['create', 'update', 'archive', 'restore', 'relate', 'unrelate', 'delete']);
for (const agent of registry.agents) {
  if (agentIds.has(agent.id)) throw new Error(`Duplicate agent id: ${agent.id}`);
  agentIds.add(agent.id);
  for (const schemaRef of [agent.input_schema, agent.output_schema]) {
    if (!existsSync(resolve(root, 'agents', schemaRef))) throw new Error(`Missing agent schema: ${agent.id} -> ${schemaRef}`);
  }
  if (!Array.isArray(agent.capabilities) || agent.capabilities.length === 0) {
    throw new Error(`Agent ${agent.id} must declare capabilities.`);
  }
  for (const capability of agent.capabilities) {
    if (capability.domain !== '*' && !manifests.has(capability.domain)) {
      throw new Error(`Agent ${agent.id} capability references unknown domain: ${capability.domain}`);
    }
    const domainIds = capability.domain === '*' ? Array.from(manifests.keys()) : [capability.domain];
    for (const domainId of domainIds) {
      const manifest = manifests.get(domainId);
      if (!manifest) continue;
      for (const collection of capability.collections ?? []) {
        if (collection !== '*' && !manifest.collections.includes(collection)) {
          throw new Error(`Agent ${agent.id} capability references unknown collection: ${domainId}.${collection}`);
        }
      }
    }
    for (const op of capability.ops ?? []) {
      if (!operationKinds.has(op)) throw new Error(`Agent ${agent.id} capability references unknown operation: ${op}`);
    }
  }
}

console.log(`Domain config valid: ${catalog.domains.length} domains, active=${active.id}, ${activeManifest.collections.length} active collections, ${activeManifest.workflows.length} active workflows, ${registry.agents.length} agents.`);
