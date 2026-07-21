# WonderFood

Local-first food planning for Android: kitchen inventory, recipes, meal plans, shopping, receipts, selectable data homes, and reviewable AI proposals in one private workspace.

[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8%2B-3ddc84.svg)](app/build.gradle.kts)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-f18e33.svg)](app/build.gradle.kts)
[![Local-first](https://img.shields.io/badge/local--first-review%20before%20save-147d48.svg)](PRIVACY.md)

![WonderFood screenshot gallery](docs/images/screenshot-gallery.png)

## Demo

![WonderFood demo](docs/images/wonderfood-demo.gif)

## Why It Exists

Food apps usually split your life into separate places: pantry lists, recipes, meal logs, shopping, receipts, nutrition, and AI chats. WonderFood keeps them in one workspace, lets the household choose where that workspace lives, and treats every AI/share/deep-link mutation as a draft you can edit, accept, or reject.

## Highlights

- **Kitchen inventory:** fridge, freezer, pantry, lots, expiry, prices, notes, images, and nullable nutrition.
- **Meal workspace:** daily meal timeline, meal plans, recipe matching, meal logs, and use-first prompts.
- **Shopping and receipts:** manual lists, receipt evidence, deterministic receipt parsing, and put-away review.
- **Reviewable AI:** provider output maps to typed command envelopes; app validation owns every write.
- **No-LLM fallbacks:** manual forms, CSV import/export, deterministic parsing, and command links work without an in-app LLM.
- **External commands:** Android shares, HTTPS/custom-scheme links, and explicit command intents stage bounded proposals.
- **Selectable data homes:** start local, or connect Google Sheets, Notion, or a Postgres-backed HTTPS API with rollback snapshots before switching.
- **Optional integrations:** encrypted Google Drive app-data backup and Health Connect support can be enabled by the user.

## Current Release Work: 1.0.5

WonderFood 1.0.5 is the active verification target:

- **Canonical local runtime:** app state, imports, exports, AI context, and AppFunctions are being cut over to canonical household commands and Room state.
- **New V3 shell:** `Now`, `Food`, `Week`, and `Cart` organize the app around daily use instead of setup screens.
- **Local-first default:** SQLite works without accounts, internet, Notion, Sheets, Postgres, or AI.
- **Real data homes:** Google Sheets, Notion, and Postgres use one canonical household contract instead of provider-specific snapshots.
- **Readable household workspaces:** Notion and Sheets schema work focuses on useful food, recipe, meal, purchase, cart, and planning views that humans can inspect and automate.
- **Safer switching:** backend changes create a local rollback snapshot before committing the new data home.
- **AI as proposals:** recipe import, pantry normalization, can-cook ranking, meal planning, cart building, personalization, cooking coach, receipt parsing, and nutrition estimation have typed contracts while deterministic app validation owns writes.
- **Provider-ready mapping:** TheMealDB and Open Food Facts mappings now preserve source, confidence, warnings, and cache policy.
- **Better import/receipt consistency:** deterministic receipt parsing, draft normalization, canonical import/export, and snapshot merging make AI and non-AI intake behave more uniformly.
- **Manual workflows still work:** food entry, cart items, recipe creation, and meal logging are available without AI.
- **Release proof:** local tests, emulator/device proof, live-provider proof, signing, checksums, CI, PR merge, and release publication are required before 1.0.5 is called complete.

Latest published install remains [WonderFood 1.0.4](https://github.com/vaddisrinivas/wonderfood/releases/tag/v1.0.4) until the 1.0.5 release evidence is complete:

- Use `WonderFood-play-v1.0.4.apk` for Google/Play-integrated features.
- Use `WonderFood-foss-v1.0.4.apk` for the no-Google-dependency build.

## Product Principles

- Food data stays on-device unless the user explicitly chooses a remote data home, invokes an external provider, creates an encrypted backup, exports, or shares.
- AI, assistant, share, and deep-link mutations are proposals. The user can edit, accept, or reject them before persistence.
- Unknown nutrition remains unknown. Provider estimates retain source and confidence.
- External bulk proposals are bounded, validated, audited, and applied atomically.
- No personal pantry, account, receipt, health, credential, or provider data is bundled.

Product status lives in [FEATURES.md](FEATURES.md), release order in [ROADMAP.md](ROADMAP.md), and user-visible changes in [CHANGELOG.md](CHANGELOG.md).

## Build

Requirements: JDK 17 and Android SDK 36.

```bash
git clone https://github.com/vaddisrinivas/wonderfood.git
cd wonderfood
./gradlew :app:assembleFossDebug :app:testFossDebugUnitTest
```

Play-integrated debug build:

```bash
./gradlew :app:assemblePlayDebug
```

Install on a connected device or emulator:

```bash
./gradlew :app:installFossDebug
```

Full local quality gate:

```bash
./scripts/quality/android-harness.sh local
```

Connected Android tests:

```bash
./scripts/quality/android-harness.sh connected
```

## Optional Integrations

- AI providers are configured as a deterministic Primary and one optional Fallback. Each request tries Primary once, then Fallback only after failure; there is no rotation or load balancing.
- Data home setup is shown on first run. Local SQLite needs no account. Google Sheets uses a Sheet URL plus Google authorization in the `play` flavor. Notion uses a page URL plus integration token. Postgres uses a Postgres-backed HTTPS API or user-owned service endpoint with household ID and API token. Android must not ship raw database credentials, privileged server tokens, or raw database socket paths.
- Google Drive app-data backup is available in the `play` flavor and requires replacing `google_web_client_id` in `app/src/main/res/values/google_auth.xml` with a public OAuth web client ID.
- Health Connect access is available in the `play` flavor and requested through Android's permission flow.
- HTTPS app links require a deployed `https://wonderfood.app/.well-known/assetlinks.json`.
- Other apps can stage commands with links, Android shares, or the explicit command intent documented in [docs/app-command-contract.md](docs/app-command-contract.md).

## FOSS Distribution

WonderFood is Apache-2.0 and local-first, with Fastlane metadata and screenshots prepared under [fastlane/metadata/android/en-US](fastlane/metadata/android/en-US). The `foss` flavor builds without Google Identity, Play Services Auth, or Health Connect SDK dependencies; the `play` flavor keeps Google Drive backup and Health Connect integrations. Distribution notes and disclosure drafts live in [docs/distribution/FOSS_READINESS.md](docs/distribution/FOSS_READINESS.md).

## Modules

- `app`: Compose UI, Android integrations, command/deep-link intake, local SQLite runtime, import/export, and sync UI.
- `core:model`: canonical food domain and snapshot contracts.
- `core:engine`: validated command execution policies.
- `core:data`: Room repository and migration contracts.
- `core:ai`: versioned structured proposal envelopes and provider boundaries.

The runtime app uses the canonical household repository for the v1.0.5 path. Keep contract changes synchronized across app, Room, sync, AI, and AppFunctions.

## Privacy and Security

See [PRIVACY.md](PRIVACY.md) and [SECURITY.md](SECURITY.md). Android automatic backup is disabled; WonderFood's explicit backup flow encrypts archives before upload. Cleartext networking is denied except emulator/local-development loopback hosts.

Release signing and OAuth proof gates are tracked in [docs/release/RELEASE_CHECKLIST.md](docs/release/RELEASE_CHECKLIST.md).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Device screenshots, accessibility feedback, food-domain test cases, and import/export edge cases are especially useful.

## License

WonderFood is licensed under [Apache-2.0](LICENSE).
