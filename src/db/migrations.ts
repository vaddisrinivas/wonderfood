import { SQLiteDatabase } from 'expo-sqlite';
import { loadCatalog } from '@/src/domain/catalog';

export const DATABASE_NAME = 'wonderfood-lifeos.db';
export const DATABASE_VERSION = 3;

const TABLES = {
  meta: 'meta',
  records: 'records',
  relations: 'record_relations',
  conversations: 'conversations',
  messages: 'conversation_messages',
  source_snapshots: 'source_snapshots',
  provider_links: 'provider_links',
  outbox: 'outbox_events',
  actions: 'action_events',
  operations: 'operations',
  sync_conflicts: 'sync_conflicts',
  undo_events: 'undo_events',
  workflow_runs: 'workflow_runs',
  agent_runs: 'agent_runs',
  citations: 'citations',
  source_snapshots_causality: 'source_snapshot_relations',
} as const;

export type RecoveryExport = {
  schema_version: number;
  tables: Array<{
    name: string;
    rows: Array<Record<string, unknown>>;
  }>;
};

type Migration = {
  version: number;
  up: (db: SQLiteDatabase) => Promise<void>;
  down?: (db: SQLiteDatabase) => Promise<void>;
};

const MIGRATIONS: Migration[] = [
  {
    version: 1,
    up: async (db) => {
      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS ${TABLES.meta} (
          key TEXT PRIMARY KEY,
          value TEXT NOT NULL
        )
      `);

      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS ${TABLES.records} (
          id TEXT PRIMARY KEY,
          domain TEXT NOT NULL,
          collection TEXT NOT NULL,
          title TEXT NOT NULL,
          properties TEXT NOT NULL,
          source_provider TEXT NOT NULL CHECK(source_provider IN ('notion', 'google_sheets', 'sqlite', 'postgres', 'web', 'user')),
          source_external_id TEXT NOT NULL,
          source_url TEXT,
          source_observed_at TEXT NOT NULL,
          source_content_hash TEXT,
          archived_at TEXT,
          created_at TEXT NOT NULL,
          updated_at TEXT NOT NULL
        )
      `);

      await db.execAsync(`
        CREATE INDEX IF NOT EXISTS ${TABLES.records}_domain_idx ON ${TABLES.records}(domain, collection)
      `);
      await db.execAsync(`
        CREATE INDEX IF NOT EXISTS ${TABLES.records}_updated_idx ON ${TABLES.records}(updated_at)
      `);

      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS ${TABLES.relations} (
          from_id TEXT NOT NULL,
          collection TEXT NOT NULL,
          name TEXT NOT NULL,
          target_id TEXT NOT NULL,
          target_domain TEXT NOT NULL,
          target_collection TEXT NOT NULL,
          created_at TEXT NOT NULL,
          PRIMARY KEY (from_id, name, target_id)
        )
      `);

      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS ${TABLES.citations} (
          id TEXT PRIMARY KEY,
          record_id TEXT,
          conversation_id TEXT,
          label TEXT NOT NULL,
          detail TEXT NOT NULL,
          href TEXT NOT NULL,
          tone TEXT NOT NULL,
          payload_json TEXT,
          created_at TEXT NOT NULL
        )
      `);

      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS ${TABLES.conversations} (
          id TEXT PRIMARY KEY,
          domain TEXT NOT NULL,
          title TEXT NOT NULL,
          detail TEXT NOT NULL,
          created_at TEXT NOT NULL,
          updated_at TEXT NOT NULL,
          archived_at TEXT
        )
      `);

      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS ${TABLES.messages} (
          id TEXT PRIMARY KEY,
          conversation_id TEXT NOT NULL,
          role TEXT NOT NULL CHECK(role IN ('user', 'assistant')),
          sort_index INTEGER NOT NULL,
          body TEXT NOT NULL,
          answer_payload TEXT,
          created_at TEXT NOT NULL,
          FOREIGN KEY (conversation_id) REFERENCES ${TABLES.conversations}(id) ON DELETE CASCADE
        )
      `);

      await db.execAsync(`
        CREATE INDEX IF NOT EXISTS ${TABLES.messages}_conversation_idx
          ON ${TABLES.messages}(conversation_id, sort_index);
      `);

      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS ${TABLES.provider_links} (
          id TEXT PRIMARY KEY,
          provider TEXT NOT NULL,
          external_id TEXT NOT NULL,
          name TEXT NOT NULL,
          status TEXT NOT NULL,
          freshness TEXT,
          workspace TEXT,
          url TEXT,
          created_at TEXT NOT NULL,
          updated_at TEXT NOT NULL
        )
      `);

      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS ${TABLES.source_snapshots} (
          id TEXT PRIMARY KEY,
          provider TEXT NOT NULL,
          external_id TEXT NOT NULL,
          scope TEXT,
          observed_at TEXT NOT NULL,
          payload_json TEXT NOT NULL,
          checksum TEXT,
          created_at TEXT NOT NULL,
          updated_at TEXT NOT NULL
        )
      `);

      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS ${TABLES.source_snapshots_causality} (
          snapshot_id TEXT NOT NULL,
          record_id TEXT NOT NULL,
          PRIMARY KEY (snapshot_id, record_id)
        )
      `);

      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS ${TABLES.outbox} (
          id TEXT PRIMARY KEY,
          action_key TEXT NOT NULL,
          domain TEXT NOT NULL,
          payload_json TEXT NOT NULL,
          status TEXT NOT NULL DEFAULT 'pending',
          attempts INTEGER NOT NULL DEFAULT 0,
          last_error TEXT,
          created_at TEXT NOT NULL,
          updated_at TEXT NOT NULL
        )
      `);

      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS ${TABLES.actions} (
          id TEXT PRIMARY KEY,
          domain TEXT NOT NULL,
          conversation_id TEXT,
          actor TEXT NOT NULL,
          tool TEXT NOT NULL,
          record_ids TEXT,
          before_json TEXT,
          after_json TEXT,
          undo_payload_json TEXT,
          idempotency_key TEXT UNIQUE,
          status TEXT NOT NULL DEFAULT 'queued',
          created_at TEXT NOT NULL,
          updated_at TEXT NOT NULL
        )
      `);

      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS ${TABLES.undo_events} (
          id TEXT PRIMARY KEY,
          action_id TEXT NOT NULL UNIQUE,
          payload_json TEXT NOT NULL,
          expires_at TEXT,
          created_at TEXT NOT NULL,
          FOREIGN KEY (action_id) REFERENCES ${TABLES.actions}(id) ON DELETE CASCADE
        )
      `);

      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS ${TABLES.workflow_runs} (
          id TEXT PRIMARY KEY,
          domain TEXT NOT NULL,
          workflow_id TEXT NOT NULL,
          inputs_json TEXT,
          status TEXT NOT NULL DEFAULT 'running',
          payload_json TEXT,
          created_at TEXT NOT NULL,
          updated_at TEXT NOT NULL
        )
      `);

      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS ${TABLES.agent_runs} (
          id TEXT PRIMARY KEY,
          domain TEXT NOT NULL,
          role TEXT NOT NULL,
          state TEXT NOT NULL,
          request_json TEXT,
          response_json TEXT,
          created_at TEXT NOT NULL,
          updated_at TEXT NOT NULL
        )
      `);

      await db.execAsync('PRAGMA foreign_keys = ON');
      await db.execAsync(`PRAGMA user_version = ${DATABASE_VERSION}`);
      await db.runAsync(
        `INSERT OR REPLACE INTO ${TABLES.meta} (key, value) VALUES ($key, $value)`,
        { $key: 'lifecycle', $value: 'ready' }
      );

      const catalog = loadCatalog();
      await db.runAsync(
        `INSERT OR REPLACE INTO ${TABLES.meta} (key, value) VALUES ($key, $value)`,
        {
          $key: 'active_domain_id',
          $value: catalog.activeDomainId,
        }
      );
    },
    down: async (db) => {
      for (const name of Object.values(TABLES).reverse()) {
        await db.execAsync(`DROP TABLE IF EXISTS ${name}`);
      }
      await db.execAsync('PRAGMA user_version = 0');
    },
  },
  {
    version: 2,
    up: async (db) => {
      await db.execAsync(`ALTER TABLE ${TABLES.records} ADD COLUMN revision INTEGER NOT NULL DEFAULT 1`);
      await db.execAsync(`ALTER TABLE ${TABLES.records} ADD COLUMN schema_version TEXT NOT NULL DEFAULT '1.0.0'`);
      await db.execAsync(`ALTER TABLE ${TABLES.records} ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0`);
      await db.execAsync(`ALTER TABLE ${TABLES.records} ADD COLUMN privacy TEXT NOT NULL DEFAULT 'personal' CHECK(privacy IN ('private','personal','shared'))`);
      await db.execAsync(`ALTER TABLE ${TABLES.records} ADD COLUMN provenance_json TEXT`);
      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS ${TABLES.operations} (
          op_id TEXT PRIMARY KEY,
          kind TEXT NOT NULL,
          domain TEXT NOT NULL,
          collection TEXT NOT NULL,
          record_id TEXT NOT NULL,
          expected_revision INTEGER,
          result_revision INTEGER,
          actor TEXT NOT NULL CHECK(actor IN ('user','ai','import','sync','agent','api','workflow')),
          origin TEXT NOT NULL,
          idempotency_key TEXT,
          changes_json TEXT,
          before_json TEXT,
          after_json TEXT,
          inverse_op_id TEXT,
          status TEXT NOT NULL DEFAULT 'applied' CHECK(status IN ('applied','rejected','undone','superseded')),
          reject_reason TEXT,
          created_at TEXT NOT NULL
        )
      `);
      await db.execAsync(`
        CREATE UNIQUE INDEX IF NOT EXISTS ${TABLES.operations}_idem_idx
          ON ${TABLES.operations}(idempotency_key) WHERE idempotency_key IS NOT NULL
      `);
      await db.execAsync(`
        CREATE INDEX IF NOT EXISTS ${TABLES.operations}_record_idx
          ON ${TABLES.operations}(record_id, created_at)
      `);
      await db.execAsync(`PRAGMA user_version = 2`);
    },
    down: async (db) => {
      await db.execAsync(`DROP TABLE IF EXISTS ${TABLES.operations}`);
      await db.execAsync(`PRAGMA user_version = 1`);
    },
  },
  {
    version: 3,
    up: async (db) => {
      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS ${TABLES.sync_conflicts} (
          id TEXT PRIMARY KEY,
          domain TEXT NOT NULL,
          collection TEXT NOT NULL,
          record_id TEXT NOT NULL,
          provider TEXT NOT NULL,
          external_id TEXT NOT NULL,
          fields_json TEXT NOT NULL,
          base_json TEXT,
          local_json TEXT NOT NULL,
          remote_json TEXT NOT NULL,
          status TEXT NOT NULL DEFAULT 'needs_review'
            CHECK(status IN ('needs_review','resolved','dismissed')),
          resolution_op_id TEXT,
          created_at TEXT NOT NULL,
          resolved_at TEXT
        )
      `);
      await db.execAsync(`
        CREATE INDEX IF NOT EXISTS ${TABLES.sync_conflicts}_record_idx
          ON ${TABLES.sync_conflicts}(record_id, status, created_at)
      `);
      await db.execAsync(`PRAGMA user_version = 3`);
    },
    down: async (db) => {
      await db.execAsync(`DROP TABLE IF EXISTS ${TABLES.sync_conflicts}`);
      await db.execAsync(`PRAGMA user_version = 2`);
    },
  },
];

