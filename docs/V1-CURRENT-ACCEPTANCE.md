# WonderFood V1 Current Acceptance

Status: **NOT ACCEPTED**  
Audit date: `2026-07-26`  
Commit: `61cb98b`  
Branch basis: `codex/lifeos-e2e-implementation`

## Audit basis

- Read: `docs/V1-PLATFORM-AUDIT-AND-REMEDIATION-PLAN.md`, `docs/REPOSITORY-AUDIT.md`, and the changed/runtime risk-bearing files around auth, chat, MCP, providers, reactive runtime, release proof, tests, and docs.
- Mutation policy for this audit: **no live provider mutation**, **no product edits**, **docs only**.
- Repository truth check: `docs/REPOSITORY-AUDIT.md` is itself stale at `8b2cb9a`, so it cannot be treated as current acceptance proof.

## Deterministic gates run at `61cb98b`

- `npm run config:validate` -> `PASS`
  Notes: `Domain config valid: 3 domains, active=food, 29 active collections, 5 active workflows, 7 agents.`

- `npm run check:kernel-boundaries` -> `PASS`
  Notes: `scanned 126 files`, `Canonical write owners: 4`.

- `npm run check:operation-boundary` -> `PASS`
  Notes: operation-boundary grep passed; provider clear/restore proof passed.

- `npm run phase8:check:release-readiness` -> `BLOCKED`
  Blockers: `android_release_artifacts_missing`, `android_release_artifacts_stale`, `android_release_build_receipt_missing_or_stale`, `android_apk_not_release_signed`, `android_aab_unsigned`, `android_signing_env_missing`, `ios_export_missing`, `ios_release_env_missing`.

- `npm run typecheck` -> `FAIL`
  Exact failures come from `spikes/**`:
  `spikes/ai-sdk/src/client/chat-client.tsx:1-3`
  `spikes/ai-sdk/src/server/{agent,model,stream-handler}.ts:1-3`
  `spikes/mcp-sdk/src/server.ts:2-3`
  `spikes/mcp-sdk/tests/parity.test.ts:2-3`
  Root cause: `tsconfig.json:12-17` includes all `**/*.ts` and `**/*.tsx`, so unmaintained spike code breaks repo-wide typecheck.

- `npm run test` -> `PASS`
  Notes: `24` files passed, `102` tests passed.

- `npm run test:server:direct` -> `PASS`
  Notes: all current direct server contract suites passed, including auth, MCP, verifier, undo, retrieval, reactive, workflow, and provider tests.

- `npm run phase4:check:mcp` -> `PASS`
  Notes: tool contract, workflow replay, and streamable HTTP replay all passed.

- `NPM_CONFIG_CACHE=/tmp/wonderfood-npm-cache npm run doctor` -> `PASS`
  Notes: `19/19 checks passed`.

- `npm audit --omit=dev --json` -> `FAIL`
  Root app: `13` production vulnerabilities (`12 moderate`, `1 high`), including `brace-expansion`.
  Server package: `2` moderate vulnerabilities through `@modelcontextprotocol/sdk` / `@hono/node-server`.

## Finding registry

### P0

- `P0-01` `PARTIAL`
  Summary: anonymous/private MCP read exposure is fixed, but principal/resource confidentiality is still not fully closed.
  Source: `server/src/mcp/official-server.ts:302-317,408-417`; `server/src/mcp/auth.ts:127-138`; `server/src/mcp/resources.ts:207-248`
  Commit/test evidence: hardening landed across `7b8b0a9` and follow-on tests in `server/test/mcp-official-security.ts:70-153`; `npm run phase4:check:mcp` passed.
  Residual gap: authenticated callers still self-assert domain/principal scope via headers, and authenticated unscoped callers still see global `wonderfood://records`, `wonderfood://actions`, `wonderfood://workflows`, and `wonderfood://conversations` indexes.
  Acceptance impact: V1 confidentiality is still not accepted.

### P1

- `P1-01` `PARTIAL`
  Summary: request-time auth now fails closed, but deployment-time safety is still incomplete.
  Source: `server/src/mcp/auth.ts:65-73,75-109`; `server/src/index.ts:56-57,1842-1845`
  Commit/test evidence: auth hardening in `7b8b0a9`; `server/test/ingress-security.ts:71-97`; `server/test/mcp-official-security.ts:70-90`
  Residual gap: startup still allows external bind, and `LIFEOS_LOCAL_DEV` bypass is not restricted to loopback.
  Acceptance impact: external deployment misconfiguration still has too much room for operator error.

