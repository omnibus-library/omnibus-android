package com.omnibus.omnibus.feature.reader.ui.curl

import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomInt
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SettledPageTest : DescribeSpec({

    describe("decoding the settled page") {
        it("TC-319: reads the columns, the spine and both neighbours") {
            val columns = randomInt(1..2)
            val spine = randomDouble(1.0, 2_000.0).toFloat()
            val reply = buildJsonObject {
                put("columns", columns)
                put("spine", spine)
                put("next", "page")
                put("prev", "section")
            }

            SettledPage.decode(reply) shouldBe SettledPage(columns, spine, NeighbourKind.Page, NeighbourKind.Section)
        }

        context("when the spine is null") {
            it("TC-319: has no spine and keeps the other fields") {
                val reply = Json.parseToJsonElement("""{"columns":1,"spine":null,"next":"page","prev":"page"}""")

                SettledPage.decode(reply) shouldBe SettledPage(1, null, NeighbourKind.Page, NeighbourKind.Page)
            }
        }

        context("when a neighbour kind is not known") {
            it("TC-319: reads it as None") {
                val reply = buildJsonObject {
                    put("columns", 1)
                    put("next", "chapter")
                    put("prev", "page")
                }

                SettledPage.decode(reply) shouldBe SettledPage(1, null, NeighbourKind.None, NeighbourKind.Page)
            }
        }

        context("when the reply is not an object") {
            it("TC-319: decodes to null") {
                checkAll(Exhaustive.collection<JsonElement>(listOf(JsonPrimitive("page"), JsonNull, JsonArray(emptyList())))) { reply ->
                    SettledPage.decode(reply) shouldBe null
                }
            }
        }

        context("when there is no reply") {
            it("TC-319: decodes to null") {
                SettledPage.decode(null) shouldBe null
            }
        }
    }
})
