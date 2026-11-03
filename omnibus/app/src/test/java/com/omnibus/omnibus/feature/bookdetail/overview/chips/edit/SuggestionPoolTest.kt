package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import com.omnibus.omnibus.data.metadata.VocabularyEntry
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class SuggestionPoolTest : DescribeSpec({

    fun entry(name: String, count: Long = 1) = VocabularyEntry(name, count)

    describe("collect") {

        it("TC-26: dedups ignoring case, first spelling wins, higher count kept") {
            SuggestionPool.collect(listOf(entry("Fantasy", 3), entry("fantasy", 9), entry("FANTASY", 1))) shouldBe
                listOf(entry("Fantasy", 9))
        }

        it("TC-27: sorts by lowercased name") {
            SuggestionPool.collect(listOf(entry("cherry"), entry("Banana"), entry("apple"))).map { it.name } shouldContainExactly
                listOf("apple", "Banana", "cherry")
        }
    }

    describe("filtered") {

        val pool = listOf(entry("Urban Fantasy"), entry("Noir"), entry("Cozy"))

        it("TC-28: matches the trimmed query as a case-insensitive substring") {
            SuggestionPool.filtered(pool, emptyList(), "  FAN ") shouldBe listOf(entry("Urban Fantasy"))
            SuggestionPool.filtered(pool, emptyList(), "zzz") shouldBe emptyList()
        }

        it("TC-29: returns at most 5, and the head of the pool for an empty query") {
            val eight = (1..8).map { entry("Tale $it") }
            SuggestionPool.filtered(eight, emptyList(), "tale") shouldBe eight.take(5)
            SuggestionPool.filtered(eight, emptyList(), "") shouldBe eight.take(5)
            SuggestionPool.filtered(eight, emptyList(), "   ") shouldBe eight.take(5)
        }

        it("TC-30: leaves out current chips ignoring case, before the 5-limit") {
            val withMatch = listOf(entry("Fantasy"), entry("Fantasy A"), entry("Fantasy B"), entry("Fantasy C"), entry("Fantasy D"), entry("Fantasy E"))
            SuggestionPool.filtered(withMatch, listOf("fantasy"), "") shouldBe withMatch.drop(1)
        }
    }

    describe("showsCreateRow") {

        val pool = listOf(entry("Fantasy"), entry("Noir"))

        it("TC-31: true for text no value equals, including a substring-only match") {
            SuggestionPool.showsCreateRow(pool, emptyList(), "Grimdark") shouldBe true
            SuggestionPool.showsCreateRow(pool, emptyList(), "fan") shouldBe true
        }

        it("TC-32: false for blank text, an existing pool value, a current chip, or a value the 5-limit cut off") {
            val sixValues = listOf("Ant", "Bee", "Cat", "Dog", "Eel", "Fox")
            val sixPool = sixValues.map { entry(it) }
            SuggestionPool.filtered(sixPool, emptyList(), "").map { it.name } shouldBe sixValues.take(5)

            SuggestionPool.showsCreateRow(pool, emptyList(), "   ") shouldBe false
            SuggestionPool.showsCreateRow(pool, emptyList(), "fantasy") shouldBe false
            SuggestionPool.showsCreateRow(pool, listOf("Cozy"), "cozy") shouldBe false
            SuggestionPool.showsCreateRow(pool, listOf("Cozy"), "Grimdark") shouldBe true
            SuggestionPool.showsCreateRow(pool, listOf("Cozy"), " COZY ") shouldBe false
            SuggestionPool.showsCreateRow(sixPool, emptyList(), "fox") shouldBe false
        }
    }

    describe("committedChip") {

        it("TC-33: returns the trimmed text, accepts exactly 128 characters, and accepts a 64th value up to 63 existing") {
            SuggestionPool.committedChip("  Space Opera ", emptyList()) shouldBe "Space Opera"
            SuggestionPool.committedChip("a".repeat(128), emptyList()) shouldBe "a".repeat(128)
            SuggestionPool.committedChip("new", List(63) { "v$it" }) shouldBe "new"
        }

        it("TC-34: refuses blank text and case-insensitive duplicates of a current chip") {
            SuggestionPool.committedChip("", emptyList()) shouldBe null
            SuggestionPool.committedChip("   ", emptyList()) shouldBe null
            SuggestionPool.committedChip("noir", listOf("Noir")) shouldBe null
            SuggestionPool.committedChip("NOIR", listOf("Noir")) shouldBe null
        }

        it("TC-35: refuses past 64 values or 128 characters") {
            SuggestionPool.committedChip("new", List(64) { "v$it" }) shouldBe null
            SuggestionPool.committedChip("a".repeat(129), emptyList()) shouldBe null
        }
    }
})
