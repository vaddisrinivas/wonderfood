# WonderFood V1 Platform Audit and Remediation Plan

Status: **audit complete; remediation not complete**  
Audit baseline: `fd8e2e6` (`2026-07-26`)  
Scope: app shell, domain config, canonical data, AI/chat, MCP, workflows, providers, persistence, tests, documentation, and release evidence.

## Executive decision

WonderFood is not V1-ready.

The product has a strong config-driven foundation, broad feature coverage, and many proof scripts. It also has one P0 confidentiality defect and several P1 authorization, mutation, verification, durability, and isolation defects. Passing historical gates do not override current source findings.

Commit `fd8e2e6` fixed exactly four audited issues:

1. Android backup flag mismatch.
2. Agent registry wildcard delete capability.
3. Canonical record schema drift.
4. Broken Python MCP domain-catalog path.

It did **not** complete V1. It did not fix the P0 MCP resource exposure or the P1 server/runtime defects below.

## North star

WonderFood V1 is a local-first LifeOS whose product behavior is controlled by validated, versioned config:

`catalog -> domain manifest -> surfaces/views -> schemas -> skills -> workflows -> policies -> provider adapters -> one canonical operation boundary -> receipts/undo`

The app, web shell, chat, MCP, providers, and automations must interpret the same contracts. No surface may invent a parallel schema, permission rule, workflow, or write path.

Required properties:

- Adding a valid domain package changes navigation, surfaces, queries, actions, rendering, skills, and workflows without editing feature-specific UI.
- Invalid or incompatible config is rejected before activation; last-known-good remains active.
- AI proposes or invokes typed actions. It never bypasses policy, validation, canonical writes, receipts, provider authority, or Undo.
- Every write is scoped, authenticated, authorized, idempotent, revision-aware, durable, observable, and reversibly proven where the policy says reversible.
- Read surfaces return only the caller-authorized projection.
- Provider truth, canonical truth, cached projection, and model text are never conflated.
- “Passed” means current-tree evidence with provenance, not a historical receipt.

## Current feature and workflow inventory

### Config/control plane

| Contract | Current source | V1 expectation |
|---|---|---|
| Shell and domains | `packages/domain-config/domain-catalog.v1.json` | Config owns tabs, global actions, active domain, labels, icons, status, manifest and skill links. |
| Domain surfaces | `packages/domain-config/domains/{food,health,plants}.v1.json` | Config owns surfaces, views, collections, dashboard blocks, visual identity, relations, data homes, rendering and MCP exposure. |
| Runtime schemas | `packages/domain-config/schemas/*.json` | Every loaded artifact and every boundary payload validated by compiled validators. |
| Agent registry | `packages/domain-config/agents/registry.v1.json` | Capabilities are least-privilege and mechanically enforced. |
| Config sources | `src/config/{types,fetchers,runtime,sync,ai}.ts` | Preview, conflict, migration review, acceptance, rollback and last-known-good activation. |
| Package bridge | `src/domain/{catalog,runtime,surface,renderer,app-package-bridge}.ts` | App renders from one normalized package model, not route-local constants. |

### User surfaces

| Surface | Present behavior | V1 gap |
|---|---|---|
| Today/domain home | Dashboard blocks and domain switching | Prove all blocks/actions derive from activated config on web and native. |
| Food | Overview, meals, kitchen, shopping; 29 collections | Close runtime safety issues; prove every collection/editor state. |
| Health | Overview, journal, plans; Health Connect | Medical/privacy policy, permission, empty/error/offline proof. |
| Plants | Overview, journal, supplies | Package portability proof without Food-specific branching. |
| Chat | Threads, streaming, citations, actions, Undo | Plan-hint injection, response binding, idempotency, verification and executor bypass defects. |
| Search/capture/detail | Cross-record lookup, capture, record editor | Authorization, schema parity, unknown/archived/conflict states. |
| Config/settings/sources/system | Control plane and provider state | Secret-safe diagnostics, activation rollback, truthful provider authority. |
| Health diagnostics | Native health status | Production-safe copy, permissions, transport and device proof. |

