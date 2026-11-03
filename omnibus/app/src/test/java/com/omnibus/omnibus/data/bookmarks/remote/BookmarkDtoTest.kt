package com.omnibus.omnibus.data.bookmarks.remote

import com.omnibus.omnibus.data.bookmarks.Bookmark
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okio.Buffer

class BookmarkDtoTest : DescribeSpec({

    describe("the bookmark row") {

        // TC-11: a full row maps every field; a minimal row decodes with defaults and ignores keys the app does not read.
        it("TC-11: rows decode leniently into the domain bookmark") {
            val full = """
                {"id":9,"book_uuid":"book-1","position":"epubcfi(/6/4!/4/2:0)","title":"Mine","client_id":"client-9",
                 "created_at":1761775445,"created_at_iso":"2025-10-29T19:50:45Z","spine_index":2,
                 "chapter_title":"One","percent_through_book":12.5}
            """.trimIndent()

            BookmarksJson.decodeFromString<BookmarkDto>(full).toDomain() shouldBe Bookmark(
                id = 9,
                clientId = "client-9",
                bookUuid = "book-1",
                position = "epubcfi(/6/4!/4/2:0)",
                title = "Mine",
                chapterTitle = "One",
                fraction = 0.125f,
                createdAtSeconds = 1_761_775_445L,
            )

            val minimal = """{"id":3,"extra":"ignored","title":null,"client_id":null,"chapter_title":null}"""
            BookmarksJson.decodeFromString<BookmarkDto>(minimal).toDomain() shouldBe Bookmark(
                id = 3,
                clientId = null,
                bookUuid = "",
                position = "",
                title = null,
                chapterTitle = null,
                fraction = null,
                createdAtSeconds = null,
            )
        }

        // TC-12: the percent maps to a 0..1 fraction; a missing percent or NaN gives null.
        it("TC-12: fraction is clamped to 0..1 and null when missing or NaN") {
            fun fractionFor(percent: Double?) = BookmarkDto(id = 1L, percentThroughBook = percent).toDomain().fraction

            fractionFor(0.0) shouldBe 0f
            fractionFor(100.0) shouldBe 1f
            fractionFor(250.0) shouldBe 1f
            fractionFor(-5.0) shouldBe 0f
            fractionFor(null) shouldBe null
            fractionFor(Double.NaN) shouldBe null
        }

        // TC-13: the create body is one flat object with the snake_case names, sent as JSON; a null title is left out.
        it("TC-13: the create body is one flat object and omits a null title") {
            fun bodyOf(title: String?): Pair<String, JsonObject> {
                val request = BookmarkCreateRequestDto(
                    bookUuid = "book-1",
                    position = "epubcfi(/6/4)",
                    title = title,
                    clientId = "client-1",
                    clientCreatedAt = 1_700_000_123L,
                ).toJsonRequest()
                val text = Buffer().also { request.writeTo(it) }.readUtf8()
                return request.contentType().toString() to Json.parseToJsonElement(text).jsonObject
            }

            val (contentType, withTitle) = bodyOf("Chapter 1")
            contentType shouldBe "application/json; charset=utf-8"
            withTitle.keys shouldBe setOf("book_uuid", "position", "title", "client_id", "client_created_at")
            withTitle["book_uuid"]!!.jsonPrimitive.content shouldBe "book-1"
            withTitle["position"]!!.jsonPrimitive.content shouldBe "epubcfi(/6/4)"
            withTitle["title"]!!.jsonPrimitive.content shouldBe "Chapter 1"
            withTitle["client_id"]!!.jsonPrimitive.content shouldBe "client-1"
            withTitle["client_created_at"]!!.jsonPrimitive.long shouldBe 1_700_000_123L

            val (_, withoutTitle) = bodyOf(null)
            withoutTitle.keys shouldBe setOf("book_uuid", "position", "client_id", "client_created_at")
        }
    }
})
