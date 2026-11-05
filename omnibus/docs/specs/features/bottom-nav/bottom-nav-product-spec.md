# Bottom Navigation: Product Spec

| | |
|---|---|
| Status | Draft |
| Related issues | #124 (tab set and selection), #162 (content under the system bars), #16 (masthead and Add books button) |
| Technical spec | [`bottom-nav-technical-spec.md`](bottom-nav-technical-spec.md) |
| UI reference | [`bottom-nav-mockup.html`](bottom-nav-mockup.html): open it in a browser and use the device presets and the width slider |

## About this spec

This is the source of truth for what the bottom navigation looks like and how it behaves. It is written for product and design, and says nothing about how it is built; the technical spec covers that. When the app and this spec disagree, one of them is wrong: fix the app, or change the spec in the same PR and say why.

**IDs.** Every requirement has a stable ID, `P-<AREA>-n`. Cite IDs in tickets, test names and PRs. Never renumber one. A dropped requirement is marked *Withdrawn* and left in place.

**Wording.** "Must" is required. "Should" is the default, and departing from it needs a reason in the PR.

### Glossary

| Term | Meaning |
|---|---|
| Bar | The floating card that holds the tabs. |
| Tab | One destination in the bar: Library, Search, Stats or You. |
| Add book button | The floating action button (FAB) that starts adding a book to the library. |
| Chrome | The bar, the add book button and the mini player together. |
| Mini player bar | The audiobook mini player, as its own floating bar above the nav bar. |
| Narrow layout | The bar fills the screen width, and the add book button floats above the bar's right end. |
| Wide layout | The bar stops growing and stays in the bottom-left corner; the add book button moves to the bottom-right corner. |
| Breakpoint | The screen width at which the layout switches from narrow to wide. |

---

## 1. Why

Today the bar is a flush strip across the whole bottom of the screen. That works on a phone, but on a foldable or a tablet it stretches five icons across 800 dp or more, and the tabs drift far apart. The redesign makes the bar a self-contained card: it fills a phone screen, stops growing at a comfortable width on bigger screens, and leaves the rest of the bottom edge free.

The Library's add button moves out of the masthead into a floating action button with a clearer "add a book" icon, so the main action is in reach of the thumb.

## 2. Scope

**In scope**

- The bar's look, size, position and behaviour on every screen width.
- The tab set: Library, Search, Stats, You.
- The add book button: its look, where it sits, and when it shows.
- The motion when the layout changes between narrow and wide.
- How the chrome hides, and how content scrolls around it.

**Out of scope**

