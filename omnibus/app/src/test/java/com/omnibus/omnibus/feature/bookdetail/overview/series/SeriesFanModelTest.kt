package com.omnibus.omnibus.feature.bookdetail.overview.series

import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.data.series.SeriesMember
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class SeriesFanModelTest : DescribeSpec({

    /** The metadata wording with English literals, joined as the composable joins it. */
    fun SeriesCardMeta.text(): String =
        parts(book = { "Book $it" }, thisBook = "This book", upNext = "Up next").joinToString(" · ")

    fun member(uuid: String, index: String?) = SeriesMember(uuid, "Title $uuid", index, null, null, false)

    describe("card wording") {

        it("#104 TC-34: this book reads Book 2 · This book") {
            val meta = SeriesCardMeta("2", isThis = true, isUpNext = false)

            meta.parts(book = { "Book $it" }, thisBook = "This book", upNext = "Up next") shouldContainExactly
                listOf("Book 2", "This book")
            meta.text() shouldBe "Book 2 · This book"
        }

        it("#104 TC-35: the next book reads Book 3 · Up next") {
            SeriesCardMeta("3", isThis = false, isUpNext = true).text() shouldBe "Book 3 · Up next"
        }

        it("#104 TC-36: other books read Book N") {
            SeriesCardMeta("4", isThis = false, isUpNext = false).text() shouldBe "Book 4"
        }

        it("#104 TC-37: missing and fractional indexes in the wording") {
            SeriesCardMeta(null, isThis = true, isUpNext = false).text() shouldBe "This book"
            SeriesCardMeta(null, isThis = false, isUpNext = true).text() shouldBe "Up next"
            SeriesCardMeta(null, isThis = false, isUpNext = false).text() shouldBe ""
            SeriesCardMeta("", isThis = false, isUpNext = true).text() shouldBe "Up next"
            SeriesCardMeta(null, isThis = false, isUpNext = false).parts(
                book = { "Book $it" }, thisBook = "This book", upNext = "Up next",
            ).shouldBeEmpty()
            SeriesCardMeta(" 1.5 ", isThis = false, isUpNext = false).text() shouldBe "Book 1.5"
        }
    }

    describe("up next") {

        val plain = (1..5).map { member("m$it", "$it") }

        it("#104 TC-38: up next is the book after this one") {
            upNextIndex(plain, current = 2) shouldBe 3
        }

        it("#104 TC-39: a duplicate index is not up next") {
            val members = listOf(member("a", "1"), member("b", "2"), member("c", "2"), member("d", "3"))
            upNextIndex(members, current = 1) shouldBe 3
        }

        it("#104 TC-40: trailing unnumbered books are never up next") {
            val members = listOf(member("a", "5"), member("b", null), member("c", "II"))
            upNextIndex(members, current = 0) shouldBe null
        }

        it("#104 TC-41: an unnumbered book is followed by the next in list order") {
            val members = listOf(member("a", "1"), member("b", null), member("c", "II"), member("d", "3"))
            upNextIndex(members, current = 1) shouldBe 2
            upNextIndex(listOf(member("a", "1"), member("b", ""), member("c", "2")), current = 1) shouldBe 2
            upNextIndex(listOf(member("a", "1"), member("b", "II")), current = 1) shouldBe null
        }

        it("#104 TC-42: fractional indexes compare numerically") {
            upNextIndex(listOf(member("a", "1"), member("b", "1.5"), member("c", "2")), current = 1) shouldBe 2
            upNextIndex(listOf(member("a", "2"), member("b", "2.5")), current = 0) shouldBe 1
            upNextIndex(listOf(member("a", "1.5"), member("b", "1.5"), member("c", "2")), current = 0) shouldBe 2
        }

        it("#104 TC-43: an unnumbered book in the middle is skipped") {
            val members = listOf(member("a", "1"), member("b", "2"), member("c", null), member("d", "3"))
            upNextIndex(members, current = 1) shouldBe 3
        }

        it("#104 TC-44: no up next when absent or last") {
            upNextIndex(plain, current = null) shouldBe null
            upNextIndex(plain, current = 4) shouldBe null
            upNextIndex(emptyList(), current = 0) shouldBe null
        }
    }

    describe("the fan model") {

        val members = (1..5).map { member("m$it", "$it") }
        val dune = testBook(id = 3, uniqueIdentifier = "m3", series = "  Dune  ", seriesIndex = "3", seriesId = 4L)

        it("#104 TC-45: the model marks this book and up next") {
            val model = seriesFanModel(dune, members).shouldNotBeNull()

            model.current shouldBe 2
            model.seriesId shouldBe 4L
            model.seriesName shouldBe "Dune"
            model.cards.map { it.member } shouldContainExactly members
            model.cards.map { it.meta.isThis } shouldContainExactly listOf(false, false, true, false, false)
            model.cards.map { it.meta.isUpNext } shouldContainExactly listOf(false, false, false, true, false)
        }

        it("#104 TC-46: the section shows only for a series of more than one") {
            val two = listOf(member("m3", "3"), member("m4", "4"))
            seriesFanModel(dune.copy(seriesId = null), two).shouldBeNull()
            seriesFanModel(dune.copy(series = null), two).shouldBeNull()
            seriesFanModel(dune.copy(series = ""), two).shouldBeNull()
            seriesFanModel(dune.copy(series = "   "), two).shouldBeNull()
            seriesFanModel(dune, null).shouldBeNull()
            seriesFanModel(dune, emptyList()).shouldBeNull()
            seriesFanModel(dune, listOf(member("m3", "3"))).shouldBeNull()
            seriesFanModel(dune, two).shouldNotBeNull()
        }

        it("#104 TC-47: an absent book still shows the series unmarked") {
            val model = seriesFanModel(dune.copy(uniqueIdentifier = "elsewhere"), members).shouldNotBeNull()

            model.current shouldBe null
            model.cards.none { it.meta.isThis } shouldBe true
            model.cards.none { it.meta.isUpNext } shouldBe true
        }
    }
})
