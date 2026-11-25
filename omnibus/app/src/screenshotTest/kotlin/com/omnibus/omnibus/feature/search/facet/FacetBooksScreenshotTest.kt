package com.omnibus.omnibus.feature.search.facet

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.FacetKind
import com.omnibus.omnibus.feature.search.SearchFailure
import com.omnibus.omnibus.screenshot.Shot

private val woodlandRoute = FacetBooksRoute(FacetKind.Tag, "Woodland")

/** A book with no cover, so each row draws its generated plate. */
private fun shelfBook(id: Long, title: String, author: String, formats: List<String>, hasPhysical: Boolean = false) = Book(
    id = id,
    uniqueIdentifier = "u-$id",
    filename = "f$id",
    title = title,
    creators = listOf(Creator(author, "author", null, null)),
    coverUrl = null,
    accent = null,
    formats = formats,
    series = null,
    seriesIndex = null,
    hasPhysical = hasPhysical,
    addedAt = null,
    lastInteractedAt = null,
    bookFiles = emptyList(),
)

/** The books tagged "Woodland", in title order, as the design shows them. */
private val woodlandBooks = listOf(
    shelfBook(1, "The Wood Wife", "Terri Windling", listOf("epub")),
    shelfBook(2, "Mythago Wood", "Robert Holdstock", listOf("epub"), hasPhysical = true),
    shelfBook(3, "Uprooted", "Naomi Novik", listOf("epub", "m4b")),
    shelfBook(4, "The Bear and the Nightingale", "Katherine Arden", listOf("m4b")),
    shelfBook(5, "Deerskin", "Robin McKinley", listOf("epub")),
    shelfBook(6, "Spinning Silver", "Naomi Novik", listOf("epub", "m4b")),
    shelfBook(7, "The Hazel Wood", "Melissa Albert", listOf("epub")),
)

@PreviewTest
@PreviewLightDark
@Composable
fun FacetBooksLoaded() {
    Shot {
        FacetBooksScreenContent(
            route = woodlandRoute,
            uiState = FacetBooksUIState(FacetBody.Loaded(woodlandBooks, total = 14, nextCursor = "c", nextPage = NextPageState.Idle)),
            onBack = {},
            onRetry = {},
            onLoadMore = {},
            onRetryNextPage = {},
            onOpen = {},
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun FacetBooksEmpty() {
    Shot {
        FacetBooksScreenContent(
            route = woodlandRoute,
            uiState = FacetBooksUIState(FacetBody.Empty),
            onBack = {},
            onRetry = {},
            onLoadMore = {},
            onRetryNextPage = {},
            onOpen = {},
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun FacetBooksFailed() {
    Shot {
        FacetBooksScreenContent(
            route = woodlandRoute,
            uiState = FacetBooksUIState(FacetBody.Failed(SearchFailure.Unreachable)),
            onBack = {},
            onRetry = {},
            onLoadMore = {},
            onRetryNextPage = {},
            onOpen = {},
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun FacetBooksNextPageFailed() {
    Shot {
        FacetBooksScreenContent(
            route = woodlandRoute,
            uiState = FacetBooksUIState(
                FacetBody.Loaded(woodlandBooks.take(3), total = 14, nextCursor = "c", nextPage = NextPageState.Failed),
            ),
            onBack = {},
            onRetry = {},
            onLoadMore = {},
            onRetryNextPage = {},
            onOpen = {},
        )
    }
}
