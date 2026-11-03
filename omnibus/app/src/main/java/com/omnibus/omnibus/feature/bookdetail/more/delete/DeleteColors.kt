package com.omnibus.omnibus.feature.bookdetail.more.delete

import androidx.compose.ui.graphics.Color
import com.omnibus.omnibus.ui.theme.Oklch

/** The delete flow's colours, from the design boards. */
internal object DeleteColors {
    /** The destructive ink of an enabled row, the design's oklch(.76 .13 28). */
    val RowInk = Oklch(0.76, 0.13, 28.0).toColor()

    /** The filled danger pill, the design's oklch(.47 .15 28). */
    val DangerFill = Oklch(0.47, 0.15, 28.0).toColor()

    /** The checked box, the design's oklch(.6 .17 28). */
    val CheckFill = Oklch(0.6, 0.17, 28.0).toColor()

    /** The sheet's container, the design's #15120f. */
    val SheetContainer = Color(0xFF15120F)

    /** The sheet's muted text, the design's #c8c4be. */
    val Muted = Color(0xFFC8C4BE)

    /** The intro and body text, the design's #dbd7d1. */
    val Body = Color(0xFFDBD7D1)

    /** The hairline above each row, rgba(247, 244, 239, .1). */
    val RowRule = Color(0xFFF7F4EF).copy(alpha = 0.10f)

    /** The quiet pill's fill, and the disabled danger pill's fill. */
    val QuietFill = Color(0xFFF7F4EF).copy(alpha = 0.10f)

    /** The disabled danger pill's ink. */
    val DisabledInk = Color(0xFFF7F4EF).copy(alpha = 0.60f)

    /** The losses box's fill. */
    val LossBox = Color(0xFF1F1B17)
}
