# P1R-REVIEW Reaccept provider and schema foundation

- Task: `P1R-REVIEW` (Evidence/plan-compliance review)
- Base branch: `main`
- Working branch: `orchestry/tsk_DGFJeEF/p1r-review-reaccept-provider-and-schema-`
- Reviewer: Evidence Reviewer (`agt_r4dyQ--`)

## Scope check
- Intent signals: reaccept canonical provider + schema foundation, require repair commits merged, and prove contracts/tests for phase3/5/6.
- Current branch ancestry confirms required repair commits are present:
  - `ff7d60d` (AJV 2020 repair)
  - `d646de3` (W1R kernel repair)
  - plus `f6336f8` (`P1-C`), `4e15b6f` (`P1-D`), `aad3cf7` (`P1-E`)
- Scope drift: **REJECT**-level finding.
  - Branch diff vs `origin/main` includes broad non-review-only churn and lockfile edits across app + server + spikes (4 lockfiles), indicating non-minimal reaccept scope and non-product-evidence-only churn.

## Required checks executed
- `npm run config:validate` — **PASS**
- `npm run typecheck` — **FAIL**
  - Missing modules in spikes: `ai`, `@ai-sdk/react`, `@ai-sdk/openai`, `@modelcontextprotocol/sdk/...`
- `npm run doctor` — **PASS**
- `npm run export:web` — **PASS**
- `npm run export:android` — **PASS**

Kernel/contract/boundary checks:
- `npm run check:kernel-boundaries` — **FAIL** (`./server/node_modules/.bin/tsx` missing)
- `npm run check:writer-boundary` — **PASS**
- `npm run check:migrations` — **PASS**
- `./server/node_modules/.bin/tsx --tsconfig tsconfig.json server/test/approval-schema-contract.ts` — **FAIL** (`tsx` missing)
- `./server/node_modules/.bin/ts... server/test/package-contract.ts` — **FAIL** (`tsx` missing)
- `npm run test:contract:notion` — **FAIL** (`tsx` missing)
- `npm run test:server:notion` — **FAIL** (`tsx` missing)
- `npm run test:server:webhook-ingress` — **FAIL** (`tsx` missing)
- `npm run test:server:webhook-retry` — **FAIL** (`tsx` missing)
- `npm run test:server:multiturn` — **FAIL** (`tsx` missing)
- `npm run test:server:openai-web-search` — **FAIL** (`tsx` missing)

Provider/writeback/outbox checks:
- `npm run check:writer-boundary` — **PASS**
- `npm run check:provider-writeback` — **PARTIAL**
  - `tests/providers/writeback.test.ts` **PASS**
  - then `check:reactive-provider-writeback` fails (`tsx` missing)
- `npm run check:provider-clear-restore` — **FAIL** (`tsx` missing)
- `npm run check:live-provider-writeback` — **FAIL** (`tsx` missing)
- `npm run check:operation-boundary` — **PARTIAL**
  - boundary grep **PASS**
  - then `check:provider-clear-restore` fails (`tsx` missing)

Phase checks:
- `npm run check:ai-runtime` — **PASS**
- `npm run check:workflow-runtime` — **PASS**
- `npm run phase3:check:chat-send` — **FAIL** (`tsx` missing)
- `npm run phase3:check:chat-undo` — **FAIL** (`tsx` missing)
- `npm run phase3:check:chat-rollback-idempotency` — **FAIL** (`tsx` missing)
- `npm run phase5:check` — **FAIL** (fails at `test:server:notion` `tsx` missing)
- `npm run phase6:check` — **FAIL** (fails at `test:server:sheets` `tsx` missing)

Core contract tests still runnable in current env:
- `npm exec -- vitest run tests/contracts/w1-kernel-contracts.test.ts tests/contracts/import-boundary.test.ts tests/ops/apply.test.ts tests/ops/plan.test.ts tests/ops/undo.test.ts` — **PASS**

## P1-C import-only audit
- Commit `f6336f8` added shared contract package and replaced local type definitions:
  - New contract files: `packages/shared/contracts/*` and re-export index.
  - Changed imports in kernel/app/domain/ops/db/tests to these contract files.
- Most changed files are import/type relocations and type-name re-exports.
- Non-trivial behavior edits observed in changed files:
  - `server/src/kernel/package.ts`: moved query/package type validation to shared contracts and changed several `expression`/`computed field` field casts.
  - `server/src/kernel/computed-fields.ts`: changed `spec.expression` typing to explicit casts.
  - `server/src/kernel/reactive-proposal-verification.ts`: moved receipt types to shared and adjusted null-path handling for create/postcondition verification.
- No unresolved behavioral deltas in the inspected hunks were proven, but all behavior-related checks still depend on blocked `tsx`-based phase tests.

## Deterministic blockers / disqualifiers
- `server/node_modules/.bin/tsx` is absent in this checkout, so all server-side tsx-based checks are blocked.
- This blocks mandatory evidence for approval/package/webhook contracts and phase5/phase6/phase3/operation-boundary provider-clear-restore tests.
- Lockfile edits exist and are part of this review diff:
  - `package-lock.json`
  - `server/package-lock.json`
  - `spikes/ai-sdk/package-lock.json`
  - `spikes/mcp-sdk/package-lock.json`
- This is against the task requirement to reject lockfile edits and violates “no weakened tests” when mandatory coverage is incomplete.

## Verdict
- **REJECT**
- Failure summary:
  - `REJECT: lockfile edits`
  - `REJECT: skipped contract/test proof paths due missing tsx runtime (approval/kernel/package/webhook + phase5 + phase6 + phase3 + operation-boundary writeback clear/restore)`
  - `REJECT: `npm run typecheck` failing due missing spike dependencies`
