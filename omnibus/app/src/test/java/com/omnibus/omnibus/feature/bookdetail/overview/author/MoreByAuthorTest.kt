package com.omnibus.omnibus.feature.bookdetail.overview.author

import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.library.randomCreator
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.testing.randomLong
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class MoreByAuthorTest : DescribeSpec({
    /** [count] books with the ids 1 to [count]. */
    fun books(count: Int) = (1..count).map { randomBook().copy(id = it.toLong()) }

    describe("choosing the author's other books") {
        context("when the author has more books than the shelf holds") {
            it("#105 TC-8: takes the first 12 in the server's order") {
                val books = books(30)

                moreByAuthor(books, currentBookId = 0).map { it.uuid } shouldContainExactly books.take(12).map { it.uuid }
            }
        }

        context("when the current book is among them") {
            it("#105 TC-10: leaves it out, giving its place in the limit to the next book") {
                val books = books(13)

                moreByAuthor(books, currentBookId = 2).map { it.uuid } shouldContainExactly
                    books.filterNot { it.id == 2L }.map { it.uuid }
            }
        }

        it("#105 TC-12: describes a book for its card") {
            val book = randomBook().copy(id = 4, title = "The Dispossessed", publishedDate = "1974-05-01")

            moreByAuthor(listOf(book), currentBookId = 1).single() shouldBe
                AuthorShelfBook(book.uuid, book.coverIdentity(), "The Dispossessed", "1974")
        }
    }

    describe("finding the book's author") {
        context("when the first creator has an id") {
            it("#105 TC-14: is that creator, with the name trimmed") {
                val id = randomLong()
                val book = randomBook().copy(creators = listOf(randomCreator().copy(name = " Ursula K. Le Guin ", id = id)))

                authorTargetOf(book) shouldBe AuthorTarget(id, "Ursula K. Le Guin")
            }
        }

        context("when the first creator has no id") {
            it("#105 TC-15: finds nobody, whatever later creators have") {
                val book = randomBook().copy(
                    creators = listOf(randomCreator().copy(id = null), randomCreator().copy(id = randomLong())),
                )

                authorTargetOf(book).shouldBeNull()
            }
        }

        context("when the book has no creators") {
            it("#105 TC-16: finds nobody") {
                authorTargetOf(randomBook().copy(creators = emptyList())).shouldBeNull()
            }
        }

        context("when the first creator's name is blank") {
            it("#105 TC-17: falls back to the book's author line") {
                val id = randomLong()
                val book = randomBook().copy(creators = listOf(randomCreator().copy(name = "  ", id = id)))

                authorTargetOf(book) shouldBe AuthorTarget(id, book.authorDisplay)
            }
        }
    }
})
