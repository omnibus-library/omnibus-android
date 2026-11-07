package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.feature.reader.ui.randomCfi
import com.omnibus.omnibus.testing.randomInt
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ReaderStartTest : DescribeSpec({

    describe("resolving where a position opens") {
        context("with its own EPUB cfi") {
            it("TC-62: opens at that cfi over a derived cfi or percent") {
                val cfi = randomCfi()

                readerStartOf(cfi, randomCfi(), randomInt(1..100)) shouldBe ReaderStart.AtCfi(cfi)
            }
        }

        context("with no own EPUB cfi") {
            context("with a percent") {
                context("with a derived EPUB cfi") {
                    it("TC-62: boots at the derived cfi") {
                        val derived = randomCfi()

                        readerStartOf(null, derived, 57) shouldBe ReaderStart.AtPercent(0.57, derived)
                    }
                }

                context("without a derived EPUB cfi") {
                    it("TC-62: opens near the percent, for no anchor or a non-cfi one") {
                        checkAll(Exhaustive.of(null, "pdf-page:3")) { anchor ->
                            readerStartOf(anchor, anchor, 40) shouldBe ReaderStart.AtPercent(0.4, null)
                        }
                    }
                }
            }

            context("without a positive percent") {
                it("TC-62: opens at the beginning, for a zero or missing percent") {
                    checkAll(Exhaustive.of(null, 0)) { percent ->
                        readerStartOf(null, null, percent) shouldBe ReaderStart.Beginning
                    }
                }
            }
        }
    }
})
