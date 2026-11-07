package com.omnibus.omnibus.data.library

import com.omnibus.omnibus.data.library.remote.BookDto
import com.omnibus.omnibus.data.library.remote.LibraryJson
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

private fun decodeBook(json: String): Book = LibraryJson.decodeFromString<BookDto>(json).toDomain()

class BookDecodingTest : DescribeSpec({

    describe("reading a book from the server") {

        it("TC-19: maps every field of the book record") {
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

            decodeBook(json) shouldBe Book(
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

        it("#93 TC-8: maps the detail fields") {
            val book = decodeBook(
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
            )

            book.description shouldBe "<p>Hi</p>"
            book.genres shouldBe listOf("Fantasy")
            book.subjects shouldBe listOf("tag")
            book.publisher shouldBe "Pub"
            book.publishedDate shouldBe "1925-04-10"
            book.language shouldBe "en"
            book.isbn13 shouldBe "9780000000001"
            book.seriesId shouldBe 4
        }

        context("when lists are missing") {
            it("TC-18: reads them as empty") {
                val book = decodeBook("""{"id": 4}""")

                book.creators.shouldBeEmpty()
                book.formats.shouldBeEmpty()
                book.bookFiles.shouldBeEmpty()
                book.genres.shouldBeEmpty()
                book.subjects.shouldBeEmpty()
            }
        }

        context("when lists are null") {
            it("TC-18: reads them as empty") {
                val book = decodeBook(
                    """{"id": 5, "creators": null, "formats": null, "book_files": null, "genres": null, "subjects": null}""",
                )

                book.creators.shouldBeEmpty()
                book.formats.shouldBeEmpty()
                book.bookFiles.shouldBeEmpty()
                book.genres.shouldBeEmpty()
                book.subjects.shouldBeEmpty()
            }
        }
    }
})
