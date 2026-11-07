package com.omnibus.omnibus.feature.bookdetail.overview.series

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.data.series.randomSeriesMember
import com.omnibus.omnibus.testing.randomLong
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class SeriesFanModelTest : DescribeSpec({
    /** Members with these series indexes, in this order. */
    fun members(vararg indexes: String?): List<SeriesMember> =
        indexes.mapIndexed { i, index -> randomSeriesMember().copy(uuid = "m$i", seriesIndex = index) }

    /** A book in a series, which is the member with [uuid]. */
    fun bookIn(uuid: String): Book = randomBook().copy(uniqueIdentifier = uuid, series = "Dune", seriesId = randomLong())

    describe("a card's wording") {
        fun SeriesCardMeta.text() =
            parts(book = { "Book $it" }, thisBook = "This book", upNext = "Up next").joinToString(" · ")

        it("#104 TC-34: reads the book's number, then what marks it") {
            val cases = Exhaustive.of(
                SeriesCardMeta("2", isThis = true, isUpNext = false) to "Book 2 · This book",
                SeriesCardMeta("3", isThis = false, isUpNext = true) to "Book 3 · Up next",
                SeriesCardMeta(" 1.5 ", isThis = false, isUpNext = false) to "Book 1.5",
            )

            checkAll(cases) { (meta, text) -> meta.text() shouldBe text }
        }

        context("when there is no index") {
            it("#104 TC-37: leaves out the number, for a missing or a blank index") {
                checkAll(Exhaustive.of(null, "")) { index ->
                    SeriesCardMeta(index, isThis = true, isUpNext = false).text() shouldBe "This book"
                }
            }
        }
    }

    describe("the book up next") {
        it("#104 TC-38: is the book after this one") {
            upNextIndex(members("1", "2", "3"), current = 1) shouldBe 2
        }

        context("when a later book repeats this book's number") {
            it("#104 TC-39: skips it") {
                upNextIndex(members("1", "2", "2", "3"), current = 1) shouldBe 3
            }
        }

        context("when only unnumbered books follow") {
            it("#104 TC-40: is none") {
                upNextIndex(members("5", null, "II"), current = 0).shouldBeNull()
            }
        }

        context("when an unnumbered book sits between") {
            it("#104 TC-43: skips it") {
                upNextIndex(members("1", "2", null, "3"), current = 1) shouldBe 3
            }
        }

        context("when this book is unnumbered") {
            it("#104 TC-41: is the next in list order") {
                upNextIndex(members("1", null, "II", "3"), current = 1) shouldBe 2
            }
        }

        context("when the indexes are fractional") {
            it("#104 TC-42: compares them numerically") {
                upNextIndex(members("1.5", "1.5", "2"), current = 0) shouldBe 2
            }
        }

        context("when no book follows this one") {
            it("#104 TC-44: is none, for an absent or a last book") {
                upNextIndex(members("1", "2"), current = null).shouldBeNull()
                upNextIndex(members("1", "2"), current = 1).shouldBeNull()
            }
        }
    }

    describe("the fan") {
        it("#104 TC-45: marks this book, with the next as up next") {
            val model = seriesFanModel(bookIn("m1"), members("1", "2", "3", "4")).shouldNotBeNull()

            model.cards.map { it.meta.isThis } shouldContainExactly listOf(false, true, false, false)
            model.cards.map { it.meta.isUpNext } shouldContainExactly listOf(false, false, true, false)
        }

        context("when there is no series of more than one") {
            it("#104 TC-46: does not show, for no id, a blank name, unknown members or too few of them") {
                val book = bookIn("m0")
                val two = members("1", "2")
                val cases = Exhaustive.of(
                    book.copy(seriesId = null) to two,
                    book.copy(series = null) to two,
                    book.copy(series = "   ") to two,
                    book to null,
                    book to emptyList(),
                    book to members("1"),
                )

                checkAll(cases) { (candidate, known) -> seriesFanModel(candidate, known).shouldBeNull() }
            }
        }

        context("when this book is not among the members") {
            it("#104 TC-47: shows the series with nothing marked") {
                val model = seriesFanModel(bookIn("elsewhere"), members("1", "2", "3")).shouldNotBeNull()

                model.cards.none { it.meta.isThis || it.meta.isUpNext } shouldBe true
            }
        }
    }
})
