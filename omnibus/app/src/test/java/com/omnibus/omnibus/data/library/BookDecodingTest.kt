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
})
