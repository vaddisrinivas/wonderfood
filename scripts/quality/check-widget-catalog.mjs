import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const root = process.cwd();
const contractPath = path.join(root, 'packages/shared/contracts/package.ts');
const schemaPath = path.join(root, 'packages/domain-config/schemas/domain.v1.schema.json');
const surfacePath = path.join(root, 'src/presentation/json-render-surface.tsx');
const widgetsPath = path.join(root, 'src/presentation/json-render-widgets.tsx');
const foodPath = path.join(root, 'packages/domain-config/domains/food.v1.json');
const evidencePath = path.join(root, 'app/build/evidence/widget-catalog.json');

const contract = fs.readFileSync(contractPath, 'utf8');
const schema = JSON.parse(fs.readFileSync(schemaPath, 'utf8'));
const surface = fs.readFileSync(surfacePath, 'utf8');
const widgetsSource = fs.readFileSync(widgetsPath, 'utf8');
const food = JSON.parse(fs.readFileSync(foodPath, 'utf8'));

const contractWidgets = extractContractWidgets(contract);
const schemaWidgets = new Set(schema.$defs.package_ui_component.properties.widget.enum);
const surfaceWidgets = extractSurfaceWidgetMap(surface);
const registeredComponents = extractRegisteredComponents(widgetsSource);
const catalogLabels = extractFoodCatalogLabels(food);

const problems = [];
const allWidgets = new Set([...contractWidgets, ...schemaWidgets, ...surfaceWidgets.keys()]);

for (const widget of allWidgets) {
  if (!contractWidgets.has(widget)) problems.push(`${widget}: missing from AppPackage TypeScript contract`);
  if (!schemaWidgets.has(widget)) problems.push(`${widget}: missing from domain JSON Schema`);
  const component = surfaceWidgets.get(widget);
  if (!component) problems.push(`${widget}: missing from JSON Render surface widget map`);
  if (component && !registeredComponents.has(component)) problems.push(`${widget}: mapped to ${component}, but component is not registered`);
  if (!catalogLabels.has(labelize(widget))) problems.push(`${widget}: missing from food config widget catalog`);
}

for (const component of ['record list', 'metric', 'action', 'text card', 'package editor']) {
  if (!catalogLabels.has(component)) problems.push(`config catalog: missing ${component}`);
}

if (problems.length) {
  console.error('Widget catalog check failed:');
  for (const problem of problems) console.error(`- ${problem}`);
  process.exit(1);
}

fs.mkdirSync(path.dirname(evidencePath), { recursive: true });
fs.writeFileSync(evidencePath, `${JSON.stringify({
  status: 'PASS',
  commit: currentCommit(),
  checkedAt: new Date().toISOString(),
  widgets: [...allWidgets].sort(),
  registryComponents: [...registeredComponents].sort(),
}, null, 2)}\n`);

console.log(`Widget catalog check: PASS (${allWidgets.size} widgets, evidence: ${path.relative(root, evidencePath)})`);

function extractContractWidgets(source) {
  const widgetBlock = source.match(/widget\?:\s*([\s\S]*?);/);
  if (!widgetBlock) throw new Error('Unable to find AppPackage widget union.');
  return new Set([...widgetBlock[1].matchAll(/'([^']+)'/g)].map((match) => match[1]));
}

function extractSurfaceWidgetMap(source) {
  const mapBlock = source.match(/const typeByWidget:\s*Record<string,\s*string>\s*=\s*{([\s\S]*?)};/);
  if (!mapBlock) throw new Error('Unable to find JSON Render widget map.');
  return new Map([...mapBlock[1].matchAll(/(\w+):\s*'([^']+)'/g)].map((match) => [match[1], match[2]]));
}

function extractRegisteredComponents(source) {
  const registryBlock = source.match(/export const JSON_RENDER_WIDGET_REGISTRY:[\s\S]*?=\s*{([\s\S]*?)};/);
  if (!registryBlock) throw new Error('Unable to find JSON Render widget registry.');
  return new Set([...registryBlock[1].matchAll(/^\s*(\w+),/gm)].map((match) => match[1]));
}

function extractFoodCatalogLabels(document) {
  const config = document.ui?.screens?.config?.components ?? [];
  const catalogs = config.filter((component) => component.kind === 'widget' && component.widget === 'widgetCatalog');
  return new Set(catalogs.flatMap((component) => component.props?.items ?? []).map((item) => String(item.title ?? '').trim().toLowerCase()).filter(Boolean));
}

function labelize(widget) {
  return widget.replace(/([a-z0-9])([A-Z])/g, '$1 $2').toLowerCase();
}

function currentCommit() {
  try {
    return execFileSync('git', ['rev-parse', '--short', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim();
  } catch {
    return 'unknown';
  }
}
