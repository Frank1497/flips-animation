# Razr Fold Scenes: design

Date: 2026-10-11. Owner: Franklyn. Status: awaiting written-spec approval.

## 1. Goal

An Android app for the Motorola Razr 60 Ultra (device `leap`, Android 16, SDK 36).
It plays an animated wallpaper scene that follows the real hinge angle as the phone
opens and closes. Franklyn picks which scenes he wants in the app.

Success: on his phone, the scene visibly tracks his hand on whichever screen is lit,
at both ends of the fold, and the screen swap reads as a blink inside the animation,
not as a jump in progress.

## 2. Measured device facts (from adb, 2026-10-11)

| Fact | Value | Source |
|---|---|---|
| Hinge sensor | `Hinge Angle Wakeup`, `android.sensor.hinge_angle` (36), on-change, registered at 66.7 ms, resolution 0.01 | `dumpsys sensorservice`, DuoHinge log |
| Continuous | Yes. Values seen: 180, 174, 168, 161, 152 ... 6, then 30, 87, 109, 126, 147, 162 | live capture of one slow close and open |
| Displays | Display 0 = inner, 1224x2992. Display 1 = cover, 1080x1272, `FLAG_PRESENTATION` (assignment inferred from state order) | `dumpsys display` |
| Lit screen | One at a time. Closing: inner lit down to about 63 deg, cover lit from about 56 deg. Opening: cover lit at 30 deg, both dark at 87 deg, inner lit by 109 deg | state capture |
| Dark gap | One sample (under about 0.4 s) in each direction at 0.18 s sampling. True length unknown | state capture |
| Fold states | CLOSED_HALL, CLOSED, TENT, HALF_OPENED_QVD, HALF_OPENED_MAIN, OPENED, OPENED_HALL | `cmd device_state print-states` |
| Cover wallpaper today | Motorola live wallpaper `blue_ryhme_cli`, display id 1 | `dumpsys wallpaper` |

## 3. Non-goals (first release)

- No system-wide frost overlay. That is stage 2, a separate spec.
- No Wolverine and no God of War scenes. Removed by Franklyn.
- No real 3D models. The Rick and Morty faces are flat art with a soft shadow.
- Not published. The Rick and Morty and Spider-Verse art is private use only.
- Other phones are untested and unsupported.

## 4. Scenes

| ID | Scene | Behaviour (p = progress 0 to 1, see section 6) |
|---|---|---|
| A | Sunrise on the crease | Starry night, sun rises, sky dusk to day, horizon on the hinge line |
| B | Eclipse ring | Ring of light centred on the crease widens and shifts colour |
| C | Mountains assemble | Five layers rise into one landscape, sun lifts |
| D | Spider-Verse | Web spins out from the crease over a night city, sky heats navy to crimson, pixel Peter and Miles masks. Masks do not split (split is Rick and Morty only) |
| F | Rick and Morty | Pixel portal background. Closed: half of Rick fused with half of Morty. Opening: slide apart (Rick up, Morty down), then each grows to a full face with the portal between |

Prototype code for all five is in `~/.hermes/profiles/kratos/cache/scratch/gif/`
(`concepts.py`, `concepts3.py`, `concepts5.py`). It is Python reference only, not shipped.

## 5. Architecture

One Gradle module, Kotlin, minSdk 33, targetSdk 36.

| Unit | Purpose | Depends on |
|---|---|---|
| `HingeSource` | Registers `Sensor.TYPE_HINGE_ANGLE`, emits angle 0..180 only while the wallpaper is visible | SensorManager |
| `PanelTracker` | Reports which display is lit and its size. Fires on display on/off events and records the angle at each event | DisplayManager, HingeSource |
| `ProgressMapper` | Pure function: (lit panel, angle, calibration) to progress p. Section 6 | none |
| `Calibration` | Stores the angles at which each panel turns off and on (defaults 56 and 109). Updates from the last 10 real swap events | PanelTracker, DataStore |
| `Scene` (interface) | `draw(canvas, w, h, p, timeSeconds)` | none |
| Scenes A, B, C | AGSL `RuntimeShader` plus small Canvas draws | Scene |
| Scenes D, F | Canvas draws of low-res grids scaled nearest-neighbour, plus bitmap assets | Scene |
| `FoldWallpaperService` | `WallpaperService.Engine`, one per display. Gets p each frame, draws the active scene | all above |
| `PickerActivity` | Compose UI. Tick scenes, toggle Pin or Rotate, preview with a hinge-angle slider | DataStore |
| `SceneRepository` | Holds the ticked set and the mode. Rotate mode advances one scene per unfold | DataStore |

