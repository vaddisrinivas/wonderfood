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

const manifestPath = resolve(root, active.manifest);
const skillPath = resolve(root, active.skill);
if (!existsSync(manifestPath) || !existsSync(skillPath)) throw new Error('Active domain package is incomplete.');

const manifest = read(manifestPath);
const collections = new Set(manifest.collections);
for (const relation of manifest.relations) {
  if (!collections.has(relation.from) || (relation.to !== '*' && !collections.has(relation.to))) throw new Error(`Unknown relation collection: ${relation.from} -> ${relation.to}`);
}

const workflowIds = new Set(walk(join(root, 'workflows')).map((path) => read(path).id));
for (const id of manifest.workflows) if (!workflowIds.has(id)) throw new Error(`Missing workflow: ${id}`);

console.log(`Domain config valid: ${catalog.domains.length} domains, ${manifest.collections.length} Food collections, ${manifest.workflows.length} workflows.`);
