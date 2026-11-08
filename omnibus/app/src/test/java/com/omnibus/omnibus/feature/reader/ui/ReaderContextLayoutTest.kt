package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.testing.randomInt
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.pair
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ReaderContextLayoutTest : DescribeSpec({

    val gap = 12f

    // Whole pixel sizes, since Arb.float also emits NaN, which is no area.
    val areas = Arb.int(100..3000)
    val reserved = Arb.int(0..300)

    fun layoutOf(width: Int, height: Int, top: Int, bottom: Int) =
        contextCardLayout(width.toFloat(), height.toFloat(), top.toFloat(), bottom.toFloat(), gap)

    describe("the card scale") {
        context("for a phone-shaped area") {
            it("TC-1: is 0.80") {
                checkAll(Exhaustive.of(0.3f, 0.46f, 0.5f)) { aspect ->
                    contextCardScale(aspect * 1000f, 1000f, 0f, 0f) shouldBe (0.8f plusOrMinus 1e-4f)
                }
            }
        }

        it("TC-1: is 0.75 for a square area") {
            contextCardScale(400f, 400f, 0f, 0f) shouldBe (0.75f plusOrMinus 1e-4f)
        }

        context("for a mid-aspect area") {
            it("TC-1: is strictly between 0.75 and 0.80") {
                val scale = contextCardScale(750f, 1000f, 0f, 0f)

                scale shouldBeGreaterThan 0.75f
                scale shouldBeLessThan 0.8f
            }

            it("TC-1: falls as the area gets squarer") {
                val scales = listOf(0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1f).map { aspect ->
                    contextCardScale(aspect * 1000f, 1000f, 0f, 0f)
                }

                scales.zipWithNext().forEach { (wider, squarer) -> wider shouldBeGreaterThan squarer }
            }
        }

        context("when the card would not fit between the reserved space") {
            it("TC-1: shrinks until the card fits above the bottom reserved space") {
                // A landscape area 360 high: the fit (280 / 360) is below the phone shape's 0.8.
                val scale = contextCardScale(800f, 360f, 60f, 20f)

                scale shouldBeLessThan 0.8f
                (360f * scale + 60f + 20f) shouldBeLessThanOrEqualTo (360f + 1e-3f)
            }

            it("TC-1: never goes below 0.3") {
                contextCardScale(100f, 100f, 90f, 90f) shouldBe (0.3f plusOrMinus 1e-4f)
            }
        }

        context("for a non-positive area") {
            it("TC-1: is 1") {
                checkAll(Exhaustive.of(0f to 500f, 500f to 0f, -1f to 500f, 500f to -1f)) { (width, height) ->
                    contextCardScale(width, height, 0f, 0f) shouldBe 1f
                }
            }
        }

        it("TC-1: stays between 0.3 and 0.8 for any area and reserved space") {
            checkAll(Arb.pair(areas, areas), Arb.pair(reserved, reserved)) { (width, height), (top, bottom) ->
                val scale = contextCardScale(width.toFloat(), height.toFloat(), top.toFloat(), bottom.toFloat())

                scale shouldBeGreaterThanOrEqualTo 0.3f
                scale shouldBeLessThanOrEqualTo 0.8f
            }
        }

        context("with reserved space up to 30% of the height") {
            it("TC-1: fits the card between the reserved space") {
                checkAll(Arb.pair(areas, areas), Arb.pair(Arb.int(0..30), Arb.int(0..30))) { (width, height), (topPercent, bottomPercent) ->
                    val top = height * topPercent / 100f
                    val bottom = height * bottomPercent / 100f
                    val scale = contextCardScale(width.toFloat(), height.toFloat(), top, bottom)

                    (height * scale + top + bottom) shouldBeLessThanOrEqualTo (height.toFloat() + 1e-2f)
                }
            }
        }
    }

    describe("the card layout") {
        it("TC-2: keeps the area's aspect ratio") {
            checkAll(Arb.pair(areas, areas), Arb.pair(reserved, reserved)) { (width, height), (top, bottom) ->
                val card = layoutOf(width, height, top, bottom).card

                (card.width / card.height) shouldBe ((width.toFloat() / height.toFloat()) plusOrMinus 1e-3f)
            }
        }

        it("TC-2: centres the card horizontally") {
            checkAll(Arb.pair(areas, areas), Arb.pair(reserved, reserved)) { (width, height), (top, bottom) ->
                val card = layoutOf(width, height, top, bottom).card

                (card.left + card.width / 2f) shouldBe (width / 2f plusOrMinus 1e-2f)
            }
        }

        it("TC-2: puts the card's top at the reserved top") {
            checkAll(Arb.pair(areas, areas), Arb.pair(reserved, reserved)) { (width, height), (top, bottom) ->
                layoutOf(width, height, top, bottom).card.top shouldBe top.toFloat()
            }
        }

        it("TC-2: sits each peek the gap beyond the card's side") {
            checkAll(Arb.pair(areas, areas), Arb.pair(reserved, reserved)) { (width, height), (top, bottom) ->
                val layout = layoutOf(width, height, top, bottom)

                layout.previous.right shouldBe (layout.card.left - gap plusOrMinus 1e-2f)
                layout.next.left shouldBe (layout.card.right + gap plusOrMinus 1e-2f)
            }
        }

        it("TC-2: gives each peek the card's size") {
            checkAll(Arb.pair(areas, areas), Arb.pair(reserved, reserved)) { (width, height), (top, bottom) ->
                val layout = layoutOf(width, height, top, bottom)

                layout.previous.width shouldBe (layout.card.width plusOrMinus 1e-2f)
                layout.previous.height shouldBe (layout.card.height plusOrMinus 1e-2f)
                layout.next.width shouldBe (layout.card.width plusOrMinus 1e-2f)
                layout.next.height shouldBe (layout.card.height plusOrMinus 1e-2f)
            }
        }
    }

    describe("the peeks") {
        fun location(page: Int, totalPages: Int, atEnd: Boolean) =
            randomRelocateData().copy(page = page, totalPages = totalPages, atEnd = atEnd)

        it("TC-3: shows both neighbours with no location") {
            contextPeeks(null) shouldBe setOf(PeekSide.Previous, PeekSide.Next)
        }

        context("on a middle page") {
            it("TC-3: shows both neighbours") {
                val middle = location(page = randomInt(2..999), totalPages = randomInt(1000..2000), atEnd = false)

                contextPeeks(middle) shouldBe setOf(PeekSide.Previous, PeekSide.Next)
            }
        }

        context("on the first page") {
            it("TC-3: drops the previous peek") {
                val first = location(page = 1, totalPages = randomInt(1000..2000), atEnd = false)

                contextPeeks(first) shouldBe setOf(PeekSide.Next)
            }
        }

        context("at the end of the book") {
            it("TC-3: drops the next peek") {
                val last = location(page = randomInt(2..999), totalPages = randomInt(1000..2000), atEnd = true)

                contextPeeks(last) shouldBe setOf(PeekSide.Previous)
            }
        }

        context("in a single-page book") {
            it("TC-3: shows neither neighbour") {
                contextPeeks(location(page = 1, totalPages = 1, atEnd = true)) shouldBe emptySet()
            }
        }

        context("without page numbers") {
            it("TC-3: keeps the previous peek, for a zero or missing page total") {
                checkAll(Exhaustive.of(0 to 0, 1 to 0)) { (page, totalPages) ->
                    contextPeeks(location(page = page, totalPages = totalPages, atEnd = false)) shouldBe
                        setOf(PeekSide.Previous, PeekSide.Next)
                }
            }
        }
    }
})
