# LifeOS implementation ledger

## Corrected architecture checkpoint (2026-07-22)

- The canonical core is portable. SQLite, Postgres, Notion, Sheets, app UI, AI, and MCP are optional surfaces with one shared semantic contract.
- SQLite, Notion, Sheets, or Postgres may be the user-selected authority. Notion and Sheets must each remain independently usable.
- AI is optional. Users configure direct model providers with an ordered primary/fallback policy; the UI discloses the provider used.
- The app never requires `server/`, Tailscale, a WonderFood-owned host, tunnel, Mac, webhook, or subscription. `server/` remains a separate FOSS MCP/external-client package.
- All runtime configuration is editable in app. Credentials use platform-secure storage and must never be compiled through `EXPO_PUBLIC_*`.
- Export success, local contract checks, one screenshot, or one live scenario proves only that slice. It does not close a phase.
- No phase is complete until its exact functional, portability, recovery, security, accessibility, and visual gates pass with durable evidence.

## Progress (2026-07-22)

| Phase | Owner | Status | Evidence | Blocker | Next action |
|---|---|---|---|---|---|
| 0 — Architecture and contracts | Agent A (Canonical schemas + domain runtime) | PARTIAL | `3db1f9b`, `f849188`, `docs/lifeos/expo-implementation-plan.md`, `docs/lifeos/product-pass.md`, `docs/lifeos/adr-0001-architecture.md`, `packages/domain-config/*` | Existing ADR and setup docs must be reconciled with portable authority, direct provider primary/fallback, secure credentials, and complete in-app configuration | Review and accept corrected ADR/contracts; no host selection |
| 1 — SQLite canonical runtime | Agent A | PARTIAL — CORE SLICE PROVEN | `src/domain/{catalog,runtime,surface,queries,renderer}.ts`, `src/db/{migrations,provider,provider.native.tsx,provider.web.tsx,records,conversations,sources,outbox,actions,undo,seed}.ts`, `app/_layout.tsx`, `package.json`, `server/package.json`, `scripts/quality/check-phase1-sqlite-runtime.sh`, `docs/lifeos/implementation-workstreams.md`, `bddbf0e`, `1f8c0d8`, `b098158`, historical config/type/export and web SQLite smoke | Retained migration-fixture upgrades, app-level process-death/recovery, provider-field preservation, release seed policy, and full action/Undo coverage are not all proven | Add durable app-level migration/recovery fixtures and verify a clean SQLite-only Food loop |
| 2 — Generic domain renderer | Agent G (Expo UX) | PARTIAL | `src/domain/{surface,queries,renderer}.tsx`, `app/(tabs)/food.tsx`, `app/search.tsx`, `app/record/[id].tsx`, `app/sources.tsx` | Hard-coded Food/Today/provider/demo copy remains; domain picker, invalid/stale/permission states, fixture-domain navigation proof, responsive matrix, and accessibility/visual acceptance are open | Remove runtime constants and complete manifest-driven UI plus the full visual-state matrix |
| 3 — Direct AI chat | S2 | PARTIAL — DIRECT PROVIDER FOUNDATION | `src/settings/lifeos-settings.ts`, `src/chat/{client,direct-provider}.ts`, `app/settings.tsx`, `app/config.tsx`; historical server-side chat proofs remain scoped evidence | Direct provider UI/storage/routing exist; ten-turn correction, source-grounding, rich tables, second-device resume, and production visual proof remain open | Prove primary/fallback on emulator, then finish source-grounded rich chat |
| 4 — Optional MCP parity | PARTIAL — LOCAL SLICE PROVEN | `server/src/mcp/{server.ts,tools.ts,resources.ts,auth.ts,policy.ts}`, workflow checkpoint/compensation files, phase-4 local HTTP evidence, `server/test/mcp-resource-contract.ts` | Independent external-client conformance, auth/rate/timeout/audit, removal of proposal semantics, and MCP-disabled operation remain open | Validate official protocol behavior and prove MCP can be enabled or removed without changing canonical semantics |
| 5 — Optional Notion adapter | PARTIAL — ADAPTER/LIVE SLICE PROVEN | `server/src/providers/notion/*`, sync/webhook files, adapter tests; historical disposable scenario evidence at `app/build/evidence/live-workspace/notion_scenarios-1784760281.json` | Full template counts/relations/rollups, authority selection, provider Undo, permission-loss recovery, standalone Notion loop, disconnect, and durable visual proof remain open | Prove Notion-only authority and Notion-disabled modes with user-owned credentials; keep webhooks optional |
| 6 — Optional Google Sheets adapter | PARTIAL — ADAPTER/LIVE SLICE PROVEN | `server/src/providers/sheets/*`, sync/webhook files, adapter tests; historical live workbook scenario evidence | User setup, authority selection, stable workbook contract, formula/user-column preservation, provider Undo, round trip, disconnect, and Sheets-only visual UX remain open | Prove Sheets-only authority and Sheets-disabled modes with user-owned OAuth/workbook |
| 7 — Commands, Undo, workflows | PARTIAL — LOCAL/WEB SLICE PROVEN | shared schemas, `src/actions/*`, MCP state/tools, workflow checkpoint/compensation, phase-7 cross-surface evidence | All entry paths, provider/device Undo, conflict handling, cancellation/resume, receipt accounting, native rendering, and removal of legacy proposal/review semantics remain open | Close command parity and workflow gates across SQLite, optional adapters, direct AI, MCP, web, and Android |
| 8 — Android completion | Agent G | IN PROGRESS | Expo native project generated under `android/`; release APK launch evidence on emulator and S23U; Health Connect read-only bridge, permission delegate, denied/granted emulator branches, and settings deep link | Secure direct-provider credentials, shared-token removal, visible Health UI, real Health records, native capture/share/barcode/voice, backup/restore, background sync, full device/visual matrix, AAB/Play, and upgrade proof remain open | Replace public bearer-token setup, wire user settings, then complete Android product and release gates |
| 9 — Polish/perf/iOS | Agent G | NOT STARTED | Brand assets and isolated screenshots exist; no complete phase gate | Visual state matrix, responsive/accessibility evidence, performance budgets, dark theme decision, tablet/foldable UX, iOS native/signing/TestFlight remain open | Finish visual quality gates after functional phases; begin iOS only after Android gate |

