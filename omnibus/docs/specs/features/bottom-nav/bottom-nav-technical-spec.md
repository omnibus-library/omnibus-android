# Bottom Navigation: Technical Spec

| | |
|---|---|
| Status | Draft |
| Product spec | [`bottom-nav-product-spec.md`](bottom-nav-product-spec.md) (`P-*` IDs) |
| UI reference | [`bottom-nav-mockup.html`](bottom-nav-mockup.html): the reference implementation of the layout rule and the motion |
| Related issues | #124, #162, #16 |
| Android today | `ui/components/OmnibusTabBar.kt`, `feature/shell/ShellTab.kt`, `feature/shell/ShellNavigator.kt`, `feature/shell/ui/SignedInShellScreen.kt`, `ui/components/Masthead.kt` |

## About this spec

This spec covers how the bottom navigation in the product spec is built. It has two parts:

- **General** (§1–§7) holds for every client: tokens, the layout algorithm, the motion, the state model and the architecture.
- **Platform** sections (§8 Android, §9 iOS, §10 web) hold only what is specific to one client.

**IDs.** `I-<AREA>-n` is general, `AND-<AREA>-n` Android, `IOS-<AREA>-n` iOS and `WEB-<AREA>-n` web. Never renumber one. A dropped requirement is marked *Withdrawn* and left in place.

**Wording.** "Must" is required. "Should" is the default, and departing from it needs a reason in the PR.

All sizes are in dp (Android), pt (iOS) or CSS px (web), which are treated as the same unit here.

---

# Part 1: General

## 1. Tokens

Every client defines these constants under the same names. No client hard-codes the values at the call site.

### 1.1 Layout

| Token | Value | Notes |
|---|---|---|
| `navSideMargin` | 12 | `Spacing.md`. Left and right margin for the bar and the FAB. |
| `navBottomMargin` | 8 | `Spacing.sm`. Above the system navigation inset. |
| `navBarHeight` | 64 | Minimum; grows with font scale (I-LAY-6). |
| `navBarMaxWidth` | 360 | |
| `navBarHorizontalPadding` | 6 | Inside the bar, before the first and after the last tab. |
| `navCornerRadius` | 16 | Shared by the bar and the FAB (P-BAR-5). New token: Atrium's radius scale has no 16. |
| `fabSize` | 56 | |
| `fabGap` | 12 | Between the FAB and the bar (or the mini player bar) in both layouts. |
| `miniPlayerGap` | 8 | `Spacing.sm`. Between the nav bar and the mini player bar above it. |
| `contentBreathingRoom` | 16 | Extra space below the last row of content (P-CON-2). |
| `breakpoint` | derived | `2 × navSideMargin + navBarMaxWidth + fabGap + fabSize` = 452. Computed, never a literal. |

### 1.2 Tab item

| Token | Value |
|---|---|
| Icon | 24, on a 24 grid, 1.8 stroke, round caps and joins |
| Label | UI face 11, Medium; SemiBold when selected |
| Icon to label | 4 |
| Item vertical padding | 4 |
| Indicator pill | 56 × 30, corner 15, 1 from the top of the item, behind the icon |
| Colours | Unselected `ink3`; selected `accent`; pill `accent` at 16% mixed into `bg2` (OKLab) |

### 1.3 Surfaces

| Element | Fill | Border | Shadow, light | Shadow, dark |
|---|---|---|---|---|
| Bar | `bg2` | 0.5 `line` | `0 4 10 #0000002E`, `0 14 34 #00000047` | `0 4 12 #00000080`, `0 16 40 #000000B3` |
| FAB | `accent` | none | `0 3 6 #0000002E`, `0 8 20 #00000038` | `0 3 8 #00000080`, `0 10 24 #000000A6` |

Shadows are two layers, written `x y blur colour`. A platform that can't draw two layers should keep the larger one.

- I-SURF-1. Colours come from the Atrium palette only. No client uses dynamic or wallpaper colour for any part of the chrome (P-BAR-4).

### 1.4 Motion