### Workflows

| Workflow | Source | Classification |
|---|---|---|
| Meal plan to shopping | `packages/domain-config/workflows/meal-plan-to-shopping.v1.json` | Product workflow; reversible shopping changes; never order. |
| Receipt to kitchen | `packages/domain-config/workflows/receipt-to-kitchen.v1.json` | Product workflow; unreadable lines require review. |
| Weekly food reset | `packages/domain-config/workflows/weekly-food-reset.v1.json` | Scheduled product workflow; purchase/delete/credential/health export require confirmation. |
| Phase 4 replay | `packages/domain-config/workflows/phase4_replay_workflow.v1.json` | Test fixture; must not appear as a product workflow. |
| Phase 4 compensation | `packages/domain-config/workflows/phase4_compensation_probe.v1.json` | Test fixture; must not appear as a product workflow. |
| Reactive rules/proposals | `server/src/kernel/reactive-*.ts` | Runtime workflow; drain, approval, retry and observability incomplete. |

### Data and integrations

- Canonical local store: Expo SQLite plus a separate server JSON/MCP state implementation.
- External planes: Notion, Google Sheets, Postgres contract, Health Connect.
- AI/headless clients: OpenAI chat runtime, MCP resources/tools, Python MCP bridge.
- Sync: direct provider pull/writeback, merge/conflict, outboxes, webhooks as signals.
- Release targets: web export, Android export/build, iOS export; signed-store proof still distinct from export proof.

## Verified finding registry

Severity:

- **P0**: confidentiality/integrity exposure; stop release and external server use.
- **P1**: realistic unauthorized mutation, false success, data loss/corruption, cross-user leakage, or unsafe production behavior.
- **P2**: material reliability, scalability, maintainability, test, documentation, or release-quality gap.

### P0

| ID | Exact evidence | Defect and impact | Required fix | Proof |
|---|---|---|---|---|
| P0-01 | `server/src/mcp/official-server.ts:42-53`, `server/src/mcp/official-server.ts:67-70`; `server/src/mcp/resources.ts:288-365` | MCP authenticates only non-read-only tools. Resource listing/reading is public. Resources include complete records, action events, conversations and per-record/action/conversation URIs. Anyone reaching `/mcp` can read private household data and operational history. | Require principal authentication before list/read/call; authorize each resource URI and projection; do not publish global indexes by default; deny startup in non-loopback mode without auth. | Anonymous list/read denied; user A cannot enumerate/read user B; static public schema allowlist works; logs contain no resource body. |

### P1

