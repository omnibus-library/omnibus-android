package com.omnibus.omnibus.feature.bookdetail.more

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class MenuRuleSlotsTest : DescribeSpec({

    describe("placing the thick rules between menu groups") {
        it("#116 TC-9: puts a rule above each non-empty group after the first") {
            checkAll(
                Exhaustive.of(
                    emptyList<Int>() to emptyList<Int>(),
                    listOf(0, 0) to emptyList<Int>(),
                    listOf(10) to emptyList<Int>(),
                    listOf(10, 10, 10) to listOf(1, 2),
                    listOf(0, 10, 0, 10, 0) to listOf(3),
                ),
            ) { (heights, expected) ->
                menuRuleSlots(heights) shouldBe expected
            }
        }

        context("when a group joins the group above") {
            it("#117 TC-2: leaves out the rule above it") {
                checkAll(
                    Exhaustive.of(
                        Triple(listOf(10, 10, 10), listOf(false, true, false), listOf(2)),
                        Triple(listOf(10, 0, 10), listOf(false, false, true), emptyList<Int>()),
                    ),
                ) { (heights, joins, expected) ->
                    menuRuleSlots(heights, joins) shouldBe expected
                }
            }
        }
    }
})
