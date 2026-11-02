package com.omnibus.omnibus.feature.bookdetail.progress

import com.omnibus.omnibus.data.bookprogress.chapters
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

/** Element-by-element comparison, so a float rounding error does not fail a list. */
private fun List<Float>.shouldBeAbout(expected: List<Float>) {
    size shouldBe expected.size
    forEachIndexed { i, value -> value shouldBe (expected[i] plusOrMinus 1e-4f) }
}

class ProgressBarGeometryTest : DescribeSpec({

    describe("segment weights") {

        it("TC-1: segment weights follow chapter size") {
            segmentWeights(chapters(0f, .25f, .5f, .75f)).shouldBeAbout(listOf(.25f, .25f, .25f, .25f))
            segmentWeights(chapters(0f, .1f, .7f)).shouldBeAbout(listOf(.1f, .6f, .3f))
        }

        it("TC-3: no usable chapter gives one full segment") {
            segmentWeights(emptyList()).shouldBeAbout(listOf(1f))
            segmentWeights(chapters(0f)).shouldBeAbout(listOf(1f))
            segmentWeights(chapters(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.2f, 1.3f, 0f, 1f))
                .shouldBeAbout(listOf(1f))
        }

        it("TC-4: starts are sorted, deduplicated and cleaned") {
            segmentWeights(chapters(.5f, 0f, .25f)).shouldBeAbout(segmentWeights(chapters(0f, .25f, .5f)))
            segmentWeights(chapters(0f, .3f, .3f, .6f)).shouldBeAbout(listOf(.3f, .3f, .4f))
            segmentWeights(chapters(Float.NaN, 0f, 1.5f, .25f, -1f, .5f, 2f))
                .shouldBeAbout(segmentWeights(chapters(0f, .25f, .5f)))
        }

        it("TC-8: slivers fold without losing length") {
            segmentWeights(chapters(0f, .01f, .5f)).shouldBeAbout(listOf(.5f, .5f))
            segmentWeights(chapters(0f, .5f, .99f)).shouldBeAbout(listOf(.5f, .5f))

            val hundred = segmentWeights(List(100) { BookChapter(it, null, it / 100f) })
            hundred.sum() shouldBe (1f plusOrMinus 1e-4f)
            (hundred.size <= 24) shouldBe true
            hundred.forEach { (it >= 0.015f) shouldBe true }
        }

        it("TC-10: more than 24 segments are grouped") {
            segmentWeights(List(24) { BookChapter(it, null, it / 24f) }).size shouldBe 24
            segmentWeights(List(25) { BookChapter(it, null, it / 25f) }).size shouldBe 13
            segmentWeights(List(49) { BookChapter(it, null, it / 49f) }).size shouldBe 17
            segmentWeights(List(60) { BookChapter(it, null, it / 60f) }).size shouldBe 20

            listOf(24, 25, 49, 60).forEach { count ->
                segmentWeights(List(count) { BookChapter(it, null, it / count.toFloat()) }).sum() shouldBe
                    (1f plusOrMinus 1e-4f)
            }
        }
    }

    describe("bar segments") {

        it("TC-12: each segment fills with its part of the fraction") {
            val weights = listOf(.25f, .25f, .5f)
            fun fills(fraction: Float) = barSegments(weights, fraction).map { it.fill }

            fills(.5f).shouldBeAbout(listOf(1f, 1f, 0f))
            fills(.375f).shouldBeAbout(listOf(1f, .5f, 0f))
            fills(0f).shouldBeAbout(listOf(0f, 0f, 0f))
            fills(1f).shouldBeAbout(listOf(1f, 1f, 1f))
            fills(-.5f).shouldBeAbout(listOf(0f, 0f, 0f))
            fills(1.5f).shouldBeAbout(listOf(1f, 1f, 1f))
            fills(Float.NaN).shouldBeAbout(listOf(0f, 0f, 0f))
            barSegments(emptyList(), .5f) shouldBe emptyList()
            barSegments(listOf(0f), .5f) shouldBe emptyList()
            barSegments(weights, .5f).map { it.weight }.shouldBeAbout(weights)
        }
    }

    describe("flag and stem") {

        it("TC-14: the flag centre is clamped inside the bar") {
            flagCenterX(0f, 300f, 60f) shouldBe (30f plusOrMinus 1e-4f)
            flagCenterX(300f, 300f, 60f) shouldBe (270f plusOrMinus 1e-4f)
            flagCenterX(150f, 300f, 60f) shouldBe (150f plusOrMinus 1e-4f)
            flagCenterX(-10f, 300f, 60f) shouldBe (30f plusOrMinus 1e-4f)
            flagCenterX(400f, 300f, 60f) shouldBe (270f plusOrMinus 1e-4f)
            flagCenterX(100f, 300f, 400f) shouldBe (150f plusOrMinus 1e-4f)
        }

        it("TC-17: the stem stays inside the bar") {
            stemX(0f, 300f, 2f) shouldBe (0f plusOrMinus 1e-4f)
            stemX(150f, 300f, 2f) shouldBe (149f plusOrMinus 1e-4f)
            stemX(300f, 300f, 2f) shouldBe (298f plusOrMinus 1e-4f)
            stemX(.5f, 1f, 2f) shouldBe (0f plusOrMinus 1e-4f)
        }

        it("TC-93: the fill edge accounts for the gaps") {
            fillEdgeX(listOf(BarSegment(.5f, 1f), BarSegment(.5f, 0f)), 103f, 3f) shouldBe (50f plusOrMinus 1e-4f)
            fillEdgeX(listOf(BarSegment(.5f, 1f), BarSegment(.5f, .5f)), 103f, 3f) shouldBe (78f plusOrMinus 1e-4f)
            fillEdgeX(listOf(BarSegment(.5f, 0f), BarSegment(.5f, 0f)), 103f, 3f) shouldBe (0f plusOrMinus 1e-4f)
            fillEdgeX(listOf(BarSegment(.5f, 1f), BarSegment(.5f, 1f)), 103f, 3f) shouldBe (103f plusOrMinus 1e-4f)
            fillEdgeX(emptyList(), 103f, 3f) shouldBe (0f plusOrMinus 1e-4f)
        }
    }
})
