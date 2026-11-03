package com.omnibus.omnibus.feature.bookdetail.wishlist

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.data.wishlist.WishlistEntry
import com.omnibus.omnibus.data.wishlist.WishlistSource
import com.omnibus.omnibus.data.wishlist.WishlistState
import com.omnibus.omnibus.feature.bookdetail.actionbar.hasReadableCopy
import com.omnibus.omnibus.feature.bookdetail.progress.hasProgressFile
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.net.URLDecoder

/** A book with the given formats, and a physical copy when [physical] is set. */
private fun book(formats: List<String>, physical: Boolean = false): Book =
    testBook(1, formats = formats).copy(hasPhysical = physical)

private val present = WishlistState.Present(WishlistEntry(id = 1L, addedAtMillis = null, source = null))

/** The states the tables walk: U, A, P, F. */
private val allStates: List<Pair<String, WishlistState>> = listOf(
    "U" to WishlistState.Unknown,
    "A" to WishlistState.Absent,
    "P" to present,
    "F" to WishlistState.Failed,
)

/** Fileless books (no formats, or formats outside the known lists), with and without a physical copy. */
private val fileless: List<Pair<String, Book>> = listOf(
    "none" to book(emptyList()),
    "none physical" to book(emptyList(), physical = true),
    "unknown format" to book(listOf("djvu")),
    "unknown format physical" to book(listOf("djvu"), physical = true),
)

/** Books with a readable copy, with and without a physical copy. */
private val withFile: List<Pair<String, Book>> = listOf(
    "ebook" to book(listOf("epub")),
    "audiobook" to book(listOf("m4b")),
    "both" to book(listOf("epub", "m4b")),
    "ebook physical" to book(listOf("epub"), physical = true),
)

class WishlistRulesTest : DescribeSpec({

    describe("the wishlist-only gate") {

        it("#119 TC-16: isWishlistOnly is true only for Present with no readable copy") {
            for ((state, value) in allStates) {
                for ((name, b) in fileless + withFile) {
                    withClue("$state/$name") {
                        val expected = value is WishlistState.Present && name in fileless.map { it.first }
                        isWishlistOnly(b, value) shouldBe expected
                    }
                }
            }
        }
    }

    describe("the store search URL") {

        it("#119 TC-18: an ISBN-13 is the query, trimmed") {
            storeSearchUrl("9781234567897", "Ignored", "Ignored") shouldBe
                "https://www.amazon.com/s?k=9781234567897"
            storeSearchUrl("  9781234567897 ", "Ignored", "Ignored") shouldBe
                "https://www.amazon.com/s?k=9781234567897"
        }

        it("#119 TC-19: without an ISBN the query is the trimmed title and author, with no trailing separator") {
            listOf(null, "", "   ").forEach { isbn ->
                storeSearchUrl(isbn, "Dune", "Frank Herbert") shouldBe
                    "https://www.amazon.com/s?k=Dune+Frank+Herbert"
                storeSearchUrl(isbn, "Dune", "") shouldBe "https://www.amazon.com/s?k=Dune"
            }
        }

        it("#119 TC-20: special characters are UTF-8 encoded, the query has no raw separators, and it decodes back") {
            val title = "Fish & Chips #1: 100% \"Real\" + ?=é 日本 🐟"
            val author = "O'Brien = Ångström"
            val url = storeSearchUrl(null, title, author)

            val query = url.substringAfter("k=")
            listOf("&", "#", "?", " ").forEach { query shouldBe query.replace(it, "") }
            URLDecoder.decode(query, "UTF-8") shouldBe "$title $author".trim()
        }
    }

    describe("the More menu row") {

        it("#119 TC-21: hidden unless Present; for Present, enabled, disabled offline or disabled while removing") {
            for ((state, value) in allStates) {
                for (online in listOf(true, false)) {
                    for (removing in listOf(true, false)) {
                        withClue("$state online=$online removing=$removing") {
                            val expected = when {
                                value !is WishlistState.Present -> RemoveRow.Hidden
                                !online -> RemoveRow.DisabledOffline
                                removing -> RemoveRow.DisabledRemoving
                                else -> RemoveRow.Enabled
                            }
                            removeRow(value, online, removing) shouldBe expected
                        }
                    }
                }
            }
        }

        it("#119 TC-22: the removal warning follows hasFile, keyed on a readable copy") {
            removalWarning(hasFile = true) shouldBe RemovalWarning.KeepsBook
            removalWarning(hasFile = false) shouldBe RemovalWarning.OnlyPlace

            for ((name, b) in withFile) {
                withClue(name) { removalWarning(hasReadableCopy(b)) shouldBe RemovalWarning.KeepsBook }
            }
            for ((name, b) in fileless) {
                withClue(name) { removalWarning(hasReadableCopy(b)) shouldBe RemovalWarning.OnlyPlace }
            }
        }
    }

    describe("leaving the page after a removal") {

        it("#119 TC-23: leaves when the book is deleted, or when there is no file and no physical copy") {
            for (bookDeleted in listOf(true, false)) {
                for (hasFile in listOf(true, false)) {
                    for (hasPhysical in listOf(true, false)) {
                        withClue("deleted=$bookDeleted file=$hasFile physical=$hasPhysical") {
                            val expected = bookDeleted || (!hasFile && !hasPhysical)
                            leavesAfterRemoval(bookDeleted, hasFile, hasPhysical) shouldBe expected
                        }
                    }
                }
            }
        }
    }

    describe("the progress gate") {

        it("#119 TC-25: a wishlist-only book never has a progress file") {
            for ((state, value) in allStates) {
                for ((name, b) in fileless) {
                    withClue("$state/$name") {
                        if (isWishlistOnly(b, value)) hasProgressFile(b) shouldBe false
                    }
                }
            }
        }
    }
})
