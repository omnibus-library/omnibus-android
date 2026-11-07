package com.omnibus.omnibus.feature.bookdetail.overview.readalikes

import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.library.randomBookFile
import com.omnibus.omnibus.data.suggestions.randomBookSuggestion
import com.omnibus.omnibus.ui.components.CoverIdentity
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ReadAlikesModelTest : DescribeSpec({
    describe("building the shelf from the server's suggestions") {
        it("#106 TC-20: keeps the server's order") {
            val items = List(3) { randomBookSuggestion() }

            readAlikesOf(items).map { it.hardcoverId } shouldContainExactly items.map { it.hardcoverId }
        }

        it("#106 TC-20: copies each item's details onto a plate with no cover") {
            val item = randomBookSuggestion()

            readAlikesOf(listOf(item)).single() shouldBe ReadAlike(
                hardcoverId = item.hardcoverId,
                hardcoverUrl = item.hardcoverUrl,
                title = item.title,
                author = item.author,
                identity = CoverIdentity(
                    uuid = "hardcover-${item.hardcoverId}",
                    title = item.title,
                    creator = item.author,
                    hasCover = false,
                ),
                coverPath = item.coverPath,
            )
        }

        context("when the author is blank") {
            it("#106 TC-21: draws the plate without a creator") {
                readAlikesOf(listOf(randomBookSuggestion().copy(author = ""))).single().identity.creator.shouldBeNull()
            }
        }

        context("when a link is not a safe Hardcover page") {
            it("#106 TC-22: drops that suggestion") {
                val safe = randomBookSuggestion()
                val unsafe = randomBookSuggestion().copy(hardcoverUrl = "javascript:alert(1)")

                readAlikesOf(listOf(unsafe, safe)).map { it.hardcoverId } shouldContainExactly listOf(safe.hardcoverId)
            }
        }

        context("when a Hardcover id repeats") {
            it("#106 TC-22b: keeps the first") {
                val first = randomBookSuggestion()
                val repeat = randomBookSuggestion().copy(hardcoverId = first.hardcoverId, title = first.title + "2")

                readAlikesOf(listOf(first, repeat)).single().title shouldBe first.title
            }
        }
    }

    describe("deciding whether a link is safe to open") {
        it("#106 TC-23: accepts https on hardcover.app or its subdomains, in any case") {
            checkAll(
                Exhaustive.of("https://hardcover.app/books/x", "https://www.hardcover.app/books/x", "https://HARDCOVER.app/b"),
            ) { url ->
                isHardcoverBookUrl(url) shouldBe true
            }
        }

        it("#106 TC-24: rejects anything else, for other schemes, other hosts, lookalikes or non-links") {
            checkAll(
                Exhaustive.of(
                    "http://hardcover.app/b",
                    "https://example.com/b",
                    "https://hardcover.app.evil.com/b",
                    "https://evilhardcover.app/b",
                    "javascript:alert(1)",
                    "intent://x#Intent;end",
                    "",
                    "  ",
                    "/books/x",
                ),
            ) { url ->
                isHardcoverBookUrl(url) shouldBe false
            }
        }
    }

    describe("whether the section appears") {
        /** A book not on the wishlist, and with no copy. */
        fun noCopy() = randomBook().copy(isWishlisted = false, formats = emptyList(), bookFiles = emptyList(), hasPhysical = false)

        context("when the book has a copy") {
            it("#106 TC-25: shows the section, for a format, a file or a physical copy") {
                val books = Exhaustive.of(
                    noCopy().copy(formats = listOf("epub")),
                    noCopy().copy(bookFiles = listOf(randomBookFile())),
                    noCopy().copy(hasPhysical = true),
                )

                checkAll(books) { book -> showsReadAlikes(book) shouldBe true }
            }
        }

        context("when the book has no copy") {
            it("#106 TC-27: leaves the section out") {
                showsReadAlikes(noCopy()) shouldBe false
            }
        }

        context("when the book is on the wishlist") {
            context("when it has no files") {
                it("#106 TC-28: leaves the section out, even for a physical copy") {
                    showsReadAlikes(noCopy().copy(isWishlisted = true, hasPhysical = true)) shouldBe false
                }
            }

            context("when it has a file") {
                it("#106 TC-28: shows the section") {
                    showsReadAlikes(noCopy().copy(isWishlisted = true, bookFiles = listOf(randomBookFile()))) shouldBe true
                }
            }
        }
    }
})
