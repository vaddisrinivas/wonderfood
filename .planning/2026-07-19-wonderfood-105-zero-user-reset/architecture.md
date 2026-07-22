# Architecture decision brief

## Problem

WonderFood needs one coherent household domain that can power the Android app while allowing Notion or Google Sheets to operate as complete, polished household products without the app.

## Current shape

- The live Android path uses `FoodMemory` and `FoodChatStore`.
- `core:model` separately defines a canonical food snapshot.
- Legacy export/import bridges translate between the two shapes.
- Sheets has structured workspace primitives plus snapshot synchronization scaffolding.
- Notion and Postgres currently include foundation/snapshot behavior rather than complete structured round trips.
- AI already has typed proposal and command concepts that should be retained.

## Decision

Replace the legacy runtime with a canonical household repository. SQLite always stores the app's working replica, sync state, outbox, and recovery data. One selected data home supplies the household authority. In Notion or Sheets mode, the human workspace is not a projection-only mirror: it is a fully usable product whose supported edits become canonical commands when WonderFood connects. Manual app UI, workspace edits, imports, external commands, and AI all converge on the same canonical command boundary.

```text
UI / Share / Import / AI skill
            |
      ChangeProposal
            |
    Validate + review
            |
     CanonicalCommand
            |
   HouseholdRepository
      |           |
   SQLite      Sync outbox
                  |
          Active DataHomeAdapter
       Local | Notion | Sheets | Postgres
```

## Rejected alternatives

### Preserve the legacy runtime and add more bridges

Rejected because zero users remove the only meaningful benefit. It would preserve duplicate models and multiply mapper/test burden across three providers.

### Make every provider a simultaneous peer

Rejected for 1.0.5 because Notion, Sheets, and Postgres have different consistency and transaction capabilities. One active data home keeps ownership and conflict behavior understandable.

### Use Schema.org as the internal schema

Rejected because Schema.org is excellent for recipe/product interoperability but does not model local inventory lots, reviewed proposals, sync bases, conflicts, or offline commands.

## Core invariants

- IDs are app-generated UUIDs and never derived from names, Sheet rows, Notion pages, or database sequences.
- Archive/tombstone is the cross-provider deletion primitive. Physical deletion is maintenance-only.
- Quantity uses decimal value plus explicit unit; blank quantity is unknown, never zero.
- Money uses integer minor units plus ISO currency; missing money stays null.
- Every domain change records source, actor/device, timestamp, command ID, and resulting revision.
- Food-only data is optional and valid only for food-like items.
- Purchase lines, not receipts or shopping lines, are the source for spending.
- Inventory lots, not catalog items, own on-hand quantity, purchase provenance, location, and expiry.
- AI, importers, and remote adapters cannot write domain tables directly.
- One household has exactly one active data home.

## Sync envelope

Every synced record carries:

- `householdId`
- `entityType`
- `entityId`
- `schemaVersion`
- `revision`
- `createdAt`
- `updatedAt`
- `archivedAt`
- `originDeviceId`
- `lastCommandId`
- `payloadHash`

Local sync metadata stores:

- backend kind and connection ID
- remote object/row ID
- last pulled cursor
- last synced local revision
- last synced remote revision/fingerprint
- base payload for three-way comparison
- retry count and last error

## Conflict policy

Conflicts matter only as data-loss protection. They are not a primary product surface and do not justify a distributed-database design for a small household.

- No conflict: only one side changed from the base.
- Automatic import/push: one side changed, including ordinary remote human edits.
- Automatic merge: both sides changed disjoint whitelisted fields.
- Provider-home precedence: overlapping low-risk text, notes, tags, or display metadata use the selected workspace value; the displaced local value stays in recovery history.
- Review required: both sides changed the same quantity, money, archive/delete state, ingredient relation, meal date/servings, or another explicitly high-risk field; or either payload is invalid.
- Resolution choices: keep app, keep data home, merge selected fields, duplicate as new object, or archive.
- Every resolution is a canonical command and is itself syncable and auditable.

No CRDT, vector clock, simultaneous provider fan-out, or general-purpose merge editor is included. The review UX is a badge and focused `Needs review` list.

## Provider projection

