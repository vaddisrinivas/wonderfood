# LifeOS Expo implementation plan

Status: active  
Platform order: web, Android, iOS  
Active domain: Food  
Permanent shell: Today, Food, Chat

## 1. Outcome

Build one portable LifeOS canonical core. App, Notion, Google Sheets, SQLite, Postgres, AI providers, and MCP clients are optional surfaces around that core. A user may use any one surface independently or combine several without changing record meaning.

Every enabled surface uses the same domain packages, record semantics, source citations, workflows, action policy, agents, and audit trail. No vendor, hosted service, account, or model provider is mandatory.

The daily product feels like GPT plus Notion:

- Today shows high-value decisions and exceptions.
- Food is an editable, relation-aware workspace.
- Chat is linear, multi-turn, grounded, structured, and able to act.
- Search, Capture, Sources, Domains, Skills, MCP, Agents, Sync, and Settings are real supporting surfaces.
- Food is Day 0. New domains install through manifests and schemas, not permanent new tabs.

No proposal inbox, AI-response editing, agent-branch UI, or approval screen. Exact reversible actions write directly and return Undo. Ambiguous requests ask a normal chat question. Sensitive or irreversible actions are not exposed to agents; the app opens an ordinary editor when user action is needed.

## 2. Current baseline

Evidence-backed implementation slices:

- Expo SDK 57, React Native 0.86, Expo Router.
- Static web and Android bundle exports.
- Today, Food, Chat, Search, Capture, Record, Sources, and System screens.
- SQLite canonical runtime code, migrations, repositories, recovery helpers, and local smoke evidence.
- Historical server-backed chat slices with streaming, durable turns, retrieval, tables, source citations, action receipts, idempotency, and Undo.
- Notion data-source, Google Sheets, and MCP Streamable HTTP adapter slices with local contract tests and limited live proof.
- Food, Health and Plants manifests/skills; declarative seven-role agent registry; canonical schemas and five Food workflows.
- Android release APK with bundled JavaScript, Health Connect read-only bridge, and permission delegate.
- Historical passes for config validation, TypeScript, Expo Doctor, web export, and Android export.
- Native identifiers remain com.wonderfood.app.
- Web runtime and emulator release smoke evidence exists.

Not yet proven complete:

- Full phase gates below, including ten-turn chat, provider-independent setup, provider Undo, responsive/accessibility coverage, AAB/Play, physical Health Connect, backup/restore, performance, and iOS.
- Direct user-configured model providers with primary/fallback behavior.
- Complete in-app editing for providers, domains, skills, workflows, agents, schemas, sync, privacy, and appearance.
- Replacement of demo/static copy with live state and completion of visual acceptance.

## 3. Non-negotiable product contracts

### 3.1 Client parity

When enabled, App Chat, direct model providers, external GPT, MCP clients, Notion automations, and Sheets workflows use:

- the same active domain catalog;
- the same canonical record schema;
- the same domain skill;
- the same workflow definitions;
- the same tool schemas;
- the same source and citation format;
- the same action-risk policy;
- the same action receipts and Undo semantics.

Different clients may render differently. They cannot interpret the domain differently. Disabling one surface cannot corrupt or disable another.

### 3.2 Authority and data homes

- One authoritative data home per household.
- The user chooses SQLite, Notion, Sheets, or Postgres as authority during setup; none is globally mandatory.
- SQLite can operate as the standalone local authority or as an instant offline replica.
- Notion and Sheets can each operate as the sole human-facing authority, or as an explicit projection/input surface.
- Postgres is an optional self-hosted or managed authority.
- Never uncontrolled multi-master Notion plus Sheets plus Postgres writes.
- Every provider record maps to a stable canonical LifeOS ID.
- Unsupported provider fields remain preserved in source snapshots.

### 3.3 Portability and optional connectivity

- The canonical schemas, domain packages, action policy, source format, and migration rules run inside each enabled surface.
- AI is optional. Local records, capture, search, editing, workflows, and Undo remain useful without a model.
- Users configure model providers directly. They choose an ordered primary and fallback list with explicit capability and privacy labels.
- A provider failure may fall back only to a configured provider. The UI names the provider used; it never silently changes data policy.
- User model credentials stay in platform-secure storage and never in `EXPO_PUBLIC_*`, static web output, logs, screenshots, or committed files.
- The app has no required server URL, shared bearer token, tunnel, Mac, webhook, or subscription.
- External GPT/MCP clients use the separate FOSS MCP package; that package is not an app dependency.
- Every adapter has standalone health, import/export, disconnect, and recovery behavior.

