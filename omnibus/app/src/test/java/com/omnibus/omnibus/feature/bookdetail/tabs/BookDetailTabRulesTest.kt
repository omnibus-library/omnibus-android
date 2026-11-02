package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.BookDetailAnchor
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class BookDetailTabRulesTest : DescribeSpec({

    describe("tab rules") {

        it("#94 TC-10: entry direction follows tab order") {
            BookDetailTab.entries.forEach { from ->
                BookDetailTab.entries.forEach { to ->
                    val expected = when {
                        from == to -> null
                        to.ordinal > from.ordinal -> 1
                        else -> -1
                    }
                    tabEntryDirection(from, to) shouldBe expected
                }
            }
            tabEntryDirection(BookDetailTab.Overview, BookDetailTab.Files) shouldBe 1
            tabEntryDirection(BookDetailTab.Files, BookDetailTab.Reviews) shouldBe -1
        }

        it("#94 TC-11: every anchor maps to a tab") {
            BookDetailAnchor.Series.tab shouldBe BookDetailTab.Overview
            BookDetailAnchor.entries.forEach { it.tab shouldBe BookDetailTab.Overview }
        }

        it("#94 TC-12: strip pin offset is zero before the pin point and tracks scroll after") {
            // stripTop 700, pinTop 100: the pin point is scroll 600.
            stripPinOffsetPx(0, 700, 100) shouldBe 0
            stripPinOffsetPx(599, 700, 100) shouldBe 0
            stripPinOffsetPx(600, 700, 100) shouldBe 0
            stripPinOffsetPx(601, 700, 100) shouldBe 1
            stripPinOffsetPx(1000, 700, 100) shouldBe 400
            // A strip already above the pin top is pinned from the start.
            stripPinOffsetPx(0, 50, 100) shouldBe 50
        }

        it("#94 TC-13: jump target is clamped to the scroll range") {
            jumpScrollTarget(700, 100, 5000) shouldBe 600
            jumpScrollTarget(700, 100, 400) shouldBe 400
            jumpScrollTarget(50, 100, 400) shouldBe 0
            jumpScrollTarget(700, 100, 0) shouldBe 0
        }

        it("#94 TC-14: anchor target leaves clearance under the strip and clamps") {
            anchorScrollTarget(900, 100, 50, 16, 5000) shouldBe 734
            anchorScrollTarget(900, 100, 50, 16, 400) shouldBe 400
            anchorScrollTarget(50, 100, 50, 16, 5000) shouldBe 0
            anchorScrollTarget(900, 100, 50, 0, 5000) shouldBe 750

            // Unclamped, the anchor sits exactly clearance below the pinned strip.
            val scroll = anchorScrollTarget(900, 100, 50, 16, 5000)
            (900 - scroll) shouldBe 100 + 50 + 16
        }

        it("#94 TC-15: host minimum height is viewport minus bar and action area plus slack") {
            tabHostMinHeight(640.dp, 74.dp, 80.dp) shouldBe 490.dp
            tabHostMinHeight(320.dp, 74.dp, 80.dp) shouldBe 170.dp
            tabHostMinHeight(100.dp, 74.dp, 80.dp) shouldBe 0.dp
        }
    }
})
