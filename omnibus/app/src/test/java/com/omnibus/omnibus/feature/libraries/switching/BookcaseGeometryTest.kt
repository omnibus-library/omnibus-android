package com.omnibus.omnibus.feature.libraries.switching

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class BookcaseGeometryTest : DescribeSpec({

    describe("laying out the spines") {

        it("#369 TC-29: places spine i at the margin plus i spine widths and gaps, all on shelf 0 while they fit") {
            checkAll(Exhaustive.of(1, 5, 8), Exhaustive.of(360.dp, 840.dp)) { count, width ->
                val slots = BookcaseGeometry.layout(count, width)

                slots.size shouldBe count
                slots.forEachIndexed { index, slot ->
                    slot.shelf shouldBe 0
                    slot.x shouldBe expectedX(index)
                }
            }
        }

        it("#370 AC1: fits as many spines on a shelf as its width holds, and an exact fit still fits") {
            // 20 dp margins on each side, then n spines of 30 dp with 7 dp gaps between them.
            BookcaseGeometry.spinesPerShelf(360.dp) shouldBe 8
            BookcaseGeometry.spinesPerShelf((40 + 37 * 9 - 7).dp) shouldBe 9
            BookcaseGeometry.spinesPerShelf((40 + 37 * 9 - 8).dp) shouldBe 8
        }

        it("#370 AC1: starts the next library on a new shelf, filling each shelf from the left in list order") {
            val slots = BookcaseGeometry.layout(count = 19, availableWidth = 360.dp)

            slots.map { it.shelf } shouldBe List(8) { 0 } + List(8) { 1 } + List(3) { 2 }
            slots.map { it.x } shouldBe List(8, ::expectedX) + List(8, ::expectedX) + List(3, ::expectedX)
        }

        it("#370 AC1: has no limit on libraries") {
            val slots = BookcaseGeometry.layout(count = 200, availableWidth = 360.dp)

            slots.size shouldBe 200
            slots.last() shouldBe SpineSlot(shelf = 24, x = expectedX(7))
        }

        it("#370 AC3: caps a shelf at 560 dp inside its margins however wide the bookcase is") {
            checkAll(Exhaustive.of(600.dp, 840.dp, 1280.dp)) { width ->
                BookcaseGeometry.spinesPerShelf(width) shouldBe 15
            }
        }

        it("#370: keeps at least one spine on a shelf when the window is narrower than a spine") {
            BookcaseGeometry.layout(count = 2, availableWidth = 10.dp) shouldBe
                listOf(SpineSlot(shelf = 0, x = expectedX(0)), SpineSlot(shelf = 1, x = expectedX(0)))
        }
    }
})

/** The spec's position for slot [index] on its shelf: a 20 dp margin, then 30 dp spines with 7 dp gaps (37 dp each). */
private fun expectedX(index: Int): Dp = (20 + 37 * index).dp
