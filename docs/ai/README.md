# WonderFood AI Catalog

This directory freezes the Wave 0 AI interpretation contract for WonderFood.
It keeps AI behavior separate from product logic: providers interpret user input
into a versioned command envelope, and deterministic app code validates and
executes only known typed commands. In this catalog, "tools" are the typed
command names listed in `command-envelope-v1.md`.

## Current Version

- Catalog: `wf.ai.skill-catalog.v1`
- Envelope schema: `wf.ai.command-envelope.v1`
- Human contract: [skill-catalog-v1.md](skill-catalog-v1.md)
- Command contract: [command-envelope-v1.md](command-envelope-v1.md)
- Lightweight JSON Schema: [command-envelope.schema.v1.json](command-envelope.schema.v1.json)
- Golden fixtures: [fixtures/golden](fixtures/golden)

## Invariants

- Skills emit typed commands only.
- Skills never emit SQL, DAO calls, table names, Room entities, or generic CRUD.
- The AI output is always a proposal; product logic owns validation and execution.
- Destructive or uncertain commands require explicit confirmation.
- Unknown nutrition stays unknown until a trusted source or user correction exists.
- Unsupported requests return `status: "unsupported"` or `status: "needs_clarification"`
  with no commands.
- The UI renders generic envelope state and command summaries; it must not hardcode
  provider prompts or skill internals.

## Versioning Rules

1. Do not mutate historical schema or golden files after another ticket depends on
   them. Add a new version instead.
2. Additive command payload fields are allowed only when old readers can ignore them.
3. Removing or renaming a command type requires a new `catalog_version`.
4. A provider prompt, local fake, or test fixture must declare the catalog and
   envelope version it targets.
5. Future app packaging can copy these catalog files to `app/src/main/assets/ai/**`
   without changing UI code.

## Review Checklist

- Every fixture validates as JSON.
- Every command type appears in `command-envelope.schema.v1.json`.
- Every destructive command has `confirmation.required: true`.
- Every uncertain command either has confirmation or produces clarification.
- Every warning uses a stable machine-readable code.
- No example contains personal data, secrets, SQL, or provider-specific fields.
