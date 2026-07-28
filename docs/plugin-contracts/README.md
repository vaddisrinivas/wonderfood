# Plugin contracts

Milestone 3 proof:

- `packages/shared/contracts/plugin.ts` defines the manifest, lock, and compatibility resolver.
- Plugin classes: `runtime`, `build`, `server`, `specialized`.
- Resolver statuses: `compatible`, `compatible_with_fallback`, `requires_new_build`, `unsupported`.
- Locked artifacts keep `id`, `version`, `checksum`, and derived `capabilities`.
- Plugin capability locks only emit `widget:`, `tool:`, `data-source:`, `background-task:`, and `permission:` entries.
- Plugins cannot write records directly.
- Unsupported widgets can show a declared fallback string in JSON Render.

