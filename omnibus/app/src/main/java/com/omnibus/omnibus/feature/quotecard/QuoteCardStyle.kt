package com.omnibus.omnibus.feature.quotecard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntSize
import com.omnibus.omnibus.data.quotecard.CoverPair
import com.omnibus.omnibus.ui.theme.Oklch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The face the passage is set in. */
enum class QuoteTypeface { Serif, Sans, Mono }

/** The passage's size step, multiplying the length-stepped size. */
enum class QuoteSize(val scale: Float) { S(0.85f), M(1.0f), L(1.18f) }

/** The card's shape. [ratio] is width over height. */
enum class QuoteShape(val ratio: Float) {
    Square(1f),
    Post(4f / 5f),
    Portrait(3f / 4f),
    Story(9f / 16f),
}

/** A solid background, as the iOS client defines it: an OKLCH [base] and whether its ink is light. */
enum class QuoteWash(val base: Oklch, val lightInk: Boolean) {
    Ink(Oklch(0.19, 0.012, 70.0), true),
    Paper(Oklch(0.93, 0.017, 80.0), false),
    Moss(Oklch(0.38, 0.075, 155.0), true),
    Rose(Oklch(0.44, 0.11, 15.0), true),
    Dusk(Oklch(0.36, 0.085, 285.0), true),
    Sea(Oklch(0.40, 0.075, 230.0), true),
}

/** A gradient every book offers. The stops are opaque ARGB. */
enum class QuoteGradientPreset(val fromArgb: Int, val toArgb: Int, val lightInk: Boolean) {
    Ember(0xFF7A1F16.toInt(), 0xFFA24C16.toInt(), true),
    Dusk(0xFF2E2660.toInt(), 0xFF9A4060.toInt(), true),
    Lagoon(0xFF0B4A5A.toInt(), 0xFF26357F.toInt(), true),
    Grove(0xFF183F2A.toInt(), 0xFF7A5C18.toInt(), true),
    Dawn(0xFFF7E1C8.toInt(), 0xFFEEB0A8.toInt(), false),
}

/** Which row of swatches the Background section shows. */
enum class QuoteBackgroundKind { Solid, Gradient }

/** Which end of a custom gradient the colour sliders edit. */
enum class GradientStop { From, To }

/** What the card is painted with. [kind] is the swatch row it belongs to. */
sealed interface QuoteBackground {
    val kind: QuoteBackgroundKind

    /** The book's own tone. */
    data object Book : QuoteBackground {
        override val kind = QuoteBackgroundKind.Solid
    }

    data class Wash(val wash: QuoteWash) : QuoteBackground {
        override val kind = QuoteBackgroundKind.Solid
    }

    data class CustomSolid(val argb: Int) : QuoteBackground {
        override val kind = QuoteBackgroundKind.Solid
    }

    /** One of the cover's gradient pairs from the server. */
    data class Cover(val pair: CoverPair) : QuoteBackground {
        override val kind = QuoteBackgroundKind.Gradient
    }

    data class Preset(val preset: QuoteGradientPreset) : QuoteBackground {
        override val kind = QuoteBackgroundKind.Gradient
    }

    data class CustomGradient(val fromArgb: Int, val toArgb: Int, val angle: Int = DEFAULT_ANGLE) : QuoteBackground {
        override val kind = QuoteBackgroundKind.Gradient
    }
}

/** What the card paints: two stops at [angle] degrees (CSS convention), and the ink over them. */
data class QuoteFill(val from: Color, val to: Color, val angle: Int, val ink: Color)

/** The angle every background but a custom gradient is drawn at. */
const val DEFAULT_ANGLE = 135

/** The width the card is exported at, in pixels. */
const val EXPORT_WIDTH_PX = 1080

/** Ink over a dark or mid-tone hex gradient. */
internal val LightInk = Color(0xFFF5F0E8)

/** Ink over a pale hex gradient. */
internal val DarkInk = Color(0xFF221D18)

private val PassageSteps = listOf(90 to 0.082f, 180 to 0.066f, 320 to 0.052f, 520 to 0.042f)
private const val LONGEST_PASSAGE_FRACTION = 0.034f
private val Whitespace = Regex("\\s+")

