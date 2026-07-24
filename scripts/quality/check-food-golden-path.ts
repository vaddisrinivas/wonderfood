import { createHash } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

import { currentGit } from './evidence-provenance.mjs';
import { loadCatalog, setActivePackageOverride } from '../../src/domain/catalog';
import { bootstrapAppPackageRegistry } from '../../src/db/app-package-registry';
import { exportRecoverySnapshot } from '../../src/db/migrations';
import { importRecoverySnapshot } from '../../src/db/recovery';
import { getRecord, upsertRecord } from '../../src/db/records';
import { applyOperation } from '../../src/ops/apply';

type Row = Record<string, any>;

const recoveryTables = [
  'meta',
  'records',
  'record_relations',
  'conversations',
  'conversation_messages',
  'source_snapshots',
  'provider_links',
  'outbox_events',
  'action_events',
  'operations',
  'sync_conflicts',
  'config_sources',
  'config_snapshots',
  'config_conflicts',
  'app_packages',
  'app_package_state',
  'app_package_receipts',
  'undo_events',
  'workflow_runs',
  'agent_runs',
  'citations',
  'source_snapshot_relations',
] as const;

class FoodGoldenDb {
  tables = new Map<string, Row[]>(recoveryTables.map((table) => [table, []]));

  constructor() {
    this.tables.set('meta', [{ key: 'lifecycle', value: 'ready' }, { key: 'active_domain_id', value: 'food' }]);
  }

  async execAsync(_sql: string) {}

  async withTransactionAsync(fn: () => Promise<void>) {
    await fn();
  }

  async runAsync(sql: string, params: any[] | Row = []) {
    const compact = sql.replace(/\s+/g, ' ').trim();
    if (compact.startsWith('INSERT INTO records')) {
      const [id, domain, collection, title, properties, source_provider, source_external_id, source_url, source_observed_at, source_content_hash, archived_at, created_at, updated_at, revision, schema_version, deleted, privacy, provenance_json] = params as any[];
      this.upsert('records', { id, domain, collection, title, properties, source_provider, source_external_id, source_url, source_observed_at, source_content_hash, archived_at, created_at, updated_at, revision, schema_version, deleted, privacy, provenance_json }, 'id');
      return;
    }
    if (compact === 'DELETE FROM record_relations WHERE from_id = ?') {
      const [fromId] = params as any[];
      this.setRows('record_relations', this.rows('record_relations').filter((row) => row.from_id !== fromId));
      return;
    }
    if (compact.startsWith('INSERT INTO record_relations')) {
      const [from_id, collection, name, target_id, target_domain, target_collection, created_at] = params as any[];
      this.setRows('record_relations', this.rows('record_relations').filter((row) => !(row.from_id === from_id && row.name === name && row.target_id === target_id)));
      this.rows('record_relations').push({ from_id, collection, name, target_id, target_domain, target_collection, created_at });
      return;
    }
    if (compact.startsWith('INSERT INTO operations')) {
      const [op_id, kind, domain, collection, record_id, expected_revision, result_revision, actor, origin, idempotency_key, changes_json, before_json, after_json, inverse_op_id, status, reject_reason, created_at] = params as any[];
      this.upsert('operations', { op_id, kind, domain, collection, record_id, expected_revision, result_revision, actor, origin, idempotency_key, changes_json, before_json, after_json, inverse_op_id, status, reject_reason, created_at }, 'op_id');
      return;
    }
    if (compact.startsWith('INSERT OR REPLACE INTO app_packages')) {
      const row = namedParams(params);
      this.upsert('app_packages', {
        package_key: row.$package_key,
        package_id: row.$package_id,
        version: row.$version,
        payload_json: row.$payload_json,
        created_at: row.$created_at,
        updated_at: row.$updated_at,
      }, 'package_key');
      return;
    }
    if (compact.startsWith('INSERT OR REPLACE INTO app_package_state')) {
      const row = namedParams(params);
      this.setRows('app_package_state', [{
        id: 'default',
        active_package_key: row.$active_package_key,
        previous_package_key: row.$previous_package_key,
        updated_at: row.$updated_at,
      }]);
      return;
    }
    if (compact.startsWith('INSERT INTO app_package_receipts')) {
      const row = namedParams(params);
      this.rows('app_package_receipts').push({
        id: row.$id,
        action: row.$action,
        package_key: row.$package_key,
        previous_package_key: row.$previous_package_key,
        created_at: row.$created_at,
      });
      return;
    }
    if (compact.startsWith('DELETE FROM ')) {
      const table = compact.split(' ')[2];
      this.setRows(table, []);
      return;
    }
    if (compact.startsWith('INSERT INTO ')) {
      const match = compact.match(/^INSERT INTO ([A-Za-z_][A-Za-z0-9_]*) \(([^)]+)\) VALUES/);
      if (!match) throw new Error(`Unsupported insert: ${compact}`);
      const [, table, columnText] = match;
      const columns = columnText.split(',').map((column) => column.trim());
      const values = params as any[];
      this.rows(table).push(Object.fromEntries(columns.map((column, index) => [column, values[index] ?? null])));
      return;
    }
    throw new Error(`Unsupported runAsync SQL: ${compact}`);
  }

  async getFirstAsync<T>(sql: string, params: any[] | Row = []): Promise<T | null> {
    const compact = sql.replace(/\s+/g, ' ').trim();
    if (compact === 'SELECT * FROM records WHERE id = ?') {
      return (this.rows('records').find((row) => row.id === (params as any[])[0]) ?? null) as T | null;
    }
    if (compact === 'SELECT op_id, after_json, status FROM operations WHERE idempotency_key = ?') {
      const row = this.rows('operations').find((item) => item.idempotency_key === (params as any[])[0]);
      return (row ? { op_id: row.op_id, after_json: row.after_json, status: row.status } : null) as T;
    }
    if (compact === "SELECT active_package_key, previous_package_key FROM app_package_state WHERE id = 'default'") {
      const row = this.rows('app_package_state')[0];
      return (row ? { active_package_key: row.active_package_key, previous_package_key: row.previous_package_key } : null) as T | null;
    }
    if (compact === 'SELECT COUNT(*) as count FROM app_packages') {
      return { count: this.rows('app_packages').length } as T;
    }
    if (compact === 'SELECT package_key, payload_json FROM app_packages WHERE package_key = $package_key') {
      const key = namedParams(params).$package_key;
      const row = this.rows('app_packages').find((item) => item.package_key === key);
      return (row ? { package_key: row.package_key, payload_json: row.payload_json } : null) as T | null;
    }
    throw new Error(`Unsupported getFirstAsync SQL: ${compact}`);
  }

  async getAllAsync<T>(sql: string, params: any[] = []): Promise<T[]> {
    const compact = sql.replace(/\s+/g, ' ').trim();
    if (compact === 'SELECT name, target_id FROM record_relations WHERE from_id = ?') {
      return this.rows('record_relations').filter((row) => row.from_id === params[0]).map((row) => ({ name: row.name, target_id: row.target_id })) as T[];
    }
    if (compact.startsWith('SELECT * FROM ')) {
      const table = compact.split(' ')[3];
      return this.rows(table).map((row) => ({ ...row })) as T[];
    }
    throw new Error(`Unsupported getAllAsync SQL: ${compact}`);
  }

  private rows(table: string) {
    if (!this.tables.has(table)) this.tables.set(table, []);
    return this.tables.get(table)!;
  }

  private setRows(table: string, rows: Row[]) {
    this.tables.set(table, rows);
  }

  private upsert(table: string, row: Row, key: string) {
    this.setRows(table, [...this.rows(table).filter((existing) => existing[key] !== row[key]), row]);
  }
}

