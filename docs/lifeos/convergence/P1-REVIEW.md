# P1-REVIEW Canonical Kernel Foundation Acceptance

- Branch: `orchestry/tsk_On8ZIme/p1-review-accept-complete-canonical-kern`
- Canonical SHA: `ca1d3f6`
- Base SHA: `main` equivalent `114a093` (for P1 dependency task lineage)
- Dependencies
  - `P1-C` merge commit `f6336f8`
  - `P1-D` commit `4e15b6f`
  - `P1-E` merge commit `7f14855`

## Verdict

**REJECT**

## Scope and task alignment

- Requested scope: audit canonical checkout after P1-C/P1-D/P1-E.
- Scope check: branch is after all three required task merges; all task IDs are present in history and not stale.
- Scope drift test: none detected.

## Mandatory check matrix (current-tree)

- `npm run config:validate` — **PASS**
- `npm run typecheck` — **PASS**
- `npm run doctor` — **PASS**
- `npm run check:kernel-boundaries` — **PASS**
- `./server/node_modules/.bin/tsx --tsconfig tsconfig.json server/test/approval-schema-contract.ts` — **PASS**
- `npx --yes vitest run tests/contracts/w1-kernel-contracts.test.ts` — **PASS**
- `npx --yes vitest run tests/contracts/import-boundary.test.ts` — **PASS**
- `npx --yes vitest run tests/ops/apply.test.ts` — **PASS**
- `npx --yes vitest run tests/ops/plan.test.ts` — **PASS**
- `npx --yes vitest run tests/ops/undo.test.ts` — **PASS**
- `npx --yes vitest run tests/ops/writer-boundary.test.ts` — **PASS**
- `./server/node_modules/.bin/tsx --tsconfig tsconfig.json server/test/query-parity.ts` — **PASS**
- `./server/node_modules/.bin/tsx --tsconfig tsconfig.json server/test/query-sql.ts` — **PASS**
- `./server/node_modules/.bin/tsx --tsconfig tsconfig.json server/test/reactive-outbox.ts` — **PASS**
- `npm --prefix spikes/ai-sdk run check` — **PASS**
- `npm --prefix spikes/ai-sdk run guard` — **PASS**
- `npm --prefix spikes/mcp-sdk run typecheck` — **PASS**
- `npm --prefix spikes/mcp-sdk run test` — **PASS**
- `npx --yes vitest run tests/providers/writeback.test.ts` — **FAIL**

## Blocking failures (must fix before pass)

1. `tests/providers/writeback.test.ts` — writeback queue duplicate safety is broken:
   - `enqueueProviderWriteForOperation > queues duplicate-safe Notion create payloads from applied operations`
     - expected `db.outbox.size === 1`, observed `2`
   - `enqueueProviderWriteForOperation > queues Sheets update payloads and queues Undo as the inverse update`
     - expected row length `2`, observed `5`
   - `enqueueProviderWriteForOperation > queues archive provider payloads from archive operations`
     - expected `body.operation === 'archive_record'`, observed `undefined`
   - `enqueueProviderWriteForOperation > queues archive Undo as provider restore for Notion and Sheets`
     - expected only `['restore_record','restore_record']`, observed extra leading queue rows

## Locked/owned-file audit

- Changed lockfiles in scope diff include:
  - `package-lock.json`
  - `server/package-lock.json`
  - `spikes/ai-sdk/package-lock.json`
  - `spikes/mcp-sdk/package-lock.json`
- No new root/server lockfile collisions detected from these scoped changes.

- Task scope in ORCH for `P1-REVIEW` is only `docs/lifeos/convergence/P1-REVIEW.md`.
- No uncommitted edits outside scoped scope at write time.

## Shared contract / QuerySpec / committed-event status

- Shared contracts and imports remain contract-only:
  - `packages/shared/contracts/*` reference other shared contract modules only, no server/app/db implementation imports.
- QuerySpec parity evidence:
  - `server/test/query-parity.ts` passes via `tsx` direct execution.
- Committed-operation event checks:
  - `tests/ops/apply.test.ts` passes, including atomic rollback and no outbox emission for duplicate/rejected/dry-run.

## Spark/run evidence

- `P1-C`, `P1-D`, `P1-E` are complete in ORCH with `status: done`, and `P1-REVIEW` is currently `status: in_progress` under `agt_r4dyQ--`.
- This review used current-tree command execution only; no historical evidence is treated as current.