/** One space per run of whitespace, ends trimmed. A stored passage keeps its source file's line breaks. */
fun String.collapsingWhitespace(): String = trim().replace(Whitespace, " ")

/** The passage's base size as a fraction of the card width, stepped by its collapsed length. */
fun passageSizeFraction(passage: String): Float {
    val length = passage.collapsingWhitespace().let { it.codePointCount(0, it.length) }
    return PassageSteps.firstOrNull { length < it.first }?.second ?: LONGEST_PASSAGE_FRACTION
}

/** The card's exported size: [EXPORT_WIDTH_PX] wide, as tall as [shape] makes it. */
fun exportSize(shape: QuoteShape): IntSize = IntSize(EXPORT_WIDTH_PX, (EXPORT_WIDTH_PX / shape.ratio).roundToInt())

/** The WCAG contrast ratio between two opaque colours, 1 to 21. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

/** [LightInk] or [DarkInk], whichever keeps the higher contrast on the worse of the two stops. */
fun inkFor(fromArgb: Int, toArgb: Int): Color {
    val stops = listOf(Color(fromArgb), Color(toArgb))
    fun worst(ink: Color) = stops.minOf { contrastRatio(ink, it) }
    return if (worst(LightInk) >= worst(DarkInk)) LightInk else DarkInk
}

/** The axis for each 45 degree step from 0, as (x, y) with y growing downward. */
private val Directions = listOf(0 to -1, 1 to -1, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1)
    .map { it.first.toFloat() to it.second.toFloat() }

/** The CSS-angle gradient axis across [size], from edge midpoints and corners: 0 is bottom to top, 90 left to right. */
fun gradientEndpoints(angle: Int, size: Size): Pair<Offset, Offset> {
    val (dx, dy) = Directions[Math.floorMod((angle / 45f).roundToInt(), Directions.size)]
    fun at(sign: Int) = Offset(size.width * (0.5f + 0.5f * sign * dx), size.height * (0.5f + 0.5f * sign * dy))
    return at(-1) to at(1)
}

/** [this] painted for a book whose own colour is [tone]. */
fun QuoteBackground.fill(tone: Oklch): QuoteFill = when (this) {
    QuoteBackground.Book ->
        solidFill(Oklch(tone.l.coerceIn(0.28, 0.46), max(0.05, tone.c), tone.h), lightInk = true)
    is QuoteBackground.Wash -> solidFill(wash.base, wash.lightInk)
    is QuoteBackground.CustomSolid -> {
        val base = Oklch.fromSrgb(Color(argb))
        val lightInkWins = contrastRatio(inkOf(base, true), Color(argb)) >= contrastRatio(inkOf(base, false), Color(argb))
        solidFill(base, lightInkWins)
    }
    is QuoteBackground.Cover ->
        QuoteFill(Color(pair.fromArgb), Color(pair.toArgb), DEFAULT_ANGLE, if (pair.lightInk) LightInk else DarkInk)
    is QuoteBackground.Preset ->
        QuoteFill(Color(preset.fromArgb), Color(preset.toArgb), DEFAULT_ANGLE, if (preset.lightInk) LightInk else DarkInk)
    is QuoteBackground.CustomGradient ->
        QuoteFill(Color(fromArgb), Color(toArgb), angle, inkFor(fromArgb, toArgb))
}

/** A flat colour still drifts to a second stop: a flat fill behind serif type reads as a placeholder. */
private fun solidFill(base: Oklch, lightInk: Boolean): QuoteFill {
    val drifted = Oklch(max(0.06, base.l - if (lightInk) 0.10 else -0.05), base.c * 0.85, base.h + 12)
    return QuoteFill(base.toColor(), drifted.toColor(), DEFAULT_ANGLE, inkOf(base, lightInk))
}

private fun inkOf(base: Oklch, light: Boolean): Color =
    if (light) Oklch(0.95, 0.012, base.h).toColor() else Oklch(0.24, 0.02, base.h).toColor()
