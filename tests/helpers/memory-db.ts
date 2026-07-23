type Row = Record<string, any>;

export class MemoryDb {
  records = new Map<string, Row>();
  recordRelations: Row[] = [];
  operations = new Map<string, Row>();
  conflicts = new Map<string, Row>();
  providerLinks = new Map<string, Row>();
  sourceSnapshots = new Map<string, Row>();
  sourceSnapshotRelations: Row[] = [];

  async execAsync(_sql: string) {}

  async withTransactionAsync(fn: () => Promise<void>) {
    const snapshot = this.snapshot();
    try {
      await fn();
    } catch (error) {
      this.restore(snapshot);
      throw error;
    }
  }

  async runAsync(sql: string, params: any[] = []) {
    const compact = sql.replace(/\s+/g, ' ').trim();
    if (compact.startsWith('INSERT INTO records')) {
      const [id, domain, collection, title, properties, source_provider, source_external_id, source_url, source_observed_at, source_content_hash, archived_at, created_at, updated_at, revision, schema_version, deleted, privacy, provenance_json] = params;
      this.records.set(id, { id, domain, collection, title, properties, source_provider, source_external_id, source_url, source_observed_at, source_content_hash, archived_at, created_at, updated_at, revision, schema_version, deleted, privacy, provenance_json });
      return;
    }
    if (compact === 'DELETE FROM record_relations WHERE from_id = ?') {
      this.recordRelations = this.recordRelations.filter((row) => row.from_id !== params[0]);
      return;
    }
    if (compact.startsWith('INSERT INTO record_relations')) {
      const [from_id, collection, name, target_id, target_domain, target_collection, created_at] = params;
      this.recordRelations = this.recordRelations.filter((row) => !(row.from_id === from_id && row.name === name && row.target_id === target_id));
      this.recordRelations.push({ from_id, collection, name, target_id, target_domain, target_collection, created_at });
      return;
    }
    if (compact.startsWith('INSERT INTO operations')) {
      const [op_id, kind, domain, collection, record_id, expected_revision, result_revision, actor, origin, idempotency_key, changes_json, before_json, after_json, inverse_op_id, status, reject_reason, created_at] = params;
      this.operations.set(op_id, { op_id, kind, domain, collection, record_id, expected_revision, result_revision, actor, origin, idempotency_key, changes_json, before_json, after_json, inverse_op_id, status, reject_reason, created_at });
      return;
    }
    if (compact === 'UPDATE operations SET status = ? WHERE op_id = ?') {
      const [status, opId] = params;
      const row = this.operations.get(opId);
      if (row) row.status = status;
      return;
    }
    if (compact.startsWith('INSERT INTO sync_conflicts')) {
      const [id, domain, collection, record_id, provider, external_id, fields_json, base_json, local_json, remote_json, status, resolution_op_id, created_at, resolved_at] = params;
      this.conflicts.set(id, { id, domain, collection, record_id, provider, external_id, fields_json, base_json, local_json, remote_json, status, resolution_op_id, created_at, resolved_at });
      return;
    }
    if (compact === 'UPDATE sync_conflicts SET status = ?, resolution_op_id = ?, resolved_at = ? WHERE id = ?') {
      const [status, resolution_op_id, resolved_at, id] = params;
      const row = this.conflicts.get(id);
      if (row) {
        row.status = status;
        row.resolution_op_id = resolution_op_id;
        row.resolved_at = resolved_at;
      }
      return;
    }
    if (compact.startsWith('INSERT INTO provider_links')) {
      const [id, provider, external_id, name, status, freshness, workspace, url, created_at, updated_at] = params;
      this.providerLinks.set(id, { id, provider, external_id, name, status, freshness, workspace, url, created_at, updated_at });
      return;
    }
    if (compact.startsWith('INSERT INTO source_snapshots')) {
      const [id, provider, external_id, scope, observed_at, payload_json, checksum, created_at, updated_at] = params;
      this.sourceSnapshots.set(id, { id, provider, external_id, scope, observed_at, payload_json, checksum, created_at, updated_at });
      return;
    }
    if (compact.startsWith('INSERT OR IGNORE INTO source_snapshot_relations')) {
      const [snapshot_id, record_id] = params;
      if (!this.sourceSnapshotRelations.some((row) => row.snapshot_id === snapshot_id && row.record_id === record_id)) {
        this.sourceSnapshotRelations.push({ snapshot_id, record_id });
      }
      return;
    }
    throw new Error(`Unsupported runAsync SQL: ${compact}`);
  }

