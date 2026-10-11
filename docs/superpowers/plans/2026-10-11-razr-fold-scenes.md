# Razr Fold Scenes Implementation Plan (v2, overlay architecture)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android app for the Razr 60 Ultra that plays hinge-driven scenes (A sunrise, B eclipse, C mountains, D Spider-Verse, F Rick and Morty) as a full-screen overlay on whichever screen is lit, chosen in a picker.

**Architecture:** An `AccessibilityService` adds one `TYPE_ACCESSIBILITY_OVERLAY` window per lit display. Each frame it reads the hinge angle, `ProgressMapper` gives progress p, `OverlayAlpha` gives opacity, and the active `Scene` draws. Pure logic is JVM-tested. Drawing is tested on the phone.

**Tech Stack:** Kotlin, Gradle wrapper, AGSL `RuntimeShader` plus Canvas, Jetpack Compose, DataStore, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-11-razr-fold-scenes-design.md` (v2, commit `9fa68d4`)

**Supersedes:** the wallpaper-service plan at commit `1a0c5d4`. Tasks 1 and 2 are complete (see Status).

## Status

- Task 1 complete: toolchain and empty app (`ade87b5`).
- Tasks 3 to 6b complete (see the ledger). Task 6c adds the v3 wallpaper design.
- Task 2 complete: the cover wallpaper route is NO-GO. The accessibility overlay passed on both displays (`8473ea3`). Task 2's wallpaper code is removed in Task 6.

## Global Constraints

- Device: Motorola Razr 60 Ultra, device `leap`, Android 16 (SDK 36). Other phones unsupported.
- minSdk 33, targetSdk 36, compileSdk 36, Java 17. AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.06.01.
- Project `/home/franklyn/Projects/razr-fold-scenes`, branch `feat/fold-scenes`, package `dev.franklyn.foldscenes`. Run `source env.sh` before every Gradle call.
- Displays: inner is display 0 (1224x2992), cover is display 1 (1080x1272, `FLAG_PRESENTATION`).
- Progress (v3): `p = angle / 180` on both panels, clamped 0..1, NaN is 0. No calibration. Cover rest frame is p = 0, inner rest frame is p = 1.
- Wallpapers (v3, a MUST from Franklyn): choosing a scene sets both wallpapers. Inner: `WallpaperManager.setBitmap` with the p = 1 frame (home). Cover: the p = 0 frame saved to MediaStore, then Motorola's editor opened with `com.motorola.intent.action.EDIT_SECONDARY_WALLPAPER`, type `image/png`, the content URI and `FLAG_GRANT_READ_URI_PERMISSION` (without the flag the preview is blank). Franklyn taps Save. The service never presses it.
- Alpha: cover `smoothstep(0, 12, angle)`. Inner `smoothstep(0, 12, 180 - angle)`. Idle factor: 1 until 0.7 s since the angle last changed, then falls linearly to 0 at 1.0 s. Alpha = base times idle factor.
- Overlay window: `TYPE_ACCESSIBILITY_OVERLAY`, flags `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN`, `PixelFormat.TRANSLUCENT`, created with `createDisplayContext(display).createWindowContext(TYPE_ACCESSIBILITY_OVERLAY, null)`. Proven in `8473ea3`.
- Scenes are exactly A, B, C, D, F. Face split applies to F only. No wallpaper service.
- The overlay never takes touch and never captures the screen.
- Rick and Morty and Spider-Verse art is private use only.
- Install route (proven): `scp -P 8022` the APK to `u0_a495@192.168.1.15:`, then `adb -s <ip:port> install -r` through Termux's adb over SSH. The adb port changes on every wireless-debugging toggle. Re-read it before each session. Pairing needs Franklyn. Never request or type pairing codes.
- Duo Open (`com.duoopen`) is installed with its accessibility service enabled. It draws its own overlay on folds and would double up with ours. From Task 6 on, enabled services are exactly our service. Disable, never uninstall, and note it in the ledger.

## Review Focus

1. No sensor event when the overlay service starts. Use the last known angle, else 180 if the device state is OPENED, else 0.
2. Angle is NaN, negative or above 180. Clamp to 0..180. Never crash. p and alpha stay in 0..1.
3. Both displays lit at once. Each overlay maps its own panel and neither blocks the other.
4. Service stops or the display turns off. The hinge listener is unregistered, the overlay is removed, and no frame callback keeps running.
5. Zero scenes ticked, or one scene in Rotate mode. Fall back to scene A. The picker blocks unticking the last scene.

---

### Task 3: ProgressMapper and OverlayAlpha

**Files:**
- Create: `app/src/main/kotlin/dev/franklyn/foldscenes/fold/ProgressMapper.kt`, `app/src/main/kotlin/dev/franklyn/foldscenes/fold/OverlayAlpha.kt`
- Test: `app/src/test/kotlin/dev/franklyn/foldscenes/fold/ProgressMapperTest.kt`, `app/src/test/kotlin/dev/franklyn/foldscenes/fold/OverlayAlphaTest.kt`
- Modify: `app/build.gradle.kts` (add `testImplementation("junit:junit:4.13.2")` through the version catalog)

**Interfaces:**
- Produces: `enum class Panel { COVER, INNER }`, `data class Calibration(val coverOffAngle: Float = 56f, val innerOnAngle: Float = 109f)`, `fun progress(panel: Panel, angle: Float, cal: Calibration = Calibration()): Float`, `fun overlayAlpha(panel: Panel, angle: Float, idleSeconds: Float): Float`.

- [ ] **Step 1: Write failing tests.** `ProgressMapperTest`:
  - `cover_closed_is_zero`: `progress(COVER, 0f) == 0f`
  - `cover_at_swap_is_half`: `progress(COVER, 56f) == 0.5f`
  - `cover_beyond_swap_clamps_at_half`: `progress(COVER, 90f) == 0.5f`
  - `inner_at_swap_is_half`: `progress(INNER, 109f) == 0.5f`
  - `inner_flat_is_one`: `progress(INNER, 180f) == 1f`
  - `inner_below_swap_clamps_at_half`: `progress(INNER, 60f) == 0.5f`
  - `swap_is_continuous`: with `Calibration(50f, 120f)`, `progress(COVER, 50f) == progress(INNER, 120f)`
  - `monotonic_per_panel`: p never decreases as angle rises, sampled every 1 deg, both panels
  - `bad_angles_clamp`: `NaN`, `-10f`, `400f` return a value in 0..1 for both panels

  `OverlayAlphaTest`:
  - `cover_closed_is_zero`: `overlayAlpha(COVER, 0f, 0f) == 0f`
  - `cover_mid_fold_is_one`: `overlayAlpha(COVER, 40f, 0f) == 1f`
  - `inner_flat_is_zero`: `overlayAlpha(INNER, 180f, 0f) == 0f`
  - `inner_mid_fold_is_one`: `overlayAlpha(INNER, 120f, 0f) == 1f`
  - `smoothstep_midpoint`: `overlayAlpha(COVER, 6f, 0f) == 0.5f` within 1e-4
  - `idle_below_threshold_keeps_alpha`: `overlayAlpha(COVER, 40f, 0.7f) == 1f`
  - `idle_fades_linearly`: `overlayAlpha(COVER, 40f, 0.85f)` is 0.5f within 1e-4
  - `idle_past_end_is_zero`: `overlayAlpha(COVER, 40f, 1.0f) == 0f` and `overlayAlpha(COVER, 40f, 5f) == 0f`
  - `bad_inputs_clamp`: `NaN` angle, a negative idle and `400f` all return a value in 0..1
- [ ] **Step 2: Run** `source env.sh && ./gradlew testDebugUnitTest --tests '*ProgressMapperTest' --tests '*OverlayAlphaTest'`. Expected: FAIL, symbols not defined.
- [ ] **Step 3: Implement** both from the Global Constraints formulas. Treat NaN angle as 0 and a negative idle as 0. Clamp the angle to 0..180 first.
- [ ] **Step 4: Run the same command.** Expected: PASS, 18 tests.
- [ ] **Step 5: Commit** `feat: progress mapper and overlay alpha`.

### Task 4: Calibration update

**Files:**
- Create: `app/src/main/kotlin/dev/franklyn/foldscenes/fold/CalibrationUpdater.kt`
- Test: `app/src/test/kotlin/dev/franklyn/foldscenes/fold/CalibrationUpdaterTest.kt`

**Interfaces:**
- Consumes: `Calibration`.
- Produces: `enum class SwapType { COVER_OFF, INNER_ON }`, `data class SwapEvent(val type: SwapType, val angle: Float)`, `fun updated(current: Calibration, events: List<SwapEvent>): Calibration`.

- [ ] **Step 1: Write failing tests:**
  - `median_of_last_ten`: 12 COVER_OFF events, the first two at 30 deg and the next ten at 60 deg, give `coverOffAngle == 60f`
  - `rejects_out_of_range`: COVER_OFF at 5 or 95 and INNER_ON at 70 or 160 are ignored
  - `keeps_order`: if the result would give `innerOnAngle <= coverOffAngle`, return `current` unchanged
  - `empty_returns_current`
  - Accepted ranges: cover 20..90, inner 80..150.
- [ ] **Step 2: Run the class.** Expected: FAIL.
- [ ] **Step 3: Implement** with the median of the last 10 accepted events per type.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: calibration from real swap events`.