### 3.4 Chat

- Linear append-only turns.
- Corrections are new turns.
- Completed answers are never edited into alternate history.
- Retry only replaces a failed or incomplete assistant placeholder.
- Every household fact cites an immutable source snapshot.
- Tables, record cards, checklists, timelines, and citations are first-class answer blocks.
- Web search is used only when current external information is relevant.
- Model summaries are context aids, never evidence.

### 3.5 Actions

- Reads execute immediately.
- Exact reversible creates, updates, and archives execute atomically.
- Successful actions show what changed, sources, timestamp, and Undo.
- Ambiguous writes ask one focused question and write nothing.
- Purchases, messages, credential changes, destructive deletes, and private exports are not agent-callable.
- Sensitive record edits open the normal editor.
- Idempotency prevents retries from duplicating work.

### 3.6 Packages

- One domain skill per domain.
- Schemas are contracts and MCP resources, not skills.
- Workflows are declarative configs.
- Add a workflow-specific skill only when the workflow requires judgment not supplied by its domain skill.
- Package files are JSON and Markdown. No executable package JavaScript.

## 4. Multi-agent system

The user sees one assistant identity: Hearth. Internally, a bounded multi-agent runtime may cooperate. Agent branches and chain-of-thought never appear in product UI.

### 4.1 Agent roles

1. Conversation orchestrator
   - Owns the turn.
   - Classifies intent, risk, domain, latency budget, and required agents.
   - Produces the final user-visible response.

2. Retrieval agent
   - Selects the minimum relevant local, Notion, Sheets, Postgres, MCP, file, Health Connect, or web sources.
   - Produces immutable source snapshots.
   - Cannot write.

3. Domain specialist
   - Loads the active domain skill and manifest.
   - Applies Food judgment today; Health, Plants, and others later.
   - Cannot bypass schema or tool policy.

4. Planning agent
   - Converts an exact request into typed commands or a workflow execution plan.
   - Resolves dependencies and atomic boundaries.
   - Cannot execute writes.

5. Action executor
   - Validates and executes allowed commands.
   - Produces action events, before and after versions, and Undo.
   - Has no model discretion beyond validated command inputs.

6. Citation verifier
   - Binds answer claims to exact excerpts and source versions.
   - Rejects missing, mismatched, stale, or inaccessible citations.
   - Cannot modify source content.

7. Sync steward
   - Pulls provider changes, deduplicates events, applies deterministic merges, and raises record repair states.
   - Does not create chat prose.

8. Workflow coordinator
   - Runs declared workflow steps with checkpointing, cancellation, retry, and compensation.
   - Uses domain specialist, retrieval, executor, and verifier as bounded workers.

### 4.2 Agent execution rules

- Typed handoffs only. No free-form agent-to-agent conversation.
- Each handoff includes turn ID, domain, input schema, allowed tools, source IDs, deadline, token budget, and output schema.
- Parallelize independent retrieval and verification. Serialize writes.
- Every worker has a hard timeout, cancellation signal, and tool allowlist.
- The orchestrator may retry a failed read worker once. It must not retry a write without the same idempotency key.
- Agents never share raw secrets.
- Agent traces store role, status, timing, tool names, source IDs, and result hashes. Do not store hidden reasoning.
- The user sees concise progress such as Searching LifeOS, Checking the web, or Updating shopping.
- A partial failure returns useful completed results and names what failed.

### 4.3 Agent registry

Add a declarative registry containing:

- role ID and version;
- allowed domains;
- input and output schemas;
- allowed tools and resources;
- risk class;
- timeout and concurrency;
- model class;
- fallback behavior;
- telemetry and retention policy.

The registry belongs beside domain config. Domain packages may request a role but cannot widen its permissions.

### 4.4 Multi-agent tests

- Deterministic router tests for common Food requests.
- Parallel retrieval order does not change the final source set.
- Write executor cannot call read-unrelated or secret tools.
- Duplicate worker completion does not duplicate actions.
- Cancellation stops all children and leaves no partial write.
- Citation verifier blocks unsupported claims.
- Workflow compensation restores the previous state.
- External GPT/MCP and app Chat produce equivalent command and receipt shapes.

## 5. Phase 0: documentation and architecture lock

### Allowed APIs