- `P1-02` `PARTIAL`
  Summary: review approval is now durably enforced at tool call sites, but policy still exposes invalid boolean/meta states.
  Source: `server/src/mcp/policy.ts:5-12,64-114`; `server/src/mcp/tools.ts:638-780,2451-2453,2721-2723,3000-3002,3401-3403`
  Commit/test evidence: review/verifier hardening in `2398f4b`; contract repairs in `b1487ab`; `server/test/mcp-review-approval.ts:29-89`; `phase4-mcp-tool-contract` proof passed
  Residual gap: the policy layer still returns `allowed + safety + requiresClarification` instead of one exhaustive `deny | clarify | review | execute` decision.
  Acceptance impact: enforcement is better, but the policy model can still encode contradictory states.

- `P1-03` `OPEN`
  Summary: provider-backed chat mutations route through MCP tools now, but the default `local_sqlite` authority still uses direct MCP state mutations.
  Source: `server/src/agents/executor.ts:432-496,606-646,646-684,734-753,771-808,858-876,894-931`
  Commit/test evidence: chat isolation/idempotency hardening in `3bbfc3b`; undo/provider work in `8b2cb9a`; existing `npm run check:operation-boundary` only proves app-side/runtime writer boundaries, not server chat ingress parity
  Residual gap: `createRecord`, `updateRecord`, and `archiveRecord` are still called directly from the chat executor for local authority.
  Acceptance impact: the one-writer north star is still untrue on the default authority path.

- `P1-04` `PARTIAL`
  Summary: provider undo now readbacks before local success, but undo is still not a clean canonical state machine.
  Source: `server/src/mcp/state.ts:1493-1527,1574-1698,1705-1836`; `server/src/providers/undo.ts:94-225`
  Commit/test evidence: provider undo hardening in `8b2cb9a`; `server/test/provider-undo-authority-contract.ts:248-329,367-403`; `phase4-mcp-workflow-replay` proof passed
  Residual gap: successful undo still sets action status to `cancelled`, and local rollback still mutates MCP JSON state directly after provider verification.
  Acceptance impact: truthfulness improved, but undo lifecycle semantics remain ambiguous and non-canonical.

- `P1-05` `RESOLVED`
  Summary: the verifier no longer false-greens failed, missing, mismatched, or unbound writes.
  Source: `server/src/agents/verifier.ts:39-182`
  Commit/test evidence: `2398f4b`, `b1487ab`; `server/test/canonical-verification.ts:5-131`
  Resolution detail: tool mismatch, actual status mismatch, missing canonical action, empty record ids, missing reread, missing undo payload, and source-bound mismatch now all deny verification.
  Acceptance impact: this original false-green hole is closed.

- `P1-06` `PARTIAL`
  Summary: MCP/provider state files improved, but server state durability is still uneven.
  Source: `server/src/mcp/state.ts:196-229`; `server/src/providers/json-state.ts:15-46`; `server/src/conversations.ts:75-126`; `server/src/kernel/install-reactive-runtime.ts:71-87`
  Commit/test evidence: provider JSON hardening in `8b2cb9a`; `server/test/provider-persistence-contract.ts:10-37`
  Residual gap: conversations and reactive runtime still use raw whole-file JSON writes without shared quarantine/fsync/lock/checksum/backup rules.
  Acceptance impact: crash/corruption recovery is still inconsistent across stores.

- `P1-07` `PARTIAL`
  Summary: request body limits exist now, but ingress is still not fully bounded.
  Source: `server/src/index.ts:58-63,202-233`; `server/src/mcp/official-server.ts:419-447`
  Commit/test evidence: `7b8b0a9`; `server/test/ingress-security.ts:113-148`; `server/test/mcp-official-security.ts:134-153`
  Residual gap: there are no request deadlines, header bounds, or slow-body protections.
  Acceptance impact: body exhaustion is harder now, but ingress hardening is not complete.

- `P1-08` `PARTIAL`
  Summary: chat idempotency is now principal and conversation scoped, but still not durable.
  Source: `server/src/index.ts:66-123,300-345,1280-1283,1499-1502`
  Commit/test evidence: `3bbfc3b`; `server/test/chat-isolation-contract.ts:99-166`
  Residual gap: idempotency and run status still live only in process memory, so restart semantics are not proven.
  Acceptance impact: cross-tenant replay leak is fixed, but durable replay protection is still missing.

### P2

- `P2-01` `RESOLVED`
  Summary: `plan_hint` no longer drives hidden command intent.
  Source: `server/src/chat.ts:160-184`; `server/src/chat-runtime.ts:72-81`
  Commit/test evidence: source closure present at `61cb98b`; chat send/runtime suites passed in `npm run test:server:direct`
  Resolution detail: `planHint` is set to the visible text, and runtime uses `query` / `commandText` from the user message.

- `P2-02` `RESOLVED`
  Summary: `/chat/send` and `/chat/send/stream` now ignore client-supplied `previous_response_id`.
  Source: `server/src/chat.ts:167-184`; `server/src/index.ts:1280-1282,1499-1501,1450-1459`
  Commit/test evidence: source closure present at `61cb98b`; chat send/runtime suites passed in `npm run test:server:direct`
  Resolution detail: stored conversation response ids are used; `/chat/agent` explicitly passes `previousResponseId: undefined`.

