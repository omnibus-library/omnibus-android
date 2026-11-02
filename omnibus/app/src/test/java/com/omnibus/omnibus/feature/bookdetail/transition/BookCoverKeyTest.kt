package com.omnibus.omnibus.feature.bookdetail.transition

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class BookCoverKeyTest : DescribeSpec({

    describe("BookCoverKey") {

        it("issue122_tc1_keysAreEqualOnlyForSameBookAndOrigin") {
            BookCoverKey("uuid-1", BookCoverOrigin.Grid) shouldBe BookCoverKey("uuid-1", BookCoverOrigin.Grid)
            BookCoverKey("uuid-1", BookCoverOrigin.Grid) shouldBe BookCoverKey("uuid-1", BookCoverOrigin.Grid)

            // The same book from the carousel and the grid is two keys, so both can share a scene.
            (BookCoverKey("uuid-1", BookCoverOrigin.Grid) == BookCoverKey("uuid-1", BookCoverOrigin.Continue)) shouldBe false
            (BookCoverKey("uuid-1", BookCoverOrigin.Grid) == BookCoverKey("uuid-2", BookCoverOrigin.Grid)) shouldBe false

            val keys = setOf(
                BookCoverKey("uuid-1", BookCoverOrigin.Grid),
                BookCoverKey("uuid-1", BookCoverOrigin.Continue),
                BookCoverKey("uuid-2", BookCoverOrigin.Grid),
            )
            keys.size shouldBe 3
        }
    }
})
