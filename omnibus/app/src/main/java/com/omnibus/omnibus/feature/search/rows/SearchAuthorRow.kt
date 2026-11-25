package com.omnibus.omnibus.feature.search.rows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.audiobook.playback.media3.ArtworkPlate
import com.omnibus.omnibus.data.search.AuthorMatch
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.search.SearchOpen
import com.omnibus.omnibus.feature.search.highlight.highlightMatches
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** [match] as a Search row with [query]'s words highlighted. Tapping it opens the author. */
@Composable
internal fun SearchAuthorRow(
    match: AuthorMatch,
    query: String,
    onOpen: (SearchOpen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val count = bookCountLabel(match.bookCount)
    val lead = match.leadBookTitle?.trim()?.takeIf { it.isNotEmpty() }
    SearchLeadingRow(
        leading = { InitialsAvatar(match.name) },
        title = highlightMatches(query, match.name, SpanStyle(color = palette.accentText, fontWeight = FontWeight.Bold)),
        subtitle = lead?.let { stringResource(R.string.search_author_count_lead, count, it) } ?: count,
        description = rowDescription(listOf(stringResource(R.string.search_row_author), match.name, count, lead)),
        onClick = { onOpen(SearchOpen.Destination(BookDetailDestination.Author(match.id, match.name))) },
        modifier = modifier,
    )
}

/** The round tile that stands in for an author's portrait: their [name]'s initials on a raised ground. */
@Composable
private fun InitialsAvatar(name: String, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    Box(
        modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(palette.bg2),
        contentAlignment = Alignment.Center,
    ) {
        Text(ArtworkPlate.initials(name), style = uiTextStyle(15, FontWeight.SemiBold, palette.ink2))
    }
}