### Task 5: Hinge source and panel tracker

**Files:**
- Create: `.../fold/HingeSource.kt`, `.../fold/PanelTracker.kt`
- Test: `app/src/androidTest/kotlin/dev/franklyn/foldscenes/fold/HingeSourceTest.kt`
- Modify: `app/build.gradle.kts` (androidTest dependencies, `testInstrumentationRunner`)

**Interfaces:**
- Consumes: `SwapEvent`, `SwapType`.
- Produces: `class HingeSource(context: Context)` with `val angle: StateFlow<Float>`, `val lastEventNanos: StateFlow<Long>`, `fun start()`, `fun stop()`. `class PanelTracker(context: Context, hinge: HingeSource)` with `fun litDisplayIds(): Set<Int>`, `val swaps: SharedFlow<SwapEvent>`, `fun start()`, `fun stop()`.

- [ ] **Step 1: Write the instrumented tests.** `start_then_stop_unregisters_listener` (Review Focus 4): the sensor listener count returns to 0 after `stop()`. `initial_angle_defaults_when_no_event` (Review Focus 1): 180 if the device state is OPENED, else 0. `angle_is_clamped` (Review Focus 2).
- [ ] **Step 2: Run on the phone:** `adb shell am instrument -w` for the test APK. Expected: FAIL.
- [ ] **Step 3: Implement `HingeSource`** on `Sensor.TYPE_HINGE_ANGLE` at the fastest delay. Clamp to 0..180 and stamp `lastEventNanos` on every change.
- [ ] **Step 4: Implement `PanelTracker`** on `DisplayManager.DisplayListener`. On each display state change, update `litDisplayIds()` and emit a `SwapEvent` (COVER_OFF when display 1 goes off, INNER_ON when display 0 turns on) with the current hinge angle.
- [ ] **Step 5: Run the tests.** Expected: PASS. Then fold the phone slowly and check `logcat` for `SwapEvent` lines with angles near 56 and 109.
- [ ] **Step 6: Commit** `feat: hinge source and panel tracker`.

