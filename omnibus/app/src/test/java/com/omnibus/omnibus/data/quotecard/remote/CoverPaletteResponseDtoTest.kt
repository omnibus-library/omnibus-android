package com.omnibus.omnibus.data.quotecard.remote

import com.omnibus.omnibus.data.quotecard.CoverPair
import com.omnibus.omnibus.data.quotecard.json
import com.omnibus.omnibus.data.quotecard.paletteJson
import com.omnibus.omnibus.data.quotecard.randomCoverPair
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/** The pairs [json] decodes to. */
private fun pairsOf(json: String): List<CoverPair> = CoverPaletteResponseDto.fromJsonString(json).toDomain()

private fun entry(from: String, to: String, ink: String) = """{"from":"$from","to":"$to","ink":"$ink"}"""

class CoverPaletteResponseDtoTest : DescribeSpec({

    describe("reading a palette") {
        it("returns the pairs in the server's order") {
            val pairs = List(3) { randomCoverPair() }

            pairsOf(paletteJson(pairs)) shouldBe pairs
        }

        it("reads light ink as light ink") {
            val pair = randomCoverPair().copy(lightInk = true)

            pairsOf(paletteJson(listOf(pair))).single().lightInk shouldBe true
        }

        it("reads dark ink as dark ink") {
            val pair = randomCoverPair().copy(lightInk = false)

            pairsOf(paletteJson(listOf(pair))).single().lightInk shouldBe false
        }

        context("when the hex digits are upper case") {
            it("reads the colours the same") {
                pairsOf("""{"pairs":[${entry("#5D1F1C", "#C01A1A", "light")}]}""") shouldBe
                    listOf(CoverPair(0xFF5D1F1C.toInt(), 0xFFC01A1A.toInt(), lightInk = true))
            }
        }

        listOf(
            "a shorthand colour" to entry("#5d1", "#c01a1a", "light"),
            "a colour with no hash" to entry("5d1f1c", "#c01a1a", "light"),
            "a colour with an alpha pair" to entry("#5d1f1cff", "#c01a1a", "light"),
            "a colour that isn't hex" to entry("#5d1f1c", "#gggggg", "light"),
            "an empty colour" to entry("", "#c01a1a", "light"),
            "an ink that is neither light nor dark" to entry("#5d1f1c", "#c01a1a", "grey"),
            "an empty ink" to entry("#5d1f1c", "#c01a1a", ""),
            "a field of the wrong JSON type" to """{"from":5,"to":"#445566","ink":"light"}""",
            "a field missing" to """{"from":"#112233","ink":"light"}""",
            "a null entry" to "null",
            "an entry that isn't an object" to "\"nope\"",
        ).forEach { (malformed, entryJson) ->
            context("when a pair has $malformed") {
                it("keeps only the valid pair beside it") {
                    val valid = randomCoverPair()

                    pairsOf("""{"pairs":[$entryJson,${valid.json()}]}""") shouldBe listOf(valid)
                }
            }
        }

        context("when the body has no pairs key") {
            it("returns none") {
                pairsOf("{}").shouldBeEmpty()
            }
        }

        context("when the pairs are empty") {
            it("returns none") {
                pairsOf("""{"pairs":[]}""").shouldBeEmpty()
            }
        }

        context("when the body has keys the app doesn't know") {
            it("reads the pairs regardless") {
                val pair = randomCoverPair()

                pairsOf("""{"pairs":[${pair.json()}],"v":2}""") shouldBe listOf(pair)
            }
        }
    }
})
