# Razr Fold Scenes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android app for the Razr 60 Ultra that plays hinge-driven wallpaper scenes (A sunrise, B eclipse, C mountains, D Spider-Verse, F Rick and Morty), chosen in a picker.

**Architecture:** One Gradle module. A `WallpaperService` runs one engine per display. Each frame the engine reads the hinge angle, `ProgressMapper` converts it to progress p for the lit panel, and the active `Scene` draws p. Pure logic (mapper, calibration, rotation) is JVM-tested. Drawing is tested on the phone.

**Tech Stack:** Kotlin, Gradle wrapper, AGSL `RuntimeShader` plus Canvas, Jetpack Compose, DataStore, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-11-razr-fold-scenes-design.md`

## Global Constraints

- Device: Motorola Razr 60 Ultra, device `leap`, Android 16 (SDK 36). Other phones unsupported.
- minSdk 33, targetSdk 36, compileSdk 36, Java 17.
- Project path `/home/franklyn/Projects/razr-fold-scenes` (local disk, never the HDD).
- Package `dev.franklyn.foldscenes`.
- Displays: inner is display 0 (1224x2992), cover is display 1 (1080x1272). Assignment inferred, verify in Task 2.
- Swap defaults: cover turns off at 56 deg, inner turns on at 109 deg.
- Progress mapping: cover `p = 0.5 * min(angle, Ac) / Ac`; inner `p = 0.5 + 0.5 * (angle - Ai) / (180 - Ai)`, clamped 0.5..1.
- Scenes are exactly A, B, C, D, F. No Wolverine, no God of War. Face split applies to F only.
- Rick and Morty and Spider-Verse art is private use only. Never published.
- Install route (proven): `scp` the APK to Termux at `u0_a495@192.168.1.15:8022`, then `adb install` through Termux's adb. The wireless-debugging port changes on every toggle. Re-read it from the phone before each session.
- `sudo apt install` needs Franklyn's explicit yes at execution time.

## Review Focus

1. Sensor sends no event when the wallpaper first appears. The scene must still draw: use the last known angle, else 180 if the device state is OPENED, else 0.
2. Angle is NaN, negative or above 180. Clamp to 0..180. Never crash, never draw p outside 0..1.
3. Both displays lit at once (concurrent device states). Each engine maps its own panel. Neither blocks the other.
4. Wallpaper not visible. The hinge listener must be unregistered, or the battery drains.
5. Zero scenes ticked, or one scene in Rotate mode. Fall back to scene A. The picker blocks unticking the last scene.

---

### Task 1: Toolchain and empty app that installs

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/dev/franklyn/foldscenes/PickerActivity.kt`, `.gitignore`

**Interfaces:**
- Produces: a debug APK at `app/build/outputs/apk/debug/app-debug.apk` with a launcher activity.

- [ ] **Step 1: Ask Franklyn for the sudo yes, then run** `sudo apt install openjdk-17-jdk google-android-cmdline-tools-13.0-installer`. Verify: `javac -version` prints 17.
- [ ] **Step 2: Install the SDK** with `sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"` into `~/Android/Sdk`. Set `ANDROID_HOME`. Verify: `sdkmanager --list_installed` shows all three.
- [ ] **Step 3: Install Gradle** from the services.gradle.org distribution zip into `~/tools`, then run `gradle wrapper` in the project. The apt Gradle (4.4.1) is too old. Pin AGP, Kotlin and Compose BOM versions in `libs.versions.toml` to the latest stable that work together, and record them in the commit message.
- [ ] **Step 4: Create** a Compose app whose `PickerActivity` shows the text "Razr Fold Scenes".
- [ ] **Step 5: Build.** Run `./gradlew assembleDebug`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 6: Install on the phone** by the proven route. Expected: `Success`, and `pm list packages | grep foldscenes` lists the package.
- [ ] **Step 7: Commit** `chore: scaffold app and toolchain`.

### Task 2: Milestone 0, can a third-party wallpaper run on the cover screen

**Files:**
- Create: `app/src/main/kotlin/dev/franklyn/foldscenes/wallpaper/TestWallpaperService.kt`, `app/src/main/res/xml/test_wallpaper.xml`
- Modify: `AndroidManifest.xml`

**Interfaces:**
- Produces: a go or no-go note in `docs/superpowers/notes/milestone0.md`. Go means our wallpaper draws on display 1.

