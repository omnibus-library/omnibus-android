package com.omnibus.omnibus.feature.bookdetail

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.graphics.Color
import com.omnibus.omnibus.feature.bookdetail.stage.washScrimStops
import com.omnibus.omnibus.feature.bookdetail.ui.TOP_BAR_FILL_ALPHA
import com.omnibus.omnibus.feature.bookdetail.ui.isTopBarFilled
import com.omnibus.omnibus.feature.bookdetail.ui.topBarContentColor
import com.omnibus.omnibus.feature.bookdetail.ui.topBarFillColor
import com.omnibus.omnibus.feature.bookdetail.ui.topBarFillSpec
import com.omnibus.omnibus.feature.bookdetail.ui.topBarScrimStops
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import com.omnibus.omnibus.ui.theme.contrastRatio
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

/** This color drawn at [alpha] over the color it is called on, per sRGB channel, as the screen composites them. */
private fun Color.coveredBy(top: Color, alpha: Float): Color = Color(
    red = top.red * alpha + red * (1 - alpha),
    green = top.green * alpha + green * (1 - alpha),
    blue = top.blue * alpha + blue * (1 - alpha),
)

/** The scrim's alpha at [fraction] along the bar, linear between the stops either side of it. */
private fun alphaAt(stops: List<Pair<Float, Float>>, fraction: Float): Float {
    val upper = stops.first { it.first >= fraction }
    val lower = stops.last { it.first <= fraction }
    val t = (fraction - lower.first) / (upper.first - lower.first)
    return lower.second + (upper.second - lower.second) * t
}

/**
 * The ground under the unfilled bar's arrow at its worst: the brightest art on a dark ground (blurred cover, warm
 * glow), black on a light one, then the wash, then the bar's scrim at 80% down the bar, where the glyph's lowest
 * point sits.
 */
private fun groundUnderBar(palette: OmnibusPalette, lightGround: Boolean): Color {
    val art = if (lightGround) {
        Color.Black
    } else {
        Color(0.8f, 0.8f, 0.8f).coveredBy(top = Color(1f, 168 / 255f, 96 / 255f), alpha = 0.32f)
    }
    val washed = art.coveredBy(top = palette.bg0, alpha = washScrimStops(lightGround).minOf { it.second })
    return washed.coveredBy(top = palette.bg0, alpha = alphaAt(topBarScrimStops(lightGround), 0.8f))
}

class BookDetailTopBarTest : DescribeSpec({

    describe("filling the top bar") {

        it("#93 TC-28: starts just past the threshold") {
            val threshold = randomInt(0..1_000)

            isTopBarFilled(threshold, threshold.toFloat()) shouldBe false
            isTopBarFilled(threshold + 1, threshold.toFloat()) shouldBe true
        }

        context("when the reader has asked for reduced motion") {
            it("#93 TC-101: snaps") {
                topBarFillSpec(reducedMotion = true).shouldBeInstanceOf<SnapSpec<Float>>()
            }
        }

        context("when the reader has not asked for reduced motion") {
            it("#270 TC-6: animates the fill over 200 ms") {
                topBarFillSpec(reducedMotion = false).shouldBeInstanceOf<TweenSpec<Float>>().durationMillis shouldBe 200
            }
        }
    }

    describe("the unfilled bar's scrim") {

        context("when the ground is dark") {
            it("#270 TC-1: keeps the arrow at 3:1 or better over the worst art under the bar") {
                val palette = OmnibusPalette.Atrium

                val ground = groundUnderBar(palette, lightGround = false)

                contrastRatio(topBarContentColor(palette), ground) shouldBeGreaterThanOrEqualTo 3.0
            }
        }

        context("when the ground is light") {
            it("#270 TC-1: keeps the arrow at 3:1 or better over the worst art under the bar") {
                val palette = OmnibusPalette.Light

                val ground = groundUnderBar(palette, lightGround = true)

                contrastRatio(topBarContentColor(palette), ground) shouldBeGreaterThanOrEqualTo 3.0
            }
        }

        it("#270 TC-2: is a valid scrim for either ground, from offset 0 to 1 with alphas that never rise") {
            checkAll(Exhaustive.of(true, false)) { lightGround ->
                val stops = topBarScrimStops(lightGround)

                stops.first().first shouldBe 0f
                stops.last().first shouldBe 1f
                stops.zipWithNext().forEach { (before, after) ->
                    after.first shouldBeGreaterThan before.first
                    after.second shouldBeLessThanOrEqualTo before.second
                }
                stops.forEach { (_, alpha) ->
                    alpha shouldBeGreaterThanOrEqualTo 0f
                    alpha shouldBeLessThanOrEqualTo 1f
                }
            }
        }
    }

    describe("the filled bar's colour") {

        it("#270 TC-3: fills with bg1 at the fill alpha for either theme") {
            checkAll(Exhaustive.of(OmnibusPalette.Atrium, OmnibusPalette.Light)) { palette ->
                val fill = topBarFillColor(palette)

                fill.copy(alpha = 1f) shouldBe palette.bg1
                // Colors are stored at 8 bits per channel, so 0.92 reads back as 235/255.
                fill.alpha shouldBe (TOP_BAR_FILL_ALPHA plusOrMinus 1f / 255f)
            }
        }
    }
})
