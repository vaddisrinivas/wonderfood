# ORCH Utopia Queue

Status: active ORCH queue source.

Model policy:

- Adapter: `codex` only.
- Model: `gpt-5.3-codex-spark` only.
- Model fallback is forbidden; report blocked if Spark is unavailable.
- Tasks must run from `.orchestry/tasks` through `orch run`, not ad hoc parallel shells.
- Max concurrent ORCH agents: 4.

Reuse policy:

- Do not handwrite infrastructure when a good standard library fits.
- Keep Wonder's product kernel: `applyOperation`, receipts, provenance, undo, provider authority, package activation, and policy gates.
- Durable/public contracts use JSON Schema + Ajv + generated TypeScript.
- Zod is for transient API/UI/env parsing only.
- No Drizzle during convergence. Preserve existing SQLite plumbing and Wonder QuerySpec semantics.
- AI SDK may own model provider/tool-call plumbing for the AI package builder.
- AI SDK must produce typed `PackageChangeRequest`; Wonder preview/approval/activation still decides.
- XState owns workflow lifecycle and durable snapshots; machine actions propose operations only.
- Expo AuthSession owns OAuth/PKCE browser flow and redirects; provider ownership, scopes, token storage, and sync remain Wonder-owned.
- Official MCP TypeScript SDK owns MCP protocol/transport; plugin manifest, grants, scopes, quotas, receipts remain Wonder-owned.
- fast-check is preferred for query parity, package rollback, rule replay, workflow lifecycle, outbox races, duplicate delivery, and provider readback failures.

Initial ORCH wave:

1. P0-01 baseline and evidence report.
2. P0-02 pinned AI SDK/Expo transport spike.
3. P0-03 official MCP v1.29 stateless spike.

Later tasks follow the dependency DAG in
`docs/lifeos/utopian-platform-implementation-plan.md`.

Run:

```bash
orch task list
orch run --all --watch
```
