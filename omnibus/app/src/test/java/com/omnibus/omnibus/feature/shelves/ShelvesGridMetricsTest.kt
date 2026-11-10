package com.omnibus.omnibus.feature.shelves

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ShelvesGridMetricsTest : DescribeSpec({

    describe("the column count") {
        it("#257 TC-9: grows the columns with the width, never below one") {
            // Widths from none to very wide, ascending: one column up to 324 dp, two from there, eleven at 2000 dp.
            listOf(0f, 100f, 156f, 323.9f, 324f, 2000f).map { ShelvesGridMetrics.columns(it) } shouldBe
                listOf(1, 1, 1, 1, 2, 11)
        }
    }

    describe("the cell width") {
        it("#257 TC-9: keeps each cell between the minimum and the maximum with two columns or more") {
            checkAll(Exhaustive.of(324f, 2000f)) { width ->
                val cell = ShelvesGridMetrics.cellWidth(width)

                cell shouldBeGreaterThanOrEqualTo 156f
                cell shouldBeLessThanOrEqualTo 300f
            }
        }

        it("#257 TC-9: never makes a single column wider than the maximum") {
            checkAll(Exhaustive.of(200f, 323.9f)) { width ->
                ShelvesGridMetrics.cellWidth(width) shouldBeLessThanOrEqualTo 300f
            }
        }
    }
})