### Task 6: Scene interface, scene A, overlay service, preview slider, cleanup

**Files:**
- Create: `.../scene/Scene.kt`, `.../scene/SceneA.kt`, `.../scene/Scenes.kt`, `.../overlay/FoldOverlayService.kt`, `.../overlay/SceneView.kt`, `.../ui/PreviewScreen.kt`, `app/src/main/res/xml/fold_accessibility.xml`
- Modify: `AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `PickerActivity.kt`
- Delete: `.../probe/ProbeAccessibilityService.kt`, `app/src/main/res/xml/probe_accessibility.xml`, `.../wallpaper/TestWallpaperService.kt`, `app/src/main/res/xml/test_wallpaper.xml`, and their manifest entries and strings
- Test: `app/src/androidTest/kotlin/dev/franklyn/foldscenes/scene/SceneRenderTest.kt`

**Interfaces:**
- Consumes: `progress`, `overlayAlpha`, `HingeSource`, `PanelTracker`, `Panel`.
- Produces: `interface Scene { val id: String; fun draw(canvas: Canvas, width: Int, height: Int, p: Float, timeSeconds: Float) }`, `object Scenes { val all: List<Scene>; fun byId(id: String): Scene }`, `class FoldOverlayService : AccessibilityService`, `class SceneView(context: Context) : View` with `var scene: Scene`, `var progress: Float`, `var alpha01: Float`.

- [ ] **Step 1: Write `SceneRenderTest`.** For every scene in `Scenes.all`, at sizes 1224x2992 and 1080x1272, draw to a `Bitmap` at p in `{0, 0.25, 0.5, 0.75, 1}`. Assert no exception, the bitmap is not a single colour, and p=0 differs from p=1.
- [ ] **Step 2: Run on the phone.** Expected: FAIL, no scenes.
- [ ] **Step 3: Implement `SceneA`** as an AGSL `RuntimeShader` ported from `sunrise()` in `~/.hermes/profiles/kratos/cache/scratch/gif/concepts.py`.
- [ ] **Step 4: Implement `SceneView`**: draws the active scene at p with `alpha01` applied to the layer. Never touchable.
- [ ] **Step 5: Implement `FoldOverlayService`.** Per lit display from `PanelTracker`, create the overlay window exactly as in Global Constraints (copy the proven call from `ProbeAccessibilityService`). Drive each overlay from `Choreographer` frames: angle from `HingeSource`, idle seconds from `lastEventNanos`, then `progress` and `overlayAlpha`. Remove the overlay and stop the frame callback when its display turns off or the service is destroyed (Review Focus 4).
- [ ] **Step 6: Implement `PreviewScreen`** with a 0..180 slider and a Cover or Inner toggle that drives a panel-sized `SceneView`.
- [ ] **Step 7: Remove** the probe and test-wallpaper files listed above. Run `./gradlew assembleDebug`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 8: Run `SceneRenderTest`.** Expected: PASS for scene A.
- [ ] **Step 9: Install and enable on the phone.** Set `enabled_accessibility_services` to exactly our service (this disables Duo Open's service without uninstalling it) and `accessibility_enabled` to 1. Read it back. Then screenshot each display while the overlay shows, and fold slowly once with `logcat` recording p and alpha.
- [ ] **Step 10: Commit** `feat: overlay service, scene A, preview; remove probes`.

### Task 6c: Linear progress, remove calibration, SceneBitmap, WallpaperApplier

**Files:**
- Modify: `fold/ProgressMapper.kt`, `fold/PanelRouting.kt`, `overlay/FoldOverlayService.kt`, `ui/PreviewScreen.kt`, `PickerActivity.kt`, `AndroidManifest.xml`, `androidTest/.../scene/SceneRenderTest.kt`
- Delete: `fold/CalibrationUpdater.kt`, `fold/PanelTracker.kt`, their tests (`CalibrationUpdaterTest.kt`, `PanelSwapTest.kt`), and `PanelRoutingTest` cases for `isPlausibleSwap`
- Create: `wallpaper/SceneBitmap.kt` (move the logic of `androidTest/.../scene/RenderHelper.kt` into main), `wallpaper/WallpaperApplier.kt`, tests below

**Interfaces:**
- Produces: `fun progress(angle: Float): Float` (replaces the panel and calibration form), `object SceneBitmap { fun render(scene: Scene, width: Int, height: Int, p: Float, timeSeconds: Float = 0f): Bitmap }` (hardware route), `object WallpaperApplier { fun applyInner(context: Context, scene: Scene): Result<Unit>; fun applyCover(context: Context, scene: Scene): Result<Uri>; fun coverFileName(sceneId: String): String }`.
- Constants: inner size 1224x2992, cover size 1080x1272.

- [ ] **Step 1: Rewrite `ProgressMapperTest` first.** `zero_is_zero`, `flat_is_one`, `ninety_is_half`, `linear` (p equals angle/180 within 1e-6 every 1 deg), `monotonic`, `bad_angles_clamp` (NaN, -10, 400 stay in 0..1). Add `coverFileName_is_stable_and_safe`: `coverFileName("sunrise") == "razrfold_sunrise_cover.png"` and an id with unsafe characters is sanitized to letters, digits and underscores. Run it. Expected: FAIL.
- [ ] **Step 2: Implement** `progress(angle)` and `coverFileName`. Remove `Calibration`, `isValid`, the range constants, `CalibrationUpdater`, `PanelTracker`, `swapFor`, `isPlausibleSwap` and their tests. Keep `Panel`, `panelFor`, `sanitizeAngle`, `overlayAlpha`. Update every caller (service, PreviewScreen). Run `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 3: Update `FoldOverlayService`.** Drop the tracker, the swap collector, `calibration` and `swapEvents`. `p = progress(hinge.angle.value)`. Log lines for frames and overlay add and remove stay.
- [ ] **Step 4: Write the instrumented tests (RED first).** `scene_bitmap_has_requested_size_and_content` (inner 1224x2992 and cover 1080x1272, more than 20 distinct sampled colours), `rest_frames_differ` (p = 0 differs from p = 1, mean channel difference over 8), `cover_editor_intent_resolves` (`PackageManager.resolveActivity` for the Moto action with type `image/png` is not null on this phone), `applyCover_inserts_mediastore_row_and_overwrites` (call the save half twice, exactly one row named `razrfold_sunrise_cover.png` remains; do NOT start the editor activity in this test, split the save into `saveCoverFrame(context, scene): Uri` that the test calls). Do not call `setBitmap` in any test.
- [ ] **Step 5: Implement `SceneBitmap.render`** with `RenderNode`, `HardwareRenderer` and `ImageReader` exactly as the existing test helper does, then delete the test helper and point `SceneRenderTest` at `SceneBitmap`.
- [ ] **Step 6: Implement `WallpaperApplier`.** `applyInner`: render p = 1 at 1224x2992, `WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)`, return `Result`. `applyCover`: `saveCoverFrame` renders p = 0 at 1080x1272 and writes it with `MediaStore.Images` into `Pictures/RazrFoldScenes` (delete an existing row with the same name first; use `IS_PENDING`), then `startActivity` of the intent from Global Constraints with `FLAG_ACTIVITY_NEW_TASK`. If the intent does not resolve, return a failure whose message says the image is saved in Pictures/RazrFoldScenes. Add `<uses-permission android:name="android.permission.SET_WALLPAPER" />`. Run all rendering off the main thread except the scene `draw` call, which must stay on the main thread (shared shader invariant): render through a `Handler(Looper.getMainLooper())` hop or `withContext(Dispatchers.Main)`.
- [ ] **Step 7: UI.** In `PickerActivity`, under the preview: a button "Set inner wallpaper" and a button "Set cover wallpaper". Show the Result as a text line (success, or the failure message). A one-line note under the cover button: "Opens Motorola's editor. Tap Save there."
- [ ] **Step 8: Verify.** `assembleDebug assembleDebugAndroidTest testDebugUnitTest`, install both APKs by the proven route, run the instrument and report `OK (N tests)`. Do NOT tap either apply button on the phone and never call `setBitmap` on the device. The controller applies the wallpapers with Franklyn.
- [ ] **Step 9: Commit** `feat: linear progress, wallpaper applier; remove calibration`.

