package com.omnibus.omnibus.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * A color in OKLCH, matching CSS `oklch(L C H / alpha)` and the iOS client's `OKLCH`.
 *
 * @property l Lightness, 0 to 1.
 * @property c Chroma. Absolute, not a percentage.
 * @property h Hue, in degrees.
 * @property alpha Opacity, 0 to 1.
 */
data class Oklch(
    val l: Double,
    val c: Double,
    val h: Double,
    val alpha: Double = 1.0,
) {

    /** This color in sRGB, clamped into gamut. */
    fun toColor(): Color {
        val radians = Math.toRadians(h)
        return Color(
            red = l.toFloat(),
            green = (c * cos(radians)).toFloat(),
            blue = (c * sin(radians)).toFloat(),
            alpha = alpha.toFloat(),
            colorSpace = ColorSpaces.Oklab,
        ).convert(ColorSpaces.Srgb)
    }

    /** The same color with its chroma multiplied by [scale], for muting an accent. */
    fun chroma(scale: Double): Oklch = copy(c = c * scale)

    companion object {

        /** The OKLCH of [color], reading its sRGB components. */
        fun fromSrgb(color: Color): Oklch {
            val srgb = color.convert(ColorSpaces.Srgb)
            return fromSrgb(srgb.red.toDouble(), srgb.green.toDouble(), srgb.blue.toDouble(), srgb.alpha.toDouble())
        }

        /** The OKLCH of the gamma-encoded sRGB components [r], [g] and [b], each 0 to 1. */
        fun fromSrgb(r: Double, g: Double, b: Double, alpha: Double = 1.0): Oklch {
            val lr = decode(r)
            val lg = decode(g)
            val lb = decode(b)

            val lCone = cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb)
            val mCone = cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb)
            val sCone = cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb)

            val lightness = 0.2104542553 * lCone + 0.7936177850 * mCone - 0.0040720468 * sCone
            val a = 1.9779984951 * lCone - 2.4285922050 * mCone + 0.4505937099 * sCone
            val bAxis = 0.0259040371 * lCone + 0.7827717662 * mCone - 0.8086757660 * sCone

            val hue = Math.toDegrees(atan2(bAxis, a)).let { if (it < 0) it + 360 else it }
            return Oklch(lightness, hypot(a, bAxis), hue, alpha)
        }

        /**
         * Parses the per-book `accent` string: `oklch(L C H [/ A])`, where `L` may be a percentage
         * (any value above 1 is divided by 100), or `#rrggbb`. Returns null for anything else so
         * callers can fall back to a title-derived tone.
         */
        fun parse(css: String): Oklch? {
            val value = css.trim().lowercase()
            return when {
                value.startsWith("oklch(") && value.endsWith(")") -> parseOklch(value.substring(6, value.length - 1))
                value.startsWith("#") -> parseHex(value.substring(1))
                else -> null
            }
        }

        private fun parseOklch(inner: String): Oklch? {
            val tokens = inner.replace('/', ' ').split(' ', ',').filter { it.isNotEmpty() }
            if (tokens.size < 3) return null
            val numbers = tokens.map {
                it.removeSuffix("%").toDoubleOrNull()?.takeIf { value -> value.isFinite() } ?: return null
            }
            val lightness = if (tokens[0].endsWith("%") || numbers[0] > 1) numbers[0] / 100 else numbers[0]
            return Oklch(lightness, numbers[1], numbers[2], numbers.getOrElse(3) { 1.0 })
        }

        private fun parseHex(hex: String): Oklch? {
            if (hex.length != 6 || !hex.all { it in '0'..'9' || it in 'a'..'f' }) return null
            val rgb = hex.toInt(16)
            return fromSrgb(
                r = (rgb shr 16 and 0xFF) / 255.0,
                g = (rgb shr 8 and 0xFF) / 255.0,
                b = (rgb and 0xFF) / 255.0,
            )
        }

        private fun decode(channel: Double): Double =
            if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
    }
}
