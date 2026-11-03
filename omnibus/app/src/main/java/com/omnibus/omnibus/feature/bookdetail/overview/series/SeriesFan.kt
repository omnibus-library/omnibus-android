package com.omnibus.omnibus.feature.bookdetail.overview.series

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.feature.bookdetail.BookDetailAnchor
import com.omnibus.omnibus.feature.bookdetail.bookDetailAnchor
import com.omnibus.omnibus.feature.bookdetail.tabs.RibbonShape
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.components.coverShadow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Series section's stateless view: a heading with a "Series page" link, then the fan of covers.
 *
 * Gathered, the covers overlap in a hand with this book raised and ribboned. Spread, they sit in a sideways-scrolling
 * row with a title and a line of metadata under each. The gather and spread state is hoisted, so tests drive it
 * without Hilt.
 *
 * @param model What to show.
 * @param spread Whether the fan is spread.
 * @param onToggleSpread Called when a cover is tapped in the gathered state, or this book is tapped when spread.
 * @param onOpenBook Called with the uuid of another book tapped while spread.
 * @param onOpenSeriesPage Called when the "Series page" link is tapped.
 * @param ribbonColor The ribbon's fill, the book's accent tint.
 * @param reducedMotion When true, the spread and gather snap instead of animating.
 * @param scrollState The row's scroll position, owned by the caller.
 */
@Composable
internal fun SeriesFan(
    model: SeriesFanModel,
    spread: Boolean,
    onToggleSpread: () -> Unit,
    onOpenBook: (uuid: String) -> Unit,
    onOpenSeriesPage: () -> Unit,
    ribbonColor: Color,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    Column(modifier.fillMaxWidth().testTag(SeriesFanTestTags.SECTION)) {
        Spacer(Modifier.height(40.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .bookDetailAnchor(BookDetailAnchor.Series)
                .testTag(SeriesFanTestTags.HEADING_ROW),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.book_detail_series_heading, model.seriesName),
                style = displayTextStyle(30, OmnibusTheme.palette.ink0, FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
                    .testTag(SeriesFanTestTags.HEADING),
            )
            Spacer(Modifier.width(16.dp))
            SeriesPageLink(onOpenSeriesPage)
        }
        Spacer(Modifier.height(6.dp))
        FanRow(model, spread, onToggleSpread, onOpenBook, ribbonColor, reducedMotion, scrollState)
    }
}

/** The "Series page ›" link at the end of the heading. Its chevron is drawn, not read. */
@Composable
private fun SeriesPageLink(onOpenSeriesPage: () -> Unit) {
    val palette = OmnibusTheme.palette
    val clickLabel = stringResource(R.string.book_detail_series_page_click_label)
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onOpenSeriesPage)
            .testTag(SeriesFanTestTags.SERIES_PAGE_LINK),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.book_detail_series_page),
            style = uiTextStyle(15, FontWeight.SemiBold, palette.ink1),
            maxLines = 1,
        )
        Spacer(Modifier.width(3.dp))
        Chevron(palette.ink1)
    }
}

/** The link's chevron, drawn on a 24-unit box and mirrored under right-to-left. */
@Composable
private fun Chevron(color: Color) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.size(14.dp)) {
        val u = size.width / 24f
        fun x(v: Float) = if (rtl) size.width - v * u else v * u
        val path = Path().apply {
            moveTo(x(9f), 6f * u)
            lineTo(x(15f), 12f * u)
            lineTo(x(9f), 18f * u)
        }
        drawPath(path, color, style = Stroke(width = 2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The row of covers, with the gather and spread motion, the scroll that centres this book, and the bleed. */
@Composable
private fun FanRow(
    model: SeriesFanModel,
    spread: Boolean,
    onToggleSpread: () -> Unit,
    onOpenBook: (uuid: String) -> Unit,
    ribbonColor: Color,
    reducedMotion: Boolean,
    scrollState: ScrollState,
) {
    val n = model.cards.size
    val target = if (spread) 1f else 0f
    val progress = remember { Animatable(target) }
    val textFade = animateFloatAsState(
        target,
        if (reducedMotion) snap<Float>() else tween<Float>(SeriesFanLayout.TEXT_FADE_MILLIS, easing = EaseOut),
        label = "series-fan-text",
    )
    // Reduced motion reads the target directly, so the first frame after a toggle is already final.
    val p = if (reducedMotion) target else progress.value
    val textAlpha = if (reducedMotion) target else textFade.value
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val toggle = {
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        onToggleSpread()
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val sectionWidthDp = maxWidth.value
        LaunchedEffect(spread) {
            launch {
                if (reducedMotion) {
                    progress.snapTo(target)
                } else {
                    progress.animateTo(target, tween(SeriesFanLayout.ANIMATION_MILLIS, easing = FanEasing))
                }
            }
            launch {
                val targetPx = if (!spread) {
                    0
                } else {
                    with(density) {
                        spreadScrollTargetDp(model.current, n, sectionWidthDp + 2 * SeriesFanLayout.EDGE_DP).dp.roundToPx()
                    }
                }
                // The spread width is applied this frame; wait for that layout so the scroll range exists.
                if (targetPx > 0) {
                    withTimeoutOrNull(1_000) { snapshotFlow { scrollState.maxValue }.first { it > 0 } }
                }
                val clamped = targetPx.coerceAtMost(scrollState.maxValue)
                if (reducedMotion) {
                    scrollState.scrollTo(clamped)
                } else {
                    scrollState.animateScrollTo(clamped, tween(SeriesFanLayout.ANIMATION_MILLIS, easing = FanEasing))
                }
            }
        }
        // The full spread width applies at once so the scroll range exists; it shrinks back only once the gather settles.
        val contentWidth = if (spread || p > 0f) spreadWidthDp(n).dp else maxWidth
        val rowHeight = (SeriesFanLayout.GATHERED_HEIGHT_DP + (SeriesFanLayout.SPREAD_HEIGHT_DP - SeriesFanLayout.GATHERED_HEIGHT_DP) * p).dp
        Box(
            Modifier
                .bleedHorizontally(SeriesFanLayout.EDGE_DP.dp)
                .testTag(SeriesFanTestTags.ROW)
                .horizontalScroll(scrollState, enabled = spread),
        ) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).width(contentWidth).height(rowHeight)) {
                model.cards.forEachIndexed { i, card ->
                    val pose = lerpPose(
                        fanPose(i, n, model.current, sectionWidthDp, spread = false),
                        fanPose(i, n, model.current, sectionWidthDp, spread = true),
                        p,
                    )
                    val cardTap: () -> Unit = if (spread && !card.meta.isThis) {
                        { onOpenBook(card.member.uuid) }
                    } else {
                        toggle
                    }
                    SeriesCard(card, pose, spread, textAlpha, ribbonColor, cardTap)
                }
            }
        }
    }
}

