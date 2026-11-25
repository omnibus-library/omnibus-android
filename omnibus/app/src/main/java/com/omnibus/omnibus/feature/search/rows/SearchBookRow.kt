package com.omnibus.omnibus.feature.search.rows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.data.search.LibraryHit
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.search.SearchOpen
import com.omnibus.omnibus.feature.search.highlight.highlightMatches
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.components.coverShadow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.util.Locale

/**
 * One book in a list: its cover, [title] (highlighted when it matches a search), [byline] and [badges]. Its spoken text is
 * [description], one focusable element that opens the book on [onClick].
 */
@Composable
fun SearchBookRow(
    title: AnnotatedString,
    byline: String,
    badges: List<FormatBadge>,
    coverIdentity: CoverIdentity,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = Spacing.screen, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(
            identity = coverIdentity,
            size = CoverSize.Sm,
            cornerRadius = 3.dp,
            modifier = Modifier.width(38.dp).coverShadow(3.dp, strength = 0.5f),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = uiTextStyle(17, FontWeight.SemiBold, palette.ink0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (byline.isNotEmpty()) {
                Text(
                    byline,
                    style = uiTextStyle(15, FontWeight.Normal, palette.ink2),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                FormatBadges(badges)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            painterResource(R.drawable.ic_reader_chevron_right),
            null,
            tint = palette.ink2,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** [hit] as a Search row with [query]'s words highlighted. Tapping it opens the book on the asking tab's stack. */
@Composable
internal fun LibraryHitRow(
    hit: LibraryHit,
    query: String,
    onOpen: (SearchOpen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val badges = formatBadges(hit.formats, hit.hasPhysical)
    val badgeWords = badges.map { stringResource(it.label).lowercase(Locale.ROOT) }
    val kind = stringResource(R.string.search_row_book)
    SearchBookRow(
        title = highlightMatches(query, hit.title, SpanStyle(color = palette.accentText, fontWeight = FontWeight.Bold)),
        byline = bookByline(hit.authorDisplay, hit.year),
        badges = badges,
        coverIdentity = hit.coverIdentity(),
        description = rowDescription(listOf(kind, hit.title, hit.authorDisplay, hit.year) + badgeWords),
        onClick = { onOpen(SearchOpen.Destination(BookDetailDestination.Book(hit.uuid))) },
        modifier = modifier,
    )
}

/** The author and year line: the non-blank parts joined with " · ", so a missing part drops out with its separator. */
internal fun bookByline(author: String, year: String?): String =
    listOf(author, year.orEmpty()).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")

/** A spoken row description: the non-blank [parts] joined with ", ". */
internal fun rowDescription(parts: List<String?>): String =
    parts.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.joinToString(", ")

/** The cover to draw for this hit: its accent, or a plate from its title when it has no art. */
internal fun LibraryHit.coverIdentity(): CoverIdentity = CoverIdentity(
    uuid = uuid,
    title = title,
    creator = authorDisplay.ifBlank { null },
    accent = accent,
    hasCover = coverUrl != null,
)