- [ ] **Step 1: Implement `TestWallpaperService : WallpaperService`** whose engine fills its surface with solid red on display 0 and solid blue on any other display (`displayContext.display.displayId`), and logs `displayId` and surface size to tag `FoldTest`.
- [ ] **Step 2: Install, then open** the live wallpaper chooser (`android.service.wallpaper.LIVE_WALLPAPER_CHOOSER`) on the cover screen and on the inner screen and set the test wallpaper from each.
- [ ] **Step 3: Verify the go criterion.** Run `dumpsys wallpaper` and look for our component with `mDisplayId=1`. Run `screencap -p -d 4630947043778501780` for the cover. The image must be blue. Also read the `FoldTest` log.
- [ ] **Step 4: Write `milestone0.md`** with go or no-go, the log lines, and the screenshot path.
- [ ] **Step 5: Branch on the result.** Go: continue to Task 3. No-go: stop, report to Franklyn, and offer inner-only or the stage-2 overlay. Do not start Task 6 onward.
- [ ] **Step 6: Commit** `spike: cover-display wallpaper test (go/no-go)`.

### Task 3: ProgressMapper

**Files:**
- Create: `app/src/main/kotlin/dev/franklyn/foldscenes/fold/ProgressMapper.kt`
- Test: `app/src/test/kotlin/dev/franklyn/foldscenes/fold/ProgressMapperTest.kt`

**Interfaces:**
- Produces: `enum class Panel { COVER, INNER }`, `data class Calibration(val coverOffAngle: Float = 56f, val innerOnAngle: Float = 109f)`, `fun progress(panel: Panel, angle: Float, cal: Calibration = Calibration()): Float`.

- [ ] **Step 1: Write failing tests**, one per assertion:
  - `cover_closed_is_zero`: `progress(COVER, 0f) == 0f`
  - `cover_at_swap_is_half`: `progress(COVER, 56f) == 0.5f`
  - `cover_beyond_swap_clamps_at_half`: `progress(COVER, 90f) == 0.5f`
  - `inner_at_swap_is_half`: `progress(INNER, 109f) == 0.5f`
  - `inner_flat_is_one`: `progress(INNER, 180f) == 1f`
  - `inner_below_swap_clamps_at_half`: `progress(INNER, 60f) == 0.5f`
  - `swap_is_continuous`: `progress(COVER, cal.coverOffAngle) == progress(INNER, cal.innerOnAngle)` for `Calibration(50f, 120f)`
  - `monotonic_per_panel`: p never decreases as angle rises, sampled every 1 deg, both panels
  - `bad_angles_clamp`: `NaN`, `-10f`, `400f` all return a value in 0..1
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*ProgressMapperTest'`. Expected: FAIL, symbol not defined.
- [ ] **Step 3: Implement** the Global Constraints formulas. Treat NaN as 0. Clamp the angle to 0..180 first.
- [ ] **Step 4: Run the same command.** Expected: PASS, 9 tests.
- [ ] **Step 5: Commit** `feat: progress mapper with swap continuity`.

### Task 4: Calibration update

**Files:**
- Create: `app/src/main/kotlin/dev/franklyn/foldscenes/fold/CalibrationUpdater.kt`
- Test: `app/src/test/kotlin/dev/franklyn/foldscenes/fold/CalibrationUpdaterTest.kt`

**Interfaces:**
- Consumes: `Calibration` from Task 3.
- Produces: `enum class SwapType { COVER_OFF, INNER_ON }`, `data class SwapEvent(val type: SwapType, val angle: Float)`, `fun updated(current: Calibration, events: List<SwapEvent>): Calibration`.

- [ ] **Step 1: Write failing tests:**
  - `median_of_last_ten`: 12 COVER_OFF events, the first two at 10 deg and the next ten at 60 deg, give `coverOffAngle == 60f`
  - `rejects_out_of_range`: COVER_OFF at 5 or 95 deg and INNER_ON at 70 or 160 deg are ignored
  - `keeps_order`: if the results would give `innerOnAngle <= coverOffAngle`, return `current` unchanged
  - `empty_returns_current`: no events returns `current`
  - Accepted ranges: cover 20..90, inner 80..150.
