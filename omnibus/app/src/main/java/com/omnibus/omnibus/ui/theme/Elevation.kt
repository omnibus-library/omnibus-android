package com.omnibus.omnibus.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The floating card's two-layer shadow (light and dark values from the design, #271). The shadow must come before any
 * `clip` in the modifier chain, or the clip cuts it off. [fill] must be the card's opaque fill: it is painted under the
 * card to cast the shadow. From API 28 the shadow is drawn with two blurred layers; below that it is an elevation
 * approximation.
 */
fun Modifier.floatingCardShadow(cornerRadius: Dp, fill: Color, dark: Boolean): Modifier {
    val layers = if (dark) DarkCardShadow else LightCardShadow
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawBehind {
            drawIntoCanvas { canvas ->
                val radius = cornerRadius.toPx()
                layers.forEach { layer ->
                    val paint = Paint().apply {
                        color = fill
                        asFrameworkPaint().setShadowLayer(
                            layer.blur.toPx(),
                            0f,
                            layer.offsetY.toPx(),
                            layer.color.toArgb(),
                        )
                    }
                    canvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, paint)
                }
            }
        }
    } else {
        shadow(
            elevation = if (dark) 8.dp else 6.dp,
            shape = RoundedCornerShape(cornerRadius),
            ambientColor = layers.last().color,
            spotColor = layers.first().color,
        )
    }
}

/** One blurred layer of a card shadow. [blur] is Android's blur radius, about 0.87 times the CSS blur. */
private class CardShadowLayer(val blur: Dp, val offsetY: Dp, val color: Color)

private val LightCardShadow = listOf(
    CardShadowLayer(blur = 17.dp, offsetY = 6.dp, color = Color(0x291E140A)),
    CardShadowLayer(blur = 2.6f.dp, offsetY = 1.dp, color = Color(0x1F1E140A)),
)

private val DarkCardShadow = listOf(
    CardShadowLayer(blur = 21.dp, offsetY = 8.dp, color = Color(0x99000000)),
    CardShadowLayer(blur = 2.6f.dp, offsetY = 1.dp, color = Color(0x80000000)),
)
