# Cover-screen wallpaper: how it is actually set (Razr 60 Ultra)

Date: 2026-10-26. Device: motorola_razr_60_ultra (leap_g), adb 192.168.1.15:37455.
Author: Research Bot. Method: jadx decompile of five APK/JAR files plus one live probe on the device.

## TL;DR

1. The cover wallpaper is two ordinary WallpaperManager slots, not a Motorola-private store.
   `FLAG_CLI = 4` is the cover home slot and `FLAG_CLI_LOCK = 16` is the cover lock slot.
   `WallpaperManagerService` on this ROM (Motorola-patched `services.jar`) keeps them as separate
   `WallpaperData` entries backed by the files `wallpaper_cli_orig` and `wallpaper_cli_lock_orig`.
   SystemUI `ImageWallpaper` renders them on display 1.
2. A normal third-party app can write them. The only gate is the ordinary `android.permission.SET_WALLPAPER`,
   which the app already holds. **Confirmed live**: a throwaway test app with only that permission called
   `WallpaperManager.setBitmap(bmp, null, false, 16)`. The cover-lock slot changed from id 46 to id 67
   with a 1080x1272 crop. No signature or privileged permission is involved.
3. `EDIT_SECONDARY_WALLPAPER` is the wrong tool, not a broken one. Motorola's editor is only a crop UI that
   saves a JPEG and returns a `fileUri`. It never calls WallpaperManager. The caller must apply the result
   itself. That is exactly why the wallpaper ids never moved when our app used the editor. (The Moto app
   does apply it itself, see section 2.)
4. Fix: render the 1080x1272 cover frame and call `WallpaperManager.setBitmap(bitmap, null, false, 4)`
   and `(..., 16)` directly. No editor, no Save taps. Details and caveats are in section 6.

## 0. Corrections to the brief (these change the analysis)

| Brief said | Actually |
|---|---|
| `Moto.apk` = `com.motorola.personalize` | It is `com.motorola.moto` v9.0.745.23 (the Moto app, installed under `/data/app` as an updated system app). `com.motorola.personalize` is a separate priv-app at `/system_ext/priv-app/Personalize/Personalize.apk` (v01.0.9.006). I pulled and decompiled both. |
| `CoverScreen Clockface.apk` = `com.motorola.clockface` | It is `apps.ijp.clockface` "CoverScreen Clockface" v CF_BETA_5.8, a third-party Play Store app (installer `com.android.vending`). It has no `EDIT_SECONDARY_WALLPAPER` filter. The real `com.motorola.clockface` is `/system_ext/priv-app/MotoClockFace/MotoClockFace.apk`. I pulled it from the device, and its md5 matches the copy Pickle Rick cached (`00d8e849...`). `cmd package query-activities` on the device confirms the intent resolves to `com.motorola.clockface/...WallpaperPickerGatewayActivity`. |
| ExternalDisplayLauncher "owns/renders the cover home background" | It only draws a blurred copy behind its panels (section 3). SystemUI draws the wallpaper. |

I did not analyse `apps.ijp.clockface` beyond its manifest. It is irrelevant to the mechanism.

## 1. Where the files came from

- `ExternalDisplayLauncher.apk` is byte-identical to the device copy (md5 `71b773e2...`). It is `com.motorola.launcher.secondarydisplay` v16.84.251117.
- `MotoClockFace.apk`, `Personalize.apk` and `services.jar` were pulled from the device. `Moto.apk` came from the project dir.
- jadx output is under `/home/franklyn/work_cw/jx_{Moto,Personalize,MotoClockFace,ExternalDisplayLauncher,svc}/sources`.
- Some jadx methods failed to decompile (coroutine state machines). The findings below come from methods that decompiled cleanly.

## 2. Write side: who calls what

### 2a. com.motorola.clockface, `EDIT_SECONDARY_WALLPAPER` editor (confirmed, decompile)

