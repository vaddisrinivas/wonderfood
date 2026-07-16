# WonderFood V2 UX And Design System Contract

Status: frozen for WF-B01  
Date: 2026-07-16  
Applies to: Android 1.0 production UI

## Purpose

WonderFood V2 replaces the prototype chrome with one coherent food workspace. The app
should feel like a local-first, Notion-like food database that is fast enough for daily
kitchen use and careful enough for uncertain AI, nutrition, and inventory changes.

The primary user job is to answer: what do I have, what can I make, what will I eat, and
what should I buy next? The UI prioritizes reviewable food objects and next actions over
dashboard metrics.

## Frozen Destination Model

The app has exactly five top-level destinations:

| Destination | Purpose | Primary objects | Primary action |
|---|---|---|---|
| Today | What is planned, eaten, pending, or next today. | Calendar day, meal slots, meal logs, pending reviews. | Log, cook, eat, review. |
| Kitchen | What is physically available. | Food pages, stock lots, zones, transactions. | Add, use, move, correct. |
| Plan | Future meals and plan versus actual. | Week, day pages, slots, draft plans. | Plan, move, skip, replace. |
| Recipes | Computable recipes and cooking. | Recipe pages, ingredient rows, steps, versions. | Cook, scale, plan, add missing. |
| Shop | Buying and put-away workflow. | Shopping items, origins, trips, receipts, put-away queue. | Check off, receive, put away. |

Hard rules:

- There is no `More` tab.
- There is no `AI` tab.
- There is no phone drawer.
- AI is a floating or contextual action, not a destination.
- Settings, profile, health, AI provider setup, theme, export, and restore are secondary
  routes opened from top app bar actions, overflow menus, object pages, or settings rows.
- The bottom navigation on phones and the adaptive rail on tablets show only Today,
  Kitchen, Plan, Recipes, and Shop.
- Do not add repeated navigation counters or dashboard counters. Counts belong where they
  explain a nearby decision, such as "3 items expiring soon" inside Kitchen.

## Navigation Behavior

Phones use a Material 3 bottom navigation bar with five items. Each destination restores
its own scroll position and filter state. Detail pages push on top of the selected
destination and keep the bottom bar visible unless the user enters focused cooking or
shopping mode.

Tablets and landscape use a Material 3 navigation rail with the same five items. Do not
use a permanent drawer as the main app shell. Expanded layouts may add supporting panes
beside the active destination, such as a calendar preview beside Plan, but the rail remains
the only top-level navigation.

AI entry points:

- Floating action button on list-like destinations when capture is the most likely next
  action.
- Contextual action in object pages, selection toolbars, empty states, and review sheets.
- Collapsed bottom sheet for speak, photo, barcode, receipt, text, and share intake.
- Proposal review sheet before any uncertain or destructive write.

Search is global from the top app bar and local within dense collections. Search screens
must show recent items, common filters, or personalized suggestions instead of a blank page.

## Page Anatomy

Every destination follows this vertical anatomy:

1. Top app bar: destination title, search, contextual actions, overflow.
2. Local context: date strip, active filter chips, selected collection, or mode switch.
3. Primary content: real food data first. Avoid hero panels that hide the list/grid.
4. Context rail or compact summary: only when it explains the current task.
5. Bottom/FAB action: capture, add, cook, shop, or review.

Every object detail page follows a Notion-like page pattern:

1. Page header with image or icon, title, aliases/status, and primary action.
2. Properties block with editable rows, source badges, confidence badges, and timestamps.
3. Relation blocks that link to foods, lots, recipes, meals, shopping, receipts, and events.
4. Activity timeline with append-only history and undoable actions.
5. Overflow menu for archive, delete, export, duplicate, or advanced correction.

Property rows use label, value, provenance, confidence, and affordance. The value is more
visually prominent than the label. Unknown values are explicit and never rendered as zero.

## Destination Contracts

### Today

Today answers "what needs attention now?" It is the only destination where the current date
is visually prominent.

- First viewport: date/week strip, next meal or pending review, compact planned versus
  actual meal timeline.
- Secondary content: use-soon rail, recent activity, optional nutrition summary when
  enabled.
- Empty day: show a calm prompt to plan, log, or ask AI. Collapse empty sections.
- Health Connect status never occupies hero space. Health is a secondary row or detail
  section under settings and meal/day pages.

