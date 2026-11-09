package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** The screen edge an [EdgeFade] is anchored to. The solid part sits against this edge. */
enum class FadeEdge { Top, Bottom }

private const val SOLID_ALPHA = 0.96f
private const val EASE_STOPS = 12

/**
 * A `bg0` band that covers content under a system bar or the bottom overlay: a solid part against [edge], then an
 * eased fade to transparent.
 *
 * It never takes touches or semantics, so content beneath it stays tappable. Unlike Book Detail's mask
 * `Modifier.edgeFade`, it paints over content rather than cutting it away.
 *
 * @param solid The height of the solid part, measured from [edge] (for example the status bar inset).
 * @param fade The height of the eased part beyond [solid].
 */
@Composable
fun EdgeFade(edge: FadeEdge, solid: Dp, fade: Dp, modifier: Modifier = Modifier) {
    val ground = OmnibusTheme.palette.bg0
    Box(
        modifier
            .fillMaxWidth()
            .height(solid + fade)
            .drawBehind {
                if (size.height <= 0f) return@drawBehind
                val fraction = solid.toPx() / size.height
                val stops = edgeFadeStops(fraction).map { (position, alpha) ->
                    val y = if (edge == FadeEdge.Bottom) 1f - position else position
                    y to ground.copy(alpha = alpha)
                }
                val ordered = if (edge == FadeEdge.Bottom) stops.reversed() else stops
                drawRect(Brush.verticalGradient(*ordered.toTypedArray()))
            },
    )
}

/**
 * The gradient stops of an edge fade, as (position, alpha) pairs measured from the anchored edge: 0 is the edge and 1
 * is the transparent end. The solid part holds [SOLID_ALPHA] up to [solidFraction], then an ease-out smoothstep falls
 * to exactly 0 at the end.
 */
internal fun edgeFadeStops(solidFraction: Float): List<Pair<Float, Float>> {
    val f = solidFraction.coerceIn(0f, 1f)
    val stops = mutableListOf(0f to SOLID_ALPHA, f to SOLID_ALPHA)
    for (i in 1..EASE_STOPS) {
        val t = i / EASE_STOPS.toFloat()
        val position = if (i == EASE_STOPS) 1f else f + (1f - f) * t
        val alpha = if (i == EASE_STOPS) 0f else SOLID_ALPHA * (1f - t * t * (3f - 2f * t))
        stops += position to alpha
    }
    return stops
}

@Composable
@PreviewLightDark
private fun EdgeFadePreview() {
    OmnibusTheme {
        Box(Modifier.fillMaxSize().background(OmnibusTheme.palette.ink0)) {
            EdgeFade(FadeEdge.Top, solid = 28.dp, fade = 24.dp, modifier = Modifier.align(Alignment.TopCenter))
            EdgeFade(FadeEdge.Bottom, solid = 57.dp, fade = 36.dp, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