Each unit is testable alone. `ProgressMapper` and `Calibration` run as plain JVM unit tests.

## 6. Dark gap handling

The OS turns one screen off and the other on. No app can draw on a dark panel, so the
gap itself cannot be removed. The app removes the jump instead.

Old behaviour: p follows the raw angle, so about 53 degrees of progress vanish across the gap.

New mapping, with Ac = angle where the cover turns off (default 56) and Ai = angle where the inner turns on (default 109):

- Cover lit: `p = 0.5 * min(angle, Ac) / Ac`
- Inner lit: `p = 0.5 + 0.5 * (angle - Ai) / (180 - Ai)`, clamped to 0.5..1

At the swap both panels show p = 0.5, so the gap reads as a blink at one continuous frame.
Closing runs the same mapping backwards. Calibration replaces the defaults with measured
swap angles after a few folds. The mapper uses display on and off events as truth, and the angle only for position within a panel.

## 7. Picker behaviour

- Tick any of the five scenes.
- Mode toggle: Pin (play one chosen scene) or Rotate (next ticked scene on each unfold).
- Preview: a hinge-angle slider that drives p on both panel sizes without folding the phone.
- Set wallpaper button for the live wallpaper.

## 8. Risks

| Risk | Impact | Plan |
|---|---|---|
| Android may not let a third-party live wallpaper take the cover display. Motorola controls it today | The whole cover half fails | **Milestone 0.** Build a one-screen test wallpaper first. Test on the cover. If blocked: ship inner-screen only, or move the cover to the stage-2 accessibility overlay |
| Hinge events may stop under 30 degrees (OnePlus Open did) | Cover end of the fold stalls | Hold the last angle. Fade to p = 0 on the closed-state event |
| Frame rate on the 1224x2992 panel with D and F | Stutter | Draw pixel scenes at 1/7 scale, test with the phone's frame stats. Cut effects if under 60 fps |
| Source art is low resolution and copyrighted | Soft faces. Not publishable | Private use only. Replace art before any release |
| Build toolchain missing on this machine | Nothing builds | Section 10 |

## 9. Testing

- JVM unit tests: `ProgressMapper` (swap continuity, clamps, reverse direction), `Calibration` (median update, bad data rejected).
- On-device: install over adb (`adb install`), drive p with the picker slider, screenshot each display with `adb exec-out screencap -p -d <displayId>` and compare against the prototype GIF frames.
- Real fold: slow close and open while `adb logcat` records angle, display state and p. Pass means p is continuous at the swap in the log.
- Acceptance: Franklyn watches one open and one close of each scene on the phone.

## 10. Toolchain (needs Franklyn's approval before install)

- `openjdk-17-jdk` and Google Android command-line tools from apt, then the SDK platform 36 and build-tools through `sdkmanager`.
- Gradle through the project wrapper. Estimated 3 GB. Disk free: 1138 GB.
- Project path: `~/Projects/razr-fold-scenes` (local disk, not the read-only HDD).

## 11. Build order

0. Milestone 0: cover-wallpaper test (go or no-go).
1. Hinge, panel tracker, mapper, calibration, with unit tests.
2. Wallpaper service with Scene A and the picker slider preview.
3. Scenes B, C.
4. Scenes D, F with the art assets.
5. Picker polish, Rotate mode, real-fold test on the phone.

## 12. Open items

None blocking. Stage 2 (frost overlay) gets its own spec after this app ships.
