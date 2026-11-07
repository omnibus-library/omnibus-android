package com.omnibus.omnibus.data.bookmarks.remote

import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class BookmarkDtoTest : DescribeSpec({

    describe("reading a bookmark row") {
        context("when every field is present") {
            it("TC-11: maps each field to the domain bookmark") {
                val id = randomLong()
                val clientId = randomString()
                val bookUuid = randomString()
                val position = randomString()
                val title = randomString()
                val chapterTitle = randomString()
                val createdAt = randomLong()
                val row = """
                    {"id":$id,"book_uuid":"$bookUuid","position":"$position","title":"$title","client_id":"$clientId",
                     "created_at":$createdAt,"created_at_iso":"2025-10-29T19:50:45Z","spine_index":2,
                     "chapter_title":"$chapterTitle","percent_through_book":12.5}
                """.trimIndent()

                BookmarksJson.decodeFromString<BookmarkDto>(row).toDomain() shouldBe Bookmark(
                    id = id,
                    clientId = clientId,
                    bookUuid = bookUuid,
                    position = position,
                    title = title,
                    chapterTitle = chapterTitle,
                    fraction = 0.125f,
                    createdAtSeconds = createdAt,
                )
            }
        }

        context("when only the id is usable") {
            it("TC-11: takes defaults for the fields that are missing or null") {
                val id = randomLong()
                val row = """{"id":$id,"extra":"ignored","title":null,"client_id":null,"chapter_title":null}"""

                BookmarksJson.decodeFromString<BookmarkDto>(row).toDomain() shouldBe Bookmark(
                    id = id,
                    clientId = null,
                    bookUuid = "",
                    position = "",
                    title = null,
                    chapterTitle = null,
                    fraction = null,
                    createdAtSeconds = null,
                )
            }
        }

        it("TC-12: clamps the percent to a 0..1 fraction, across in-range, out-of-range, missing and NaN percents") {
            checkAll(
                Exhaustive.of(
                    0.0 to 0f,
                    100.0 to 1f,
                    250.0 to 1f,
                    -5.0 to 0f,
                    null to null,
                    Double.NaN to null,
                ),
            ) { (percent, fraction) ->
                BookmarkDto(id = randomLong(), percentThroughBook = percent).toDomain().fraction shouldBe fraction
            }
        }
    }
})