| ID | Exact evidence | Defect and impact | Required fix | Proof |
|---|---|---|---|---|
| P1-01 | `server/src/index.ts:152-156`; `server/src/mcp/auth.ts:14-19` | Server and MCP fail open when tokens are absent. A deployment mistake silently disables authentication. | Production startup must fail closed. Explicit local-development bypass must require loopback plus named dev flag. Centralize principal parsing. | Missing token + non-loopback refuses boot; malformed/missing bearer returns 401; dev bypass cannot bind externally. |
| P1-02 | `server/src/mcp/policy.ts:136-164`; `server/src/mcp/tools.ts:2708-2715`, `server/src/mcp/tools.ts:2884-2913` | Policy labels standard writes `review-required` while `allowed: true`; tool paths mutate when clarification is false. “Review required” is presentation metadata, not an enforcement state. | Replace booleans with exhaustive decision union: `deny`, `clarify`, `review`, `execute`. Only `execute` reaches canonical writer. Bind approval to exact action digest, principal and expiry. | Review decision produces zero record/provider writes; tampered/expired/wrong-user approval denied; accepted approval writes once. |
| P1-03 | `server/src/agents/executor.ts:486-622` | Chat executor calls MCP state create/update/archive directly. It bypasses the canonical operation engine, provider authority/writeback, revision checks and shared policy enforcement. | One command bus and one canonical writer for app, chat, MCP, workflow and reactive paths. Remove direct state mutations from executor. | Static boundary test; same command through every ingress yields equivalent receipt, revision, outbox and Undo. |
| P1-04 | `server/src/mcp/state.ts:1579-1593`, `server/src/mcp/state.ts:1641-1648` | Undo changes only local MCP JSON state, then marks the action cancelled. Provider-origin writes can remain live remotely while the receipt claims Undo succeeded. | Provider-aware compensating operation through the canonical writer/outbox; verify provider readback before success. Use `undo_completed`, not overloaded `cancelled`. | Notion/Sheets create/update/archive Undo round trips; failed delivery stays pending/failed and never says applied. |
| P1-05 | `server/src/agents/verifier.ts:15-27`, `server/src/agents/verifier.ts:126-139`; `server/test/canonical-verification.ts:89-99` | Verifier accepts supplied actual status/IDs but does not compare them before returning verified. Test explicitly expects verified for `actualStatus: failed`, empty IDs and unbound source. | Verify expected tool, completed status, exact record IDs/revisions, source requirements, undo payload and provider receipt from canonical reread. | Existing false-positive test inverted; mutation, stale revision, failed status, missing source and provider mismatch all deny. |
| P1-06 | `server/src/mcp/state.ts:195-244` and equivalent JSON stores in conversations, health, checkpoint, package-registry and reactive runtime | Invalid/truncated JSON silently becomes empty state; writes overwrite the target directly. Crash/concurrency can erase data with no surfaced recovery path. | Prefer SQLite transactions/WAL for durable server state. Interim: temp file, fsync, atomic rename, file lock/single writer, checksum, backup, quarantine corrupt files, visible degraded health. | Kill-during-write, concurrent-writer, corrupt-file and recovery tests; no silent empty reset. |
| P1-07 | `server/src/index.ts:102-111`, `server/src/index.ts:197-203` | JSON/raw bodies buffer without limits, deadlines or early content-length rejection. Memory exhaustion is trivial. | Use framework body limits or bounded streaming parser; route-specific sizes; request/header/time limits; 413 response. | Oversize/chunked/slow body tests; bounded process memory; normal webhook/chat requests pass. |
| P1-08 | `server/src/index.ts:49-52`, `server/src/index.ts:1038-1066`, `server/src/index.ts:1236-1264` | Idempotency cache is global and keyed only by client string. A collision can replay another conversation’s answer into the current conversation; state vanishes on restart. | Scope key by authenticated principal + endpoint + conversation + operation; persist request fingerprint and response atomically; reject same key/different fingerprint. | Cross-user/cross-conversation/cross-route collision tests; restart replay; mismatch returns 409. |

### P2

