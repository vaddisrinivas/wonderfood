# Hearth — LifeOS vNext prototype

Open `index.html` in a browser. No build, server, assets, or app code needed.

What works:

- Today, Chat, Food views, Kitchen database, editable record detail, sources.
- Search/command palette: `Cmd/Ctrl + K`.
- Capture modal; sample inbox update.
- Food tabs, record relations/activity, automation toggles/runs, skills/MCP, domain picker, and sync states.

Design premise: a local-first LifeOS shell with GPT-quality chat on top of connected, Notion-quality records. Food is active now; domain packages configure later capability.

Sample graph mirrors the current workspace fixture: Basmati Rice, Greek Yogurt, Frozen Spinach; two recipes; meal plan and snack; shopping gaps; and a Trader Joe’s receipt.

This is a standalone product prototype. It does not modify, call, or resemble the current Kotlin/Android UI.

## Product navigation

- **Today:** decisions and exceptions from the active domain; never a settings dump.
- **Chat:** linear multi-turn work with citations, structured answers, context attachment, and record links.
- **Food:** one connected workspace rendered as databases, collections, records, relations, and activity.
- **Search/Capture:** global actions. Search spans records and commands; Capture routes rough text, voice, links, and receipts into typed records.
- **System:** visible agents/automations, skills, MCP connections, domain packages, data homes, and sync health.

## Interaction meaning

- Every blue record/source link is intended to deep-link to the same canonical object—not a duplicate screen model.
- Chat citations open immutable source snapshots; changed sources show freshness without rewriting chat history.
- Exact reversible chat actions execute and show Undo. Ambiguous actions become normal follow-up questions. Sensitive or irreversible changes open the ordinary editor.
- Domain packages add schemas, views, skills, workflows, and agents through config; they do not add hard-coded app architecture.
- SQLite is always the responsive local replica. Notion, Sheets, or Postgres can be the authoritative data home.

Full implementation and cutover contract: `../../build/react-native-reimagination/product-plan.md`.
