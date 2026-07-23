# LifeOS implementation ledger

## Progress (2026-07-22)

| Phase | Owner | Status | Evidence | Blocker | Next action |
|---|---|---|---|---|---|
| 0 — Architecture and contracts | Agent A (Canonical schemas + domain runtime) | DONE (PASS) | `3db1f9b`, `f849188`, `docs/lifeos/expo-implementation-plan.md`, `docs/lifeos/product-pass.md`, `docs/lifeos/adr-0001-architecture.md`, `packages/domain-config/*` | Server package host still pending for final ADR closure | Decide final server host in Phase 3 before closing ADR |
| 1 — SQLite canonical runtime | Agent A | DONE (PASS) | `src/domain/{catalog,runtime,surface,queries,renderer}.ts`, `src/db/{migrations,provider,provider.native.tsx,provider.web.tsx,records,conversations,sources,outbox,actions,undo,seed}.ts`, `app/_layout.tsx`, `package.json`, `server/package.json`, `scripts/quality/check-phase1-sqlite-runtime.sh`, `docs/lifeos/implementation-workstreams.md`, `bddbf0e`, `1f8c0d8`, `b098158`, `2026-07-22: npm run config:validate`, `2026-07-22: npm run typecheck`, `2026-07-22: npm run export:web`, `2026-07-22: npm run export:android`, `2026-07-22: web dev SQLite migration/seed smoke` | Runtime and web worker are live; fixed the records upsert placeholder mismatch exposed by web SQLite. Checked-in native folders are authoritative for Health Connect, so Expo's non-CNG sync check is explicitly disabled | Continue with phase-2 renderer hardening |
| 2 — Generic domain renderer | Agent G (Expo UX) | PARTIAL | `src/domain/{surface,queries,renderer}.tsx`, `app/(tabs)/food.tsx`, `app/search.tsx`, `app/record/[id].tsx`, `app/sources.tsx` | Empty/permission states still need stronger domain-agnostic copy and actions in `search`, `record`, and `sources` when DB is null or empty | Finish domain-agnostic renderer integration for remaining phase-2 screens and empty/loading states |
| 3 — Server and real chat | S2 | DONE (PASS) | `server/src/{chat.ts,chat-storage.ts,conversations.ts,index.ts,provenance.ts,agents/{orchestrator,verifier,retrieval}.ts}`, `src/chat/*.ts`, `scripts/quality/check-phase3-chat-send.ts`, `scripts/quality/check-phase3-chat-rollback-idempotency.ts`, `scripts/quality/check-phase3-chat-undo.ts`, `server/src/providers/openai.ts`, `server/src/chat.ts`, `src/chat/client.ts`, `docs/lifeos/contracts/chat-contracts.md`, `server/test/multiturn-conversation-contract.ts`; live two-turn OpenAI proof on 2026-07-22 | Canonical action payloads, model selection, stream/replay, and restart-safe multi-turn context are covered; Notion/Sheets runtime and UI polish remain | Continue phase 4/5/6 adapter parity |
| 4 — MCP parity | DONE (PASS) | `server/src/mcp/{server.ts,tools.ts,resources.ts,auth.ts,policy.ts}`, `server/src/workflows/{checkpoint.ts,compensation.ts}`, `server/src/mcp/state.ts`, `packages/domain-config/workflows/phase4_replay_workflow.v1.json`, `packages/domain-config/workflows/phase4_compensation_probe.v1.json`, `scripts/quality/check-phase4-mcp-tool-contract.ts`, `scripts/quality/check-phase4-mcp-workflow-replay.ts`, `scripts/quality/check-phase4-mcp-workflow-replay-http.ts`, `server/test/mcp-resource-contract.ts`, `docs/lifeos/contracts/mcp-contracts.md` | Local replay/resource gates pass; hosted `/mcp` initialize, tools/list, resources/list, and catalog resources/read pass against `lifeos-api.thetechcruise.com` | Keep hosted MCP auth/tunnel monitoring as an operational release gate |
| 5 — Notion adapter | PASS (LIVE PULL + CHAT; WEBHOOK READY) | `server/src/providers/notion/*`, `server/src/providers/sync/notion.ts`, `server/src/providers/webhooks/notion.ts`, `server/src/index.ts`, `server/test/provider-webhook-ingress.ts`, `scripts/quality/check-phase5-notion-adapter.ts`; disposable live evidence at `app/build/evidence/live-workspace/notion_scenarios-1784760281.json`; hosted pull/chat proof against Food Items data source `3a7dace3-e35e-4ce9-b817-0b80af6e413c` | Hosted pull and source-grounded chat pass; Notion webhook signing secret is not configured in the hosted environment | Provision `NOTION_WEBHOOK_SECRET`, register the endpoint, and run signed webhook → pull → canonical proof |
| 6 — Google Sheets adapter | PASS (ADAPTER + LIVE CANONICAL WORKBOOK + HOSTED) | `server/src/providers/sheets/*`, `server/src/providers/sync/sheets.ts`, `server/src/providers/webhooks/sheets.ts`, `server/src/providers/sheets/workbook.ts`, `server/test/provider-sync-sheets.ts`, `server/test/provider-webhook-ingress.ts`, `scripts/quality/check-phase6-sheets-adapter.ts`; approved workbook `1WpEwm07ApcnuiLDVhzl8vy4D5kU8KjmtbAVC4qLphcU` has `LifeOS Canonical` rows mirrored from Notion; hosted pull returns 4 canonical Food records; authenticated webhook ingress and Chat citations pass | Hosted runtime is Mac-backed and depends on private OAuth refresh material; no managed always-on host yet | Keep the authenticated route monitored; migrate to managed secret storage/host when productionizing |
| 7 — Commands, Undo, workflows | PASS (SERVER + CLIENT CONTRACT + WEB) | `packages/domain-config/schemas/{command.v1.schema.json,action-event.v1.schema.json,agent-handoff.v1.schema.json,undo.v1.schema.json}`, `src/actions/{policy.ts,engine.ts,undo.ts}`, `server/src/mcp/{tools.ts,state.ts}`, `server/src/workflows/{runner.ts,checkpoint.ts,compensation.ts}`, `scripts/quality/check-phase7-chat-client-cross-surface.ts`; evidence at `app/build/evidence/phase7-chat-client-cross-surface-proof.json`; web live chat verified at `http://127.0.0.1:8094` | Native UI action receipt rendering and Android device proof remain outside this server/runtime slice | Carry the same contract into Android/emulator QA |
| 8 — Android completion | Agent G | IN PROGRESS | Expo native project generated under `android/`; release APK `android/app/build/outputs/apk/release/app-release.apk` launches on fresh API 34 emulatorx and renders the Today dashboard (`app/build/evidence/emulatorx-healthconnect-release-after-sqlite-fix.png`); migration WAL setup is now outside the SQLite transaction (`bd9ef6b`); `wonderfood://health-connect` now routes through `MainActivity` to the system Health Connect controller; API 34 emulatorx onboarding deep-link proof is captured at `app/build/evidence/emulatorx-health-settings-deeplink.png`; `src/health/connect.ts`; Health Connect read-only manifest permissions and permission delegate; authenticated snapshot sync routes in `server/src/health/snapshots.ts`; `scripts/quality/check-health-connect-android.sh` PASS | Emulatorx permission-denied and package-granted branches are verified; app-specific read/export/delete, background scheduling, and visible settings trigger remain open | Wire the existing bridge into a visible settings action and exercise real Health Connect reads |
| 9 — Polish/perf/iOS | Agent G | BLOCKED | no production runtime yet | earlier phases incomplete | Hold |

