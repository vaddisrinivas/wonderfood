# External Android automation validation

This checklist supports issue #5 and records device evidence for every external entry route.

## Preconditions

- Keep an untrusted demo phone profile with no meaningful pantry data.
- Clear app cache/state between runs unless route behavior is explicitly idempotent.
- Set `ANDROID_SERIAL` so evidence maps to one serial.
- Install build under test and verify all routes are on the same release build.
- Capture evidence before changing routes:
  - screenshot
  - `adb logcat -d -t 3000`
  - app database export (`wonderfood.db`) when possible
  - route command transcript

## Route matrix

- Share intent (messaging/repost/clipboard flow)
  - Command: script entry `android.intent.action.SEND` with `android.intent.extra.TEXT`
  - Expected: staged proposal/card shown, not auto-write

- Explicit command intent
  - Command: `com.wonderfood.app.action.COMMAND` with action + fields
  - Expected: staged proposal/card shown, not auto-write

- Deep link import
  - Command: `https://wonderfood.app/action?...` or `wonderfood://action?...`
  - Expected: staged proposal/card shown, not auto-write

- Structured text command intent
  - Command: `com.wonderfood.app.action.COMMAND` with `android.intent.extra.TEXT`
  - Expected: staged proposal/card shown, not auto-write

- Direct action shortcut equivalents (voice quick intents)
  - Command: `wonderfood://voice/...`
  - Expected: route-aware draft and confirm flow is visible

## Issue #5 evidence required before marking done

- `share` route screenshot + logcat snippet + proposal count
- `intent` route screenshot + logcat snippet + proposal count
- `deep-link` route screenshot + logcat snippet + proposal count
- `shortcut / google-intent` route screenshot + logcat snippet + proposal count
- `Samsung Routines` and `Google Assistant/App Actions` manual pass notes with device model / build
- One short section confirming no route performed direct mutation without user action

To make the audit easy, write one line per route into a final evidence note:

- serial: `<device serial>`
- build under test: `<app version / tag / build id>`
- route: share / intent / deep-link / shortcut / routine / assistant
- proposal_count observed in UI
- mutation observed: yes/no
- screenshot: `.../<route>/screen.png`
- log tail: `.../<route>/logcat.txt`

Evidence layout expected from
`./scripts/quality/validate-external-automation.sh`:

- `open-today/`
- `voice-shortcut/`
- `explicit-intent/`
- `command-text/`
- `share-intent/`
- `deeplink/`
- `summary/`

## Recommended script path

Use this shared runner before manual pass-in:

```bash
./scripts/quality/validate-external-automation.sh build/evidence/external-routes
```

If a route cannot be triggered on the target build, record the blocked reason and OS/app version in the issue thread.
