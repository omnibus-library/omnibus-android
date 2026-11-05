package com.omnibus.omnibus.feature.bookdetail.stage

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.bookdetail.overview.series.SeriesLineState
import com.omnibus.omnibus.feature.bookdetail.shotBook
import com.omnibus.omnibus.screenshot.Shot

@PreviewTest
@PreviewLightDark
@Composable
fun BookBylineLinkedAuthorAndYear() {
    BylineShot(creatorId = 7L, publishedDate = "1969")
}

@PreviewTest
@PreviewLightDark
@Composable
fun BookBylineUnlinkedAuthor() {
    BylineShot(creatorId = null)
}

/** The series line takes the year's place, as plain text when the book has no series section. */
@PreviewTest
@PreviewLightDark
@Composable
fun BookBylineSeriesReplacesYear() {
    BylineShot(creatorId = null, series = "Dune", seriesIndex = "3", publishedDate = "1965", author = "Frank Herbert")
}

/** With a series section to scroll to, the line is an underlined link with the series total. */
@PreviewTest
@PreviewLightDark
@Composable
fun BookBylineSeriesLink() {
    BylineShot(
        creatorId = null,
        series = "Dune",
        seriesIndex = "3",
        author = "Frank Herbert",
        seriesLine = SeriesLineState(total = 6, onClick = {}),
    )
}

@Composable
private fun BylineShot(
    creatorId: Long?,
    publishedDate: String? = null,
    series: String? = null,
    seriesIndex: String? = null,
    author: String = "Ursula K. Le Guin",
    seriesLine: SeriesLineState? = null,
) {
    val book = shotBook(
        creators = listOf(Creator(author, "author", null, creatorId)),
        series = series,
        seriesIndex = seriesIndex,
        publishedDate = publishedDate,
    )
    Shot {
        BookByline(bylineModel(book), onNavigate = {}, modifier = Modifier.fillMaxWidth(), seriesLine = seriesLine)
    }
}
