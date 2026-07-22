# LifeOS Product Pass

This pass moves WonderFood from proof receipts to a visible product shell.

## Product contract

- Food is the active Day 0 domain.
- Health is a companion domain through Health Connect.
- Plants remains the zero-code package sanity check.
- Expo, Notion, Google Sheets, SQLite/Postgres, GPT/MCP share one contract: domain catalog + canonical schemas + domain skills + workflow configs + typed tools.

## User-visible surfaces

- App: responsive Food workspace plus full-screen GPT-like chat on web and Android first, iOS last.
- Chat: multi-turn threads, history, tables/lists, precise source citations, typed actions with receipts and Undo.
- Settings: LifeOS control center, Data home, AI assistant, Health Connect, backup/import/export.
- Notion: human data plane and presentation dashboard.
- Sheets: spreadsheet-primary data plane.
- MCP: server-side bridge exposing the same skills, schemas, resources, validation, and risk-bounded actions.

## Product bar

- No proof-only claims in product copy.
- No hidden “package story”; domain catalog must be visible in-app.
- Schema and data-plane links must be visible from Chat and Settings.
- AI responses must cite app/data-plane sources when available.
- User edits records in normal editors and never edits AI answers. Exact reversible actions apply directly and return Undo.

## Notion benchmark inputs

- LifeOS 2026 remains the canonical WonderFood/LifeOS page.
- 2024 template remains the visual/data-plane baseline.
- LiFE RPG 2.0 sample + empty templates are now benchmark inputs for richer presentation, gamified life loops, wiki/help structure, sample-data onboarding, and template health checks.
- Known Notion risk to avoid: duplicated templates can accidentally freeze `@now` into a timestamp. LifeOS templates should prefer explicit dynamic date properties/buttons where possible and include a visible template-health checklist.

## LiFE RPG parts borrowed

- Character identity → LifeOS profile / food identity: goals, diet style, constraints, skill focus.
- Skills and rank settings → domain skills with visible maturity/status, not hidden config.
- Difficulty presets → operating mode: easy/normal/hardcore rules for how strict plans, goals, and reviews should be.
- Good habit / bad habit → food loops: water, protein, prep, home-cooked meals; waste, overspend, duplicate buying, missed meals.
- Boss fights → bounded campaigns: pantry reset, budget week, nutrition sprint, recipe backlog cleanup.
- Stage P.A.R.A. → Projects, Areas, Resources, Archive across Food now and future domains later.
- Activity log / daily journal → daily food + health context log, source-backed.
- Financial vault → food spending and grocery budget surface.
- RPGenie Tavern → GPT-like assistant posture: conversational, contextual, source-quoting, reversible writes with Undo.
- Sample/empty template split → every domain template needs both beautiful sample data and a clean operational empty state.

## Source sync loop

- Notion → human data plane: dashboards, relations, rollups, template health, and presentation.
- Google Sheets → spreadsheet-primary mirror: auditable rows, formulas, import/export, schema-health checks.
- Expo → web/Android/iOS surface: Food now, local canonical store, Health Connect context, source cards, record editors and Undo receipts.
- MCP/GPT → headless client: same domain catalog, skills, schemas, validation, actions and risk policy.
- Chat answers should cite source handles, not vibes: app snapshot, LifeOS Notion, LifeOS Sheets, MCP schema, template health, and provider web/file citations when available.

## Chat source pack handles

- `[App snapshot]`: on-device kitchen, shopping, recipes, meal logs/plans, receipts, preferences, and Health Connect context.
- `[LifeOS Notion]`: presentation dashboard, relations, rollups, quests, habits, journal, vaults, and template-health checks.
- `[LifeOS Sheets]`: workbook mirror for schema rows, import/export checks, formulas, conflicts, and source handles.
- `[MCP schema]`: `wonderfood://lifeos/domain-catalog-v1` for GPT/plugin parity.
- `[Template health]`: `@now` duplication guard, sample/empty parity, relation/rollup checks, and source visibility.
- Chat injects this source-pack context into provider prompts and local fallback answers, then renders compact source cards in the thread.

## Skill architecture

- Domain skill = user-facing operating brain for a package: Food, Health, Plants, Finance, etc.
- Workflow config = repeatable typed playbook inside/across domains: weekly reset, receipt reconciliation, meal-plan shopping, health export, template-health audit.
- Schema = contract/resource, not usually a top-level skill: command envelope, Notion/Sheets graph, Room tables, validation rules.
- MCP exposes all three: domain skill, schema resources/validators, and workflow actions.
- Default rule: one domain skill per domain; workflows are versioned configs unless they need genuinely distinct judgment; schemas are contracts/resources, never skills.

## Notion UI automation stance

- API creates durable anchors first: `WF_ANCHOR:*` headings/callouts, named data sources, source-pack blocks, and `WF_UI_TODO:*` slots.
- Chrome/Playwright UI automation should target anchors, not raw coordinates.
- UI automation is reserved for Notion-only gaps: buttons, page templates, database layout polish, property pinning/order, and native automations.
- Notion buttons are not treated as API-triggerable runtime primitives. Runtime actions should use Worker/MCP/webhook/property triggers; UI automation may click native buttons only for setup/QA.
