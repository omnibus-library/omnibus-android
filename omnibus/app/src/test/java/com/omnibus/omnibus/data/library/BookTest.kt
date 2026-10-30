package com.omnibus.omnibus.data.library

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class BookTest : DescribeSpec({

    describe("uuid") {

        it("TC-20: uses uniqueIdentifier when present") {
            testBook(1, uniqueIdentifier = "abc-123").uuid shouldBe "abc-123"
        }

        it("TC-20: falls back to id.toString() when uniqueIdentifier is null") {
            testBook(7, uniqueIdentifier = null).uuid shouldBe "7"
        }
    }

    describe("displayTitle") {

        it("TC-21: is the title when present") {
            testBook(1, title = "Dune", filename = "dune.epub").displayTitle shouldBe "Dune"
        }

        it("TC-21: is the filename when the title is null") {
            testBook(1, title = null, filename = "dune.epub").displayTitle shouldBe "dune.epub"
        }

        it("TC-21: is the filename when the title is blank") {
            testBook(1, title = "   ", filename = "dune.epub").displayTitle shouldBe "dune.epub"
        }
    }

    describe("authorDisplay") {

        it("TC-22: joins creator names with a comma") {
            val book = testBook(
                1,
                creators = listOf(
                    Creator("Ann", null, null, null),
                    Creator("Ben", null, null, null),
                ),
            )

            book.authorDisplay shouldBe "Ann, Ben"
        }

        it("TC-22: skips blank creator names") {
            val book = testBook(
                1,
                creators = listOf(
                    Creator("Ann", null, null, null),
                    Creator("   ", null, null, null),
                    Creator("Ben", null, null, null),
                ),
            )

            book.authorDisplay shouldBe "Ann, Ben"
        }

        it("TC-22: is Unknown author when there are no creators") {
            testBook(1, creators = emptyList()).authorDisplay shouldBe "Unknown author"
        }
    }

    describe("hasEpub") {

        it("TC-49: is true for formats=[epub]") {
            testBook(1, formats = listOf("epub")).hasEpub.shouldBeTrue()
        }

        it("TC-49: is true for formats=[EPUB] (case-insensitive)") {
            testBook(1, formats = listOf("EPUB")).hasEpub.shouldBeTrue()
        }

        it("TC-49: is true when a bookFile has format Epub and formats are empty") {
            testBook(1, formats = emptyList()).copy(
                bookFiles = listOf(
                    BookFile(id = 1, format = "Epub", filename = "a.epub", ordinal = 0, label = null, sizeBytes = 1, etag = null),
                ),
            ).hasEpub.shouldBeTrue()
        }

        it("TC-49: is false for only pdf and cbz formats") {
            testBook(1, formats = listOf("pdf", "cbz")).hasEpub.shouldBeFalse()
        }

        it("TC-49: is false when formats and bookFiles are both empty") {
            testBook(1, formats = emptyList()).hasEpub.shouldBeFalse()
        }

        it("TC-49: excludes pdf and cbz, unlike hasEbook") {
            testBook(1, formats = listOf("pdf")).let {
                it.hasEbook.shouldBeTrue()
                it.hasEpub.shouldBeFalse()
            }
        }
    }

    describe("format classification") {

        listOf("epub", "kepub", "pdf", "mobi", "azw3", "cbz", "cbr").forEach { format ->

            it("TC-23: hasEbook is true for $format in either case") {
                testBook(1, formats = listOf(format)).hasEbook.shouldBeTrue()
                testBook(1, formats = listOf(format.uppercase())).hasEbook.shouldBeTrue()
            }
        }

        listOf("m4b", "m4a", "mp3", "aac", "flac", "ogg", "opus", "wav").forEach { format ->

            it("TC-23: hasAudiobook is true for $format in either case") {
                testBook(1, formats = listOf(format)).hasAudiobook.shouldBeTrue()
                testBook(1, formats = listOf(format.uppercase())).hasAudiobook.shouldBeTrue()
            }
        }

        it("TC-23: unrelated and empty formats are neither ebook nor audiobook") {
            testBook(1, formats = listOf("txt", "docx")).let {
                it.hasEbook.shouldBeFalse()
                it.hasAudiobook.shouldBeFalse()
            }
            testBook(1, formats = emptyList()).let {
                it.hasEbook.shouldBeFalse()
                it.hasAudiobook.shouldBeFalse()
            }
        }

        it("TC-23: a book can be both an ebook and an audiobook") {
            testBook(1, formats = listOf("epub", "m4b")).let {
                it.hasEbook.shouldBeTrue()
                it.hasAudiobook.shouldBeTrue()
            }
        }
    }
})
