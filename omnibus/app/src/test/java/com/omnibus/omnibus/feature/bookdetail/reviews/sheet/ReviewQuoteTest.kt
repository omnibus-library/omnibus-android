package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import com.omnibus.omnibus.data.highlights.randomHighlight
import com.omnibus.omnibus.feature.bookdetail.highlights.HighlightRowModel
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ReviewQuoteTest : DescribeSpec({

    describe("quoting into a review") {
        it("TC-24, TC-26, TC-27: goes before the body, with a blank line, for each kind of body") {
            checkAll(
                Exhaustive.of(
                    null to "> A line\n\n",
                    "" to "> A line\n\n",
                    "  \n " to "> A line\n\n",
                    "My take" to "> A line\n\nMy take",
                ),
            ) { (body, review) ->
                quoteIntoReview("A line", body) shouldBe review
            }
        }

        it("TC-25, TC-28: marks every line of a normalised quote, for each shape of quote") {
            checkAll(
                Exhaustive.of(
                    "L1\nL2\n\nL4" to "> L1\n> L2\n>\n> L4\n\n",
                    "  A\r\nB  \r\n\n" to "> A\n> B\n\n",
                ),
            ) { (quote, review) ->
                quoteIntoReview(quote, null) shouldBe review
            }
        }
    }

    describe("the highlight picker's rows") {
        it("TC-29: are the highlights with text, in their order") {
            val first = randomString()
            val second = randomString()
            val highlights = listOf(null, "   ", first, second).map { randomHighlight().copy(text = it) }

            highlightPickerRows(highlights, emptyList()).map { it.text } shouldContainExactly listOf(first, second)
        }

        it("TC-30: carry the highlight's colour, date, chapter, with a blank note as none") {
            val chapters = listOf(BookChapter(0, null, 0f), BookChapter(1, null, 0.25f), BookChapter(2, null, 0.5f))
            val highlight = randomHighlight().copy(text = randomString(), note = "  ", format = "epub", fraction = 0.3f)

            highlightPickerRows(listOf(highlight), chapters) shouldContainExactly listOf(
                HighlightRowModel(
                    id = highlight.id,
                    text = highlight.text,
                    note = null,
                    color = highlight.color,
                    chapterNumber = 2,
                    createdAtMillis = highlight.createdAtMillis,
                ),
            )
        }
    }
})