- Expo Router SDK 57 navigation imports only.
- Expo SQLite: SQLiteProvider, useSQLiteContext, async prepared reads and writes, transactions.
- Model providers: direct user-configured adapters with declared capabilities, ordered primary/fallback selection, and provider-specific conversation state.
- OpenAI adapter: Responses plus Conversations; web_search with rendered URL citations.
- OpenAI Agents SDK may orchestrate server-side workers. It must not create a second conversation-history system.
- MCP: Streamable HTTP. Existing stdio bridge is development-only.
- Notion: API version 2026-03-11, data-source IDs, data-source query, page markdown or blocks, verified webhooks.
- Sheets v4: values get, batchGet, update, batchUpdate, append.
- EAS: internal APK, production AAB, Play internal draft.

### Official references

- Expo Router SDK 57: https://docs.expo.dev/versions/v57.0.0/sdk/router/
- Expo SQLite: https://docs.expo.dev/versions/latest/sdk/sqlite/
- Expo API routes: https://docs.expo.dev/router/web/api-routes/
- EAS APK: https://docs.expo.dev/build-reference/apk/
- Notion 2026 upgrade: https://developers.notion.com/guides/get-started/upgrade-guide-2026-03-11
- Notion data sources: https://developers.notion.com/reference/query-a-data-source
- Notion webhooks: https://developers.notion.com/reference/webhooks-events-delivery
- Sheets values: https://developers.google.com/workspace/sheets/api/guides/values
- OpenAI conversation state: https://developers.openai.com/api/docs/guides/conversation-state
- OpenAI MCP: https://developers.openai.com/api/docs/guides/tools-connectors-mcp
- OpenAI web search: https://developers.openai.com/api/docs/guides/tools-web-search
- Agents sessions: https://openai.github.io/openai-agents-js/guides/sessions/

### Locked decisions

1. Keep Expo web output static.
2. Keep the canonical core portable and independent of Notion, Sheets, Postgres, AI, or MCP.
3. Support direct user-configured model providers. Store ordered primary/fallback selection and show which provider answered.
4. Keep `server/` as a separate FOSS MCP/external-client package. Never make it an app runtime requirement.
5. Use Responses Conversations for durable OpenAI chat history. Do not combine an Agents Session with conversation ID or previous response ID for the same history.
6. Use SQLite locally. Web persistence receives a separate validation gate because Expo SQLite web is alpha.
7. Let the user select one authority. Notion, Sheets, SQLite, and Postgres remain optional and independently usable.
8. Notion webhooks are optional change signals. Deduplicate, order when needed, and refetch canonical content.
9. Sheets append is undoable only when the returned updated range is stored.
10. Never place a shared bearer token or provider credential in an Expo public environment variable or static bundle.

### Gate

- Architecture decision record accepted.
- Standalone, direct-provider, Notion-only, Sheets-only, database-only, and MCP-client modes documented.
- Every runtime option is editable and inspectable inside the app.
- Credential storage and OAuth model selected for each platform.
- Authority selection and projection rules documented for SQLite, Notion, Sheets, and Postgres.
- Primary/fallback model-provider policy reviewed.
- Package, command, source, citation, agent-handoff, action-event, and Undo schemas reviewed.

## 6. Phase 1: local canonical runtime

### Files

- src/db/provider.tsx
- src/db/migrations.ts
- src/db/records.ts
- src/db/conversations.ts
- src/db/sources.ts
- src/db/outbox.ts
- src/db/actions.ts
- src/db/undo.ts
- src/domain/catalog.ts
- src/domain/runtime.ts

### Tasks

1. Install the Expo-compatible expo-sqlite version.
2. Copy the official SQLiteProvider and migration pattern.
3. Enable WAL and foreign keys.
4. Create tables for records, relations, provider links, source snapshots, conversations, messages, citations, action events, outbox, workflow runs, and agent runs.
5. Validate records against the canonical schema before writes.
6. Replace sample repositories with SQLite repositories.
7. Retain deterministic sample data only in development seed mode.
8. Add migration rollback and recovery export.

### Verification

- Fresh install migrates.
- Upgrade from every retained migration fixture succeeds.
- Add, edit, relate, archive, relaunch, and recover.
- Failed transaction leaves no partial record or relation.
- Every reversible write has a compensating Undo payload.
- Bound parameters only.
- Process death loses no accepted write.

### Anti-pattern guards

