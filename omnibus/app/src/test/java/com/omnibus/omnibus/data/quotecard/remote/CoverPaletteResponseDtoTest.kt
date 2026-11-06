package com.omnibus.omnibus.data.quotecard.remote

import com.omnibus.omnibus.data.quotecard.CoverPair
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/** The pairs [json] decodes to. */
private fun pairsOf(json: String): List<CoverPair> =
    QuoteCardJson.decodeFromString<CoverPaletteResponseDto>(json).toDomain()

private fun pair(from: String, to: String, ink: String) = """{"from":"$from","to":"$to","ink":"$ink"}"""

class CoverPaletteResponseDtoTest : DescribeSpec({

    describe("CoverPaletteResponseDto.toDomain") {

        it("maps the pairs in server order, light ink and dark ink") {
            val json = """{"pairs":[
                ${pair("#5d1f1c", "#c01a1a", "light")},
                ${pair("#1e1b1b", "#ab2527", "light")},
                ${pair("#efe4e6", "#d5c2c9", "dark")}
            ]}"""

            pairsOf(json) shouldBe listOf(
                CoverPair(0xFF5D1F1C.toInt(), 0xFFC01A1A.toInt(), lightInk = true),
                CoverPair(0xFF1E1B1B.toInt(), 0xFFAB2527.toInt(), lightInk = true),
                CoverPair(0xFFEFE4E6.toInt(), 0xFFD5C2C9.toInt(), lightInk = false),
            )
        }

        it("reads upper-case hex digits") {
            pairsOf("""{"pairs":[${pair("#5D1F1C", "#C01A1A", "dark")}]}""") shouldBe
                listOf(CoverPair(0xFF5D1F1C.toInt(), 0xFFC01A1A.toInt(), lightInk = false))
        }

        listOf(
            "a shorthand colour" to pair("#5d1", "#c01a1a", "light"),
            "a colour with no hash" to pair("5d1f1c", "#c01a1a", "light"),
            "a colour with an alpha pair" to pair("#5d1f1cff", "#c01a1a", "light"),
            "a colour that isn't hex" to pair("#5d1f1c", "#gggggg", "light"),
            "an empty colour" to pair("", "#c01a1a", "light"),
            "an ink that is neither light nor dark" to pair("#5d1f1c", "#c01a1a", "grey"),
            "an empty ink" to pair("#5d1f1c", "#c01a1a", ""),
        ).forEach { (why, malformed) ->
            it("drops a pair with $why and keeps the rest") {
                val kept = pair("#112233", "#445566", "light")

                pairsOf("""{"pairs":[$malformed,$kept]}""") shouldBe
                    listOf(CoverPair(0xFF112233.toInt(), 0xFF445566.toInt(), lightInk = true))
            }
        }

        it("drops a pair with a field missing") {
            pairsOf("""{"pairs":[{"from":"#112233","ink":"light"}]}""") shouldBe emptyList()
        }

        it("reads a body with no pairs as none, and ignores unknown keys") {
            pairsOf("""{}""") shouldBe emptyList()
            pairsOf("""{"pairs":[],"generated":"today"}""") shouldBe emptyList()
            pairsOf("""{"pairs":[{"from":"#112233","to":"#445566","ink":"dark","name":"Wing"}],"v":2}""") shouldBe
                listOf(CoverPair(0xFF112233.toInt(), 0xFF445566.toInt(), lightInk = false))
        }
    }
})
