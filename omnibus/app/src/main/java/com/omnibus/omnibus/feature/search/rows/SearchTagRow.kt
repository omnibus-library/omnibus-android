package com.omnibus.omnibus.feature.search.rows

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.search.TagMatch
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.search.SearchOpen
import com.omnibus.omnibus.feature.search.highlight.highlightMatches
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** [match] as a Search row with [query]'s words highlighted. Tapping it opens the tag's "Books tagged" list. */
@Composable
internal fun SearchTagRow(
    match: TagMatch,
    query: String,
    onOpen: (SearchOpen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val count = bookCountLabel(match.bookCount)
    SearchGlyphRow(
        glyph = R.drawable.ic_search_tag,
        title = highlightMatches(query, match.name, SpanStyle(color = palette.accentText, fontWeight = FontWeight.Bold)),
        subtitle = count,
        description = rowDescription(listOf(stringResource(R.string.search_row_tag), match.name, count)),
        onClick = { onOpen(SearchOpen.Destination(BookDetailDestination.Tag(match.name))) },
        modifier = modifier,
    )
}