- No user input interpolated into SQL.
- No heavy synchronous DB calls.
- No native-only exclusive transaction in shared web code.
- No provider field loss.

## 7. Phase 2: generic domain renderer

### Files

- src/domain/surface.ts
- src/domain/queries.ts
- src/domain/renderer.tsx
- app/(tabs)/food.tsx
- app/(tabs)/index.tsx
- app/search.tsx
- app/capture.tsx
- app/record/[id].tsx

### Tasks

1. Render surfaces, collections, filters, actions, and record properties from the active manifest.
2. Add domain picker in the Food title.
3. Generate Today from active-domain queries.
4. Make search and capture domain-aware.
5. Render properties, relations, blocks, activity, provenance, and source links on record pages.
6. Add loading, empty, offline, stale, invalid-package, permission, and error states.
7. Create one fixture domain proving no navigation code changes are required.

### Verification

- Food screen contains no hard-coded surface branching.
- Fixture domain installs through config.
- Invalid manifest fails closed.
- 320, 390, 768, and 1280 widths have no horizontal overflow.
- Keyboard, screen reader, dynamic type, and 44dp targets pass.
- Today, Food, Chat, Search, Capture, Record, Sources, and System pass the visual quality gate in every supported state.
- Dates, identity, source status, record counts, adapter state, and capability labels come from runtime state rather than demo constants.

## 8. Phase 3: direct AI chat

### Files

- server/package.json
- server/src/index.ts
- server/src/chat.ts
- server/src/conversations.ts
- server/src/provenance.ts
- server/src/actions.ts
- server/src/agents/registry.ts
- server/src/agents/orchestrator.ts
- server/src/agents/retrieval.ts
- server/src/agents/domain.ts
- server/src/agents/planner.ts
- server/src/agents/executor.ts
- server/src/agents/verifier.ts
- server/src/providers/openai.ts
- src/chat/client.ts
- src/chat/types.ts
- src/chat/citations.ts

### Tasks

1. Add a provider registry with user-selected primary and ordered fallback providers.
2. Support direct provider calls from the app with encrypted credentials and ordered primary/fallback routing.
3. Keep external GPT/MCP transport in the separate FOSS package, outside the app runtime.
4. Create or resume provider-native conversation state per LifeOS thread.
5. Stream content blocks, tool progress, citations, provider identity, fallback events, and action receipts.
6. Implement bounded agent registry and typed handoffs.
7. Retrieve fresh enabled sources every turn; absence of Notion, Sheets, MCP, or web search is valid.
8. Persist immutable source snapshots and citation spans.
9. Add current web search only when required and supported by the selected provider.
10. Replace synthetic chat answers with the typed client.
11. Keep an offline local-data answer mode with explicit limitations.
12. Add stop, retry failed response, thread rename, pin, archive, search, and resume.
13. Store direct-provider credentials in platform-secure storage; never compile them into static web or native bundles.
14. Document provider disconnect, credential replacement, export, reset, and recovery entirely in app.

### Verification

- Ten-turn correction-heavy conversation remains coherent.
- Relaunch and second device resume the same linear history.
- Source links open exact records, pages, ranges, or URLs.
- Changed source displays Updated since this answer.
- Model failure does not duplicate user turns or writes.
- Worker timeout and cancellation work.
- Primary failure invokes only the configured fallback, preserves the user turn once, and displays the provider used.
- Local-only and direct-provider modes work with no server.
- Connector loss degrades to local capability without data loss.
- No hidden reasoning is stored or shown.
- No edit-response or branch UI exists.

### Anti-pattern guards

- No model, provider, or shared bearer credential in an Expo public/static bundle.
- No summary used as citation evidence.
- No unsupported source fabricated by the model.
- No uncontrolled recursive agents.
- No second chat history owned by Agents Session.

#### Phase 3 historical slice note (2026-07-22)

- `/chat/send` and `/chat/send/stream` now use the shared `chat-send-request.v1` envelope.
- Streaming always emits `run.start`, optional `token` frames, and terminal `run.end`.
- `/chat/send` and `/chat/undo` expose canonical action receipts and source references (`source_ids`) from MCP/agent execution.
- Idempotency replay and deterministic error/cancel handling are in place with evidence scripts in `app/build/evidence/phase3-chat-*`.
- These checks prove a historical server-backed slice only. They do not close direct-provider, fallback, ten-turn, second-device, or credential-storage gates.

## 9. Phase 4: MCP and external-client parity

### Files

