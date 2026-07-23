import type { SQLiteDatabase } from 'expo-sqlite';
import { RecordProvider } from '@/src/domain/runtime';

export type ProviderLink = {
  id: string;
  provider: RecordProvider;
  external_id: string;
  name: string;
  status: string;
  freshness: string | null;
  workspace: string | null;
  url: string | null;
  created_at: string;
  updated_at: string;
};

type ProviderLinkRow = Omit<ProviderLink, 'freshness' | 'workspace'> & {
  freshness: string | null;
  workspace: string | null;
};

export type SourceSnapshot = {
  id: string;
  provider: RecordProvider;
  external_id: string;
  scope: string | null;
  observed_at: string;
  payload_json: string;
  checksum: string | null;
  created_at: string;
  updated_at: string;
};

export type SourceCausality = {
  snapshot_id: string;
  record_id: string;
};

export async function upsertProviderLink(db: SQLiteDatabase, link: ProviderLink): Promise<void> {
  await db.runAsync(
    `
      INSERT INTO provider_links (id, provider, external_id, name, status, freshness, workspace, url, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        provider = excluded.provider,
        external_id = excluded.external_id,
        name = excluded.name,
        status = excluded.status,
        freshness = excluded.freshness,
        workspace = excluded.workspace,
        url = excluded.url,
        updated_at = excluded.updated_at
    `,
    [
      link.id,
      link.provider,
      link.external_id,
      link.name,
      link.status,
      link.freshness,
      link.workspace,
      link.url,
      link.created_at,
      link.updated_at,
    ]
  );
}

export async function listProviderLinks(db: SQLiteDatabase): Promise<ProviderLink[]> {
  return db.getAllAsync<ProviderLinkRow>('SELECT * FROM provider_links ORDER BY updated_at DESC');
}

export async function getAllProviderLinks(db: SQLiteDatabase): Promise<ProviderLink[]> {
  return listProviderLinks(db);
}

export async function getProviderLink(db: SQLiteDatabase, id: string): Promise<ProviderLink | null> {
  return db.getFirstAsync<ProviderLinkRow>('SELECT * FROM provider_links WHERE id = ?', [id]);
}

export async function upsertSourceSnapshot(db: SQLiteDatabase, snapshot: SourceSnapshot): Promise<void> {
  await db.runAsync(
    `
      INSERT INTO source_snapshots (
        id, provider, external_id, scope, observed_at, payload_json, checksum, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        provider = excluded.provider,
        external_id = excluded.external_id,
        scope = excluded.scope,
        observed_at = excluded.observed_at,
        payload_json = excluded.payload_json,
        checksum = excluded.checksum,
        updated_at = excluded.updated_at
    `,
    [
      snapshot.id,
      snapshot.provider,
      snapshot.external_id,
      snapshot.scope,
      snapshot.observed_at,
      snapshot.payload_json,
      snapshot.checksum,
      snapshot.created_at,
      snapshot.updated_at,
    ]
  );
}

export async function getSnapshot(db: SQLiteDatabase, id: string): Promise<SourceSnapshot | null> {
  return db.getFirstAsync<SourceSnapshot>('SELECT * FROM source_snapshots WHERE id = ?', [id]);
}

export async function listSourceSnapshots(db: SQLiteDatabase, provider?: RecordProvider): Promise<SourceSnapshot[]> {
  if (provider) {
    return db.getAllAsync<SourceSnapshot>('SELECT * FROM source_snapshots WHERE provider = ? ORDER BY observed_at DESC', [provider]);
  }
  return db.getAllAsync<SourceSnapshot>('SELECT * FROM source_snapshots ORDER BY observed_at DESC');
}

export async function linkSnapshotToRecord(
  db: SQLiteDatabase,
  linkage: SourceCausality
): Promise<void> {
  await db.runAsync(
    `INSERT OR IGNORE INTO source_snapshot_relations (snapshot_id, record_id) VALUES (?, ?)`,
    [linkage.snapshot_id, linkage.record_id]
  );
}

export async function listRecordSourceSnapshots(db: SQLiteDatabase, recordId: string): Promise<SourceSnapshot[]> {
  const rows = await db.getAllAsync<SourceSnapshot>(
    `
      SELECT s.*
      FROM source_snapshots s
      INNER JOIN source_snapshot_relations r ON r.snapshot_id = s.id
      WHERE r.record_id = ?
      ORDER BY s.observed_at DESC
    `,
    [recordId]
  );
  return rows;
}
