package com.omnibus.omnibus.data.shelves

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ShelfFilterTest : DescribeSpec({

    describe("encoding the filter clauses") {

        it("#232 TC-6: writes each field and mode as its wire token") {
            checkAll(Exhaustive.enum<FilterField>(), Exhaustive.enum<FilterMode>()) { field, mode ->
                val encoded = filterQueryValue(listOf(FilterClause(field, mode, listOf("value"))))!!

                val clause = Json.parseToJsonElement(encoded).jsonArray.single().jsonObject
                clause["field"]?.jsonPrimitive?.content shouldBe field.wire
                clause["mode"]?.jsonPrimitive?.content shouldBe mode.wire
            }
        }

        it("#232 TC-6: writes the clauses in the server's compact shape") {
            filterQueryValue(listOf(FilterClause(FilterField.Genre, FilterMode.Include, listOf("Fantasy")))) shouldBe
                """[{"field":"genre","mode":"include","values":["Fantasy"]}]"""
        }

        it("#232 TC-6: lower-cases format values") {
            val encoded = filterQueryValue(listOf(FilterClause(FilterField.Format, FilterMode.Include, listOf("EPUB", " Pdf "))))!!

            val values = Json.parseToJsonElement(encoded).jsonArray.single().jsonObject["values"]!!.jsonArray
            values.map { it.jsonPrimitive.content } shouldBe listOf("epub", "pdf")
        }

        it("#232 TC-6: trims values and drops blank ones") {
            val encoded = filterQueryValue(listOf(FilterClause(FilterField.Tag, FilterMode.Include, listOf("  Fantasy ", "", "   "))))!!

            val values = Json.parseToJsonElement(encoded).jsonArray.single().jsonObject["values"]!!.jsonArray
            values.map { it.jsonPrimitive.content } shouldBe listOf("Fantasy")
        }

        it("#232 TC-6: omits a clause left with no values") {
            val encoded = filterQueryValue(
                listOf(
                    FilterClause(FilterField.Tag, FilterMode.Include, listOf(" ")),
                    FilterClause(FilterField.Genre, FilterMode.Exclude, listOf("Horror")),
                ),
            )!!

            val clauses = Json.parseToJsonElement(encoded).jsonArray
            clauses.map { it.jsonObject["field"]!!.jsonPrimitive.content } shouldBe listOf("genre")
        }

        it("#232 TC-6: gives null when no clauses are given") {
            filterQueryValue(emptyList()) shouldBe null
        }

        it("#232 TC-6: gives null when every clause is left with no values") {
            filterQueryValue(listOf(FilterClause(FilterField.Tag, FilterMode.Include, listOf("  ")))) shouldBe null
        }
    }
})
