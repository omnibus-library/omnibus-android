package com.omnibus.omnibus.feature.bookdetail.highlights

import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.ui.theme.Oklch
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class HighlightRibbonTest : DescribeSpec({

    describe("the ribbon colours") {

        it("TC-26: each colour has its design tint and its spoken label, and all are distinct") {
            HighlightColor.Amber.tint() shouldBe Oklch(0.78, 0.14, 75.0).toColor()
            HighlightColor.Green.tint() shouldBe Oklch(0.78, 0.14, 150.0).toColor()
            HighlightColor.Blue.tint() shouldBe Oklch(0.74, 0.12, 245.0).toColor()
            HighlightColor.Rose.tint() shouldBe Oklch(0.74, 0.14, 10.0).toColor()
            HighlightColor.Violet.tint() shouldBe Oklch(0.74, 0.12, 300.0).toColor()
            HighlightColor.entries.map { it.tint() }.distinct().size shouldBe 5

            HighlightColor.Amber.labelRes shouldBe R.string.book_detail_highlights_color_amber
            HighlightColor.Green.labelRes shouldBe R.string.book_detail_highlights_color_green
            HighlightColor.Blue.labelRes shouldBe R.string.book_detail_highlights_color_blue
            HighlightColor.Rose.labelRes shouldBe R.string.book_detail_highlights_color_rose
            HighlightColor.Violet.labelRes shouldBe R.string.book_detail_highlights_color_violet
            HighlightColor.entries.map { it.labelRes }.distinct().size shouldBe 5
        }
    }
})
