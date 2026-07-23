import { describe, expect, it } from 'vitest';

import { DATABASE_VERSION, exportRecoverySnapshot, rollbackDatabase, runMigrations } from '@/src/db/migrations';

type Row = Record<string, unknown>;

class MigrationMemoryDb {
  userVersion = 0;
  tables = new Map<string, { columns: string[]; rows: Row[] }>();
  indexes = new Set<string>();

  async withTransactionAsync(fn: () => Promise<void>) {
    await fn();
  }

  async execAsync(sql: string) {
    const compact = sql.replace(/\s+/g, ' ').trim();
    const userVersion = compact.match(/^PRAGMA user_version = (\d+)$/i);
    if (userVersion) {
      this.userVersion = Number(userVersion[1]);
      return;
    }
    if (/^PRAGMA (journal_mode|foreign_keys)/i.test(compact)) return;

    const create = compact.match(/^CREATE TABLE IF NOT EXISTS ([A-Za-z_][A-Za-z0-9_]*) \((.*)\)$/i);
    if (create) {
      const [, name, body] = create;
      const columns = Array.from(body.matchAll(/(?:^|,)\s*([A-Za-z_][A-Za-z0-9_]*)\s+(TEXT|INTEGER)\b/gi)).map((match) => match[1]);
      const existing = this.tables.get(name);
      this.tables.set(name, { columns: existing ? Array.from(new Set([...existing.columns, ...columns])) : columns, rows: existing?.rows ?? [] });
      return;
    }

    const alter = compact.match(/^ALTER TABLE ([A-Za-z_][A-Za-z0-9_]*) ADD COLUMN ([A-Za-z_][A-Za-z0-9_]*) /i);
    if (alter) {
      const [, tableName, column] = alter;
      const table = this.tables.get(tableName);
      if (!table) throw new Error(`missing table ${tableName}`);
      if (!table.columns.includes(column)) table.columns.push(column);
      const rawDefault = compact.match(/\bDEFAULT\s+('[^']*'|\d+)/i)?.[1];
      const defaultValue = rawDefault == null ? null : rawDefault.startsWith("'") ? rawDefault.slice(1, -1) : Number(rawDefault);
      for (const row of table.rows) {
        row[column] = defaultValue;
      }
      return;
    }

    const createIndex = compact.match(/^CREATE (?:UNIQUE )?INDEX IF NOT EXISTS ([A-Za-z_][A-Za-z0-9_]*) /i);
    if (createIndex) {
      this.indexes.add(createIndex[1]);
      return;
    }

    const drop = compact.match(/^DROP TABLE IF EXISTS ([A-Za-z_][A-Za-z0-9_]*)$/i);
    if (drop) {
      this.tables.delete(drop[1]);
      return;
    }

    throw new Error(`Unsupported execAsync SQL: ${compact}`);
  }

  async runAsync(sql: string, params: Row | unknown[] = []) {
    const compact = sql.replace(/\s+/g, ' ').trim();
    if (compact.startsWith('INSERT OR REPLACE INTO meta')) {
      const table = this.ensureTable('meta');
      const key = Array.isArray(params) ? params[0] : params.$key;
      const value = Array.isArray(params) ? params[1] : params.$value;
      table.rows = table.rows.filter((row) => row.key !== key);
      table.rows.push({ key, value });
      return;
    }
    throw new Error(`Unsupported runAsync SQL: ${compact}`);
  }

  async getFirstAsync<T>(sql: string): Promise<T | null> {
    const compact = sql.replace(/\s+/g, ' ').trim();
    if (compact === 'PRAGMA user_version') return { user_version: this.userVersion } as T;
    throw new Error(`Unsupported getFirstAsync SQL: ${compact}`);
  }

  async getAllAsync<T>(sql: string): Promise<T[]> {
    const compact = sql.replace(/\s+/g, ' ').trim();
    const tableInfo = compact.match(/^PRAGMA table_info\(([A-Za-z_][A-Za-z0-9_]*)\)$/i);
    if (tableInfo) {
      return (this.tables.get(tableInfo[1])?.columns ?? []).map((name, cid) => ({ cid, name })) as T[];
    }
    const selectAll = compact.match(/^SELECT \* FROM ([A-Za-z_][A-Za-z0-9_]*)$/i);
    if (selectAll) {
      return (this.tables.get(selectAll[1])?.rows ?? []) as T[];
    }
    throw new Error(`Unsupported getAllAsync SQL: ${compact}`);
  }

  insertRecordV1(row: Row) {
    const table = this.ensureTable('records');
    table.rows.push(row);
  }

  private ensureTable(name: string) {
    const table = this.tables.get(name);
    if (table) return table;
    const next = { columns: [], rows: [] as Row[] };
    this.tables.set(name, next);
    return next;
  }
}

describe('database migrations', () => {
  it('fresh install reaches current schema with operation and conflict tables', async () => {
    const db = new MigrationMemoryDb();
    await runMigrations(db as any);

    expect(db.userVersion).toBe(DATABASE_VERSION);
    expect(db.tables.has('operations')).toBe(true);
    expect(db.tables.has('sync_conflicts')).toBe(true);
    const recordColumns = await db.getAllAsync<{ name: string }>('PRAGMA table_info(records)');
    expect(recordColumns.map((column) => column.name)).toEqual(expect.arrayContaining([
      'revision',
      'schema_version',
      'deleted',
      'privacy',
      'provenance_json',
    ]));
    expect(db.indexes.has('operations_idem_idx')).toBe(true);
    expect(db.indexes.has('operations_record_idx')).toBe(true);
    expect(db.indexes.has('sync_conflicts_record_idx')).toBe(true);
  });

  it('upgrades v1 records with envelope defaults', async () => {
    const db = new MigrationMemoryDb();
    await runMigrations(db as any);
    await rollbackDatabase(db as any, 1);
    db.userVersion = 1;
    db.insertRecordV1({
      id: 'legacy-record',
      domain: 'food',
      collection: 'recipe',
      title: 'Legacy recipe',
      properties: '{}',
      source_provider: 'sqlite',
      source_external_id: 'legacy-record',
      source_observed_at: '2026-07-23T00:00:00.000Z',
      created_at: '2026-07-23T00:00:00.000Z',
      updated_at: '2026-07-23T00:00:00.000Z',
    });

    await runMigrations(db as any);
    const rows = await db.getAllAsync<Row>('SELECT * FROM records');
    expect(db.userVersion).toBe(DATABASE_VERSION);
    expect(rows[0]).toMatchObject({
      revision: 1,
      schema_version: '1.0.0',
      deleted: 0,
      privacy: 'personal',
      provenance_json: null,
    });
  });

  it('exports operations and sync conflicts in recovery snapshots', async () => {
    const db = new MigrationMemoryDb();
    await runMigrations(db as any);

    const snapshot = await exportRecoverySnapshot(db as any);
    expect(snapshot.schema_version).toBe(DATABASE_VERSION);
    expect(snapshot.tables.map((table) => table.name)).toEqual(expect.arrayContaining(['operations', 'sync_conflicts']));
  });
});