/**
 * Widens the row by [bleed] on each side, so it runs past the panel's padding to the screen edge, while the layout
 * keeps the panel's own width. The bleed is symmetric, so right-to-left needs nothing extra.
 */
private fun Modifier.bleedHorizontally(bleed: Dp): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val bleedPx = bleed.roundToPx()
    val width = constraints.maxWidth + 2 * bleedPx
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-bleedPx, 0) }
}

/**
 * One cover in the fan, with its title and metadata when spread.
 *
 * The outer box is the card's only semantics and touch node, and it is not rotated. The rotation is drawn on an
 * inner layer about the bottom centre, as iOS does.
 */
@Composable
private fun SeriesCard(
    card: SeriesFanCard,
    pose: FanPose,
    spread: Boolean,
    textAlpha: Float,
    ribbonColor: Color,
    onClick: () -> Unit,
) {
    val palette = OmnibusTheme.palette
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val resources = LocalContext.current.resources // stringResource can't be called inside the non-composable lambda
    val metaText = card.meta.parts(
        book = { resources.getString(R.string.book_detail_series_book, it) },
        thisBook = stringResource(R.string.book_detail_series_this_book),
        upNext = stringResource(R.string.book_detail_series_up_next),
    ).joinToString(stringResource(R.string.book_detail_series_meta_separator))
    val description = if (metaText.isEmpty()) {
        card.member.title
    } else {
        stringResource(R.string.book_detail_series_card_description, card.member.title, metaText)
    }
    val clickLabel = stringResource(
        when {
            !spread -> R.string.book_detail_series_spread_click_label
            card.meta.isThis -> R.string.book_detail_series_gather_click_label
            else -> R.string.book_detail_series_open_click_label
        },
    )
    Box(
        Modifier
            .zIndex(pose.z.toFloat())
            .offset { IntOffset(pose.xDp.dp.roundToPx(), (pose.yDp + 6f).dp.roundToPx()) }
            .width(SeriesFanLayout.CARD_WIDTH_DP.dp)
            .wrapContentHeight(Alignment.Top, unbounded = true)
            .clickable(
                interactionSource = null,
                indication = null,
                onClickLabel = clickLabel,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = description
                if (card.meta.isThis) selected = true
            }
            .testTag(SeriesFanTestTags.card(card.member.uuid)),
    ) {
        Column(
            Modifier.graphicsLayer {
                rotationZ = if (rtl) -pose.rotationDeg else pose.rotationDeg
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
        ) {
            Box(Modifier.size(SeriesFanLayout.CARD_WIDTH_DP.dp, SeriesFanLayout.COVER_HEIGHT_DP.dp)) {
                BookCover(
                    CoverIdentity(card.member.uuid, card.member.title, null, card.member.accent, card.member.hasCover),
                    Modifier.fillMaxSize().coverShadow(4.dp),
                    CoverSize.Md,
                    cornerRadius = 4.dp,
                    keepsAspect = false,
                )
                if (card.meta.isThis) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-10).dp, y = (-3).dp)
                            .size(14.dp, 30.dp)
                            .shadow(2.dp, RibbonShape)
                            .background(ribbonColor, RibbonShape)
                            .testTag(SeriesFanTestTags.RIBBON),
                    )
                }
            }
            Column(
                Modifier
                    .padding(top = 8.dp)
                    .graphicsLayer { alpha = textAlpha }
                    .clearAndSetSemantics {},
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = card.member.title,
                    style = uiTextStyle(13, FontWeight.SemiBold, palette.ink0),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (metaText.isNotEmpty()) {
                    Text(
                        text = metaText,
                        style = uiTextStyle(13, FontWeight.Normal, palette.ink1),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