  async getFirstAsync<T>(sql: string, params: any[] = []): Promise<T | null> {
    const compact = sql.replace(/\s+/g, ' ').trim();
    if (compact === 'SELECT * FROM records WHERE id = ?') {
      return (this.records.get(params[0]) ?? null) as T | null;
    }
    if (compact === 'SELECT op_id, after_json, status FROM operations WHERE idempotency_key = ?') {
      const row = Array.from(this.operations.values()).find((item) => item.idempotency_key === params[0]);
      return (row ? { op_id: row.op_id, after_json: row.after_json, status: row.status } : null) as T | null;
    }
    if (compact === 'SELECT * FROM operations WHERE op_id = ?') {
      return (this.operations.get(params[0]) ?? null) as T | null;
    }
    if (compact === 'SELECT * FROM sync_conflicts WHERE id = ?') {
      return (this.conflicts.get(params[0]) ?? null) as T | null;
    }
    if (compact === 'SELECT * FROM source_snapshots WHERE provider = ? AND external_id = ? ORDER BY observed_at DESC LIMIT 1') {
      const rows = Array.from(this.sourceSnapshots.values())
        .filter((row) => row.provider === params[0] && row.external_id === params[1])
        .sort((left, right) => String(right.observed_at).localeCompare(String(left.observed_at)));
      return (rows[0] ?? null) as T | null;
    }
    throw new Error(`Unsupported getFirstAsync SQL: ${compact}`);
  }

  async getAllAsync<T>(sql: string, params: any[] = []): Promise<T[]> {
    const compact = sql.replace(/\s+/g, ' ').trim();
    if (compact === 'SELECT name, target_id FROM record_relations WHERE from_id = ?') {
      return this.recordRelations.filter((row) => row.from_id === params[0]).map((row) => ({ name: row.name, target_id: row.target_id })) as T[];
    }
    if (compact === 'SELECT * FROM sync_conflicts WHERE status = ? ORDER BY created_at DESC') {
      return Array.from(this.conflicts.values())
        .filter((row) => row.status === params[0])
        .sort((left, right) => String(right.created_at).localeCompare(String(left.created_at))) as T[];
    }
    throw new Error(`Unsupported getAllAsync SQL: ${compact}`);
  }

  private snapshot() {
    return {
      records: new Map(this.records),
      recordRelations: this.recordRelations.map((row) => ({ ...row })),
      operations: new Map(this.operations),
      conflicts: new Map(this.conflicts),
      providerLinks: new Map(this.providerLinks),
      sourceSnapshots: new Map(this.sourceSnapshots),
      sourceSnapshotRelations: this.sourceSnapshotRelations.map((row) => ({ ...row })),
    };
  }

  private restore(snapshot: ReturnType<MemoryDb['snapshot']>) {
    this.records = new Map(snapshot.records);
    this.recordRelations = snapshot.recordRelations.map((row) => ({ ...row }));
    this.operations = new Map(snapshot.operations);
    this.conflicts = new Map(snapshot.conflicts);
    this.providerLinks = new Map(snapshot.providerLinks);
    this.sourceSnapshots = new Map(snapshot.sourceSnapshots);
    this.sourceSnapshotRelations = snapshot.sourceSnapshotRelations.map((row) => ({ ...row }));
  }
}
