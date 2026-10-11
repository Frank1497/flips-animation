# Razr Fold Scenes: design (v2, overlay architecture)

Date: 2026-10-11. Owner: Franklyn. Status: awaiting written-spec approval.
Supersedes the wallpaper-service design committed as `d3576a1`. Reason in section 3.

## 1. Goal

An Android app for the Motorola Razr 60 Ultra (device `leap`, Android 16, SDK 36).
While the phone folds or unfolds, an animated scene plays full-screen over whichever
screen is lit, driven by the real hinge angle. Franklyn picks scenes inside the app.

Success: on his phone, the scene visibly tracks his hand on the cover screen and on the
inner screen, and the screen swap reads as a blink inside one continuous animation.

## 2. Measured device facts (adb, 2026-10-11)

| Fact | Value | Source |
|---|---|---|
| Hinge sensor | `android.sensor.hinge_angle` (36), on-change, continuous, resolution 0.01 | `dumpsys sensorservice`, live capture |
| Displays | Display 0 inner 1224x2992. Display 1 cover 1080x1272, `FLAG_PRESENTATION` | `dumpsys display` |
| Lit screen | One at a time. Closing: inner lit to about 63 deg, cover from about 56 deg. Opening: cover lit at 30 deg, both off at 87 deg, inner lit by 109 deg | state capture |
| Dark gap | Both screens off for between 0.06 s and 0.24 s (one open, event granularity). Overlay on the inner screen was drawn 39 ms after it turned on | FoldProbe log 21:22:03 |
| Cover wallpaper route | NO-GO. Motorola's cover picker offers Google Photos and Motorola Themes only. Our service did not appear there, with or without the `moto.cli.livewallpaper` tag | tested 21:10 |
| Cover overlay | PASS. `TYPE_ACCESSIBILITY_OVERLAY` through `createDisplayContext().createWindowContext()` drew on display 1 while closed | probe screenshot, commit `8473ea3` |
| Inner overlay | PASS. Same call drew on display 0, 1224x2992, 39 ms after the panel turned on | probe screenshot |
| Accessibility enable | `adb shell settings put secure enabled_accessibility_services ...` works. Duo Open's service stays listed alongside ours | probe report |

## 3. What changed from v1, and why

Motorola owns the cover wallpaper picker, and our wallpaper service is not in it. An
accessibility overlay needs no picker and draws on both displays. The cost: the scene is
a full-screen animation during the fold, not a permanent wallpaper behind the icons.
The overlay fades out when the phone rests flat or shut.

## 4. Non-goals

- No permanent wallpaper behind icons and widgets.
- No Wolverine and no God of War scenes.
- No real 3D models. Rick and Morty faces are flat art with a soft shadow.
- Not published. The Rick and Morty and Spider-Verse art is private use only.
- No frost or blur of the live screen. That was stage 2 of the original idea. The scenes replace it.
- Other phones are unsupported.

## 5. Scenes

| ID | Scene | Behaviour (p = progress 0 to 1, section 7) |
|---|---|---|
| A | Sunrise on the crease | Starry night, sun rises, sky dusk to day |
| B | Eclipse ring | Ring of light on the crease widens and shifts colour |
| C | Mountains assemble | Five layers rise into one landscape |
| D | Spider-Verse | Web spins out over a night city, pixel Peter and Miles masks, no split |
| F | Rick and Morty | Pixel portal. Closed: halves of Rick and Morty fused. Opening: slide apart, then each grows to a full face |

Python prototypes: `~/.hermes/profiles/kratos/cache/scratch/gif/` (`concepts.py`, `concepts3.py`, `concepts5.py`). Reference only.

## 6. Architecture

One Gradle module, Kotlin, minSdk 33, targetSdk 36.

