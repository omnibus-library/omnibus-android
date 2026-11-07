package com.omnibus.omnibus.data.progress

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

/** A server record that carries a position, so only its clocks decide. */
private fun positioned() = randomServerReadingPosition().copy(cfi = "epubcfi(/6/8)")

class PositionSyncTest : DescribeSpec({

    describe("deciding whether a record is a position") {

        context("when the record has a CFI") {
            it("TC-59: carries a position, at any percent, padded or not") {
                checkAll(
                    Exhaustive.of("epubcfi(/6/4)", "  epubcfi(/6/4)"),
                    Exhaustive.of(null, 0, 1, 57),
                ) { cfi, percent ->
                    PositionSync.carriesPosition(cfi, percent) shouldBe true
                }
            }
        }

        context("when the record has no CFI") {
            it("TC-59: carries a position with a percent above zero") {
                checkAll(Exhaustive.of("", "  ", null, "pdf-page:3", "comic-page:2"), Exhaustive.of(1, 57)) { cfi, percent ->
                    PositionSync.carriesPosition(cfi, percent) shouldBe true
                }
            }

            it("TC-59: carries no position without one") {
                checkAll(Exhaustive.of("", "  ", null, "pdf-page:3", "comic-page:2"), Exhaustive.of(null, 0)) { cfi, percent ->
                    PositionSync.carriesPosition(cfi, percent) shouldBe false
                }
            }
        }
    }

    describe("deciding whether the server's position is newer") {

        it("TC-56: is strictly greater on the ordering clock") {
            checkAll(Exhaustive.of(101L to true, 100L to false, 99L to false)) { (clock, expected) ->
                val local = randomReadingPosition().copy(clientUpdatedAt = 100)

                PositionSync.isNewer(positioned().copy(clientUpdatedAt = clock), local) shouldBe expected
            }
        }

        context("when there is no local position") {
            it("TC-56: is newer") {
                PositionSync.isNewer(positioned().copy(clientUpdatedAt = 1), null) shouldBe true
            }
        }

        context("when the record has a client clock") {
            it("TC-56: orders by it over a later server clock") {
                val local = randomReadingPosition().copy(clientUpdatedAt = 100)

                PositionSync.isNewer(positioned().copy(clientUpdatedAt = 99, updatedAt = 500), local) shouldBe false
            }
        }

        context("when the record has no client clock") {
            it("TC-56: orders by the server clock") {
                val local = randomReadingPosition().copy(clientUpdatedAt = 100)

                PositionSync.isNewer(positioned().copy(clientUpdatedAt = null, updatedAt = 101), local) shouldBe true
            }
        }

        context("when the record carries no position") {
            it("TC-60: is never newer, however late its clock") {
                checkAll(Exhaustive.of(null, "pdf-page:3")) { cfi ->
                    PositionSync.isNewer(
                        randomServerReadingPosition().copy(cfi = cfi, percent = null, clientUpdatedAt = Long.MAX_VALUE / 2),
                        null,
                    ) shouldBe false
                }
            }
        }
    }
})
