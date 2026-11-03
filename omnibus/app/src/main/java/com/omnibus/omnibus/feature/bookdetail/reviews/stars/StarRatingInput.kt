package com.omnibus.omnibus.feature.bookdetail.reviews.stars

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.reviews.halfStarRound
import com.omnibus.omnibus.feature.bookdetail.reviews.starNumber
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.util.Locale

/** The test tags of [StarRatingInput]. */
object StarRatingInputTestTags {
    const val ROW = "star_rating_input_row"
}

/** The opacity of the stars while they are disabled. */
private const val DISABLED_ALPHA = 0.35f

/** The height of each cell, which is taller than a star so a tap has room. */
private val CELL_HEIGHT = 46.dp

/**
 * Five stars that take a rating in half stars. A tap sets the half star under it, and a tap on the half star that is the
 * current rating clears it. A horizontal drag shows the half star under the finger and saves it on release, so a drag
 * never clears. A vertical swipe is left to the page's scroll. Stars fill from the layout's start edge, so they fill from
 * the right in right-to-left layouts.
 *
 * The row is one accessibility node: an adjustable control whose scroll-forward and scroll-backward move the rating by
 * half a star. When [enabled] is false it is dimmed and ignores every gesture and action.
 *
 * @param rating The saved rating, or null when unrated.
 * @param onRatingChange Called with the new rating, or null when it is cleared. A drag that ends on the current rating
 *   does not call it.
 * @param tint The fill colour of the full and half stars.
 * @param enabled Whether the stars respond to gestures and accessibility actions.
 * @param starSize The width and height of each star.
 * @param cellWidth The width of each of the five cells. A half star is half a cell.
 */
@Composable
fun StarRatingInput(
    rating: Float?,
    onRatingChange: (Float?) -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    starSize: Dp = 32.dp,
    cellWidth: Dp = 44.dp,
) {
    val palette = OmnibusTheme.palette
    val outline = palette.ink0.copy(alpha = 0.6f)
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales[0] ?: Locale.getDefault() }
    val description = stringResource(R.string.book_detail_rate_stars_description)
    val unrated = stringResource(R.string.book_detail_rate_stars_unrated)

    // The gesture handlers outlive recompositions, so they read the latest values through these.
    val currentRating by rememberUpdatedState(rating)
    val currentOnChange by rememberUpdatedState(onRatingChange)
    // The half star under a drag in progress. Only the stars show it; the rating is saved on release.
    var preview by remember { mutableStateOf<Float?>(null) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LaunchedEffect(enabled) { if (!enabled) preview = null }

    val shown = preview ?: rating ?: 0f
    val stateText = if (shown > 0f) {
        stringResource(R.string.book_detail_rate_stars_state, starNumber(shown, locale))
    } else {
        unrated
    }

    val gestures = if (!enabled) {
        Modifier
    } else {
        Modifier
            .pointerInput(rtl) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat()
                    currentOnChange(toggledRating(currentRating, halfStepAt(if (rtl) width - offset.x else offset.x, width)))
                }
            }
            .pointerInput(rtl) {
                fun step(x: Float): Float = size.width.toFloat().let { w -> halfStepAt(if (rtl) w - x else x, w) }
                detectHorizontalDragGestures(
                    onDragStart = { preview = step(it.x) },
                    onDragEnd = {
                        val value = preview
                        preview = null
                        if (value != null && value != currentRating) currentOnChange(value)
                    },
                    onDragCancel = { preview = null },
                ) { change, _ ->
                    change.consume()
                    preview = step(change.position.x)
                }
            }
    }

    Row(
        modifier
            .then(gestures)
            .clearAndSetSemantics {
                testTag = StarRatingInputTestTags.ROW
                contentDescription = description
                stateDescription = stateText
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..5f, steps = 9)
                if (enabled) {
                    setProgress { target ->
                        val next = halfStarRound(target).takeIf { it > 0f }
                        if (next != currentRating) currentOnChange(next)
                        true
                    }
                } else {
                    disabled()
                }
            }
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
    ) {
        for (slot in 1..5) {
            val fill = when {
                shown >= slot -> 1f
                shown >= slot - 0.5f -> 0.5f
                else -> 0f
            }
            Box(Modifier.size(cellWidth, CELL_HEIGHT), contentAlignment = Alignment.Center) {
                InputStar(fill, tint, outline, Modifier.size(starSize))
            }
        }
    }
}

/**
 * The star's outline in a 24-unit square, as the design draws it: five points, in order. This is the same geometry as
 * [com.omnibus.omnibus.feature.bookdetail.reviews.stars.StarRow]'s star, so keep the two in step.
 */
private val INPUT_STAR_POINTS = floatArrayOf(
    12f, 2.8f, 14.8f, 8.6f, 21.1f, 9.5f, 16.5f, 13.9f, 17.6f, 20.2f,
    12f, 17.2f, 6.4f, 20.2f, 7.5f, 13.9f, 2.9f, 9.5f, 9.2f, 8.6f,
)

/** One star filled to [fill], from 0 (empty) to 1 (full), from its layout's start edge. */
@Composable
private fun InputStar(fill: Float, tint: Color, outline: Color, modifier: Modifier) {
    Canvas(modifier) {
        val scale = size.minDimension / 24f
        val path = Path().apply {
            moveTo(INPUT_STAR_POINTS[0] * scale, INPUT_STAR_POINTS[1] * scale)
            for (i in 2 until INPUT_STAR_POINTS.size step 2) {
                lineTo(INPUT_STAR_POINTS[i] * scale, INPUT_STAR_POINTS[i + 1] * scale)
            }
            close()
        }
        val stroke = Stroke(width = 1.7f * scale, join = StrokeJoin.Round)
        drawPath(path, outline, style = stroke)
        if (fill > 0f) {
            val width = size.width * fill
            val left = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
            clipRect(left = left, right = left + width) {
                drawPath(path, tint)
                drawPath(path, tint, style = stroke)
            }
        }
    }
}
