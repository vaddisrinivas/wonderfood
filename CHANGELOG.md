# Changelog

All user-visible changes are recorded here. WonderFood follows Keep a Changelog
structure.

## Unreleased

## 1.0.0 - 2026-07-17

### Added

- Five-destination Android workspace for Today, Kitchen, Plan, Recipes, and Shop.
- Manual food, grocery, recipe, meal, plan, receipt, CSV, share, and app-command intake.
- Editable AI conversations, persistent chat history, visible AI context, and editable proposals.
- Deterministic Primary → Fallback provider routing with no round robin.
- Receipt notes, line-level proposal review, store/price provenance, storage, expiry, and nutrition fields.
- Bounded deep-link and proposal-package commands with atomic review and audit history.
- Encrypted local/Google Drive backup flow, optional Health Connect, and Android automation contracts.

### Changed

- Receipt purchases default to Kitchen put-away review instead of the shopping-needed state.
- Unknown nutrition remains unset instead of appearing as zero.
- Food imagery and emoji prefer stored identity before deterministic fallback.
- AI prompts/skills are visible and editable under Settings → AI assistant.

### Security

- Automatic Android backup is disabled; provider credentials remain Keystore-protected.
- External links, shares, and intents stage untrusted drafts and cannot silently mutate local data.
