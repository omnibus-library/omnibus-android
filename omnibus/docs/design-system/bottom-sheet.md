# Bottom Sheets

This document decides when Omnibus uses a bottom sheet, what goes on one, and how it behaves. It builds on the [Material 3 bottom sheet guidance](https://m3.material.io/components/bottom-sheets/guidelines) and is stricter than it. Where the two disagree, this document wins.

Only **modal** bottom sheets are used. Standard (persistent, non-modal) bottom sheets are not part of the design system. Adding one requires changing this document first.

## Should this be a bottom sheet?

Work through these in order. The first match decides.

1. **Is it a place the user goes to?** It needs back-stack history or a deep link, holds long-form writing, is a creative editor, or takes more than about a minute. → **Full screen.**
2. **Is it a single blocking decision or notice with nothing else to choose?** "Delete this shelf?", "Can't play offline. OK." → **Dialog.**
3. **Is it a few actions tied to one element, each a single line that acts immediately?** The overflow on a row, a status pill. Up to about 7 items. → **Dropdown menu.**
4. **Is it passive feedback that needs no answer?** "Added to shelf." → **Snackbar.**
5. **Is it a short, self-contained task or a set of choices that belongs to the current screen?** → **Bottom sheet.**

When nothing matches, the content is probably two things at once. Split it before reaching for a sheet.

The test for step 5: when the sheet closes, the user is back where they were with nothing lost. A sheet may *end* by navigating somewhere as the result of a choice (opening a bookmark, starting playback of the chosen file), but it is never a destination itself.

## What belongs on a sheet

**Good fits:**

- **Pickers.** Choose one or many from a set: a sleep timer, playback speed, shelves, filter values.
- **Lists where tapping a row is the choice.** Chapters, contents, bookmarks. The list may be long; it scrolls inside the sheet (see [Height](#height)).
- **Search to pick one thing.** A text field and results, where the search only exists to find the item being picked (the book to merge into, the book to request).
- **Confirmations that carry options or context.** Which files to delete, what will be affected. A confirmation with nothing to choose is a dialog.
- **Action lists that need more than one line per row.** Rows with descriptions or live state (Ready, Sending, Sent) that a menu can't show.
- **Grouped choices.** Show *and* sort, or several facets together. A menu holds one flat set of choices only.

**Does not belong on a sheet:**

- **Long-form writing.** Any free-text field meant for more than a sentence or two: a review body, a journal entry, notes. → Full screen.
- **Creative editors.** Anything with many controls and a live preview of something the user is making, such as a quote card. → Full screen.
- **Multi-step flows.** A sheet allows at most two steps, and the second must be a confirm or review of the first (choose → confirm, search → confirm). Three or more steps, or a loop, is a screen.
- **Browsing.** Picking many items from the whole library, or exploring rather than choosing. → Full screen.

### Input limits

Input on a sheet is limited to pickers, toggles, sliders and **one single-line text field**. More than that is a form, and a form is a screen.

Any sheet holding input that isn't applied yet must catch every way of closing (swipe down, tap on the scrim, Back) and ask before discarding it. A sheet that applies each change immediately needs no guard.

### Destructive actions

A plain "Delete X?" is a dialog. A sheet is used only when the confirmation needs options or context, such as choosing which files or copies to remove. If a sheet's options can be empty, so that it would open straight on its confirm step, show the dialog instead.

## Behaviour

### Height

- A sheet opens at the height of its content. There is no half-open resting state: no partial expansion, no peek height.
- A sheet is never taller than **90%** of the window. Longer content scrolls inside the sheet, with the title row staying in place.
- Content that keeps needing the full 90% is a hint that it should be a screen (see step 1).

### Stacking

- Never open a sheet from a sheet. A task that needs a second level is a screen.
- The only thing allowed on top of a sheet is the dialog that asks before discarding unsaved input.

### Dismissal

- A sheet always closes with a swipe down, a tap on the scrim and Back.
- A sheet may block those **only** while its own committed action is in flight (sending, deleting, merging). It shows progress the whole time and unblocks when the action finishes or fails.
- A sheet never blocks dismissal because the user hasn't finished input. Unsaved input gets the discard prompt from [Input limits](#input-limits).

## Anatomy

- **Drag handle.** Always shown on the bottom sheet form: 36 × 5 dp in `line2`.
- **Title row.** Every sheet has a title stating what the sheet is for.
- **Close button (✕).** Required only on sheets with an explicit commit (Apply, Delete, Merge, Send). Sheets that apply changes immediately or pick on tap close with swipe, scrim or Back alone.
- **Actions.** Where commit buttons sit is decided by each sheet's content.
- **Scrim.** Every sheet uses the same shared scrim token. No sheet hides its scrim or sets its own alpha.

Every sheet is built on one shared app sheet component that enforces this anatomy, the height cap and the wide-window rules below. Don't build a `ModalBottomSheet` directly in a feature.

## Wide windows

At **600 dp** wide and up (the breakpoint where the navigation rail appears), a bottom sheet becomes a **centred dialog**:

- Width is capped at 560 dp, and height at about 80% of the window.
- Long content scrolls inside the dialog.
- There is no drag handle. The title row, the close button rule, the content and the dismissal rules are unchanged.

This applies to every sheet, including panels whose changes apply live (reader settings, playback speed).

## Examples

| Sheet | Verdict | Why |
|---|---|---|
| Sleep timer | Fits | Picker that applies immediately. |
| Add to shelf | Fits | Multi-select toggles, each applied immediately. |
| Shelf filter facet | Fits | Picker with an explicit Apply. |
| Bookmarks (book page) | Fits | Picker that ends by navigating to the bookmark. |
| Merge | Fits | Search to pick one book, then confirm. Two steps. |
| Delete book (with file choices) | Fits | Destructive confirmation that carries options. |
| Add books (wide window) | Fits | Becomes a centred dialog from 600 dp. |
| Write a review | Violation | Long-form writing, and it stacks a second sheet. Should be a screen. |
| Quote card | Violation | Creative editor with a live preview. Should be a screen. |
| Library filter menu | Violation | Grouped choices (show and sort) in a menu. Should be a sheet. |
| Delete book (single copy) | Violation | Opens straight on a confirm with no options. Should be a dialog. |
| Reader settings | Violation | Hides its scrim and is taller than 90%. |
| Add books, Edit chips | Violation | Open half height instead of at content height. |
| Chapters, Contents, Merge search, Add a request | Violation | Taller than 90% of the window. |
| Every other sheet on wide windows | Violation | Stays a bottom sheet from 600 dp. |

Violations are tracked in [Spec Violation (Design System): Bottomsheet](https://github.com/omnibus-library/omnibus-android/issues/322). Wide-window handling is tracked in [#321](https://github.com/omnibus-library/omnibus-android/issues/321).
