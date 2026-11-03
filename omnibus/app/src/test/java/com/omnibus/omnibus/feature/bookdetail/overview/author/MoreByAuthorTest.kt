package com.omnibus.omnibus.feature.bookdetail.overview.author

import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.testBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class MoreByAuthorTest : DescribeSpec({

    fun books(count: Int) = (1..count).map { testBook(id = it.toLong()) }

    describe("moreByAuthor") {

        it("#105 TC-7: shows at most 12 books") {
            moreByAuthor(books(30), currentBookId = 999).size shouldBe 12
        }

        it("#105 TC-8: takes the first 12 in the server's order") {
            moreByAuthor(books(30), currentBookId = 999).map { it.uuid } shouldContainExactly
                (1..12).map { "uuid-$it" }
        }

        it("#105 TC-9: leaves out the current book") {
            moreByAuthor(books(5), currentBookId = 3).map { it.uuid } shouldContainExactly
                listOf("uuid-1", "uuid-2", "uuid-4", "uuid-5")
        }

        it("#105 TC-10: the current book does not use up a place in the limit") {
            val shown = moreByAuthor(books(13), currentBookId = 2)

            shown.size shouldBe 12
            shown.map { it.uuid } shouldContainExactly (listOf(1L) + (3L..13L)).map { "uuid-$it" }
        }

        it("#105 TC-11: an author whose only book is the current one has none to show") {
            moreByAuthor(books(1), currentBookId = 1).shouldBeEmpty()
            moreByAuthor(emptyList(), currentBookId = 1).shouldBeEmpty()
        }

        it("#105 TC-12: carries the title, the year and the cover identity") {
            val book = testBook(
                id = 4, title = "The Dispossessed", publishedDate = "1974-05-01", accent = "#c8a04a",
                coverUrl = "/cover", creators = listOf(Creator("Ursula K. Le Guin", null, null, 7)),
            )

            val shown = moreByAuthor(listOf(book), currentBookId = 1).single()

            shown.title shouldBe "The Dispossessed"
            shown.year shouldBe "1974"
            shown.identity.uuid shouldBe "uuid-4"
            shown.identity.creator shouldBe "Ursula K. Le Guin"
            shown.identity.hasCover shouldBe true
        }

        it("#105 TC-13: a book without a date has no year, and one without a title shows its filename") {
            val shown = moreByAuthor(listOf(testBook(id = 4, title = null, filename = "x.epub")), 1).single()

            shown.year shouldBe null
            shown.title shouldBe "x.epub"
        }
    }

    describe("authorTargetOf") {

        it("#105 TC-14: is the first creator when they have an id") {
            val book = testBook(1, creators = listOf(Creator(" Ursula K. Le Guin ", "author", null, 7)))

            authorTargetOf(book) shouldBe AuthorTarget(7, "Ursula K. Le Guin")
        }

        it("#105 TC-15: is null when the first creator has no id, even if a later one has") {
            val book = testBook(
                1,
                creators = listOf(Creator("A", null, null, null), Creator("B", null, null, 9)),
            )

            authorTargetOf(book) shouldBe null
        }

        it("#105 TC-16: is null when the book has no creators") {
            authorTargetOf(testBook(1, creators = emptyList())) shouldBe null
        }

        it("#105 TC-17: a blank name falls back to the book's author line") {
            authorTargetOf(testBook(1, creators = listOf(Creator("  ", null, null, 7)))) shouldBe
                AuthorTarget(7, "Unknown author")
        }
    }
})
