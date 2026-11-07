package com.omnibus.omnibus.feature.bookdetail.tabs

import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class BookDetailTabRulesTest : DescribeSpec({

    describe("the direction a tab enters from") {

        it("#94 TC-10: follows tab order, forward, backward or none for the same tab") {
            checkAll(
                Exhaustive.of(
                    Triple(BookDetailTab.Overview, BookDetailTab.Files, 1),
                    Triple(BookDetailTab.Files, BookDetailTab.Reviews, -1),
                    Triple(BookDetailTab.Reviews, BookDetailTab.Reviews, null),
                ),
            ) { (from, to, expected) ->
                tabEntryDirection(from, to) shouldBe expected
            }
        }
    }

    // In these, the strip sits 700 px down the page and pins 100 px from the top: its pin point is scroll 600.
    describe("pinning the strip") {

        it("#94 TC-12: moves it only past the pin point") {
            checkAll(Exhaustive.of(600 to 0, 601 to 1, 1000 to 400)) { (scroll, expected) ->
                stripPinOffsetPx(scroll, 700, 100) shouldBe expected
            }
        }

        context("when the strip starts above the pin") {
            it("#94 TC-12: pins from the start") {
                stripPinOffsetPx(0, 50, 100) shouldBe 50
            }
        }
    }

    describe("reporting the strip as pinned") {

        it("reports pinned from one slack pixel before the pin point") {
            tabsPinned(598, 700, 100, 1) shouldBe false
            tabsPinned(599, 700, 100, 1) shouldBe true
        }

        context("when the host is unplaced") {
            it("reports unpinned") {
                tabsPinned(500, -1, 0, 1) shouldBe false
            }
        }
    }

    describe("the scroll a jump lands on") {

        it("#94 TC-13: is the pin point, clamped to the scroll range") {
            checkAll(
                Exhaustive.of(
                    Triple(700, 5000, 600),
                    Triple(700, 400, 400),
                    Triple(50, 400, 0),
                ),
            ) { (stripTop, maxScroll, expected) ->
                jumpScrollTarget(stripTop, 100, maxScroll) shouldBe expected
            }
        }

        it("#94 TC-14: leaves an anchor its clearance under the strip, clamped to the scroll range") {
            checkAll(
                Exhaustive.of(
                    Triple(900, 5000, 900 - (100 + 50 + 16)),
                    Triple(900, 400, 400),
                    Triple(50, 5000, 0),
                ),
            ) { (anchorTop, maxScroll, expected) ->
                anchorScrollTarget(anchorTop, 100, 50, 16, maxScroll) shouldBe expected
            }
        }
    }
})