- server/src/mcp/server.ts
- server/src/mcp/resources.ts
- server/src/mcp/tools.ts
- server/src/mcp/auth.ts
- server/src/mcp/policy.ts
- scripts/mcp/wonderfood_mcp_server.py
- docs/mcp-bridge.md

### Tasks

1. Expose Streamable HTTP MCP as an optional adapter.
2. Expose catalog, manifests, schemas, records, source snapshots, and conversations as resources.
3. Expose search, read, create, update, archive, run workflow, and Undo as typed tools.
4. Generate schemas from the same domain package as the app.
5. Apply identical policy and receipt formats to app Chat and external clients.
6. Rewrite or retire the stdio Python bridge and deleted Kotlin asset paths.
7. Keep local App/Notion/Sheets use fully functional when MCP is disabled.
8. Let users configure MCP clients/servers in app without a product-owned host.

### Verification

- App Chat and external GPT/MCP return equivalent command and receipt shapes.
- Cross-domain and secret access is denied.
- Idempotency blocks duplicates.
- Sensitive and irreversible operations are absent.
- MCP conformance, auth, rate, timeout, and audit tests pass.
- Disabling MCP leaves canonical records, direct AI, and enabled provider surfaces intact.

## 10. Phase 5: Notion 2026 adapter

### Files

- server/src/providers/notion/client.ts
- server/src/providers/notion/discovery.ts
- server/src/providers/notion/projection.ts
- server/src/providers/notion/pull.ts
- server/src/providers/notion/push.ts
- server/src/providers/notion/webhook.ts
- server/src/providers/notion/citations.ts

### Tasks

1. Implement optional protected OAuth or internal-integration connection.
2. Use Notion version 2026-03-11.
3. Discover databases and data sources; query by data-source ID.
4. Map canonical IDs to Notion pages.
5. Use page markdown for simple content and blocks for unsupported structure.
6. Verify webhook signatures from the raw body.
7. Dedupe webhook event IDs, handle out-of-order delivery, and refetch current content.
8. Preserve unsupported Notion blocks as provider-owned read-only content.
9. Create exact page or block citations.
10. Support Notion as standalone authority, explicit projection, or disabled adapter.
11. Disconnect without deleting canonical or unsupported provider data.

### Verification

- Disposable import of the real LifeOS 2026 template matches counts, IDs, relations, required fields, and rollups.
- App to Notion to app loop passes.
- Duplicate and reordered events remain idempotent.
- Permission loss becomes stale, not data loss.
- Notion-only user can run the core Food loop.
- Existing visual dashboard remains useful without the app.
- App/SQLite, Sheets-only, direct AI, and MCP modes remain usable without Notion.

### Anti-pattern guards

- No database-ID query for modern data sources.
- Use in_trash, not removed archived behavior.
- Never treat webhook payload as full canonical content.
- Never store credentials in Notion.

## 11. Phase 6: Google Sheets adapter

### Files

- server/src/providers/sheets/client.ts
- server/src/providers/sheets/workbook.ts
- server/src/providers/sheets/projection.ts
- server/src/providers/sheets/pull.ts
- server/src/providers/sheets/push.ts
- server/src/providers/sheets/health.ts

### Tasks

1. Add optional OAuth with least-privilege scope.
2. Add stable LifeOS ID, version, updated-at, source, and archive columns.
3. Define one tab per canonical collection plus schema, relations, sync, and health tabs.
4. Batch reads and writes.
5. Store prior ValueRange before update.
6. Store append updated range for Undo.
7. Preserve formulas and user-owned columns.
8. Validate required tabs, columns, IDs, types, formulas, duplicates, and relation targets.
9. Support Sheets as standalone authority, explicit projection, or disabled adapter.
10. Disconnect without deleting canonical or user-owned workbook data.

### Verification

- Sheets-only user can manage the core Food loop.
- App change reaches Sheets.
- Sheets change reaches canonical state and Notion according to authority rules.
- Repeated sync is idempotent.
- Formula and user-column preservation passes.
- Undo restores cells and canonical records.
- App/SQLite, Notion-only, direct AI, and MCP modes remain usable without Sheets.

### Anti-pattern guards

- Prefer drive.file; no broad Drive scope without need.
- No v3 feeds.
- No append without returned-range tracking.
- No silent multi-master conflict resolution.

## 12. Phase 7: commands, Undo, and workflows

### Files