Entry: `WallpaperPickerGatewayActivity` (exported, actions `EDIT_SECONDARY_WALLPAPER` with `image/*`, and `SET_SECONDARY_WALLPAPER`).
It forwards to `WallpaperPickerCliActivity` when launched on display 1, or `WallpaperPickerMainDisplayActivity` on display 0. `WallpaperPickerCliActivity` finishes at once if the device is not on the cover.

Extras read by `WallpaperPickerBaseActivity.initActivity`:

- `style` (int, default 3, matches the ClockFaceStyle enum we decompiled): this is `targetWhich`.
- `templateId`, `defaultTemplate` (strings).
- `filePath` (string). If empty it becomes `<clockface filesDir>/wallpaper.jpg`.
- The intent data URI is the source image.

On Save the editor runs `PhotoEditFragment.setWallpaperInternal`, then `BaseFragment.saveWallpaper`, then `WallpaperViewModel.saveWallpaper`, then `WallpaperRepository.saveWallpaper` and `saveWallpaperFile`. That path does only this:

- Writes a JPEG (quality 90) to `filePath`.
- Writes a second JPEG to `<clockface filesDir>/<templateId>/{home|lock}/<name>_<uniqueId>.jpg`. The folder is `lock` only when `style == 2`, otherwise `home`.
- Updates the clockface `wallpaper` SharedPreferences.
- Calls `setResult(RESULT_OK)` with the data URI set to a `content://com.motorola.clockface.fileprovider/...` URI (flags 3, read and write grant) and extras `success`, `wallpaperType`, `wallpaperId`, `wallpaperInfo`, `fileUri`.
- `success` is inverted: it is 1 when saving failed and 0 when it worked. RESULT_OK is returned either way. Do not trust `success`.

**It does not call `WallpaperManager` on this path.** The code that does call it, `WallpaperViewModel.setWallpaper` and `WallpaperRepository.setWallpaper`, which map `style 3` to `which 4` and anything else to `which 16`, has no callers in the picker UI.
I checked both the jadx output and the raw dex dump Pickle Rick cached. The only real callers of `setBitmapToWallpaperManager` are the clock-face-editor path (`WallpaperManagerInternalImpl.setWallpaper`, from `BaseViewClockFaceTemplate.onQuitEditMode`) and the AI "Moo" wallpaper path.

Consequence: our `RESULT_OK` plus unchanged ids is the expected behaviour, not a bug in how we call it. The editor was never going to change the slot.

### 2b. com.motorola.moto (Moto.apk), the app that really changes the cover (confirmed, decompile)

`StyleSyncPreviewFragment` and `GenerativeStylePreviewFragment` do this:

1. Launch `EDIT_SECONDARY_WALLPAPER` (`image/*`, data = image URI, `filePath=""`, flags 3). **No `style` extra**, so the editor defaults to style 3.
2. On `RESULT_OK` they read the result extra `fileUri`.
3. They call `b91.N(fileUri, ws.HOME_SCREEN)`, which calls `g65.b`. Its helper `g(bitmap, which)` does `WallpaperManager.setStream(...)` or `setBitmap(bitmap, null, false, which)`:
   - `HOME_SCREEN` sets `which=4`.
   - `LOCK_SCREEN` sets `which=16`.
   - `BOTH_SCREENS` sets 4 then 16.
   Log tag text: `setCLIWallpaper - <ws> - result = <bool>`.

So the Moto app is a client of exactly the same public `WallpaperManager` API plus the editor's returned file. The editor is a crop step, and Moto does the write.

### 2c. com.motorola.personalize (Personalize.apk), does NOT write the cover on this build (single-source)

