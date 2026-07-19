# WonderFood release checklist

This checklist is required before calling a build release-ready.

## Signing

- Document signing owner and key custody.
- Build the release artifact from a clean source state.
- Record APK/AAB file name, version code, version name, SHA-256 checksum, and build command.
- Keep debug-signed artifacts clearly separate from release-signed artifacts.

## OAuth and app links

- Configure release Google OAuth clients outside source control.
- Verify Google Sheets authorization in the `play` flavor on a physical device using the release signing certificate.
- Verify Google Drive backup/restore if included in the release notes.
- Publish and verify `https://wonderfood.app/.well-known/assetlinks.json` for the release certificate before claiming verified links.

## Data homes

- Local SQLite starts without account, network, token, or permission.
- Google Sheets connects with Sheet URL plus Google authorization, creates/checks schema, and avoids overwriting existing WonderFood data without review.
- Notion connects with page URL plus token and exports a snapshot only after page access succeeds.
- Supabase/PostgREST/WonderFood server connects over HTTPS and exports a household snapshot only after API validation succeeds.
- Direct PostgreSQL DSN mode remains advanced/config-only unless a release includes a reviewed direct adapter.
- Backend switching creates a local rollback snapshot before committing the new active backend.

## Privacy and security

- `PRIVACY.md`, `SECURITY.md`, `FEATURES.md`, and release notes match the shipped behavior.
- Android automatic backup remains disabled.
- Keystore-protected credentials are excluded from explicit backup payloads.
- No OAuth token, API token, DSN, signing secret, or private provider data is committed.

## Device proof

- Install and launch the signed build on a physical Android device.
- Capture proof for first-run onboarding, Local setup, and the selected release remote backend.
- Run the focused unit/build gate and record command output.
- Run `./scripts/quality/collect-release-evidence.sh` with release signing env set
  and archive the generated evidence directory.
- Record any broad known test failures separately from release blockers.
