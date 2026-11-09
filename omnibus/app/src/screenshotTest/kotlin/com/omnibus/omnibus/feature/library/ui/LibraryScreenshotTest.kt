package com.omnibus.omnibus.feature.library.ui

import android.content.res.Configuration
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.LibraryCategory
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.SeriesBooks
import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.feature.library.ContinueCard
import com.omnibus.omnibus.feature.library.LibraryUIState
import com.omnibus.omnibus.feature.library.continuePageCount
import com.omnibus.omnibus.feature.library.PositionLabel
import com.omnibus.omnibus.feature.library.RelativeAge
import com.omnibus.omnibus.screenshot.Shot

/**
 * The masthead, the heading and the grid under it, with the filter button. The third book has a blank title and no
 * creators, so its caption falls back to the filename and "Unknown author".
 */
@PreviewTest
@PreviewLightDark
@Composable
fun LibraryBooks() {
    LibraryShot(
        LibraryUIState(
            books = listOf(book(1L), book(2L), book(3L, title = "   ", author = null, filename = "fallback.epub"), book(4L)),
            hasLoaded = true,
        ),
    )
}

/** A reload keeps the old grid and the new heading, with no loading view. */
@PreviewTest
@PreviewLightDark
@Composable
fun LibraryReloading() {
    LibraryShot(
        LibraryUIState(books = listOf(book(1L), book(2L)), isInitialLoading = true, category = LibraryCategory.Audiobooks),
    )
}

/** A filter with no books keeps its heading and filter button, not the empty library view. */
@PreviewTest
@PreviewLightDark
@Composable
fun LibraryFilteredEmpty() {
    LibraryShot(LibraryUIState(hasLoaded = true, category = LibraryCategory.Ebooks))
}

/** The offline pill sits left of the add button, on its centre line. */
@PreviewTest
@PreviewLightDark
@Composable
fun LibraryOffline() {
    LibraryShot(LibraryUIState(books = listOf(book(1L), book(2L)), hasLoaded = true, isOffline = true))
}

/** The Continue carousel between the masthead and the heading, with its dots above the heading. */
@PreviewTest
@PreviewLightDark
@Composable
fun LibraryWithContinue() {
    val cards = listOf(continueCard(1L), continueCard(2L), continueCard(3L))
    LibraryShot(
        LibraryUIState(
            books = listOf(book(1L), book(2L)),
            hasLoaded = true,
            continueCards = cards,
            continueSelectedId = cards.first().id,
        ),
    )
}

