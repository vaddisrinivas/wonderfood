# LifeOS truth layer v1

The app may look like Notion, Sheets, chat, or a native dashboard, but records mutate through one local boundary:

1. A UI, sync adapter, workflow, or chat action creates an `Operation`.
2. `applyOperation()` validates the active domain manifest and current revision.
3. The operation writes the canonical record, relation rows, provenance, and ledger row in one transaction.
4. Stale writers are rejected with `revision_conflict`.
5. Repeated provider/user actions can use `idempotency_key` and return `duplicate` instead of mutating twice.
6. `undoOperation()` applies a generated inverse operation and marks the original operation `undone`.
7. AI and agent proposals pass through `src/ai/runtime.ts`, which rejects raw SQL/CRUD/command channels and checks declared `{domain, collections, ops}` capabilities before `applyOperation()`.
8. Dry-run calls use the same validation path and return before/after diffs without writing records, relations, or ledger rows.

Canonical records now carry:

- `revision`
- `schema_version`
- `deleted`
- `privacy`
- `provenance`

Normal app writes should call `upsertRecord()` or `archiveRecord()`. Those wrappers route into `applyOperation()`.

Provider local-copy clear/disconnect remains a local destructive maintenance action. It backs up records/snapshots in memory and restores through `upsertRecord()`, so restored records re-enter the operation boundary.

Proof gates:

- `npm run check:provider-clear-restore`
- `npm run check:ai-runtime`

Those gates prove provider clear/restore/disconnect, operation revision, stale-write rejection, idempotency, local undo, AI capability rejection, and dry-run without writes.
