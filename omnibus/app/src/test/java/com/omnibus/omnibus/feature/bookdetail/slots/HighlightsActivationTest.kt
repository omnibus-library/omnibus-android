package com.omnibus.omnibus.feature.bookdetail.slots

import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.highlights.HighlightsSection
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldBeInstanceOf

class HighlightsActivationTest : DescribeSpec({

    describe("highlights slot") {

        // Asserts membership and position only, never the whole list, so the next ticket's line can land beside it.
        it("TC-35: the highlights slot registers the highlights section first") {
            highlightsSections.first() shouldBeSameInstanceAs HighlightsSection
            BookDetailSlots.Registered.highlightsSections shouldContain HighlightsSection
            HighlightsSection.shouldBeInstanceOf<BookDetailSection>()
        }
    }
})