### Task 7: Scenes B and C

**Files:**
- Create: `.../scene/SceneB.kt`, `.../scene/SceneC.kt`
- Modify: `Scenes.kt`

**Interfaces:**
- Consumes: `Scene`. Produces: `SceneB` and `SceneC` in `Scenes.all`.

- [ ] **Step 1: Add both ids to `Scenes.all`.** Run `SceneRenderTest`. Expected: FAIL on the new ids.
- [ ] **Step 2: Implement `SceneB`** (eclipse ring, ported from `eclipse()`), a shader. **Implement `SceneC`** (five mountain layers, ported from `mountains()`), Canvas paths.
- [ ] **Step 3: Run `SceneRenderTest`.** Expected: PASS. Screenshot p = 0, 0.5, 1 for each and compare with the prototype.
- [ ] **Step 4: Commit** `feat: scenes B and C`.

### Task 8: Scenes D and F with assets

**Files:**
- Create: `.../scene/SceneD.kt`, `.../scene/SceneF.kt`, `app/src/main/res/drawable-nodpi/rick.png`, `morty.png`, `spider.png`, `miles.png`, `scripts/export_assets.py`
- Modify: `Scenes.kt`

**Interfaces:**
- Consumes: `Scene`. Produces: `SceneD`, `SceneF` in `Scenes.all`.

