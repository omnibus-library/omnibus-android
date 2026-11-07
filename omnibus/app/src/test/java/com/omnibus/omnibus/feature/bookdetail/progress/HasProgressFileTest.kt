package com.omnibus.omnibus.feature.bookdetail.progress

import com.omnibus.omnibus.data.library.randomBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class HasProgressFileTest : DescribeSpec({

    describe("whether a book has a progress file") {
        it("TC-77: follows the formats, for each of an ebook, an audiobook, both, a pdf, none") {
            checkAll(
                Exhaustive.of(
                    listOf("epub") to true,
                    listOf("m4b") to true,
                    listOf("epub", "m4b") to true,
                    listOf("pdf") to true,
                    emptyList<String>() to false,
                ),
            ) { (formats, has) ->
                hasProgressFile(randomBook().copy(formats = formats)) shouldBe has
            }
        }
    }
})
