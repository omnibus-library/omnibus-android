package com.omnibus.omnibus.feature.bookdetail.overview.series

import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.data.series.SeriesMember
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class SeriesLineTest : DescribeSpec({

    fun member(uuid: String, index: String?) = SeriesMember(uuid, "Title $uuid", index, null, null, false)

    describe("the stage series line wording") {

        it("#104 TC-48: wording is name, name with index, or index of total") {
            seriesLineWording(null, 5) shouldBe SeriesLineWording.NameOnly
            seriesLineWording("", 5) shouldBe SeriesLineWording.NameOnly
            seriesLineWording("  ", 5) shouldBe SeriesLineWording.NameOnly
            seriesLineWording("2", 5) shouldBe SeriesLineWording.WithTotal("2", 5)
            seriesLineWording("2", null) shouldBe SeriesLineWording.WithIndex("2")
        }

        it("#104 TC-49: of M is left out when N is out of range") {
            seriesLineWording("5", 5) shouldBe SeriesLineWording.WithTotal("5", 5)
            seriesLineWording("1.5", 3) shouldBe SeriesLineWording.WithTotal("1.5", 3)
            seriesLineWording(" 2 ", 5) shouldBe SeriesLineWording.WithTotal("2", 5)
            seriesLineWording("7", 5) shouldBe SeriesLineWording.WithIndex("7")
            seriesLineWording("3.5", 3) shouldBe SeriesLineWording.WithIndex("3.5")
            seriesLineWording("II", 5) shouldBe SeriesLineWording.WithIndex("II")
            seriesLineWording("2", 1) shouldBe SeriesLineWording.WithIndex("2")
            seriesLineWording("2", 0) shouldBe SeriesLineWording.WithIndex("2")
        }
    }

    describe("the link state") {

        val dune = testBook(id = 3, uniqueIdentifier = "m3", series = "Dune", seriesIndex = "3", seriesId = 4L)
        val five = (1..5).map { member("m$it", "$it") }

        it("#104 TC-50: the line becomes a link exactly when the section shows") {
            val cases = listOf(
                dune.copy(seriesId = null) to five,
                dune.copy(series = null) to five,
                dune.copy(series = "") to five,
                dune.copy(series = "   ") to five,
                dune to null,
                dune to emptyList(),
                dune to listOf(member("m3", "3")),
                dune to five.take(2),
                dune to five,
            )
            for ((book, members) in cases) {
                val state = seriesLineState(book, members) {}
                (state != null) shouldBe (seriesFanModel(book, members) != null)
            }
        }

        it("#104 TC-51: the state carries the count and the click") {
            var clicks = 0
            val state = seriesLineState(dune, five) { clicks++ }.shouldNotBeNull()

            state.total shouldBe 5
            state.onClick()
            state.onClick()
            clicks shouldBe 2
            seriesLineState(dune.copy(seriesId = null), five) {}.shouldBeNull()
        }
    }
})
