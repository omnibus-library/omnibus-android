package com.omnibus.omnibus.data.journals

import com.omnibus.omnibus.data.journals.remote.JournalEntryDto
import com.omnibus.omnibus.data.journals.remote.JournalsJson
import com.omnibus.omnibus.testing.randomLong
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class JournalEntryDtoTest : DescribeSpec({

    fun sut(): JournalEntryDto = JournalEntryDto(id = randomLong(), authorId = randomLong())

    describe("reading a server row") {

        it("TC-1: maps every field, with the time in milliseconds") {
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

        context("when optional fields are absent") {
            it("TC-3: takes the defaults") {
                checkAll(
                    Exhaustive.of(
                        """{"id":1,"author_id":7}""",
                        """{"id":1,"author_id":7,"author_name":null,"body_md":null,"status":null,"created_at":null}""",
                    ),
                ) { json ->
                    JournalsJson.decodeFromString<JournalEntryDto>(json).toDomain() shouldBe
                        JournalEntry(1, 7, "", "", false, null)
                }
            }
        }

        context("when the row has fields the app does not read") {
            it("TC-4: ignores them") {
                val json = """{"id":3,"author_id":7,"body_html":"<p>Kept</p>","surprise":{"nested":[1,2]}}"""

                JournalsJson.decodeFromString<JournalEntryDto>(json).toDomain() shouldBe
                    JournalEntry(3, 7, "", "", false, null)
            }
        }
    }

    describe("telling a draft from a published entry") {

        it("TC-2: reads draft whatever its case or padding") {
            checkAll(Exhaustive.of("draft", " DRAFT ")) { status ->
                sut().copy(status = status).toDomain().isDraft shouldBe true
            }
        }

        context("when the status is anything else") {
            it("TC-2: reads as published") {
                checkAll(Exhaustive.of("published", "weird", null)) { status ->
                    sut().copy(status = status).toDomain().isDraft shouldBe false
                }
            }
        }
    }
})