| ID | Exact evidence | Defect and impact | Required fix | Proof |
|---|---|---|---|---|
| P2-01 | `server/src/chat.ts:104-107`; `server/src/chat-runtime.ts:69-78`, `server/src/chat-runtime.ts:116-133` | Untrusted `plan_hint` replaces the text used for mutation detection, policy and planning, but executor receives visible message text. Hidden command can cause action/text mismatch. | Remove external plan hints or treat as non-authoritative UI suggestion. Derive intent from canonical user message; bind plan digest to executed command. | Adversarial hint/message matrix never mutates outside visible intent. |
| P2-02 | `server/src/chat.ts:114-117`, `server/src/chat.ts:440` | Caller supplies arbitrary `previous_response_id`, forwarded to model provider without server-side conversation ownership validation. | Store provider response IDs server-side and accept only an opaque server continuation token bound to principal/conversation/model. | Foreign, stale and malformed IDs rejected; valid continuation works after restart. |
| P2-03 | `server/src/agents/retrieval.ts:79-88`, `server/src/agents/retrieval.ts:127-140` | Every chat pull queries live Notion and Sheets and flattens up to 12 arbitrary properties into the model prompt. This expands secret/PII exposure, latency and provider load. | Schema-driven safe projection, sensitivity tags, query routing, cache/ETag, provider timeout/circuit breaker, explicit source budget. | Secret-canary never reaches prompt/log; irrelevant provider is not called; latency/load budgets pass. |
| P2-04 | `server/src/providers/notion/pull.ts:281-303` | Notion pull reads one page only and ignores cursor/`has_more`; records beyond page size disappear from sync/retrieval. | Implement bounded pagination with cursor checkpoint, rate-limit/backoff and partial-sync status. | 250+ row fixture; interrupted resume; no duplicates/omissions. |
| P2-05 | `server/src/index.ts:56`; `server/src/kernel/install-reactive-runtime.ts:120-142` | Runtime installs an observer, but normal server startup never schedules or drains the reactive outbox. Proposals can remain forever. | Durable worker lifecycle: startup recovery, periodic/event wake, lease, retry/backoff, dead-letter, health metrics, graceful shutdown. | Restart and transient-failure tests; eventual drain; one active lease. |
| P2-06 | `server/src/kernel/reactive-proposal-executor.ts:88-94`; `server/src/kernel/reactive-outbox.ts:150-164` | Review-required proposal returns `ok: true` with queued action; outbox interprets any `ok` as ack and removes it from runnable work. Approval cannot resume it through this queue. | Use explicit execution result states (`executed`, `awaiting_review`, `retry`, `terminal_failure`); only executed/terminal outcome acked; approval event requeues exact proposal. | Review proposal remains pending, survives restart, executes once after valid approval. |
| P2-07 | `server/src/kernel/operation-observer.ts:20-26` | Observer errors are swallowed without telemetry or durable replay. Canonical write succeeds, but reactive consequences silently vanish. | Canonical transaction writes an outbox event; worker consumes it. At minimum log redacted structured failure and expose degraded health. | Forced observer failure appears in health/metrics and replays after recovery. |
| P2-08 | `android/app/build.gradle:107-131`; `scripts/android/install-play-proof-pack.sh:5-20` | Debug/release signing and “Play proof” language are ambiguous; the helper installs a debug artifact on a physical device. Export/build evidence is not store-signed release evidence. | Explicit dev/internal/release variants; release fails without release key; rename proof script; provenance includes package, variant, signer digest, commit and device target. | Release artifact certificate allowlist; in-place install only to explicit serial; unsigned/debug artifacts cannot satisfy release gate. |
| P2-09 | `scripts/validate-domain-config.mjs:1-48`; dependency `ajv` in `package.json` | Config validation is hand-rolled despite Ajv being installed. Coverage can drift from JSON Schema semantics and referenced schemas. | Compile all schemas with Ajv strict mode/formats; resolve `$ref`; validate catalog, manifests, workflows, templates, agent registry and examples; cache validators. | Mutation corpus proves required/type/enum/ref/additional-property/version failures. |
| P2-10 | `tests/helpers/memory-db.ts:1-196` | Golden/kernel tests use a permissive fake SQL interpreter. It cannot prove SQLite constraints, transactions, locking, migrations, query plans or JSON behavior. | Use temporary real SQLite for persistence/contract tests; keep fake only for pure unit tests. | Same suite against real schema; FK/unique/transaction/migration failure tests. |
| P2-11 | `server/src/mcp/state.ts:145-151` and agent/retrieval scoring paths | Confidence/evidence semantics are inconsistent: some paths hardcode/null/default scores without a shared calibrated contract. | One provenance schema: confidence nullable, method and evidence required when non-null, no UI/model implication that heuristic rank is truth confidence. | Schema/property tests and copy review; no fabricated confidence. |
| P2-12 | `FEATURES.md`, `README.md`, `ROADMAP.md`, `docs/lifeos/implementation-ledger.md`, `docs/release/RELEASE_CHECKLIST.md` | Claims and evidence are spread across historical ledgers and can conflict with current code. Several fixture workflows and proof outputs look product-like. | Generate capability matrix from config + current evidence manifest; mark fixture/dev-only artifacts; archive superseded claims; doc lint for status vocabulary and evidence provenance. | One generated current-state report; no “shipped/complete” without current receipt. |
| P2-13 | live provider/device proof scripts under `scripts/quality/` and `scripts/android/` | Live tokens, external records and physical device targets create accidental mutation/leak risk if broad gates run automatically. | Separate hermetic/default, sandbox-live and release-device lanes. Require explicit target IDs, test tenant markers, bounded cleanup and secret redaction. | Default CI cannot call live services/device; live lane refuses production-looking target and records cleanup receipt. |