- [ ] **Step 2: Run** the new test class. Expected: FAIL.
- [ ] **Step 3: Implement** with the median of the last 10 accepted events per type.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: calibration from real swap events`.

### Task 5: Hinge source and panel tracker

**Files:**
- Create: `.../fold/HingeSource.kt`, `.../fold/PanelTracker.kt`
- Test: `app/src/androidTest/kotlin/dev/franklyn/foldscenes/fold/HingeSourceTest.kt`

**Interfaces:**
- Consumes: `Panel`, `SwapEvent`, `SwapType`.
- Produces: `class HingeSource(context: Context)` with `val angle: StateFlow<Float>`, `fun start()`, `fun stop()`. `class PanelTracker(context: Context, hinge: HingeSource)` with `fun isLit(displayId: Int): Boolean`, `val swaps: SharedFlow<SwapEvent>`.

- [ ] **Step 1: Write the instrumented test** `start_then_stop_unregisters_listener`, asserting the SensorManager listener count returns to 0 after `stop()` (Review Focus 4). Add `initial_angle_defaults_when_no_event`: with no event, angle is 180 if the device state is OPENED, else 0 (Review Focus 1).
- [ ] **Step 2: Run on the phone:** `adb shell am instrument -w` for the test APK. Expected: FAIL.
- [ ] **Step 3: Implement `HingeSource`** on `Sensor.TYPE_HINGE_ANGLE`, a wake-up sensor at 66.7 ms (16 ms if the sensor allows it). On `onSensorChanged`, clamp to 0..180.
- [ ] **Step 4: Implement `PanelTracker`** on `DisplayManager.DisplayListener`. On each display on or off event, emit a `SwapEvent` with the current hinge angle.
- [ ] **Step 5: Run the test again.** Expected: PASS. Then fold the phone slowly and check `logcat` for `SwapEvent` lines with angles near 56 and 109.
- [ ] **Step 6: Commit** `feat: hinge source and panel tracker`.

### Task 6: Scene interface, scene A, wallpaper engine, preview slider

**Files:**
- Create: `.../scene/Scene.kt`, `.../scene/SceneA.kt`, `.../wallpaper/FoldWallpaperService.kt`, `.../ui/PreviewScreen.kt`
- Test: `app/src/androidTest/kotlin/dev/franklyn/foldscenes/scene/SceneRenderTest.kt`
- Modify: manifest (service replaces `TestWallpaperService`)

**Interfaces:**
- Produces: `interface Scene { val id: String; fun draw(canvas: Canvas, width: Int, height: Int, p: Float, timeSeconds: Float) }`, `object Scenes { val all: List<Scene>; fun byId(id: String): Scene }`.

- [ ] **Step 1: Write `SceneRenderTest`.** For every scene in `Scenes.all`, at sizes 1224x2992 and 1080x1272, draw to a `Bitmap` at p in `{0, 0.25, 0.5, 0.75, 1}`. Assert: no exception, the bitmap is not a single colour, and the p=0 bitmap differs from the p=1 bitmap.
- [ ] **Step 2: Run on the phone.** Expected: FAIL, no scenes.
- [ ] **Step 3: Implement `SceneA`** as an AGSL `RuntimeShader` ported from `sunrise()` in `~/.hermes/profiles/kratos/cache/scratch/gif/concepts.py`.
- [ ] **Step 4: Implement `FoldWallpaperService`.** One engine per display, a frame loop on `Choreographer`. Each frame: angle from `HingeSource`, panel from the display id, `p = progress(...)`, then `Scenes.byId(active).draw(...)`. Start the hinge on `onVisibilityChanged(true)`, stop it on false.
- [ ] **Step 5: Implement `PreviewScreen`** with a 0..180 slider that drives p for both panel sizes.
- [ ] **Step 6: Run the test.** Expected: PASS for scene A. Screenshot each display at the slider's 0, 56 and 180 and compare with the prototype frames by eye.
- [ ] **Step 7: Commit** `feat: scene A, wallpaper engine, preview slider`.

### Task 7: Scenes B and C

**Files:**
- Create: `.../scene/SceneB.kt`, `.../scene/SceneC.kt`
- Modify: `Scenes.kt`

**Interfaces:**
- Consumes: `Scene`. Produces: the objects `SceneB` and `SceneC`, listed in `Scenes.all`.

- [ ] **Step 1: Add both to `Scenes.all`.** Run `SceneRenderTest`. Expected: FAIL on the new ids.
- [ ] **Step 2: Implement `SceneB`** (eclipse ring, ported from `eclipse()`), a shader. **Implement `SceneC`** (five mountain layers, ported from `mountains()`), Canvas paths.
- [ ] **Step 3: Run `SceneRenderTest`.** Expected: PASS. Screenshot p = 0, 0.5, 1 for each and compare with the prototype.
- [ ] **Step 4: Commit** `feat: scenes B and C`.

### Task 8: Scenes D and F with assets

**Files:**
- Create: `.../scene/SceneD.kt`, `.../scene/SceneF.kt`, `app/src/main/res/drawable-nodpi/rick.png`, `morty.png`, `spider.png`, `miles.png`, `scripts/export_assets.py`
- Modify: `Scenes.kt`

**Interfaces:**
- Consumes: `Scene`. Produces: `SceneD`, `SceneF` in `Scenes.all`.

- [ ] **Step 1: Write `export_assets.py`.** It crops Rick and Morty from `~/.hermes/profiles/kratos/cache/scratch/gif/src/rm_keyed.png` (split at x=478, recentred on the chin line, as in `concepts5.py`) and exports `spider.png` and `miles.png` (32x34) from `concepts3.py`. Run it. Verify: four files exist and open.
- [ ] **Step 2: Run `SceneRenderTest`.** Expected: FAIL on D and F.
- [ ] **Step 3: Implement `SceneD`.** Pixel web grid and city, masks drawn nearest-neighbour at 1/7 scale, matching `bg_spider()`. Peter and Miles masks sit side by side. No face split.
- [ ] **Step 4: Implement `SceneF`.** Pixel portal at 1/7 scale (`bg_rick()`), plus the face split: slide `smooth(0, 100 deg)`, grow `smooth(85, 150 deg)`, tilt up to 6 deg. Drive the slide and grow from p scaled to 0..180 deg equivalents.
- [ ] **Step 5: Run `SceneRenderTest`.** Expected: PASS. Run the frame check: `dumpsys gfxinfo` while the slider sweeps. Record frame times. Over 16.7 ms on 5% or more of frames means cut effects.
- [ ] **Step 6: Commit** `feat: scenes D and F with assets`.

### Task 9: Picker, repository, Pin and Rotate

**Files:**
- Create: `.../data/SceneRepository.kt`, `.../data/RotationPolicy.kt`
- Modify: `PickerActivity.kt`
- Test: `app/src/test/kotlin/dev/franklyn/foldscenes/data/RotationPolicyTest.kt`

**Interfaces:**
- Consumes: `Scenes.all`.
- Produces: `enum class Mode { PIN, ROTATE }`, `data class Selection(val ticked: Set<String>, val mode: Mode, val pinned: String, val lastIndex: Int)`, `fun nextScene(sel: Selection): Pair<String, Selection>`.

- [ ] **Step 1: Write failing tests:**
  - `rotate_cycles_ticked_in_order`: ticked A, C, F cycles A, C, F, A
  - `pin_always_returns_pinned`
  - `empty_ticked_falls_back_to_A`
  - `single_ticked_in_rotate_returns_it`
  - `unknown_ids_ignored`
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement `nextScene`** as a pure function. Back it with `SceneRepository` on DataStore. The engine calls `nextScene` once per unfold, when the angle first rises above 20 deg after being closed.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Build the picker in Compose.** Checkboxes for the five scenes, the Pin and Rotate toggle, a block on unticking the last scene, a Preview button, a Set wallpaper button.
- [ ] **Step 6: Install and tick a scene in the app.** Expected: the wallpaper changes after the next unfold. Verify with `logcat`.
- [ ] **Step 7: Commit** `feat: picker with pin and rotate`.

### Task 10: Real-fold acceptance

**Files:**
- Create: `docs/superpowers/notes/acceptance.md`

- [ ] **Step 1: Record** `adb logcat` for tags `FoldTest|FoldScenes` while Franklyn folds slowly shut, waits 3 s, and opens slowly. Run it for each of the five scenes.
- [ ] **Step 2: Check the log.** At each swap, `p` on the last cover frame and the first inner frame differ by at most 0.05. Closing mirrors opening. Expected: true for all five scenes.
- [ ] **Step 3: Franklyn watches** one open and one close per scene and answers pass or fail per scene in `acceptance.md`.
- [ ] **Step 4: Update calibration defaults** in `Calibration` to the measured medians if they differ by more than 5 deg from 56 and 109.
- [ ] **Step 5: Commit** `test: real-fold acceptance`.
