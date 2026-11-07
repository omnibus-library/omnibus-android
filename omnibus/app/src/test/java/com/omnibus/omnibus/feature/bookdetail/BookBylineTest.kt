package com.omnibus.omnibus.feature.bookdetail

import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.library.randomCreator
import com.omnibus.omnibus.feature.bookdetail.stage.SeriesLine
import com.omnibus.omnibus.feature.bookdetail.stage.bylineModel
import com.omnibus.omnibus.feature.bookdetail.stage.yearFitsBeside
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class BookBylineTest : DescribeSpec({

    describe("the year on the byline") {

        context("when the book is in no series") {
            it("#93 TC-19: reads the publication year") {
                val book = randomBook().copy(series = null, publishedDate = "1965-08-01")

                bylineModel(book).year shouldBe 1965
            }
        }

        context("when the book is in a series") {
            it("#93 TC-23: gives way to the series") {
                bylineModel(randomBook().copy(series = randomString(), publishedDate = "1965")).year shouldBe null
            }
        }
    }

    describe("the series on the byline") {

        context("when the series has a number") {
            it("#93 TC-21: reads the name with the number") {
                val series = randomString()

                bylineModel(randomBook().copy(series = series, seriesIndex = "3")).series shouldBe SeriesLine(series, "3")
            }
        }

        context("when the number is blank") {
            it("#93 TC-22: reads the name alone, for an empty, blank or missing number") {
                checkAll(Exhaustive.of("", "  ", null)) { index ->
                    val series = randomString()

                    bylineModel(randomBook().copy(series = series, seriesIndex = index)).series shouldBe
                        SeriesLine(series, null)
                }
            }
        }

        context("when the series is blank") {
            it("#93 TC-24: is left out, for an empty, blank or missing series") {
                checkAll(Exhaustive.of("", "   ", null)) { series ->
                    bylineModel(randomBook().copy(series = series)).series shouldBe null
                }
            }
        }
    }

    describe("the link on the author") {

        context("when an earlier creator has no id") {
            it("#93 TC-25: goes to the first creator with one") {
                val id = randomLong()
                val name = randomString()
                val book = randomBook().copy(
                    creators = listOf(randomCreator().copy(id = null), randomCreator().copy(name = name, id = id)),
                )

                bylineModel(book).authorLink shouldBe BookDetailDestination.Author(id, name)
            }
        }

        context("when no creator has an id") {
            it("#93 TC-26: is left out") {
                val book = randomBook().copy(creators = listOf(randomCreator().copy(id = null)))

                bylineModel(book).authorLink shouldBe null
            }
        }

        context("when an earlier creator has a blank name") {
            it("#93 TC-27: skips that creator") {
                val id = randomLong()
                val name = randomString()
                val book = randomBook().copy(
                    creators = listOf(
                        Creator("  ", randomString(), null, randomLong()),
                        randomCreator().copy(name = name, id = id),
                    ),
                )

                bylineModel(book).authorLink shouldBe BookDetailDestination.Author(id, name)
            }
        }
    }

    describe("fitting the year beside the author") {

        it("#93 TC-133: fits up to the exact width") {
            val author = randomInt(0..500)
            val year = randomInt(0..500)
            val gap = randomInt(0..50)
            val needed = author + gap + year

            yearFitsBeside(author, year, gap, maxWidthPx = needed) shouldBe true
            yearFitsBeside(author, year, gap, maxWidthPx = needed - 1) shouldBe false
        }
    }
})