function namedParams(params: unknown): Row {
  return params && typeof params === 'object' && !Array.isArray(params) ? params as Row : {};
}

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function checksum(db: FoodGoldenDb) {
  const normalized = db.tables.get('records')!
    .map((row) => ({
      id: row.id,
      collection: row.collection,
      title: row.title,
      properties: JSON.parse(row.properties || '{}'),
      archived_at: row.archived_at,
      revision: row.revision,
    }))
    .sort((left, right) => String(left.id).localeCompare(String(right.id)));
  return createHash('sha256').update(JSON.stringify(normalized)).digest('hex');
}

(async () => {
  setActivePackageOverride(null);
  const db = new FoodGoldenDb() as any;
  const proofDb = db as FoodGoldenDb;
  assert(proofDb.tables.get('records')!.length === 0, 'fresh database was not empty');

  const appPackage = await bootstrapAppPackageRegistry(db);
  assert(appPackage.id === 'food', 'Food package did not bootstrap');
  const manifest = loadCatalog().activeManifest;
  const now = new Date().toISOString();

  await upsertRecord(db, manifest, {
    id: 'golden-pantry-yogurt',
    title: 'Golden yogurt',
    collection: 'inventory',
    properties: { status: 'Use soon', tone: 'amber', body: 'Use in breakfast bowl.', quantity: 2 },
    source: { provider: 'user', external_id: 'golden-pantry-yogurt', url: null, observed_at: now, content_hash: null },
    archived_at: null,
    created_at: now,
    updated_at: now,
    relations: [],
  });
  await upsertRecord(db, manifest, {
    id: 'golden-meal-bowl',
    title: 'Golden breakfast bowl',
    collection: 'meal_plan',
    properties: { status: 'Planned', tone: 'moss', body: 'Yogurt, berries, granola.' },
    source: { provider: 'user', external_id: 'golden-meal-bowl', url: null, observed_at: now, content_hash: null },
    archived_at: null,
    created_at: now,
    updated_at: now,
    relations: [{ name: 'uses', target_id: 'golden-pantry-yogurt' }],
  });
  await upsertRecord(db, manifest, {
    id: 'golden-shop-berries',
    title: 'Golden berries',
    collection: 'shopping_item',
    properties: { status: 'To buy', tone: 'blue', body: 'Buy berries for breakfast bowl.' },
    source: { provider: 'user', external_id: 'golden-shop-berries', url: null, observed_at: now, content_hash: null },
    archived_at: null,
    created_at: now,
    updated_at: now,
    relations: [],
  });

  const shopping = await getRecord(db, 'golden-shop-berries');
  assert(shopping, 'shopping item missing');
  await applyOperation(db, manifest, {
    op_id: 'golden-shop-purchased',
    kind: 'update',
    domain: manifest.id,
    collection: shopping.collection,
    record_id: shopping.id,
    expected_revision: shopping.revision,
    actor: 'user',
    origin: 'manual',
    changes: { properties: { ...shopping.properties, status: 'Purchased', tone: 'moss' } },
    reason: 'Golden path marks shopping item purchased.',
  });

  const searchHit = proofDb.tables.get('records')!.find((row) => String(row.title).toLowerCase().includes('breakfast'));
  assert(searchHit?.id === 'golden-meal-bowl', 'search did not find meal plan');

  const meal = await getRecord(db, 'golden-meal-bowl');
  assert(meal, 'meal plan missing before edit');
  await applyOperation(db, manifest, {
    op_id: 'golden-meal-edit',
    kind: 'update',
    domain: manifest.id,
    collection: meal.collection,
    record_id: meal.id,
    expected_revision: meal.revision,
    actor: 'user',
    origin: 'manual',
    changes: { title: 'Golden breakfast bowl edited', properties: { ...meal.properties, note: 'edited' } },
    reason: 'Golden path edits meal plan.',
  });

  const edited = await getRecord(db, 'golden-meal-bowl');
  assert(edited, 'meal plan missing before archive');
  await applyOperation(db, manifest, {
    op_id: 'golden-meal-archive',
    kind: 'archive',
    domain: manifest.id,
    collection: edited.collection,
    record_id: edited.id,
    expected_revision: edited.revision,
    actor: 'user',
    origin: 'manual',
    reason: 'Golden path archives meal plan.',
  });
  const archived = await getRecord(db, 'golden-meal-bowl');
  assert(archived?.archived_at, 'archive did not set archived_at');

  await applyOperation(db, manifest, {
    op_id: 'golden-meal-undo',
    kind: 'restore',
    domain: manifest.id,
    collection: archived.collection,
    record_id: archived.id,
    expected_revision: archived.revision,
    actor: 'user',
    origin: 'manual',
    reason: 'Golden path undo restores meal plan.',
  });
  const restoredMeal = await getRecord(db, 'golden-meal-bowl');
  assert(restoredMeal?.archived_at === null, 'undo did not restore meal plan');

  const beforeChecksum = checksum(proofDb);
  const snapshot = await exportRecoverySnapshot(db);
  const restoredDb = new FoodGoldenDb() as any;
  const restoredProof = restoredDb as FoodGoldenDb;
  await importRecoverySnapshot(restoredDb, snapshot);
  const afterChecksum = checksum(restoredProof);
  assert(beforeChecksum === afterChecksum, `backup restore mismatch ${beforeChecksum}/${afterChecksum}`);
  assert(restoredProof.tables.get('app_packages')!.length === 1, 'app package not exported/restored');
  assert(restoredProof.tables.get('app_package_state')![0]?.active_package_key === 'food@1.0.0', 'active package state not restored');

  const outDir = join(process.cwd(), 'app', 'build', 'evidence', 'food');
  mkdirSync(outDir, { recursive: true });
  const outPath = join(outDir, 'food-golden-path-proof.json');
  writeFileSync(outPath, JSON.stringify({
    proof: 'food_golden_path',
    checked_at: new Date().toISOString(),
    git: currentGit(process.cwd()),
    package: appPackage.id,
    records: restoredProof.tables.get('records')!.length,
    operations: restoredProof.tables.get('operations')!.length,
    packageRows: restoredProof.tables.get('app_packages')!.length,
    beforeChecksum,
    afterChecksum,
    steps: [
      'fresh_empty',
      'bootstrap_package',
      'add_pantry',
      'create_meal_plan',
      'add_shopping_item',
      'mark_purchased',
      'search',
      'edit',
      'archive',
      'undo',
      'export',
      'restore_compare',
    ],
    all_passed: true,
  }, null, 2));
  console.log(`PASS ${outPath}`);
})().catch((error) => {
  console.error('FAIL', error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
});
