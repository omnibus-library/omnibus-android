package com.omnibus.omnibus.feature.search.rows

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.testing.randomBoolean
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection

/** Each format in a random letter case, as a server may send it. */
private fun inRandomCase(format: String): String = if (randomBoolean()) format.uppercase() else format

class FormatBadgesTest : DescribeSpec({

    describe("the badges for a book") {

        it("#351 TC-18: shows Ebook for every ebook format, in any letter case") {
            checkAll(Exhaustive.collection(Book.EBOOK_FORMATS)) { format ->
                formatBadges(listOf(inRandomCase(format)), hasPhysical = false) shouldBe listOf(FormatBadge.Ebook)
            }
        }

        it("#351 TC-18: shows Audio for every audio format, in any letter case") {
            checkAll(Exhaustive.collection(Book.AUDIO_FORMATS)) { format ->
                formatBadges(listOf(inRandomCase(format)), hasPhysical = false) shouldBe listOf(FormatBadge.Audio)
            }
        }

        it("#351 TC-18: shows no badge for formats that are neither ebook nor audio") {
            formatBadges(listOf("djvu", "txt"), hasPhysical = false) shouldBe emptyList()
        }

        it("#351 TC-18: shows no badge for a book with no formats") {
            formatBadges(emptyList(), hasPhysical = false) shouldBe emptyList()
        }

        it("#351 TC-18: shows each badge once, in display order, for several formats and a physical copy") {
            formatBadges(listOf("EPUB", "pdf", "M4B", "mp3"), hasPhysical = true) shouldBe
                listOf(FormatBadge.Ebook, FormatBadge.Audio, FormatBadge.Print)
        }
    }
})
