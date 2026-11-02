package com.omnibus.omnibus.data.library

import com.omnibus.omnibus.data.library.remote.BookDto
import com.omnibus.omnibus.data.library.remote.LibraryJson
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class BookDecodingTest : DescribeSpec({

    describe("BookDto decoding") {

        it("TC-18: lenient decode - missing arrays are empty, a null title is null, unknown keys are ignored") {
            val book = LibraryJson.decodeFromString<BookDto>(
                """{"id": 4, "title": null, "unknown_key": {"nested": [1, 2]}, "future_field": true}""",
            ).toDomain()

            book.id shouldBe 4
            book.title.shouldBeNull()
            book.creators.shouldBeEmpty()
            book.formats.shouldBeEmpty()
            book.bookFiles.shouldBeEmpty()
        }

        it("TC-18: lenient decode - null arrays fall back to empty") {
            val book = LibraryJson.decodeFromString<BookDto>(
                """{"id": 5, "creators": null, "formats": null, "book_files": null}""",
            ).toDomain()

            book.creators.shouldBeEmpty()
            book.formats.shouldBeEmpty()
            book.bookFiles.shouldBeEmpty()
        }

        it("TC-19: full decode - every field maps to the domain Book") {
            val json = """
                {
                    "id": 12,
                    "unique_identifier": "uuid-12",
                    "filename": "dune.epub",
                    "title": "Dune",
                    "creators": [
                        {"name": "Frank Herbert", "role": "author", "file_as": "Herbert, Frank", "id": 3}
                    ],
                    "cover_url": "/api/books/uuid-12/cover",
                    "accent": "#c8a04a",
                    "formats": ["epub", "m4b"],
                    "series": "Dune Chronicles",
                    "series_index": "1.5",
                    "has_physical": true,
                    "added_at": "2026-01-02T03:04:05Z",
                    "last_interacted_at": "2026-02-03T04:05:06Z",
                    "book_files": [
                        {
                            "id": 30,
                            "format": "epub",
                            "filename": "dune.epub",
                            "ordinal": 0,
                            "label": "EPUB",
                            "size_bytes": 123456,
                            "etag": "\"abc\""
                        }
                    ]
                }
            """

            LibraryJson.decodeFromString<BookDto>(json).toDomain() shouldBe Book(
                id = 12,
                uniqueIdentifier = "uuid-12",
                filename = "dune.epub",
                title = "Dune",
                creators = listOf(Creator("Frank Herbert", role = "author", fileAs = "Herbert, Frank", id = 3)),
                coverUrl = "/api/books/uuid-12/cover",
                accent = "#c8a04a",
                formats = listOf("epub", "m4b"),
                series = "Dune Chronicles",
                seriesIndex = "1.5",
                hasPhysical = true,
                addedAt = "2026-01-02T03:04:05Z",
                lastInteractedAt = "2026-02-03T04:05:06Z",
                bookFiles = listOf(
                    BookFile(
                        id = 30,
                        format = "epub",
                        filename = "dune.epub",
                        ordinal = 0,
                        label = "EPUB",
                        sizeBytes = 123456,
                        etag = "\"abc\"",
                    ),
                ),
            )
        }
    }

    describe("#93 book detail fields") {

        val file = BookFile(
            id = 1,
            format = "epub",
            filename = "a.epub",
            ordinal = 0,
            label = null,
            sizeBytes = 1,
            etag = null,
        )

        it("#93 TC-1: year parses an ISO date") {
            testBook(1, publishedDate = "1925-04-10").year shouldBe 1925
        }

        it("#93 TC-2: year parses d/m/yyyy") {
            testBook(1, publishedDate = "8/4/2015").year shouldBe 2015
        }

        it("#93 TC-3: year is null without a four-digit run") {
            listOf<String?>("unknown", "", "   ", "99", null).forEach { published ->
                testBook(1, publishedDate = published).year.shouldBeNull()
            }
        }

        it("#93 TC-4: first run of exactly four ASCII digits") {
            yearIn("12345").shouldBeNull()
            yearIn("12345 and 1999") shouldBe 1999
            yearIn("Published 1925 (rev. 1999)") shouldBe 1925
            yearIn("١٩٢٥").shouldBeNull()
        }

        it("#93 TC-5: new fields default") {
            val book = testBook(1)

            book.description.shouldBeNull()
            book.genres.shouldBeEmpty()
            book.subjects.shouldBeEmpty()
            book.publisher.shouldBeNull()
            book.publishedDate.shouldBeNull()
            book.language.shouldBeNull()
            book.isbn13.shouldBeNull()
            book.seriesId.shouldBeNull()
            book.isWishlisted shouldBe false
            book.wishlistSource.shouldBeNull()
        }

        it("#93 TC-6: isWishlistOnly") {
            testBook(1, isWishlisted = true).isWishlistOnly shouldBe true
            testBook(1, isWishlisted = true, bookFiles = listOf(file)).isWishlistOnly shouldBe false
            testBook(1, bookFiles = listOf(file)).isWishlistOnly shouldBe false
            testBook(1).isWishlistOnly shouldBe false
        }

        it("#93 TC-7: testBook carries the new fields") {
            val book = testBook(
                1,
                genres = listOf("Fantasy"),
                subjects = listOf("t"),
                publishedDate = "2001",
                description = "<p>x</p>",
                seriesId = 4,
                isWishlisted = true,
            )

            book.genres shouldBe listOf("Fantasy")
            book.subjects shouldBe listOf("t")
            book.publishedDate shouldBe "2001"
            book.description shouldBe "<p>x</p>"
            book.seriesId shouldBe 4
            book.isWishlisted shouldBe true
        }

        it("#93 TC-8: decodes the new book fields") {
            val book = LibraryJson.decodeFromString<BookDto>(
                """
                {
                    "id": 9,
                    "description": "<p>Hi</p>",
                    "genres": ["Fantasy"],
                    "subjects": ["tag"],
                    "publisher": "Pub",
                    "published": "1925-04-10",
                    "language": "en",
                    "isbn13": "9780000000001",
                    "series_id": 4
                }
                """,
            ).toDomain()

            book.description shouldBe "<p>Hi</p>"
            book.genres shouldBe listOf("Fantasy")
            book.subjects shouldBe listOf("tag")
            book.publisher shouldBe "Pub"
            book.publishedDate shouldBe "1925-04-10"
            book.language shouldBe "en"
            book.isbn13 shouldBe "9780000000001"
            book.seriesId shouldBe 4
            book.year shouldBe 1925
        }

        it("#93 TC-9: missing new fields use defaults") {
            val book = LibraryJson.decodeFromString<BookDto>("""{"id": 1}""").toDomain()

            book.description.shouldBeNull()
            book.genres.shouldBeEmpty()
            book.subjects.shouldBeEmpty()
            book.publisher.shouldBeNull()
            book.publishedDate.shouldBeNull()
            book.language.shouldBeNull()
            book.isbn13.shouldBeNull()
            book.seriesId.shouldBeNull()
            book.isWishlisted shouldBe false
            book.wishlistSource.shouldBeNull()
        }

        it("#93 TC-10: null new fields and unknown keys") {
            val book = LibraryJson.decodeFromString<BookDto>(
                """{"id": 1, "genres": null, "subjects": null, "description": null, "series_id": null, "unknown_key": 5}""",
            ).toDomain()

            book.genres.shouldBeEmpty()
            book.subjects.shouldBeEmpty()
            book.description.shouldBeNull()
            book.seriesId.shouldBeNull()
        }
    }
})
