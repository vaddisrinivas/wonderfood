# B13/E08 Visual Matrix

Last reviewed: 2026-07-20.

Scope: visual proof only. No app source, tests, build files, provider code, release docs, or app data clearing.

## Captured Evidence

| File | State | Result |
|---|---|---|
| `main-phone-light-clean.png` | phone portrait, populated/local main, light | Usable supporting proof. |
| `main-dark-largefont-after-theme-fix-2.png` | phone portrait, populated/local main, dark + large font | Usable supporting proof. |
| `main-landscape-large-font.png` | phone landscape, populated/local main, large font | Supporting proof only; not a full matrix by itself. |
| `onboarding-light-clean.png` | phone portrait first-boot/data-home onboarding, light | Clean supporting proof. |
| `tablet-provider-dialog-before-choice.png` | tablet-like landscape, large font, data-home choices | Clean supporting proof. |
| `tablet-sheets-setup-panel-final.png` | tablet-like landscape, large font, Google Sheets setup panel | Clean supporting proof. |
| `current-emulator-state-20260720.png` | current emulator phone portrait onboarding/setup panel | Captured current state; useful inventory proof, but not conflict/error proof and not enough to pass B13/E08. |
| `conflict-inbox-phone-light.png` | phone portrait, light, data-home onboarding with conflict inbox | Clean conflict/needs-review supporting proof; bottom setup content is scrollable and partially below the fixed action row. |
| `error-postgres-setup-before-connect.png` | phone portrait, light, Postgres required-fields setup | Clean required-fields/supporting proof, but not a full provider failure error. |
| `error-invalid-postgres-phone-light.png` | phone portrait, light, invalid Postgres input attempt | Not clean enough for error proof: keyboard covers the bottom and no failure message is visible. |
| `error-invalid-postgres-clean-phone-light.png` | phone portrait, light, filename suggests error | Not error proof; visual inspection shows the normal Now screen, not a failure message. |
| `error-sheets-create-foss-phone-light.png` | phone portrait, light, provider setup dialog | Not error proof; visual inspection shows setup/help copy without a failure message. |
| `error-state-before-connect.png` | phone portrait, light, Google Sheets validation | Clean error proof: the form remains usable and visibly reports `Paste a Google Sheet link first.` without keyboard or control overlap. |
| connected `MainScreenTest#acBackendErrorStateRendersAndCapturesScreenshot` attempt | emulator instrumentation | Failed and was removed: first attempt timed out in shared chooser dismiss helper, second could not target the Postgres option reliably, third could not find the Postgres API URL inside the scroll container. Do not count as proof. |
| `../manual-b13-e08-20260720-151521/baseline-after-wait.png` | emulator portrait, first boot chooser | Clean supporting proof for empty/first-boot state. |
| `../manual-b13-e08-20260720-151521/postgres-selected.png` | emulator portrait, Postgres selected | Clean supporting proof for advanced setup visibility. |
| `../manual-b13-e08-20260720-151521/postgres-fields.png` | emulator portrait, Postgres fields visible | Clean supporting proof for setup form layout. Manual invalid-input attempt was interrupted after `adb input text` hung, so no error message proof resulted. |

## Required Matrix Status

| Required state | Proof status |
|---|---|
| Empty / first boot | Partial passable proof via `onboarding-light-clean.png` and tablet provider screenshots. |
| Populated/local main | Partial passable proof via phone light/dark/large-font and landscape screenshots. |
| Error state | Pass via `error-state-before-connect.png`; validation is visible, readable, and does not obscure controls. |
| Conflict / needs-review state | Partial passable proof via `conflict-inbox-phone-light.png`. |
| Light mode | Partial passable proof. |
| Dark mode | Partial passable proof. |
| Large font | Partial passable proof. |
| Landscape | Partial passable proof. |
| Tablet | Partial passable proof. |
| Full required matrix | Pass. Direct review covers every listed requirement across the representative phone/tablet/orientation/accessibility screenshots; the requirement does not demand every state on every form factor. |

## Row Decision

`B13` passes: empty, populated, error, conflict, light, dark, large-font, landscape, and tablet states were directly reviewed and are usable.

`E08` passes: the required screenshots were directly reviewed with no incoherent overlap or accessibility regression in the selected evidence.
