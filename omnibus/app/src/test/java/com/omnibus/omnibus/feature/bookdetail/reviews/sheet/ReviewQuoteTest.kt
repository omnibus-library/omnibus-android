package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.feature.bookdetail.highlights.HighlightRowModel
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class ReviewQuoteTest : DescribeSpec({

    /** A highlight with the given passage, and a placed chapter when [fraction] and [format] are given. */
    fun highlight(
        id: Long,
        text: String?,
        note: String? = null,
        color: HighlightColor = HighlightColor.Amber,
        format: String? = null,
        fraction: Float? = null,
        createdAtMillis: Long? = 1_700_000_000_000L,
    ) = Highlight(
        id = id,
        text = text,
        note = note,
        color = color,
        location = null,
        format = format,
        createdAtMillis = createdAtMillis,
        fraction = fraction,
    )

    /** Three chapters starting at 0, 0.25 and 0.5 of the book. */
    val chapters = listOf(BookChapter(0, null, 0f), BookChapter(1, null, 0.25f), BookChapter(2, null, 0.5f))

    describe("quoteIntoReview") {

        it("TC-24: one line into an empty body is a quote and a blank line") {
            quoteIntoReview("A line", null) shouldBe "> A line\n\n"
        }

        it("TC-25: every line of a multi-line quote is prefixed, and blank interior lines become a bare >") {
            quoteIntoReview("L1\nL2\n\nL4", null) shouldBe "> L1\n> L2\n>\n> L4\n\n"
        }

        it("TC-26: a blank body gives the same result as no body") {
            quoteIntoReview("A line", "") shouldBe quoteIntoReview("A line", null)
            quoteIntoReview("A line", "  \n ") shouldBe quoteIntoReview("A line", null)
            quoteIntoReview("A line", "") shouldBe "> A line\n\n"
        }

        it("TC-27: the quote goes before the existing body, which is unchanged") {
            quoteIntoReview("A line", "My take") shouldBe "> A line\n\nMy take"
        }

        it("TC-28: line endings are normalised, the quote is trimmed, and each line loses trailing spaces") {
            quoteIntoReview("  A\r\nB  \r\n\n", null) shouldBe "> A\n> B\n\n"
        }
    }

    describe("highlightPickerRows") {

        it("TC-29: highlights without text are dropped and the rest keep their order") {
            val rows = highlightPickerRows(
                highlights = listOf(
                    highlight(1L, text = null),
                    highlight(2L, text = "   "),
                    highlight(3L, text = "Keep me"),
                    highlight(4L, text = "Also"),
                ),
                chapters = chapters,
            )

            rows.map { it.id } shouldContainExactly listOf(3L, 4L)
            rows.map { it.text } shouldContainExactly listOf("Keep me", "Also")
        }

        it("TC-30: a row carries its colour and date, and a chapter footer only when the chapter is known") {
            val placed = highlight(
                id = 7L,
                text = "Placed",
                note = "  ",
                color = HighlightColor.Rose,
                format = "epub",
                fraction = 0.3f,
            )
            val unplaced = highlight(id = 8L, text = "Unplaced", format = null, fraction = 0.3f)

            val rows = highlightPickerRows(listOf(placed, unplaced), chapters)

            rows shouldContainExactly listOf(
                HighlightRowModel(
                    id = 7L,
                    text = "Placed",
                    note = null,
                    color = HighlightColor.Rose,
                    chapterNumber = 2,
                    createdAtMillis = 1_700_000_000_000L,
                ),
                HighlightRowModel(
                    id = 8L,
                    text = "Unplaced",
                    note = null,
                    color = HighlightColor.Amber,
                    chapterNumber = null,
                    createdAtMillis = 1_700_000_000_000L,
                ),
            )
        }

        it("TC-31: no highlights, or only text-less ones, give no rows") {
            highlightPickerRows(emptyList(), chapters).shouldBeEmpty()
            highlightPickerRows(listOf(highlight(1L, text = null), highlight(2L, text = "")), chapters).shouldBeEmpty()
        }
    }
})