- [ ] **Step 1: Write `export_assets.py`.** It crops Rick and Morty from `~/.hermes/profiles/kratos/cache/scratch/gif/src/rm_keyed.png` (split at x=478, recentred on the chin line, as in `concepts5.py`) and exports `spider.png` and `miles.png` (32x34) from `concepts3.py`. Run it with `~/yolo-venv/bin/python`. Verify four files exist and open.
- [ ] **Step 2: Run `SceneRenderTest`.** Expected: FAIL on D and F.
- [ ] **Step 3: Implement `SceneD`.** Pixel web grid and city, masks nearest-neighbour at 1/7 scale, matching `bg_spider()`. Peter and Miles masks side by side, no split.
- [ ] **Step 4: Implement `SceneF`.** Pixel portal at 1/7 scale (`bg_rick()`) plus the face split: slide over 0..100 deg-equivalent, grow over 85..150, tilt up to 6 deg. Map p to those 0..180 deg-equivalents.
- [ ] **Step 5: Run `SceneRenderTest`.** Expected: PASS. Then `dumpsys gfxinfo` while the preview slider sweeps. Record frame times. Missing 16.7 ms on 5% or more of frames means cut effects.
- [ ] **Step 6: Commit** `feat: scenes D and F with assets`.

### Task 9: Scene picker (pin one scene), repository, service status, deferred fixes

