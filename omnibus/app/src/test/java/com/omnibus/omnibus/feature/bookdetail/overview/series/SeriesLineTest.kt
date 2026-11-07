package com.omnibus.omnibus.feature.bookdetail.overview.series

import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.series.randomSeriesMember
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class SeriesLineTest : DescribeSpec({
    describe("the wording of the stage series line") {
        context("when the book has no number") {
            it("#104 TC-48: is the name alone, for a missing or blank index") {
                checkAll(Exhaustive.of(null, "", "  ")) { index ->
                    seriesLineWording(index, randomInt(0..50)) shouldBe SeriesLineWording.NameOnly
                }
            }
        }

        context("when the number is within the series") {
            it("#104 TC-49: gives the number of the total, up to the last book, trimmed or fractional") {
                val cases = Exhaustive.of(
                    Triple("5", 5, "5"),
                    Triple("1.5", 3, "1.5"),
                    Triple(" 2 ", 5, "2"),
                )

                checkAll(cases) { (index, total, number) ->
                    seriesLineWording(index, total) shouldBe SeriesLineWording.WithTotal(number, total)
                }
            }
        }

        context("when the total does not apply") {
            it("#104 TC-49: gives the number alone, for an unknown total, one past it, a non-number or a single book") {
                val cases = Exhaustive.of(
                    "2" to null,
                    "7" to 5,
                    "3.5" to 3,
                    "II" to 5,
                    "2" to 1,
                    "2" to 0,
                )

                checkAll(cases) { (index, total) ->
                    seriesLineWording(index, total) shouldBe SeriesLineWording.WithIndex(index)
                }
            }
        }
    }

    describe("the link on the stage series line") {
        fun bookInSeries() = randomBook().copy(series = "Dune", seriesId = randomLong())

        context("when the series section would not show") {
            it("#104 TC-50: is a plain line, for no series id or too few members") {
                val cases = Exhaustive.of(
                    bookInSeries().copy(seriesId = null) to List(3) { randomSeriesMember() },
                    bookInSeries() to listOf(randomSeriesMember()),
                    bookInSeries() to null,
                )

                checkAll(cases) { (book, members) -> seriesLineState(book, members) {}.shouldBeNull() }
            }
        }

        context("when the series section would show") {
            it("#104 TC-51: carries the number of books") {
                seriesLineState(bookInSeries(), List(5) { randomSeriesMember() }) {}.shouldNotBeNull().total shouldBe 5
            }
        }
    }
})
