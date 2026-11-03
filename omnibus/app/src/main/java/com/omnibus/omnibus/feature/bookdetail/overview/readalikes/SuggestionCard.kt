package com.omnibus.omnibus.feature.bookdetail.overview.readalikes

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.fetch.Fetcher
import coil3.request.ImageRequest
import com.omnibus.omnibus.feature.bookdetail.overview.shelf.BookShelfTestTags
import com.omnibus.omnibus.feature.bookdetail.overview.shelf.ShelfCardWidth
import com.omnibus.omnibus.feature.bookdetail.overview.shelf.ShelfCoverHeight
import com.omnibus.omnibus.ui.components.GeneratedCoverPlate
import com.omnibus.omnibus.ui.components.coverShadow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlin.math.max

/** The corner radius of a suggestion cover. It matches the covers on the other shelves. */
private val SuggestionCoverCorner: Dp = 4.dp

/**
 * One suggestion on the read-alikes shelf: its cover, its title on up to two lines and its author under it. It has the
 * same size, text styles and test tags as [com.omnibus.omnibus.feature.bookdetail.overview.shelf.BookShelfCard], but
 * draws the server's cover over the generated plate, which `BookCover` cannot do. The card is one button announced as
 * its title and author; [onClickLabel] says what activating it does.
 *
 * @param coverFetcher Fetches the cover image through the repository, so the bearer token is sent. Null draws the plate
 * alone.
 */
@Composable
internal fun SuggestionCard(
    item: ReadAlike,
    onClick: () -> Unit,
    onClickLabel: String,
    coverFetcher: Fetcher.Factory<SuggestionCoverRequest>?,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Column(
        modifier
            .width(ShelfCardWidth)
            .clip(RoundedCornerShape(SuggestionCoverCorner))
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .testTag(BookShelfTestTags.CARD),
    ) {
        SuggestionCover(item, coverFetcher)
        Text(
            text = item.title,
            style = uiTextStyle(14, FontWeight.SemiBold, palette.ink0),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 9.dp).testTag(BookShelfTestTags.TITLE),
        )
        if (item.author.isNotBlank()) {
            Text(
                text = item.author,
                style = uiTextStyle(13, FontWeight.Normal, palette.ink1),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp).testTag(BookShelfTestTags.SUBTITLE),
            )
        }
    }
}

/**
 * The cover box: the generated plate, the suggestion's cover over it once it loads, and the lighting. A cover that
 * fails to load leaves the plate in view. The box is decorative to accessibility services, as the card describes it.
 */
@Composable
private fun SuggestionCover(item: ReadAlike, coverFetcher: Fetcher.Factory<SuggestionCoverRequest>?) {
    val shape = RoundedCornerShape(SuggestionCoverCorner)
    Box(
        Modifier
            .size(width = ShelfCardWidth, height = ShelfCoverHeight)
            .coverShadow(SuggestionCoverCorner)
            .clip(shape)
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), shape)
            .clearAndSetSemantics {},
    ) {
        GeneratedCoverPlate(item.identity, Modifier.fillMaxSize())
        val path = item.coverPath
        if (path != null && coverFetcher != null && !LocalInspectionMode.current) {
            val context = LocalPlatformContext.current
            // The key includes the book's id: a rank-based path can point to another book after the server refreshes.
            val request = remember(path, item.hardcoverId, coverFetcher, context) {
                ImageRequest.Builder(context)
                    .data(SuggestionCoverRequest(path))
                    .fetcherFactory(coverFetcher)
                    .memoryCacheKey("suggestion-cover:${item.hardcoverId}:$path")
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SuggestionCoverLighting(Modifier.fillMaxSize())
    }
}

/**
 * What turns a cover from a picture into an object: a gutter shadow down the spine edge and a faint specular wash
 * across the top-left, as if the shelf were lit from above. Mirrors the private `CoverLighting` in
 * `ui/components/BookCover.kt`; keep the two in step.
 */
@Composable
private fun SuggestionCoverLighting(modifier: Modifier = Modifier) {
    Box(
        modifier.drawBehind {
            val gutter = max(3.dp.toPx(), size.width * 0.07f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.22f),
                        Color.Black.copy(alpha = 0.04f),
                        Color.Black.copy(alpha = 0f),
                    ),
                    startX = 0f,
                    endX = gutter,
                ),
                size = Size(gutter, size.height),
            )
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0f)),
                    start = Offset.Zero,
                    end = Offset(size.width / 2, size.height / 2),
                ),
            )
        },
    )
}
