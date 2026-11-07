package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of

class ReaderIndicatorsTest : DescribeSpec({

    fun sut(
        location: RelocateData? = randomRelocateData(),
        title: String = randomString(),
        chromeVisible: Boolean = randomBoolean(),
    ) = ReaderIndicators(location = location, title = title, chromeVisible = chromeVisible)

    describe("the labels") {
        context("without a location") {
            it("RC-1: show nothing, with the chrome up or hidden") {
                checkAll(Exhaustive.boolean()) { chrome ->
                    val labels = sut(location = null, chromeVisible = chrome)

                    labels.top shouldBe null
                    labels.bottom shouldBe null
                }
            }
        }
    }

    describe("the top label") {
        context("with the chrome hidden") {
            it("RC-2: is the title") {
                val title = randomString()

                sut(title = title, chromeVisible = false).top shouldBe ReaderLabel.Title(title)
            }

            context("with a blank title") {
                it("RC-3: is nothing, for an empty or whitespace title") {
                    checkAll(Exhaustive.of("", "   ")) { title ->
                        sut(title = title, chromeVisible = false).top shouldBe null
                    }
                }
            }
        }

        context("with the chrome up") {
            context("with pages left in the chapter") {
                it("RC-4: counts them, down to the last one") {
                    val location = randomRelocateData().copy(chapterPagesLeft = 1)

                    sut(location = location, chromeVisible = true).top shouldBe ReaderLabel.PagesLeftInChapter(1)
                }
            }

            context("without pages left in the chapter") {
                fun location() = randomRelocateData().copy(chapter = 3, totalChapters = 12)

                it("RC-5: is chapter of total, for a zero or negative count") {
                    checkAll(Exhaustive.of(0, -1)) { left ->
                        sut(location = location().copy(chapterPagesLeft = left), chromeVisible = true).top shouldBe
                            ReaderLabel.ChapterOf(3, 12)
                    }
                }

                it("RC-6: is nothing, for a missing chapter or chapter total") {
                    val none = location().copy(chapterPagesLeft = 0)

                    checkAll(Exhaustive.of(none.copy(chapter = 0), none.copy(totalChapters = 0))) { noChapter ->
                        sut(location = noChapter, chromeVisible = true).top shouldBe null
                    }
                }
            }
        }
    }

    describe("the bottom label") {
        context("with page numbers") {
            fun location() = randomRelocateData().copy(page = 1, totalPages = randomInt(1..1000))

            context("with the chrome hidden") {
                it("RC-2: is the bare page") {
                    sut(location = location(), chromeVisible = false).bottom shouldBe ReaderLabel.Page(1)
                }
            }

            context("with the chrome up") {
                it("RC-4: is page of total") {
                    val location = location()

                    sut(location = location, chromeVisible = true).bottom shouldBe
                        ReaderLabel.PageOf(1, location.totalPages)
                }
            }
        }

        context("without page numbers") {
            it("RC-7: falls back to the percent, for no total with the chrome up or hidden") {
                val location = randomRelocateData().copy(totalPages = 0, pct = randomInt(1..100))

                checkAll(Exhaustive.boolean()) { chrome ->
                    sut(location = location, chromeVisible = chrome).bottom shouldBe ReaderLabel.Percent(location.pct)
                }
            }

            it("RC-8: never shows page 0, with the chrome up or hidden") {
                val location = randomRelocateData().copy(page = 0, totalPages = randomInt(1..1000), pct = randomInt(1..100))

                checkAll(Exhaustive.boolean()) { chrome ->
                    sut(location = location, chromeVisible = chrome).bottom shouldBe ReaderLabel.Percent(location.pct)
                }
            }

            context("with a zero percent") {
                it("RC-9: is nothing") {
                    sut(location = randomRelocateData().copy(totalPages = 0, pct = 0)).bottom shouldBe null
                }
            }
        }
    }
})