### Kitchen

Kitchen answers "what do I have?" It must show food before controls.

- First viewport: search, compact filters, actionable food or lot list/grid.
- Modes: gallery and list. Preserve the user's choice.
- Filters: zone, freshness, category, low/out, needs review. Keep filters as horizontal
  chips; if more controls are needed, open a bottom sheet.
- Food cards: image/icon, name, zone, lot status, expiry/freshness signal, confidence/source
  badges where relevant.
- No visible Remove button on cards. Destructive actions live in overflow and confirmation.

### Plan

Plan answers "what will happen?" It owns future intent, draft plans, accepted plans, and
plan versus actual.

- First viewport: week strip or compact month switcher, day slots, plan status.
- Day pages show planned meals, actual meals, plan gaps, related shopping items, and kitchen
  events.
- Actions: plan, move, skip, swap, replace, eat. Invalid state moves are disabled with clear
  reason text.
- Plan gaps can generate reviewable shopping items; they do not silently mutate Shop.

### Recipes

Recipes answers "what can I make?" It treats recipes as structured pages, not text blobs.

- First viewport: searchable recipe gallery/list with availability status.
- Recipe page: image, title, servings, time, tags, ingredient sections, have/need matching,
  steps, notes, versions, related foods, last cooked.
- Cooking mode: focused, readable at large text, keeps screen awake, exposes timers and
  step progress.
- Finish flow: summary, proposed deductions, leftovers, and undo snackbar.

### Shop

Shop answers "what should I buy and where does it go?"

- First viewport: grouped checklist with origin/reason labels visible.
- Modes: normal list, shopping mode, receipt review, put-away queue.
- Bought items do not disappear. They move to a bought state and remain recoverable.
- Receipt rows show source, confidence, matched food/lot, and review status.
- Put-away connects purchases back to Kitchen lots.

## Density And Layout

WonderFood is an everyday utility, so density is compact but not cramped.

- Base grid: 4dp and 8dp increments only.
- Screen padding: 16dp compact, 24dp medium, 32dp expanded.
- Card/list row radius: 8dp unless a Material component requires otherwise.
- Collection row height: 56dp minimum for simple rows, 72dp to 96dp when showing image,
  status, and two metadata lines.
- Detail page max text width: keep long notes and instructions readable, ideally under
  600dp.
- Touch targets: 48dp minimum in production UI.
- Avoid nested decorative cards. Use sections, dividers, list rows, and full-width bands.
- Avoid giant hero blocks, repeated stat tiles, and control panels above content.

Compact filters:

- Use single-line horizontal chips for the common filters.
- Chips may scroll horizontally; they must not wrap into a tall block in the first viewport.
- Advanced filters, sort, grouping, and saved views open in modal bottom sheets on phones.
- On tablets, advanced filters may use a side sheet or supporting pane.

## Color Semantics

Use Material 3 color roles with WonderFood food semantics.

| Semantic | Material role | Use |
|---|---|---|
| Herb primary | `primary`, `primaryContainer` | Primary action, selected nav, safe food add/use actions. |
| Olive secondary | `secondary`, `secondaryContainer` | Kitchen zones, neutral categorization, supportive chips. |
| Tomato tertiary | `tertiary`, `tertiaryContainer` | Attention, expiry, review-needed, but not hard errors. |
| Oat surface | `background`, `surface`, `surfaceVariant` | App canvas, object pages, cards, grouped rows. |
| Ink text | `onBackground`, `onSurface` | Headings and primary values. |
| Muted ink | `onSurfaceVariant` | Labels, metadata, helper text. |
| Error | `error`, `errorContainer` | Delete, discard, failed sync, invalid command. |

Color rules:

- Strong red/error is reserved for destructive or failed states.
- Nutrition and health use secondary styling unless the user explicitly opens health detail.
- Confidence and source badges use soft containers plus text, never loud pills everywhere.
- Unknown nutrition, unknown quantity, or estimated AI output must be visually distinct from
  exact verified values.
- Dark mode keeps the same semantic mapping and preserves contrast.

## Typography

Use the app's Material 3 typography as the source of truth.

