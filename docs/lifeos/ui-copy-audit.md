# LifeOS UI Copy Audit

Captured on Android emulator and S23U after the LifeOS rewrite pass.

## Visible item counts

- S23U home/proof entry: 25 visible text/description items.
- S23U Settings home: 40 visible text/description items.
- S23U LifeOS top: 51 visible text/description items.
- Emulator Settings home: 39 visible text/description items.
- Emulator LifeOS top: 44 visible text/description items.
- Emulator LifeOS lower/source pack: 52 visible text/description items.
- Emulator LifeOS skills/detail toggle: 45 visible text/description items.
- Emulator LifeOS architecture detail after explicit tap: 41 visible text/description items.
- Emulator Chat with source-pack answer: 48 visible text/description items.

## Text rewrite decisions

- `LifeOS control center` became `LifeOS`.
- `AI assistant` became `Model + skills`.
- `Domain packages` became `Domains`.
- `Native surface map` became `App surfaces`.
- `Schema surfaces` became `Data model`.
- `Skills & MCP` became `Skills, workflows, MCP`.
- `Operating loops` became `Operating playbooks`.
- `Source sync loop` became `Source loop`.
- `Template health` became `Template QA`.
- `Data planes` became `Data homes`.
- `Runtime status` became `Live status`.
- `Capability center` became `Chat context`.
- `Choose data home is active` and `On this phone is active` became `Phone store is active`.
- Long architecture sections now hide behind `Show architecture details`.

## Architecture copy

- Domain skill: one user-facing skill per domain package, such as Food or Health.
- Workflow skill: one reusable playbook when the workflow has steps, gates, and review rules.
- Schema: a versioned contract/resource shared by Android, Notion, Sheets, and MCP, not usually a top-level skill.
- Notion UI automation: API creates stable anchors; browser automation should target those anchors only for UI-only setup like buttons, database page templates, property order, and page layout polish.

## Screen-by-screen rewrite

- Settings is now a product menu, not a proof receipt: LifeOS, Food profile, Goals & health, Model + skills, Appearance, Canonical household store.
- LifeOS top now answers what is live: Food workspace, source surfaces, data home, Health Connect, domain picker.
- LifeOS lower now uses compact chips and short source-pack rows instead of dumping every schema surface as equal weight text.
- Skills now explains the rule plainly: domain skill per domain, workflow skill per repeatable playbook, schema as shared contract unless it has behavior.
- Architecture details remain available, but only after a deliberate tap.
- Chat now says what it can read/cite and shows source handles/cards instead of pretending to be a generic textbox.

## Remaining design issue

LifeOS is materially cleaner, but still not visually great. The next product-grade UI pass should turn these sections into a real dashboard with hierarchy, cards, and fewer always-visible labels:

- Overview
- Domains
- Source pack
- Skills/workflows/MCP
- Template QA
- Data homes

This pass removed bad proof/config language and reduced the default architecture dump. It still needs a stronger visual design pass before it feels like GPT + Notion in one place.
