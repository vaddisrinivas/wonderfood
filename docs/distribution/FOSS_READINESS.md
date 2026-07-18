# WonderFood FOSS Readiness

Status: buildable FOSS flavor ready for maintainer review; no publishing or directory submission approved yet.

## What Is Ready

- License: Apache-2.0 text normalized for GitHub detection.
- Source: Android app and core modules are public.
- Build gate: `./gradlew :app:testFossDebugUnitTest :app:testPlayDebugUnitTest :app:lintFossDebug :app:lintPlayDebug :app:assembleFossDebug :app:assemblePlayDebug`.
- Quality: `./scripts/quality/android-harness.sh local`.
- Metadata: Fastlane/Izzy-style metadata and screenshots are present under `fastlane/metadata/android/en-US`.
- Privacy: Android automatic backup is disabled; explicit backups are encrypted before upload.
- Data model: local-first food workspace; AI and external commands remain reviewable proposals.
- Flavor split: `src/foss` stubs disable Google Drive sign-in/authorization and Health Connect; `src/play` carries the Google/Health implementations.
- FOSS audit: `fossDebugRuntimeClasspath` has no Google Identity, Play Services Auth, or Health Connect dependency matches; the FOSS merged manifest has no Health Connect permissions/provider query.

## Required Disclosure

Potential FOSS directory disclosures:

- **NonFreeNet:** optional AI provider requests go to the provider selected by the user.
- **Network:** app-link verification and user-configured AI requests may use network access.
- **Play flavor only:** Google Drive backup uses Google Identity / Play Services Auth, and Health Connect uses Android Health Connect permissions.

## F-Droid Main Notes

The `foss` flavor is the candidate flavor for F-Droid-style review. Current local verification:

- `./gradlew :app:testFossDebugUnitTest :app:testPlayDebugUnitTest :app:lintFossDebug :app:lintPlayDebug :app:assembleFossDebug :app:assemblePlayDebug`
- FOSS dependency grep for Google Identity / Play Services Auth / Health Connect: no matches.
- FOSS merged-manifest grep for Health Connect permissions/provider query: no matches.

## Before Submission

- Confirm GitHub recognizes Apache-2.0 after license normalization.
- Add repository topics from `docs/distribution/GITHUB_METADATA.md`.
- Upload `docs/images/social-preview.png` as the GitHub social preview.
- Decide whether first public FOSS listing targets IzzyOnDroid, F-Droid main, or both.
- Capture fuller demo data before launch; current screenshots/video are clean but sparse.

## Suggested GitHub Topics

`android`, `food`, `food-tracker`, `foss`, `health-connect`, `jetpack-compose`, `kotlin`, `local-first`, `meal-planner`, `nutrition`, `pantry`, `privacy`, `recipe-manager`
