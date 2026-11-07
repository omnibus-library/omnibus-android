package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.LibraryCategory
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.feature.library.ContinueCard
import com.omnibus.omnibus.feature.library.LibraryUIState
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

/** The screen for [uiState]. Every Continue card counts as drawn in, so its bar shows at full length, not mid-animation. */
@Composable
private fun LibraryShot(uiState: LibraryUIState) {
    val pagerState = rememberPagerState(pageCount = { uiState.continueCards.size })
    val continueState = remember {
        ContinueCarouselState(pagerState, uiState.continueCards.map { it.id }.toMutableStateList())
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
            bottomInset = 0.dp,
            continueState = continueState,
        )
    }
}
