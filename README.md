# WonderFood

WonderFood is a JSON-rendered, package-driven Expo app shell for personal software.

The product direction is simple:

- App behavior comes from package JSON.
- UI surfaces render through JSON Render.
- Data stays local-first, with optional Notion / Google Sheets provider homes.
- AI proposes package, schema, workflow, and data changes; Wonder validates before mutation.
- Official libraries are preferred over custom framework code.

## Current shape

- Native shell: Expo / React Native.
- Renderer: `@json-render/react-native` with an A2UI-shaped package contract.
- Config: `packages/domain-config/`.
- Shared contracts: `packages/shared/contracts/`.
- Server: local/private Wonder runtime for chat, providers, package changes, workflows, and official MCP.
- Providers: Notion SDK and Google APIs client are installed; Wonder-owned mapping, authority, undo, and verification stay in repo.

## Development

```bash
npm install
npm run start
npm run android:dev
npm run web
```

## Core gates

```bash
npm run typecheck
npm run config:validate
npm run check:json-render-only-ui
npm run check:mcp-official-only
npm run export:web
npm run export:android
```

## Repo rules

- Do not commit secrets.
- Do not add spike artifacts to the production tree.
- Do not add custom MCP protocol code; use the official MCP SDK.
- Do not add new hand-built UI screens when package JSON can own the surface.
- Do not bypass the canonical writer / approval / provider verification kernel.

## Useful docs

- [AI contracts](docs/ai/README.md)
- [Release checklist](docs/release/RELEASE_CHECKLIST.md)
- [Testing](docs/testing/README.md)
- [Security](SECURITY.md)
- [Privacy](PRIVACY.md)