## Cross-cutting ambiguity, antipattern and risk ledger

### Product/config ambiguity

- The stated north star is “every screen controlled by config,” but route files still exist as individually authored screens. V1 needs a measured config-coverage report, not a slogan.
- `food` is active; `health` and `plants` are preview. Preview domains must not inherit Food-specific prompt, collection, or policy assumptions.
- `phase4_*` workflows are fixtures mixed into the production workflow directory.
- “Local-first” is ambiguous while server MCP uses a parallel JSON state instead of the app canonical SQLite model.
- “Direct reversible writes” conflicts with policy values named `review-required`.
- “Undo” is ambiguous across local rollback, provider compensation, workflow compensation and action cancellation.

### Architecture antipatterns

- Multiple writers and state models.
- Authorization scattered across route, MCP and policy helpers.
- Boolean policy combinations permit invalid states.
- In-memory coordination maps for durable semantics.
- Direct synchronous filesystem persistence in request paths.
- Advisory callbacks for required downstream work.
- Provider fetch on every retrieval request.
- Test fixtures co-located with deployable config.
- Evidence scripts treated as product correctness instead of focused receipts.

### Security/privacy

- P0 unauthenticated private resources.
- Fail-open deployment auth.
- No explicit principal/tenant binding in state keys and resource URIs.
- Broad provider properties sent to model context.
- No documented resource-level authorization matrix.
- Live proof credentials/targets need stronger isolation.
- Health data requires domain-specific minimization, retention, export and deletion policy.

### Scalability/reliability

- Whole-file JSON stores create O(n) writes, lock contention and crash loss.
- Global record/action indexes return hundreds of full objects.
- No body limits or backpressure.
- Notion pagination absent.
- In-memory idempotency/run state fails on restart and multi-instance deployment.
- Reactive queue has no autonomous worker lifecycle.
- Provider calls lack one consistent cache/rate/circuit policy.

### Testing gaps

- No adversarial auth/resource enumeration suite.
- No policy state-machine property tests.
- No cross-ingress writer-parity suite.
- Verifier test encodes a false positive.
- No crash/concurrency persistence tests.
- Fake database overstates persistence confidence.
- No tenant/cross-conversation isolation tests.
- No large dataset/pagination/backpressure tests.
- Visual/export tests do not prove signed-store release.
- Historical receipts need current-tree provenance regeneration after remediation.

### Documentation/release gaps

- Capability claims, implementation ledger, release checklist and audit evidence lack one generated authority.
- No threat model/data-flow inventory for app, server, MCP, providers, model and Health Connect.
- No operator runbook for corrupt state, stuck outbox, provider partial sync, key rotation or rollback.
- No explicit supported deployment topology and trust boundary.
- Debug/internal/release artifact terminology is inconsistent.
- `fd8e2e6` must be recorded as a four-item patch, not “audit closure.”

## Use mature components; avoid reinvention

Adoption requires a short ADR and contract tests. Do not replace working code merely for novelty.

