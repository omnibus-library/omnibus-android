package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.feature.reader.ui.RelocateData
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class PercentFloorTest : DescribeSpec({

    fun page(pct: Int, totalPages: Int = 100) = RelocateData(cfi = "epubcfi(/6/2)", pct = pct, totalPages = totalPages)

    describe("PercentFloor") {

        // TC-102: reading forward holds the opened percent; the page's own percent only counts once it passes it.
        it("TC-102: the floor holds the opened percent while reading forward") {
            val floor = PercentFloor(openedPercent = 57)

            val written = listOf(page(3, totalPages = 0), page(50), page(52), page(50), page(60)).map(floor::percentFor)

            written shouldContainExactly listOf(null, 57, 57, 57, 60)
        }

        // TC-103: once the reader goes back behind the landing, the floor is off for good.
        it("TC-103: the floor turns off once behind the landing") {
            val floor = PercentFloor(openedPercent = 57)
            floor.percentFor(page(50)) shouldBe 57

            val written = listOf(page(45), page(60), page(52)).map(floor::percentFor)

            written shouldContainExactly listOf(45, 60, 52)
        }

        // TC-104: without an opened percent there is no floor.
        it("TC-104: no floor without an opened percent") {
            val floor = PercentFloor(openedPercent = null)

            val written = listOf(page(52), page(3, totalPages = 0)).map(floor::percentFor)

            written shouldContainExactly listOf(52, null)
        }
    }
})