- Destination title: `headlineSmall` or `titleLarge` depending on app bar density.
- Page title: `headlineMedium` on detail pages, `titleLarge` in compact headers.
- Section headings: `titleMedium`.
- Row values: `bodyLarge` or `titleMedium` when the value is the decision.
- Labels, chips, badges, metadata: `labelLarge`, `labelMedium`, or `bodySmall`.
- Large numbers use tabular/monospace treatment only when comparison matters.
- Maximum practical hierarchy per screen: four sizes and two weights.
- Letter spacing remains 0. Do not scale type with viewport width.

## Imagery And Icons

Imagery should reveal the actual food, receipt, recipe, or state when available.

- Food and recipe pages prefer user photos.
- If no photo exists, use a stable category illustration or Material icon in a soft
  container.
- Do not mix random stock styles inside collections.
- Receipt thumbnails are inspectable and open a detail/review page.
- Icons come from Material icons and support the action label or object type.
- Images must have stable aspect ratios to avoid layout shift.

## States

Empty states:

- Explain the next useful action in one sentence.
- Include one primary CTA and, when useful, one secondary AI/contextual CTA.
- Use a simple icon or illustration. Avoid generic "nothing here" copy.

Loading states:

- Prefer skeleton rows or quiet progress indicators in the content area.
- Keep navigation and already loaded content stable.
- Long AI work uses a bottom sheet or inline review placeholder with cancel/dismiss where
  safe.

Error states:

- State what failed, what remains safe, and how to retry.
- Do not hide local data because AI, Health Connect, barcode, or network enrichment failed.
- Failed writes show no partial success unless the engine confirms it.

Success states:

- Use snackbars for routine completion with Undo when available.
- Use a compact summary after cooking, shopping, importing, or accepting proposals.
- Success feedback can be warm, but it must not block repeated kitchen work.

## Mutation And Safety Rules

All writes go through reviewable commands when risk or uncertainty exists.

- Destructive actions such as delete, discard, archive, clear, and permanent correction live
  in an overflow menu or advanced section.
- Destructive actions require confirmation with the object name, consequence, and safer
  alternative when one exists.
- Cards and rows may expose safe primary actions like Cook, Add, Plan, Bought, Use, Move,
  or Review.
- Archive is preferred over delete for user-created food history.
- Uncertain AI proposals never silently write.
- Uncertain pantry deductions never silently reduce stock.
- Undo appears after reversible changes and links back to the affected page when useful.

## Health And Nutrition

Health is secondary. WonderFood can support goals and Health Connect, but the app is not a
medical product.

- Do not create a Health top-level destination.
- Do not put Health Connect connection state in the Today hero.
- Nutrition summaries appear only where they help food decisions: Today summary, recipe
  details, meal/day pages, and settings.
- Unknown nutrition remains unknown. Do not show zero calories/protein/carbs/fat for
  missing values.
- Every nutrition value exposes source and confidence where detail matters.

## Settings And Secondary Routes

Settings are reachable from account/profile/top app bar actions, destination overflow, and
object pages. They are not a top-level tab.

Settings groups:

- Taste and constraints.
- AI behavior and provider advanced fields.
- Health goals and Health Connect.
- Data, export, restore, and backup.
- Theme.
- Stores and brands.

Each group uses compact rows and detail pages. Avoid a "More" grid.

## Preview And QA Contract

WF-B01 is docs-only because the current Compose app is still prototype chrome and Wave 0
architecture is settling. WF-B02 and WF-B03 must implement previews against this contract.

Required preview/review matrix for later UI work:

- Compact phone, light and dark.
- Compact phone with large font.
- Medium landscape.
- Expanded tablet with navigation rail.
- Empty, loading, error, success, populated, and long-list states.
- RTL and long food/recipe names for rows, chips, buttons, and page headers.

## Implementation Checklist

- Five top-level destinations only: Today, Kitchen, Plan, Recipes, Shop.
- No More tab, AI tab, or phone drawer.
- AI is floating/contextual and opens a sheet or review flow.
- Real food content appears before controls or metrics.
- Filters stay compact as horizontal chips and sheets.
- Pages use Notion-like headers, properties, relations, and activity.
- Health and nutrition remain secondary.
- No repeated dashboard counters.
- Destructive actions are in overflow or advanced areas with confirmation.
- Phone uses bottom nav; tablet uses navigation rail.
- Unknown and estimated values are visually distinct from exact values.