`ThemesViewModel.setCliWallpaperIfNeed` and `MyActionThemesViewModel` do call `WallpaperManager.setBitmap(bmp, null, true, 4)` or `setWallpaperComponent(..., 4)`. But they are gated by `Utilities.isCLISupported()`, which is `SUPPORT_CLI_WALLPAPER.booleanValue() && CLIManager.isCLISupported()`. In this build `SUPPORT_CLI_WALLPAPER` is the constant `Boolean.FALSE`, so the branch is dead code.
The Styles-screen change you saw tonight therefore most likely came through the Moto app (2b), not Personalize. I did not trace the exact UI screen you used, so treat "which app" as unconfirmed.
Personalize also declares `com.motorola.permission.SECONDARY_WALLPAPER_CHANGED`, which I did not chase.

### 2d. Server side: why this is possible from a normal app (confirmed, `services.jar` decompile plus live)

`WallpaperManagerService.setWallpaper(...)` (the binder method behind `WallpaperManager.setBitmap/setStream`):

- Permission check: `checkPermission("android.permission.SET_WALLPAPER")` only. This is a normal, install-time permission.
- Then the app-op check `isWallpaperSupported` (app op 48) and `isSetWallpaperAllowed` (no `no_set_wallpaper` user restriction, caller package matches uid).
- `which` is accepted if `(mHasCLI ? 20 : 0 | 3 | (mHasDesktop ? 8 : 0)) & which != 0`. So 4, 16 and 20 (4|16) are valid on a CLI device.
- `getWallpaperSafeLocked(user, which)` picks `mCLIWallpaperMap` for any value with bit 4, and `mCLILockWallpaperMap` for exactly 16. Each backs onto `wallpaper_cli_orig` or `wallpaper_cli_lock_orig` in `/data/system/users/0/` (the log line on my test confirmed `file=wallpaper_cli_lock_orig`).
- No caller UID, signature or `READ_WALLPAPER_INTERNAL` check on the write path. That permission is only required for the read side (`getWallpaperFile`).

**Slot interaction (code-read, not live-tested):** when you set `which=4` while the cover-lock slot is empty and the current home is a static image, the service first migrates the old home image into the lock slot (`migrateStaticSystemToLockWallpaperLocked(user, 1)`: "Migrating current cli wallpaper to be lock-only"). If you want both slots to show your image, set both explicitly (or use `which=20`, see section 6).

## 3. Read side: ExternalDisplayLauncher (confirmed, decompile)

- The only wallpaper read in the whole launcher is `view/BackgroundBlurView`. It listens for `android.intent.action.WALLPAPER_CHANGED`, then:
  - bails if there is a live wallpaper,
  - requires `android.permission.READ_WALLPAPER_INTERNAL`,
  - calls `WallpaperManager.getWallpaperFile(4)` (falling back to `WallpaperManager.openDefaultWallpaper(ctx, 4)`),
  - crops and blurs it behind the panel UI.
  The hidden helpers `WallpaperManagerHidden.FLAG_CLI = 4` and `getBitmap(wm, 4)` match.
- No ContentProvider, Settings key or private file path is used for the wallpaper. Its providers (`...page`, `...widgetPackages`, `...initializer`) are for page configuration and widgets.
- The actual cover home background is drawn by SystemUI `ImageWallpaper`. Live `dumpsys wallpaper` shows the CLI slot's wallpaper connection with `mDisplayId=1` and `mWallpaperComponent=ImageWallpaper`, and `mHasCLI` is `CLIManager.isCLISupported() && display 1 exists` in the service.

## 4. Cross-reference: do write and read paths match? (yes)

| Hop | Evidence |
|---|---|
| Moto app writes `setBitmap(..., which=4 or 16)` | `g65.g`, plus the `setCLIWallpaper` log tag (Moto.apk decompile) |
| MotoClockFace's own wallpaper code uses the same constants | `FLAG_CLI = 4`, `FLAG_CLI_LOCK = 16` in `WallpaperRepository`, plus `PreferencesRepository` keys the lock variant on `16 == which` |
| Server stores `which` 4 and 16 in separate CLI maps and files | `services.jar` `getWallpaperSafeLocked`, `wallpaper_cli_orig`, `wallpaper_cli_lock_orig` |
| Launcher reads `getWallpaperFile(4)` | `BackgroundBlurView` (ExternalDisplayLauncher decompile) |
| Clockface watches for external changes | `CLIWallpaperChangedReceiver` (`WALLPAPER_CHANGED` with `which` and `callingPackage` extras) schedules `CLIWallpaperChangedExternallyJobService`, which re-snapshots the CLI wallpaper into the clock face config. This is inferred from code, not observed live. |

