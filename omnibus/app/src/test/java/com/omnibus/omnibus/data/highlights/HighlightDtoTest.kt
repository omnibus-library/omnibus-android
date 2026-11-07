package com.omnibus.omnibus.data.highlights

import com.omnibus.omnibus.data.highlights.remote.HighlightDto
import com.omnibus.omnibus.data.highlights.remote.HighlightsJson
import com.omnibus.omnibus.testing.randomLong
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class HighlightDtoTest : DescribeSpec({

    fun sut(): HighlightDto = HighlightDto(id = randomLong())

    describe("reading a server row") {

        it("TC-1: maps every field, with times in milliseconds") {
            val json = """
                {"id":9,"book_uuid":"b","client_id":"c","text":"A passage","note":"A note","color":"rose",
                 "epub_cfi_range":"epubcfi(/6/4!/4/2,/1:0,/1:20)","created_at":3000000000,
                 "created_at_iso":"2065-01-24T05:20:00Z","spine_index":2,"chapter_title":"One",
                 "percent_through_book":12.5}
            """.trimIndent()

            HighlightsJson.decodeFromString<HighlightDto>(json).toDomain() shouldBe Highlight(
                id = 9,
                text = "A passage",
                note = "A note",
                color = HighlightColor.Rose,
                location = "epubcfi(/6/4!/4/2,/1:0,/1:20)",
                format = "epub",
                createdAtMillis = 3_000_000_000_000L,
                fraction = 0.125f,
            )
        }

        context("when optional fields are explicit nulls") {
            it("TC-5: takes the defaults") {
                val json = """
                    {"id":1,"text":null,"note":null,"color":null,"epub_cfi_range":null,"created_at":null,
                     "percent_through_book":null,"extra":{}}
                """.trimIndent()

                HighlightsJson.decodeFromString<HighlightDto>(json).toDomain() shouldBe
                    Highlight(1, null, null, HighlightColor.Amber, null, null, null, null)
            }
        }
    }

    describe("the highlight's colour") {

        it("TC-2: matches the token whatever its case or padding, for every colour") {
            checkAll(
                Exhaustive.of(
                    "amber" to HighlightColor.Amber,
                    "GREEN" to HighlightColor.Green,
                    "Blue" to HighlightColor.Blue,
                    " rose " to HighlightColor.Rose,
                    "VIOLET" to HighlightColor.Violet,
                ),
            ) { (token, expected) ->
                sut().copy(color = token).toDomain().color shouldBe expected
            }
        }

        context("when the token is not a known colour") {
            it("TC-2: reads as Amber") {
                checkAll(Exhaustive.of("teal", "", null)) { token ->
                    sut().copy(color = token).toDomain().color shouldBe HighlightColor.Amber
                }
            }
        }
    }

    describe("the highlight's format") {

        it("TC-3: comes from the location prefix") {
            checkAll(
                Exhaustive.of(
                    "epubcfi(/6/4)" to "epub",
                    "pdf:12" to "pdf",
                    "pdf-page:3" to "pdf",
                    "/some/path" to null,
                    "" to null,
                    null to null,
                ),
            ) { (location, expected) ->
                sut().copy(epubCfiRange = location).toDomain().format shouldBe expected
            }
        }
    }

    describe("the highlight's place in the book") {

        it("TC-4: is the percent as a fraction, clamped to the book") {
            checkAll(
                Exhaustive.of(
                    0.0 to 0f,
                    50.0 to 0.5f,
                    100.0 to 1f,
                    -5.0 to 0f,
                    150.0 to 1f,
                    Double.POSITIVE_INFINITY to 1f,
                ),
            ) { (percent, expected) ->
                sut().copy(percentThroughBook = percent).toDomain().fraction shouldBe expected
            }
        }

        context("when the percent is not a number") {
            it("TC-4: is unknown") {
                sut().copy(percentThroughBook = Double.NaN).toDomain().fraction shouldBe null
            }
        }
    }
})
