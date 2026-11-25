# Search

| Version | Status | Last updated |
|---|---|---|
| 1.0.0 | Accepted | 2026-11-25 |

## Summary

Search is the app's second tab. A reader types into one field and, as they type, sees the books, authors, series, tags and genres in their library that match, grouped by type. Picking a result opens it: a book, author or series opens its own page, and a tag or genre opens the list of books filed under it.

## Scope

**In scope**

- The Search tab: the search field, the grouped results, and every loading, empty and error state.
- Results for books, authors, series, tags and genres from the reader's own library.
- Opening a result, including the "Books tagged" and "Books in genre" lists.
- "See all" lists for a section with more matches than it shows.
- Phones, and wide windows from 600dp.
- The light and dark themes.

**Out of scope**

- Searching settings.
- Searching while offline, or without the server.
- Browse shortcuts, recently finished books or any other content before the reader types.
- Recent searches, suggestions and autocomplete.
- Narrators, publishers, shelves and collections as result types.
- Searching the text inside books.
- Online and metadata-provider results.
- A long-press menu on results.
- A list-detail layout on wide windows.

## Glossary

| Term | Meaning |
|---|---|
| Query | The text in the search field, with leading and trailing spaces removed. |
| Section | One group of results of a single type: Books, Authors, Series, Tags or Genres. |
| Total | How many items of a section's type match the query in the whole library. It can be larger than the number the section shows. |
| Format badge | A small label on a book row naming a way the book can be read: an ebook format, audio, or print. |
| Facet list | The list of books filed under one tag ("Books tagged") or one genre ("Books in genre"). |
| See all list | The list of every match in one section, opened from that section's "See all" link. |

---

# Part 1: Product

## Screens

### Search tab, before typing

| Dark | Light |
|---|---|
| <img src="images/search/prompt-dark.png" width="300" alt="Search tab before typing, dark"> | <img src="images/search/prompt-light.png" width="300" alt="Search tab before typing, light"> |

- The masthead ("Search") sits above the field, as on the other tab roots.
- The field is not focused when the tab opens.

### Results

| Typing, dark | Typing, light |
|---|---|
| <img src="images/search/results-typing-dark.png" width="300" alt="Results while typing, dark"> | <img src="images/search/results-typing-light.png" width="300" alt="Results while typing, light"> |

| Scrolled, dark | Scrolled, light |
|---|---|
| <img src="images/search/results-scrolled-dark.png" width="300" alt="Results scrolled, dark"> | <img src="images/search/results-scrolled-light.png" width="300" alt="Results scrolled, light"> |

- The masthead scrolls away with the results, and the field stays pinned at the top.
- Sections appear in a fixed order: Books, Authors, Series, Tags, Genres. A section with no matches is not shown.
- "See all N" appears in a section's header only when its total is larger than the number of rows it shows.

### Result rows

| Dark | Light |
|---|---|
| <img src="images/search/rows-dark.png" width="300" alt="Each kind of result row, dark"> | <img src="images/search/rows-light.png" width="300" alt="Each kind of result row, light"> |

| Row | Leading | First line | Second line | Third line |
|---|---|---|---|---|
| Book | Cover | Title | Author · year | Format badges |
| Author | Initials avatar | Name | "N books · incl. ‹lead title›" | None |
| Series | Series glyph tile | Name | "‹author› · N books" | None |
| Tag | Tag glyph tile | Name | "N books" | None |
| Genre | Genre glyph tile | Name | "N books" | None |

- A book with no cover gets the app's generated cover plate.
- Format badges, in this order, each shown at most once:
  - EBOOK when the book has any ebook format;
  - AUDIO when the book has any audio format;
  - PRINT when the reader's library has a physical copy.
- A missing year drops the " · year". A missing author drops "‹author› · ". A missing lead title drops " · incl. ‹lead title›".
- "1 book" is singular.

### Facet list

| Dark | Light |
|---|---|
| <img src="images/search/tag-books-dark.png" width="300" alt="Books tagged Woodland, dark"> | <img src="images/search/tag-books-light.png" width="300" alt="Books tagged Woodland, light"> |

- The title is the tag or genre name. The line under it gives the count ("14 books tagged "Woodland"" or "14 books in "Fantasy"").
- Rows are book rows, as in the results, with no highlighting.

### See all lists

