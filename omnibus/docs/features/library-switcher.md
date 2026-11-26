# Library Switcher

| Version | Status | Last updated |
|---|---|---|
| 1.1.0 | Accepted | 2026-11-26 |

## Summary

The library switcher is for readers who have accounts on more than one Omnibus server. Each server they sign in to is saved as a library, with its own session and its own data on the device. Picking another library from the You tab syncs the current one, plays a short bookcase animation and reopens the app on the chosen library, with no need to sign out or set the server up again.

## Scope

**In scope**

- Saving several libraries, each with its own address, name, username, session and data on the device.
- The account card on the You tab and the switcher sheet it opens.
- Manage libraries: the list, each library's detail screen, renaming and removing.
- Adding a library from the You tab, including an address that's already saved.
- The switching screen: the sync, the bookcase animation, the failure state, and its use when adding and removing libraries.
- A library whose session the server has ended.
- Moving the existing server and session into the first library on upgrade.
- Phones, and wide windows from 600dp.
- The light and dark themes.

**Out of scope**

- Signing out of a library without removing it.
- Reordering libraries.
- An indicator of changes waiting to sync.
- Syncing a library in the background while another library is active.
- Saving notes, highlights, bookmarks, ratings, reviews or read status on the device until they sync.
- Long-pressing the You tab to switch.
- Syncing the list of libraries between devices.
- Showing more than one library at once.

## Glossary

| Term | Meaning |
|---|---|
| Library | One saved Omnibus server: its address, a name, the username signed in to it, its session, and its data on this device. The address identifies the library. |
| Active library | The library the app is showing. Every other library is inactive. |
| Address | The server address, normalized as the Connect screen already does: scheme added when missing, trailing slashes removed, host compared without regard to case. |
| Session | The sign-in the server issued for a library. Each library keeps its own. |
| Signed out | A library whose session the server has ended. It stays in the list with its data. |
| Unsynced change | A change made on this device that the active library's server hasn't received yet. |
| Sync | Sending every unsynced change to the library's server. |
| Switch | Making another library active. |
| Bookcase | The shelves of spines on the switching screen, one spine per library. |
| Monogram | The single character that stands for a library on spines and tiles. |
| Illuminated initial | The monogram drawn large in an ornamental frame, as the first letter of the library's name on the switching screen. |

---

# Part 1: Product

## Screens

### You tab

| Dark | Light |
|---|---|
| <img src="images/library-switcher/you-card-dark.png" width="300" alt="You tab with the account card, dark"> | <img src="images/library-switcher/you-card-light.png" width="300" alt="You tab with the account card, light"> |

- The account card sits under the masthead, above the Requests row. The whole card opens the switcher sheet.
- The "Libraries" row opens Manage libraries.

### Switcher sheet

| Dark | Light |
|---|---|
| <img src="images/library-switcher/switcher-sheet-dark.png" width="300" alt="Switcher sheet, dark"> | <img src="images/library-switcher/switcher-sheet-light.png" width="300" alt="Switcher sheet, light"> |

