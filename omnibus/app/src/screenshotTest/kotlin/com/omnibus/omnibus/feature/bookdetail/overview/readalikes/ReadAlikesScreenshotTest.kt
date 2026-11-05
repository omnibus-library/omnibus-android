package com.omnibus.omnibus.feature.bookdetail.overview.readalikes

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.suggestions.BookSuggestion
import com.omnibus.omnibus.screenshot.Shot

@PreviewTest
@PreviewLightDark
@Composable
fun ReadAlikesReady() {
    ReadAlikesShot(ReadAlikesUiState.Ready(readAlikesOf((1L..3L).map { suggestion(it) })))
}

/** A blank author draws no author line, and a cover path with no fetcher still draws the plate. */
@PreviewTest
@PreviewLightDark
@Composable
fun ReadAlikesNoAuthorAndUnfetchedCover() {
    ReadAlikesShot(
        ReadAlikesUiState.Ready(
            readAlikesOf(listOf(suggestion(1, author = ""), suggestion(2, coverPath = "/api/suggestions/u/2/cover"))),
        ),
    )
}

/** The static three-quarter ring that reduced motion draws, so the shot has no spin in flight. */
@PreviewTest
@PreviewLightDark
@Composable
fun ReadAlikesSearching() {
    ReadAlikesShot(ReadAlikesUiState.Searching)
}

@PreviewTest
@PreviewLightDark
@Composable
fun ReadAlikesUnavailable() {
    ReadAlikesShot(ReadAlikesUiState.Unavailable)
}

private fun suggestion(id: Long, author: String = "Author $id", coverPath: String? = null) =
    BookSuggestion(id, "https://hardcover.app/books/b$id", "Book $id", author, coverPath)

@Composable
private fun ReadAlikesShot(state: ReadAlikesUiState) {
    Shot {
        ReadAlikes(bookTitle = "The Left Hand of Darkness", state = state, onOpen = {}, reducedMotion = true)
    }
}
