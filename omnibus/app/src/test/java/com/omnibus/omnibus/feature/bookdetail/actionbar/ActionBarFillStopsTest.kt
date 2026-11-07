package com.omnibus.omnibus.feature.bookdetail.actionbar

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ActionBarFillStopsTest : DescribeSpec({

    describe("the fill's gradient stops") {
        it("TC-24, TC-28: draw no fill for each of zero, a negative, NaN") {
            checkAll(Exhaustive.of(0f, -0f, -1f, Float.NaN)) { fill ->
                fillStops(fill, 1000f, 36f).shouldBeNull()
            }
        }

        it("TC-26: stay on the pill at each end") {
            val low = fillStops(0.01f, 1000f, 36f)!!
            val high = fillStops(0.99f, 1000f, 36f)!!

            low.solidEnd shouldBe 0f
            high.fadeEnd shouldBe 1f
        }

        it("TC-27: cover the pill for each fill from one up") {
            checkAll(Exhaustive.of(1f, 1.5f, Float.POSITIVE_INFINITY)) { fill ->
                fillStops(fill, 1000f, 36f) shouldBe FillStops(1f, 1f)
            }
        }

        it("TC-28: have no soft edge for each unusable size") {
            checkAll(Exhaustive.of(0f to 36f, 1000f to 0f, Float.NaN to 36f)) { (width, soft) ->
                fillStops(0.5f, width, soft) shouldBe FillStops(0.5f, 0.5f)
            }
        }
    }
})
