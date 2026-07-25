# W1R-REVIEW Independent Wave 1 Current-Tree Acceptance

## Outcome
- Verdict: **REJECT**
- Block production work until all required checks pass.

## Baseline and lineage
- Branch: `orchestry/tsk_ARt54hM/w1r-review-independent-wave-1-current-tr`
- Base branch detected: `main`
- Base lineage used for this review: `114a0931a3d7099e9eaaad851317b26b794d4cab` → `78bbfdf`
- `git merge-base HEAD main` = `5f9b091c9667593f74e383554e59eb562211c4dd` (ancestor of `114a093`).

## Diff scope (`114a093..HEAD`)
- Files: `40`
- Insertions: `13744`
- Deletions: `0`
- Notable added/changed paths:
  - `package.json` (added `check:kernel-boundaries`)
  - `packages/domain-config/schemas/approval/*`
  - `scripts/quality/check-kernel-boundaries.ts`
  - `server/test/approval-schema-contract.ts`
  - `spikes/ai-sdk/**`
  - `spikes/mcp-sdk/**`
  - `tests/contracts/w1-kernel-contracts.test.ts`
  - `tests/contracts/w1-kernel-boundary-fixtures.json`
  - `tests/ops/apply.test.ts`

## Required commands run

### 1) `npm run config:validate`
- Result: PASS
- Output: domain config validation passed.

### 2) `npm run typecheck`
- Result: FAIL (`exit 2`)
- Failure: missing modules from spike deps (`ai`, `@ai-sdk/*`, `@modelcontextprotocol/sdk`) imported from root-including typecheck scope.

### 3) `npm run check:kernel-boundaries`
- Result: FAIL (`exit 127`)
- Failure: `./server/node_modules/.bin/tsx: No such file or directory`

### 4) Kernel/ops contract checks
- `npx --yes tsx server/test/kernel-validation.ts` → PASS
- `npx --yes tsx server/test/package-contract.ts` → PASS
- `npx --yes tsx tests/contracts/w1-kernel-contracts.test.ts` → FAIL (`exit 1`)
  - Failure: Vitest execution path in this environment fails at module boundary (`Vitest cannot be imported in a CommonJS module ...`).

### 5) Approval contract
- `npx --yes tsx server/test/approval-schema-contract.ts` → FAIL (`exit 1`)
- Failure: Ajv date-time schema resolution error (`no schema with key or ref "https://json-schema.org/draft/2020-12/schema"`).

### 6) AI spike proof checks
- `npm --prefix spikes/ai-sdk run check` → FAIL (`exit 2`)
  - Failure: missing `ai`, `@ai-sdk/react`, `@ai-sdk/openai` type modules.
- `npm --prefix spikes/ai-sdk run guard` → PASS

### 7) MCP spike checks
- `npm --prefix spikes/mcp-sdk run typecheck` → FAIL (`exit 2`)
  - Failure: missing `@modelcontextprotocol/sdk/*` modules.
- `npm --prefix spikes/mcp-sdk test` → FAIL (`exit 1`)
  - Failure: same missing `@modelcontextprotocol/sdk` package import.

## Compliance checks (scope policy)
- Lockfile edits:
  - Added: `spikes/ai-sdk/package-lock.json`, `spikes/mcp-sdk/package-lock.json`
  - No root lockfile edits in this acceptance window.
- Test-strength edits:
  - Added/extended tests are contract-focused (`approval-schema-contract`, `w1-kernel-contracts`, `tests/ops/apply.test`).
  - No test removals, no test path skipping, and no weakening markers observed.
- Forbidden-path edits:
  - No production app logic beyond expected scoped folders/contracts/spikes was edited in this acceptance range.

## Gate status
- Current-tree gate: **NOT PASSED**
- Blocking issues:
  1. Missing dependency/tooling availability for boundary and spike checks (`tsx`, `ai`, `@ai-sdk/*`, `@modelcontextprotocol/sdk`).
  2. Approval schema contract runtime Ajv bootstrap failure in `approval-schema-contract`.
  3. Kernel ops contract test runner invocation must use an ESM-safe execution path in this workspace.

## Required to move to PASS
- Install required dependencies in workspace contexts (root/server and spike workspaces) per project policy.
- Re-run the exact command list above after dependency/tooling install.
- Resolve `approval-schema-contract` Ajv date-time schema bootstrap so check exits PASS.
- Re-run `tests/contracts/w1-kernel-contracts.test.ts` through the project test runner path.
