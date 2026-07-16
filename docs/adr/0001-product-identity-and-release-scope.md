# ADR 0001: Product Identity And Release Scope

Status: accepted for 1.0 implementation  
Date: 2026-07-16

## Decision

WonderFood 1.0 is an Android-only, local-first food workspace.

Release identity:

| Field | Value |
|---|---|
| App name | `WonderFood` |
| Android `applicationId` | `com.wonderfood.app` |
| Android namespace | `com.wonderfood.app` |
| Minimum SDK | `26` |
| Target SDK | `36` |
| Version policy | Semantic app version plus monotonic Play `versionCode` |
| Initial version | `1.0` / `1` |
| License | Apache-2.0 unless repository visibility changes before release |
| Repository visibility | Private during product build, public-ready before GitHub release |
| Initial release region | United States first, then expand after Play review and vitals are clean |

## Scope

In scope for 1.0:

- Today, Kitchen, Plan, Recipes, and Shop.
- Local SQLite/Room-backed food data.
- AI proposal review with deterministic product commands.
- Voice, direct actions, receipt/photo/barcode intake, optional Health Connect, export/restore.
- GitHub source release and Google Play production release.

Out of scope for 1.0:

- iOS, web, cloud accounts, social feed, billing, ads, and multi-device sync.
- Silent AI writes and silent uncertain inventory deductions.
- Medical claims or invented nutrition.

## Consequences

- Existing prototype package `com.example.wonderfood` is treated as a baseline-only package.
- New installs use `com.wonderfood.app`; prototype emulator data is preserved under
  `docs/baseline/` for migration fixtures.
- Play Console app creation must use `com.wonderfood.app` and happen after this ADR.
