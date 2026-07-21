# WonderFood Release/Device Row Triage

- Generated: 20260720-151215
- Scope: E01, E07, E09, E12, E13, E14, E15, E16
- Mode: read-only, no signing secrets, no install, no PR/tag/release creation
- Matrix: .planning/2026-07-20-wonderfood-105-accelerated-release-campa/acceptance_matrix.md

## Row Status From Matrix

| Row | Matrix status | Triage meaning |
|---|---|---|
| E01 | TODO | remaining matrix TODO/BLOCKED rows prevent final acceptance |
| E07 | TODO | needs connected supported low and current API runs |
| E09 | TODO | needs physical phone release-candidate proof |
| E12 | TODO | needs final commit CI pass, read-only status below if available |
| E13 | TODO | needs signing-owner build outside this no-secret triage |
| E14 | TODO | needs PR creation/review/merge after proof; this script does not create it |
| E15 | TODO | needs tag/release publication after signed artifacts; this script does not create it |
| E16 | TODO | needs downloadable artifact/install URL verification |

## Local Signals

- Git head: db7202f
- Branch: codex/wonderfood-105-zero-user-reset
- Dirty files: 141
- Version name: 1.0.5
- Version code: 5

## Scoped Git Status
```
 M docs/release/RELEASE_CHECKLIST.md
 M scripts/quality/run-google-sheets-live-proof.sh
?? .planning/2026-07-20-wonderfood-105-accelerated-release-campa/
?? scripts/quality/run-google-sheets-scenario-proof.sh
?? scripts/quality/run-local-postgres-live-proof.sh
?? scripts/quality/run-local-postgres-scenario-proof.sh
?? scripts/quality/run-notion-live-proof.sh
?? scripts/quality/run-notion-scenario-proof.sh
?? scripts/quality/run-postgres-live-proof.sh
?? scripts/quality/run-provider-live-proofs.sh
?? scripts/quality/run-provider-standalone-visual-proof.sh
?? scripts/quality/triage-release-device-rows.sh
```

## ADB Devices
```
List of devices attached
emulator-5554          device product:sdk_gphone64_arm64 model:sdk_gphone64_arm64 device:emu64a transport_id:1843

```

## Connected Proof Tasks
```
./gradlew :app:connectedFossDebugAndroidTest
./gradlew :app:connectedPlayDebugAndroidTest
./gradlew :core:data:connectedDebugAndroidTest
```

## Release APK Candidates
```
```

## Existing Checksum Files
```
```

## Read-only GitHub PR State
```
no pull requests found for branch "codex/wonderfood-105-zero-user-reset"
```

## Read-only GitHub Workflow State
```
[{"conclusion":"success","createdAt":"2026-07-19T23:51:42Z","databaseId":29708602171,"displayTitle":"Document WonderFood 1.0.4 features","headBranch":"main","headSha":"db7202f30149efaa2187a72f9635bb930d93236a","status":"completed","workflowName":"Android Quality"},{"conclusion":"success","createdAt":"2026-07-19T23:09:51Z","databaseId":29707459565,"displayTitle":"Ship WonderFood 1.0.4 workspace foundations (#39)","headBranch":"v1.0.4","headSha":"caafdd2169431fe782364ec1bacc11868597110f","status":"completed","workflowName":"Android Release"},{"conclusion":"success","createdAt":"2026-07-19T23:09:45Z","databaseId":29707457010,"displayTitle":"Ship WonderFood 1.0.4 workspace foundations (#39)","headBranch":"main","headSha":"caafdd2169431fe782364ec1bacc11868597110f","status":"completed","workflowName":"Android Quality"},{"conclusion":"success","createdAt":"2026-07-19T21:29:45Z","databaseId":29704453961,"displayTitle":"Ship WonderFood 1.0.4 workspace foundations","headBranch":"codex/release-1.0.4-open-issues","headSha":"44622411f6e44ebcd4893c63351727c448eae2b7","status":"completed","workflowName":"Android Quality"},{"conclusion":"failure","createdAt":"2026-07-19T21:21:59Z","databaseId":29704224714,"displayTitle":"Ship WonderFood 1.0.4 workspace foundations","headBranch":"codex/release-1.0.4-open-issues","headSha":"2bdba3cbca25477ad6ced070ccfbdf1e38ec2f6b","status":"completed","workflowName":"Android Quality"}]
```

## Read-only v1.0.5 Release State
```
release not found
```

## Non-Secret Acceleration Commands

- `./scripts/quality/triage-release-device-rows.sh`
- `./scripts/quality/android-harness.sh connected`
- `./scripts/quality/collect-device-evidence.sh "$ANDROID_SERIAL" build/evidence/<run>/device`
- `./scripts/quality/verify-release-assetlinks.sh` with `ASSETLINKS_URL=` for local-only file validation, or default URL for published-domain validation.
- `./scripts/quality/collect-release-evidence.sh build/evidence/<run>/release` only when the signing owner has loaded release signing env; otherwise it records missing signing env and skips release builds.

## Overclaim Guard

This triage is not release proof. Rows E01/E07/E09/E12-E16 remain TODO until the acceptance matrix contains current command/device/CI/signing/PR/release/install evidence for each row.
