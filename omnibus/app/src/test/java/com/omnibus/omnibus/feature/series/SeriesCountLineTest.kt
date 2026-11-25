package com.omnibus.omnibus.feature.series

import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.library.randomCreator
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull

class SeriesCountLineTest : DescribeSpec({

    describe("the author on the count line") {

        it("#349 TC-7: is the first creator's name on the first book") {
            val name = randomCreator().copy(name = "Robert Holdstock")
            val books = listOf(randomBook().copy(creators = listOf(name, randomCreator())), randomBook())

            seriesCountAuthor(books) shouldBe "Robert Holdstock"
        }

        context("when the first creator's name is blank") {

            it("#349 TC-7: skips it for the next creator with a name") {
                val blank = randomCreator().copy(name = "   ")
                val named = randomCreator().copy(name = "M. A. Venn")
                val books = listOf(randomBook().copy(creators = listOf(blank, named)))

                seriesCountAuthor(books) shouldBe "M. A. Venn"
            }
        }

        context("when no creator on the first book has a name") {

            it("#349 TC-7: is null, not a fallback") {
                val books = listOf(randomBook().copy(creators = listOf(randomCreator().copy(name = " "))), randomBook())

                seriesCountAuthor(books).shouldBeNull()
            }
        }

        context("when the first book has no creators but a later book has one") {

            it("#349 TC-7: is null") {
                val books = listOf(randomBook().copy(creators = emptyList()), randomBook())

                seriesCountAuthor(books).shouldBeNull()
            }
        }
    }
})
