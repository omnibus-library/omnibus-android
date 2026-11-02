package com.omnibus.omnibus.feature.bookdetail.stage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** A series line: the series name and its position, when the book has one. */
data class SeriesLine(val name: String, val index: String?)

/** The lines under the title. [year] is null whenever [series] is shown. */
data class BylineModel(
    val author: String,
    val authorLink: BookDetailDestination.Author?,
    val series: SeriesLine?,
    val year: Int?,
)

/** The byline for [book]: the author (linked when possible), then the series or the year. */
internal fun bylineModel(book: Book): BylineModel {
    val series = book.series?.takeIf { it.isNotBlank() }?.let {
        SeriesLine(it.trim(), book.seriesIndex?.takeIf { index -> index.isNotBlank() }?.trim())
    }
    val link = book.creators.firstNotNullOfOrNull { creator ->
        creator.id?.takeIf { creator.name.isNotBlank() }?.let { BookDetailDestination.Author(it, creator.name.trim()) }
    }
    return BylineModel(
        author = book.authorDisplay,
        authorLink = link,
        series = series,
        year = if (series == null) book.year else null,
    )
}

/** Whether the year fits on the author's line: both widths and the gap within [maxWidthPx]. */
internal fun yearFitsBeside(authorWidthPx: Int, yearWidthPx: Int, gapPx: Int, maxWidthPx: Int): Boolean =
    authorWidthPx + gapPx + yearWidthPx <= maxWidthPx

/**
 * The author, the year (beside it when both fit, else under it) or the series line, centred under the title.
 *
 * @param onNavigate Receives the author destination when the author is linked.
 */
@Composable
internal fun BookByline(model: BylineModel, onNavigate: (BookDetailDestination) -> Unit, modifier: Modifier = Modifier) {
    val style = uiTextStyle(16, FontWeight.Medium, OmnibusTheme.palette.ink1).copy(textAlign = TextAlign.Center)
    val series = model.series
    val year = model.year
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            series != null -> {
                AuthorText(model.author, model.authorLink, style, onNavigate)
                SeriesText(series, style)
            }
            year != null -> AuthorWithYear(model.author, model.authorLink, year, style, onNavigate)
            else -> AuthorText(model.author, model.authorLink, style, onNavigate)
        }
    }
}

/** The author, a button when [link] is set, otherwise plain text. */
@Composable
private fun AuthorText(
    author: String,
    link: BookDetailDestination.Author?,
    style: TextStyle,
    onNavigate: (BookDetailDestination) -> Unit,
) {
    val description = stringResource(R.string.book_detail_author_description, author)
    val clickLabel = stringResource(R.string.book_detail_author_click_label)
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .then(
                if (link != null) {
                    Modifier.clickable(onClickLabel = clickLabel, role = Role.Button) { onNavigate(link) }
                } else {
                    Modifier
                },
            )
            .testTag(BookDetailTestTags.AUTHOR)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = author, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The author with the year beside it when both fit on one line, else the year on its own line under it. */
@Composable
private fun AuthorWithYear(
    author: String,
    link: BookDetailDestination.Author?,
    year: Int,
    style: TextStyle,
    onNavigate: (BookDetailDestination) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val gapPx = with(LocalDensity.current) { GAP.roundToPx() }
    val yearBeside = stringResource(R.string.book_detail_year_beside, year)
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val authorWidth: Int = measurer.measureWidth(author, style)
        val yearWidth: Int = measurer.measureWidth(yearBeside, style)
        if (yearFitsBeside(authorWidth, yearWidth, gapPx, constraints.maxWidth)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AuthorText(author, link, style, onNavigate)
                Spacer(Modifier.width(GAP))
                Text(
                    text = yearBeside,
                    style = style,
                    maxLines = 1,
                    modifier = Modifier.testTag(BookDetailTestTags.YEAR),
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AuthorText(author, link, style, onNavigate)
                Text(
                    text = year.toString(),
                    style = style,
                    modifier = Modifier.heightIn(min = 40.dp).testTag(BookDetailTestTags.YEAR),
                )
            }
        }
    }
}

/** The series line in place of the year. Plain text: #104 makes it a link. */
@Composable
private fun SeriesText(series: SeriesLine, style: TextStyle) {
    val line = if (series.index != null) {
        stringResource(R.string.book_detail_series_line, series.name, series.index)
    } else {
        series.name
    }
    val description = stringResource(R.string.book_detail_series_description, line)
    Text(
        text = line,
        style = style,
        maxLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .testTag(BookDetailTestTags.SERIES)
            .semantics { contentDescription = description },
    )
}

/** The intrinsic width of [text] on one line, in pixels. */
private fun androidx.compose.ui.text.TextMeasurer.measureWidth(text: String, style: TextStyle): Int {
    val result: TextLayoutResult = measure(text = text, style = style, maxLines = 1, softWrap = false)
    return result.size.width
}

/** The gap between the author and the year when they share a line. */
private val GAP = 6.dp
