# WonderFood LifeOS

WonderFood is being reimagined as a local-first LifeOS built with Expo and React Native: a quiet workspace for food, planning, personal context, and reviewable AI actions.

[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Expo SDK 57](https://img.shields.io/badge/Expo-SDK%2057-000020.svg)](https://expo.dev/)
[![React Native](https://img.shields.io/badge/React%20Native-0.86-61dafb.svg)](https://reactnative.dev/)
[![Android primary](https://img.shields.io/badge/platform-Android%20primary-3ddc84.svg)](app.json)

## Platform order

1. **Static web first:** fastest review and deployment surface.
2. **Android primary:** product-quality mobile target and first native release.
3. **iOS last:** enabled in configuration, packaged after web and Android gates are stable.

Both native targets use the application identifier `com.wonderfood.app`.

## Current implementation

Implemented now: responsive Today/Food/Chat shell, record editor, global search, capture, multi-turn server chat with streaming, tables, source citations, action receipts and idempotent Undo; SQLite persistence; MCP Streamable HTTP; Notion data-source pull/push/webhooks; Google Sheets adapter; Health Connect read bridge; and self-contained Android release packaging. Food is active; Health and Plants are real config packages available for later selection.

Provider secrets stay outside the repo. Local live Notion/OpenAI and Google Sheets pulls are available when the private environment is loaded; the approved workbook keeps a human-facing Runtime dashboard plus a machine-readable `LifeOS Canonical` tab for sync and source citations.

- [LifeOS 2026 Notion](https://app.notion.com/p/manasa-srinivas/LifeOS-2026-3a45dd535a93816fb7d3d4a0a2bc2bf1)
- [LifeOS Google Sheets](https://docs.google.com/spreadsheets/d/1WpEwm07ApcnuiLDVhzl8vy4D5kU8KjmtbAVC4qLphcU/edit)
- Domain runtime files: `packages/domain-config/`

## Local development

Requirements: Node.js 22+, npm, and Android Studio for native Android work.

```bash
npm install
npm run start
```

Copy `.env.example` to `.env.local`, set the Mac LAN address, and start the server with the private environment loaded. Never commit tokens.

Launch a target:

```bash
npm run web
npm run android
# optional Metro-backed development shell
npm run android:dev
npm run ios
```

## Quality gates

Run the same ordered checks used by CI:

```bash
npm run typecheck
npm run config:validate
npm run doctor
npm run export:web
npm run export:android
```

Or run the combined gate:

```bash
npm run quality
```

Exports are written to `dist/web` and `dist/android`. The web export is static. Android remains the primary native package; iOS export/build automation will follow after those gates stabilize.

## EAS packaging

Preview Android APK:

```bash
npx eas-cli build --platform android --profile preview-android
```

Production Android App Bundle:

```bash
npx eas-cli build --platform android --profile production-android
```

iOS production build, last in the rollout:

```bash
npx eas-cli build --platform ios --profile production-ios
```

Signing credentials and provider secrets stay in EAS or local environment storage; they must not be committed.

## Product contracts and retained evidence

The React Native shell is new, but WonderFood's validated product work remains authoritative reference material:

- [LifeOS product pass](docs/lifeos/product-pass.md) and [UI copy audit](docs/lifeos/ui-copy-audit.md)
- [AI contracts and golden fixtures](docs/ai/README.md)
- [MCP bridge](docs/mcp-bridge.md)
- [Privacy](PRIVACY.md), [security](SECURITY.md), and [release checklist](docs/release/RELEASE_CHECKLIST.md)
- [Testing evidence](docs/testing/README.md), [design history](docs/design/v3-product-experience.md), and [distribution readiness](docs/distribution/FOSS_READINESS.md)
- Existing screenshots, demo media, release notes, and native Android evidence remain under `docs/`, `fastlane/`, and repository history.

Do not treat historical Kotlin/Compose commands as current Expo build instructions. Preserve those records while the LifeOS implementation replaces the legacy runtime.

## Safety principles

- Local-first data remains usable without an account or AI provider.
- Ordinary reversible changes write directly and offer Undo; sensitive or irreversible changes stay explicit.
- Provider credentials never ship in the app or repository.
- Unknown nutrition stays unknown; estimates retain provenance and confidence.
- Destructive agent actions require explicit user confirmation.

## License

WonderFood is licensed under [Apache-2.0](LICENSE).
