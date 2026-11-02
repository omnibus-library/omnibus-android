package com.omnibus.omnibus.feature.bookdetail

import com.omnibus.omnibus.feature.bookdetail.stage.bookTitleSizeSp
import com.omnibus.omnibus.feature.bookdetail.stage.titleMinSizeSp
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class BookStageTitleTest : DescribeSpec({

    describe("bookTitleSizeSp") {

        it("#93 TC-14: up to 22 code points is 48") {
            bookTitleSizeSp("") shouldBe 48
            bookTitleSizeSp("a") shouldBe 48
            bookTitleSizeSp("a".repeat(21)) shouldBe 48
            bookTitleSizeSp("a".repeat(22)) shouldBe 48
        }

        it("#93 TC-15: 23 to 30 is 42") {
            bookTitleSizeSp("a".repeat(23)) shouldBe 42
            bookTitleSizeSp("a".repeat(30)) shouldBe 42
        }

        it("#93 TC-16: 31 or more is 38") {
            bookTitleSizeSp("a".repeat(31)) shouldBe 38
            bookTitleSizeSp("a".repeat(200)) shouldBe 38
        }

        it("#93 TC-17: counts code points, untrimmed") {
            // Each emoji is two UTF-16 units but one code point.
            bookTitleSizeSp("😀".repeat(22)) shouldBe 48
            bookTitleSizeSp("😀".repeat(22) + "a") shouldBe 42
            bookTitleSizeSp("a".repeat(31)) shouldBe 38
            // Leading spaces count.
            bookTitleSizeSp(" ".repeat(2) + "a".repeat(21)) shouldBe 42
        }

        it("#93 TC-18: shrink floor is 70% rounded up") {
            titleMinSizeSp(48) shouldBe 34
            titleMinSizeSp(42) shouldBe 30
            titleMinSizeSp(38) shouldBe 27
        }
    }
})
