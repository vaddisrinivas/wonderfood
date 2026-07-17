# WonderFood

WonderFood is a local-first Android food workspace for kitchen inventory, shopping,
recipes, meal plans, meal logs, receipts, and optional Health Connect integration.
AI is optional: manual forms, CSV import/export, deterministic parsing, Android shares,
and reviewable command links work without an in-app LLM.

Product status lives in [`FEATURES.md`](FEATURES.md), release order in
[`ROADMAP.md`](ROADMAP.md), and user-visible changes in [`CHANGELOG.md`](CHANGELOG.md).

## Product principles

- Food data stays on-device unless the user explicitly invokes an external provider,
  encrypted Google Drive backup, export, or share.
- AI, assistant, share, and deep-link mutations are proposals. The user can edit,
  accept, or reject them before persistence.
- AI replies and every structured proposal remain editable. Starting a new chat keeps
  earlier conversations readable in Chat history; only the confirmed reset deletes them.
- Receipt photos accept optional user context before analysis, and that note stays with
  the local receipt evidence and provider request.
- Unknown nutrition remains unknown. Provider estimates retain source and confidence.
- External bulk proposals are bounded, validated, audited, and applied atomically.
- No personal pantry, account, receipt, health, credential, or provider data is bundled.

## Build

Requirements: JDK 17 and Android SDK 36.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Install on a connected device or emulator:

```bash
./gradlew :app:installDebug
```

Signed APKs are published on the [GitHub Releases](https://github.com/vaddisrinivas/wonderfood/releases)
page with a SHA-256 checksum. Maintainer instructions live in
[`docs/releasing.md`](docs/releasing.md).

Full local quality gate:

```bash
./scripts/quality/android-harness.sh local
```

Connected Android tests:

```bash
./scripts/quality/android-harness.sh connected
```

## Optional integrations

- AI providers are configured as a deterministic Primary and one optional Fallback.
  Each request tries Primary once, then Fallback only after failure; there is no rotation
  or load balancing. API keys are encrypted with Android Keystore and excluded from
  Android backup.
- Google Drive app-data backup requires replacing `google_web_client_id` in
  `app/src/main/res/values/google_auth.xml` with a public OAuth web client ID.
- HTTPS app links require a deployed `https://wonderfood.app/.well-known/assetlinks.json`.
- Other apps can stage commands with links, Android shares, or the explicit command
  intent documented in [`docs/app-command-contract.md`](docs/app-command-contract.md).

## Modules

- `app`: Compose UI, Android integrations, command/deep-link intake, local SQLite
  runtime, import/export, and sync UI.
- `core:model`: canonical food domain and snapshot contracts.
- `core:engine`: validated command execution policies.
- `core:data`: Room repository and migration foundation.
- `core:ai`: versioned structured proposal envelopes and provider boundaries.

The runtime app currently uses its audited SQLite store while the canonical core
modules remain independently tested boundaries. Keep contract changes synchronized
across both paths.

## Privacy and security

See [`PRIVACY.md`](PRIVACY.md) and [`SECURITY.md`](SECURITY.md). Android automatic
backup is disabled; WonderFood's explicit backup flow encrypts archives before upload.
Cleartext networking is denied except emulator/local-development loopback hosts.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). The project is licensed under Apache-2.0.
