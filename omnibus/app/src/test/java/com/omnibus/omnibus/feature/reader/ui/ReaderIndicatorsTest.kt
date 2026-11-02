package com.omnibus.omnibus.feature.reader.ui

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotStartWith
import java.io.File

class ReaderIndicatorsTest : DescribeSpec({

    // Unit tests run with the module directory as the working directory.
    val source = File("src/main/java/com/omnibus/omnibus/feature/reader/ui/ReaderIndicators.kt")

    /** A location mid-book: page 361 of 661, seven pages left in chapter 3 of 12, 54%. */
    val reading = RelocateData(
        page = 361,
        totalPages = 661,
        pct = 54,
        chapter = 3,
        totalChapters = 12,
        chapterPagesLeft = 7,
    )

    fun indicators(
        location: RelocateData? = reading,
        title: String = "Nineteen Eighty-Four",
        chromeVisible: Boolean = false,
    ) = ReaderIndicators(location = location, title = title, chromeVisible = chromeVisible)

    describe("ReaderIndicators") {

        it("RC-1: null location gives no labels") {
            for (chrome in listOf(false, true)) {
                val labels = indicators(location = null, chromeVisible = chrome)
                labels.top shouldBe null
                labels.bottom shouldBe null
            }
        }

        it("RC-2: reading shows title and bare page") {
            val labels = indicators(chromeVisible = false)

            labels.top shouldBe ReaderLabel.Title("Nineteen Eighty-Four")
            labels.bottom shouldBe ReaderLabel.Page(361)
        }

        it("RC-3: blank title gives no top label") {
            indicators(title = "", chromeVisible = false).top shouldBe null
            indicators(title = "   ", chromeVisible = false).top shouldBe null
            indicators(title = "   ", chromeVisible = false).bottom shouldBe ReaderLabel.Page(361)
        }

        it("RC-4: chrome up shows pages left and page of total") {
            indicators(chromeVisible = true).top shouldBe ReaderLabel.PagesLeftInChapter(7)
            indicators(
                location = reading.copy(chapterPagesLeft = 1),
                chromeVisible = true,
            ).top shouldBe ReaderLabel.PagesLeftInChapter(1)
            indicators(chromeVisible = true).bottom shouldBe ReaderLabel.PageOf(361, 661)
        }

        it("RC-5: no pages left falls back to chapter of") {
            val location = reading.copy(chapterPagesLeft = 0)
            indicators(location = location, chromeVisible = true).top shouldBe ReaderLabel.ChapterOf(3, 12)
            indicators(
                location = reading.copy(chapterPagesLeft = -1),
                chromeVisible = true,
            ).top shouldBe ReaderLabel.ChapterOf(3, 12)
        }

        it("RC-6: chapter fallback needs both numbers above 0") {
            indicators(
                location = reading.copy(chapterPagesLeft = 0, chapter = 0),
                chromeVisible = true,
            ).top shouldBe null
            indicators(
                location = reading.copy(chapterPagesLeft = 0, totalChapters = 0),
                chromeVisible = true,
            ).top shouldBe null
        }

        it("RC-7: no totalPages falls back to percent") {
            val location = RelocateData(page = 1, totalPages = 0, pct = 18)

            indicators(location = location, chromeVisible = false).bottom shouldBe ReaderLabel.Percent(18)
            indicators(location = location, chromeVisible = true).bottom shouldBe ReaderLabel.Percent(18)
        }

        it("RC-8: page 0 is never shown") {
            val location = RelocateData(page = 0, totalPages = 661, pct = 18)

            for (chrome in listOf(false, true)) {
                indicators(location = location, chromeVisible = chrome).bottom shouldBe ReaderLabel.Percent(18)
            }
        }

        it("RC-9: pct 0 without page numbers gives no bottom label") {
            indicators(location = RelocateData(totalPages = 0, pct = 0), chromeVisible = false).bottom shouldBe null
            indicators(location = RelocateData(page = 0, pct = 0), chromeVisible = true).bottom shouldBe null
        }

        it("RC-10: first and last page are valid") {
            indicators(location = RelocateData(page = 1, totalPages = 1), chromeVisible = false).bottom shouldBe
                ReaderLabel.Page(1)
            indicators(location = RelocateData(page = 1, totalPages = 1), chromeVisible = true).bottom shouldBe
                ReaderLabel.PageOf(1, 1)
            indicators(location = RelocateData(page = 661, totalPages = 661), chromeVisible = true).bottom shouldBe
                ReaderLabel.PageOf(661, 661)
        }

        it("RC-11: top and bottom resolve independently") {
            val location = RelocateData(totalPages = 0, pct = 100, chapterPagesLeft = 4)
            val labels = indicators(location = location, chromeVisible = true)

            labels.top shouldBe ReaderLabel.PagesLeftInChapter(4)
            labels.bottom shouldBe ReaderLabel.Percent(100)
        }

        it("RC-12: ReaderIndicators has no Android or Compose dependency") {
            source.readLines().forEach { line ->
                line shouldNotStartWith "import android"
                line shouldNotStartWith "import androidx"
            }
        }
    }
})