- Libraries are listed in the order they were added. The active library has a check and stays in its place.
- A row's second line is "‹username› · ‹host›", where the host includes the port when the address has one.
- From 600dp the sheet is a centred dialog, as the [bottom-sheet rules](../design-system/bottom-sheet.md#wide-windows) require.

### Manage libraries

| List, dark | Detail, dark |
|---|---|
| <img src="images/library-switcher/manage-libraries-dark.png" width="300" alt="Manage libraries, dark"> | <img src="images/library-switcher/library-detail-dark.png" width="300" alt="Library detail, dark"> |

| List, light | Detail, light |
|---|---|
| <img src="images/library-switcher/manage-libraries-light.png" width="300" alt="Manage libraries, light"> | <img src="images/library-switcher/library-detail-light.png" width="300" alt="Library detail, light"> |

- The detail screen's title is the library's name, and it follows the name field as it changes.
- A rename is saved when the reader leaves the screen.
- The address and username are plain text rows, not fields, so only the name looks like something to edit.

### Removing a library

| Confirm | Syncing | Sync failed |
|---|---|---|
| <img src="images/library-switcher/remove-confirm-dark.png" width="240" alt="Remove confirmation, dark"> | <img src="images/library-switcher/remove-syncing-dark.png" width="240" alt="Remove syncing, dark"> | <img src="images/library-switcher/remove-failed-dark.png" width="240" alt="Remove sync failed, dark"> |
| <img src="images/library-switcher/remove-confirm-light.png" width="240" alt="Remove confirmation, light"> | <img src="images/library-switcher/remove-syncing-light.png" width="240" alt="Remove syncing, light"> | <img src="images/library-switcher/remove-failed-light.png" width="240" alt="Remove sync failed, light"> |

- These dialogs are for removing an inactive library. Removing the active library confirms with the same dialog, then continues on the switching screen (see [Removing the active library](#removing-the-active-library)).

### Adding a library

| Connect | Already saved |
|---|---|
| <img src="images/library-switcher/add-library-dark.png" width="240" alt="Add library, Connect, dark"> | <img src="images/library-switcher/add-library-duplicate-dark.png" width="240" alt="Add library, address already saved, dark"> |
| <img src="images/library-switcher/add-library-light.png" width="240" alt="Add library, Connect, light"> | <img src="images/library-switcher/add-library-duplicate-light.png" width="240" alt="Add library, address already saved, light"> |

- "Add library" opens the existing Connect screen full-screen, with a close button. Connecting goes on to the existing sign-in screen, unchanged: the username can be edited and "Use a different server" returns to Connect.
- First-time setup, with no libraries saved, is the existing flow and has no close button.

### Switching screen

| Syncing | Opening | Sync failed |
|---|---|---|
| <img src="images/library-switcher/switching-syncing-dark.png" width="240" alt="Switching, syncing Home, dark"> | <img src="images/library-switcher/switching-opening-dark.png" width="240" alt="Switching, opening Work book club, dark"> | <img src="images/library-switcher/switching-failed-dark.png" width="240" alt="Switching, sync failed, dark"> |
| <img src="images/library-switcher/switching-syncing-light.png" width="240" alt="Switching, syncing Home, light"> | <img src="images/library-switcher/switching-opening-light.png" width="240" alt="Switching, opening Work book club, light"> | <img src="images/library-switcher/switching-failed-light.png" width="240" alt="Switching, sync failed, light"> |

The screen runs in three stages:

1. **Syncing.** The active library's spine is raised and its name is shown. The app syncs it. This stage is skipped when there are no unsynced changes.
2. **Lift.** The raised spine settles back onto its shelf, then the new library's spine lifts. The name and illuminated initial crossfade to the new library's as the new spine lifts.
3. **Opening.** The screen holds until the new library is ready, then crossfades into it.

- Spines are drawn in list order, filling each shelf from the left. Each spine's height is fixed for its library, so the bookcase looks the same every time.
- The illuminated initial replaces the first letter of the name when the name starts with that letter. When the monogram is the Maltese cross, the full name follows the initial.
- In the failure state the bookcase stays still and keeps showing the current library.

### More libraries than one shelf holds

| Phone, dark | Phone, light |
|---|---|
| <img src="images/library-switcher/switching-shelves-dark.png" width="300" alt="Two shelves on a phone, dark"> | <img src="images/library-switcher/switching-shelves-light.png" width="300" alt="Two shelves on a phone, light"> |

| Wide window, dark |
|---|
| <img src="images/library-switcher/switching-wide-dark.png" width="520" alt="Bookcase on a wide window, dark"> |

| Wide window, light |
|---|
| <img src="images/library-switcher/switching-wide-light.png" width="520" alt="Bookcase on a wide window, light"> |

- When a shelf is full, the next library starts a new shelf below it.
- When the bookcase is taller than the window allows, it scrolls by itself: it starts on the active library's shelf, then pans to the new library's shelf before the lift.

### Session ended

| Dark | Light |
|---|---|
| <img src="images/library-switcher/session-ended-dark.png" width="300" alt="Sign-in after the session ended, dark"> | <img src="images/library-switcher/session-ended-light.png" width="300" alt="Sign-in after the session ended, light"> |

- This is the existing sign-in screen in a re-sign-in mode: the username is locked, and "Switch library" replaces "Use a different server" and "Register".
- "Switch library" opens the switcher sheet. It shows only when other libraries are saved.

## States and copy

### Account card and switcher sheet

| State | When | Copy | Action |
|---|---|---|---|
| Account card | Always, on the You tab. | Name. Second line: "‹username› · ‹host›". Link: "Switch". | Tap opens the switcher sheet. |
| Sheet | The account card is tapped. | Title: "Libraries". Actions: "Add library", "Manage libraries". | Tap a library, or an action. |
| Signed out tag | A library's session has ended. | "Signed out" | None |

### Manage libraries

| State | When | Copy | Action |
|---|---|---|---|
| List | Manage libraries is open. | Title: "Libraries". Action: "Add library". | Tap a library opens its detail. |
| Detail | A library is open. | Labels: "Name", "Address", "Username". Name hint: "Shown in your list of libraries. Leave it blank to use the address." Button: "Remove library". | Edit the name, or remove. |
| Confirm remove | "Remove library" is tapped. | Title: "Remove ‹name›?" Body: "You'll be signed out of ‹name›, and its data on this device will be deleted." Buttons: "Cancel", "Remove". | "Remove" starts removing. |
| Removing | An inactive library is being synced before removal. | Button: "Removing", with a progress indicator. "Cancel" is disabled. | None |
| Remove sync failed | The sync before removal failed. | Title: "Couldn't sync ‹name›". Body: "Some changes on this device haven't reached ‹name›. If you remove it now, they'll be lost." Buttons: "Cancel", "Remove anyway". | "Remove anyway" removes it. |

### Adding a library

| State | When | Copy | Action |
|---|---|---|---|
| Connect | "Add library" is tapped. | The existing Connect screen, with a close button. | Close returns to where the reader was. |
| Already saved | The address is already a saved library. | Error: "‹address› is already in your libraries." Button: "Switch to ‹name›". | Switches to that library; the active library just closes the flow. |

### Switching screen

| State | When | Copy | Action |
|---|---|---|---|
| Syncing | The active library is being synced. | "Syncing ‹current›" | None |
| Opening | The new library is being opened. | "Opening ‹new›" | None |
| Removing | The active library is being synced before removal. | "Syncing ‹current›" | None |
| Sync failed | The sync failed or took longer than 10 seconds. | Title: "Couldn't sync ‹current›". Body: "Some changes on this device haven't reached ‹current›. If you switch now, they may be lost." Buttons: "Stay on ‹current›", "Switch anyway". | "Stay on ‹current›" returns to where the reader was. "Switch anyway" continues. |
| Remove sync failed | As above, while removing the active library. | Title: "Couldn't sync ‹current›". Body: "Some changes on this device haven't reached ‹current›. If you remove it now, they'll be lost." Buttons: "Cancel", "Remove anyway". | "Cancel" returns to the detail screen. "Remove anyway" continues. |

### Session ended

| State | When | Copy | Action |
|---|---|---|---|
| Re-sign-in | The server ended a library's session, and that library is opened. | Title: "Welcome back". Address under the title. Body: "Your session on ‹name› has ended. Sign in to open it again." Username locked. Link: "Switch library". | Sign in, or switch. |

## Requirements

### Libraries

- A library MUST be identified by its address. The app MUST NOT save two libraries with the same address.
- Each library MUST keep its own username and session.
- A new library's name MUST be its host, including the port when the address has one.
- A name MUST be at most 40 characters. A blank name MUST show as the host. Names MAY repeat.
- Libraries MUST be listed in the order they were added, in the sheet and in Manage libraries. There MUST be no limit on how many libraries can be saved.
- The address and username MUST NOT be editable. A different address is a different library.

### Monograms

- A library's monogram MUST be the first letter in its name, upper-cased, set in Grenze Gotisch SemiBold.
- When the name contains no letter, the monogram MUST be a Maltese cross (✠), drawn so it looks the same on every device.
- The same monogram MUST be used on spines, tiles, the account card and the illuminated initial.

### Sessions

- Switching MUST NOT end any library's session. Switching to a signed-in library MUST NOT ask the reader to sign in.
- When the server rejects a library's session, that library MUST become signed out. Its data on the device MUST be kept.
- Opening a signed-out library MUST show the re-sign-in screen, with its username filled in and locked.
- Signing in again MUST be the only way to restore a signed-out library's session. Using a different account on the same server MUST mean removing the library and adding it again.
- There MUST be no action to sign out of a library without removing it.

### Account card and sheet

- The account card MUST show the active library's monogram, name, username and host. Tapping it MUST open the switcher sheet.
- Tapping a library in the sheet MUST switch to it at once, with no confirmation. Tapping the active library MUST only close the sheet.
- Tapping a signed-out library MUST switch to it, and the switch MUST end on its re-sign-in screen.

### Adding a library

- "Add library" MUST open the Connect screen with a close button. Closing MUST return the reader to where they were, with nothing saved.
- Signing in MUST save the library and switch to it on the switching screen. The switch MUST sync the previous library first, as any switch does. The new library's spine MUST drop onto the end of the last shelf before it lifts.
- An address that matches a saved library, after normalizing, MUST show the Already saved state instead of signing in again.

### Switching

- A switch MUST first sync the active library, sending every unsynced change to its server, and wait for the result.
- The sync MUST count as failed when any change isn't accepted, the server can't be reached, the session is rejected, or 10 seconds pass.
- When the sync fails, the switching screen MUST show the Sync failed state in place. It MUST NOT use a dialog.
- "Stay on ‹current›" MUST return the reader to where they were, with their unsynced changes still held. "Switch anyway" MUST continue the switch.
- When there are no unsynced changes, the Syncing stage MUST be skipped.
- A switch MUST stop audio playback, close the reader and the player, and clear every tab's history.
- The switch MUST end on the new library's Library tab, or on its re-sign-in screen when it is signed out.
- Nothing from the previous library MUST remain visible after a switch: no titles, covers, progress, filters or shelves.
- When the new library's server can't be reached, the switch MUST still complete, and the app MUST show its usual can't-reach-server states.
- Back MUST do nothing while syncing, lifting or opening. In the Sync failed state, Back MUST act as "Stay on ‹current›".

### Switching animation

- The bookcase MUST show every library as a spine, in list order, filling each shelf from the left.
- A shelf MUST hold as many spines as fit its width. The next library MUST start a new shelf below.
- The active library's spine MUST be raised and drawn in the accent colour.
- The lift MUST take about 0.9 seconds: the current spine settles, then the new spine lifts, and the name and illuminated initial crossfade during the lift.
- When a library has just been added, its spine MUST drop onto the end of the last shelf, taking about 0.45 seconds, before the lift. While remove-animations is on, the drop MUST happen in one step.
- When the bookcase doesn't fit the window, it MUST scroll to the new library's shelf before the lift.
- The Opening stage MUST hold until the new library is ready, then crossfade into it.
- There MUST be no way to skip the animation.

### Data on the device

- Each library MUST keep its own reading positions, listening positions and sessions, per-book playback speeds, offline covers, dismissed resume offers, filters and shelves.
- Switching MUST NOT clear or change any other library's data.
- Reader appearance settings and the Library sort MUST be shared by every library on the device.
- Changes to a library that are still unsynced after "Switch anyway" and are saved on the device (listening positions, listening sessions and per-book playback speeds) MUST stay with that library. They MUST be sent the next time it is the active library, and never while another library is active.
- Changes held only in memory (notes, highlights, bookmarks, ratings, reviews, read status, and reading position not yet sent) MAY be lost after "Switch anyway". Keeping them is out of scope.

### Removing

- Removing MUST first ask for confirmation.
- Removing MUST sync the library, then end its session on the server, delete its data on the device, and remove it from the list.
- When that sync fails, the reader MUST be offered "Cancel" or "Remove anyway". "Remove anyway" MUST continue the removal.
- Ending the session on the server MUST NOT block removal. When the server can't be reached, the library MUST still be removed.
- Removing an inactive library MUST happen in the dialog, without the switching screen.

### Removing the active library

- After confirmation, removing the active library MUST run on the switching screen: it syncs, the library's spine fades off its shelf and the gap closes, then the most recently used remaining library's spine lifts and that library opens.
- When no library remains, the app MUST end on the Connect screen, as on first launch.

### Upgrading

- On the first launch after upgrading, an existing saved server and session MUST become the first library, named after its host, without the reader signing in again.
- That library's username MUST come from the server. Until it can be read, the account card MUST show the host alone on its second line.
- The existing data on the device MUST become that library's data.

### Wide windows

- From 600dp, the switcher sheet MUST be a centred dialog.
- From 600dp, each shelf MUST be at most 560dp wide, with the bookcase, name and status centred in the window.

### Accessibility

- The account card MUST be one focusable element announced as "‹name›, signed in as ‹username› on ‹host›. Switch library".
- Each row in the sheet MUST announce its name, username, host, and "current" or "signed out" where those apply.
- When the switching screen opens, TalkBack MUST announce "Switching to ‹new›", then each status change.
- The bookcase and the illuminated initial MUST be decorative and not focusable.
- On Sync failed, focus MUST move to the failure title.
- When the system's remove-animations setting is on, the lift MUST happen in one step and the name MUST crossfade. Nothing MUST slide or pan.
- Every touch target MUST be at least 48dp.

---

# Part 2: Engineering

## Server contract

### `GET api/_health`

Already used by Connect. A 2xx answer means an Omnibus server is at the address. No fields are read.

### `POST api/auth/login`

Already used by sign-in. Body: `username`, `password`.

| Field | Meaning |
|---|---|
| `token` | The new session. Saved for the library being signed in to. |
| `user.username` | Saved as the library's username. |

Errors are handled as sign-in already handles them.

### `GET api/auth/me`

Already used to check the session at launch. Read at upgrade for `username`, which fills in the first library's username. A 401 makes the library signed out.

### `POST api/auth/logout`

Ends the session the request carries, sent with the removed library's token. The server answers 204, including when the session has already ended. The answer is not read, and any failure, including a connection failure, is ignored.

### Sync

The sync uses the endpoints each feature already writes to. It adds no endpoint.

### Errors

| Response | Result |
|---|---|
| 401 on any request for the active library | That library becomes signed out, and the app shows its re-sign-in screen. |
| Connection failure, an error status, or 10 seconds while syncing | Sync failed. |
| Connection failure on logout | Ignored; removal continues. |

## Architecture

The feature follows the layers in `docs/engineering/feature-layers.md`.

- **Data.**
  - Where each library's data lives on the device, how it is scoped to the library, and how it is deleted are set by `docs/engineering/local-storage.md`.
  - A `LibrariesRepository` replaces the single saved server. It holds the libraries (id, address, name, username, when added, when last used) and which one is active, in the database's `libraries` table, and exposes add, rename, remove and set-active.
  - `ServerConfigurationRepository` keeps validating addresses. Saving an address becomes adding a library.
  - `StoredAuthTokenProvider` and `SavedServerBaseUrlProvider` read the active library's token and address. `invalidate()` clears only the active library's token.
  - A `LibrarySync` sends every unsynced change for one library and reports success or failure within a time limit:
    - for the active library, it drains the highlights, bookmarks, reviews, read-status, journal and metadata write queues, the pending listening positions and sessions, the pending playback speeds, and the latest reading position;
    - for an inactive library (when removing), it sends only that library's pending changes in the database, using that library's own address and token.
  - `LibrarySync` also reports whether anything is waiting, so the Syncing stage can be skipped.
- **Switch.** Committing a switch saves the new active library, then restarts the app process. The new process opens straight into the switching screen's Opening stage. A restart is the only way to make sure none of the in-memory state held by `@Singleton` repositories (per-book highlights, bookmarks, reviews, progress records, playback state, cover memory cache and others) carries over from the previous library. It also stops playback and clears every back stack.
- **Upgrade.** The upgrade in `docs/engineering/local-storage.md` creates the first library from the saved server and moves the existing data into it. The app then fetches `api/auth/me` to fill in the username.
- **UI.**
  - The You root gains the account card above the Requests row, and a "Libraries" row.
  - The switcher sheet uses the shared app sheet component.
  - `ManageLibrariesRoute` and `LibraryDetailRoute(libraryId)` are pushed onto the You tab's stack.
  - "Add library" reuses `ServerConfigurationScreen` and `LoginScreen` in an adding mode, with a close action. `LoginScreen` gains a re-sign-in mode: username locked, and "Switch library" in place of the change-server and register links.
  - `SwitchingRoute` is a root route above the shell. It covers switching, adding and removing the active library, and is restored in its Opening stage after the restart.
  - The bookcase is drawn in Compose. Spine heights come from a hash of the library id.
- **Theme and assets.**
  - Grenze Gotisch SemiBold (SIL Open Font License) is added to `res/font` for monograms.
  - The Maltese cross and the illuminated frame are vector drawables.
- **Reuse.**
  - `ServerAddress.normalize` for comparing addresses.
  - The existing session check at launch, for the active library.
  - The navigation rail's existing 600dp breakpoint for wide windows.

## Constants

| Name | Value | Meaning |
|---|---|---|
| Sync limit | 10s | Longest a sync runs before it counts as failed. |
| Lift | 900ms | The settle and the lift together. |
| Drop-in | 450ms | How long a newly added library's spine takes to drop onto the shelf. |
| Name length | 40 characters | Longest library name. |
| Spine width | 30dp | Width of every spine. |
| Spine gap | 7dp | Space between spines on a shelf. |
| Spine height | 84dp to 112dp | Range of spine heights, fixed per library. |
| Raise | 16dp | How far the active spine is lifted. |
| Shelf margin | 20dp | Space on each side of the bookcase. |
| Shelf width | 560dp | Widest a shelf grows. |
| Illuminated initial | 64dp | Size of the framed initial. |
| Wide breakpoint | 600dp | Window width from which the sheet is a dialog and the shelf cap applies. |

---

## Changelog

**1.1.0** (2026-11-26)

- Adds the Drop-in constant (450ms) and states how long the new spine's drop takes, and that it is one step under remove-animations. Why: adding a library drops the new spine before the lift, and its timing wasn't specified.

**1.0.2** (2026-11-25)

- The library detail screen shows the address and username as plain text rows instead of field boxes, with refreshed renders. Why: the filled boxes looked like inputs that ignored taps. No behaviour changes.

**1.0.1** (2026-11-25)

- The architecture's storage details move to `docs/engineering/local-storage.md`, which keeps each library's data in one database instead of a folder of files per library. Why: storage is shared by features beyond this one, and rows scoped to a library are simpler to keep apart and delete than files. No behaviour changes.

**1.0.0** (2026-11-25)

- Initial spec.
