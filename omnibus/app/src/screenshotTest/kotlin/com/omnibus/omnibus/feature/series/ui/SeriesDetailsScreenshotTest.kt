package com.omnibus.omnibus.feature.series.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.series.SeriesDetailsScreenState
import com.omnibus.omnibus.feature.series.SeriesDetailsUIState
import com.omnibus.omnibus.feature.series.SeriesLoadFailure
import com.omnibus.omnibus.screenshot.Shot

private const val SERIES = "The Harrowgate Almanac"

/** A book of the Harrowgate series with fixed values, so the references never change with the random fixtures. */
private fun almanacBook(
    id: Long,
    title: String,
    seriesIndex: String?,
    authors: List<String> = listOf("Robert Holdstock"),
    formats: List<String> = listOf("epub"),
    hasPhysical: Boolean = false,
    coverUrl: String? = null,
): Book = Book(
    id = id,
    uniqueIdentifier = "almanac-$id",
    filename = "almanac-$id.epub",
    title = title,
    creators = authors.map { Creator(name = it, role = "aut", fileAs = null, id = null) },
    coverUrl = coverUrl,
    accent = null,
    formats = formats,
    series = SERIES,
    seriesIndex = seriesIndex,
    hasPhysical = hasPhysical,
    addedAt = null,
    lastInteractedAt = null,
    bookFiles = emptyList(),
)

/** The page with books 1, 3, 10 and one unnumbered book, so it shows a single placeholder, a range and every badge. */
private fun loadedState(): SeriesDetailsUIState = SeriesDetailsUIState(
    seriesId = 1L,
    name = SERIES,
    books = listOf(
        almanacBook(id = 1, title = "Lavondyss", seriesIndex = "1", formats = listOf("epub", "m4b")),
        almanacBook(
            id = 3,
            title = "The Ballad of the Long Road Home and the Lanterns That Lit It",
            seriesIndex = "3",
            authors = listOf("Robert Holdstock", "Ann Example", "Sam Placeholder", "Pat Sample"),
            formats = listOf("m4b"),
        ),
        almanacBook(id = 10, title = "Ten", seriesIndex = "10", formats = listOf("epub"), coverUrl = null),
        almanacBook(
            id = 11,
            title = "A Story Without A Number That Runs Long Enough To Wrap Onto A Second Line And Ellipsize",
            seriesIndex = null,
            formats = listOf("pdf"),
            hasPhysical = true,
        ),
    ),
)

@PreviewTest
@PreviewLightDark
@Composable
fun SeriesDetailsLoaded() {
    Shot {
        SeriesDetailsContent(
            uiState = loadedState(),
            onBack = {},
            onOpenBook = {},
            onRefresh = {},
            onRetry = {},
            onRefreshFailureShown = {},
            windowWidth = 390.dp,
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun SeriesDetailsEmpty() {
    Shot {
        SeriesDetailsContent(
            uiState = SeriesDetailsUIState(seriesId = 1L, name = SERIES, books = emptyList()),
            onBack = {},
            onOpenBook = {},
            onRefresh = {},
            onRetry = {},
            onRefreshFailureShown = {},
            windowWidth = 390.dp,
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun SeriesDetailsUnreachable() {
    Shot {
        SeriesDetailsContent(
            uiState = SeriesDetailsUIState(seriesId = 1L, name = SERIES, loadFailure = SeriesLoadFailure.Unreachable),
            onBack = {},
            onOpenBook = {},
            onRefresh = {},
            onRetry = {},
            onRefreshFailureShown = {},
            windowWidth = 390.dp,
        )
    }
}

@PreviewTest
@Preview(name = "Wide", widthDp = 840, heightDp = 1100)
@Composable
fun SeriesDetailsWide() {
    Shot(darkTheme = false) {
        SeriesDetailsContent(
            uiState = loadedState(),
            onBack = {},
            onOpenBook = {},
            onRefresh = {},
            onRetry = {},
            onRefreshFailureShown = {},
            windowWidth = 840.dp,
        )
    }
}

@PreviewTest
@Preview(name = "Wide dark", widthDp = 840, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SeriesDetailsWideDark() {
    Shot(darkTheme = true) {
        SeriesDetailsContent(
            uiState = loadedState(),
            onBack = {},
            onOpenBook = {},
            onRefresh = {},
            onRetry = {},
            onRefreshFailureShown = {},
            windowWidth = 840.dp,
        )
    }
}

@PreviewTest
@Preview(name = "Largest font", widthDp = 390, heightDp = 1100, fontScale = 2f)
@Composable
fun SeriesDetailsLargestFont() {
    Shot(darkTheme = false) {
        SeriesDetailsContent(
            uiState = loadedState(),
            onBack = {},
            onOpenBook = {},
            onRefresh = {},
            onRetry = {},
            onRefreshFailureShown = {},
            windowWidth = 390.dp,
        )
    }
}

@PreviewTest
@Preview(
    name = "Largest font dark",
    widthDp = 390,
    heightDp = 1100,
    fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun SeriesDetailsLargestFontDark() {
    Shot(darkTheme = true) {
        SeriesDetailsContent(
            uiState = loadedState(),
            onBack = {},
            onOpenBook = {},
            onRefresh = {},
            onRetry = {},
            onRefreshFailureShown = {},
            windowWidth = 390.dp,
        )
    }
}
