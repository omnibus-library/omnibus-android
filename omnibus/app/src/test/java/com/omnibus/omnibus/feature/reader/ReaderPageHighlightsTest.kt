package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.highlights.randomHighlight
import com.omnibus.omnibus.feature.reader.ui.randomCfi
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ReaderPageHighlightsTest : DescribeSpec({

    describe("the highlights the page draws from saved rows") {
        context("when a saved row has no EPUB location") {
            it("#54 TC-2: draws none of them, for a null, PDF or Kobo location") {
                checkAll(Exhaustive.of(null, "pdf:/page/3", "pdf-page:3", "/body/DocFragment[3]/body/p[12]")) { location ->
                    pageHighlightsOf(listOf(randomHighlight().copy(location = location)), emptyList()).shouldBeEmpty()
                }
            }
        }

        context("when a saved row has an EPUB location") {
            it("#54 TC-2: draws it in the row's colour") {
                val row = randomHighlight().copy(location = randomCfi(), color = randomEnum())

                pageHighlightsOf(listOf(row), emptyList()).single().color shouldBe row.color
            }

            it("#54 TC-2: draws it with its location trimmed") {
                val cfi = randomCfi()

                pageHighlightsOf(listOf(randomHighlight().copy(location = " \n$cfi  ")), emptyList())
                    .single().cfiRange shouldBe cfi
            }

            context("when the note is null or blank") {
                it("#54 TC-2: draws it without a note") {
                    checkAll(Exhaustive.of(null, "", "   ")) { note ->
                        val row = randomHighlight().copy(location = randomCfi(), note = note)

                        pageHighlightsOf(listOf(row), emptyList()).single().hasNote shouldBe false
                    }
                }
            }

            context("when the note has text") {
                it("#54 TC-2: draws it with a note") {
                    val row = randomHighlight().copy(location = randomCfi(), note = randomString())

                    pageHighlightsOf(listOf(row), emptyList()).single().hasNote shouldBe true
                }
            }
        }
    }

    describe("the highlights the page draws from session and saved rows") {
        context("when a session row and a saved row share a cfiRange") {
            it("#54 TC-3: keeps one entry for the range, the session copy winning") {
                val cfi = randomCfi()
                val session = PageHighlight(cfi, HighlightColor.Amber, hasNote = true)
                val saved = randomHighlight().copy(location = cfi, color = HighlightColor.Green, note = null)

                pageHighlightsOf(listOf(saved), listOf(session)) shouldBe listOf(session)
            }
        }

        context("when a session row and a saved row have different cfiRanges") {
            it("#54 TC-3: lists the session row before the saved one") {
                val session = randomPageHighlight()
                val savedCfi = randomCfi()
                val saved = randomHighlight().copy(location = savedCfi)

                pageHighlightsOf(listOf(saved), listOf(session)).map { it.cfiRange } shouldContainExactly
                    listOf(session.cfiRange, savedCfi)
            }
        }
    }
})
