# ORCH Utopia Queue

Status: active ORCH queue source.

Model policy:

- Adapter: `codex` only.
- Model: `gpt-5.3-codex-spark` only.
- Model fallback is forbidden; report blocked if Spark is unavailable.
- Tasks must run from `.orchestry/tasks` through `orch run`, not ad hoc parallel shells.
- Max concurrent writable ORCH agents: 4.
- Up to 6 additional Spark agents remain idle or read-only until disjoint work is ready.

Reuse policy:

- Do not handwrite infrastructure when a good standard library fits.
- Freeze target contracts and prove the official library in an isolated spike first.
- Then delete the custom implementation first inside the bounded replacement task, adopt the official library, verify, and merge.
- Do not retain indefinite compatibility runtimes. Temporary migration adapters require an owner, deletion task, expiry milestone, and parity test.
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
2. P0-02 isolated pinned AI SDK parity proof.
3. P0-03 isolated official MCP v1.29 parity proof.
4. P0-04 approval receipt schema and threat fixtures.

Later tasks follow the dependency DAG in
`docs/lifeos/utopian-platform-implementation-plan.md`.

Run:

```bash
orch task list
orch run --all --watch
```
