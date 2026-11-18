package com.omnibus.omnibus.data.highlights

import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

/** A surrogate pair: one code point, two UTF-16 units. */
private const val ASTRAL = "📖"

/** An EPUB CFI range of [codePoints] code points, all ASCII. */
private fun rangeOf(codePoints: Int): String = "epubcfi(" + "a".repeat(codePoints - "epubcfi(".length)

class HighlightDraftTest : DescribeSpec({

    describe("creating a highlight draft") {
        context("when the book uuid is blank") {
            it("#54 TC-1: gives no draft for empty and whitespace uuids") {
                checkAll(Exhaustive.of("", "   ")) { uuid ->
                    HighlightDraft.create(uuid, rangeOf(64), randomEnum(), randomString()).shouldBeNull()
                }
            }
        }

        context("when the cfi range is null, blank or not an EPUB CFI") {
            it("#54 TC-1: gives no draft for each") {
                checkAll(Exhaustive.of(null, "", "   ", "pdf:/page/3", "/6/4!/4", "pdf-page:3")) { range ->
                    HighlightDraft.create(randomString(), range, randomEnum(), randomString()).shouldBeNull()
                }
            }
        }

        context("when the cfi range is exactly 4096 code points") {
            it("#54 TC-1: makes a draft that keeps the range") {
                val range = rangeOf(4096)

                HighlightDraft.create(randomString(), range, randomEnum(), null)?.cfiRange shouldBe range
            }
        }

        context("when the cfi range is 4097 code points") {
            it("#54 TC-1: gives no draft") {
                HighlightDraft.create(randomString(), rangeOf(4097), randomEnum(), null).shouldBeNull()
            }
        }

        context("when the text has whitespace around it") {
            it("#54 TC-1: trims the text") {
                val text = randomString()

                HighlightDraft.create(randomString(), rangeOf(64), randomEnum(), "  $text\n")?.text shouldBe text
            }
        }

        context("when the text is missing or blank") {
            it("#54 TC-1: makes a draft with a null text for null, empty and whitespace texts") {
                checkAll(Exhaustive.of(null, "", "   ")) { text ->
                    HighlightDraft.create(randomString(), rangeOf(64), randomEnum(), text).shouldNotBeNull().text.shouldBeNull()
                }
            }
        }

        context("when the text is exactly 8192 code points") {
            it("#54 TC-1: keeps it whole") {
                val text = "t".repeat(8192)

                HighlightDraft.create(randomString(), rangeOf(64), randomEnum(), text)?.text shouldBe text
            }
        }

        context("when the text is 8193 code points") {
            it("#54 TC-1: cuts it to its first 8192 code points") {
                val first8192 = "t".repeat(8192)

                HighlightDraft.create(randomString(), rangeOf(64), randomEnum(), first8192 + "x")?.text shouldBe first8192
            }
        }

        context("when the 8192nd code point of a longer text is a surrogate pair") {
            it("#54 TC-1: keeps the pair whole") {
                val first8192 = "t".repeat(8191) + ASTRAL

                HighlightDraft.create(randomString(), rangeOf(64), randomEnum(), first8192 + "x")?.text shouldBe first8192
            }
        }
    }
})
