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

        it("#369 TC-29: places spine i at the margin plus i spine widths and gaps, all on shelf 0") {
            checkAll(Exhaustive.of(1, 5, 8), Exhaustive.of(360.dp, 840.dp)) { count, width ->
                val slots = BookcaseGeometry.layout(count, width)

                slots.size shouldBe count
                slots.forEachIndexed { index, slot ->
                    slot.shelf shouldBe 0
                    slot.x shouldBe expectedX(index)
                }
            }
        }
    }
})

/** The spec's position for slot [index]: a 20 dp margin, then 30 dp spines with 7 dp gaps (37 dp each). */
private fun expectedX(index: Int): Dp = (20 + 37 * index).dp