The canonical model is richer than each human workspace. Adapters project only useful concepts while retaining stable binding metadata.

### Notion

- Household surfaces: WonderFood Home, Kitchen, Shopping, Meals, Recipes, Spending.
- Advanced/supporting data sources: Stock Lots, Recipe Ingredients, Purchase Lines, Workspace/Bindings, and Needs review.
- Use a duplicable template for polished linked views because the Notion API cannot manage database views.
- Use relations, rollups, formulas, buttons, page templates, charts, filters, sorts, groups, and realistic seed data so core work remains useful without WonderFood.
- Accept human convenience inputs such as `Buy next`, ingredient text, and quick purchase totals; normalize them on app connection without making Notion users maintain machine tables.
- Bind by data-source/property IDs; display names may be renamed.

### Google Sheets

- Visible tabs: Home, Kitchen, Shopping, Meals, Recipes, Spending, Lists & Help.
- Hidden tabs: `_wf_meta`, `_wf_lots`, `_wf_ingredients`, `_wf_purchase_lines`, `_wf_bindings` only where needed.
- Use Tables, typed columns, dropdowns, checkboxes, saved views, named ranges, formulas, charts, conditional formatting, developer metadata, protected machine columns, and atomic batch updates.
- No Apps Script or deployment is required. Visible formulas and user-added columns are preserved across app sync.

### Postgres/Supabase

- Canonical relational tables plus sync/change tables.
- HTTPS client APIs only.
- Authenticated membership and mandatory RLS.
- Realtime is optional acceleration, never the correctness mechanism.

## Failure modes

- Provider unavailable: continue locally and retain outbox.
- Auth expired: stop remote operations, preserve local changes, request reauthentication.
- Schema drift: stop writes, show repair/upgrade flow, never overwrite unknown columns/properties.
- Partial push: use idempotency keys and retry only unacknowledged operations.
- Duplicate remote record: quarantine in Sync Inbox and require merge/archive review.
- Remote deletion: import as an archive proposal unless the record is unchanged locally and deletion policy allows automatic archive.
- Corrupt remote value: retain raw evidence, reject domain mutation, surface actionable validation.
- App never reconnects: Notion/Sheets core CRUD, planning, cart, recipe, and spending workflows remain usable; only app-only AI/capture/matching enhancements stop refreshing.

## Blast radius

- `core:model`: replace food-only snapshot model with canonical household entities.
- `core:data`: new Room schema, repository, outbox, bindings, conflicts, provider contracts.
- `core:engine`: canonical commands, validation, audit, atomic transactions.
- `core:ai`: skill contracts targeting canonical proposals.
- `app/data`: remove `FoodMemory` persistence and bridge code.
- `app/sync`: rewrite providers against `DataHomeAdapter`.
- `app/ui/main`: onboarding, quick add, gestures, household cart, spending, sync inbox.
- Tests, fixtures, documentation, setup guides, release automation, and provider proof scripts.

## Acceptance matrix

| Capability | Local | Notion | Sheets | Postgres |
|---|---|---|---|---|
| Create/update/archive item | Required | Live round trip | Live round trip | Live round trip |
| Food and non-food inventory | Required | Required | Required | Required |
| Shopping and plan gaps | Required | Required | Required | Required |
| Receipt and purchase lines | Required | Required | Required | Required |
| Spending summaries | Required | View/projection | Formulas/projection | Query/projection |
| Offline write/retry | Required | Required | Required | Required |
| Remote human edit | N/A | Required | Required | Required |
| Three-way conflict | Repository test | Live proof | Live proof | Live proof |
| Archive/tombstone | Required | Required | Required | Required |
| Auth failure and repair | N/A | Required | Required | Required |
| Secret leakage check | Required | Required | Required | Required |
| AI proposal parity | Required | Same commands | Same commands | Same commands |

## Rollback

- Keep `v1.0.4` release/tag unchanged.
- Implement on a `codex/` release branch until the full reset is usable.
- Before destructive provider operations, create `latest-safety` and provision disposable proof workspaces.
- Provider disconnect never deletes the SQLite replica.
- Postgres migrations use a dedicated schema/version and reversible down scripts during development.
- If a phase fails, revert its commit without restoring the deleted legacy path until a replacement decision is made.
