package com.omnibus.omnibus.feature.bookdetail.progress

import com.omnibus.omnibus.data.bookprogress.chapters
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

/** Element-by-element comparison, so a float rounding error does not fail a list. */
private fun List<Float>.shouldBeAbout(expected: List<Float>) {
    size shouldBe expected.size
    forEachIndexed { i, value -> value shouldBe (expected[i] plusOrMinus 1e-4f) }
}

private fun evenChapters(count: Int) = List(count) { BookChapter(it, null, it / count.toFloat()) }

class ProgressBarGeometryTest : DescribeSpec({

    describe("the segment weights") {
        it("TC-3: are one full segment for each list with no usable chapter") {
            checkAll(
                Exhaustive.of(
                    emptyList(),
                    chapters(0f),
                    chapters(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.2f, 1.3f, 0f, 1f),
                ),
            ) { list ->
                segmentWeights(list).shouldBeAbout(listOf(1f))
            }
        }

        it("TC-1, TC-4: follow chapter size, sorted, deduplicated, cleaned, for each kind of start") {
            checkAll(
                Exhaustive.of(
                    chapters(0f, .1f, .7f) to listOf(.1f, .6f, .3f),
                    chapters(.5f, 0f, .25f) to listOf(.25f, .25f, .5f),
                    chapters(0f, .3f, .3f, .6f) to listOf(.3f, .3f, .4f),
                    chapters(Float.NaN, 0f, 1.5f, .25f, -1f, .5f, 2f) to listOf(.25f, .25f, .5f),
                ),
            ) { (list, weights) ->
                segmentWeights(list).shouldBeAbout(weights)
            }
        }

        it("TC-8: fold each sliver into its neighbour") {
            checkAll(Exhaustive.of(chapters(0f, .01f, .5f), chapters(0f, .5f, .99f))) { list ->
                segmentWeights(list).shouldBeAbout(listOf(.5f, .5f))
            }
        }

        context("when there are more than 24 segments") {
            it("TC-10: group down to the limit without losing length, for each count") {
                checkAll(Exhaustive.of(25, 49, 60, 100)) { count ->
                    val weights = segmentWeights(evenChapters(count))

                    (weights.size <= 24) shouldBe true
                    weights.sum() shouldBe (1f plusOrMinus 1e-4f)
                }
            }
        }
    }

    describe("the bar's segments") {
        it("TC-12: fill with their part of each fraction") {
            checkAll(
                Exhaustive.of(
                    .5f to listOf(1f, 1f, 0f),
                    .375f to listOf(1f, .5f, 0f),
                    1f to listOf(1f, 1f, 1f),
                    -.5f to listOf(0f, 0f, 0f),
                    1.5f to listOf(1f, 1f, 1f),
                    Float.NaN to listOf(0f, 0f, 0f),
                ),
            ) { (fraction, fills) ->
                barSegments(listOf(.25f, .25f, .5f), fraction).map { it.fill }.shouldBeAbout(fills)
            }
        }
    }

    describe("the flag") {
        it("TC-14: stays inside the bar, for each position") {
            checkAll(
                Exhaustive.of(
                    Triple(0f, 60f, 30f),
                    Triple(300f, 60f, 270f),
                    Triple(150f, 60f, 150f),
                    Triple(-10f, 60f, 30f),
                    Triple(400f, 60f, 270f),
                    Triple(100f, 400f, 150f),
                ),
            ) { (x, flagWidth, center) ->
                flagCenterX(x, 300f, flagWidth) shouldBe (center plusOrMinus 1e-4f)
            }
        }
    }

    describe("the fill's edge") {
        it("TC-93: accounts for the gaps, for each kind of fill") {
            checkAll(
                Exhaustive.of(
                    listOf(BarSegment(.5f, 1f), BarSegment(.5f, 0f)) to 50f,
                    listOf(BarSegment(.5f, 1f), BarSegment(.5f, .5f)) to 78f,
                    listOf(BarSegment(.5f, 0f), BarSegment(.5f, 0f)) to 0f,
                    listOf(BarSegment(.5f, 1f), BarSegment(.5f, 1f)) to 103f,
                    emptyList<BarSegment>() to 0f,
                ),
            ) { (segments, edge) ->
                fillEdgeX(segments, 103f, 3f) shouldBe (edge plusOrMinus 1e-4f)
            }
        }
    }
})
