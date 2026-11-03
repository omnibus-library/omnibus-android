package com.omnibus.omnibus.feature.bookdetail.progress

import com.omnibus.omnibus.data.library.testBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class HasProgressFileTest : DescribeSpec({

    describe("hasProgressFile") {

        it("TC-77: a book with an ebook or an audiobook has a progress file") {
            hasProgressFile(testBook(1, formats = listOf("epub"))) shouldBe true
            hasProgressFile(testBook(1, formats = listOf("m4b"))) shouldBe true
            hasProgressFile(testBook(1, formats = listOf("epub", "m4b"))) shouldBe true
            hasProgressFile(testBook(1, formats = listOf("pdf"))) shouldBe true
            hasProgressFile(testBook(1, formats = emptyList())) shouldBe false
        }
    }
})
