package com.omnibus.omnibus.data.journals

import com.omnibus.omnibus.data.journals.remote.JournalEntryDto
import com.omnibus.omnibus.data.journals.remote.JournalsJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException

class JournalEntryDtoTest : DescribeSpec({

    describe("the wire mapping") {

        it("TC-1: a full server row maps every field, and seconds become milliseconds") {
            val json = """
                {"id":9,"author_id":7,"author_name":"Ann","body_md":"He was **here**","status":"published",
                 "created_at":1761264000}
            """.trimIndent()

            JournalsJson.decodeFromString<JournalEntryDto>(json).toDomain() shouldBe JournalEntry(
                id = 9,
                authorId = 7,
                authorName = "Ann",
                bodyMd = "He was **here**",
                isDraft = false,
                createdAtMillis = 1_761_264_000_000L,
            )
        }

        it("TC-2: status reads draft ignoring case and surrounding spaces; anything else is published") {
            listOf(
                "\"draft\"" to true,
                "\" DRAFT \"" to true,
                "\"published\"" to false,
                "\"weird\"" to false,
            ).forEach { (token, expected) ->
                val row = JournalsJson.decodeFromString<JournalEntryDto>("""{"id":1,"author_id":7,"status":$token}""")
                row.toDomain().isDraft shouldBe expected
            }
            JournalsJson.decodeFromString<JournalEntryDto>("""{"id":1,"author_id":7}""").toDomain().isDraft shouldBe false
        }

        it("TC-3: a row with only id and author_id takes the defaults, and an absent or null created_at reads as null") {
            val bare = JournalsJson.decodeFromString<JournalEntryDto>("""{"id":1,"author_id":7}""").toDomain()
            bare shouldBe JournalEntry(1, 7, "", "", false, null)

            val explicitNulls = JournalsJson.decodeFromString<JournalEntryDto>(
                """{"id":1,"author_id":7,"author_name":null,"body_md":null,"status":null,"created_at":null}""",
            ).toDomain()
            explicitNulls shouldBe JournalEntry(1, 7, "", "", false, null)
        }

        it("TC-4: body_html, progress, client_id, author_has_avatar, book_uuid, updated_at, *_iso and unknown keys are ignored") {
            val json = """
                {"id":3,"author_id":7,"author_name":"Ann","body_md":"Kept","body_html":"<p>Kept</p>","progress":42,
                 "client_id":"c","author_has_avatar":true,"book_uuid":"b","updated_at":1761264001,
                 "created_at":1761264000,"created_at_iso":"2025-10-24T00:00:00Z","updated_at_iso":"2025-10-24T00:00:01Z",
                 "surprise":{"nested":[1,2]}}
            """.trimIndent()

            JournalsJson.decodeFromString<JournalEntryDto>(json).toDomain() shouldBe JournalEntry(
                id = 3,
                authorId = 7,
                authorName = "Ann",
                bodyMd = "Kept",
                isDraft = false,
                createdAtMillis = 1_761_264_000_000L,
            )
        }

        it("TC-5: id and author_id are required, so a row missing either does not decode") {
            shouldThrow<SerializationException> {
                JournalsJson.decodeFromString<JournalEntryDto>("""{"author_id":7,"body_md":"no id"}""")
            }
            shouldThrow<SerializationException> {
                JournalsJson.decodeFromString<JournalEntryDto>("""{"id":1,"body_md":"no author"}""")
            }
        }
    }
})
