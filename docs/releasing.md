# Releasing WonderFood

The `Android Release` workflow publishes a signed APK and SHA-256 checksum when a
semantic-version tag such as `v1.0.0` is pushed. The tag must exactly match the
`versionName` in `app/build.gradle.kts`.

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
4. Merge the release commit into `main` and wait for Android Quality to pass.
5. Create and push the matching tag, for example `git tag -a v1.0.1 -m "WonderFood 1.0.1"`.
6. Confirm the GitHub Release contains the APK and checksum and that `apksigner verify`
   passes.
