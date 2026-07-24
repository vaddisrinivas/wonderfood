# Findings and decisions

## User correction

WonderFood currently has zero production users. Backward compatibility, migration preservation, and minimizing schema churn are not release requirements. The correct optimization is a clean replacement architecture.

The user further clarified that Notion and Google Sheets are primary household experiences that must work independently of the Android app. Treating them as exports, mirrors, or machine-normalized dumps is a product failure.

## Verified repository facts

- The live app model and canonical core model are separate.
- `FoodChatStore.readMemory()` is currently the bridge to visible app data.
- Backend gateways and workspace schema foundations already exist and can supply useful provider/API code, but should not dictate the new domain.
- The existing typed proposal, command validation, source, confidence, and rollback ideas remain valuable.

## Decisions

- Delete legacy persistence/bridge code after migrating callers.
- SQLite remains present in every mode as the offline replica and safety layer.
- Exactly one backend is active for a household.
- Notion receives first provider priority because it is the primary household interface.
- Google Sheets receives first-class tables and metadata rather than raw ranges.
- Postgres/Supabase uses HTTPS, user authentication, and mandatory RLS; no direct Android DSN.
- Schema.org is an interoperability layer, not the internal model.
- One 1.0.5 release and final PR are acceptable, implemented as ordered commits.
- Conflict handling is deliberately proportional: retain enough three-way state to prevent silent loss of overlapping high-risk edits, but do not build CRDTs, vector clocks, multi-provider fan-out, or a general merge editor.
- In a connected Notion/Sheets household, the chosen workspace has precedence for overlapping low-risk human fields. High-risk quantity, money, archive, relation, and schedule overlaps enter `Needs review`.
- Notion and Sheets receive provider-native dashboards, views, formulas, and workflows that remain useful without the app.
- Community hosting and owned large recipe catalog remain future products; the private household schema stays ready for them.

## External platform constraints retained from research

- Notion can manage data-source schemas and pages, but polished views require a user/template path; use a duplicable template.
- Notion mappings should retain property IDs because names can change.
- Google Sheets supports Tables, validations, filters, protected ranges, batch updates, and developer metadata suitable for stable human rows.
- Supabase exposed tables require RLS; service-role credentials must never ship to Android.

## 2026-07-19 official platform refresh

- Notion API version `2026-03-11` can create/update/query data sources and their properties, but the official update-data-source reference says view management is not supported through the API. The polished setup therefore requires a duplicable Notion template plus API binding: https://developers.notion.com/reference/update-a-data-source
- Notion data source properties include relations and rollups; related data sources must be shared with the connection. Property IDs remain stable bindings even when visible names change: https://developers.notion.com/reference/property-object
- Notion users can create multiple database views with independent filters, sorts, groups, layouts, and conditional colors, which supports a real household dashboard/template: https://www.notion.com/help/views-filters-and-sorts
- The current Google Sheets API supports first-class Tables through `addTable`, typed columns, filters, views, protected ranges, and table-aware row appends: https://developers.google.com/workspace/sheets/api/guides/tables
- Developer metadata can bind semantic rows/columns and remains associated as a spreadsheet is edited, allowing sync to survive sorting and row movement: https://developers.google.com/workspace/sheets/api/guides/metadata
- Sheets batch updates are atomic, and visible tables can use formulas, validations, charts, named ranges, filters, and protected ranges without Apps Script: https://developers.google.com/workspace/sheets/api/guides/batchupdate

## Scope control

The architecture is intentionally broad enough for the requested future product, but the 1.0.5 implementation stops at a private household workspace. Public recipe/community hosting adds identity, moderation, search indexing, publication, and abuse controls and should be a separate hosted system.

## Errors encountered

- 2026-07-20: Kotlin incremental/daemon compile twice hit missing generated class or backup files under `app/build/intermediates/built_in_kotlinc/...`; Gradle fallback no-daemon compilation completed successfully. Treat as generated build cache fragility, not source failure, unless it repeats without fallback.
- 2026-07-20: Local emulator proof became unavailable after ADB reported no devices. Starting `Pixel_3a_API_34_extension_level_7_arm64-v8a` needed `-skin 1080x2220` because the saved `pixel_3a` skin was missing, then crashed with `Failed to allocate ColorBuffer with Vulkan backing` / `Failed to find memory type for ColorBuffers`. A retry with `-gpu swiftshader_indirect` did not produce an ADB device within the bounded wait.
