# WonderFood 1.0.5 Plan: Household inventory, expenses, and real sync

## Goal

Ship a next release that makes WonderFood useful for all household shopping and pantry tracking, adds receipt-backed expense visibility, and upgrades Notion/Google Sheets/Postgres from foundation-level data homes to polished two-way sync targets.

## Release thesis

WonderFood should answer: what do we have, what can we cook/use, what do we need to buy, where should it go, and what are we spending?

## Phase 1: Generalized household inventory

Status: planned

Scope:
- Introduce item kind: food, household, personal care, cleaning, pet, medicine, other.
- Allow non-food pantry/storage items with quantity, unit, location, notes, price, source, confidence, and archive state.
- Preserve food-specific nutrition/expiry/recipe fields without forcing them onto non-food items.
- Update labels/copy so Shopping can be anything, while Kitchen/Food can still bias toward food.

Acceptance:
- User can add toilet paper, soap, batteries, medicine, or cleaning supplies to inventory and cart.
- Non-food items do not show bogus nutrition.
- Search/filter supports food and non-food categories.
- Archive quantity-zero behavior works for both food and non-food.

## Phase 2: Universal cart and shopping

Status: planned

Scope:
- Shopping list/cart supports any household item.
- Cart rows keep source: manual, low stock, recipe gap, household staple, receipt reorder, AI suggestion.
- Cart categories include produce, dairy, pantry, frozen, cleaning, personal care, household, medicine, pet, other.
- Store/merchant is optional; item can be bought anywhere.

Acceptance:
- User can add arbitrary non-food item to cart.
- Cart grouping works for mixed food/non-food shops.
- Magic suggestions can recommend household staples and recipe gaps separately.
- Plan-to-cart only generates food/recipe gaps unless user enables household staple suggestions.

## Phase 3: Receipt-backed expenses

Status: planned

Scope:
- Add purchase/expense model for receipt totals, line items, taxes, discounts, merchant, category, payment note, and confidence.
- Support household spend categories, not just grocery food.
- Add spend dashboard: this month, last month, weekly average, by merchant, by category, food vs household, waste estimate where available.
- Cart cost estimate uses prior item prices.

Acceptance:
- Receipt with food and non-food items records item expenses separately.
- Spend dashboard shows last-month and current-month household/grocery spend.
- Uncertain categories are marked for review.
- Expense rows link back to receipt and inventory/cart item provenance.

## Phase 4: Polished Google Sheets two-way sync

Status: planned

Scope:
- Make Sheets a real household workbook with readable tabs, filters, validations, and seed/sample data.
- Implement two-way sync for inventory/items, cart, recipes, meal plans, purchases/expenses, and settings/profiles where safe.
- Add conflict detection and reviewed import, not silent overwrite.
- Keep hidden raw sync tabs only for app-owned metadata.

Acceptance:
- User can edit a supported row in Sheets and app imports it as a reviewed change.
- App edits export back to human-readable tabs.
- Conflicts show side-by-side app vs Sheets values.
- No secrets are written to Sheets.

## Phase 5: Polished Notion two-way sync

Status: planned

Scope:
- Create proper Notion databases/views for inventory, cart, recipes, plans, purchases/expenses, and inbox/conflicts.
- Use relation/rollup-friendly shapes without excessive duplication.
- Implement two-way sync with reviewed changes and stable IDs.
- Seed Notion with realistic household data for visual approval.

Acceptance:
- Notion workspace looks useful to a household member on its own.
- User can edit supported Notion properties and app imports changes as reviewed drafts.
- App exports update existing Notion pages instead of duplicating rows.
- Conflict/inbox view is readable in Notion.

## Phase 6: Postgres/Supabase production adapter

Status: planned

Scope:
- Move beyond connection parsing to a safe HTTPS adapter for Postgres/Supabase/PostgREST.
- Define migrations/tables matching the canonical workspace schema.
- Add upsert, pull, conflict, and sync cursor support.
- Keep direct DSN off mobile unless explicitly behind a safe service boundary.

Acceptance:
- Supabase/PostgREST backend can sync canonical workspace objects.
- Row-level household isolation is documented/tested where applicable.
- Offline local changes queue and sync later.
- Secrets stay in Keystore and are never exported.

## Phase 7: Skill pack implementation

Status: planned

Scope:
- Build WonderFood-owned skills, using external repos only as references:
  - wonderfood-recipe-import
  - wonderfood-pantry-normalize
  - wonderfood-meal-plan
  - wonderfood-cart-builder
  - wonderfood-cooking-coach
  - wonderfood-nutrition-estimate
  - wonderfood-receipt-parse
- Keep every skill output as a reviewable proposal.

Acceptance:
- Each skill has typed input/output contract and golden fixtures.
- Skills call deterministic parsers/providers first where available.
- AI is used for fuzzy extraction/ranking/coaching, not silent writes.

## Phase 8: Release hardening

Status: planned

Scope:
- Complete cross-domain golden parity for inventory, cart, plans, recipes, receipts, expenses, and sync.
- Validate on emulator and physical phone.
- Publish signed 1.0.5 release with clear notes and migration guidance.

Acceptance:
- Local quality, connected emulator quality, GitHub quality, and release workflow pass.
- Notion and Sheets are visually reviewed with seed data.
- Release notes remove the 1.0.4 sync limitation wording if accepted.
