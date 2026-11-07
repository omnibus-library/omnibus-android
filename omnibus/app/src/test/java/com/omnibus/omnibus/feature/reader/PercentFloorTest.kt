package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.feature.reader.ui.randomRelocateData
import com.omnibus.omnibus.testing.randomInt
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class PercentFloorTest : DescribeSpec({

    fun page(pct: Int, totalPages: Int = randomInt(1..1000)) = randomRelocateData().copy(pct = pct, totalPages = totalPages)

    describe("the percent written for a page") {
        context("with an opened percent") {
            context("while reading forward from the landing") {
                it("TC-102: holds the opened percent until the page passes it") {
                    val floor = PercentFloor(openedPercent = 57)

                    listOf(page(50), page(52), page(50), page(60)).map(floor::percentFor) shouldContainExactly
                        listOf(57, 57, 57, 60)
                }
            }

            context("after going back behind the landing") {
                it("TC-103: writes the page's own percent for the rest of the open") {
                    val floor = PercentFloor(openedPercent = 57)
                    floor.percentFor(page(50))

                    listOf(page(45), page(60), page(52)).map(floor::percentFor) shouldContainExactly listOf(45, 60, 52)
                }
            }

            context("before the page has locations") {
                it("TC-102: writes no percent") {
                    PercentFloor(openedPercent = 57).percentFor(page(3, totalPages = 0)) shouldBe null
                }
            }
        }

        context("without an opened percent") {
            it("TC-104: writes the page's own percent") {
                PercentFloor(openedPercent = null).percentFor(page(52)) shouldBe 52
            }
        }
    }
})