- packages/domain-config/schemas/command.v1.schema.json
- packages/domain-config/schemas/action-event.v1.schema.json
- packages/domain-config/schemas/agent-handoff.v1.schema.json
- packages/domain-config/schemas/undo.v1.schema.json
- src/actions/engine.ts
- src/actions/policy.ts
- src/actions/undo.ts
- server/src/workflows/runner.ts
- server/src/workflows/checkpoint.ts
- server/src/workflows/compensation.ts

### Tasks

1. Use one command boundary for UI, Chat, MCP, sync, import, and workflows.
2. Implement action risk classes and tool exposure.
3. Add action receipt with actor, domain, tool, source IDs, record IDs, before and after versions, timestamp, idempotency key, and Undo deadline.
4. Run weekly reset, receipt-to-kitchen, and meal-plan-to-shopping workflows.
5. Checkpoint after each step.
6. Cancel cleanly.
7. Resume idempotently.
8. Compensate reversible completed steps after failure.
9. Update old proposal and review docs and fixtures.

### Verification

- Every entry path creates the same event shape.
- Undo is atomic and conflict-aware.
- Interrupted workflow resumes or compensates.
- Receipt workflow accounts for every line.
- No hidden review queue appears.
- No action retries duplicate records.

## 13. Phase 8: Android product completion

### Tasks

1. Create development build only when a native dependency requires it.
2. Native share target, camera, receipt, barcode, voice, and deep links.
3. Health Connect with least privilege and contextual permission.
4. Encrypted credential storage.
5. Local encrypted backup and verified restore.
6. Background outbox and sync with visible freshness.
7. Push notification hooks for completed workflows and sync repair only.
8. EAS preview APK, production AAB, Play internal draft.

### Device matrix

- Small phone.
- Medium phone.
- S23U-size phone.
- Tablet.
- Foldable breakpoint.
- Physical S23U only for final Health Connect and device-specific proof.

### Verification

- Offline Food loop survives process death and reboot.
- Permission denied and revoked states remain useful.
- TalkBack, keyboard, dynamic type, contrast, and target sizes pass.
- Upgrade over com.wonderfood.app preserves data.
- APK and AAB install and launch.
- Health Connect export and delete pass on physical device.

## 14. Phase 9: polish, performance, and iOS

### Tasks

1. Final brand icon and splash.
2. Complete light states, then deliberate dark theme.
3. Virtualize long records and threads.
4. Tablet rail and split view.
5. Motion limited to transform and opacity where possible.
6. Measure cold start, Today render, search, thread resume, sync, and list scroll.
7. Complete iOS native dependencies, permissions, signing, EAS, TestFlight, and release after Android gates pass.

### Verification

- No overflow at required breakpoints.
- All empty, loading, offline, stale, error, permission, migration, and recovery states exist.
- Performance budgets are recorded and enforced.
- iOS behavior matches contracts, not necessarily Android visuals.

## 15. Multi-agent execution plan for implementation

Use one orchestrator task and parallel, isolated workstreams. Every worker must report sources, exact files, checks, confidence, and gaps. The orchestrator validates all output before merging.

### Workstream A: canonical data

- Owns packages/domain-config, src/db, src/domain, migrations, repositories, validators.
- Cannot edit chat UI or provider adapters.
- Gate: migrations, config, repository, relation, and Undo tests.

### Workstream B: chat and agents

- Owns server chat, Responses, Conversations, agent registry, handoffs, streaming, client chat types.
- Cannot edit provider projections or native capture.
- Gate: multi-turn, cancellation, tool policy, citation, and retry tests.

### Workstream C: Notion

- Owns Notion client, projection, webhooks, source snapshots, citations, disposable E2E.
- Cannot edit canonical schema without a reviewed change request.
- Gate: Notion round trip, duplicate webhook, permission-loss, and visual data-plane checks.

### Workstream D: Sheets

- Owns workbook contract, OAuth, read and write projection, health, formula preservation, E2E.
- Cannot create a second authority policy.
- Gate: Sheets-only loop, round trip, idempotency, and Undo.

### Workstream E: MCP and action engine

- Owns MCP server, tool generation, auth, policy, receipts, idempotency, workflow runner.
- Cannot expose sensitive or generic execution tools.
- Gate: app and MCP parity, scope denial, workflow compensation.

### Workstream F: app UX

- Owns Expo screens, generic renderer, accessibility, responsive states, record editor, search, capture, sources, settings.
- Uses repository and chat interfaces; no direct provider calls.
- Gate: interaction tests, screenshots, overflow, accessibility, copy audit.

