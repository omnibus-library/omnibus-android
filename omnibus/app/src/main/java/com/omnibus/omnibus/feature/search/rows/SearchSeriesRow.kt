package com.omnibus.omnibus.feature.search.rows

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.search.SeriesMatch
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.search.SearchOpen
import com.omnibus.omnibus.feature.search.highlight.highlightMatches
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** [match] as a Search row with [query]'s words highlighted. Tapping it opens the series. */
@Composable
internal fun SearchSeriesRow(
    match: SeriesMatch,
    query: String,
    onOpen: (SearchOpen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val count = bookCountLabel(match.bookCount)
    val author = match.authorDisplay?.trim()?.takeIf { it.isNotEmpty() }
    SearchGlyphRow(
        glyph = R.drawable.ic_search_series,
        title = highlightMatches(query, match.name, SpanStyle(color = palette.accentText, fontWeight = FontWeight.Bold)),
        subtitle = author?.let { stringResource(R.string.search_series_author_count, it, count) } ?: count,
        description = rowDescription(listOf(stringResource(R.string.search_row_series), match.name, author, count)),
        onClick = { onOpen(SearchOpen.Destination(BookDetailDestination.Series(match.id, match.name))) },
        modifier = modifier,
    )
}
