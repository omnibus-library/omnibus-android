package com.omnibus.omnibus.feature.bookdetail.overview.readalikes

import com.omnibus.omnibus.data.library.BookFile
import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.data.suggestions.BookSuggestion
import com.omnibus.omnibus.data.suggestions.suggestion
import com.omnibus.omnibus.ui.components.CoverIdentity
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class ReadAlikesModelTest : DescribeSpec({

    describe("readAlikesOf") {

        it("#106 TC-20: readAlikesOf keeps the server order and builds a plate identity per suggestion") {
            val items = listOf(
                BookSuggestion(3, "https://hardcover.app/books/c", "Circe", "Madeline Miller", "/api/suggestions/u/1/cover"),
                BookSuggestion(1, "https://hardcover.app/books/a", "Piranesi", "Susanna Clarke", null),
                BookSuggestion(2, "https://hardcover.app/books/b", "Lavinia", "Ursula K. Le Guin", "/api/suggestions/u/3/cover"),
            )

            val shelf = readAlikesOf(items)

            shelf.map { it.hardcoverId } shouldContainExactly listOf(3L, 1L, 2L)
            shelf[0].hardcoverUrl shouldBe "https://hardcover.app/books/c"
            shelf[0].title shouldBe "Circe"
            shelf[0].author shouldBe "Madeline Miller"
            shelf[0].coverPath shouldBe "/api/suggestions/u/1/cover"
            shelf[1].coverPath shouldBe null
            shelf[0].identity shouldBe CoverIdentity(
                uuid = "hardcover-3",
                title = "Circe",
                creator = "Madeline Miller",
                hasCover = false,
            )
        }

        it("#106 TC-21: a blank author gives a plate identity without a creator") {
            val shelf = readAlikesOf(listOf(suggestion(4, author = "")))

            shelf.single().author shouldBe ""
            shelf.single().identity.creator shouldBe null
        }

        it("#106 TC-22: readAlikesOf drops suggestions without a safe Hardcover URL") {
            val items = listOf(
                suggestion(1, url = "https://hardcover.app/books/a"),
                suggestion(2, url = "javascript:alert(1)"),
                suggestion(3, url = "https://example.com/b"),
                suggestion(4, url = "https://www.hardcover.app/books/d"),
            )

            readAlikesOf(items).map { it.hardcoverId } shouldContainExactly listOf(1L, 4L)
            readAlikesOf(listOf(suggestion(5, url = "http://hardcover.app/b"))) shouldBe emptyList()
        }

        it("#106 TC-22b: readAlikesOf drops a repeated hardcover id, keeping the first") {
            val items = listOf(
                suggestion(1, title = "First"),
                suggestion(2),
                suggestion(1, title = "Second"),
            )

            val shelf = readAlikesOf(items)

            shelf.map { it.hardcoverId } shouldContainExactly listOf(1L, 2L)
            shelf.first().title shouldBe "First"
        }
    }

    describe("isHardcoverBookUrl") {

        listOf(
            "https://hardcover.app/books/x",
            "https://www.hardcover.app/books/x",
            "https://HARDCOVER.app/b",
        ).forEach { url ->
            it("#106 TC-23: isHardcoverBookUrl accepts https on hardcover.app and its subdomains: $url") {
                isHardcoverBookUrl(url) shouldBe true
            }
        }

        listOf(
            "http://hardcover.app/b",
            "https://example.com/b",
            "https://hardcover.app.evil.com/b",
            "https://evilhardcover.app/b",
            "javascript:alert(1)",
            "intent://x#Intent;end",
            "",
            "  ",
            "/books/x",
        ).forEach { url ->
            it("#106 TC-24: isHardcoverBookUrl rejects $url") {
                isHardcoverBookUrl(url) shouldBe false
            }
        }
    }

    describe("showsReadAlikes") {

        it("#106 TC-25: showsReadAlikes is true for a book with formats or files") {
            showsReadAlikes(testBook(1)) shouldBe true
            showsReadAlikes(
                testBook(
                    1,
                    formats = emptyList(),
                    bookFiles = listOf(BookFile(1, "epub", "a.epub", 0, null, 1, null)),
                ),
            ) shouldBe true
        }

        it("#106 TC-26: showsReadAlikes is true for a physical-only book") {
            showsReadAlikes(testBook(1, formats = emptyList()).copy(hasPhysical = true)) shouldBe true
        }

        it("#106 TC-27: showsReadAlikes is false for a book with no copy at all") {
            showsReadAlikes(testBook(1, formats = emptyList())) shouldBe false
        }

        it("#106 TC-28: showsReadAlikes hides a wishlist-only book and shows a wishlisted book that has files") {
            showsReadAlikes(testBook(1, isWishlisted = true, formats = emptyList())) shouldBe false
            showsReadAlikes(
                testBook(1, isWishlisted = true, formats = emptyList()).copy(hasPhysical = true),
            ) shouldBe false
            showsReadAlikes(
                testBook(
                    1,
                    isWishlisted = true,
                    formats = emptyList(),
                    bookFiles = listOf(BookFile(1, "epub", "a.epub", 0, null, 1, null)),
                ),
            ) shouldBe true
        }
    }

    describe("polling constants") {

        it("#106 TC-29: the poll interval is 4 s and the budget is 5 re-asks") {
            SUGGESTIONS_POLL_INTERVAL_MILLIS shouldBe 4_000L
            SUGGESTIONS_POLL_ATTEMPTS shouldBe 5
        }
    }
})