| Unit | Purpose | Depends on |
|---|---|---|
| `HingeSource` | Registers `Sensor.TYPE_HINGE_ANGLE`, emits angle 0..180 only while the overlay service runs | SensorManager |
| `PanelTracker` | Emits which displays are lit and each swap event with the angle at that moment | DisplayManager, HingeSource |
| `ProgressMapper` | Pure: (panel, angle, calibration) to progress p | none |
| `OverlayAlpha` | Pure: (panel, angle, secondsSinceLastMove) to overlay alpha 0..1 | none |
| `CalibrationUpdater` | Median of the last 10 swap angles, replaces the 56 and 109 defaults | PanelTracker |
| `Scene` | `draw(canvas, w, h, p, timeSeconds)` | none |
| Scenes A, B, C | AGSL `RuntimeShader` plus Canvas | Scene |
| Scenes D, F | Low-res grid scaled nearest-neighbour plus bitmap assets | Scene |
| `FoldOverlayService` | `AccessibilityService`. Per lit display, adds one overlay window and drives it on `Choreographer`. Removes it when the display turns off | all above |
| `SceneView` | Custom `View` in each overlay. Draws the active scene at p and alpha | Scene |
| `PickerActivity` | Compose: tick scenes, Pin or Rotate, preview slider, enable-service status and a button to the accessibility settings | SceneRepository |
| `SceneRepository` | Ticked set, mode, last index, in DataStore | DataStore |

Overlay window: `TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN`, `PixelFormat.TRANSLUCENT`. Created with `createDisplayContext(display).createWindowContext(TYPE_ACCESSIBILITY_OVERLAY, null)`. Proven in the probe.

## 7. Progress, alpha and the dark gap

The OS owns the gap. The app removes the jump and the dead range.

With Ac = angle where the cover turns off (default 56) and Ai = angle where the inner turns on (default 109):

- Cover lit: `p = 0.5 * min(angle, Ac) / Ac`
- Inner lit: `p = 0.5 + 0.5 * (angle - Ai) / (180 - Ai)`, clamped to 0.5..1

At the swap both panels show p = 0.5.

Alpha, so the overlay does not hide the home screen at rest:

- Cover: `alpha = smoothstep(0, 12, angle)`
- Inner: `alpha = smoothstep(0, 12, 180 - angle)`
- If the angle has not changed for 0.7 s, alpha fades to 0 over 0.3 s (tent mode, resting half-open). Any new movement restores it.

## 8. Picker behaviour

- Tick any of the five scenes. At least one must stay ticked.
- Pin (one chosen scene) or Rotate (next ticked scene on each unfold).
- Preview: a 0..180 slider that drives p and alpha on a panel-sized preview.
- Service status: shows enabled or disabled, with a button to Accessibility settings and the adb command in a help line.

## 9. Risks

| Risk | Impact | Plan |
|---|---|---|
| Overlay may not draw over the lock screen or secure windows | Scene missing there | Test on the lock screen in acceptance. Document if absent |
| Android 16 restricted settings block enabling a sideloaded accessibility service | User cannot enable it in the UI | adb route proven. The picker shows the steps |
| Frame rate of a full-screen shader on 1224x2992 | Stutter | Scenes D and F draw at 1/7 scale. Measure `dumpsys gfxinfo` in Task 8. Cut effects if frames miss 16.7 ms on 5% or more |
| Hinge events pause below 30 deg (OnePlus did) | Cover end stalls | Hold the last angle. Use the closed device state to force p = 0 |
| Overlay hides a banking or secure screen | Safety | Overlay never takes touch. It fades when idle. No screenshots taken |
| Source art is low resolution and copyrighted | Soft faces. Not publishable | Private use only |

## 10. Testing

- JVM unit tests: `ProgressMapper` (swap continuity, clamps, direction), `OverlayAlpha` (zero at rest, one mid-fold, idle fade), `CalibrationUpdater`, `RotationPolicy`.
- On-device: `SceneRenderTest` renders every scene to a bitmap at both panel sizes and five p values.
- Real fold: slow close and open while `adb logcat` records angle, display state, p and alpha. Pass means p differs by at most 0.05 across the swap.
- Acceptance: Franklyn watches one open and one close per scene.

## 11. Toolchain

Done in Task 1, user space: JDK 17 at `~/tools/jdk-17`, Gradle 9.8.1 at `~/tools/gradle-9.8.1`, SDK at `~/Android/Sdk`. `source env.sh` before any Gradle call.
AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.06.01.

## 12. Build order

1. Toolchain and empty app. Done.
2. Cover and inner overlay probe. Done, both pass.
3. Mapper and alpha (pure, tested).
4. Calibration (pure, tested).
5. Hinge source and panel tracker.
6. Overlay service, scene view, scene A, preview slider. Probe and test wallpaper code removed.
7. Scenes B and C.
8. Scenes D and F with assets.
9. Picker, repository, Pin and Rotate, service status.
10. Real-fold acceptance.
