package com.omnibus.omnibus.feature.bookdetail.reviews

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class ReviewTextTest : DescribeSpec({

    /** The plain text of [md], with the spoiler word "(spoiler)" for an unfinished reader. */
    fun plain(md: String?, finished: Boolean = false) = reviewPlainText(md, finished, inlineSpoiler = "(spoiler)")

    describe("reviewPlainText") {

        it("TC-27: headings, quotes, links, images and list markers") {
            plain("## Title") shouldBe "Title"
            plain("> q") shouldBe "q"
            plain("[t](u)") shouldBe "t"
            plain("see ![a](u) this").also {
                it.shouldContain("see")
                it.shouldContain("this")
                it.shouldNotContain("![")
            }
            plain("- a") shouldBe "• a"
            plain("* b") shouldBe "• b"
            plain("+ c") shouldBe "• c"
            plain("1. d") shouldBe "1. d"
        }

        it("TC-28: emphasis marks are removed only where they wrap a word") {
            plain("**b**") shouldBe "b"
            plain("__b__") shouldBe "b"
            plain("~~s~~") shouldBe "s"
            plain("`c`") shouldBe "c"
            plain("*i*") shouldBe "i"
            plain("_i_") shouldBe "i"
            plain("2 * 3") shouldBe "2 * 3"
            plain("snake_case_name") shouldBe "snake_case_name"
        }

        it("TC-29: inline spoilers are replaced until the book is finished") {
            plain("a ||secret|| b") shouldBe "a (spoiler) b"
            plain("a ||secret|| b", finished = true) shouldBe "a secret b"
        }

        it("TC-30: line endings, blank runs, edges, and empty results") {
            plain("  a\r\nb\r\n\r\n\r\n\r\nc  ") shouldBe "a\nb\n\nc"
            plain(null) shouldBe null
            plain("") shouldBe null
            plain("   ") shouldBe null
            plain("![a](u)") shouldBe null
        }
    }
})