| Token | Value |
|---|---|
| `emphasized` | `cubic-bezier(0.2, 0, 0, 1)` (Material 3 emphasized). New: Atrium has no easing tokens yet. |
| `barResizeDuration` | 400 ms |
| `fabMoveDuration` | 350 ms |
| `toWideFabDelay` | 160 ms |
| `toNarrowBarDelay` | 120 ms |
| `fabDipScale` | 0.9, at 45% of the dip |
| `indicatorDuration` | 200 ms |

## 2. Icons

- I-ICON-1. **Tabs.** Library, Stats and You each have an outline and a filled drawable; they already exist (`ic_tab_*`, `ic_tab_*_filled`). Search has none yet.
- I-ICON-2. **Search.** Outline path `M16.6,10.5a6.1,6.1 0 1 1 -12.2,0a6.1,6.1 0 1 1 12.2,0zM15,15l4.6,4.6` at stroke 1.8. The selected icon is the same path at stroke 2.6, as its own drawable. Where iOS or web already ship a search glyph, that glyph wins and every client uses it.
- I-ICON-3. **Add book.** A book outline with a filled "+" badge at bottom-right, on the 24 grid. It is one tint: the "+" is cut out of the badge (even-odd), so it shows the FAB's fill through it.
  - Book (stroke 1.8, round caps and joins, no fill): `M12.5,19.1H7a2.1,2.1 0 0 1 -2.1,-2.1V5.5a2.1,2.1 0 0 1 2.1,-2.1h8a2.1,2.1 0 0 1 2.1,2.1v6M8.5,3.4v15.7`
  - Badge with the "+" cut out (fill, even-odd): `M22.5,17.5a5,5 0 1 1 -10,0a5,5 0 1 1 10,0zM16.6,15.1h1.8v1.5h1.5v1.8h-1.5v1.5h-1.8v-1.5h-1.5v-1.8h1.5z`
  - The book's outline stops short of the badge on the right (`v6`) and bottom (`H12.5`), so the badge has clear space around it.
- I-ICON-4. Icons carry no content description. The tab label or the FAB's own label names them.

## 3. Layout algorithm

The layout is a pure function from the available width to rectangles. Every client implements it, and unit tests pin its outputs (§7).

### 3.1 Inputs and outputs

```
input  ChromeLayoutInput
  availableWidth    window width minus the horizontal safe-area insets
                    (side system bars, display cutouts)
  bottomInset       system navigation inset (gesture area or 3-button bar)
  barHeight         measured, >= navBarHeight
  miniPlayerHeight  0 when no book is loaded
  fabVisible        P-FAB-3

output ChromeLayout
  mode              Narrow | Wide
  barWidth
  barBottom         distance from the window's bottom edge
  fabEnd            distance from the safe area's end edge
  fabBottom
  contentBottomPadding
```

All horizontal values are start/end, never left/right, so right-to-left mirrors without a second code path (P-LAY-6).

### 3.2 Rules