| Books, dark | Capped, dark |
|---|---|
| <img src="images/search/see-all-books-dark.png" width="300" alt="See all books, dark"> | <img src="images/search/see-all-capped-dark.png" width="300" alt="See all tags past the 500 cap, dark"> |

| Books, light | Capped, light |
|---|---|
| <img src="images/search/see-all-books-light.png" width="300" alt="See all books, light"> | <img src="images/search/see-all-capped-light.png" width="300" alt="See all tags past the 500 cap, light"> |

- Each list uses the same rows as its section, with the query highlighted.
- The title names the section and the query: "Books matching "wood"", "Authors matching "wood"", and so on.

### Wide windows

| Dark | Light |
|---|---|
| <img src="images/search/wide-dark.png" width="420" alt="Search on a wide window, dark"> | <img src="images/search/wide-light.png" width="420" alt="Search on a wide window, light"> |

- From 600dp the app shows its navigation rail. The Search content is one column, at most 560dp wide, centred in the space beside the rail.
- Opening a result pushes its page across the full content area, as on a phone.

### Errors

| No matches | Can't reach server | Can't reach server, with results |
|---|---|---|
| <img src="images/search/no-matches-dark.png" width="240" alt="No matches, dark"> | <img src="images/search/unreachable-dark.png" width="240" alt="Can't reach your server, dark"> | <img src="images/search/unreachable-with-results-dark.png" width="240" alt="Results kept with a snackbar, dark"> |
| <img src="images/search/no-matches-light.png" width="240" alt="No matches, light"> | <img src="images/search/unreachable-light.png" width="240" alt="Can't reach your server, light"> | <img src="images/search/unreachable-with-results-light.png" width="240" alt="Results kept with a snackbar, light"> |

## States and copy

### Search tab