- `P2-03` `PARTIAL`
  Summary: retrieval now projects allowlisted facts and only hits live providers when the query selects them, but provider load/privacy controls are incomplete.
  Source: `server/src/agents/retrieval.ts:30-202,210-235,314-321`
  Commit/test evidence: retrieval hardening in `6464051`; `server/test/retrieval-contract.ts:13-51`
  Residual gap: no cache, timeout, circuit breaker, or explicit source budget/freshness policy.
  Acceptance impact: prompt privacy is much better, but retrieval scalability and freshness semantics are still incomplete.

- `P2-04` `RESOLVED`
  Summary: Notion pull now paginates until target or limit.
  Source: `server/src/providers/notion/pull.ts:290-404`
  Commit/test evidence: pagination contract proof in `server/test/provider-retry-pagination-contract.ts:78-135`
  Resolution detail: cursor/`has_more` loop continues until the requested page is found or the limit is satisfied.

- `P2-05` `OPEN`
  Summary: reactive outbox drain exists as a helper only; startup still never runs it.
  Source: `server/src/index.ts:121-126`; `server/src/kernel/install-reactive-runtime.ts:89-146`
  Commit/test evidence: `server/test/reactive-runtime-drain.ts:108-121` proves manual drain behavior only
  Residual gap: there is no startup recovery, periodic wake, or lease-based worker lifecycle.
  Acceptance impact: review or automatic proposals can persist forever without runtime progress.

- `P2-06` `RESOLVED`
  Summary: review-required reactive proposals now persist as `awaiting_review` instead of being acked away.
  Source: `server/src/kernel/reactive-outbox.ts:8,118-132,152-203`; `server/src/kernel/reactive-proposal-executor.ts:88-94`
  Commit/test evidence: `server/test/reactive-proposal-executor.ts:116-133`; `server/test/reactive-runtime-drain.ts:108-121`
  Resolution detail: queued-for-review results no longer count as executed/acked work.

- `P2-07` `PARTIAL`
  Summary: observer failures now emit durable failed receipts, but the observer is still advisory.
  Source: `server/src/kernel/operation-observer.ts:37-49`; `server/src/kernel/install-reactive-runtime.ts:148-166`
  Commit/test evidence: `server/test/reactive-observer-failure-receipt.ts:14-49`
  Residual gap: downstream work is still outside the canonical write transaction; there is no transactional outbox boundary.
  Acceptance impact: silent failure is reduced, but guaranteed reactive delivery is not proven.

- `P2-08` `PARTIAL`
  Summary: release readiness now refuses to call unsigned/debug artifacts release proof, but the artifact lane itself still has ambiguity.
  Source: `android/app/build.gradle:89-132`; `scripts/quality/check-android-release-artifacts.sh:18-33,36-93`; `scripts/quality/check-release-readiness.mjs:53-75`
  Commit/test evidence: `npm run phase8:check:release-readiness` currently blocks on unsigned/missing artifacts
  Residual gap: build helper can still debug-sign a release APK when `BUILD_RELEASE_ARTIFACTS=1`, and hard release-sign enforcement still depends on `REQUIRE_RELEASE_SIGNING=1`.
  Acceptance impact: store-release proof remains blocked and terminology remains risky.

- `P2-09` `PARTIAL`
  Summary: Ajv-backed config validation is in place, but not yet strict enough to be final authority.
  Source: `scripts/domain-config-validator.mjs:25-52,168-179`; `tests/config/domain-config-validator.test.ts:38-70`
  Commit/test evidence: `40f435b`; `npm run config:validate` passed
  Residual gap: `Ajv2020` still runs with `strict: false`, and the mutation corpus only covers a few failure classes.
  Acceptance impact: config validation is materially better but not yet exhaustive or strict-mode safe.

- `P2-10` `OPEN`
  Summary: persistence-sensitive app tests still run through a permissive fake DB.
  Source: `tests/helpers/memory-db.ts:3-176`; `tests/ops/writer-boundary.test.ts:54-140`
  Commit/test evidence: writer-boundary tests pass, but they pass against `MemoryDb`, not real SQLite
  Residual gap: no real SQLite proof for locking, FK, transaction, migration, or JSON behavior on these lanes.
  Acceptance impact: persistence confidence is overstated.