## Active sub-agent roster

Current explicit phase-slice agents are defined in `docs/lifeos/implementation-sub-agents.md` and are binding for new edits:

- S1: Canonical schemas, SQLite, domain runtime (Phases 0–1)
- S2: Direct provider registry + Chat + conversations + citations + internal agents (Phase 3)
- S3: Optional Notion adapter + optional webhooks (Phase 5)
- S4: Optional Google Sheets adapter (Phase 6)
- S5: Optional MCP + action engine + Undo + workflows (Phases 4,7)
- S6: Expo UI + accessibility + responsive QA (Phases 2,9)
- S7: Android + Health Connect + EAS (Phase 8)

## Historical evidence log

Entries below preserve what was executed. A historical PASS applies only to the named script, route, device, or scenario; it is not phase completion.

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
- `2026-07-23`: Notion push webhooks are now explicitly optional and disabled by default (`LIFEOS_NOTION_WEBHOOKS_ENABLED=true` is required); release sync uses authenticated pull/manual refresh and no subscription or signing secret.
- `2026-07-23`: temporary hosted webhook handshake proof passed; this deployment was later retired and is not a release dependency.
- `2026-07-23`: Health Connect snapshot reads now initialize the native client after app restart before calling `readRecords`; initialization failures become explicit `error` snapshots instead of silently looking like empty data. Release APK and Health Connect gate pass.
- `2026-07-23`: Health Connect snapshot runtime now validates ISO/range timestamps, exposes full-fidelity export and authenticated deletion helpers, and adds typed client helpers; `server/test/health-snapshot-sync.ts` covers dedupe, export, invalid ranges, and deletion.
- `2026-07-22`: external Cloudflare quick-tunnel proof passed `/health`, `/providers/status`, live Sheets pull, source-grounded `/chat/send`, and MCP `initialize`; the disposable tunnel was closed immediately after verification because it was not a managed authenticated deployment.
- `2026-07-22`: removed obsolete Kotlin package `com.example.wonderfood` from the S23U; only the current Expo package `com.wonderfood.app` remains. The app process and LAN server are healthy, but visual proof is still pending because the device is secure-locked/dozing.
- `2026-07-22`: temporary authenticated remote-connector proof validated external chat, Notion/Sheets citations, and MCP. It was proof infrastructure, not required product architecture.
- `2026-07-22`: temporary user-level connector lifecycle proof passed (`a07c945`, `78f2279`).
- `2026-07-22`: retired all personal hosted bridge infrastructure: removed the exact DNS record, Cloudflare tunnel, LaunchAgent, and Keychain bridge token. The root website, shared infrastructure, Tailscale, and local conversation data were untouched. The app has no server dependency.

## Historical slice gates executed

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

These historical checks prove core/runtime slices only. Current phase status remains the table above. Direct-provider primary/fallback, independent authority modes, durable visual/accessibility evidence, provider/device Undo, Android release completion, and iOS remain open.

## Execution rules

- Every phase change must update this ledger.
- Every stream commit must include evidence paths, checks run, and blocker/next action.
- No scope creep into later phases until the phase is in PASS with required gates.
