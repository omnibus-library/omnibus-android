package com.omnibus.omnibus.data.highlights

import com.omnibus.omnibus.data.highlights.remote.HighlightDto
import com.omnibus.omnibus.data.highlights.remote.HighlightsJson
import com.omnibus.omnibus.data.highlights.remote.fractionOf
import com.omnibus.omnibus.data.highlights.remote.formatOf
import com.omnibus.omnibus.data.highlights.remote.highlightColorFromToken
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class HighlightDtoTest : DescribeSpec({

    describe("the wire mapping") {

        it("TC-1: a full server row maps every field, with millis and no overflow") {
            val json = """
                {"id":9,"book_uuid":"b","client_id":"c","text":"A passage","note":"A note","color":"rose",
                 "epub_cfi_range":"epubcfi(/6/4!/4/2,/1:0,/1:20)","created_at":3000000000,
                 "created_at_iso":"2065-01-24T05:20:00Z","spine_index":2,"chapter_title":"One",
                 "percent_through_book":12.5}
            """.trimIndent()

            val dto = HighlightsJson.decodeFromString<List<HighlightDto>>("[$json]").single()

            dto.toDomain() shouldBe Highlight(
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

        it("TC-2: colour tokens match ignoring case and spaces; unknown or missing reads as Amber") {
            listOf(
                "amber" to HighlightColor.Amber,
                "GREEN" to HighlightColor.Green,
                "Blue" to HighlightColor.Blue,
                " rose " to HighlightColor.Rose,
                "VIOLET" to HighlightColor.Violet,
                "teal" to HighlightColor.Amber,
                "" to HighlightColor.Amber,
                null to HighlightColor.Amber,
            ).forEach { (token, expected) ->
                highlightColorFromToken(token) shouldBe expected
            }
        }

        it("TC-3: the format comes from the location prefix") {
            listOf(
                "epubcfi(/6/4)" to "epub",
                "pdf:12" to "pdf",
                "pdf-page:3" to "pdf",
                "/some/path" to null,
                "" to null,
                null to null,
            ).forEach { (location, expected) ->
                formatOf(location) shouldBe expected
            }
        }

        it("TC-4: the percent maps to a clamped fraction; NaN and missing give null; the domain never holds NaN") {
            fractionOf(0.0) shouldBe 0f
            fractionOf(50.0) shouldBe 0.5f
            fractionOf(100.0) shouldBe 1f
            fractionOf(-5.0) shouldBe 0f
            fractionOf(150.0) shouldBe 1f
            fractionOf(Double.POSITIVE_INFINITY) shouldBe 1f
            fractionOf(Double.NaN) shouldBe null
            fractionOf(null) shouldBe null

            val dto = HighlightsJson.decodeFromString<List<HighlightDto>>("""[{"id":1}]""").single()
            dto.toDomain().createdAtMillis shouldBe null
            dto.toDomain().fraction shouldBe null
            (dto.toDomain().fraction?.isNaN() == true) shouldBe false
        }

        it("TC-5: unknown keys are ignored and explicit nulls take defaults; blank text passes through") {
            val json = """
                [
                  {"id":1,"book_uuid":"b","client_id":null,"created_at_iso":"x","spine_index":4,
                   "chapter_title":"T","extra":{},"text":null,"note":null,"color":null,
                   "epub_cfi_range":null,"created_at":null,"percent_through_book":null},
                  {"id":2,"text":"   ","color":"green"}
                ]
            """.trimIndent()

            val rows = HighlightsJson.decodeFromString<List<HighlightDto>>(json).map { it.toDomain() }

            rows[0] shouldBe Highlight(1, null, null, HighlightColor.Amber, null, null, null, null)
            rows[1].text shouldBe "   "
            rows[1].color shouldBe HighlightColor.Green
        }

        it("TC-6: the C16 shape: colours, state objects, the seven-argument highlight and the defaulted fraction") {
            HighlightColor.entries shouldBe listOf(
                HighlightColor.Amber,
                HighlightColor.Green,
                HighlightColor.Blue,
                HighlightColor.Rose,
                HighlightColor.Violet,
            )
            HighlightsState.Loading shouldBe HighlightsState.Loading
            HighlightsState.Failed shouldBe HighlightsState.Failed
            HighlightsState.Loaded(emptyList()) shouldBe HighlightsState.Loaded(emptyList())

            val positional = Highlight(1L, "t", "n", HighlightColor.Blue, "epubcfi(/6/4)", "epub", 5L)
            val named = Highlight(
                id = 1L,
                text = "t",
                note = "n",
                color = HighlightColor.Blue,
                location = "epubcfi(/6/4)",
                format = "epub",
                createdAtMillis = 5L,
            )
            positional shouldBe named
            positional.fraction shouldBe null

            val placed = positional.copy(fraction = 0.3f)
            placed.fraction shouldBe 0.3f
            placed.copy(fraction = null) shouldBe positional
            placed.text shouldBe "t"
            placed.color shouldBe HighlightColor.Blue
        }
    }
})
