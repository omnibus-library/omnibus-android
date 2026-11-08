package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.Motion
import kotlin.math.roundToInt

/**
 * The page area, zoomed out into a card with blank neighbour cards beside it while [contextMode] is on. The zoom is
 * draw-only (a graphics layer), so [page] is never re-measured and the book never re-paginates. While zoomed, and
 * until the zoom-in finishes, a scrim takes every touch: a tap calls [onLeave], and nothing reaches [page].
 *
 * The area comes from the constraints, not from a measured size, so the card is right on the first frame. A preview or
 * an inspection run has no animation clock, so it always shows the settled state.
 *
 * @param contextMode Whether the page is zoomed out into the card.
 * @param peeks The neighbour cards to draw beside the card. Only drawn while zoomed.
 * @param colors The reading theme's chrome colours, for the card's hairline border.
 * @param ground The page ground, behind the card and under the neighbour cards.
 * @param reduceMotion When true, the zoom lands on the next frame, with no transition.
 * @param insets The system bars and cutout, which the card keeps clear of.
 * @param onLeave Called when a tap lands on the zoomed-out page.
 * @param page The page and its labels, zoomed as one unit.
 */
@Composable
internal fun ReaderContextStage(
    contextMode: Boolean,
    peeks: Set<PeekSide>,
    colors: ReaderChromeColors,
    ground: Color,
    reduceMotion: Boolean,
    insets: WindowInsets,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    page: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(ground)
            .testTag(ReaderContextStageTags.STAGE),
    ) {
        // The constraints are bounded by the fill above, so these are the area's size on this very pass.
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val reservedTop = insets.getTop(density) +
            with(density) { (ReaderContextMetrics.barHeight + ReaderContextMetrics.cardTopGap).toPx() }
        val reservedBottom = insets.getBottom(density) + with(density) { ReaderContextMetrics.cardBottomGap.toPx() }
        val gap = with(density) { ReaderContextMetrics.pageGap.toPx() }
        val layout = remember(areaWidth, areaHeight, reservedTop, reservedBottom, gap) {
            contextCardLayout(areaWidth, areaHeight, reservedTop, reservedBottom, gap)
        }
        // Previews and inspection have no frame clock to run the spring, so they show the settled state at once.
        val instant = reduceMotion || LocalInspectionMode.current
        val animated by animateFloatAsState(
            targetValue = if (contextMode) 1f else 0f,
            animationSpec = if (instant) snap<Float>() else Motion.settle<Float>(),
            label = "context",
        )
        // The settle spring is underdamped, so it overshoots 0 and 1 on the way. Geometry (a negative corner radius
        // throws in the graphics layer) takes the clamped value, so the overshoot never reaches the layout.
        val progress = animated.coerceIn(0f, 1f)

        val scale = lerp(1f, layout.scale, progress)
        val card = ContextRect(
            left = lerp(0f, layout.card.left, progress),
            top = lerp(0f, layout.card.top, progress),
            width = areaWidth * scale,
            height = areaHeight * scale,
        )
        val corner = ReaderContextMetrics.cardCorner * progress
        // The layer clips before it scales, so the radius is divided by the scale to read as cardCorner on screen.
        val pageCorner = ReaderContextMetrics.cardCorner * (progress / scale)
        val hairline = ReaderContextMetrics.hairline
        val currentOnLeave by rememberUpdatedState(onLeave)
        val returnLabel = stringResource(R.string.reader_context_return)
        val viewConfiguration = LocalViewConfiguration.current

        if (progress > 0f) {
            if (PeekSide.Previous in peeks) {
                Box(
                    Modifier
                        .placedAt(
                            ContextRect(card.left - gap * progress - card.width, card.top, card.width, card.height),
                            density,
                        )
                        .alpha(progress)
                        .clip(RoundedCornerShape(corner))
                        .background(ground)
                        .border(hairline, colors.border, RoundedCornerShape(corner))
                        .testTag(ReaderContextStageTags.PEEK_PREVIOUS),
                )
            }
            if (PeekSide.Next in peeks) {
                Box(
                    Modifier
                        .placedAt(
                            ContextRect(card.right + gap * progress, card.top, card.width, card.height),
                            density,
                        )
                        .alpha(progress)
                        .clip(RoundedCornerShape(corner))
                        .background(ground)
                        .border(hairline, colors.border, RoundedCornerShape(corner))
                        .testTag(ReaderContextStageTags.PEEK_NEXT),
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    translationY = card.top
                    clip = progress > 0f
                    shape = RoundedCornerShape(pageCorner)
                },
        ) {
            page()
        }

        // Drawn in screen space, so the hairline is a true 0.5dp rather than scaled down with the page.
        if (progress > 0f) {
            Box(
                Modifier
                    .placedAt(card, density)
                    .border(
                        hairline,
                        colors.border.copy(alpha = colors.border.alpha * progress),
                        RoundedCornerShape(corner),
                    )
                    .testTag(ReaderContextStageTags.CARD),
            )
        }

        if (contextMode || progress > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag(ReaderContextStageTags.SCRIM)
                    .semantics {
                        role = Role.Button
                        contentDescription = returnLabel
                        onClick {
                            currentOnLeave()
                            true
                        }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            var moved = false
                            var up: PointerInputChange? = null
                            // Every event of the gesture is consumed, so nothing underneath sees any of it.
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                val current = event.changes.firstOrNull { it.id == down.id } ?: break
                                if ((current.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    moved = true
                                }
                                if (!current.pressed) {
                                    up = current
                                    break
                                }
                            }
                            // A tap is released before the long-press timeout, without moving. The timeout is read from
                            // the event times, not a timer, so a long press never counts as a tap.
                            val held = up?.let { it.uptimeMillis - down.uptimeMillis }
                            if (held != null && !moved && held < viewConfiguration.longPressTimeoutMillis) {
                                currentOnLeave()
                            }
                        }
                    },
            )
        }
    }
}

/** Places a box over [rect], given in screen pixels, sized in dp. */
private fun Modifier.placedAt(rect: ContextRect, density: Density): Modifier = with(density) {
    offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
        .size(rect.width.toDp(), rect.height.toDp())
}

/** Test tags on the stage's nodes. */
internal object ReaderContextStageTags {
    const val STAGE = "reader_context_stage"
    const val CARD = "reader_context_card"
    const val PEEK_PREVIOUS = "reader_context_peek_previous"
    const val PEEK_NEXT = "reader_context_peek_next"
    const val SCRIM = "reader_context_scrim"
}
