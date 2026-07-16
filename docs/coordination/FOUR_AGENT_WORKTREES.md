# Four-Agent Worktrees

Status: active coordination plan  
Date: 2026-07-16

All implementation lanes branch from the same preflight commit. The coordinator owns
`main` and is the only role that merges lane branches.

## Worktree Layout

| Lane | Branch | Worktree Path | Primary Tickets |
|---|---|---|---|
| Agent A | `agent/a-core-data` | `../wonderfood-agent-a` | `WF-A01` through `WF-A10` |
| Agent B | `agent/b-product-ui` | `../wonderfood-agent-b` | `WF-B01` through `WF-B10` |
| Agent C | `agent/c-ai-integrations` | `../wonderfood-agent-c` | `WF-C01` through `WF-C10` |
| Agent D | `agent/d-quality-release` | `../wonderfood-agent-d` | `WF-D01` through `WF-D12` |

## Merge Order

Within each wave, merge in this order unless a ticket explicitly says otherwise:

1. Agent A
2. Agent C
3. Agent B
4. Agent D

After each merge, run:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Run connected Android tests at wave gates that touch UI, storage migration, voice,
Health Connect, capture, or release flows.

## Rules

- One ticket per lane at a time.
- One focused commit per ticket.
- Same-wave tickets build against contracts frozen at the prior gate.
- Lanes do not edit another lane's owned paths.
- No direct data mutation path bypasses the command/repository layer after `WF-A04`.
- No personal baseline DB, screenshot, APK, token, local property, export, or signing key is
  committed.

## Coordinator Commands

Create worktrees:

```bash
git worktree add ../wonderfood-agent-a -b agent/a-core-data main
git worktree add ../wonderfood-agent-b -b agent/b-product-ui main
git worktree add ../wonderfood-agent-c -b agent/c-ai-integrations main
git worktree add ../wonderfood-agent-d -b agent/d-quality-release main
```

Inspect:

```bash
git worktree list
```

Each worktree must pass:

```bash
./gradlew :app:assembleDebug
```
