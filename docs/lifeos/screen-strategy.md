# LifeOS screen strategy

Status: active redesign contract  
Date: 2026-07-22

## Verdict

The current app is too much like an implementation report. LifeOS should feel like a near-Notion workspace with a GPT-grade assistant, not a pile of runtime cards.

Old WonderFood was closer because it showed daily objects first: meals, review queue, pantry-aware suggestions, and one clear Ask action. Keep that spirit. Expand it to all domains.

## Product model

LifeOS has three layers:

1. Data plane: records, properties, relations, rollups, sources, history.
2. Work plane: domain screens, views, cards, tables, timelines, review queues.
3. Control plane: providers, domains, skills, MCP tools, schemas, sync, backups.

The data plane is user-visible. The control plane is editable but not daily UX.

## Top-level app shell

Use five primary destinations:

| Destination | Job | Default content |
|---|---|---|
| Home | What needs attention now | Today, review queue, active domain highlights, recent graph changes |
| Domain | Work inside the active life space | Food now; Health/Plants/etc later from config |
| Chat | GPT-like assistant over allowed sources | Multi-turn chat, citations, tables, source cards, actions |
| Sources | Trust and sync | Notion, Sheets, local, Postgres, import/export receipts |
| Settings | Configure LifeOS | Providers, domains, skills, agents, schemas, MCP, appearance, backups |

No separate top-level System screen. System belongs inside Settings as a Control Deck.

## Domain screen pattern

Every domain gets the same structure:

1. Domain Home
   - High-value overview only.
   - Today/now items.
   - Review queue.
   - Saved views.
   - Recent changes.
   - Ask contextual button.

2. Views
   - Generated from domain config.
   - Can be board, table, gallery, timeline, calendar, map, checklist, detail stack, or custom widget.
   - Food examples: Overview, Kitchen, Meals, Recipes, Shopping.
   - Health examples: Overview, Metrics, Sleep, Activity, Labs, Meds.
   - Plants examples: Overview, Watering, Rooms, Species, Problems.
   - Collection atlas chips open `/collection/[id]` pages for the active domain.

3. Record detail
   - Notion-like page.
   - Title, icon/image, status.
   - Editable properties.
   - Relations and rollups.
   - Source/citation block.
   - Activity/history.
   - Ask about this record.

4. Collection settings
   - Properties.
   - Views.
   - Sort/filter/group.
   - Source mappings.
   - AI/skill behavior.

5. Collection detail
   - One generic screen per managed collection.
   - Shows records, view mode, filter, sort, review count, schema relations, source homes and actions.
   - Exposes a property kit and visual identity from the manifest so Notion/Sheets/local fields, icons and image slots stay visible.
   - Works from the active domain manifest; adding a domain should not require a new route.

## Visual identity

- Domain packages own visual identity for domains, surfaces, collections, statuses, actions, sources, skills and agents.
- Every token can carry `emoji`, `icon`, optional `image_url` and accent tone.
- UI must read these tokens first and fall back only when config is missing.
- App settings can override bundled tokens through `runtime.visualIdentityOverrides`, so personal icon/image choices do not require a rebuild.
- Notion template icons/covers and Sheets dashboard symbols should use the same token map.

## Home page

Home is not a config dashboard. It should answer:

- What should I do now?
- What changed?
- What needs review?
- What can AI help with safely?

First viewport:

1. Greeting/date and search.
2. One primary now card.
3. Review queue.
4. Active domain strip.
5. Recent/source-backed changes.

Food-first first viewport:

- Tonight: planned dinner or missing plan.
- Use soon: expiring pantry item.
- Review: receipt/proposal/sync conflict.
- Ask: one composer CTA.

## Chat screen

Chat must be a real work surface:

- Multi-turn thread list and active conversation.
- Messages can render paragraphs, tables, checklists, record cards, citations, and action proposals.
- Assistant answers quote Notion/Sheets/local/web sources where allowed.
- Every action is reviewable before write.
- Sources are visible inline, not buried.
- Chat can open record details and records can open chat with context.

Do not show “edit AI response” as a headline feature. Better actions:

- Copy
- Save to record
- Create proposal
- Open sources
- Ask follow-up
- Regenerate with source limits

## Sources screen

Sources is trust, not plumbing.

Top content:

- Connected sources.
- Last sync.
- What changed.
- What failed.
- What data is local only.
- Import/export.

Provider setup lives here or Settings, but data trust stays visible here.

## Settings and Control Deck

Settings should expose all config from the app:

- AI providers and fallback order.
- Domains.
- Skills.
- Agents.
- MCP resources/tools.
- Schemas.
- Sources and mappings.
- Sync policy.
- Appearance.
- Backup/export/restore.
- Privacy and permissions.

Control Deck is a compact advanced view inside Settings. It should not read like a roadmap.

## Glance-style YAML decision

Use the idea, not Glance itself.

Glance proves that a dashboard can be declared from YAML: pages, columns, widgets, themes, includes. That is useful for LifeOS presentation and portability.

But raw Glance YAML is not enough for LifeOS because LifeOS needs:

- Typed records.
- Relations and rollups.
- Source provenance.
- Reversible actions.
- Chat context.
- Skills and MCP contracts.
- Notion/Sheets/local sync.
- In-app editing for non-technical users.

Adopt a LifeOS presentation file:

```yaml
lifeos: 2026.7
profile: food-first
home:
  sections:
    - type: now-card
      source: active-domain
    - type: review-queue
    - type: domain-strip
domains:
  food:
    views:
      - id: kitchen
        type: gallery
        collection: inventory
        group_by: freshness
      - id: meals
        type: calendar
        collection: meal_plan
      - id: shopping
        type: checklist
        collection: shopping_item
settings:
  editable:
    - providers
    - domains
    - skills
    - schemas
    - sources
```

This file compiles into the same runtime profile as the app forms. Config Studio can export/import the portable profile as YAML or JSON.

Current in-app section IDs:

| Surface | `sectionOrder` values |
|---|---|
| Home | `now`, `review`, `lifeSpaces`, `recent`, `sourceTrust`, `control` |
| Food | `hero`, `tabs`, `manifest`, `collections`, `widgets`, `workspace`, `attention`, `view`, `package` |
| Chat | `threads`, `sources`, `messages`, `promptRail`, `context` |
| Record | `hero`, `nutrition`, `ingredients`, `instructions`, `history`, `editableNote`, `properties`, `relations`, `provenance` |

Home, Food, Chat, and Record render from this order in app settings. The same profile model is the contract for future arbitrary widgets/views.

Food supports profile widgets now. Each line in `runtime.surfaceConfig.food.widgets` is:

```text
Title|Detail|tone|/route
```

Example: `Food sources|Open provider trust.|blue|/sources`.

## Implementation order

1. Replace current System route with Settings/Control Deck language and actions.
2. Upgrade bottom tabs to Home, Domain, Chat, Sources, Settings.
3. Make Domain screen render domain home plus config-defined views.
4. Make record detail feel Notion-like: properties, relations, sources, activity.
5. Upgrade Chat rendering: tables, source cards, record cards, long threads.
6. Add in-app config editor for domains, skills, MCP, schemas, sources.
7. Add LifeOS YAML import/export as a presentation/profile layer.
8. Polish visual system: less beige, stronger hierarchy, better empty/loading/error states.

## Screen quality bar

Every screen must pass:

- First viewport has real user value.
- No implementation-report copy.
- One primary action.
- Source trust visible when needed.
- Config is editable from app.
- Domain can be swapped without code.
- Looks good on phone first.
- Works without Notion, without Sheets, without server.
