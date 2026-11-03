package com.omnibus.omnibus.feature.bookdetail.journal

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class JournalPagingTest : DescribeSpec({

    describe("the paging step") {

        it("TC-45: each step adds one page, clamped to the total, and a 1,000-entry journal reaches its end in 49 steps") {
            listOf(
                Triple(20, 1000, 40),
                Triple(990, 1000, 1000),
                Triple(20, 25, 25),
                Triple(1000, 1000, 1000),
                Triple(1200, 1000, 1000),
                Triple(0, 0, 0),
                Triple(-5, 10, 10),
            ).forEach { (shown, total, expected) ->
                nextJournalShownCount(shown, total) shouldBe expected
            }
            nextJournalShownCount(20, 100, page = 7) shouldBe 27
            nextJournalShownCount(20, 100, page = 0) shouldBe 21
            JOURNAL_PAGE shouldBe 20

            var shown = JOURNAL_PAGE
            var steps = 0
            while (shown < 1000) {
                val next = nextJournalShownCount(shown, 1000)
                check(next > shown && next <= 1000) { "Not increasing toward the total: $shown to $next" }
                shown = next
                steps++
            }
            shown shouldBe 1000
            steps shouldBe 49
        }
    }
})