- I-LAY-1. `mode = Wide` when `availableWidth >= breakpoint`, else `Narrow`. Ties are wide (P-LAY-3).
- I-LAY-2. `fabVisible` never affects `mode` or `barWidth` (P-LAY-5).
- I-LAY-3. `barWidth = navBarMaxWidth` when wide, else `availableWidth - 2 × navSideMargin`. The bar is always at `navSideMargin` from the start edge.
- I-LAY-4. `barBottom = bottomInset + navBottomMargin`. The mini player bar, when present, sits `miniPlayerGap` above the nav bar (§3.3).
- I-LAY-5. **FAB.**
  - Narrow: `fabEnd = navSideMargin` (its end edge lines up with the bar's) and `fabBottom = barBottom + blockHeight + fabGap`.
  - Wide: `fabEnd = navSideMargin` and `fabBottom = barBottom + (barHeight - fabSize) / 2`, centred on the nav bar, whatever the mini player bar does.
  - The FAB's end position is the same in both layouts, so the switch only moves it vertically (I-MOT-3).
- I-LAY-6. `barHeight` is measured, not assumed. At large font scale it grows to fit the labels (P-A11Y-3). The breakpoint never depends on it.
- I-LAY-7. `contentBottomPadding = barBottom + blockHeight + contentBreathingRoom`, plus `fabSize + fabGap` when narrow and `fabVisible`. Screens apply it as content padding on their scroll container, so content draws behind the chrome but can scroll clear of it (P-CON-1, P-CON-2).

`blockHeight` (the nav bar plus the mini player bar above it) is `barHeight`, plus `miniPlayerGap + miniPlayerHeight` when the mini player bar shows.

### 3.3 Mini player

- I-MINI-1. While a book is loaded, the mini player is its own floating bar `miniPlayerGap` above the nav bar, with the nav bar's width and start edge, and its own surface: `bg2`, 0.5 `line` border, `navCornerRadius` corners and the bar's shadow (§1.3). It animates width with `barProgress`, so it stays in step with the nav bar across the breakpoint. The audiobook spec's P-MINI-1 placement ("above the bottom navigation") still holds. Its contents and its enter and exit motion are product Q3 and a separate ticket.
- I-MINI-3. **Interim.** Until that ticket, clients keep today's mini player composable or view, unchanged inside, and place it in the I-MINI-1 slot (width, start edge and gap). The floating surface treatment ships with the mini player's own ticket.
- I-MINI-2. The mini player's visibility rule doesn't change: it is hidden on Book Detail and the player (audiobook spec P-MINI-1).

### 3.4 Reference

```
wide = availableWidth >= 2*side + max + gap + fab
barWidth = wide ? max : availableWidth - 2*side
fabBottom = wide ? barBottom + (barHeight - fab)/2
                 : barBottom + blockHeight + gap
```

## 4. Motion

### 4.1 Breakpoint switch

The chrome animates only when `mode` changes (P-MOT-6). Within a mode, every value tracks the input directly.

- I-MOT-1. **Progress, not targets.** Each client drives two progress values from 0 (narrow) to 1 (wide): `barProgress` and `fabProgress`. Rendered values are interpolated between the narrow and wide results of the *current* input: `barWidth = lerp(narrow.barWidth, wide.barWidth, barProgress)`. A window resize mid-animation then moves both ends, with no lag and no restart.
- I-MOT-2. **Timing.**

  | Direction | `barProgress` | `fabProgress` |
  |---|---|---|
  | Narrow → wide | 400 ms, delay 0 | 350 ms, delay 160 |
  | Wide → narrow | 400 ms, delay 120 | 350 ms, delay 0 |

  Both use `emphasized`. The delays carry the ordering in P-MOT-2 and P-MOT-3: going wide, the bar's end clears the FAB's column before the FAB starts to drop.
- I-MOT-3. The FAB animates its bottom offset; its end offset doesn't change (I-LAY-5). When the FAB is hidden, `fabProgress` still runs, so it is in the right place if it shows mid-switch.
- I-MOT-4. **Dip.** On every mode change the FAB's scale runs keyframes 1 → 0.9 (at 45%) → 1, `emphasized`, lasting the FAB's delay plus its move (510 ms going wide, 430 ms going narrow). Scale is around the centre and doesn't affect layout.
- I-MOT-5. **Interruption.** A mode change during an animation retargets from the current progress, with the new direction's durations and no delay. It never snaps back to 0 or 1.
- I-MOT-6. **Reduced motion.** With the system's reduce-motion setting on, progress jumps straight to its target and the dip is skipped (P-MOT-7).

### 4.2 Other motion

| ID | What | Spec |
|---|---|---|
| I-MOT-7 | Tab selection | The new tab's pill grows from 0 to 56 wide around its centre and fades in, over `indicatorDuration` with `emphasized`. The old pill fades out at the same time. The glyph swap from outline to filled cuts, with no crossfade. |
| I-MOT-8 | FAB in and out (tab switch, P-FAB-4) | In: scale 0.6 → 1 and alpha 0 → 1 on Atrium `settle`. Out: scale 1 → 0.6 and alpha 1 → 0 on `snap`. Hit testing is off from the moment the exit starts. |
| I-MOT-9 | Chrome hide and show (P-VIS-2) | The bars and the FAB slide down by `barBottom + blockHeight` and fade, on `snap` going out and `settle` coming back. The show starts when the back gesture or pop is committed, not when the transition ends. |
| I-MOT-10 | Tab content | Crossfade between tabs, as today: a slide would read as a push or pop. |
| I-MOT-11 | Theme change | No animation. |

## 5. State and navigation model

### 5.1 Tabs

```
enum Tab(id, labelKey, icon, selectedIcon, root)
  Library  "library"  start tab
  Search   "search"
  Stats    "stats"
  You      "you"
```

- I-NAV-1. The order of the enum is the order of the bar. `Library` is the start tab.
- I-NAV-2. `Authors` and `Series` are removed as tabs. Their routes move under Search (#124 AC1, its own ticket). Until Search's Browse page exists, Search's root is a placeholder like the other unbuilt tabs.

### 5.2 Navigator

The existing `ShellNavigator` semantics carry over unchanged.

- I-NAV-3. One back stack per tab, each starting at the tab's root. All stacks stay alive while another tab is selected (P-TAB-5).
- I-NAV-4. `select(tab)`: if `tab` is already selected, pop it to its root (P-TAB-6); otherwise make it selected.
- I-NAV-5. `back()`: pop the selected stack if it has more than its root; else, if not on the start tab, select the start tab; else report that the shell should exit (P-TAB-7).
- I-NAV-6. The stacks and the selected tab are saved state: they survive rotation, fold and unfold, and process death (P-TAB-8). `ChromeLayout` is never saved; it is recomputed from the current window.

### 5.3 Chrome state

Derived, never stored:

```
ChromeState
  visible     = selected tab's stack contains no BookDetail entry      (P-VIS-1)
  fabVisible  = visible && selected == Library && stack.size == 1       (P-FAB-3)
  miniPlayer  = the audiobook mini player's own visibility rule         (I-MINI-2)
```

- I-STATE-1. `visible` checks the whole selected stack, not only its top, to match iOS: a screen pushed above Book Detail keeps the chrome hidden.
- I-STATE-2. A FAB is a tab root's property, not a shell constant: the shell asks the selected root for an optional `FabSpec(icon, label, onClick)`. Only Library supplies one today. This keeps the layout rule generic if another tab gains an action.

## 6. Architecture

```
Shell
├── NavHost / NavDisplay       full-window; draws behind the chrome
├── ChromeOverlay              aligned to the bottom; draws above content
│   ├── MiniPlayerBar          optional; I-MINI-1
│   ├── NavBar                 the tabs
│   └── Fab                    optional, from the selected root's FabSpec
└── provides contentBottomPadding to every entry
```

- I-ARCH-1. The shell is a box with the chrome overlaid, not a column. Content runs the full window height (#162).
- I-ARCH-2. The shell measures the bar (I-LAY-6), computes `ChromeLayout` and provides `contentBottomPadding` to entries through a scoped value: a `CompositionLocal` on Android, an environment value on iOS, a CSS custom property on web. Entries apply it to their scroll container. Entries must not compute it.
- I-ARCH-3. The layout function (§3), the progress interpolation (I-MOT-1) and the timing tables (I-MOT-2) live in plain code with no UI framework types, so they can be unit tested.
- I-ARCH-4. Only the overlay's children take touches. The space around the bar and FAB passes touches through to content (P-CON-4).
- I-ARCH-5. Haptics: a tab change fires the selection tick; a reselect fires nothing; the FAB fires the light key tick before calling `onClick`.
- I-ARCH-6. A FAB tap is ignored while a previous tap's sheet is opening or open (product §11, rapid taps).

## 7. Testing

- I-TEST-1. **Layout function.** Table tests: 320, 412, 451, 452 and 841 wide; with and without the FAB; with and without a mini player; a large measured bar height; RTL. Assert `mode`, `barWidth`, `fabEnd`, `fabBottom` and `contentBottomPadding`.
- I-TEST-2. **Motion.** Unit test the timing table per direction, the lerp at progress 0, 0.5 and 1, retargeting from mid-progress (I-MOT-5), and that reduced motion produces zero-length animations. No client tests animation frames on a device.
- I-TEST-3. **Navigator.** Select, reselect and back across all four tabs (I-NAV-3 to I-NAV-5), and the derived `ChromeState` with Book Detail at the top and below the top of the stack.

---

# Part 2: Platforms

## 8. Android

### 8.1 Changes to existing code

| File | Change |
|---|---|
| `feature/shell/ShellTab.kt` | Tabs become `Library, Search, Stats, You` (I-NAV-1). Add `SearchRoot`; remove `AuthorsRoot` and `SeriesRoot` as tab roots. |
| `feature/shell/ShellNavigator.kt` | No behaviour change. Add `chromeState` (§5.3) as a derived property. |
| `feature/shell/ui/SignedInShellScreen.kt` | `Column` becomes `Box` with `ChromeOverlay` aligned bottom (I-ARCH-1). Drop `consumeWindowInsets(WindowInsets.navigationBars)` on `NavDisplay`. The existing `MiniPlayer` moves into the overlay's mini player slot, unchanged inside (I-MINI-3). |
| `ui/components/OmnibusTabBar.kt` | Becomes the floating bar: no full-width background or hairline, rounded `bg2` surface and shadow (§1.3), pill indicator (I-MOT-7). It no longer pads `navigationBars`; the overlay positions it. |
| `ui/components/Masthead.kt`, `feature/library/ui/LibraryScreen.kt` | Remove `MastheadAddButton` from the Library masthead (P-FAB-1). `onAddBooks` moves to the Library root's `FabSpec`. |
| `ui/theme/Space.kt` | Add `Radius.chrome = 16.dp` (`navCornerRadius`). |
| `ui/theme/Motion.kt` | Add `Motion.emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)` and the duration constants in §1.4. |

### 8.2 New code

```
feature/shell/
  chrome/
    ChromeLayout.kt         pure layout function, input and output types (§3)   unit tested
    ChromeMotion.kt         timing table, lerp helpers (I-MOT-1, I-MOT-2)       unit tested
    ChromeState.kt          derived visibility (§5.3)
  ui/
    ChromeOverlay.kt        measures, lays out and animates the bars and FAB
    ShellFab.kt             the 56 dp FAB
    LocalShellContentPadding.kt
res/drawable/
  ic_tab_search.xml, ic_tab_search_selected.xml, ic_add_book.xml
res/values/strings.xml
  shell_tab_search = "Search"; remove shell_tab_authors and shell_tab_series once nothing uses them.
  The FAB reuses masthead_add_books.
```

### 8.3 Implementation notes

- AND-LAY-1. Read `availableWidth` from the shell's own constraints (`BoxWithConstraints`, or a `Layout` measuring the overlay), minus horizontal `WindowInsets.safeDrawing`. Don't use the screen size or the window size class: split-screen and freeform windows must follow the app's width (product §11).
- AND-LAY-2. `bottomInset` is `WindowInsets.navigationBars` bottom. The keyboard (`ime`) is not included, so it covers the chrome (P-VIS-5).
- AND-LAY-3. Provide padding as `LocalShellContentPadding: PaddingValues`, read by each entry's `LazyVerticalGrid` / `LazyColumn` `contentPadding`. This replaces `StatusBarPadded` for the bottom edge (#162).
- AND-MOT-1. Drive `barProgress` and `fabProgress` with an `updateTransition(mode)` and `transition.animateFloat { spec }`, where `spec` picks the row of I-MOT-2 from `initialState isTransitioningTo targetState`. Interpolate in the layout phase (`Modifier.layout` or `offset { }`), so frames don't recompose.
- AND-MOT-2. The dip is `transition.animateFloat` with `keyframes { 0.9f at (duration × 0.45) }` and applied with `graphicsLayer { scaleX; scaleY }`.
- AND-MOT-3. Reduced motion uses the existing `rememberReducedMotion()` in `ui/theme/Motion.kt`: when true, use `snap()` specs.
- AND-MOT-4. FAB enter and exit use `AnimatedVisibility` with `scaleIn(initialScale = 0.6f) + fadeIn` on `Motion.settle()` and `scaleOut(targetScale = 0.6f) + fadeOut` on `Motion.snap()`.
- AND-SURF-1. Draw the two shadow layers with two chained `Modifier.dropShadow` calls (Compose UI 1.9+; the catalog's BOM `2026.02.01` is newer), using the §1.3 values. Don't use `Modifier.shadow`: elevation shadows can't take these offsets and colours.
- AND-A11Y-1. Each tab keeps `Modifier.selectable(role = Role.Tab)`. The bar's row gets `Modifier.selectableGroup()`. The FAB is `Role.Button` with `contentDescription = masthead_add_books`. Every target is at least 48 dp (`minimumInteractiveComponentSize`).
- AND-HAP-1. Tab change: `HapticFeedbackType.SegmentTick` (as today). FAB: `HapticFeedbackType.VirtualKey` (as the masthead button today).
- AND-TEST-1. Unit tests (Kotest `DescribeSpec`) cover `ChromeLayout`, `ChromeMotion`, `ChromeState` and `ShellNavigator` (§7).
- AND-TEST-2. Instrumented tests follow `docs/engineering/testing.md`: no pinned screen size, and only relative assertions. CI's 320 dp AVD is always narrow, so the wide layout is covered by AND-TEST-1 and AND-TEST-3, not on device. On device, assert in the narrow layout that the bar's start and end margins are equal, the FAB's end edge lines up with the bar's, the FAB is above the bar, and the last grid row can be scrolled above the FAB.
- AND-TEST-3. Screenshot tests (`app/src/screenshotTest/`, `@PreviewTest` with `@PreviewLightDark`) cover how the chrome looks: the narrow layout at 360 wide and the wide layout at 841 wide, each with and without the FAB, the selected state of each tab, and the add book icon. Previews may fix their width (`@Preview(widthDp = ...)`), since they render on the JVM, not on a device.

## 9. iOS

Guidance for when iOS adopts this design (product Q2). The general part is authoritative; this section only maps it.

- IOS-ARCH-1. In `App/MainTabView.swift`, replace the system or flush bar with an overlay in a `ZStack(alignment: .bottom)`. Read `availableWidth` from a `GeometryReader` minus `safeAreaInsets` leading and trailing.
- IOS-MOT-1. Store `barProgress` and `fabProgress` as `@State Double`. Animate them with `withAnimation(.timingCurve(0.2, 0, 0, 1, duration: 0.4).delay(...))`, using the I-MOT-2 values. Reduced motion is `@Environment(\.accessibilityReduceMotion)`.
- IOS-A11Y-1. Tabs use `.accessibilityAddTraits(.isSelected)` when selected. Haptics are `UISelectionFeedbackGenerator` for a tab change and `UIImpactFeedbackGenerator(style: .light)` for the FAB.
- IOS-ICON-1. Search keeps `magnifyingglass`, bold when selected (#124). The add book icon is a custom symbol built from I-ICON-3.

## 10. Web

- WEB-ARCH-1. The mockup's structure is the reference: the overlay is `position: fixed` at the bottom, with the bar and FAB positioned from the layout function. `contentBottomPadding` is a CSS custom property on the scroll container.
- WEB-MOT-1. Use CSS transitions on `width` and `bottom`, enabled only while a mode change is in progress (the mockup's `.anim` class), so live resizes aren't eased. Honour `prefers-reduced-motion: reduce`.
- WEB-TOK-1. The tokens in §1 go in `frontend/assets/atrium.css` alongside the palette.

---

## 11. Open questions

| # | Question | Current answer |
|---|---|---|
| T1 | Should the motion tokens (`emphasized`, the durations) join Atrium's shared motion set, next to the springs in `Motion.kt`? | Yes. Propose them to the design system when the first client ships. |
| T2 | Should `ChromeLayout` be shared code, for example Kotlin Multiplatform, rather than written per client? | No. It is about 20 lines, and the unit-test tables keep the clients in step. |

## 12. Changelog

| Date | Change |
|---|---|
| 2026-11-05 | First draft, from the floating-bar mockup and the current Android shell. |
| 2026-11-05 | I-MINI-1: the mini player is its own floating bar above the nav bar; I-MINI-3 keeps today's mini player in that slot until its own ticket. |