| Need | Default choice | Replace/avoid |
|---|---|---|
| HTTP limits, schemas, errors, lifecycle | Fastify with route schemas, `bodyLimit`, hooks and plugins; alternatively keep Node HTTP only behind a rigorously tested bounded adapter | Hand-buffered request bodies and scattered response/auth helpers |
| Runtime validation | Ajv strict mode for JSON Schema; Zod only for TypeScript-native internal inputs | Parallel hand-written partial validators |
| Durable server state/idempotency/outbox | SQLite (`better-sqlite3` for single-process or a proven async driver) with WAL, transactions and migrations | Whole-file JSON stores and process-global maps |
| Background execution | Durable SQLite job/outbox worker first; BullMQ only if Redis/multi-node is actually required | Home-grown in-memory polling/ack semantics |
| State machines | Existing XState for approval/workflow lifecycle where visualization/exhaustiveness helps | Boolean combinations such as `allowed + requiresClarification + safety` |
| Observability | OpenTelemetry APIs plus structured redacted logger such as Pino | Empty catches and ad hoc console evidence |
| Rate/retry | `p-retry`/`p-limit` or framework equivalents with provider-specific policy | Unbounded fan-out and bespoke retry loops |
| Secret detection | Gitleaks in CI/pre-commit; secret-canary prompt tests | Grep-only confidence |
| Security regression | OWASP ZAP baseline for exposed HTTP plus focused contract tests | Manual “looks local” assumptions |

## Hard-things-first remediation plan

Do not parallelize two agents on the same files. Maximum three implementers plus one integration owner. Each lane starts from the same clean checkpoint. Integration owner alone resolves overlaps and updates this registry.

### Phase 0 — freeze truth and prove exploits

Goal: convert each P0/P1 into a failing deterministic test before architectural edits.

- Add auth/resource enumeration, review-without-write, executor parity, provider Undo, false verifier, corrupt-state, oversize-body and cross-conversation idempotency tests.
- Add evidence manifest containing commit, dirty-state hash, command, environment class, artifact hash and result.
- Classify every config/workflow file as product, preview or fixture.
- Stop conditions: no live provider/device calls; no production data copied into fixtures; no remediation begins without a red test for its defect.

Checks:

```sh
npm run config:validate
npm run typecheck
npm run test
npm run test:server:direct
```

### Phase 1 — P0 containment and authentication substrate

Dependencies: Phase 0. Blocks every external/server release.

1. Fail startup when a non-loopback listener lacks auth configuration.
2. Create one authenticated principal contract.
3. Apply auth before every MCP list/read/call and HTTP protected route.
4. Add resource-level authorization/projection. Remove global private indexes from default resource listing.
5. Redact identifiers/bodies from auth errors and logs.

Stop condition: anonymous or cross-principal resource read succeeds.

### Phase 2 — one policy machine and one writer

Dependencies: Phase 1. Blocks AI/MCP/workflow mutation.

1. Replace policy booleans with exhaustive decision states.
2. Define signed/digested approval contract bound to principal, action, target, before revision, expiration and idempotency key.
3. Route chat executor and MCP tools through the canonical operation engine.
4. Enforce schema, capability, domain/collection, revision, provider authority and idempotency at that boundary.
5. Emit receipt and outbox in the same transaction.

Stop conditions: any ingress imports low-level state mutation; review/clarify path changes state; same command differs by ingress.

### Phase 3 — durable state, isolation and bounded HTTP

Dependencies: Phase 1 contract; can overlap late Phase 2 only in isolated files.

1. Inventory JSON stores and define SQLite tables/migrations for MCP state, conversations, health snapshots, workflow checkpoints, package registry, reactive runtime and idempotency.
2. Provide one-time import with backup, checksum, quarantine and restart-safe marker.
3. Persist tenant/principal/conversation-scoped idempotency and run state.
4. Install bounded body parsing, timeouts and request cancellation.
5. Add backup/restore and corrupt-state operator runbook.

Stop conditions: invalid state silently resets; same idempotency key crosses scope; oversize request exceeds configured bound.

### Phase 4 — truthful verification, Undo and provider authority

Dependencies: Phases 2–3.

