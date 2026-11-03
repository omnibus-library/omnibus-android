package com.omnibus.omnibus.feature.bookdetail.actionbar

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ranges.shouldBeIn

class ActionBarFillStopsTest : DescribeSpec({

    describe("the fill's gradient stops") {

        it("TC-24: zero fill has no fill stops") {
            fillStops(0f, 1000f, 36f).shouldBeNull()
            fillStops(-0f, 1000f, 36f).shouldBeNull()
        }

        it("TC-25: soft edge sits symmetrically around the fill") {
            val stops = fillStops(0.5f, 1000f, 36f)!!
            stops.solidEnd shouldBe (0.464f plusOrMinus 1e-5f)
            stops.fadeEnd shouldBe (0.536f plusOrMinus 1e-5f)
            (stops.solidEnd <= stops.fadeEnd) shouldBe true
        }

        it("TC-26: stops are clamped to the pill") {
            val low = fillStops(0.01f, 1000f, 36f)!!
            low.solidEnd shouldBe 0f
            low.fadeEnd shouldBe (0.046f plusOrMinus 1e-5f)

            val high = fillStops(0.99f, 1000f, 36f)!!
            high.solidEnd shouldBe (0.954f plusOrMinus 1e-5f)
            high.fadeEnd shouldBe 1f

            for (stops in listOf(low, high)) {
                stops.solidEnd shouldBeIn 0f..1f
                stops.fadeEnd shouldBeIn 0f..1f
                (stops.solidEnd <= stops.fadeEnd) shouldBe true
            }
        }

        it("TC-27: full fill covers the pill") {
            fillStops(1f, 1000f, 36f) shouldBe FillStops(1f, 1f)
            fillStops(1.5f, 1000f, 36f) shouldBe FillStops(1f, 1f)
        }

        it("TC-28: degenerate inputs give valid stops") {
            fillStops(Float.NaN, 1000f, 36f).shouldBeNull()
            fillStops(-1f, 1000f, 36f).shouldBeNull()
            fillStops(2f, 1000f, 36f) shouldBe FillStops(1f, 1f)
            fillStops(0.5f, 0f, 36f) shouldBe FillStops(0.5f, 0.5f)
            fillStops(0.5f, 1000f, 0f) shouldBe FillStops(0.5f, 0.5f)
            fillStops(0.5f, Float.NaN, 36f) shouldBe FillStops(0.5f, 0.5f)
        }
    }
})