## Active sub-agent roster

Current explicit phase-slice agents are defined in `docs/lifeos/implementation-sub-agents.md` and are binding for new edits:

- S1: Canonical schemas, SQLite, domain runtime (Phases 0–1)
- S2: Chat + Responses + Conversations + citations + internal agents (Phase 3)
- S3: Notion adapter + webhooks (Phase 5)
- S4: Google Sheets adapter (Phase 6)
- S5: MCP + action engine + Undo + workflows (Phases 4,7)
- S6: Expo UI + accessibility + responsive QA (Phases 2,9)
- S7: Android + Health Connect + EAS (Phase 8)

## Required evidence log

- `3db1f9b` checkpoint committed before and includes baseline SQLite/manifest scaffolds.
- `docs/lifeos/expo-implementation-plan.md` read and mapped to file-level contracts.
- `docs/lifeos/product-pass.md` read and used for acceptance framing.
- `docs/lifeos/adr-0001-architecture.md` created.
- `src/db/migrations.ts` now enforces v1 schema and rollback/export helpers.
- `src/db/provider.tsx` routes DB context through a shared web-safe provider path, and `src/db/provider.web.tsx` now uses `SQLiteProvider`.
- `server/package.json` now uses `tsx` to run the TypeScript server entrypoint.
- `server/src/index.ts` now uses ESM-safe `http` import so the server boots under `tsx`.
- `metro.config.js` adds `.wasm` asset support for Expo SQLite web worker bundling; web export now succeeds with `SQLiteProvider` on web.
- `tsconfig.json` includes Node typings for server-side route compilation, and `package.json` added `@types/node`.
- `server/src/conversations.ts` now uses file-backed persistence (`server-data/conversations.json` by default) with recovery load and safe replay of conversation rows across process lifetimes.
- `docs/lifeos/implementation-workstreams.md` now defines explicit ownership, contracts, anti-pattern guards, and mandatory report fields per workstream.
- `docs/lifeos/implementation-sub-agent-kickoff-2026-07-22.md` formalizes explicit S3/S4/S5/S2 phase-slice ownership, contracts, forbidden edits, and acceptance gates.
- `bddbf0e` checkpointed UI runtime write-path correctness and search render typing fix.
- `1f8c0d8` removed fixture-backed fallbacks from domain query entrypoints and preserved empty-state behavior when DB is unavailable.
- `696a91f` implemented chat/server baseline + phase-1 gates rerun.
- `2026-07-22` added command/action/agent-handoff/undo JSON schemas and `src/actions/{policy.ts,engine.ts,undo.ts}` runtime skeleton.
- `2026-07-22` executed `server-chat` hardening (chat retrieval/provenance/orchestrator/openai endpoint idempotency).
- `2026-07-22`: `npm run config:validate` ✅ — Domain config valid (3 domains, 12 Food collections, 3 workflows).
- `2026-07-22`: `npm run typecheck` ✅ after adding `@types/node` and Node TS lib coverage.
- `2026-07-22`: `npm run doctor` first hit the local cache-path defect (`/Users/srinivasvaddi/.npm`, ENOTDIR), then ran with the safe temp cache; checked-in native folders are authoritative for Health Connect and the non-CNG sync check is explicitly disabled in `package.json`; current result is 19/19.
- `2026-07-22`: `npm run export:web` ✅ (static routes exported) after `metro.config.js` wasm asset wiring.
- `2026-07-22`: `npm run export:android` ✅.
- `2026-07-22`: `97e9dc1` + follow-up run/check (same day) improved `/chat/send` and `/chat/retry` lifecycle; run state now appended user turns and deletes active-run state deterministically in both paths.
- `2026-07-22`: `7bbe263` completed chat action-receipt completion flow and persisted retry/user append consistency in server chat routes.
- `2026-07-22`: `691fc88` added conversation continuity and active-run introspection (`/chat/run`), plus agent handoff telemetry in server chat responses for Workstream B traceability.
- `2026-07-22`: `server/src/conversations.ts` persistence/reload smoke checks (via `tsx` script + `LIFEOS_CHAT_CONVERSATIONS_PATH`) confirm stored threads and restartable load of `diag-thread-3` user messages.
- `2026-07-22`: `src/chat/client.ts`, `server/src/chat.ts`, and `server/src/providers/openai.ts` replaced deterministic fallback meal-style content with explicit offline/live-unavailable messaging and empty structured payloads when model text is unavailable; `npm run typecheck` passes.
- `2026-07-22`: `server/src/index.ts` stream path now emits cache responses in a stable `response` envelope; `server/src/providers/openai.ts` and `src/chat/client.ts` now keep streaming token path aligned so cache/replay and run-end payloads resolve to server results.
- `2026-07-22`: `src/db/seed.ts` no longer injects demo conversation/action records in seed mode; only sample records remain for fixture-driven developer data.
- `2026-07-22`: `app/(tabs)/chat.tsx` now starts from empty conversation state when no persisted threads exist, instead of hardcoded demo summaries.
- `2026-07-22`: `server/src/agents/retrieval.ts` removed seed-backed retrieval snapshots to prevent fabricated citation source inference.
- `2026-07-22`: `server/src/chat.ts` now normalizes both JSON and plain-Markdown model replies into deduplicated structured answer cards, strips raw Markdown markers, and keeps the assistant bubble from duplicating the answer card; emulator proof at `app/build/evidence/emulator-chat-final6.png` shows a clean table/answer flow with source cards.
- `2026-07-22`: hosted Google Sheets webhook ingress now requires `Authorization: Bearer LIFEOS_SERVER_TOKEN`; unauthenticated public request returned 401 while hosted health remained 200. Local ingress and retry contracts remain PASS.
- `2026-07-22`: hosted LaunchAgent now refreshes the private Google OAuth access token from its refresh token before starting the connector; after restart, authenticated live Sheets pull returned 4 records and unauthenticated webhook ingress remained 401.
- `2026-07-22`: Responses web search is now an opt-in/intent-triggered server capability; direct and streamed `url_citation` annotations normalize into deduplicated clickable chat source cards. Contract proof: `app/build/evidence/phase3-web-search/phase3-web-search-proof.json`.
- `2026-07-22`: hosted authenticated chat query “latest food safety guidance” completed with a live FDA URL citation after the web-search timeout/prompt guard; proof: `app/build/evidence/live-workspace/web-search-live-1784767810.json`.
- `2026-07-22`: `npm run phase3:check:chat-send` ✅ validated `/chat/send` + `/chat/send/stream` envelopes, event stream output (`run.start`, `token`, `run.end`), and action source-id preservation; proof at `app/build/evidence/phase3-chat-send/phase3-chat-send-proof.json`.
- `2026-07-22`: `npm run phase3:check:chat-rollback-idempotency` ✅ validated `/chat/undo` idempotent replay semantics with repeated same-key requests; proof at `app/build/evidence/phase3-chat-rollback-idempotency/phase3-chat-rollback-idempotency-proof.json`.
- `2026-07-22`: `npm run phase3:check:chat-undo` ✅ validated `/chat/send` → `/chat/undo` action reversal by creating a mutating action, confirming pre-undo record creation in MCP state, running `/chat/undo`, and confirming record removal; proof at `app/build/evidence/phase3-chat-undo/phase3-chat-undo-proof.json`.
- `2026-07-22`: `scripts/quality/check-phase1-sqlite-runtime.sh` includes copy-based process-death restore checks and explicit rollback smoke checks; script passes and confirms metadata/row persistence.
- `2026-07-22`: `server/src/agents/orchestrator.ts` now requires mutating intent before creating a command action, preventing non-write chat turns from returning `action.receipt`.
- `2026-07-22`: `server/src/agents/verifier.ts` now only includes `undo_ready` checks for non-`chat_reply` actions.
- `2026-07-22`: `server/src/mcp/{server.ts,tools.ts,resources.ts,auth.ts,policy.ts}` added Streamable-HTTP MCP endpoint with tool/resource parity and review-only action link/package helpers.
- `2026-07-22`: `server/src/mcp/server.ts` protocol alignment moved to `2026-03-11`, workflow tool dispatch now normalizes legacy workflow step aliases, and `server/src/mcp/resources.ts` resources now include deterministic domain-catalog and conversation-manifest URIs.
- `2026-07-22`: `server/src/workflows/checkpoint.ts`, `server/src/workflows/compensation.ts`, and `server/src/mcp/state.ts` now add checkpoint result persistence plus checkpoint-based workflow undo replay.
- `2026-07-22`: `server/src/mcp/tools.ts` fixed `run_workflow` create_record id handling, `scripts/quality/check-phase4-mcp-workflow-replay.ts` now demonstrates workflow replay + checkpoint + undo removing all created records, and `scripts/quality/check-phase4-mcp-workflow-replay-http.ts` validates the same replay flow through the Streamable-HTTP endpoint.
- `2026-07-22`: `scripts/quality/check-phase4-mcp-tool-contract.ts` now validates write envelopes, provider-rejection contracts, policy review-only behavior, and compensation rollback checks; proof at `app/build/evidence/phase4-mcp-tool-contract/phase4-mcp-tool-contract-proof.json`.
- `2026-07-22`: `npm run phase5:check:notion-adapter` ✅ adapter smoke contract check; proof at `app/build/evidence/phase5-notion-adapter/phase5-notion-adapter-proof.json`.
- `2026-07-22`: `npm run phase6:check:sheets-adapter` ✅ adapter smoke contract check; proof at `app/build/evidence/phase6-sheets-adapter/phase6-sheets-adapter-proof.json`.
- `2026-07-22`: `npm run config:validate`, `NPM_CONFIG_CACHE=/tmp/wonderfood-npm-cache npm run doctor`, `npm run export:web`, `npm run export:android`, `npm run phase3:check:chat-undo`, `npm run phase4:check:mcp-workflow-replay` ✅ all passed in one gate pass; proof at `app/build/evidence/phase3-chat-undo/phase3-chat-undo-proof.json` and `app/build/evidence/phase4-mcp-workflow-replay/phase4-mcp-workflow-replay-proof.json`.
- `2026-07-22`: `npm run phase4:check:mcp-workflow-replay:http` ✅ replay + undo over Streamable-HTTP; proof at `app/build/evidence/phase4-mcp-workflow-replay-http/phase4-mcp-workflow-replay-http-proof.json`.
- `2026-07-22`: Web dev runtime smoke ✅ at `http://127.0.0.1:8094`: Today seeded 4 Food records, Food workspace rendered all tabs/rows, Chat sent a live-runtime request and returned the server response; no browser console errors after fixing `src/db/records.ts` placeholder count and adding local-origin CORS/preflight handling in `server/src/index.ts`.
- `2026-07-22`: `npm run phase7:check:chat-client-cross-surface`, `npm run phase3:check:chat-send`, `npm run phase3:check:chat-rollback-idempotency`, and `npm run phase4:check:mcp` ✅ after web fixes.
- `2026-07-22`: Direct web record-route stability ✅ after memoizing `loadCatalog()`: three fresh navigations to `/record/pantry-yogurt` each resolved to the detail view with no reload loop; screenshot captured after data settled.
- `2026-07-22`: Android debug APK assembled and installed on the wireless S23U; launcher activity started and screenshot captured at `app/build/evidence/s23u-expo-native.png`. This is native shell proof only; Health Connect and bundled JS/device chat remain open.
- `2026-07-22`: Release APK assembled with bundled JS (`android/app/build/outputs/apk/release/app-release.apk`), installed successfully on S23U, launcher remained focused, and native Today dashboard screenshot captured at `app/build/evidence/s23u-expo-release.png`; default `npm run android` now uses the self-contained release variant, while Metro-backed development is explicit via `npm run android:dev`.
- `2026-07-22`: `react-native-health-connect@3.5.3` added with read-only Food/Health permissions (`Nutrition`, `Hydration`, `Steps`, `ActiveCaloriesBurned`, `Weight`), Android permission delegate/rationale intent, typed JS status/permission/snapshot bridge at `src/health/connect.ts`, minSdk raised to 26, and release APK rebuilt successfully; `npm run phase8:check:health-connect` ✅. S23U package `com.google.android.apps.healthdata` is installed, but device is currently locked so permission UI/read proof is intentionally not claimed.
- `2026-07-22`: fixed absolute-URL handling in `server/src/providers/notion/client.ts`; live `/providers/notion/pull?live=true` now returns 4 Food Items records, and live `/chat/send` quotes the canonical Tomatoes facts with a Notion source handle. Live disposable Notion scenario proof remains PASS at `app/build/evidence/live-workspace/notion_scenarios-1784760281.json`.
- `2026-07-22`: catalog gap closed: Health and Plants now have separate manifests and skills, `loadCatalog()` resolves all three packages, validator checks every domain package, and MCP exposes the registry plus domain skill/manifest resources.
- `2026-07-22`: added unauthenticated secret-safe `/providers/status` for client settings/diagnostics; it reports configured providers, canonical IDs, model, and API version without returning tokens.
- `2026-07-22`: webhook replay commit is now deferred until provider refetch + canonical apply succeeds for Notion and Sheets; `npm run test:server:webhook-retry` proves a transient Notion pull failure remains retryable.
- `2026-07-22`: added secret-free `.env.example` files for phone client/server setup; release APK uses the LAN server URL supplied at build time.
- `2026-07-22`: Health Connect now has authenticated `POST /health/connect/snapshot` and `GET /health/connect/snapshots` routes with bounded payloads, SHA-256 dedupe and local durable storage; `npm run test:server:health-snapshot` passes.
- `2026-07-22`: added typed Expo client helpers in `src/providers/sync.ts` for live Notion and Sheets pulls, sharing the server's provider result/source-snapshot contract.
- `2026-07-22`: refreshed the existing local Google OAuth cache without exposing token values; approved workbook metadata and `LifeOS Canonical` values read live; `server/src/agents/retrieval.ts` now merges Notion and Sheets provider snapshots so Chat source cards cite both surfaces.
- `2026-07-22`: release bootstrap no longer skips bundled sample rows outside `__DEV__`; sample sources are explicitly `sqlite`/`wonderfood://sample` so a fresh APK is not an empty shell or a fake Notion citation. Rebuilt and installed release APK with `EXPO_PUBLIC_LIFEOS_SERVER_URL=http://10.0.0.173:8790`; bundle inspection confirms both the LAN chat endpoint and sample bootstrap are present.
- `2026-07-22`: retrieval ranking now sorts merged local/provider snapshots by relevance, keeping matching Notion and Google Sheets citations first; live chat proof returned Tomatoes from both surfaces before unrelated rows (`c7dd56f`).
- `2026-07-22`: Sheets writes now enforce optimistic concurrency with optional expected version/digest tokens; stale direct writes return HTTP 409 and MCP update/archive calls pass the stored provider digest, with adapter tests proving conflicts perform no batch update.
- `2026-07-22`: durable multi-turn recovery now persists the latest OpenAI response pointer and injects a bounded structured summary of the last eight turns (including source labels) into the next model prompt; `server/test/multiturn-conversation-contract.ts` proves disk recovery and context assembly.
- `2026-07-22`: fixed hosted MCP static-resource resolution when launched with `npm --prefix server`; live resource reads previously returned `Missing resource file`, and root discovery now serves the catalog, Food skill, and schemas from the same paths as local MCP. Hosted initialize/tools/list/resources/list/resources/read then passed.
- `2026-07-22`: hosted conversation, MCP, Health Connect, and webhook replay state moved from volatile `/tmp` paths to `server-data/hosted/`; the launcher performs a one-time non-overwriting migration of prior proof state. A live read-only thread resumed after a hosted service restart from the durable conversation file.
- `2026-07-22`: emulatorx release smoke launched `com.wonderfood.app` successfully; Today dashboard screenshot captured at `app/build/evidence/emulatorx-release-durable.png`, and `READ_NUTRITION`, `READ_STEPS`, and `READ_WEIGHT` were present but `granted=false`, proving the permission-denied branch without touching S23U.
- `2026-07-22`: API 34 emulatorx exposed `com.google.android.healthconnect.controller` and its onboarding UI; the direct manage-permissions intent did not reach an app-specific grant page, so granted/read proof remains unclaimed.
- `2026-07-22`: fresh API 34 emulatorx release install initially showed the splash indefinitely; moving `PRAGMA journal_mode = WAL` outside the migration transaction fixed the startup gate. Fresh-install UI text, launcher activity, and Today screenshot now pass at `app/build/evidence/emulatorx-healthconnect-release-after-sqlite-fix.png` (`bd9ef6b`).
- `2026-07-22`: emulatorx API 34 package-level Health Connect grant branch passed for all five read scopes (`READ_NUTRITION`, `READ_HYDRATION`, `READ_STEPS`, `READ_ACTIVE_CALORIES_BURNED`, `READ_WEIGHT`); the app still needs a visible settings trigger and end-to-end record read/export proof.
- `2026-07-22`: added the native `wonderfood://health-connect` bridge and typed `openLifeOSHealthSettings()` helper; fresh API 34 emulatorx deep-link smoke opens the Health Connect onboarding controller (`app/build/evidence/emulatorx-health-settings-deeplink.png`).
- `2026-07-23`: Notion webhook ingress now acknowledges the unsigned subscription verification handshake without echoing or persisting the token; `npm run test:server:webhook-ingress`, retry, and webhook contract checks pass. Manual Notion connection verification and secret provisioning remain external setup.
- `2026-07-22`: external Cloudflare quick-tunnel proof passed `/health`, `/providers/status`, live Sheets pull, source-grounded `/chat/send`, and MCP `initialize`; the disposable tunnel was closed immediately after verification because it was not a managed authenticated deployment.
- `2026-07-22`: removed obsolete Kotlin package `com.example.wonderfood` from the S23U; only the current Expo package `com.wonderfood.app` remains. The app process and LAN server are healthy, but visual proof is still pending because the device is secure-locked/dozing.
- `2026-07-22`: created the named Cloudflare Tunnel `wonderfood-lifeos` with `lifeos-api.thetechcruise.com`, configured it to `127.0.0.1:8790`, enabled bearer auth for server/MCP calls, and verified authenticated external chat plus live Notion/Sheets citations. Rebuilt and installed the release APK with the hosted URL/token; bundle inspection confirms both values are present. The runtime is Mac-backed; a user-level service now keeps it running while logged in, with migration to an always-on host remaining optional.
- `2026-07-22`: promoted the named connector to a user-level LaunchAgent with Keychain-backed origin auth; clean stop/start verification passed and the public health/chat endpoint recovered automatically (`a07c945`, `78f2279`).

