package com.omnibus.omnibus.feature.reader.ui

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/** A page width from a 320 dp phone to a 1280 dp tablet spread, in dp. */
private fun arbBoundsWidth(): Arb<Float> = Arb.float(320f, 1280f, includeNaNs = false)

/** A page height from a short window to a tall phone, in dp. */
private fun arbBoundsHeight(): Arb<Float> = Arb.float(480f, 2000f, includeNaNs = false)

/** A selection of one to four lines anywhere on a page, in dp. */
private fun arbRects(): Arb<List<PageRect>> = Arb.list(
    arbitrary {
        PageRect(
            x = Arb.float(0f, 1200f, includeNaNs = false).bind().toDouble(),
            y = Arb.float(0f, 2000f, includeNaNs = false).bind().toDouble(),
            width = Arb.float(0f, 400f, includeNaNs = false).bind().toDouble(),
            height = Arb.float(1f, 60f, includeNaNs = false).bind().toDouble(),
        )
    },
    1..4,
)

class ReaderSelectionPlacementTest : DescribeSpec({

    describe("placing the selection menu") {
        context("when there is room above the selection") {
            it("#54 TC-6: sits with its bottom 20 dp above the top of the selection") {
                val rect = PageRect(x = 100.0, y = 300.0, width = 200.0, height = 20.0)

                val placement = selectionMenuPlacement(listOf(rect), boundsWidth = 360f, boundsHeight = 640f)
                    .shouldNotBeNull()

                placement.top + SelectionMenuMetrics.HEIGHT shouldBe (280f plusOrMinus 0.01f)
            }

            it("#54 TC-6: points its tail down at the selection") {
                val rect = PageRect(x = 100.0, y = 300.0, width = 200.0, height = 20.0)

                selectionMenuPlacement(listOf(rect), boundsWidth = 360f, boundsHeight = 640f)
                    .shouldNotBeNull().tailUp shouldBe false
            }
        }

        context("when the selection is under the chrome band") {
            it("#54 TC-6: sits with its top 20 dp below the bottom of the selection") {
                val rect = PageRect(x = 100.0, y = 20.0, width = 200.0, height = 20.0)

                val placement = selectionMenuPlacement(listOf(rect), boundsWidth = 360f, boundsHeight = 640f)
                    .shouldNotBeNull()

                placement.top shouldBe (60f plusOrMinus 0.01f)
            }

            it("#54 TC-6: points its tail up at the selection") {
                val rect = PageRect(x = 100.0, y = 20.0, width = 200.0, height = 20.0)

                selectionMenuPlacement(listOf(rect), boundsWidth = 360f, boundsHeight = 640f)
                    .shouldNotBeNull().tailUp shouldBe true
            }
        }

        context("when the selection is in the right column of a two-column spread") {
            it("#54 TC-6: centres the menu on the selection's horizontal span") {
                val rect = PageRect(x = 600.0, y = 300.0, width = 300.0, height = 20.0)

                val placement = selectionMenuPlacement(listOf(rect), boundsWidth = 1080f, boundsHeight = 640f)
                    .shouldNotBeNull()

                placement.left + placement.width / 2 shouldBe (750f plusOrMinus 0.01f)
            }
        }
    }

    describe("placing the selection menu over any selection and page width") {
        it("#54 TC-7: is min(300, page width - 20) dp wide") {
            checkAll(arbBoundsWidth(), arbBoundsHeight(), arbRects()) { boundsWidth, boundsHeight, rects ->
                val placement = selectionMenuPlacement(rects, boundsWidth, boundsHeight).shouldNotBeNull()

                placement.width shouldBe (minOf(300f, boundsWidth - 20f) plusOrMinus 0.01f)
            }
        }

        it("#54 TC-7: keeps the menu at least 10 dp from both side edges") {
            checkAll(arbBoundsWidth(), arbBoundsHeight(), arbRects()) { boundsWidth, boundsHeight, rects ->
                val placement = selectionMenuPlacement(rects, boundsWidth, boundsHeight).shouldNotBeNull()

                placement.left shouldBeGreaterThanOrEqual (10f - 0.01f)
                placement.left + placement.width shouldBeLessThanOrEqual (boundsWidth - 10f + 0.01f)
            }
        }

        it("#54 TC-7: keeps the tail's centre at least 26 dp from either end of the menu") {
            checkAll(arbBoundsWidth(), arbBoundsHeight(), arbRects()) { boundsWidth, boundsHeight, rects ->
                val placement = selectionMenuPlacement(rects, boundsWidth, boundsHeight).shouldNotBeNull()

                placement.tailCenterX shouldBeGreaterThanOrEqual (26f - 0.01f)
                placement.tailCenterX shouldBeLessThanOrEqual (placement.width - 26f + 0.01f)
            }
        }
    }
})
