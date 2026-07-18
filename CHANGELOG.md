# Changelog

All user-visible changes are recorded here. WonderFood follows Keep a Changelog
structure.

## Unreleased

## 1.0.3 - 2026-07-18

### Added

- Added Android AppFunctions workflow actions for food workspace automation.
- Added production receipt capture provider wiring and safer receipt-to-Kitchen put-away.
- Added pantry-first planning, nutrition provider-chain scaffolding, meal-prep batch planning, recipe import parsing, scaled shopping-list gaps, household profiles, compatibility export, and prepared-base remix suggestions.
- Added cross-channel golden tests, risk-policy coverage, and external automation validation scripts.

### Changed

- Updated the release and App Links verification flow for trustworthy signed distribution.

## 1.0.2 - 2026-07-18

### Added

- Added separate FOSS and Play distribution flavors.
- Added FOSS stubs for Google Drive backup and Health Connect integrations.

### Changed

- Updated README, screenshots, demo media, and Fastlane metadata for open-source discovery.
- Kept Google Identity, Play Services Auth, and Health Connect dependencies out of the FOSS flavor.

## 1.0.1 - 2026-07-18

### Changed

- Moved primary create actions into a context-aware bottom action dock.
- Kept AI, receipt, and related quick actions as compact secondary dock actions.
- Made Shop's bottom action follow To buy, Receipts, and Put away modes.
- Reduced duplicate top action rows across Today, Kitchen, Plan, Recipes, and Shop.
- Improved AI chat history previews to show the latest user message in each chat.

## 1.0.0 - 2026-07-17

### Added

- Five-destination Android workspace for Today, Kitchen, Plan, Recipes, and Shop.
- Manual food, grocery, recipe, meal, plan, receipt, CSV, share, and app-command intake.
- Editable AI conversations, persistent chat history, visible AI context, and editable proposals.
- Deterministic Primary → Fallback provider routing with no round robin.
- Azure OpenAI v1 Responses, v1 Chat Completions, and legacy deployment routing.
- Receipt notes, line-level proposal review, store/price provenance, storage, expiry, and nutrition fields.
- Bounded deep-link and proposal-package commands with atomic review and audit history.
- Encrypted local/Google Drive backup flow, optional Health Connect, and Android automation contracts.

### Changed

- Receipt purchases default to Kitchen put-away review instead of the shopping-needed state.
- Unknown nutrition remains unset instead of appearing as zero.
- Food imagery and emoji prefer stored identity before deterministic fallback.
- AI prompts/skills are visible and editable under Settings → AI assistant.
- AI connection tests report real provider HTTP errors instead of false-positive success.

### Security

- Automatic Android backup is disabled; provider credentials remain Keystore-protected.
- External links, shares, and intents stage untrusted drafts and cannot silently mutate local data.
