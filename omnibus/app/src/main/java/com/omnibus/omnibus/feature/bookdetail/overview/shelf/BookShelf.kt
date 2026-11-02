package com.omnibus.omnibus.feature.bookdetail.overview.shelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.components.coverShadow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The width of a shelf card and its cover. */
internal val ShelfCardWidth: Dp = 112.dp

/** The height of a shelf card's cover. */
internal val ShelfCoverHeight: Dp = 164.dp

/** The gutter the Overview panel puts on each side of its sections, which a shelf row runs out through. */
private val PanelGutter: Dp = 16.dp

private val ShelfCoverCorner: Dp = 4.dp
private val CardSpacing: Dp = 14.dp

/**
 * A sideways-scrolling row of [BookShelfCard]s that runs off both edges of the Overview panel, so the first card
 * lines up with the section's text and the last scrolls clear of the right edge. Fill [content] with `item`/`items`.
 */
@Composable
fun BookShelfRow(content: LazyListScope.() -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier
            .layout { measurable, constraints ->
                val bleed = PanelGutter.roundToPx()
                val widened = if (constraints.hasBoundedWidth) {
                    constraints.copy(
                        minWidth = constraints.minWidth + 2 * bleed,
                        maxWidth = constraints.maxWidth + 2 * bleed,
                    )
                } else {
                    constraints
                }
                val placeable = measurable.measure(widened)
                val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
                layout(width, placeable.height) {
                    placeable.place(-bleed, 0)
                }
            }
            .testTag(BookShelfTestTags.ROW),
        contentPadding = PaddingValues(horizontal = PanelGutter),
        horizontalArrangement = Arrangement.spacedBy(CardSpacing),
        content = content,
    )
}

/**
 * One book on a shelf: a 112 x 164 dp cover, its [title] on up to two lines, and a [subtitle] line (such as the year)
 * under it. The card is a single button for accessibility, announced as its title and subtitle; [onClickLabel] says
 * what activating it does.
 */
@Composable
fun BookShelfCard(
    identity: CoverIdentity,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    onClickLabel: String?,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Column(
        modifier
            .width(ShelfCardWidth)
            .clip(RoundedCornerShape(ShelfCoverCorner))
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .testTag(BookShelfTestTags.CARD),
    ) {
        BookCover(
            identity = identity,
            modifier = Modifier
                .size(width = ShelfCardWidth, height = ShelfCoverHeight)
                .coverShadow(ShelfCoverCorner),
            cornerRadius = ShelfCoverCorner,
            keepsAspect = false,
        )
        Text(
            text = title,
            style = uiTextStyle(14, FontWeight.SemiBold, palette.ink0),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 9.dp).testTag(BookShelfTestTags.TITLE),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = uiTextStyle(13, FontWeight.Normal, palette.ink1),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp).testTag(BookShelfTestTags.SUBTITLE),
            )
        }
    }
}

/** Test tags for [BookShelfRow] and [BookShelfCard]. */
internal object BookShelfTestTags {
    const val ROW = "bookShelf.row"
    const val CARD = "bookShelf.card"
    const val TITLE = "bookShelf.title"
    const val SUBTITLE = "bookShelf.subtitle"
}