- The content of the Search, Stats and You tabs. Each is its own ticket; until then each shows a placeholder.
- Moving Authors and Series into Search's Browse directory (#124 AC1 tracks it).
- The Add books sheet that the add book button opens (#16, Linear H00-107).
- A navigation rail or side bar on large screens. The floating bar is used at every width (see Q1).

## 3. The bar

**Requirements**

- P-BAR-1. The bar is a floating card with rounded corners. It sits above the content, casts a shadow, and is inset from the screen edges.
- P-BAR-2. **Size and position.** The bar sits 12 dp from the left edge and 8 dp above the system navigation area (gesture pill or 3-button bar). It is 64 dp tall.
- P-BAR-3. **Width.** On a narrow screen the bar fills the width, leaving 12 dp on both sides. On a wide screen it is 360 dp wide and stays in the bottom-left corner (§5).
- P-BAR-4. **Look.** The bar uses the theme's raised-surface colour (`bg2`) with a hairline border, a high-elevation shadow and 16 dp corners. It uses only the shared Omnibus palette, so it looks the same as on iOS and web. It does not take colours from the device wallpaper (Material You dynamic colour).
- P-BAR-5. The bar's corners match the add book button's corners (16 dp), so the two read as one family.
- P-BAR-6. Light and dark themes both use the same layout. Only the colours change.

**Acceptance criteria**

- P-BAR-AC1: On a phone the bar has even 12 dp margins on the left and right, and sits 8 dp above the navigation area.
- P-BAR-AC2: On a foldable or tablet the bar is 360 dp wide in the bottom-left corner.
- P-BAR-AC3: The bar looks right in light and dark themes, and its colours don't change with the wallpaper.

## 4. Tabs

**Requirements**

- P-TAB-1. **Tab set.** Four tabs, in this order: Library, Search, Stats, You. Authors and Series are no longer tabs.
- P-TAB-2. Each tab shows an icon with a label under it. Labels are always shown.
- P-TAB-3. **Selected tab.** The selected tab's icon and label use the accent colour, its icon switches to the filled version, and a soft accent pill sits behind the icon. Search has no filled icon, so its selected icon is drawn with a heavier line instead. The other tabs are grey (`ink3`).
- P-TAB-4. The app opens on Library.
- P-TAB-5. **Switching tabs** shows that tab where the user left it: its screen, scroll position and anything opened inside it. Switching gives a light haptic tick.
- P-TAB-6. **Tapping the selected tab** returns that tab to its first screen. In Search that is the Browse page.
- P-TAB-7. **Back.** Back closes the current screen inside the tab. On the first screen of Search, Stats or You it returns to Library. On Library's first screen it leaves the app.
- P-TAB-8. The selected tab and each tab's history survive rotation, folding and unfolding, and the app being closed in the background.

**Acceptance criteria**

- P-TAB-AC1: The bar shows Library, Search, Stats and You, in that order.
- P-TAB-AC2: Each tab keeps its place when the user switches away and back.
- P-TAB-AC3: Tapping the selected tab returns it to its first screen.
- P-TAB-AC4: Back follows P-TAB-7.

## 5. Narrow and wide layouts

The layout depends on the width the app has, not on the device type. A folded phone, an unfolded phone, split-screen and a resizable window all follow the same rule.

**Requirements**

- P-LAY-1. **Narrow layout.** The bar fills the width with 12 dp on each side. The add book button floats above the bar's right end, 12 dp above it, with its right edge lined up with the bar's right edge.
- P-LAY-2. **Wide layout.** The bar is 360 dp wide in the bottom-left corner. The add book button sits in the bottom-right corner, 12 dp from the right edge, vertically centred on the bar.
- P-LAY-3. **Breakpoint.** The layout is wide when the bar at full size, the add book button and the margins all fit side by side: 12 + 360 + 12 + 56 + 12 = **452 dp**. Below that it is narrow. At exactly 452 dp it is wide.
- P-LAY-4. **Even margins.** The space left and right of the chrome is always 12 dp. There is never a width where the bar has stopped growing but the button hasn't moved beside it.
- P-LAY-5. The breakpoint is the same whether or not the add book button is showing, so the bar doesn't change width when the user switches tabs.
- P-LAY-6. **Right-to-left languages** mirror the layout: the wide bar sits in the bottom-right corner and the button in the bottom-left.

**Acceptance criteria**

- P-LAY-AC1: On a typical phone (360–430 dp) the layout is narrow, with even margins.
- P-LAY-AC2: On an unfolded foldable, in landscape and on a tablet, the layout is wide.
- P-LAY-AC3: Dragging a split-screen divider across 452 dp switches the layout, and back.
- P-LAY-AC4: In Arabic or Hebrew the wide layout is mirrored.

## 6. Moving between layouts

**Requirements**

- P-MOT-1. Switching between narrow and wide is animated, never a jump.
- P-MOT-2. **Going wide:** the bar shrinks first. Once its end has cleared the button, the button drops into the corner.
- P-MOT-3. **Going narrow:** the button lifts first, and then the bar grows back under it.
- P-MOT-4. The button dips slightly in size while it moves, so the move reads as one deliberate motion.
- P-MOT-5. The whole switch takes about half a second.
- P-MOT-6. Resizing a window without crossing the breakpoint is not animated: the bar follows the resize directly, so it never lags behind the user's drag.
- P-MOT-7. If the user has turned off animations in the system settings, the switch is instant.

**Acceptance criteria**

- P-MOT-AC1: Unfolding a foldable shows the bar shrink and the button settle into the corner, in that order. Folding reverses it.
- P-MOT-AC2: With system animations turned off, the layout switches with no motion.

## 7. The add book button

**Requirements**

- P-FAB-1. The add book button replaces the add button in the Library masthead. The masthead keeps its other trailing controls, such as the offline pill.
- P-FAB-2. **Look.** A 56 dp rounded square with 16 dp corners, filled with the accent colour, with a shadow. The icon is a book with a "+" badge on its corner, in `accentInk`. The icon tells the user the button adds a book to their library, not something generic.
- P-FAB-3. **Where it shows.** Only on Library's first screen. It is hidden on Search, Stats and You, and on any screen opened inside Library.
- P-FAB-4. It animates in and out when the user switches to and from Library: it grows and fades in, and shrinks and fades out.
- P-FAB-5. **Tapping it** gives a light haptic tick and opens the Add books sheet (upload or barcode scan). Until the sheet exists, tapping it does nothing.
- P-FAB-6. It shows for everyone, offline too. The sheet decides what it offers.
- P-FAB-7. Screen readers announce it as "Add books by upload or barcode scan", a button.

**Acceptance criteria**

- P-FAB-AC1: The button shows on Library's first screen and nowhere else.
- P-FAB-AC2: The masthead no longer has an add button.
- P-FAB-AC3: TalkBack and VoiceOver read the label in P-FAB-7.

## 8. When the chrome shows

**Requirements**

- P-VIS-1. The chrome shows on every tab's screens, except while a book's detail page is anywhere in the selected tab's history. Book Detail owns the bottom edge for its own action area.
- P-VIS-2. The chrome slides down out of view when Book Detail opens. It comes back as soon as the user commits to leaving Book Detail, not after the closing animation ends.
- P-VIS-3. The reader and the full player open over the whole shell, so the chrome isn't visible there.
- P-VIS-4. **Mini player.** While a book is loaded, the mini player is its own floating bar directly above the nav bar: the same width and left edge as the nav bar, 8 dp above it, with the same rounded corners, surface and shadow. In the narrow layout the add book button floats above the mini player bar; in the wide layout it stays beside the nav bar. The mini player bar shows and hides with the rest of the chrome. Its own design (what's inside the bar) is a separate ticket (Q3).
- P-VIS-5. **Keyboard.** The keyboard covers the chrome. The chrome doesn't move up with it.

**Acceptance criteria**

- P-VIS-AC1: Opening a book hides the chrome, and going back brings it back.
- P-VIS-AC2: The mini player bar sits above the nav bar with the same width and left edge, and the two hide together.
- P-VIS-AC3: Typing in a search field covers the bar.

## 9. Content around the chrome

**Requirements**

- P-CON-1. Content runs the full height of the screen and scrolls behind the chrome. It shows in the margins around the bar and button, which is what makes the bar read as floating.
- P-CON-2. Scrolled to the end, the last row sits fully above the chrome, with some breathing room. Nothing is unreachable behind the bar or the button.
- P-CON-3. In the narrow layout, screens with the add book button leave room for it too.
- P-CON-4. Anything under the margins around the chrome stays tappable.

**Acceptance criteria**

- P-CON-AC1: The last row of the library can be scrolled fully into view above the bar and the button, on a phone and on a foldable.
- P-CON-AC2: A cover visible next to the wide bar can be tapped.

## 10. Accessibility

- P-A11Y-1. Each tab is announced with its label and as a tab, and the selected one as selected.
- P-A11Y-2. Every tab and the add book button has a touch target of at least 48 × 48 dp.
- P-A11Y-3. At large font sizes the bar grows taller to fit its labels. A label that still doesn't fit is shortened with an ellipsis rather than wrapping, and screen readers still read it in full.
- P-A11Y-4. Selected and unselected tabs differ by more than colour: the filled icon (or heavier line for Search) and the pill mark the selection too.
- P-A11Y-5. Focus order, for keyboard and switch access, runs through the tabs in order, then the add book button.

## 11. Edge cases

| Case | Behaviour |
|---|---|
| Smallest phones (320 dp) | Narrow layout, bar 296 dp wide. All four labels fit at the default font size. |
| Exactly 452 dp | Wide layout (P-LAY-3). |
| Folding or unfolding mid-scroll | The layout animates (§6); the scroll position and selected tab are kept. |
| Rotating a phone to landscape | Usually wide. Margins are measured from the safe area, so a side-mounted 3-button bar or a camera cutout never overlaps the chrome. |
| Split-screen or resizable window | The layout follows the app's window width, not the screen's. |
| Fast back-and-forth resizes across the breakpoint | Each change picks up from wherever the motion currently is; nothing jumps or restarts. |
| Gesture navigation vs 3-button navigation | The bar always sits 8 dp above whichever is present. |
| Rapid repeated taps on the add book button | Only one Add books sheet opens. |
| Switching tabs while the layout is animating | Both happen; the add book button fades out wherever it is. |
| Theme switch | Colours change at once; the layout and position don't. |
| Large font sizes | P-A11Y-3. The breakpoint doesn't change, because it depends on width only. |
| A mini player appears or disappears | The mini player bar appears or leaves above the nav bar; the nav bar doesn't move. In the narrow layout the add book button moves up or down with it. |
| Mini player stopped from its ✕ | The mini player bar leaves, and in the narrow layout the add book button settles back above the nav bar. |
| Offline | Everything shows as usual (P-FAB-6). |
| App restored after being closed in the background | The selected tab and every tab's history come back (P-TAB-8). The layout is worked out again from the current width. |

## 12. Decisions

| # | Decision | Why |
|---|---|---|
| D1 | A floating card, capped at 360 dp, rather than a full-width strip. | A full-width bar spreads tabs too far apart on foldables and tablets. |
| D2 | Anchored bottom-left on wide screens, not centred. | It leaves the bottom-right free for the add book button, and other floating controls later. |
| D3 | The bar caps at the same width at which the button moves beside it (P-LAY-4). | Any other rule leaves a range of widths with uneven margins. The cost is a one-time width change at the breakpoint, which §6 animates. |
| D4 | Shared Omnibus palette, not Material You dynamic colour. | Keeps Android in line with iOS and web, and avoids two unrelated accent colours side by side. |
| D5 | Four tabs, with Search in place of Authors and Series. | Matches iOS (#124). |
| D6 | A book-with-a-badge icon, not a plain "+". | Says what is being added. It also stays distinct from the Library tab's book icon. |
| D7 | The add book button shows on Library's first screen only. | It is the Library's action; on other tabs it would read as a global add. |
| D8 | 360 dp max width. | Gives each of the four tabs about 87 dp, close to today's per-tab width on a phone. |

## 13. Open questions

| # | Question | Current answer |
|---|---|---|
| Q1 | Should very large screens (tablets in landscape, desktop windows) use a side navigation rail instead? | No. The floating bar is used at every width for now. |
| Q2 | Do iOS and web adopt the floating bar? Today iOS has a flush bar (#124). | This spec is written to apply to every client. Each platform's adoption is its own ticket. |
| Q3 | What goes inside the floating mini player bar, and how it animates in and out? | Placement is settled (P-VIS-4). The contents and motion need a mockup and their own ticket. Until then the app keeps today's mini player, in the new position. |
| Q4 | Should tapping Library while already on its first screen scroll to the top? | Not yet. Today it only returns to the first screen (P-TAB-6). |
| Q5 | Should the selected icon also bounce, as on iOS (#124)? | No. The pill is the selection motion. Revisit if parity is wanted. |
| Q6 | In the Library an accent-filled circle means "a filter is on" (#16). Does an accent-filled button conflict with that? | No: the button's shape, size and position set it apart. Check in review. |
| Q7 | Does the content need a bottom fade behind the bar, as #162 proposed for the flush bar? | No. The bar is opaque and floats, and content showing in its margins is intended. |

## 14. Changelog

| Date | Change |
|---|---|
| 2026-11-05 | First draft, from the floating-bar mockup review, #124, #162, #16 and the current Android shell. |
| 2026-11-05 | P-VIS-4: the mini player is its own floating bar above the nav bar, not a row inside it. Q3 narrowed to its contents. |
