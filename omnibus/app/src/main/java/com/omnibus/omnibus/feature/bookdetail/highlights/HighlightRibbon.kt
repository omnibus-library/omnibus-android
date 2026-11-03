package com.omnibus.omnibus.feature.bookdetail.highlights

import android.graphics.Paint
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.ui.theme.Oklch

/** The design's ribbon (`M0 0H22V46L11 38L0 46Z`) scaled into its box: a notch at the bottom. */
internal val HighlightRibbonShape: Shape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(size.width / 2f, size.height * 38f / 46f)
    lineTo(0f, size.height)
    close()
}

private val TINTS: Map<HighlightColor, Color> = mapOf(
    HighlightColor.Amber to Oklch(0.78, 0.14, 75.0).toColor(),
    HighlightColor.Green to Oklch(0.78, 0.14, 150.0).toColor(),
    HighlightColor.Blue to Oklch(0.74, 0.12, 245.0).toColor(),
    HighlightColor.Rose to Oklch(0.74, 0.14, 10.0).toColor(),
    HighlightColor.Violet to Oklch(0.74, 0.12, 300.0).toColor(),
)

/** The ribbon fill for this colour. It is the same in light and dark themes. */
internal fun HighlightColor.tint(): Color = TINTS.getValue(this)

/** The spoken name of this colour, such as "Amber highlight". */
internal val HighlightColor.labelRes: Int
    @StringRes get() = when (this) {
        HighlightColor.Amber -> R.string.book_detail_highlights_color_amber
        HighlightColor.Green -> R.string.book_detail_highlights_color_green
        HighlightColor.Blue -> R.string.book_detail_highlights_color_blue
        HighlightColor.Rose -> R.string.book_detail_highlights_color_rose
        HighlightColor.Violet -> R.string.book_detail_highlights_color_violet
    }

/**
 * The 13 × 32 dp notched ribbon in [color]'s tint, with a soft drop shadow. Its colour name is its spoken description.
 * The shadow draws on API 28 and later.
 */
@Composable
internal fun HighlightRibbon(color: HighlightColor, modifier: Modifier = Modifier) {
    val label = stringResource(color.labelRes)
    val layoutDirection = LocalLayoutDirection.current
    val paint = remember(color) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.tint().toArgb() }
    }
    Box(
        modifier
            .size(13.dp, 32.dp)
            .semantics { contentDescription = label }
            .drawBehind {
                val path = (HighlightRibbonShape.createOutline(size, layoutDirection, this) as Outline.Generic).path
                paint.setShadowLayer(2.dp.toPx(), 1.dp.toPx(), 2.dp.toPx(), Color.Black.copy(alpha = 0.45f).toArgb())
                drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), paint) }
            },
    )
}