Rotate mode is DROPPED (Franklyn, 22:41): the cover wallpaper needs his tap per change, so one pinned scene keeps both wallpapers and the overlay matched.

**Files:**
- Create: `.../data/SceneRepository.kt`, `.../data/SceneChoice.kt`, `.../ui/PickerScreen.kt`, `.../ui/ServiceStatus.kt`
- Modify: `PickerActivity.kt`, `overlay/FoldOverlayService.kt`, `ui/PreviewScreen.kt`, `scene/SceneF.kt` (pre-warm hook only), `FoldScenesApp.kt`, `AndroidManifest.xml` (only if needed)
- Test: `app/src/test/kotlin/dev/franklyn/foldscenes/data/SceneChoiceTest.kt`, instrumented `app/src/androidTest/.../data/SceneRepositoryTest.kt`

**Interfaces:**
- Consumes: `Scenes.all`, `Scenes.byId`, `WallpaperApplier`, `SceneBitmap`, `HingeSource`.
- Produces: `fun resolveSceneId(saved: String?): String` (pure; returns `saved` if it is the id of a scene in `Scenes.all`, else the first scene's id), `class SceneRepository(context: Context)` with `val sceneId: Flow<String>`, `suspend fun setSceneId(id: String)`, `fun currentSceneIdBlocking(): String` (DataStore preferences, key `scene_id`, always passed through `resolveSceneId`), `object ServiceStatus { fun isEnabled(context: Context): Boolean }` (reads `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` and checks for our component, tolerating the `:` list and either component-string form).

- [ ] **Step 1: Write failing JVM tests (`SceneChoiceTest`).** `known_id_is_kept` (every id in `Scenes.all`... NOTE: `Scenes.all` builds `Paint`s in two scene constructors, so a JVM test that touches `Scenes` would hit unmocked Android classes. Take the list of valid ids as a parameter instead: `fun resolveSceneId(saved: String?, validIds: List<String>): String`; the repository passes `Scenes.all.map { it.id }`). Tests: `known_id_is_kept` (`resolveSceneId("eclipse", ids5) == "eclipse"`), `null_falls_back_to_first` (`== "sunrise"`), `unknown_falls_back_to_first`, `blank_falls_back_to_first`, `empty_valid_ids_returns_empty_string_without_crashing`. Run. Expected: FAIL (symbol not defined).
- [ ] **Step 2: Implement `resolveSceneId` and `SceneRepository`.** Add the DataStore preferences dependency to the version catalog (latest stable compatible with Kotlin 2.4.21). Instrumented `SceneRepositoryTest` on the phone: `default_is_first_scene` (fresh DataStore name for the test, not the app's real one), `set_then_read_roundtrip`, `invalid_stored_value_resolves_to_first`. The test must use its own DataStore file and delete it afterwards. Run JVM tests, expected PASS.
- [ ] **Step 3: Overlay uses the chosen scene.** In `FoldOverlayService`, read `SceneRepository.currentSceneIdBlocking()` once when each overlay is created (a blocking read of a tiny DataStore on the main thread at overlay creation is acceptable; it happens at most a few times per fold; say so in a comment) and set `view.scene = Scenes.byId(id)`. Also collect `sceneId` flow in a service-scoped coroutine on `Dispatchers.Main.immediate` so a change in the picker updates live overlays, cancelled in teardown.
- [ ] **Step 4: Deferred fixes folded in (each from the ledger):**
  - Frame loop pauses while idle: if every overlay has `alpha == 0` and the last hinge event is more than 1.5 s old, do not repost the Choreographer callback; resume when `HingeSource.lastEventNanos` changes (collect it, or poll it from a cheap `Handler.postDelayed` every 250 ms while paused, whichever is simpler and leaves no leaked callback on teardown). Review Focus 4 still holds: nothing runs after teardown.
  - Gate the 100 ms per-overlay frame log behind `BuildConfig.DEBUG`.
  - `SceneView.onDraw`: latch the scene off after the first thrown exception (a failing shader must not log a stack every frame): keep a `failed` flag, log once.
  - Pre-warm Scene F's face bitmaps off the main thread at app start (`FoldScenesApp` posts the shadow-blur work to a background thread; the final bitmap assignment must be thread-safe, e.g. via a `@Volatile` field or a `lazy(SYNCHRONIZED)`), without drawing anything off main. If this cannot be done without touching the main-thread-only draw invariant, skip it and say so.
  - Apply-button `busy` state survives rotation/folding: move it into a `ViewModel` (`PickerViewModel`), and run the apply on `viewModelScope`; the result text also lives there.
  - Soften the `SceneRenderTest`-adjacent merge test: change the colour-distance rule from `> 40` to `> 30` (it measured ~49), and sample Rick's face at y = 35% instead of 30%.
- [ ] **Step 5: Build `PickerScreen` in Compose** inside the existing `Scaffold`. A vertical list of the five scenes, each row a radio button with the scene name and a thumbnail: the thumbnail is the scene's p = 0.5 frame rendered at 200 px wide through `SceneBitmap` on `Dispatchers.Default` once per scene (cache the Bitmaps in the ViewModel; show a placeholder until ready). Scene names: sunrise "Sunrise", eclipse "Eclipse", mountains "Mountains", spiderverse "Spider-Verse", rickmorty "Rick and Morty". Selecting a row calls `setSceneId`; it does NOT apply wallpapers by itself. Below the list: the preview slider (existing `PreviewScreen`, now previewing the chosen scene), then three buttons: **Set inner wallpaper**, **Set cover wallpaper** and **Set both** (inner first; when it succeeds the cover editor opens; the Save tap stays with Franklyn). Each button applies the CURRENTLY chosen scene (`Scenes.byId(chosen)`), never the first. A short warning line above the buttons: "Setting the inner wallpaper replaces your current one. It is not backed up." A service status row at the top: `ServiceStatus.isEnabled` shown as "Fold animation: on / off", with a button "Open accessibility settings" (`Settings.ACTION_ACCESSIBILITY_SETTINGS`) and a one-line help text with the adb command `adb shell settings put secure enabled_accessibility_services dev.franklyn.foldscenes/dev.franklyn.foldscenes.overlay.FoldOverlayService`. Recheck the status in `onResume`.
- [ ] **Step 6: Tests for the UI logic.** JVM test `ServiceStatusParseTest` for the pure parser `fun containsService(setting: String?, component: ComponentName-string pair)` (write it as a pure function taking Strings: `isServiceListed(setting: String?, pkg: String, cls: String): Boolean`): listed alone, listed in a `:`-separated list, listed in the short form `pkg/.overlay.FoldOverlayService`, absent, null, empty, substring-lookalike (`other.pkg/...FoldOverlayService` must be false). Run RED then GREEN.
- [ ] **Step 7: Verify.** `assembleDebug assembleDebugAndroidTest testDebugUnitTest`, install both APKs by the proven route, run the instrument (`OK (N tests)`). Confirm the accessibility setting still lists our service. Do NOT launch the app and do NOT apply any wallpaper on the phone; the controller does that with Franklyn.
- [ ] **Step 8: Commit** in two commits: `feat: scene choice repository and picker` and `fix: deferred overlay and apply fixes`.

### Task 10: Real-fold acceptance

**Files:**
- Create: `docs/superpowers/notes/acceptance.md`

- [ ] **Step 1: Record** `adb logcat` for tag `FoldScenes` while Franklyn folds slowly shut, waits 3 s, and opens slowly. Run it for each of the five scenes.
- [ ] **Step 2: Check the log.** At each swap, `p` on the last cover frame and the first inner frame differ by at most 0.05. Closing mirrors opening. Expected: true for all five.
- [ ] **Step 3: Test the lock screen.** Fold with the phone locked. Record whether the overlay draws, in `acceptance.md`.
- [ ] **Step 4: Franklyn watches** one open and one close per scene and answers pass or fail per scene in `acceptance.md`.
- [ ] **Step 5: Confirm at rest.** The wallpaper frame persists closed and open, and the overlay fades into it with no visible jump at either end, on both screens.
- [ ] **Step 6: Commit** `test: real-fold acceptance`.