That is a consistent loop confirmed by independent decompiles (Moto app, MotoClockFace, ExternalDisplayLauncher, services.jar), and by the live probe for slot 16.

## 5. Live test (what I actually ran)

A 12 KB throwaway APK (`dev.cwprobe`, only `uses-permission SET_WALLPAPER`, signed with the debug keystore; source in `/home/franklyn/work_cw/probe/`) was installed and run on the phone:

- `setBitmap(1080x1272 magenta, null, false, 16)` returned 67. `getWallpaperId(16)` went 46 to 67.
  `dumpsys wallpaper`: CLI lock slot `id=67`, `mCropHint=Rect(0,0 - 1080,1272)`. No SecurityException. Logcat: `setWallpaper which=0x10 ... updateWallpaperBitmapLocked() : id=67 file=wallpaper_cli_lock_orig`.
- `clear(16)` then ran as cleanup. See the side effect below.
- The probe app is uninstalled.

**Not verified: that the pixels actually appear on the cover.** The phone was open, so display 1 was off, and `screencap -d 4630947043778501780` returned a blank 8373-byte image even after a wake keyevent. You need to fold the phone or screencap with the cover on to confirm visually.

**Side effect I caused, and it is yours to know about:** my cleanup `clear(16)` removed the separate cover-lock slot. `dumpsys wallpaper` now shows the CLI wallpaper as one entry with `mWhich=20` (home and lock together), `id=68`, `mBindSource=SET_LIVE`, component still `ImageWallpaper`, crop `1962x2304` unchanged from the photo you set tonight. Before: home id 66 and a separate lock entry id 46 (Motorola default, crop 567x666). So the cover lock screen now falls back to the home image. The photo file itself appears intact (crop unchanged), but I cannot confirm it visually either. If the cover is not what you expect, re-apply it from the Moto app.
I did not test `which=4` live because it would overwrite your personal photo and I cannot back it up without root.

## 6. What to change in the app

Confidence ratings are in section 7.

1. Replace the editor flow with a direct write. In `WallpaperApplier`:
   ```kotlin
   val wm = WallpaperManager.getInstance(ctx)
   wm.setBitmap(coverBitmap1080x1272, null, false, 16)  // cover lock (what the closed, PIN-locked cover shows)
   wm.setBitmap(coverBitmap1080x1272, null, false, 4)   // cover home
   ```
   Use plain ints (`4`, `16`): the `FLAG_CLI*` constants are hidden API. `setBitmap(Bitmap, Rect, boolean, int)` itself is public and the live probe accepted `16`. `SET_WALLPAPER` is already in the manifest, so no new permission is needed.
2. Optional single call: `which = 20` (4 or 16) is accepted by the server and is the state Motorola itself reaches after `clear(16)` (`mWhich=20`). I read this in the code and saw the resulting state, but did not call `setBitmap` with 20 myself.
3. Success check: compare `getWallpaperId(4)` and `getWallpaperId(16)` before and after. They do change on a real write (my test: 46 to 67). The existing `slotOutcome` helper works for this, but only with a direct write: with the editor, ids never move.
4. If you want to keep Motorola's crop UI: launch `EDIT_SECONDARY_WALLPAPER` with `startActivityForResult`, read `fileUri` from the result extras (or `intent.data`), decode it, then call `setBitmap(..., which)` yourself, as the Moto app does. Without the final `setBitmap` step the editor does nothing visible.
5. Guard for non-Motorola devices: `which=4/16` is only valid when the service has `mHasCLI`. Otherwise the call throws `IllegalArgumentException("Must specify a valid wallpaper category")`. Wrap in `runCatching`, or gate on `PackageManager.hasSystemFeature` / display count (display 1 must exist).
6. The ordering caveat from 2d: set lock first, or set both explicitly, so a stale home image is not migrated into the lock slot. Not live-tested.

