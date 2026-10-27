# Flip's Animation (razr-fold-scenes) Redo PRD v2

## HR Eng

| Flip's Animation Redo PRD v2 |  | Franklyn calls the current app "boring." The fold-scene engine works (7 scenes, hinge-driven, wallpaper handoff; cover-wallpaper bug ROOT-CAUSED and fix VERIFIED 2026-10-25). The app around it has no identity and a flat, unpolished picker screen. This revamp commits to: the FSF mark as the launcher icon, the name "Flip's Animation," a full light/dark color system that follows the phone's system theme, a redesigned PickerScreen with real motion craft, and a mapped user flow — scoped against the plan already executed for this project (`docs/superpowers/plans/2026-10-11-razr-fold-scenes.md`) so the redo sits on proven ground, not a guess. |  |
| **Author**: Kratos **Contributors**: Pickle Rick (engineering + logo asset + cover-bug fix), Franklyn (product, final calls) **Intended audience**: Engineering, Franklyn | **Status**: Draft v2 — decisions locked **Created**: 2026-10-25 **Revised**: 2026-10-25 | **Self Link**: `docs/prd/2026-10-25-app-redo-prd.md` **Figma**: `https://www.figma.com/design/l8UPpuaEZ9QUeEeZ7DOXyu` **Context**: design doc, implementation plan, SDD ledger (all `docs/superpowers/` + `.superpowers/sdd/`) |

## Decisions locked this revision (were open in v1)

| Question | v1 status | Decision |
| :---- | :---- | :---- |
| App name | Open, default proposed | **Flip's Animation** — confirmed by Franklyn |
| Logo | Not sourced | **FSF mark** ("Free Stuff by Franklyn" flaming-lock badge), reused as-is per Franklyn's explicit call — confirmed NOT original to this project, confirmed NOT third-party IP (Franklyn's own asset from his FMF/Jellyfin project, source: `~/hdd-backup/Projects/FMF/backups/fsf-logo-deploy.png`, 1326×1326) |
| Theme | Scoped, not built | **Light + Dark, synced to phone system setting** — Android `Resources.Configuration.UI_MODE_NIGHT_*` / Compose `isSystemInDarkTheme()`, no in-app toggle, no manual override |
| Cover-wallpaper bug | Parallel/blocking item | **Root-caused and fix verified live on-device by Franklyn, 2026-10-25.** See Engineering Findings below. Folded into this PRD's scope since it changes the "Set cover wallpaper" UI contract (two Save taps, not one). |

## Introduction

razr-fold-scenes ("Flip's Animation") is a private Android app for Franklyn's Motorola Razr 60 Ultra. An `AccessibilityService` draws an animated scene over the cover and inner displays while the phone folds, driven by the real hinge angle, and sets matching wallpapers at rest. Seven scenes exist (Sunrise, Eclipse, Mountains, Spider-Verse, Rick and Morty, Ronaldo, can-can). The engine works. The app around it does not look like something anyone would choose to open — this PRD fixes that, end to end: identity, theme, screen, flow.

## Engineering Findings Folded Into This Revamp (not re-litigated, see ledger for full evidence)

1. **Cover-wallpaper bug, root cause (Pickle Rick, 2026-10-25):** Motorola's cover display has two independent wallpaper slots — unlocked/home (`which=4`) and lock screen (`which=16`). The closed cover almost always shows the LOCK slot (Franklyn's phone has a PIN). `WallpaperApplier.applyCover` never sent the undocumented `style` intent extra that Motorola's picker uses to route the Save — so every prior Save silently wrote only the home slot, never the lock slot the user actually sees. **Fix, verified working on-device by Franklyn:** launch the editor twice — `style=2` (lock) then the existing default/`style=3` (home) — two Save taps, both slots covered, no silent writes (holds the design doc's "no silent cover wallpaper change" rule). This is now a **P0 functional requirement** of this redo, not a side fix: the picker UI must reflect a two-tap cover flow, and the status row must report both slots independently.
2. **Overlay mechanism is sound.** `dumpsys window`/`dumpsys display` confirmed the accessibility overlay correctly attaches to display 1 (cover) in all fold states; the earlier theory that Motorola's display layout map hides display 1 was a dead end (`mCurrentLayout` only ever lists display 0 because display 1 runs in its own `FLAG_OWN_DISPLAY_GROUP` — this is normal, not a bug). No engine change needed here.
3. **Scene state at time of audit:** datastore had `rickmorty` selected, not `ronaldo` as the ledger assumed — picker UI should make the current selection unambiguous at a glance (ties into CUJ 2 below).

