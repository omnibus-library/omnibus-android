package com.omnibus.omnibus.feature.bookdetail.more

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class MenuRuleSlotsTest : DescribeSpec({

    describe("menuRuleSlots") {
        val cases = listOf(
            listOf<Int>() to listOf<Int>(),
            listOf(0) to listOf<Int>(),
            listOf(0, 0) to listOf<Int>(),
            listOf(10) to listOf<Int>(),
            listOf(10, 10) to listOf(1),
            listOf(10, 0, 10) to listOf(2),
            listOf(0, 10) to listOf<Int>(),
            listOf(0, 10, 10) to listOf(2),
            listOf(10, 10, 10) to listOf(1, 2),
            listOf(10, 0, 0) to listOf<Int>(),
            listOf(10, 10, 0) to listOf(1),
            listOf(0, 10, 0, 10, 0) to listOf(3),
        )
        for ((heights, expected) in cases) {
            it("#116 TC-9: $heights gives rules above $expected") {
                menuRuleSlots(heights) shouldBe expected
            }
        }
    }
})
