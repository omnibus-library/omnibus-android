package com.omnibus.omnibus.ui.components

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean

private val ebookFormats = listOf("epub", "PDF", "mobi", "CBZ")
private val audioFormats = listOf("m4b", "MP3", "Flac")
private val otherFormats = listOf("txt", "doc", "html")

class FormatBadgesTest : DescribeSpec({

    describe("the format badges of a book") {

        it("TC-8: shows EBOOK, AUDIO and PRINT in that order, each at most once, for any mix of formats and physical copy") {
            val formats = Arb.list(Arb.element(ebookFormats + audioFormats + otherFormats), 0..6)

            checkAll(formats, Exhaustive.boolean()) { bookFormats, hasPhysical ->
                val expected = buildList {
                    if (bookFormats.any { it.lowercase() in ebookFormats.map(String::lowercase) }) add(FormatBadge.Ebook)
                    if (bookFormats.any { it.lowercase() in audioFormats.map(String::lowercase) }) add(FormatBadge.Audio)
                    if (hasPhysical) add(FormatBadge.Print)
                }

                formatBadges(bookFormats, hasPhysical) shouldBe expected
            }
        }

        it("TC-8: shows no badge for a book with no matching format and no physical copy") {
            formatBadges(otherFormats, hasPhysical = false) shouldBe emptyList()
        }
    }
})
