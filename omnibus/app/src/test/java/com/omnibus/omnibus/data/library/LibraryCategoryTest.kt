package com.omnibus.omnibus.data.library

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class LibraryCategoryTest : DescribeSpec({

    describe("formats") {

        it("TC-105: All has no formats, Ebooks and Audiobooks list exactly the wire formats in order") {
            LibraryCategory.All.formats.isEmpty() shouldBe true
            LibraryCategory.Ebooks.formats.joinToString(",") shouldBe "epub,pdf,mobi,azw3,cbz,cbr"
            LibraryCategory.Audiobooks.formats.joinToString(",") shouldBe "m4b,m4a,mp3,aac,flac,ogg,opus"
        }

        it("TC-106: category formats are not taken from Book's format sets") {
            (LibraryCategory.Ebooks.formats.contains("kepub")) shouldBe false
            (LibraryCategory.Audiobooks.formats.contains("wav")) shouldBe false
        }
    }

    describe("toQuery") {

        it("TC-108: carries the given sort and direction for every sort and both directions") {
            LibrarySort.entries.forEach { sort ->
                SortDirection.entries.forEach { dir ->
                    val query = LibraryCategory.Ebooks.toQuery(sort, dir)

                    query.sort shouldBe sort
                    query.dir shouldBe dir
                    query.formats shouldBe LibraryCategory.Ebooks.formats
                }
            }
        }

        it("TC-108: the wire tokens are the server's sort and direction values") {
            LibrarySort.entries.map { it.apiName } shouldBe listOf(
                "title",
                "author",
                "series",
                "recently_interacted",
                "last_updated",
                "newest_added",
            )
            SortDirection.entries.map { it.apiName } shouldBe listOf("asc", "desc")
        }

        it("TC-108: All with the default sort and direction is the default query") {
            LibraryCategory.All.toQuery(LibrarySort.NewestAdded, SortDirection.Desc) shouldBe BooksPageQuery()
        }
    }
})
