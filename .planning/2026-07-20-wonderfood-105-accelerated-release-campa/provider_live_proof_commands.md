# Provider Live Proof Commands

Use these only when live env is present. Do not print token values.

## One-shot proof

```bash
scripts/quality/run-provider-live-proofs.sh
```

The wrapper auto-loads `/Users/srinivasvaddi/.config/agent-secrets/agent.env`
through `/Users/srinivasvaddi/.codex/skills/agent-env/scripts/run-with-agent-env.sh`
when both files are present. It prints missing variable names only, never values.

## Provider-only proof

```bash
scripts/quality/run-provider-live-proofs.sh notion
scripts/quality/run-provider-live-proofs.sh sheets
scripts/quality/run-provider-live-proofs.sh postgres
```

## Direct commands

```bash
/Users/srinivasvaddi/.codex/skills/agent-env/scripts/run-with-agent-env.sh \
  scripts/quality/run-notion-live-proof.sh

/Users/srinivasvaddi/.codex/skills/agent-env/scripts/run-with-agent-env.sh \
  scripts/quality/run-google-sheets-live-proof.sh

/Users/srinivasvaddi/.codex/skills/agent-env/scripts/run-with-agent-env.sh \
  scripts/quality/run-postgres-live-proof.sh
```

## Acceptance rows accelerated

- `C14`: Notion live export/read proof runner now exists; broader edit/pull/conflict/archive/retry/repair still needs test coverage or manual evidence.
- `C19`: Sheets live runner already existed and is now included in the one-shot wrapper.
- `C23`: Postgres live runner already existed and is now included in the one-shot wrapper.
- `C25`: Use the generated Notion/Sheets workspaces from the live runs for visual standalone inspection while the app is offline.
- `E04`: Run all three providers through the one-shot wrapper once env is available.
