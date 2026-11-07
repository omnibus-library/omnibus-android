package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import com.omnibus.omnibus.data.metadata.randomVocabularyEntry
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class SuggestionPoolTest : DescribeSpec({
    fun entry(name: String) = randomVocabularyEntry().copy(name = name)

    fun entries(vararg names: String) = names.map { entry(it) }

    describe("collecting the library's values") {
        context("when values repeat in different cases") {
            it("TC-26: merges them under the first spelling with the highest count") {
                val merged = SuggestionPool.collect(
                    listOf(entry("Fantasy").copy(count = 3), entry("fantasy").copy(count = 9), entry("FANTASY").copy(count = 1)),
                )

                merged shouldBe listOf(entry("Fantasy").copy(count = 9))
            }
        }

        it("TC-27: sorts them ignoring case") {
            SuggestionPool.collect(entries("cherry", "Banana", "apple")).map { it.name } shouldContainExactly
                listOf("apple", "Banana", "cherry")
        }
    }

    describe("filtering the suggestions") {
        it("TC-28: matches the trimmed query anywhere in a value, ignoring case") {
            val pool = entries("Urban Fantasy", "Noir", "Cozy")

            SuggestionPool.filtered(pool, emptyList(), "  FAN ") shouldBe listOf(pool[0])
        }

        context("when the query is blank") {
            it("TC-29: gives the first five values, for an empty or a whitespace query") {
                val pool = (1..8).map { entry("Tale $it") }

                checkAll(Exhaustive.of("", "   ")) { query ->
                    SuggestionPool.filtered(pool, emptyList(), query) shouldBe pool.take(5)
                }
            }
        }

        context("when a value is already a chip") {
            it("TC-30: leaves it out before taking the five") {
                val pool = entries("Fantasy", "Fantasy A", "Fantasy B", "Fantasy C", "Fantasy D", "Fantasy E")

                SuggestionPool.filtered(pool, listOf("fantasy"), "") shouldBe pool.drop(1)
            }
        }
    }

    describe("offering to add typed text as a new value") {
        context("when no value equals the text") {
            it("TC-31: offers it, for new text or text only part of a value") {
                checkAll(Exhaustive.of("Grimdark", "fan")) { typed ->
                    SuggestionPool.showsCreateRow(entries("Fantasy", "Noir"), emptyList(), typed) shouldBe true
                }
            }
        }

        context("when the text cannot be a new value") {
            it("TC-32: does not offer it, for blank text, a value past the suggestion limit or a current chip") {
                val pool = entries("Ant", "Bee", "Cat", "Dog", "Eel", "Fox")

                checkAll(Exhaustive.of("   ", "fox", " COZY ")) { typed ->
                    SuggestionPool.showsCreateRow(pool, listOf("Cozy"), typed) shouldBe false
                }
            }
        }
    }

    describe("committing typed text as a chip") {
        it("TC-33: gives the trimmed text") {
            SuggestionPool.committedChip("  Space Opera ", emptyList()) shouldBe "Space Opera"
        }

        context("when the text is at a limit") {
            it("TC-33: accepts 128 characters or a 64th value") {
                checkAll(Exhaustive.of("a".repeat(128) to emptyList(), "new" to List(63) { "v$it" })) { (typed, existing) ->
                    SuggestionPool.committedChip(typed, existing) shouldBe typed
                }
            }
        }

        context("when the text breaks a rule") {
            it("TC-34: refuses blank text, a repeat in any case, 129 characters or a 65th value") {
                val cases = Exhaustive.of(
                    "   " to emptyList(),
                    "NOIR" to listOf("Noir"),
                    "a".repeat(129) to emptyList(),
                    "new" to List(64) { "v$it" },
                )

                checkAll(cases) { (typed, existing) ->
                    SuggestionPool.committedChip(typed, existing).shouldBeNull()
                }
            }
        }
    }
})