## Required gates executed

- `npm run config:validate` ✅ (re-run 2026-07-22; no regressions)
- `npm run typecheck` ✅ (re-run 2026-07-22; fixed chat/server type-contract blockers)
- `npm run doctor` ✅ (19/19; deliberate non-CNG sync check disabled because checked-in native folders are authoritative)
- `npm run export:web` ✅ (re-run 2026-07-22)
- `npm run export:android` ✅ (re-run 2026-07-22)
- `npm run phase3:check:chat-undo` ✅
- `npm run phase3:check:chat-send` ✅
- `npm run phase3:check:chat-rollback-idempotency` ✅
- `npm run phase4:check:mcp-tool-contract` ✅
- `npm run phase4:check:mcp-workflow-replay` ✅
- `npm run phase4:check:mcp-workflow-replay:http` ✅
- `npm run phase4:check:mcp` ✅ (all above in one command)
- `npm run phase8:check:health-connect` ✅ (manifest, JS bridge, permission delegate, and release APK)
- `npm run doctor` ✅ (19/19 with temporary cache override after local cache-path defect)
- `npm run export:web` ✅ (re-run after Android startup fix)
- `npm run export:android` ✅ (re-run after Android startup fix)
- `npm run phase3:check:chat-send` ✅ (re-run after Android startup fix)
- `npm run phase3:check:chat-rollback-idempotency` ✅ (re-run after Android startup fix)
- `npm run phase5:check:notion-adapter` ✅
- `npm run phase6:check:sheets-adapter` ✅
- `npm run phase6:check` ✅ (`test:server:sheets`, `contract:provider:sheets`, `phase6:check:sheets-adapter`)
- `npm run phase6:check` ✅ (`test:server:sheets`, `contract:provider:sheets`, `phase6:check:sheets-adapter`)

First run of `npm run doctor` failed due local `.npm` path mismatch (`ENOTDIR /Users/srinivasvaddi/.npm`); rerun succeeded with explicit cache override.

Core runtime, config, typecheck, web export, chat, MCP, and hosted provider checks pass. Android/Health Connect native proof passes; unlocked-emulator permission/read proof, Notion webhook secret registration, and final Expo UI polish remain open.

## Execution rules

- Every phase change must update this ledger.
- Every stream commit must include evidence paths, checks run, and blocker/next action.
- No scope creep into later phases until the phase is in PASS with required gates.
