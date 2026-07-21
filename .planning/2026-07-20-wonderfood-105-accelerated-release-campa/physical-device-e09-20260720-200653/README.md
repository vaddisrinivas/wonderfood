# E09 Physical Device Proof

- Device: Samsung SM-S918U1 (S23 Ultra), Android API 36
- Package: `com.wonderfood.app.foss`
- Version: `1.0.5-foss` (`versionCode=5`, `targetSdk=36`)
- APK SHA-256: `78548af6c3e05ac8643d002b3468e3d7f640800a0cec2b44c2935eae86c77ee2`
- Install: streamed install succeeded
- Launch PID: `20257`
- Relaunch PID after force-stop: `21025`
- Canonical database: `wonderfood-v105-household.db`
- Database SHA-256 before restart: `2bb28c173aea4538c5a795bcea19d8e1ca39619621d61b340d19e2013801657f`
- Database SHA-256 after restart: `2bb28c173aea4538c5a795bcea19d8e1ca39619621d61b340d19e2013801657f`
- Byte comparison: identical (`db_cmp_exit=0`)
- UI result: onboarding selected `Start local now`; after restart the app returned directly to the Now screen with tabs `Now`, `Food`, `Week`, `Saved`, and `Cart`.

Evidence includes install output, package metadata, ADB device identity, UI XML, screenshots before and after restart, database files, and checksums.
