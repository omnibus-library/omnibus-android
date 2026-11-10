package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.omnibus.omnibus.feature.library.SeriesGroup
import com.omnibus.omnibus.feature.library.coverPose
import com.omnibus.omnibus.feature.library.coverProgress
import com.omnibus.omnibus.feature.library.unfoldFrame
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlin.math.roundToInt

/**
 * The offset in px from the anchor cell's cover box to member [member]'s cover box, where the anchor is grid item
 * [anchorIndex] and the member is [memberKey]. A visible member is measured. Otherwise it is placed in grid units from
 * the anchor, as [SeriesOutlineOverlay] extrapolates rows. It reads [info], so call it only in a layout or draw lambda.
 */
internal fun memberCoverOffset(
    info: LazyGridLayoutInfo,
    anchorIndex: Int,
    member: Int,
    memberKey: Any,
    columns: Int,
    rowGap: Float,
    columnGap: Float,
    screenPadding: Float,
): IntOffset {
    // Rows after the first carry the 26 dp row gap as top padding, so a cell's cover box starts below it.
    fun pad(index: Int) = if (index - HEADER_ITEM_COUNT >= columns) rowGap else 0f
    val anchor = info.visibleItemsInfo.firstOrNull { it.index == anchorIndex } ?: return IntOffset.Zero
    info.visibleItemsInfo.firstOrNull { it.key == memberKey }?.let { m ->
        return IntOffset(
            m.offset.x - anchor.offset.x,
            (m.offset.y + pad(m.index) - anchor.offset.y - pad(anchor.index)).roundToInt(),
        )
    }
    // Otherwise (folding, or scrolled off): grid units, as the overlay's rowBox extrapolates.
    val a = anchorIndex - HEADER_ITEM_COUNT
    val m = a + 1 + member
    val slotWidth = (info.viewportSize.width - 2 * screenPadding - columnGap * (columns - 1)) / columns
    val rows = visibleRows(info, columns, rowGap)
    val pitch = if (rows.isEmpty()) 0f else rowPitch(rows, rowGap)
    return IntOffset(
        ((m % columns - a % columns) * (slotWidth + columnGap)).roundToInt(),
        ((m / columns - a / columns) * pitch).roundToInt(),
    )
}

/**
 * The whole of an unfolding or folding series, drawn at time [t] in the anchor cell: the summary text, the stack
 * caption and every member's cover and caption, posed by [coverPose]. It sits over the anchor's real cells, which are
 * hidden under it.
 *
 * The ghost has no click targets and is hidden from accessibility, so TalkBack reaches only the real cells.
 *
 * @param group The series.
 * @param width The cell width from [LibraryGridMetrics.cellWidth].
 * @param t The unfold time in 0..1. A lambda, so the frame is read only where it is drawn.
 * @param memberOffset The slot of each member relative to the anchor, from [memberCoverOffset]. A lambda for the same
 * reason.
 */
@Composable
internal fun SeriesUnfoldGhost(
    group: SeriesGroup,
    width: Dp,
    t: () -> Float,
    memberOffset: (member: Int) -> IntOffset,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    // Each child keeps its own size and may overflow the host, so the ghost never resizes the anchor cell.
    val free = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).width(width)

    Box(modifier.testTag(LibraryTestTags.ghost(group.seriesId)).semantics { hideFromAccessibility() }) {
        SeriesSummaryContent(
            group = group,
            onOpenSeries = null,
            onFoldUp = null,
            modifier = free.clearAndSetSemantics {}.graphicsLayer {
                val frame = unfoldFrame(t())
                alpha = frame.summaryAlpha
                translationY = frame.summaryRiseDp.dp.toPx()
            },
        )
        SeriesStackCaption(
            group = group,
            modifier = free
                .offset { IntOffset(0, (width * 1.5f + 8.dp).roundToPx()) }
                .graphicsLayer { alpha = unfoldFrame(t()).stackCaptionAlpha },
        )
        // Back to front, so member 0 draws on top, as the stack's front cover does.
        for (i in group.books.indices.reversed()) {
            val book = group.books[i]
            val cover = Modifier
                .testTag(LibraryTestTags.ghostCover(book.id))
                .graphicsLayer {
                    // Px from this member's slot to the anchor, read per frame in the layer.
                    val slot = memberOffset(i)
                    val pose = coverPose(
                        index = i,
                        progress = coverProgress(t(), i),
                        cellWidthDp = width.value,
                        slotDxDp = -slot.x.toDp().value,
                        slotDyDp = -slot.y.toDp().value,
                    )
                    transformOrigin = TransformOrigin(0f, 1f)
                    scaleX = pose.widthFraction
                    scaleY = pose.widthFraction
                    translationX = pose.dxDp.dp.toPx()
                    translationY = pose.dyDp.dp.toPx()
                    alpha = pose.alpha
                }
                .drawWithContent {
                    drawContent()
                    val veil = coverPose(i, coverProgress(t(), i), width.value, 0f, 0f).veilAlpha
                    if (veil > 0f) drawRoundRect(palette.bg0.copy(alpha = veil), cornerRadius = CornerRadius(5.dp.toPx()))
                }
            Column(free.offset { memberOffset(i) }) {
                BookCellContent(
                    book = book,
                    subtitle = memberSubtitle(book),
                    modifier = cover,
                    captionModifier = Modifier.clearAndSetSemantics {}.graphicsLayer {
                        alpha = unfoldFrame(t()).memberCaptionAlpha
                    },
                    coverShadow = i == 0 || i >= 3,
                    isGhost = true,
                )
            }
        }
    }
}

/**
 * A grid item that shows [cell], and while [ghosting] also the series' [SeriesUnfoldGhost] over it. The cell keeps its
 * layout, clicks and semantics at alpha 0. The item's z-index lifts the ghost over the cells around it.
 *
 * @param modifier The item modifier: placement animation and row spacing. The host is the item's root.
 * @param cell The cell, given the modifier that hides it while [ghosting].
 */
@Composable
internal fun SeriesGhostHost(
    ghosting: Boolean,
    group: SeriesGroup,
    width: Dp,
    t: () -> Float,
    memberOffset: (member: Int) -> IntOffset,
    modifier: Modifier,
    cell: @Composable (Modifier) -> Unit,
) {
    Box(modifier.zIndex(if (ghosting) 1f else 0f)) {
        cell(if (ghosting) Modifier.alpha(0f) else Modifier)
        if (ghosting) SeriesUnfoldGhost(group, width, t, memberOffset, Modifier.matchParentSize())
    }
}
