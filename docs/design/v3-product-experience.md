# WonderFood V3 product experience

Status: implementation contract  
Date: 2026-07-17

V3 does not add destinations. It exposes the capabilities already present through
one understandable loop:

> Capture → Review → Organize → Decide → Act → History

The V2 design system remains the visual and safety foundation. This document
defines the destination-level information architecture used by the current UI.

## Today — command center

First viewport order:

1. Pending receipt/proposal attention, when present.
2. Compact planned-versus-eaten meal timeline.
3. One explicit Log meal action.
4. Use-soon Kitchen items and recent decisions.
5. Nutrition only when values exist and help a decision.

Do not render a decorative Today hero or a grid of empty metrics.

## Kitchen — working food database

- Search appears before the collection.
- Common filters: All, Use soon, Needs details, storage zone, category.
- Add food, receipt capture, and AI intake are peers; manual entry never looks secondary.
- Cards show stable image/emoji, quantity, zone, freshness, and only useful provenance.
- Detail pages own full nutrition, price/store, lots, notes, relations, and activity.

## Plan — week canvas

- A seven-day strip is the primary local context.
- Planned and actual state remain visually distinct.
- Empty plans show one Plan today action, not repeated calls to action.
- “Plan with my Kitchen” opens AI with an editable request; it does not write Shop directly.
- Missing ingredients become reviewable shopping suggestions.

## Recipes — what can I make?

- Default availability views: Make now, Almost, and All.
- Every recipe exposes have/need matching before opening details.
- Recipe detail owns scale, plan, add missing, image, structured ingredients, and steps.
- Focused cooking mode and leftover/deduction review remain roadmap work.

## Shop — acquisition pipeline

Shop has three local modes, not three destinations:

1. **To buy** — needed checklist grouped by reason/category.
2. **Receipts** — capture evidence and line-level review.
3. **Put away** — bought/extracted items awaiting Kitchen confirmation.

Storage inventory counters do not lead the Shop screen. Bought items remain
recoverable and do not silently disappear.

## AI — contextual work surface

- AI remains a contextual sheet, never a destination.
- Composer accepts text, receipt/photo, and voice with room for a user note.
- Conversation history, page context, provider status, and proposal state remain visible.
- User and assistant text are editable.
- Proposals expose source, confidence, warnings, individual fields, Accept, Edit, and Reject.
- All confirmed writes use the same command boundary as manual/external intake.

## Shared state requirements

Every key screen must have empty, populated, partial-confidence, working, error,
and success/undo states. Compact phone, large font, landscape, tablet, dark mode,
and TalkBack semantics are release checks—not optional polish.
