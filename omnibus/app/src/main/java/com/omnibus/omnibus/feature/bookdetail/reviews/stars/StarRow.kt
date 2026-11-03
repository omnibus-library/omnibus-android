package com.omnibus.omnibus.feature.bookdetail.reviews.stars

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.reviews.halfStarRound
import com.omnibus.omnibus.feature.bookdetail.reviews.starNumber
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.util.Locale

/**
 * Five stars for [rating], drawn to the nearest half star. Full stars are filled in [tint], half stars fill from the
 * layout's start edge, and the rest are outlined. It is one node for accessibility, read as "4.5 out of 5 stars".
 *
 * @param starSize The width and height of each star.
 */
@Composable
fun StarRow(rating: Float, starSize: Dp, tint: Color, modifier: Modifier = Modifier) {
    val shown = halfStarRound(rating)
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales[0] ?: Locale.getDefault() }
    val description = stringResource(R.string.book_detail_reviews_stars_description, starNumber(shown, locale))
    val outline = OmnibusTheme.palette.ink0.copy(alpha = 0.55f)

    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (slot in 1..5) {
            val fill = when {
                shown >= slot -> 1f
                shown >= slot - 0.5f -> 0.5f
                else -> 0f
            }
            Star(fill, tint, outline, Modifier.size(starSize))
        }
    }
}

/** The star's outline in a 24-unit square, as the design draws it: five points, in order. */
private val STAR_POINTS = floatArrayOf(
    12f, 2.8f, 14.8f, 8.6f, 21.1f, 9.5f, 16.5f, 13.9f, 17.6f, 20.2f,
    12f, 17.2f, 6.4f, 20.2f, 7.5f, 13.9f, 2.9f, 9.5f, 9.2f, 8.6f,
)

/** One star filled to [fill], from 0 (empty) to 1 (full), from its layout's start edge. */
@Composable
private fun Star(fill: Float, tint: Color, outline: Color, modifier: Modifier) {
    Canvas(modifier) {
        val scale = size.minDimension / 24f
        val path = Path().apply {
            moveTo(STAR_POINTS[0] * scale, STAR_POINTS[1] * scale)
            for (i in 2 until STAR_POINTS.size step 2) lineTo(STAR_POINTS[i] * scale, STAR_POINTS[i + 1] * scale)
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
