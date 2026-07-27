# Wonder V1 Execution Checklist

Baseline: `1b4ac49`

## Contract lock

- [ ] Create V1 worktree/branch from baseline.
- [ ] Prove `@a2ui/web_core/v0_9` under Expo/Metro.
- [ ] Pin all dependencies once.
- [ ] Define and validate AppPackage V3.
- [ ] Define native capability envelope.
- [ ] Implement package JSON-Patch proposal, preview, durable approval, activation, restart recovery, and rollback.
- [ ] Add five conformance package fixtures.
- [ ] Commit and publish `CONTRACT_LOCK_SHA`.

## Parallel verticals

- [ ] Launch V2 from `CONTRACT_LOCK_SHA`.
- [ ] Launch V3 from `CONTRACT_LOCK_SHA`.
- [ ] Launch V4 from `CONTRACT_LOCK_SHA`.

### V2 — A2UI/UI/native

- [ ] A2UI is the only persisted UI contract.
- [ ] JSON Render is the only renderer.
- [ ] Basic A2UI catalog works.
- [ ] Record/form/feed/poll/post/link/board/chart/map/media/permission catalog works.
- [ ] DTCG theme tokens work.
- [ ] Home/Chat/Settings/Sources/package screens are package surfaces.
- [ ] Five conformance packages render on web and Android.
- [ ] Legacy shell/schema/spec builder and JSON Render spike deleted.
- [ ] Commit SHA reported.

### V3 — AI/MCP

- [ ] Vercel AI SDK owns chat streaming/tool loop.
- [ ] Official MCP SDK owns MCP transport/session/protocol.
- [ ] Hostile identity, scope, replay, approval, and body-limit tests pass.
- [ ] AI/MCP writes remain typed proposals through the kernel.
- [ ] Manual transports, fake agent theater, and AI/MCP spikes deleted.
- [ ] Commit SHA reported.

### V4 — providers/sharing

- [ ] Official Notion SDK path works.
- [ ] Narrow Sheets and Drive SDK paths work.
- [ ] Mapping, leases, verified reread, receipts, Undo, and authority preserved.
- [ ] Drive invite/resource sharing works.
- [ ] Notion OAuth/page access verification works.
- [ ] Fresh disposable Notion, Sheets, and Drive proofs pass and clean up.
- [ ] Raw provider HTTP plumbing deleted.
- [ ] Commit SHA reported.

## Integration

- [ ] V1 integrates V2 commit once.
- [ ] V1 integrates V3 commit once.
- [ ] V1 integrates V4 commit once.
- [ ] No shared contract amendments were smuggled into vertical commits.
- [ ] Resolve only central wiring conflicts.
- [ ] Run full current-tree gate from `tasks/plan.md`.
- [ ] Run legacy absence/direct-writer audits.
- [ ] Independent read-only reviewer accepts exact integrated SHA.
- [ ] Worktree clean.
- [ ] Push accepted branch.

## Completion statement

- [ ] One SHA proves schemas, UI, queries, actions, rules, workflows, themes, providers, and native capabilities are package-driven.
- [ ] Every mutation remains validated, approved when required, receipted, verified, and rollbackable.
- [ ] No custom Wonder UI DSL, second renderer/store/writer, CRDT, or arbitrary plugin runtime remains.
