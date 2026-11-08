package com.omnibus.omnibus.feature.library

import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ContinueCarouselLayoutTest : DescribeSpec({

    describe("the cards per page") {
        it("#217 TC-1: two cards from 712 dp at font scale 1, never more") {
            checkAll(
                Exhaustive.of(
                    0.dp to 1,
                    400.dp to 1,
                    411.dp to 1,
                    673.dp to 1,
                    711.99.dp to 1,
                    712.dp to 2,
                    800.dp to 2,
                    1280.dp to 2,
                    10_000.dp to 2,
                ),
            ) { (width, cardsPerPage) ->
                continueCardsPerPage(width, 1f) shouldBe cardsPerPage
            }
        }

        it("#217 TC-2: the breakpoint grows with the font scale") {
            checkAll(
                Exhaustive.of(
                    Triple(1.25f, 804.dp, 803.99.dp),
                    Triple(1.5f, 896.dp, 895.99.dp),
                    Triple(2f, 1080.dp, 1079.99.dp),
                ),
            ) { (fontScale, boundary, below) ->
                continueCardsPerPage(boundary, fontScale) shouldBe 2
                continueCardsPerPage(below, fontScale) shouldBe 1
            }
        }
    }

    describe("the page count") {
        it("#217 TC-4: is the cards divided by the cards per page, rounded up, and 0 for no cards") {
            checkAll(
                Exhaustive.of(
                    Triple(0, 1, 0),
                    Triple(0, 2, 0),
                    Triple(1, 2, 1),
                    Triple(2, 2, 1),
                    Triple(3, 2, 2),
                    Triple(5, 1, 5),
                    Triple(5, 2, 3),
                ),
            ) { (cardCount, cardsPerPage, pages) ->
                continuePageCount(cardCount, cardsPerPage) shouldBe pages
            }
        }
    }

    describe("grouping the cards into pages") {
        it("#217 TC-4: cards are grouped in order, the last page holding the rest") {
            val cards = listOf("a", "b", "c", "d", "e")
            checkAll(
                Exhaustive.of(
                    Triple(cards, 2, listOf(listOf("a", "b"), listOf("c", "d"), listOf("e"))),
                    Triple(cards, 1, listOf(listOf("a"), listOf("b"), listOf("c"), listOf("d"), listOf("e"))),
                    Triple(emptyList<String>(), 2, emptyList<List<String>>()),
                ),
            ) { (input, cardsPerPage, pages) ->
                continuePages(input, cardsPerPage) shouldBe pages
            }
        }

        it("#217 TC-4: has as many pages as the page count gives") {
            checkAll(
                Exhaustive.of(
                    Pair(0, 1),
                    Pair(0, 2),
                    Pair(1, 2),
                    Pair(2, 2),
                    Pair(3, 2),
                    Pair(5, 1),
                    Pair(5, 2),
                ),
            ) { (cardCount, cardsPerPage) ->
                val cards = List(cardCount) { "card$it" }
                continuePages(cards, cardsPerPage).size shouldBe continuePageCount(cardCount, cardsPerPage)
            }
        }
    }

    describe("the page that holds a card") {
        it("#217 TC-7: a card's page is its index divided by the cards per page") {
            val ids = listOf("a", "b", "c", "d", "e")
            checkAll(
                Exhaustive.of(
                    Triple("a", 2, 0),
                    Triple("b", 2, 0),
                    Triple("c", 2, 1),
                    Triple("d", 2, 1),
                    Triple("e", 2, 2),
                    Triple("a", 1, 0),
                    Triple("e", 1, 4),
                ),
            ) { (id, cardsPerPage, page) ->
                pageOfCard(id, ids, cardsPerPage) shouldBe page
            }
        }

        it("#217 TC-7: a card that is null, absent or in an empty list is on no page") {
            val ids = listOf("a", "b", "c", "d", "e")
            checkAll(Exhaustive.of("z", null)) { id ->
                pageOfCard(id, ids, 2) shouldBe -1
            }
            pageOfCard("a", emptyList(), 2) shouldBe -1
        }
    }
})
