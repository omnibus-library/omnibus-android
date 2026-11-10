package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.feature.library.MosaicArrangement
import com.omnibus.omnibus.feature.library.ShelfMetaPart
import com.omnibus.omnibus.feature.library.shelfMetaParts
import com.omnibus.omnibus.feature.library.shelfMosaicOf
import com.omnibus.omnibus.feature.library.shelfPlateTone
import com.omnibus.omnibus.feature.library.showsOwnerAttribution
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.components.TextLinkButton
import com.omnibus.omnibus.ui.components.coverShadow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Test tags for [ShelvesRail] and its cards. */
internal object ShelvesRailTestTags {
    const val HEADING = "shelves-heading"
    const val ALL = "shelves-all"
    const val ROW = "shelves-row"
    const val START_FADE = "shelves-fade-start"
    const val END_FADE = "shelves-fade-end"
    fun card(id: Long) = "shelves-card-$id"
}

/** Whether a rail edge fade is showing, read by tests. A custom key, so accessibility services never see it. */
internal val ShelvesRailFadeShownKey = SemanticsPropertyKey<Boolean>("ShelvesRailFadeShown")

/** The separator between the parts of a card's meta line. */
private const val SHELF_META_SEPARATOR = " · "

/** The width of a shelf card, its mosaic and its text. */
private val ShelfCardWidth = 108.dp

/** The corner radius of a card's mosaic. */
private val MosaicCorner = 6.dp

/** The gap between the covers of a mosaic. */
private val MosaicGap = 1.dp

/** The gap between two cards in the row. */
private val CardSpacing = 14.dp

/** The width of each edge fade over the row. */
private val RailFadeWidth = 40.dp

/**
 * The Library's Shelves section: a heading over a row of [ShelfCard]s, one for each of [shelves], which the caller has
 * already filtered for [viewerId]. The row snaps a card's leading edge to the start padding, and a fade shows at each
 * end the row can still scroll towards. Tapping a card does nothing yet.
 *
 * @param onOpenAll Called when the heading's All link is tapped
 */
@Composable
internal fun ShelvesRail(
    shelves: List<ShelfPreview>,
    viewerId: Long?,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    onOpenAll: () -> Unit = {},
) {
    val palette = OmnibusTheme.palette
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.library_shelves_heading),
                style = displayTextStyle(27, palette.ink0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).testTag(ShelvesRailTestTags.HEADING),
            )
            TextLinkButton(
                text = stringResource(R.string.library_shelves_all),
                color = palette.accent,
                size = 15,
                onClick = onOpenAll,
                modifier = Modifier.testTag(ShelvesRailTestTags.ALL),
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Box(Modifier.fillMaxWidth()) {
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = Spacing.screen),
                horizontalArrangement = Arrangement.spacedBy(CardSpacing),
                verticalAlignment = Alignment.Top,
                flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
                modifier = Modifier.fillMaxWidth().testTag(ShelvesRailTestTags.ROW),
            ) {
                items(shelves, key = { it.shelf.id }) { preview ->
                    ShelfCard(preview, viewerId)
                }
            }
            RailFade(start = true, state = state)
            RailFade(start = false, state = state)
        }
    }
}

/**
 * One shelf on the rail: its mosaic with a kind badge, then its name and meta line. The card is a single node for
 * TalkBack, described by its name, book count, kind, and whether it is public and whose it is.
 */
