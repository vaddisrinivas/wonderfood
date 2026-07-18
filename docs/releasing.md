# Releasing WonderFood

The `Android Release` workflow publishes a signed APK and SHA-256 checksum when a
semantic-version tag such as `v1.0.0` is pushed. The tag must exactly match the
`versionName` in `app/build.gradle.kts`.

### Trust and reproducibility expectations

- Keep keystore identity and recovery plan documented (owner, backup location, and
  rotation procedure).
- Capture the exact release artifact hash set before publishing:
  - `app-release.apk`
  - `app-release.apk.sha256`
  - Android package certificate fingerprints (`SHA-1`, `SHA-256`, `SHA-512`) from the signing key.
- Validate signatures with `apksigner verify --verbose` before publishing.
- Save the evidence output path in the release notes or attached checklist.
- Publish a short migration note for users on prior releases.
- Keep a checksum of `.well-known/assetlinks.json` for archival evidence.
- Host `https://wonderfood.app/.well-known/assetlinks.json` tied to the release
  signing cert for verified App Links (currently tracked as `.well-known/assetlinks.json`).
- Run a release asset check:
  - confirm `.well-known/assetlinks.json` is deployed at the HTTPS hostname
  - confirm the deployed JSON contains `com.wonderfood.app` with the release SHA-256 fingerprint
  - confirm HTTPS auto-verify status for `wonderfood.app` and `www.wonderfood.app`
- For F-Droid readiness:
  - verify all third-party dependencies are reproducible in clean checkout
  - verify version lockfiles and versionCode/Name are explicit and changeloged
  - keep a reproducible build command in release notes and confirm artifact hashes

## Required repository secrets

- `ANDROID_SIGNING_KEY_BASE64`: base64-encoded release keystore.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: signing-key alias.
- `ANDROID_KEY_PASSWORD`: signing-key password.

The signing key is the permanent Android app identity. Back it up securely and never
commit it. Losing it prevents compatible updates; replacing it makes existing installs
reject future APKs.

## Release checklist

1. Increment `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Move user-visible entries from `Unreleased` into a dated changelog section.
3. Run `./scripts/quality/android-harness.sh local`.
4. Capture release evidence (commands run, checksums, signing fingerprints, app and
   build ID).
5. Validate release signing fingerprint and App Links manifest:

```bash
export ANDROID_KEYSTORE_PATH=/path/to/wonderfood-release.jks
export ANDROID_KEYSTORE_PASSWORD=...
export ANDROID_KEY_ALIAS=...
export ANDROID_KEY_PASSWORD=...

keytool -list -v \
  -keystore "$ANDROID_KEYSTORE_PATH" \
  -alias "$ANDROID_KEY_ALIAS" \
  -storepass "$ANDROID_KEYSTORE_PASSWORD" \
  -keypass "$ANDROID_KEY_PASSWORD" \
  | awk '/SHA256:/{print $2; exit}'

# Optional: write fingerprint directly into .well-known/assetlinks.json
./scripts/quality/refresh-assetlinks-fingerprint.sh

export ASSETLINKS_URL=https://wonderfood.app/.well-known/assetlinks.json
./scripts/quality/verify-release-assetlinks.sh
```

The release verifier script also checks that both `https://wonderfood.app` and
`https://www.wonderfood.app` return successful HTTPS responses during verification.

6. Archive the signed `.well-known/assetlinks.json` fingerprint source proof and
   F-Droid reproducibility checks with the same build ID.
7. Merge the release commit into `main` and wait for Android Quality to pass.
8. Create and push the matching tag, for example `git tag -a v1.0.1 -m "WonderFood 1.0.1"`.
9. Confirm the GitHub Release contains the APK and checksum and that `apksigner verify`
   passes.
