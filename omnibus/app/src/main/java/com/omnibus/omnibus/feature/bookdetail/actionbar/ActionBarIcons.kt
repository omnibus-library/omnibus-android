package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The open book for the reading labels, drawn in code from the design's 24 by 24 path. */
internal val OpenBookIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ActionBarOpenBook",
        defaultWidth = 21.dp,
        defaultHeight = 21.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .addPath(
            pathData = addPathNodes("M12 6.5C10.4 5.2 8 4.5 4.5 4.5H3v13.5h1.5c3.5 0 5.9.7 7.5 2"),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .addPath(
            pathData = addPathNodes("M12 6.5c1.6-1.3 4-2 7.5-2H21v13.5h-1.5c-3.5 0-5.9.7-7.5 2"),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .addPath(
            pathData = addPathNodes("M12 6.5v13.5"),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}

/** The four-bar audio mark for the listening labels: 18 by 16 dp, bars 3 dp wide with 2 dp gaps, bottom-aligned. */
@Composable
internal fun AudioBarsMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 18.dp, height = 16.dp)) {
        val barWidth = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val radius = CornerRadius(1.5.dp.toPx())
        val heights = listOf(0.45f, 1f, 0.6f, 0.8f)
        heights.forEachIndexed { index, fraction ->
            val barHeight = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = radius,
            )
        }
    }
}
