package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** One shadow layer: y offset, blur and colour, as in tech spec §1.3 (`0 y blur colour`). */
internal data class ShadowLayer(val y: Dp, val blur: Dp, val color: Color)

/** The bar's and FAB's two-layer shadows (tech spec §1.3). */
internal object ChromeShadows {
    fun bar(dark: Boolean): List<ShadowLayer> = if (dark) {
        listOf(ShadowLayer(4.dp, 12.dp, Color(0x80000000)), ShadowLayer(16.dp, 40.dp, Color(0xB3000000)))
    } else {
        listOf(ShadowLayer(4.dp, 10.dp, Color(0x2E000000)), ShadowLayer(14.dp, 34.dp, Color(0x47000000)))
    }

    fun fab(dark: Boolean): List<ShadowLayer> = if (dark) {
        listOf(ShadowLayer(3.dp, 8.dp, Color(0x80000000)), ShadowLayer(10.dp, 24.dp, Color(0xA6000000)))
    } else {
        listOf(ShadowLayer(3.dp, 6.dp, Color(0x2E000000)), ShadowLayer(8.dp, 20.dp, Color(0x38000000)))
    }
}

/** Whether the Omnibus theme in effect is the dark palette. */
internal val isOmnibusDark: Boolean
    @Composable @ReadOnlyComposable get() = OmnibusTheme.palette == OmnibusPalette.Atrium

/**
 * Draws [layers] behind the content with `dropShadow`, not `shadow`: elevation shadows can't take these offsets and
 * colours (AND-SURF-1). Apply before `clip`, or the clip cuts the shadow off.
 */
internal fun Modifier.chromeShadow(shape: Shape, layers: List<ShadowLayer>): Modifier =
    layers.asReversed().fold(this) { acc, layer ->
        acc.dropShadow(shape, Shadow(radius = layer.blur, color = layer.color, offset = DpOffset(0.dp, layer.y)))
    }