@Composable
internal fun ShelfCard(preview: ShelfPreview, viewerId: Long?, modifier: Modifier = Modifier) {
    val shelf = preview.shelf
    val palette = OmnibusTheme.palette
    val parts = shelfMetaParts(shelf, viewerId)
    val partTexts = parts.map { metaPartText(it) }
    // The meta line reads "Smart" for a smart shelf, while TalkBack names the kind, so the kind is spoken instead.
    val spokenParts = parts.drop(1).filter { it != ShelfMetaPart.Smart }.map { metaPartText(it) }
    val description = listOf(shelf.name, partTexts.first(), stringResource(kindLabelRes(shelf.kind))) + spokenParts
    Column(
        modifier
            .width(ShelfCardWidth)
            .clearAndSetSemantics {
                this[SemanticsProperties.TestTag] = ShelvesRailTestTags.card(shelf.id)
                contentDescription = description.joinToString(", ")
            },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).coverShadow(MosaicCorner)) {
            ShelfMosaicArt(
                preview = preview,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(MosaicCorner))
                    .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(MosaicCorner)),
            )
            if (shelf.kind != ShelfKind.Manual) {
                ShelfBadge(shelf.kind, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = shelf.name,
            style = uiTextStyle(13, FontWeight.SemiBold, palette.ink0),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = partTexts.joinToString(SHELF_META_SEPARATOR),
            style = uiTextStyle(11, FontWeight.Normal, palette.ink3),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The mosaic of [preview]: its covers arranged by how many there are, or the tinted plate when it has none. */
@Composable
private fun ShelfMosaicArt(preview: ShelfPreview, modifier: Modifier) {
    val mosaic = shelfMosaicOf(preview.coverUuids)
    val covers = mosaic.covers
    when (mosaic.arrangement) {
        MosaicArrangement.Plate -> ShelfPlate(preview.shelf, modifier)
        MosaicArrangement.Single -> MosaicSlot(covers[0], modifier)
        MosaicArrangement.SideBySide -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(MosaicGap)) {
            MosaicSlot(covers[0], Modifier.weight(1f).fillMaxHeight())
            MosaicSlot(covers[1], Modifier.weight(1f).fillMaxHeight())
        }
        MosaicArrangement.LeadAndStack -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(MosaicGap)) {
            MosaicSlot(covers[0], Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(MosaicGap)) {
                MosaicSlot(covers[1], Modifier.weight(1f).fillMaxWidth())
                MosaicSlot(covers[2], Modifier.weight(1f).fillMaxWidth())
            }
        }
        MosaicArrangement.Grid -> Column(modifier, verticalArrangement = Arrangement.spacedBy(MosaicGap)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MosaicGap)) {
                MosaicSlot(covers[0], Modifier.weight(1f).fillMaxHeight())
                MosaicSlot(covers[1], Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MosaicGap)) {
                MosaicSlot(covers[2], Modifier.weight(1f).fillMaxHeight())
                MosaicSlot(covers[3], Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** One cover of a mosaic, drawn from its uuid at the small size, with square corners and no 2:3 ratio. */
@Composable
private fun MosaicSlot(uuid: String, modifier: Modifier) {
    BookCover(
        identity = CoverIdentity(uuid = uuid, title = "", hasCover = true),
        modifier = modifier,
        size = CoverSize.Sm,
        cornerRadius = 0.dp,
        keepsAspect = false,
    )
}

/** A shelf with no covers: a gradient in its tone, with its kind's glyph in the middle. */
@Composable
private fun ShelfPlate(shelf: Shelf, modifier: Modifier) {
    val tone = shelfPlateTone(shelf)
    val top = remember(tone) { tone.copy(l = 0.38, c = tone.c * 0.7).toColor() }
    val bottom = remember(tone) { tone.copy(l = 0.22, c = tone.c * 0.5).toColor() }
    Box(
        modifier.drawBehind {
            drawRect(Brush.linearGradient(listOf(top, bottom), start = Offset.Zero, end = Offset(size.width, size.height)))
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(shelfKindGlyph(shelf.kind)),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.size(26.dp),
        )
    }
}

/** A 20 dp dark disc with the kind's glyph in white, for a smart or wishlist shelf. */
@Composable
private fun ShelfBadge(kind: ShelfKind, modifier: Modifier) {
    Box(
        modifier
            .size(20.dp)
            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(shelfKindGlyph(kind)),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(11.dp),
        )
    }
}

/**
 * One edge fade over the row, in the screen's background colour. It shows only while the row can scroll towards its
 * edge. The fade has no pointer input and is hidden from accessibility, so drags and taps reach the row.
 */
@Composable
private fun BoxScope.RailFade(start: Boolean, state: LazyListState) {
    val ground = OmnibusTheme.palette.bg0
    val shownState = remember(state, start) {
        derivedStateOf { if (start) state.canScrollBackward else state.canScrollForward }
    }
    // Read here, in composition, so a flip recomposes and refreshes the semantics value.
    val shown = shownState.value
    Box(
        Modifier
            .matchParentSize()
            .semantics {
                hideFromAccessibility()
                this[SemanticsProperties.TestTag] = if (start) ShelvesRailTestTags.START_FADE else ShelvesRailTestTags.END_FADE
                this[ShelvesRailFadeShownKey] = shown
            }
            // Read in the draw phase, so the fade is right on the first frame and a fling doesn't recompose.
            .drawBehind {
                val canScroll = if (start) state.canScrollBackward else state.canScrollForward
                if (!canScroll) return@drawBehind
                val width = RailFadeWidth.toPx()
                val left = if (start) 0f else size.width - width
                val colors = if (start) listOf(ground, Color.Transparent) else listOf(Color.Transparent, ground)
                drawRect(
                    brush = Brush.horizontalGradient(colors, startX = left, endX = left + width),
                    topLeft = Offset(left, 0f),
                    size = Size(width, size.height),
                )
            },
    )
}

/** The localised text of one meta-line part. */
@Composable
private fun metaPartText(part: ShelfMetaPart): String = when (part) {
    is ShelfMetaPart.BookCount -> {
        val count = part.count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        pluralStringResource(R.plurals.library_shelves_book_count, count, count)
    }
    ShelfMetaPart.Smart -> stringResource(R.string.library_shelves_meta_smart)
    ShelfMetaPart.Public -> stringResource(R.string.library_shelves_meta_public)
    is ShelfMetaPart.Owner -> stringResource(R.string.library_shelves_meta_owner, part.name)
}

/** The kind's word as TalkBack reads it in a card's description. */
private fun kindLabelRes(kind: ShelfKind): Int = when (kind) {
    ShelfKind.Smart -> R.string.library_shelves_kind_smart
    ShelfKind.Wishlist -> R.string.library_shelves_kind_wishlist
    ShelfKind.Manual -> R.string.library_shelves_kind_manual
}

/** The glyph for the kind: a wand, a heart, or stacked shelves. */
internal fun shelfKindGlyph(kind: ShelfKind): Int = when (kind) {
    ShelfKind.Smart -> R.drawable.ic_shelf_smart
    ShelfKind.Wishlist -> R.drawable.ic_shelf_wishlist
    ShelfKind.Manual -> R.drawable.ic_shelf_manual
}

@PreviewLightDark
@Composable
private fun ShelvesRailPreview() {
    OmnibusTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ShelvesRail(
                shelves = listOf(
                    previewShelf(1L, "Currently Reading", ShelfKind.Manual, ShelfVisibility.Private, 12L, 4),
                    previewShelf(2L, "Sci-Fi Favourites", ShelfKind.Smart, ShelfVisibility.Private, 34L, 3),
                    previewShelf(3L, "Book Club 2026", ShelfKind.Manual, ShelfVisibility.Public, 8L, 2),
                    previewShelf(4L, "Hunter's Wishlist", ShelfKind.Wishlist, ShelfVisibility.Private, 1L, 1),
                    previewShelf(5L, "Someday", ShelfKind.Manual, ShelfVisibility.Private, 0L, 0),
                ),
                viewerId = 7L,
            )
        }
    }
}

/** A shelf for the preview, owned by the viewer, with [covers] cover uuids that load nothing. */
private fun previewShelf(
    id: Long,
    name: String,
    kind: ShelfKind,
    visibility: ShelfVisibility,
    bookCount: Long,
    covers: Int,
) = ShelfPreview(
    shelf = Shelf(
        id = id,
        ownerUserId = 7L,
        ownerUsername = "hunter",
        kind = kind,
        name = name,
        visibility = visibility,
        accent = null,
        bookCount = bookCount,
    ),
    coverUuids = List(covers) { "preview-$id-$it" },
)
