#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = process.cwd();
const read = (path) => JSON.parse(readFileSync(resolve(root, path), 'utf8'));

const food = read('packages/domain-config/domains/food.v1.json');
const template = read('packages/domain-config/templates/lifeos-data-plane-template.v1.json');

function fail(message) {
  console.error(`Data-plane template invalid: ${message}`);
  process.exit(1);
}

if (template.schema_version !== 'lifeos.data_plane_template.v1') fail('schema_version mismatch');
if (template.sync_contract?.webhooks_required !== false) fail('webhooks must be optional');
if (template.sync_contract?.hosted_server_required !== false) fail('hosted server must not be required');
if (template.sync_contract?.source_snapshots_required !== true) fail('source snapshots must be required');
if (template.notion?.max_navigation_depth > 3) fail('Notion navigation exceeds depth 3');

const foodCollections = new Set(food.collections);
const coverage = template.collection_coverage ?? {};
for (const collection of food.collections) {
  const row = coverage[collection];
  if (!row) fail(`missing collection coverage: ${collection}`);
  if (!Array.isArray(row.notion) || row.notion.length === 0) fail(`missing Notion mapping: ${collection}`);
  if (!Array.isArray(row.sheets) || row.sheets.length === 0) fail(`missing Sheets mapping: ${collection}`);
}

for (const collection of Object.keys(coverage)) {
  if (!foodCollections.has(collection)) fail(`coverage references unknown Food collection: ${collection}`);
}

const notionDatabaseNames = new Set((template.notion?.databases ?? []).map((database) => database.name));
const sheetTabNames = new Set((template.sheets?.tabs ?? []).map((tab) => tab.name));

for (const [collection, row] of Object.entries(coverage)) {
  for (const name of row.notion) {
    if (!notionDatabaseNames.has(name)) fail(`Notion mapping for ${collection} points to missing database: ${name}`);
  }
  for (const name of row.sheets) {
    if (!sheetTabNames.has(name)) fail(`Sheets mapping for ${collection} points to missing tab: ${name}`);
  }
}

const wildcardNotion = (template.notion?.databases ?? []).filter((database) => database.collections?.includes('*'));
if (wildcardNotion.length !== 1) fail('expected one canonical wildcard Notion database');

const wildcardSheets = (template.sheets?.tabs ?? []).filter((tab) => tab.collections?.includes('*'));
if (wildcardSheets.length < 2) fail('expected canonical Records and Schema Sheets tabs');

const requiredCanonicalColumns = ['id', 'domain', 'collection', 'title', 'status', 'food_detail_json', 'relations_json'];
const recordsTab = (template.sheets?.tabs ?? []).find((tab) => tab.name === 'Records');
for (const column of requiredCanonicalColumns) {
  if (!recordsTab?.columns?.includes(column)) fail(`Records tab missing column: ${column}`);
}

const buttonNames = new Set((template.notion?.buttons ?? []).map((button) => button.name));
for (const name of ['Plan dinner from pantry', 'Add receipt', 'Log cooked', 'Build shopping list']) {
  if (!buttonNames.has(name)) fail(`missing Notion button model: ${name}`);
}

console.log(`Data-plane template valid: ${food.collections.length} Food collections mapped to ${notionDatabaseNames.size} Notion databases and ${sheetTabNames.size} Sheets tabs.`);
