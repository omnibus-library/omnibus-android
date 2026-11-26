package com.omnibus.omnibus.feature.libraries.switching

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The height of a library's spine, 84 to 112 dp, fixed per library so the bookcase looks the same every time. The height
 * comes from [String.hashCode], which the JDK specifies, so it is the same on every run and every device.
 */
object SpineHeights {

    /** The spine height for [libraryId]. */
    fun of(libraryId: String): Dp = (MIN_HEIGHT_DP + Math.floorMod(libraryId.hashCode(), HEIGHT_STEPS)).dp

    private const val MIN_HEIGHT_DP = 84

    /** 84 to 112 inclusive. */
    private const val HEIGHT_STEPS = 29
}
