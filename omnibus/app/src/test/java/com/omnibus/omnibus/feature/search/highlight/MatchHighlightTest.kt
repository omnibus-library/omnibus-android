package com.omnibus.omnibus.feature.search.highlight

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

private val Highlight = SpanStyle(fontWeight = FontWeight.Bold)

/** The ranges of [text] that the highlight styled, in order. */
private fun highlighted(query: String, text: String): List<IntRange> =
    highlightMatches(query, text, Highlight).spanStyles.map { it.start until it.end }

class MatchHighlightTest : DescribeSpec({

    describe("highlighting matches") {

        it("#351 TC-20: highlights every word of the query, ignoring case") {
            val text = "Lavondyss: Journey into Mythago Wood"

            highlighted("wood myth", text) shouldBe listOf(24 until 28, 32 until 36)
        }

        it("#351 TC-20: highlights every match of a word, in any letter case") {
            val text = "the wood and the Woodcutter"

            highlighted("WOOD", text) shouldBe listOf(4 until 8, 17 until 21)
        }

        it("#351 TC-20: merges overlapping matches into one span") {
            highlighted("wood woo", "Mythago Wood") shouldBe listOf(8 until 12)
        }

        it("#351 TC-20: keeps the text as it is") {
            highlightMatches("wood", "Mythago Wood", Highlight).text shouldBe "Mythago Wood"
        }

        it("#351 TC-20: highlights nothing for a blank query") {
            highlighted("   ", "Mythago Wood") shouldBe emptyList()
        }

        it("#351 TC-20: highlights nothing when no word matches") {
            highlighted("zzz", "Mythago Wood") shouldBe emptyList()
        }

        it("#351 TC-20: finds a match after a character whose lower case is longer") {
            highlighted("wood", "İzmir Wood") shouldBe listOf(6 until 10)
        }
    }
})