/** A tablet in landscape: two Continue cards side by side above the grid. */
@PreviewTest
@Preview(name = "Light", widthDp = 1280, heightDp = 800)
@Preview(name = "Dark", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LibraryWithContinueWide() {
    val cards = (1L..5L).map { continueCard(it) }
    LibraryShot(
        LibraryUIState(
            books = (1L..9L).map { book(it) },
            hasLoaded = true,
            continueCards = cards,
            continueSelectedId = cards.first().id,
        ),
        cardsPerPage = 2,
    )
}

/**
 * The Shelves section between the Continue card and "All books": a card for each arrangement and kind, a plate for an
 * empty shelf, and another user's public shelf, which shows its owner.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun LibraryWithShelves() {
    val cards = listOf(continueCard(1L))
    LibraryShot(
        LibraryUIState(
            books = listOf(book(1L), book(2L)),
            hasLoaded = true,
            continueCards = cards,
            continueSelectedId = cards.first().id,
            shelves = listOf(
                shelf(1L, "Currently Reading", ShelfKind.Manual, ShelfVisibility.Private, bookCount = 12L, covers = 4),
                shelf(2L, "Sci-Fi Favourites", ShelfKind.Smart, ShelfVisibility.Private, bookCount = 34L, covers = 3),
                shelf(3L, "Book Club 2026", ShelfKind.Manual, ShelfVisibility.Public, bookCount = 8L, covers = 2),
                shelf(4L, "Hunter's Wishlist", ShelfKind.Wishlist, ShelfVisibility.Private, bookCount = 1L, covers = 1),
                shelf(5L, "Someday", ShelfKind.Manual, ShelfVisibility.Private, bookCount = 0L, covers = 0),
                shelf(6L, "Weekend Reads", ShelfKind.Manual, ShelfVisibility.Public, bookCount = 5L, covers = 2, ownerId = 8L, ownerName = "Ann"),
            ),
            viewerId = 7L,
        ),
    )
}

/** A shelf for the screenshots, with [covers] cover uuids that load nothing, so each cover draws as a plate. */
private fun shelf(
    id: Long,
    name: String,
    kind: ShelfKind,
    visibility: ShelfVisibility,
    bookCount: Long,
    covers: Int,
    ownerId: Long = 7L,
    ownerName: String = "Hunter",
) = ShelfPreview(
    shelf = Shelf(
        id = id,
        ownerUserId = ownerId,
        ownerUsername = ownerName,
        kind = kind,
        name = name,
        visibility = visibility,
        accent = null,
        bookCount = bookCount,
    ),
    coverUuids = List(covers) { "preview-$id-$it" },
)

/**
 * The grouped Library with the switch on: Dune Chronicles (three books, one loaded), The Expanse (two, one loaded)
 * and Solo (one book) fold into stacks among the single books. The series reads supply the books not loaded yet.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun LibraryGroupedFolded() {
    LibraryShot(
        LibraryUIState(
            books = listOf(
                book(1L),
                seriesBook(2L, seriesId = 10L, series = "Dune Chronicles", index = "1"),
                book(3L),
                seriesBook(4L, seriesId = 20L, series = "The Expanse", index = "1"),
                seriesBook(5L, seriesId = 30L, series = "Solo", index = "1"),
                book(6L),
            ),
            hasLoaded = true,
            groupBySeries = true,
            seriesBooks = mapOf(
                10L to SeriesBooks(
                    name = "Dune Chronicles",
                    books = listOf(
                        seriesBook(2L, seriesId = 10L, series = "Dune Chronicles", index = "1"),
                        seriesBook(7L, seriesId = 10L, series = "Dune Chronicles", index = "2"),
                        seriesBook(8L, seriesId = 10L, series = "Dune Chronicles", index = "3"),
                    ),
                ),
                20L to SeriesBooks(
                    name = "The Expanse",
                    books = listOf(
                        seriesBook(4L, seriesId = 20L, series = "The Expanse", index = "1"),
                        seriesBook(9L, seriesId = 20L, series = "The Expanse", index = "2"),
                    ),
                ),
                30L to SeriesBooks(
                    name = "Solo",
                    books = listOf(seriesBook(5L, seriesId = 30L, series = "Solo", index = "1")),
                ),
            ),
        ),
    )
}

/**
 * Two series unfolded at 2 columns, the Expanse folded between them: Dune Chronicles starts mid-row, so the outline is
 * a staircase, and Solo, a one-book series, sits in the corner of a two-cell run.
 */
@PreviewTest
@Preview(name = "Light", widthDp = 360, heightDp = 1800)
@Preview(name = "Dark", widthDp = 360, heightDp = 1800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LibraryGroupedUnfoldedNarrow() {
    LibraryShot(
        LibraryUIState(
            books = listOf(
                book(1L),
                seriesBook(2L, seriesId = 10L, series = "Dune Chronicles", index = "1"),
                seriesBook(4L, seriesId = 20L, series = "The Expanse", index = "1"),
                book(6L),
                seriesBook(5L, seriesId = 30L, series = "Solo", index = "1"),
            ),
            hasLoaded = true,
            groupBySeries = true,
            unfoldedSeries = setOf(10L, 30L),
            seriesBooks = mapOf(
                10L to SeriesBooks(
                    name = "Dune Chronicles",
                    books = listOf(
                        seriesBook(2L, seriesId = 10L, series = "Dune Chronicles", index = "1"),
                        seriesBook(7L, seriesId = 10L, series = "Dune Chronicles", index = "2"),
                        seriesBook(8L, seriesId = 10L, series = "Dune Chronicles", index = "3"),
                    ),
                ),
                20L to SeriesBooks(
                    name = "The Expanse",
                    books = listOf(
                        seriesBook(4L, seriesId = 20L, series = "The Expanse", index = "1"),
                        seriesBook(9L, seriesId = 20L, series = "The Expanse", index = "2"),
                    ),
                ),
                30L to SeriesBooks(
                    name = "Solo",
                    books = listOf(seriesBook(5L, seriesId = 30L, series = "Solo", index = "1")),
                ),
            ),
        ),
    )
}

/**
 * Six columns: The Expanse (four books) starts in the fifth column and wraps to the next row, so its outline is open at
 * the wrapped edges. Dune Chronicles (three books) fits one row, so its outline is a closed box.
 */
@PreviewTest
@Preview(name = "Light", widthDp = 840, heightDp = 1100)
@Preview(name = "Dark", widthDp = 840, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LibraryGroupedUnfoldedWide() {
    LibraryShot(
        LibraryUIState(
            books = listOf(
                book(1L),
                book(2L),
                book(3L),
                book(4L),
                seriesBook(11L, seriesId = 20L, series = "The Expanse", index = "1"),
                book(5L),
                book(6L),
                book(7L),
                seriesBook(15L, seriesId = 10L, series = "Dune Chronicles", index = "1"),
                book(8L),
                book(9L),
            ),
            hasLoaded = true,
            groupBySeries = true,
            unfoldedSeries = setOf(10L, 20L),
            seriesBooks = mapOf(
                10L to SeriesBooks(
                    name = "Dune Chronicles",
                    books = listOf(
                        seriesBook(15L, seriesId = 10L, series = "Dune Chronicles", index = "1"),
                        seriesBook(16L, seriesId = 10L, series = "Dune Chronicles", index = "2"),
                        seriesBook(17L, seriesId = 10L, series = "Dune Chronicles", index = "3"),
                    ),
                ),
                20L to SeriesBooks(
                    name = "The Expanse",
                    books = listOf(
                        seriesBook(11L, seriesId = 20L, series = "The Expanse", index = "1"),
                        seriesBook(12L, seriesId = 20L, series = "The Expanse", index = "2"),
                        seriesBook(13L, seriesId = 20L, series = "The Expanse", index = "3"),
                        seriesBook(14L, seriesId = 20L, series = "The Expanse", index = "4"),
                    ),
                ),
            ),
        ),
    )
}

/** A book with a uuid of `uuid-<id>` and no cover, so no image loads. */
private fun book(
    id: Long,
    title: String? = "Book $id",
    author: String? = "Author $id",
    filename: String = "book-$id.epub",
) = Book(
    id = id,
    uniqueIdentifier = "uuid-$id",
    filename = filename,
    title = title,
    creators = author?.let { listOf(Creator(it, null, null, null)) }.orEmpty(),
    coverUrl = null,
    accent = null,
    formats = listOf("epub"),
    series = null,
    seriesIndex = null,
    hasPhysical = false,
    addedAt = null,
    lastInteractedAt = null,
    bookFiles = emptyList(),
)

/** A book [id] at [index] of series [seriesId], named [series]. */
private fun seriesBook(id: Long, seriesId: Long, series: String, index: String) = book(id).copy(
    seriesId = seriesId,
    series = series,
    seriesIndex = index,
)

/** A reading card for book [id], with a half-read bar. */
private fun continueCard(id: Long) = ContinueCard(
    id = "c$id:epub",
    book = book(id),
    medium = ProgressFormat.Reading,
    fraction = 0.5,
    position = PositionLabel.PercentRead(50),
    lastOpened = RelativeAge.Hours(2),
    resumeFileId = null,
)

/**
 * The screen for [uiState], with its Continue cards grouped [cardsPerPage] to a page. Every Continue card counts as
 * drawn in, so its bar shows at full length, not mid-animation.
 */
@Composable
private fun LibraryShot(uiState: LibraryUIState, cardsPerPage: Int = 1) {
    val perPage = remember { mutableIntStateOf(cardsPerPage) }
    val pagerState = rememberPagerState { continuePageCount(uiState.continueCards.size, perPage.intValue) }
    val continueState = remember {
        ContinueCarouselState(pagerState, uiState.continueCards.map { it.id }.toMutableStateList(), perPage)
    }
    Shot {
        LibraryScreenContent(
            uiState = uiState,
            onRetry = {},
            onRefresh = {},
            onLoadMore = {},
            onOpenBook = {},
            onAddBooks = {},
            onTransientErrorShown = {},
            continueState = continueState,
        )
    }
}
