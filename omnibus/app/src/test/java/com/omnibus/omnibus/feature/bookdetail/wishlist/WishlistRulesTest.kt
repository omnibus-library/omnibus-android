package com.omnibus.omnibus.feature.bookdetail.wishlist

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.wishlist.WishlistState
import com.omnibus.omnibus.data.wishlist.randomWishlistEntry
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.actionbar.ActionBarModel
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.of
import java.net.URLDecoder

/** A book with the given formats. */
private fun book(vararg formats: String): Book = randomBook().copy(formats = formats.toList())

private fun present() = WishlistState.Present(randomWishlistEntry())

private val NOT_PRESENT = listOf(WishlistState.Unknown, WishlistState.Absent, WishlistState.Failed)

class WishlistRulesTest : DescribeSpec({

    describe("whether a book is wishlist-only") {

        context("when the book is on the wishlist") {
            context("when it has no readable copy") {
                it("#119 TC-16: is, for no format or an unknown format") {
                    val filelessFormats = listOf(emptyList(), listOf("djvu"))

                    checkAll(Exhaustive.collection(filelessFormats)) { formats ->
                        isWishlistOnly(randomBook().copy(formats = formats), present()) shouldBe true
                    }
                }
            }

            context("when it has a readable copy") {
                it("#119 TC-16: is not, for an ebook, an audiobook or both") {
                    val readableFormats = listOf(listOf("epub"), listOf("m4b"), listOf("epub", "m4b"))

                    checkAll(Exhaustive.collection(readableFormats)) { formats ->
                        isWishlistOnly(randomBook().copy(formats = formats), present()) shouldBe false
                    }
                }
            }
        }

        context("when the book is not on the wishlist") {
            it("#119 TC-16: is not, whatever the state") {
                checkAll(Exhaustive.collection(NOT_PRESENT)) { state ->
                    isWishlistOnly(book(), state) shouldBe false
                }
            }
        }
    }

    describe("the action bar's wishlist variant") {

        context("when the book is wishlist-only") {
            it("#119 TC-17: checks in on the book's page") {
                val book = book()

                val model = wishlistActionModel(book, present()).shouldBeInstanceOf<ActionBarModel.Wishlist>()

                model.checkIn shouldBe BookDetailDestination.CheckIn(book.uuid)
            }

            context("when the book has no ISBN") {
                context("when the author is unknown") {
                    it("#119 TC-17: never searches for the placeholder") {
                        val noAuthor = book().copy(isbn13 = null, title = "Dune", creators = emptyList())

                        wishlistActionModel(noAuthor, present())?.findCopy shouldBe
                            BookDetailDestination.ExternalUrl("https://www.amazon.com/s?k=Dune")
                    }
                }
            }
        }

        context("when the book is not wishlist-only") {
            it("#119 TC-17: is none") {
                wishlistActionModel(book("epub"), present()) shouldBe null
            }
        }
    }

    describe("the store search URL") {

        context("when there is an ISBN") {
            it("#119 TC-18: queries it, trimmed") {
                storeSearchUrl("  9781234567897 ", "Ignored", "Ignored") shouldBe
                    "https://www.amazon.com/s?k=9781234567897"
            }
        }

        context("when the ISBN is blank") {
            it("#119 TC-19: queries the title and author, for a missing, empty or blank ISBN") {
                checkAll(Exhaustive.of(null, "", "   ")) { isbn ->
                    storeSearchUrl(isbn, "Dune", "Frank Herbert") shouldBe "https://www.amazon.com/s?k=Dune+Frank+Herbert"
                }
            }
        }

        context("when the text has special characters") {
            it("#119 TC-20: encodes them so the query decodes back") {
                val title = "Fish & Chips #1: 100% \"Real\" + ?=é 日本 🐟"
                val author = "O'Brien = Ångström"

                val query = storeSearchUrl(null, title, author).substringAfter("k=")

                listOf("&", "#", "?", " ").forEach { query shouldBe query.replace(it, "") }
                URLDecoder.decode(query, "UTF-8") shouldBe "$title $author"
            }
        }
    }

    describe("the More menu row") {

        context("when the book is not on the wishlist") {
            it("#119 TC-21: is hidden, regardless of state, connection or removal") {
                checkAll(Exhaustive.collection(NOT_PRESENT), Exhaustive.boolean(), Exhaustive.boolean()) { state, online, removing ->
                    removeRow(state, online, removing) shouldBe RemoveRow.Hidden
                }
            }
        }

        context("when the book is on the wishlist") {
            it("#119 TC-21: follows the connection first, then any removal in flight") {
                checkAll(
                    Exhaustive.of(
                        Triple(false, true, RemoveRow.DisabledOffline),
                        Triple(false, false, RemoveRow.DisabledOffline),
                        Triple(true, true, RemoveRow.DisabledRemoving),
                        Triple(true, false, RemoveRow.Enabled),
                    ),
                ) { (online, removing, expected) ->
                    removeRow(present(), online, removing) shouldBe expected
                }
            }
        }
    }

    describe("leaving the page") {
        context("after a removal") {
            it("#119 TC-23: leaves a deleted book or one nothing else holds") {
                checkAll(
                    Exhaustive.of(
                        Triple(true, true, true) to true,
                        Triple(true, false, false) to true,
                        Triple(false, false, false) to true,
                        Triple(false, true, false) to false,
                        Triple(false, false, true) to false,
                    ),
                ) { (inputs, expected) ->
                    val (bookDeleted, hasFile, hasPhysical) = inputs

                    leavesAfterRemoval(bookDeleted, hasFile, hasPhysical) shouldBe expected
                }
            }
        }
    }
})
