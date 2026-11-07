package com.omnibus.omnibus.feature.library

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class LibraryGridMetricsTest : DescribeSpec({

    describe("the column count") {
        it("TC-69: steps up exactly at 240 and 368 dp, with at least one column") {
            checkAll(Exhaustive.of(0f to 1, 239f to 1, 240f to 2, 367f to 2, 368f to 3)) { (width, columns) ->
                LibraryGridMetrics.columns(width) shouldBe columns
            }
        }
    }

    describe("the cell width") {
        it("TC-68: shares the row evenly, up to the largest cell") {
            LibraryGridMetrics.cellWidth(350f) shouldBe (167f plusOrMinus 0.01f)
            LibraryGridMetrics.cellWidth(200f) shouldBe (168f plusOrMinus 0.01f)
        }
    }
})
