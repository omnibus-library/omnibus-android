package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Fades the content out at the top and bottom of the node, as a mask over an offscreen layer (iOS `barEdgeFade`).
 * Bands are in the node's own coordinates. Above a top band's clear edge and below a bottom band's clear edge the
 * content is removed; a band with no height is skipped.
 */
internal fun Modifier.edgeFade(top: FadeBand?, bottom: FadeBand?): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        top?.let { band ->
            val clear = band.clear.toPx()
            val opaque = band.opaque.toPx()
            if (clear > 0f) drawRect(Color.Black, size = Size(size.width, clear), blendMode = BlendMode.Clear)
            if (opaque > clear) {
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = clear, endY = opaque),
                    topLeft = Offset(0f, clear),
                    size = Size(size.width, opaque - clear),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
        bottom?.let { band ->
            val opaque = band.opaque.toPx()
            val clear = band.clear.toPx()
            if (clear > opaque) {
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = opaque, endY = clear),
                    topLeft = Offset(0f, opaque),
                    size = Size(size.width, clear - opaque),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (clear < size.height) {
                drawRect(
                    Color.Black,
                    topLeft = Offset(0f, clear),
                    size = Size(size.width, size.height - clear),
                    blendMode = BlendMode.Clear,
                )
            }
        }
    }