export async function getDatabaseVersion(db: SQLiteDatabase): Promise<number> {
  const row = await db.getFirstAsync<{ user_version: number | string }>('PRAGMA user_version');
  if (row == null) return 0;
  if (typeof row.user_version === 'number') return row.user_version;
  const parsed = Number.parseInt(row.user_version, 10);
  return Number.isFinite(parsed) ? parsed : 0;
}

export async function runMigrations(db: SQLiteDatabase): Promise<void> {
  // Journal mode changes require autocommit mode on Android SQLite. Keep this
  // outside the migration transaction so a fresh install cannot remain behind
  // the splash screen while the provider waits for initialization.
  await db.execAsync('PRAGMA journal_mode = WAL');

  const currentVersion = await getDatabaseVersion(db);
  if (currentVersion > DATABASE_VERSION) {
    throw new Error(`Database schema is newer than app can handle: ${currentVersion}`);
  }

  for (const migration of MIGRATIONS) {
    if (migration.version <= currentVersion) {
      continue;
    }

    await db.withTransactionAsync(async () => {
      await db.execAsync('PRAGMA foreign_keys = OFF');
      await migration.up(db);
      await db.execAsync('PRAGMA foreign_keys = ON');
      await db.execAsync(`PRAGMA user_version = ${migration.version}`);
    });
  }
}

export async function rollbackDatabase(db: SQLiteDatabase, targetVersion: number): Promise<void> {
  const currentVersion = await getDatabaseVersion(db);
  if (targetVersion >= currentVersion) {
    return;
  }

  const migrationsToRollback = MIGRATIONS
    .filter((migration) => migration.version > targetVersion)
    .filter((migration): migration is Migration & { down: NonNullable<Migration['down']> } => typeof migration.down === 'function')
    .sort((a, b) => b.version - a.version);

  await db.withTransactionAsync(async () => {
    for (const migration of migrationsToRollback) {
      await migration.down(db);
    }
  });
}

export async function exportRecoverySnapshot(db: SQLiteDatabase): Promise<RecoveryExport> {
  const tables = Object.values(TABLES);
  const rows = [] as Array<{ name: string; rows: Array<Record<string, unknown>> }>;
  for (const table of tables) {
    try {
      const tableRows = await db.getAllAsync<Record<string, unknown>>(`SELECT * FROM ${table}`);
      rows.push({ name: table, rows: tableRows });
    } catch {
      rows.push({ name: table, rows: [] });
    }
  }

  return {
    schema_version: DATABASE_VERSION,
    tables: rows,
  };
}
