# LifeOS truth layer v1

The app may look like Notion, Sheets, chat, or a native dashboard, but records mutate through one local boundary:

1. A UI, sync adapter, workflow, or chat action creates an `Operation`.
2. `applyOperation()` validates the active domain manifest and current revision.
3. The operation writes the canonical record, relation rows, provenance, and ledger row in one transaction.
4. Stale writers are rejected with `revision_conflict`.
5. Repeated provider/user actions can use `idempotency_key` and return `duplicate` instead of mutating twice.
6. `undoOperation()` applies a generated inverse operation and marks the original operation `undone`.

Canonical records now carry:

- `revision`
- `schema_version`
- `deleted`
- `privacy`
- `provenance`

Normal app writes should call `upsertRecord()` or `archiveRecord()`. Those wrappers route into `applyOperation()`.

Provider local-copy clear remains a local destructive maintenance action. It backs up records/snapshots in memory and restores through `upsertRecord()`, so restored records re-enter the operation boundary.

Proof gate:

- `npm run check:provider-clear-restore`

That gate proves provider clear/restore plus operation revision, stale-write rejection, idempotency, and local undo.
