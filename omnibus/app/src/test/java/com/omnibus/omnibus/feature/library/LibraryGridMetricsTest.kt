package com.omnibus.omnibus.feature.library

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

class LibraryGridMetricsTest : DescribeSpec({

    describe("columns") {
        it("TC-67: a 390 dp phone (350 dp available) gets 2, and wider screens get more") {
            LibraryGridMetrics.columns(350f) shouldBe 2
            LibraryGridMetrics.columns(700f) shouldBe 5
            LibraryGridMetrics.columns(1000f) shouldBe 7
            LibraryGridMetrics.columns(100f) shouldBe 1
        }

        it("TC-69: steps up exactly at 240 and 368 dp, and never drops below one column") {
            LibraryGridMetrics.columns(239f) shouldBe 1
            LibraryGridMetrics.columns(240f) shouldBe 2
            LibraryGridMetrics.columns(367f) shouldBe 2
            LibraryGridMetrics.columns(368f) shouldBe 3
            LibraryGridMetrics.columns(0f) shouldBe 1
        }
    }

    describe("cell width") {
        it("TC-68: fills a 350 dp row to about 167 dp per cell") {
            LibraryGridMetrics.cellWidth(350f) shouldBe (167f plusOrMinus 0.01f)
        }

        it("TC-68: caps a single cell at 168 dp on a 200 dp row") {
            LibraryGridMetrics.cellWidth(200f) shouldBe (168f plusOrMinus 0.01f)
        }
    }
})
