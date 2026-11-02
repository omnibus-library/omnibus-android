package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.ui.theme.Motion
import kotlin.math.roundToInt


/** The bookmark ribbon's width. */
internal val RibbonWidth = 20.dp

/** The bookmark ribbon's height, including its notch. */
internal val RibbonHeight = 38.dp

/** Where the ribbon's notch apex sits, as a fraction of its height (iOS `RibbonShape`). */
internal const val RIBBON_NOTCH_FRACTION = 38f / 46f

/** The length of the ribbon's swing after a selection change. */
internal const val RIBBON_SWING_MILLIS = 900

/**
 * The horizontal centre of the ribbon above tab [index] (fractional while it slides), in the strip's pixels. The four
 * cells share the width inside [insetPx] on each side.
 */
internal fun ribbonCenterXPx(
    index: Float,
    stripWidthPx: Float,
    insetPx: Float,
    tabCount: Int = BookDetailTab.entries.size,
): Float {
    val cell = (stripWidthPx - 2 * insetPx) / tabCount
    return insetPx + cell * (index + 0.5f)
}

/**
 * The swing's rotation keyframes, as (time in ms, degrees): it dips 10 degrees, rebounds 4 and settles. [direction]
 * is +1 when the selection moved right, -1 when it moved left, mirroring the swing.
 */
internal fun ribbonSwingKeyframes(direction: Int): List<Pair<Int, Float>> = listOf(
    0 to 0f,
    200 to -10f * direction,
    520 to 4f * direction,
    900 to 0f,
)

/**
 * The bookmark's shape: a rectangle with a notch cut into its foot at [RIBBON_NOTCH_FRACTION] of its height (iOS
 * `RibbonShape`).
 */
internal val RibbonShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(size.width / 2f, size.height * RIBBON_NOTCH_FRACTION)
    lineTo(0f, size.height)
    close()
}

/**
 * The bookmark hanging from the selected tab. It slides to the new tab and swings once, away from the way the
 * selection moved. It never takes touches and is hidden from accessibility. Reduced motion snaps it into place
 * without the swing.
 *
 * @param color The fill, the book's accent.
 */
@Composable
internal fun BookTabRibbon(
    state: BookDetailTabState,
    color: Color,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val index = animateFloatAsState(
        targetValue = state.selected.ordinal.toFloat(),
        animationSpec = if (reducedMotion) snap<Float>() else Motion.glide<Float>(),
        label = "ribbon-index",
    )
    val swing = remember { Animatable(0f) }
    LaunchedEffect(state.selected) {
        val direction = state.entryDirection
        swing.snapTo(0f)
        if (reducedMotion || direction == 0) return@LaunchedEffect
        swing.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = RIBBON_SWING_MILLIS
                ribbonSwingKeyframes(direction).forEach { (time, degrees) ->
                    degrees at time using FastOutSlowInEasing
                }
            },
        )
    }

    Box(
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints.fixed(RibbonWidth.roundToPx(), RibbonHeight.roundToPx()),
                )
                layout(placeable.width, placeable.height) {
                    val centre = ribbonCenterXPx(index.value, constraints.maxWidth.toFloat(), TabStripInset.toPx())
                    placeable.placeRelative(
                        x = (centre - placeable.width / 2f).roundToInt(),
                        y = TabStripHeight.roundToPx() + 1.dp.roundToPx(),
                    )
                }
            }
            .graphicsLayer {
                rotationZ = swing.value
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .shadow(2.dp, RibbonShape, clip = false)
            .testTag(BookDetailTabsTestTags.RIBBON)
            .semantics { hideFromAccessibility() }
            .drawBehind {
                val path = (RibbonShape.createOutline(size, layoutDirection, this) as Outline.Generic).path
                drawPath(path, color)
                clipPath(path) {
                    drawRect(Color.Black.copy(alpha = 0.35f), size = Size(size.width, 3.5.dp.toPx()))
                    listOf(3.dp.toPx(), size.width - 3.dp.toPx()).forEach { x ->
                        drawLine(
                            color = Color.White.copy(alpha = 0.18f),
                            start = Offset(x, 3.5.dp.toPx()),
                            end = Offset(x, size.height),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())),
                        )
                    }
                }
            }
    )
}