| State | When | Copy | Action |
|---|---|---|---|
| Prompt | The query is empty. | Placeholder: "Search your library". Body: "Search your library for books, authors, series, tags and genres." | None |
| Searching | A search is running and no results are on screen. | "Searching" | None |
| Results | The latest search matched at least one item. | Section labels: "Books", "Authors", "Series", "Tags", "Genres". Link: "See all N". | Tap a row or "See all N". |
| No matches | The latest search matched nothing. | Title: "No matches". Body: "Nothing in your library matches "‹query›"." | None |
| Can't reach server | A search failed because the server couldn't be reached, and no results are on screen. | Title: "Can't reach your server". Body: "Check your connection, then try again." Button: "Try again". | "Try again" repeats the search. |
| Can't reach server, with results | A search failed because the server couldn't be reached, and earlier results are on screen. | Snackbar: "Can't reach your server". Action: "Retry". | "Retry" repeats the search. |
| Something went wrong | A search failed for any other reason (an error status, a response the app can't read), and no results are on screen. | Title: "Search isn't working". Body: "Your server couldn't run that search. Try again in a moment." Button: "Try again". | "Try again" repeats the search. |
| Something went wrong, with results | As above, with earlier results on screen. | Snackbar: "Search isn't working". Action: "Retry". | "Retry" repeats the search. |
| Session expired | The server answered that the session has expired. | The app's existing session-expired handling. | The reader signs in again. |

### Facet list and See all lists

| State | When | Copy | Action |
|---|---|---|---|
| Loading | The first page is loading. | "Loading" | None |
| Empty facet | A tag or genre has no books. | Title: "No books here". Body: "No books are tagged "‹name›"." or "No books are in "‹name›"." | None |
| Empty See all | The section no longer has matches. | Title: "No matches". Body: "Nothing in your library matches "‹query›"." | None |
| Capped | A See all list's total is above 500. | Count line: "Showing the first 500 of N. Narrow your search to see the rest." | None |
| Load failed | The first page failed. | The tab's "Can't reach your server" or "Search isn't working" state, matching the cause. | "Try again" reloads. |
| Next page failed | A later page of a facet list failed. | A row at the end of the list: "Couldn't load more books." Button: "Try again". | "Try again" loads that page again. |

## Requirements

### Field

- The field MUST NOT take focus when the tab opens. Tapping it MUST focus it and show the keyboard.
- The field MUST show a clear button whenever it holds text. Clearing MUST return the tab to the Prompt state.
- The keyboard's action key MUST be a search key, and pressing it MUST only hide the keyboard.
- Scrolling the results MUST hide the keyboard.
- Autocorrect and auto-capitalisation MUST be off.
- Once the masthead has scrolled away, the field MUST stay pinned at the top of the tab.

### Searching as you type

- A search MUST start 200ms after the last change to the query. A change within those 200ms MUST restart the wait and cancel any search still running.
- A query MUST have at least one character other than a space. Leading and trailing spaces MUST be ignored.
- Results from an earlier query MUST stay on screen until the newer search answers. The Searching state MUST show only when nothing is on screen.
- An answer to an older query MUST NOT replace the answer to a newer one.

### Results

- Sections MUST appear in the order Books, Authors, Series, Tags, Genres. A section with no matches MUST NOT appear.
- Each section MUST show at most 5 rows.
- A section MUST show "See all N", where N is its total, when its total is larger than the rows it shows, and MUST NOT show it otherwise.
- Books MUST be in the server's relevance order: a match in the title ranks above one in the author, which ranks above one in the series. Only books are ranked this way, because the server ranks only books by relevance.
- Authors, series, tags and genres MUST be in the server's order: most books first, then by name.
- A book MUST match only when every word of the query appears in its title, authors or series. The last word MAY match the start of a longer word, so "wood" finds "Woodcutter".
- Authors, series, tags and genres MUST match when the query appears anywhere in the name.
- Every part of a row's first line that matches a word of the query MUST be highlighted, ignoring case. Highlighting MUST use both weight and colour, and the highlighted text MUST meet a contrast ratio of 4.5:1 against the row's background in both themes.
- Rows MUST show the content set out in [Result rows](#result-rows).

### Opening results

- Tapping a book MUST open its Book Details page.
- Tapping an author MUST open that author's page. Tapping a series MUST open that series' page.
- Tapping a tag MUST open the "Books tagged" facet list for it. Tapping a genre MUST open the "Books in genre" facet list for it.
- Tapping "See all N" MUST open that section's See all list.
- Every page opened from Search MUST open within the Search tab, so Back returns to the results with the query and scroll position unchanged.
- Tapping a tag or a genre on a Book Details page MUST open the same facet list as tapping it in Search.

### Facet lists

- A facet list MUST show every book in the library filed under exactly that tag or genre, ignoring case.
- Books MUST be ordered by title, using the library's title sort, which ignores leading articles. The list MUST have no sort or filter controls.
- The list MUST load in pages as the reader scrolls, with no visible end until the last book.
- The count line MUST show the total number of books.

### See all lists

- A See all list MUST show every match in its section, up to 500, in the same order as the section.
- When the total is above 500, the list MUST show the first 500 and the Capped count line.

### Tab behaviour

- Selecting the Search tab while it is already selected MUST return it to its root and clear the query.
- Switching to another tab and back MUST keep the query, the results and the scroll position.

### States

- The tab MUST show each state in [States and copy](#states-and-copy) when its condition holds, with its exact copy.
- A failed search MUST NOT remove results already on screen. It MUST show the matching "with results" snackbar instead.
- The no-matches and error states MUST keep the query in the field, so the reader can change it.

### Wide windows

- From 600dp, the Search content MUST be one column, at most 560dp wide, centred beside the navigation rail.
- Opening a result MUST push its page over the whole content area beside the rail.

### Accessibility

- The field MUST be announced as "Search your library", with its current text.
- Each row MUST be one focusable element that announces its type and its content, for example "Book, Mythago Wood, Robert Holdstock, 1984, ebook, print".
- Section headers MUST be headings. "See all N" MUST announce as "See all N ‹section›", for example "See all 9 books".
- Every touch target MUST be at least 48dp.
- Rows MUST grow to fit their text at the largest font scale instead of clipping it. The first and second lines MAY end in an ellipsis.
- Highlighting MUST NOT be the only way a match is shown to TalkBack; rows are announced in full.

---

# Part 2: Engineering

## Server contract

### `GET api/search/palette`

Searches every result type at once. Used for the Search tab and for the See all lists.

| Parameter | Value |
|---|---|
| `q` | The query. |
| `limit` | Rows per section. Omitted for the Search tab, so the server sends its default of 5. For a See all list, the section's total, at most 500. |

Response fields read:

| Field | Meaning |
|---|---|
| `books[]` | `uuid`, `title`, `author_display`, `year` (nullable), `formats[]` (lower-case format names), `cover_url` (nullable), `accent` (nullable), `has_physical`. |
| `authors[]` | `id`, `name`, `book_count`, `lead_book_title` (nullable). |
| `series[]` | `id`, `name`, `book_count`, `author_display` (nullable). |
| `tags[]` | `name`, `book_count`. |
| `genres[]` | `name`, `book_count`. Genres have no id. |
| `book_total`, `author_total`, `series_total`, `tag_total`, `genre_total` | Each section's total. A missing total falls back to the length of its list. |

The server matches books with a full-text index weighted title 10, authors 4, series 3, requiring every word and treating the last as a prefix. It matches authors, series, tags and genres by substring and orders them by book count, then name. It answers an empty query with empty lists.

### `GET api/ebooks`

Lists one page of books. Used for facet lists.

| Parameter | Value |
|---|---|
| `filter` | One clause: `[{"field":"tag","mode":"include","values":["‹name›"]}]`, or `"field":"genre"` for a genre. The server matches the name exactly, ignoring case. |
| `sort`, `dir` | `title`, `asc`. |
| `limit` | The page size. |
| `cursor` | The previous page's `X-Next-Cursor`; omitted for the first page. |

The response body is the book list the Library already reads. Headers read: `X-Total-Count` (the count line) and `X-Next-Cursor` (absent on the last page).

### Errors

Both endpoints are handled the same way:

| Response | Result |
|---|---|
| No server saved | Session expired. |
| Connection failure | Can't reach server. |
| 401 | Session expired. |
| 429, other error status, or a body that can't be read | Something went wrong. |

## Architecture

The feature follows the layers in `docs/engineering/feature-layers.md`.

- **Data.**
  - `LibrarySearchRepository` gains a search returning every section with its totals, taking an optional per-section limit. The existing books-only search stays for the scan flow, and both share one decoder.
  - `BooksPageQuery` gains an optional tag or genre clause, which `DefaultLibraryRepository` sends as `filter`. Facet lists then reuse the Library's existing paging.
- **Domain models.** One result type per row kind (book, author, series, tag, genre). Each section is a list plus its total. Format badges come from `formats` and `has_physical`: EBOOK and AUDIO use the ebook and audio format sets `Book` already defines.
- **UI.**
  - `SearchScreen` and `SearchViewModel` replace the under-construction screen at `SearchRoot`.
  - The ViewModel owns the query, the 200ms debounce, cancelling superseded searches, and keeping the last results through a failure.
  - Highlighting is a pure function from the query and a string to an annotated string.
- **Theme.** The palette gains an `accentText` colour for accent-coloured text: the accent itself in the dark theme, and `#9A5A12` in the light theme, where the accent is too light to read on the background. Highlights and "See all" use it.
- **Routes.**
  - `FacetBooksRoute(kind, name)` serves both facet lists. `BookDetailDestination.Tag` and `BookDetailDestination.Genre` map to it instead of the placeholder.
  - `SearchSeeAllRoute(section, query)` serves the See all lists.
  - Author and series rows dispatch `BookDetailDestination.Author` and `BookDetailDestination.Series`, so they open whatever page those destinations open.
  - Every route is pushed onto the Search tab's stack.
- **Tab re-select.** `ShellNavigator.select` already pops a re-selected tab to its root. The Search root also clears its query when that happens.
- **Reuse.**
  - `Masthead` for the masthead.
  - `BookCover` with `CoverIdentity` for covers and the generated plate.
  - `LoadingStateView`, `EmptyStateView` and `ErrorStateView` for full-screen states.
  - The navigation rail's existing 600dp breakpoint for wide windows.

## Constants

| Name | Value | Meaning |
|---|---|---|
| Debounce | 200ms | Wait after the last change to the query before searching. |
| Minimum query | 1 character | Shortest query, ignoring spaces, that searches. |
| Section rows | 5 | Rows per section on the Search tab; the server's default `limit`. |
| See all cap | 500 | Most rows a See all list shows; the server's maximum `limit`. |
| Facet page size | 60 | Books per facet-list page; the Library's page size. |
| Content width | 560dp | Widest the Search content grows on wide windows. |
| Wide breakpoint | 600dp | Window width from which the rail and the capped column apply. |

---

## Changelog

**1.0.0** (2026-11-25)

- Initial spec.