1. Verifier rereads canonical action/record and checks expected outcome.
2. Provider mutations remain incomplete until provider delivery/readback receipt.
3. Undo becomes a compensating canonical operation, not direct JSON mutation.
4. Separate action lifecycle: queued, awaiting_review, executing, completed, undo_pending, undone, failed, dead_letter.
5. Make provider mismatch visible and retryable; never claim local-only success as provider success.

Stop condition: any failed/unbound/missing write verifies; provider Undo reports success before readback.

### Phase 5 — reactive and workflow runtime

Dependencies: Phases 2–4.

1. Transactional operation outbox replaces advisory observer dependence.
2. Worker gets lease, startup recovery, bounded concurrency, backoff, dead letter, metrics and graceful stop.
3. Review-required proposals stay pending and resume from a bound approval event.
4. Workflow configs compile before activation; fixture workflows move outside production registry.
5. Replay and compensation use canonical operations and provider-aware Undo.

Stop condition: queued work can be acked without execution/terminal disposition.

### Phase 6 — provider retrieval, pagination and privacy

Dependencies: principal contract and durable cursors.

1. Add sensitivity/AI-exposure metadata to schema fields.
2. Project only query-relevant allowed fields into model prompts.
3. Route provider queries; add cache, timeout, concurrency, rate and circuit policies.
4. Implement Notion pagination/checkpoint resume and Sheets large-range paging.
5. Add source freshness, partial-result and stale-cache semantics to answer citations.

Stop conditions: secret-canary reaches model/log; partial provider pull presented as complete.

### Phase 7 — config-driven product completeness

Dependencies: stable policy/writer/runtime contracts.

1. Generate a config coverage map for every route, component, copy string, action, query, empty/error/loading state and permission.
2. Move reusable surface definitions into versioned manifests. Keep renderer primitives in code.
3. Make domain packages install/preview/activate/rollback atomically.
4. Ensure Food, Health and Plants use the same renderer/command contracts without domain-name branches.
5. Add config migrations, compatibility range, signatures/checksums, last-known-good and safe-mode UI.
6. AI config authoring must produce diff, schema result, conflict/migration classification and rollback receipt before activation.

Stop condition: adding the reference fourth domain requires editing a feature route or mutation policy.

### Phase 8 — test system and documentation authority

Dependencies: phases 1–7 contracts frozen.

1. Replace persistence goldens with real temporary SQLite.
2. Add property/fuzz tests for config, policy, idempotency, workflow replay and malformed model/tool payloads.
3. Add multi-instance/restart/crash/load suites.
4. Generate feature/status docs from config and evidence manifest.
5. Write threat model, data flows, deployment topology, recovery, key rotation, incident and provider partial-sync runbooks.
6. Mark all historical evidence stale until regenerated on the integrated commit.

### Phase 9 — release proof

Dependencies: no open P0/P1; all P2 accepted or explicitly deferred with owner and non-safety rationale.

Required repository gates:

```sh
npm run config:validate
npm run typecheck
NPM_CONFIG_CACHE=/tmp/wonderfood-npm-cache npm run doctor
npm run export:web
npm run export:android
npm run phase3:check:chat-send
npm run phase3:check:chat-rollback-idempotency
```

Also run the complete hermetic security, server, config, operation, workflow, provider-contract, accessibility, responsive, performance and release-readiness suites introduced above.

Live proof is separate:

- Disposable provider tenant/data only.
- Explicit physical device serial only when authorized.
- Certificate-verified in-place Android update; no uninstall/data clear/downgrade.
- Signed Android release and iOS store/TestFlight proof use current commit artifacts.
- Receipts record cleanup and contain no tokens.

Stop condition: an export, debug APK, unsigned AAB, old screenshot or historical JSON receipt is used as store-release proof.

## Agent execution matrix

Use frontier GPT/Codex for security architecture, policy/writer convergence, data migration, provider correctness and integration review. Use GPT-5/4-mini only for bounded mechanical work with frozen contracts: test matrices, fixture generation, config/docs lint, copy review and evidence indexing.