## Problem Statement

**Current process:** Franklyn opens `PickerActivity`, picks a scene from a plain radio list, taps three buttons to apply wallpapers (cover wallpaper previously silently failed on the lock slot — now fixed, see above), folds the phone to see the result.
**Primary users:** Franklyn, sole user, sole device.
**Pain points (ledger, Task 9 live look + this revision's audit):**
- No app identity — no logo, no color system (`colors.xml` does not exist in the current tree), app ships as "Razr Fold Scenes" with the default/no launcher icon.
- No dark mode — Compose defaults only, does not follow the phone's system theme setting.
- PickerScreen scrolls; apply buttons sit below the fold, need a swipe.
- Status bar icons render light-on-light over the picker background.
- Spider-Verse thumbnail unreadable at 200px (pixel art scaled 1/7).
- No press feedback anywhere — flat Compose defaults on every control.
- Cover wallpaper silently failed on the slot that actually matters (fixed, see Engineering Findings).
**Importance:** This is the one thing Franklyn opens every time he wants to change his phone's look. A tool that looks unfinished gets used less, and the core bug that made it feel broken (cover wallpaper "not working") is now understood and fixed — the redo makes the rest of the experience match that bar.

## Objective & Scope

**Objective:** Make opening the app feel as good as the scenes it plays. Give it a real identity (name, FSF icon, light/dark theme synced to the OS) and a picker UI with deliberate motion and feedback, reflecting the fixed two-tap cover flow, without regressing the proven hinge/overlay/wallpaper engine.
**Ideal outcome:** Franklyn opens the app, sees the FSF mark and a theme that matches his phone's light/dark setting automatically, picks a scene through an interface with real press/selection feedback, sets both cover slots correctly in two guided taps, and reaches every control without scrolling.

### In-scope or Goals
- App identity: name **Flip's Animation** (locked), FSF mark as launcher icon — adaptive icon (`mipmap-anydpi-v26`), text-free center crop verified (corner-checked, no stray ring/text pixels at any angle — lesson from `razr-fold-android-dev` skill applied).
- `colors.xml` color system, Light + Dark, **synced to the phone's system dark-mode setting** (no manual in-app toggle) via `isSystemInDarkTheme()` / day-night resource qualifiers.
- `PickerScreen` rebuild: fits the inner display (1224×2992) without scroll at 7 scenes — validated achievable in the Figma mock (content ends at y≈1542 of 2992).
- Status bar contrast fix (theme-aware dark/light icons).
- Thumbnail legibility fix for pixel-art scenes — native pixel scale + nearest-neighbour upscale, not a scaled-down full render.
- Interaction polish: press feedback (`scale 0.97`, 160ms ease-out) on every control, spring-driven preview slider (damping 1.0 default, per apple-design skill), restyled status/result rows.
- **Cover-wallpaper two-tap flow**, UI-visible: "Set cover wallpaper" button triggers two sequential Motorola editor launches (lock, then home); status row reports both slots independently ("Cover: lock + home both set").
- Logo/icon asset pipeline: FSF source (`fsf-logo-deploy.png`, 1326×1326) → cropped text-free mark → exported to `app/src/main/res/drawable-nodpi/` + `mipmap-anydpi-v26` + `docs/store-assets/`.

### Not-in-scope or Non-Goals
- No new scenes beyond the existing 7.
- No change to `HingeSource`, `ProgressMapper`, `OverlayAlpha`, `FoldOverlayService` core fold-tracking logic — proven engine, untouched.
- No Play Store publication — private use only.
- No Rotate/multi-scene mode — dropped by Franklyn 2026-10-11, stays dropped.
- No manual theme toggle at first launch — defaults to system; toggle becomes available after first load (see Theme Specification).

## User Flow

Built as a Figma flow page (`User Flow`, in `l8UPpuaEZ9QUeEeZ7DOXyu` — page build was interrupted by a Figma Starter-plan rate limit mid-session; steps below are the authoritative spec until that page finishes):

| Step | Action | System response |
| :---- | :---- | :---- |
| 1. Open app | Tap launcher icon (FSF mark) | `PickerScreen` loads; theme resolves from system light/dark automatically |
| 2. Check status | Glance at status row | Shows fold-animation service on/off; tap routes to Accessibility settings if off |
| 3. Browse scenes | Scroll/tap through 7 rows | Thumbnails at native pixel scale (legible); tap selects + updates preview live |
| 4. Preview fold | Drag 0–180 slider, toggle Cover/Inner | Spring-driven thumb (damping 1.0), scene renders at the dragged progress on a panel-sized preview |
| 5a. Set inner wallpaper | One tap | `WallpaperManager.setBitmap`, `FLAG_SYSTEM\|FLAG_LOCK` — replaces home+lock together, no backup (told to user up front) |
| 5b. Set cover wallpaper | One tap → **two** editor launches | Motorola editor opens with `style=2` (lock) → Franklyn taps Save → editor opens again with `style=3` (home) → Franklyn taps Save. Two taps, not one — this is the verified fix, surfaced honestly in the UI rather than hidden |
| 6. Confirm result | Glance at result row | "Cover: lock + home both set \| Inner: applied" — a real two-slot check, not just "editor opened" |
| 7. Fold the phone | Physical action | Overlay draws on whichever display is lit, driven by live hinge angle; fades to the matching wallpaper frame at rest |

## Settings — App Exclusion List (restructured, 2026-10-25 — Franklyn: belongs in Settings, not a standalone top-level screen)

**Navigation structure:** `PickerScreen` gets one Settings entry (gear icon, top-right, next to the theme toggle — grouped together since both are "configure the app" actions, per apple-design's grouping-and-mapping principle: proximity implies relationship). Settings is a dedicated screen, not a sheet over the picker — it has its own back action and is reachable only by deliberate navigation, never accidentally.

```
PickerScreen
  └─ [⚙ Settings icon, top-right] → SettingsScreen
                                        ├─ Theme (System / Light / Dark) — existing toggle, relocated here
                                        └─ Apps → AppsScreen (per-app exclusion list)
```

### SettingsScreen

| Row | Content |
| :---- | :---- |
| Theme | Segmented control: System \| Light \| Dark. Selecting one is instant (no restart), matches current Theme Specification behavior — just relocated from a loose top-right icon into a proper settings row with a label, so its purpose is legible instead of an unexplained glyph. |
| Apps | Navigates to `AppsScreen`. Subtitle shows a live count: "2 apps excluded" (or "All apps allowed" at zero) so Franklyn does not have to open the screen to know the current state — apple-design's "wayfinding" principle: a screen should answer "what's there?" before being opened. |

### AppsScreen (per-app exclusion)

| Row | Content | State |
| :---- | :---- | :---- |
| Header | "Apps" title, one-line explainer: "Choose which apps never show the fold animation." | Static |
| Camera (pinned first, always visible) | System default camera app's launcher icon + label, resolved live via `PackageManager.resolveActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE))` — NOT hardcoded to one OEM package, since the design doc's device table already plans for phones other than this Razr. Confirmed live on this device: resolves to `com.motorola.camera5`. | **Suppressed by default, toggle locked off** — shown with a "Always off (camera)" label instead of a togglable switch, and a locked-padlock glyph instead of the switch control other rows use, so the visual difference (not just the disabled state) tells Franklyn at a glance this one cannot be changed. Rationale ties to the design doc's existing safety rule (overlay must never obscure a live camera viewfinder). |
| All other installed apps | Full launcher-visible app list (`PackageManager.queryIntentActivities` on `CATEGORY_LAUNCHER`, needs `QUERY_ALL_PACKAGES` in the manifest), icon + label, alphabetical | **On by default** (animation plays). A toggle switch per row flips it off; state persists (DataStore, a `Set<String>` of excluded package names). |

**Detection mechanism (already decided in the `razr-fold-android-dev` skill, not re-litigated):** camera suppression via `CameraManager.registerAvailabilityCallback` (fires when ANY process opens/closes a camera device — catches third-party camera apps too, not just the system default, so the pinned row is a UI convenience, not the only protection). Per-app exclusion reuses the existing `AccessibilityEvent` stream (`onAccessibilityEvent`, `event.packageName`, currently a no-op in `FoldOverlayService`) to detect the foreground app and gate `addOverlay`/frame drawing against the stored exclusion set — no `UsageStatsManager` "Usage access" permission needed.

### Motion & interaction spec (apple-design skill, applied concretely — not just cited)

- **Toggle switches respond on touch-down, not touch-up.** The switch thumb begins moving the instant Franklyn's finger lands, tracking 1:1 with the drag if he slides instead of taps — direct manipulation, not a post-hoc animation of a boolean flip (apple-design §1–2).
- **Toggle animation is a spring, not a fixed-duration tween:** damping 1.0 (critically damped, no bounce — flipping a setting is a definite action, not a playful flick) at response ≈0.3s, matching apple-design's "Move/reposition" row in its Quick Reference table.
- **Interruptible:** if Franklyn taps a row twice fast (toggle on, immediately off), the second tap must animate from the switch's live on-screen position, not restart from the logical end state — apple-design §3, "never animate from the target value."
- **Settings → Apps navigation is a push, not a fade.** It slides in from the trailing edge and slides out the same way on back (spatial consistency, apple-design §7) — matching whatever direction the OS's standard back-navigation uses, not a custom direction that fights system expectation.
- **The Settings gear icon anchors its own screen's transition** — if a future build adds a popover/menu instead of a full push, it must scale in from the gear icon's position (`transform-origin`-equivalent in Compose: shared-element or predictive-back anchor), never from screen center, per apple-design §3/§7's popover-not-modal rule.
- **Row press feedback:** `scale(0.97)` on press for the Camera row's locked state too (even though it cannot be toggled, tapping it should still feel acknowledged — apple-design's "feedback must be continuous during interaction," not reserved only for actionable controls) — but no scale on the switch thumb itself, since the switch already has its own motion language.
- **Typography:** row titles at body size with `0` tracking (not display-scale, so no negative tracking needed); the live-count subtitle on the Settings row uses a slightly smaller, lower-contrast weight — hierarchy from weight+size+color together, not size alone (apple-design §15–16).
- **Reduced motion:** if `prefers-reduced-motion`-equivalent (Android's `Settings.Global.ANIMATOR_DURATION_SCALE == 0` or system "Remove animations" accessibility setting) is on, the toggle switch cross-fades its on/off state instead of sliding, and the Settings↔Apps navigation becomes a plain cut, per apple-design §14.

## Theme Specification — Light / Dark, System-Synced, FSF-Derived Palette

**Revision (2026-10-25, design review comment):** Franklyn reviewed the orange/purple palette on the dashboard and asked for colors matched to the FSF logo instead ("make it similar to the logos colour"). Tokens below are sampled directly from the real badge art (`fsf-logo-deploy.png`, pixel-counted dominant colors, not estimated): flame red `#D82424`, gold `#FCCC18`, steel-blue accent `#78B4CC`, background black `#101010` (already used for `ic_launcher_background`).

Color tokens (Figma paint styles pending — Figma MCP rate-limited this session, see Risks; values below are the authoritative spec regardless):

| Token | Light | Dark | Usage |
| :---- | :---- | :---- | :---- |
| Background | `#FAFAF7` | `#101010` | Screen background (dark matches FSF badge bg exactly) |
| Surface | `#FFFFFF` | `#1C1C1C` | Cards, rows, buttons base |
| OnBackground | `#1A1A1A` | `#EDEDED` | Primary text on background |
| OnSurface | `#2A2A2A` | `#DADADA` | Primary text on surface |
| Primary | `#D82424` | `#E43024` | Selected row, primary buttons — FSF flame red |
| OnPrimary | `#FFFFFF` | `#FFFFFF` | Text/icons on primary |
| Secondary | `#FCCC18` | `#FCD818` | Accents, "Set both" button — FSF gold |
| OnSecondary | `#1A1200` | `#1A1200` | Text on secondary (dark text on gold for contrast) |
| Accent (new) | `#78B4CC` | `#8FC4DA` | Scene-preview surface, slider track highlight — FSF cyborg-eye steel blue, keeps the red/gold from feeling one-note |
| Border | `#E2DFD8` | `#2E2E2E` | Dividers, slider track base |
| Success | `#2E7D32` | `#66BB6A` | Apply-result confirmation text |
| Error | `#9C1818` | `#EF5350` | Apply-result failure/pending text — dark flame-red variant in light mode |

**Sync mechanism:** On first launch, theme defaults to the phone's system light/dark setting via Compose `isSystemInDarkTheme()`. After that, Franklyn can override it via the Theme row in **Settings** (System / Light / Dark segmented control — relocated here per this revision, not a loose top-right icon) — the override is a user choice, persisted (DataStore, `theme_override` key: `SYSTEM` / `LIGHT` / `DARK`), and takes effect immediately with no restart. Android resource-qualifier fallback (`values-night/colors.xml`) covers any non-Compose surface (notification icons, launcher adaptive-icon background) and follows the same resolved mode. Revision history: v2 originally spec'd system-sync-only with no toggle; a design-review comment asked for a toggle (declined, then clarified to default-on-first-load-with-override — accepted); this revision further moves that toggle from a standalone icon into the new Settings screen, alongside the Apps exclusion list, for the same reason (grouping configuration controls together, apple-design §16 grouping-and-mapping).

Both PickerScreen states are built in Figma: frame `PickerScreen - Light mode` and `PickerScreen - Dark mode`, side by side at 1224×2992 each, same layout, swapped token set — visually verifiable before any code is written.

## Build Sequence (derived from the existing implementation plan's task structure)

This redo follows the SAME task-and-review discipline already used to build the engine (`docs/superpowers/plans/2026-10-11-razr-fold-scenes.md`, Tasks 1–12 completed) — new tasks continue that numbering, not a parallel process.

| Task | Scope | Depends on | Files |
| :---- | :---- | :---- | :---- |
| **13. Identity assets** ✅ DONE | FSF mark adaptive icon, "Flip's Animation" name | None | `res/drawable-nodpi/ic_launcher_foreground.png`, `res/mipmap-anydpi-v26/*`, `res/values/strings.xml`, `AndroidManifest.xml`, `colors.xml` — commit `40441b4`, verified: `aapt2 dump badging` on the built APK confirms `application-label='Flip's Animation'` + adaptive icon XML present |
| **14. Theme system** | `colors.xml` + `values-night/colors.xml`, Compose `MaterialTheme` wiring, `isSystemInDarkTheme()` | Task 13 (icon background color depends on token set) | `res/values/colors.xml`, `res/values-night/colors.xml`, new `ui/Theme.kt` |
| **15. Cover two-tap flow** | `WallpaperApplier.applyCover` takes a `style` param; "Set cover wallpaper" sequences two editor launches; `WallpaperTest` asserts intent carries `style` | None (independent of theme work) | `wallpaper/WallpaperApplier.kt`, `androidTest/.../WallpaperTest.kt` |
| **15b. Overlay edge-to-edge fix** (new, 2026-10-25 — Franklyn: "status bar isn't synced with animation... it isn't full screen") | `FoldOverlayService.addOverlay`'s `WindowManager.LayoutParams` is missing `FLAG_LAYOUT_NO_LIMITS` and a `layoutInDisplayCutoutMode`; the overlay respects system-bar insets so the scene visibly stops short of the status bar instead of drawing full-screen behind it. Add the missing flag + `layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`, keep clock/battery icons visible (overlay still never takes touch, never hides system UI — just extends the scene drawing under it). Verify on-device: screenshot during a fold shows the scene reaching the very top edge on both displays. | Task 15 (sequenced, Option A — one adb/phone session at a time, confirmed 2026-10-25) | `overlay/FoldOverlayService.kt` |
| **15c. Hinge trigger threshold** (new, 2026-10-25 — Franklyn: animation should only trigger once the phone begins actually folding/unfolding, not on any minor hinge jitter) | `OverlayAlpha.overlayAlpha` currently responds to ANY angle change via a pure smoothstep from the panel's rest angle — a 1-2° sensor jitter while the phone sits still produces a nonzero alpha (tiny but nonzero) and, more importantly, `FoldOverlayService.onFrame`'s "hinge quiet" check restarts the frame loop on ANY `lastEventNanos` change, so a jittering sensor at rest keeps the overlay loop spinning instead of settling. Add a minimum-movement threshold (e.g. angle must move more than ~2-3° from its last RESTING value, not just any event, before `overlayAlpha` leaves 0) so the scene stays fully off at rest and only engages once a real fold/unfold starts. Needs on-device logging of real idle-sensor jitter magnitude first (design doc doesn't have this number) before picking the exact threshold. | Task 15b (sequenced) | `fold/OverlayAlpha.kt`, `overlay/FoldOverlayService.kt` (the hinge-quiet check) |
| **15d. Settings → Apps exclusion** (Franklyn: belongs in Settings, not a standalone screen; animation should never play over the camera or user-picked apps) | New `SettingsScreen` (gear icon from `PickerScreen`) holding the relocated Theme toggle + an "Apps" row that opens `AppsScreen`. Per the ALREADY-DECIDED detection design (`razr-fold-android-dev` skill, "Play Store accessibility-mechanism decision" section, tested 2026-10-24): camera suppression via `CameraManager.registerAvailabilityCallback` (fires on any process opening/closing a camera device, no extra permission) — NOT built yet, `FoldOverlayService` has no camera callback registered today. Per-app exclusion list reuses the existing `AccessibilityEvent` stream's `typeWindowStateChanged`/`event.packageName` for foreground-app detection (already flows through `onAccessibilityEvent`, currently a no-op `= Unit`) plus a stored exclusion set (DataStore) and the Apps list UI (needs `QUERY_ALL_PACKAGES` in the manifest, not `UsageStatsManager`). Full motion/interaction spec in the "Settings — App Exclusion List" section above (apple-design skill applied). Status: **design approved, zero code written** — new build scope, not a bug fix. | Task 15c (sequenced, Option A) | `overlay/FoldOverlayService.kt` (camera callback + `onAccessibilityEvent` body + foreground-app gate before `addOverlay`/frame draw), new `data/ExclusionRepository.kt`, new `ui/SettingsScreen.kt`, new `ui/AppsScreen.kt`, `AndroidManifest.xml` (`QUERY_ALL_PACKAGES`) |
| **16. PickerScreen rebuild** | No-scroll layout, press feedback, spring slider, thumbnail legibility fix, status/result row restyle | Tasks 14 + 15 (needs theme tokens and the real cover-flow contract) | `ui/PickerScreen.kt`, `ui/PreviewScreen.kt`, `ui/ServiceStatus.kt` |
| **17. Acceptance** | Franklyn reviews light + dark on-device, confirms cover two-tap flow end to end, confirms no-scroll on real hardware | Task 16 | Manual, on-device |

**Review Focus for this phase (same format as the original plan's Review Focus section):**
1. Theme switch (toggling phone system dark mode) must not require an app restart to take effect in Compose.
2. The cover two-tap flow must never silently skip the second tap — if Franklyn backs out of the first editor screen, the flow should not proceed to claim "both set."
3. No-scroll claim must be verified on real hardware, not just the Figma mock — Compose layout can differ from a static frame.
4. The FSF icon must render cleanly at the smallest adaptive-icon safe zone (Android's 66dp circular mask) without clipping the lock/flame details — check before calling Task 13 done.

## Product Requirements

### Critical User Journeys (CUJs)
1. **Open app, recognize it instantly**: FSF icon on the home screen, "Flip's Animation" as the label, theme matches whatever light/dark mode the phone is already in.
2. **Pick a scene without scrolling**: every row, the preview, and all apply buttons reachable with zero swipes on the inner display.
3. **Feel the picker respond**: tapping a row, dragging the slider, pressing a button each give immediate visual feedback.
4. **Read every thumbnail**: Spider-Verse and Rick-and-Morty legible as what they are, not a colour smear.
5. **Trust the cover wallpaper actually applied**: two guided Save taps, a status row that confirms both slots independently — no more silent partial failure.
6. **Control where the scene plays**: open **Settings** from the picker, tap "Apps," see every installed app with a toggle, turn the fold animation off for specific apps (e.g. Netflix mid-playback, a banking app). The system default Camera app (`com.motorola.camera5`, confirmed live via `cmd package resolve-activity -a android.media.action.IMAGE_CAPTURE`) is suppressed by default and shown as locked in the list — not user-togglable off that default, since showing a scene over the viewfinder is the one case the design doc already treats as unsafe.

### Functional Requirements

| Priority | Requirement | User Story |
| :---- | :---- | :---- |
| P0 | FSF mark as adaptive launcher icon, app renamed "Flip's Animation" | As Franklyn, I want my own icon and name on the home screen. |
| P0 | Light/Dark `colors.xml` system, defaults to system on first load, toggle relocated into Settings | As Franklyn, I want the app to match my phone at first, with a quick way to flip it myself after, from one place. |
| P0 | Cover wallpaper sets BOTH slots (lock + home) via the verified two-tap flow | As Franklyn, I want the cover to actually show what I picked when the phone is closed and locked. |
| P0 | PickerScreen fits the inner display without scrolling | As Franklyn, I want every button reachable without a swipe. |
| P1 | Status bar icon contrast fixed, theme-aware | As Franklyn, I want to read the clock and battery over the picker. |
| P1 | Thumbnail legibility fix for pixel-art scenes | As Franklyn, I want to tell Spider-Verse from Rick and Morty at a glance. |
| P1 | Press feedback on every control | As Franklyn, I want the app to feel like it heard my tap. |
| P2 | Spring-driven preview slider | As Franklyn, I want dragging the fold preview to feel physical. |
| P2 | Apply-result row reports lock/home slot status independently | As Franklyn, I want to know exactly which part of the cover wallpaper is set. |
| P1 | Settings screen (gear icon): houses relocated Theme toggle + "Apps" row with live exclusion count | As Franklyn, I want one place to configure the app instead of scattered icons. |
| P1 | Apps screen: per-app toggle to enable/disable the fold animation, system default Camera app shown locked/pre-suppressed and not user-overridable | As Franklyn, I want to stop the scene from playing over specific apps, and never want it over my camera. |

## Risks & Mitigations

- **Risk**: FSF mark is from an unrelated project (Jellyfin media server) and carries no connection to fold-scenes/phones — brand mismatch for anyone but Franklyn. **Mitigation**: none needed — explicit Franklyn decision, private single-user app, no external audience.
- **Risk**: Two-tap cover flow could be interrupted (Franklyn backs out of the first editor screen) leaving state ambiguous. **Mitigation**: Task 15's `WallpaperApplier` must track each slot's result independently and the status row must reflect partial completion honestly (e.g., "Cover: lock set, home pending") rather than assuming success.
- **Risk**: New press/spring motion on `SceneView`'s shared `RuntimeShader`/`Paint` reintroduces the main-thread invariant violation flagged in Task 6a. **Mitigation**: motion changes stay in the Compose UI layer, never in `SceneView.onDraw`.
- **Risk**: Figma Starter plan rate-limits block finishing the User Flow Figma page and any further Figma-side iteration this session. **Mitigation**: User Flow is captured in this document's table (authoritative); Figma page finishes next session or on a higher plan tier.
- **Risk**: Figma Variables are capped at 1 mode on Starter (confirmed earlier this session) — used paint styles instead for Light/Dark, which work but do not auto-switch inside Figma the way a 2-mode variable collection would. **Mitigation**: non-blocking — Android code implements the real sync; Figma styles are references, not the live mechanism.

## Tradeoff

- **Full rewrite vs. targeted redo (chosen)**: targeted redo (identity + theme + PickerScreen layer, engine untouched) ships faster and protects the proven hinge/overlay math.
- **Reused FSF mark vs. original logo (chosen: reused)**: an original mark would read as more purpose-built, but Franklyn explicitly chose reuse — decision made, not revisited here.
- **Two-tap cover flow vs. a single combined intent (chosen: two-tap)**: Motorola's picker has no single intent that writes both slots; a single-tap illusion would require simulating a second tap programmatically, which the design doc explicitly forbids ("no silent cover wallpaper change" — the service never presses Save). Two real taps, honestly labeled in the UI, is the only option consistent with that rule.

## Business Benefits/Impact/Metrics

Private single-user app — no revenue/growth metrics apply. Success is qualitative: Franklyn's own verdict on open-and-use, plus the concrete fix verification already obtained (cover wallpaper confirmed working 2026-10-25, first time ever).

| Metric | Before | After |
| :---- | :---- | :---- |
| Launcher icon | Android default / none | FSF mark, adaptive icon |
| Theme | Light-only, hardcoded | Light + Dark, system-synced |
| Cover wallpaper | Silently wrote wrong slot, never worked | Both slots set, verified working |
| Picker reachability | 3 buttons below the fold | 0 swipes needed |
| Thumbnail legibility | Spider-Verse unreadable | Recognizable at row scale |

## Stakeholders / Owners

| Name | Team/Org | Role | Note |
| :---- | :---- | :---- | :---- |
| Franklyn | Product/User | Decision-maker | Locked name, logo, theme-sync behavior this revision |
| Pickle Rick | Engineering | Implementer | Root-caused + fixed cover bug (verified); owns Tasks 13–17 build-out |
| Kratos | PM | Author/PRD, sequencing, verification | This document; Figma asset pipeline |
