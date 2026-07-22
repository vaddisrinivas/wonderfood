# LifeOS UI Copy Audit

Captured on Android emulator after the LifeOS rewrite pass.

## Visible item counts

- Settings home: 39 visible text/description items.
- LifeOS top: 47 visible text/description items.
- LifeOS middle: 45 visible text/description items.
- LifeOS source/status: 36 visible text/description items.
- Chat with source-pack answer: 48 visible text/description items.

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

## Architecture copy

- Domain skill: one user-facing skill per domain package, such as Food or Health.
- Workflow skill: one reusable playbook when the workflow has steps, gates, and review rules.
- Schema: a versioned contract/resource shared by Android, Notion, Sheets, and MCP, not usually a top-level skill.
- Notion UI automation: API creates stable anchors; browser automation should target those anchors only for UI-only setup like buttons, database page templates, property order, and page layout polish.

## Remaining design issue

LifeOS is clearer, but still dense. The next product-grade UI pass should split it into compact sections:

- Overview
- Domains
- Source pack
- Skills/workflows/MCP
- Template QA
- Data homes

The current rewrite removes bad proof/config language; it does not yet turn LifeOS into a beautiful dashboard-style native experience.