### Workstream G: Android

- Owns development build, native capture, Health Connect, encrypted storage, background work, EAS.
- Never targets the physical phone until the explicit physical-device gate.
- Gate: emulator matrix, upgrade, offline, APK, AAB, physical Health Connect.

### Integration cadence

1. Contracts and fixtures merge first.
2. Each workstream develops against fakes.
3. Contract tests run on every merge.
4. Provider disposable E2E runs after unit and contract gates.
5. App integrates one adapter at a time.
6. Demo labels are removed only after that adapter passes live E2E.
7. Android release work starts after web and data gates.
8. iOS starts after Android product gate.

### Merge order

1. Canonical schemas and ADRs.
2. SQLite runtime.
3. Generic domain renderer.
4. Server and chat agents.
5. Action engine and MCP.
6. Notion adapter.
7. Sheets adapter.
8. Workflow E2E.
9. Android native features.
10. Performance, accessibility, release, iOS.

## 16. Final verification gate

### Visual quality acceptance

- Capture comparable screenshots at 320, 390, 768, and 1280 widths plus small phone, medium phone, S23U-size phone, tablet, and foldable breakpoints.
- Review Today, Food, Chat, Search, Capture, Record, Sources, and System in populated, empty, loading, offline, stale, invalid-package, permission-denied, migration, recovery, and error states.
- No clipped text, unintended horizontal scrolling, overlapping controls, unsafe-area collisions, keyboard obstruction, or layout jump.
- Typography hierarchy, spacing rhythm, color, elevation, icons, dividers, and component states are consistent across screens.
- Text passes contrast; controls expose accessible names, focus order, selected/disabled state, dynamic type, keyboard operation, and at least 44dp targets.
- Product copy describes current capability. No stale dates, personal names, demo counts, fake live badges, provider IDs, “adapter next,” or configured-source claims in release UI.
- Long records and chats remain readable and performant; tables and citations wrap or scroll intentionally.
- Light theme is complete. Dark theme is required only when declared supported and must pass the same matrix.
- Store approved reference screenshots and machine-readable test metadata as durable release evidence, not only ignored local build output.
- A phase cannot be marked complete from export success or one screenshot alone.

Repository gates:

- npm ci
- npm run config:validate
- npm run typecheck
- npm run doctor
- npm run export:web
- npm run export:android
- secret scan
- dependency and license scan
- stale Kotlin-path and proposal-semantics scan
- static-bundle credential scan, including `EXPO_PUBLIC_*`

Product gates:

- SQLite migration, rollback, recovery, process-death tests.
- Domain package fixture install.
- Ten-turn chat and thread-resume tests.
- Citation exactness and source-change tests.
- Multi-agent routing, cancellation, timeout, scope, and idempotency tests.
- App and MCP parity tests.
- Notion disposable import and round trip.
- Sheets disposable workbook and round trip.
- SQLite-only, Notion-only, Sheets-only, Postgres, direct-AI, no-AI, and MCP-disabled mode checks.
- Primary/fallback model-provider failure and disclosure checks.
- In-app configuration export/import, validation, reset, and upgrade-migration checks.
- Workflow checkpoint and compensation tests.
- Visual quality matrix and accessibility.
- Android emulator matrix.
- EAS APK and AAB install and upgrade.
- Physical S23U Health Connect.
- Backup and restore.

Release only when product copy matches live capability. Demo labels disappear one adapter at a time after its gate passes.

## 17. Definition of done

LifeOS is done for Food when:

- A new user can run Food offline without an account.
- SQLite-only, Notion-only, Sheets-only, Postgres-backed, direct-AI, no-AI, MCP-enabled, and MCP-disabled users have coherent supported modes.
- App, Notion, Sheets, SQLite, Postgres, configured AI providers, and MCP share the same semantic contract when enabled.
- No single provider, account, hostname, tunnel, or subscription is mandatory.
- Users can configure a primary and fallback model provider directly; fallback is explicit, observable, and safe.
- Chat is genuinely multi-turn, resumes, streams, cites exact sources, renders structured answers, and acts through typed tools.
- Reversible actions return working Undo.
- Internal multi-agent work is bounded, observable, cancellable, and invisible as branch clutter.
- New domains install by package.
- Web and Android are production-grade and pass the full visual quality matrix.
- iOS is the last platform pass, not a different architecture.
