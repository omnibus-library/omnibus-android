package com.omnibus.omnibus.feature.reader.ui

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean

/** A page width from a 320 dp phone to a 1280 dp tablet spread, in dp. */
private fun arbBoundsWidth(): Arb<Float> = Arb.float(320f, 1280f, includeNaNs = false)

/** A page height from a short window to a tall phone, in dp. */
private fun arbBoundsHeight(): Arb<Float> = Arb.float(480f, 2000f, includeNaNs = false)

/**
 * A page, and a highlight of one to four lines on it, in dp. The lines start no lower than 60 dp above the page's
 * bottom, so they stay on the page.
 */
private data class Page(val width: Float, val height: Float, val rects: List<PageRect>)

private fun arbPage(): Arb<Page> = arbitrary {
    val height = arbBoundsHeight().bind()
    Page(
        width = arbBoundsWidth().bind(),
        height = height,
        rects = Arb.list(
            arbitrary {
                PageRect(
                    x = Arb.float(0f, 1200f, includeNaNs = false).bind().toDouble(),
                    y = Arb.float(0f, height - 60f, includeNaNs = false).bind().toDouble(),
                    width = Arb.float(0f, 400f, includeNaNs = false).bind().toDouble(),
                    height = Arb.float(1f, 60f, includeNaNs = false).bind().toDouble(),
                )
            },
            1..4,
        ).bind(),
    )
}

class ReaderHighlightMenuPlacementTest : DescribeSpec({

    describe("placing the highlight menu") {
        context("when there is room below the highlight") {
            it("#299 TC-7: sits a gap below the highlight") {
                val rect = PageRect(x = 100.0, y = 200.0, width = 120.0, height = 22.0)

                val placement = highlightMenuPlacement(
                    listOf(rect),
                    highlightMenuSize(hasSwitcher = false),
                    boundsWidth = 360f,
                    boundsHeight = 640f,
                ).shouldNotBeNull()

                placement.top shouldBe (230f plusOrMinus 0.01f)
            }

            it("#299 TC-7: centres the menu's tail on the highlight's span") {
                val rect = PageRect(x = 100.0, y = 200.0, width = 120.0, height = 22.0)

                val placement = highlightMenuPlacement(
                    listOf(rect),
                    highlightMenuSize(hasSwitcher = false),
                    boundsWidth = 360f,
                    boundsHeight = 640f,
                ).shouldNotBeNull()

                placement.left + placement.tailCenterX shouldBe (160f plusOrMinus 0.01f)
            }

            it("#299 TC-7: points its tail up at the highlight") {
                val rect = PageRect(x = 100.0, y = 200.0, width = 120.0, height = 22.0)

                highlightMenuPlacement(
                    listOf(rect),
                    highlightMenuSize(hasSwitcher = false),
                    boundsWidth = 360f,
                    boundsHeight = 640f,
                ).shouldNotBeNull().tailUp shouldBe true
            }
        }

        context("when the menu below would cross the bottom band") {
            it("#299 TC-8: sits a gap above the highlight") {
                val rect = PageRect(x = 100.0, y = 540.0, width = 120.0, height = 22.0)

                val placement = highlightMenuPlacement(
                    listOf(rect),
                    highlightMenuSize(hasSwitcher = false),
                    boundsWidth = 360f,
                    boundsHeight = 640f,
                ).shouldNotBeNull()

                placement.top + placement.height shouldBe (532f plusOrMinus 0.01f)
            }

            it("#299 TC-8: points its tail down at the highlight") {
                val rect = PageRect(x = 100.0, y = 540.0, width = 120.0, height = 22.0)

                highlightMenuPlacement(
                    listOf(rect),
                    highlightMenuSize(hasSwitcher = false),
                    boundsWidth = 360f,
                    boundsHeight = 640f,
                ).shouldNotBeNull().tailUp shouldBe false
            }
        }
    }

    describe("sizing the highlight menu") {
        it("#329 TC-10: is 240 dp wide and two rows tall without a switcher") {
            val size = highlightMenuSize(hasSwitcher = false)

            size.width shouldBe (240f plusOrMinus 0.01f)
            size.bodyHeight shouldBe (112.5f plusOrMinus 0.01f)
        }

        it("#329 TC-10: adds the switcher row and a rule with a switcher") {
            val size = highlightMenuSize(hasSwitcher = true)

            size.width shouldBe (240f plusOrMinus 0.01f)
            size.bodyHeight shouldBe (157f plusOrMinus 0.01f)
        }
    }

    describe("placing the highlight menu over any page, highlight and content") {
        it("#299 TC-10: keeps the menu at least 10 dp from both side edges") {
            checkAll(arbPage(), Exhaustive.boolean()) { page, hasSwitcher ->
                val placement = highlightMenuPlacement(
                    page.rects,
                    highlightMenuSize(hasSwitcher),
                    page.width,
                    page.height,
                ).shouldNotBeNull()

                placement.left shouldBeGreaterThanOrEqual (10f - 0.01f)
                placement.left + placement.width shouldBeLessThanOrEqual (page.width - 10f + 0.01f)
            }
        }

        it("#299 TC-10: keeps the menu inside the page, above its bottom band") {
            checkAll(arbPage(), Exhaustive.boolean()) { page, hasSwitcher ->
                val placement = highlightMenuPlacement(
                    page.rects,
                    highlightMenuSize(hasSwitcher),
                    page.width,
                    page.height,
                ).shouldNotBeNull()

                placement.top shouldBeGreaterThanOrEqual (0f - 0.01f)
                placement.top + placement.height shouldBeLessThanOrEqual (page.height - ReaderBands.Bottom.value + 0.01f)
            }
        }

        it("#299 TC-10: keeps the tail's centre clear of both ends of the body") {
            checkAll(arbPage(), Exhaustive.boolean()) { page, hasSwitcher ->
                val placement = highlightMenuPlacement(
                    page.rects,
                    highlightMenuSize(hasSwitcher),
                    page.width,
                    page.height,
                ).shouldNotBeNull()
                val tailInset = HighlightMenuMetrics.CORNER + HighlightMenuMetrics.TAIL_WIDTH / 2

                placement.tailCenterX shouldBeGreaterThanOrEqual (tailInset - 0.01f)
                placement.tailCenterX shouldBeLessThanOrEqual (placement.width - tailInset + 0.01f)
            }
        }
    }
})
