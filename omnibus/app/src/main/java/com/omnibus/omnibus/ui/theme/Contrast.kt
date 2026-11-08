package com.omnibus.omnibus.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** The WCAG contrast ratio between [a] and [b], from 1 to 21. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = a.luminance() + 0.05
    val lb = b.luminance() + 0.05
    return maxOf(la, lb).toDouble() / minOf(la, lb).toDouble()
}
