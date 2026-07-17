# Security policy

## Supported version

Security fixes target the current `main` branch until tagged releases begin.

## Reporting a vulnerability

Use GitHub private vulnerability reporting when available. Do not open a public issue
for credential exposure, intent injection, unsafe mutation, backup disclosure, health
data exposure, or data-loss defects.

Include affected version, reproduction steps, impact, and any suggested mitigation.
Do not include real user food, receipt, account, health, or provider data.

## Security boundaries

- Public intents and links are untrusted input.
- External changes are staged for review; destructive and sensitive actions require
  explicit confirmation.
- Provider credentials are encrypted with Android Keystore and excluded from backup.
- Android automatic backup is disabled. Explicit Google Drive archives are encrypted.
- Cleartext networking is restricted to local-development loopback hosts.

