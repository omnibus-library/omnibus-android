package com.omnibus.omnibus.feature.bookdetail

import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.feature.bookdetail.stage.SeriesLine
import com.omnibus.omnibus.feature.bookdetail.stage.bylineModel
import com.omnibus.omnibus.feature.bookdetail.stage.yearFitsBeside
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class BookBylineTest : DescribeSpec({

    describe("bylineModel") {

        it("#93 TC-19: author and year") {
            val model = bylineModel(
                testBook(
                    1,
                    creators = listOf(Creator("Frank Herbert", "author", null, 3L)),
                    publishedDate = "1965-08-01",
                ),
            )

            model.author shouldBe "Frank Herbert"
            model.year shouldBe 1965
            model.series shouldBe null
        }

        it("#93 TC-20: no year") {
            bylineModel(testBook(1, publishedDate = null)).year shouldBe null
            bylineModel(testBook(1, publishedDate = "unknown")).year shouldBe null
        }

        it("#93 TC-21: series with index") {
            val model = bylineModel(testBook(1, series = "Dune", seriesIndex = "3"))

            model.series shouldBe SeriesLine("Dune", "3")
        }

        it("#93 TC-22: series with blank index") {
            for (index in listOf("", "  ", null)) {
                bylineModel(testBook(1, series = "Dune", seriesIndex = index)).series shouldBe SeriesLine("Dune", null)
            }
        }

        it("#93 TC-23: series suppresses year") {
            val model = bylineModel(testBook(1, series = "Dune", publishedDate = "1965"))

            model.year shouldBe null
            model.series shouldBe SeriesLine("Dune", null)
        }

        it("#93 TC-24: blank series is no series") {
            for (series in listOf("", "   ", null)) {
                val model = bylineModel(testBook(1, series = series, publishedDate = "1965"))

                model.series shouldBe null
                model.year shouldBe 1965
            }
        }

        it("#93 TC-25: link is the first creator with an id") {
            val model = bylineModel(
                testBook(
                    1,
                    creators = listOf(
                        Creator("A", null, null, null),
                        Creator("Bee", "author", null, 7L),
                    ),
                ),
            )

            model.authorLink shouldBe BookDetailDestination.Author(7L, "Bee")
        }

        it("#93 TC-26: null id is not linked") {
            val model = bylineModel(testBook(1, creators = listOf(Creator("Solo Writer", "author", null, null))))

            model.authorLink shouldBe null
            model.author shouldBe "Solo Writer"
        }

        it("#93 TC-27: blank name or no creators") {
            val skipped = bylineModel(
                testBook(
                    1,
                    creators = listOf(
                        Creator("  ", "author", null, 7L),
                        Creator("Ann", "author", null, 9L),
                    ),
                ),
            )
            skipped.authorLink shouldBe BookDetailDestination.Author(9L, "Ann")

            val none = bylineModel(testBook(1, creators = emptyList()))
            none.authorLink shouldBe null
            none.author shouldBe testBook(1, creators = emptyList()).authorDisplay
        }
    }

    describe("yearFitsBeside") {

        it("#93 TC-133: year fits beside only when both fit") {
            yearFitsBeside(authorWidthPx = 100, yearWidthPx = 40, gapPx = 6, maxWidthPx = 146) shouldBe true
            yearFitsBeside(authorWidthPx = 100, yearWidthPx = 40, gapPx = 6, maxWidthPx = 145) shouldBe false
            yearFitsBeside(authorWidthPx = 0, yearWidthPx = 40, gapPx = 6, maxWidthPx = 46) shouldBe true
        }
    }
})