| Lane | Best model | Owns | Forbidden files | Mandatory checks/report |
|---|---|---|---|---|
| A: auth/MCP containment | Frontier | `server/src/mcp/official-server.ts`, `server/src/mcp/auth.ts`, resource authorization tests | State, providers, app UI, domain schemas | Anonymous/cross-principal suite; `P0-01` report |
| B: policy/canonical writer | Frontier | policy decision types, command bus, executor/tool adapters | Provider adapters, UI, release scripts | review-zero-write; ingress parity; receipt contract |
| C: persistence/idempotency/HTTP | Frontier | server persistence module, migrations, bounded request adapter | Domain manifests, UI, provider behavior | crash/concurrency/restart/413 suite; migration receipt |
| D: verifier/Undo/providers | Frontier | verifier, compensations, provider delivery/readback | Auth, UI, config rendering | false-verifier matrix; Notion/Sheets Undo contracts |
| E: reactive/workflows | Frontier or strong Codex | outbox worker, proposal lifecycle, workflow registry | Auth, app UI, provider client internals | restart/retry/review/dead-letter tests |
| F: retrieval/privacy/pagination | Frontier | safe projection, routing, Notion/Sheets paging | Writer/policy, UI | secret-canary, 250-row, latency/load report |
| G: config coverage/product | Frontier architecture + mini mechanical | config compiler, coverage report, renderer contracts, fourth-domain fixture | Server security internals, shared contract changes without integration approval | config mutation corpus; zero-feature-route fourth domain |
| H: test/docs/release evidence | GPT-5/4-mini after contracts freeze | real-DB test migration, generated matrices, docs, evidence manifest | Product runtime logic, auth/policy, signing secrets | full hermetic gate and evidence provenance |
| Integration owner | Frontier | merge order, shared contracts, migrations, final evidence | No speculative product features | inspect every diff; rerun all gates; update finding dispositions |

Every worker prompt must include:

- Exact checkout/branch/base commit.
- Owned files and forbidden files.
- Finding IDs.
- Required red tests and green checks.
- Report path with sources consulted, exact changes, commands/results, known gaps and commit hash.
- “Do not reset/clean, overwrite unrelated work, print secrets, push, deploy, call live providers, or touch a physical device.”

Integration order:

1. A.
2. B and C behind frozen principal/decision/operation contracts.
3. D.
4. E and F.
5. G.
6. H.
7. Integration audit and release proof.

## Acceptance definition: “best V1”

V1 is accepted only when all are true:

- Registry has zero open P0/P1.
- Every P2 is closed or documented as a deliberate V1 deferral with owner, user impact, mitigation and target; no security/data-integrity P2 may be deferred.
- Authentication fails closed; authorization is principal/resource/action scoped.
- Every mutating ingress reaches the same policy and canonical transactional writer.
- Review/clarify/preview paths prove zero mutation.
- Idempotency is durable, scoped and fingerprint-bound.
- Verification cannot pass a failed, stale, unbound or provider-incomplete action.
- Undo is truthful and provider-aware.
- State survives malformed files, crash, restart, concurrency and migration without silent loss.
- HTTP and queues are bounded; provider pagination and partial failures are explicit.
- Model prompts receive only allowlisted, relevant, source-attributed fields.
- Every product surface/state/action is config-derived or explicitly documented as a renderer/platform primitive.
- A fourth reference domain installs and renders without product-route edits.
- All config artifacts validate with strict JSON Schema and compatibility/migration checks.
- Hermetic tests use real persistence where persistence behavior matters.
- Web/native accessibility, responsive, offline, empty, loading, conflict, error, large-data and performance matrices pass.
- Current-tree provenance exists for every claim.
- Store release evidence proves current signed artifacts, not exports/debug builds.
- Threat model, recovery, deployment, privacy, provider and release runbooks are current.
- Independent final audit finds no reproducible safety defect, false claim, silent failure, obvious foot gun or cheaper proven component that should replace custom critical infrastructure.

Until then, call the system **pre-V1 hardening**, not complete.