## 7. Confidence per finding

| Finding | Confidence | Basis |
|---|---|---|
| A normal app with `SET_WALLPAPER` can write cover slot 16 via `setBitmap(..., 16)` | **Confirmed** | live probe, plus `services.jar` permission check |
| Slot 4 (cover home) is writable the same way | High, not live-tested | same server branch; the Moto app uses it; skipped to avoid clobbering your photo |
| No signature or privileged permission needed for the write | **Confirmed** | `services.jar` shows only `SET_WALLPAPER` plus app-op 48 and user restriction; probe succeeded |
| `EDIT_SECONDARY_WALLPAPER` does not itself change WallpaperManager state | **Confirmed**, two sources | MotoClockFace decompile and dex (no live callers of `setWallpaper`), plus your observation that ids never move |
| The Moto app applies the editor's `fileUri` with `setBitmap(..., 4/16)` | **Confirmed** | Moto.apk decompile (`g65.g`, `b91.N`, result handling) |
| Launcher reads `getWallpaperFile(4)`; SystemUI draws the cover wallpaper | **Confirmed** (launcher read, SystemUI via dumpsys) | ExternalDisplayLauncher decompile, `dumpsys wallpaper` `mDisplayId=1` |
| Personalize does not write the cover on this build | Single-source | `SUPPORT_CLI_WALLPAPER = false` constant in jadx output; not cross-checked in smali |
| Pixels from my probe actually appeared on the cover | **Unknown** | cover display was off; screencaps blank |
| `which=20` works in one call | Plausible, not tested | code read only |
| Clockface re-snapshots after an external CLI change | Plausible, not observed | receiver and job service code only |
| `style` extra only selects `targetWhich` (file folder and crop mask) | Confirmed | `initActivity`, `WallpaperViewModel` |

## 8. One discrepancy to resolve

The brief says the four slot ids did not change during the Moto/Styles change. Pickle Rick's earlier dump (`cache/scratch/razr/wallpaper.txt`, 19:42 on 10-25) shows CLI home `id=55`. Tonight's dumps show `id=66`. So the CLI home slot did change sometime in between, consistent with the Moto flow writing `which=4`. My guess is the "before" dump in the brief was taken after that write had already happened. The lock slot (`id=46`, `mBindSource=UNKNOWN`) really was untouched until my probe. I cannot prove the timing from the data I have.

## Appendix: key code locations

- Server: `jx_svc/.../wallpaper/WallpaperManagerService.java` lines ~4364 (`setWallpaper`), ~5675 (`getWallpaperSafeLocked`), ~713 (CLI file names).
- Editor: `jx_MotoClockFace/.../cli_wallpaper_picker/{view/WallpaperPickerBaseActivity,viewmodel/WallpaperViewModel,repository/WallpaperRepository,utils/FileUtils}.java`, `view/BaseFragment$saveWallpaper$2$1.java` (result extras).
- Moto app: `jx_Moto/.../motoaccount/g65.java`, `styletransfer/ui/generative/StyleSyncPreviewFragment.java` (~726, ~505), `aihub/.../GenerativeStylePreviewFragment.java` (~586, ~736).
- Launcher: `jx_ExternalDisplayLauncher/.../secondarydisplay/view/BackgroundBlurView.java`.
- Personalize: `jx_Personalize/.../styles/{Utilities,WallpaperUtils,viewmodel/ThemesViewModel}.java`.