- `P2-11` `PARTIAL`
  Summary: shared provenance exists, but confidence semantics still drift across layers.
  Source: `packages/shared/contracts/records.ts:16-21`; `src/domain/runtime.ts:193-206`; `src/actions/policy.ts:3-10`; `server/src/agents/domain.ts:1-10,17-28`; `server/src/types/command.ts:54-60`
  Commit/test evidence: no dedicated convergence test found; current runtime compiles/executes with mixed numeric and qualitative confidence semantics
  Residual gap: canonical record provenance uses numeric `confidence`, while policy and command contracts still use `'high' | 'medium' | 'low'`.
  Acceptance impact: provenance and UI semantics are still not one contract.

- `P2-12` `OPEN`
  Summary: repo docs and generated authority still disagree with current code state.
  Source: `README.md:5,49-59`; `FEATURES.md:7-12`; `docs/REPOSITORY-AUDIT.md:3,349,354`; `scripts/quality/check-lifeos-completion-audit.mjs:83-106,168-193`
  Commit/test evidence: `docs/REPOSITORY-AUDIT.md` still snapshots `8b2cb9a`; completion audit can still say `COMPLETE` from evidence inventory without consulting current open P0/P1 rows
  Residual gap: docs are not generated from the current acceptance registry, and stale snapshot references remain user-visible.
  Acceptance impact: evidence authority is stale, so completion claims are not trustworthy.

- `P2-13` `PARTIAL`
  Summary: default repo gates now avoid live provider/device mutation, but lane isolation is still soft.
  Source: `package.json:80-87,82-86,97-100`
  Commit/test evidence: default `quality` script excludes live provider/device lanes; separate live/native scripts still remain callable under the same repo surface
  Residual gap: no hard tenant/device guardrail is enforced at the package-script boundary, and release/live proof remains adjacent to hermetic proof.
  Acceptance impact: accidental live mutation risk is lower, but not fully boxed in.

## Additional regression notes outside the original registry

- Repo-wide typecheck is not green.
  Source: `tsconfig.json:12-17`; `spikes/ai-sdk/src/client/chat-client.tsx:1-3`; `spikes/ai-sdk/src/server/{agent,model,stream-handler}.ts:1-3`; `spikes/mcp-sdk/src/server.ts:2-3`; `spikes/mcp-sdk/tests/parity.test.ts:2-3`
  Impact: current README quality-gate guidance is false for this checkout.

- Production dependency audit is not green.
  Evidence: `npm audit --omit=dev --json`
  Impact: app root has `13` production vulnerabilities and server has `2` moderate vulnerabilities; this is not a direct acceptance blocker from the original registry, but it is a real release risk that should move into the next wave.

## Acceptance verdict

Do **not** call V1 complete at `61cb98b`.

Reasons:

- `P0-01` is still only `PARTIAL`.
- `P1-03` is still `OPEN`.
- `P1-01`, `P1-02`, `P1-04`, `P1-06`, `P1-07`, and `P1-08` are still `PARTIAL`.
- `npm run phase8:check:release-readiness` is `BLOCKED`.
- repo-wide `npm run typecheck` is `FAIL`.
- current proof docs are stale relative to `61cb98b`.

## Next-wave task queue

1. `A` close MCP confidentiality and startup hardening
   Scope: `server/src/mcp/{auth,official-server,resources}.ts`, `server/src/index.ts`
   Required checks: extend `server/test/mcp-official-security.ts` for self-asserted-scope denial and add non-loopback boot-refusal coverage.

2. `B` collapse chat/MCP/local authority onto one canonical writer
   Scope: `server/src/agents/executor.ts`, `server/src/mcp/policy.ts`, `server/src/mcp/tools.ts`
   Required checks: add server ingress parity suite; fail if any chat path imports direct record mutators.

3. `C` make idempotency and server state durable
   Scope: `server/src/{index,conversations}.ts`, `server/src/kernel/install-reactive-runtime.ts`, shared persistence helpers
   Required checks: restart replay, corrupt-file quarantine, concurrent-writer, and slow-body suites.

4. `D` finish undo lifecycle truth
   Scope: `server/src/mcp/state.ts`, `server/src/providers/undo.ts`, workflow compensation
   Required checks: provider undo state-machine tests proving `undone`/`undo_failed` semantics instead of `cancelled`.

5. `E` add real reactive worker lifecycle
   Scope: `server/src/kernel/{install-reactive-runtime,reactive-outbox,reactive-proposal-executor}.ts`
   Required checks: startup recovery, eventual drain, one-active-lease, and approval-resume proof.

6. `F` add retrieval cache/timeouts/source budgets
   Scope: `server/src/agents/retrieval.ts`, provider pull clients
   Required checks: retrieval privacy contract expansion plus timeout/load/pagination tests.

7. `H` fix repo truth and release truth
   Scope: docs, completion audit, release artifact script, spike typecheck bleed, real SQLite test migration, dependency audit follow-up
   Required checks: `npm run typecheck`, `npm run phase9:check:completion-audit`, `npm run phase8:check:android-release-signed`, `npm audit --omit=dev --json`.
