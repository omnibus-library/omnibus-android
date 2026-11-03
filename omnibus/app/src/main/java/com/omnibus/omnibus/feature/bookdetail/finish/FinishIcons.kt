package com.omnibus.omnibus.feature.bookdetail.finish

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The star in the review row's disc, drawn in code from the design's 24 by 24 path. */
internal val FinishStarIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FinishStar",
        defaultWidth = 18.dp,
        defaultHeight = 18.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .addPath(
            pathData = addPathNodes("M12 2.8 14.8 8.6l6.3.9-4.6 4.4 1.1 6.3L12 17.2l-5.6 3 1.1-6.3-4.6-4.4 6.3-.9z"),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}

/** The chevron at the end of a row, mirrored in right-to-left layouts. */
internal val FinishChevronIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FinishChevron",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = true,
    )
        .addPath(
            pathData = addPathNodes("M9 6l6 6-6 6"),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}
