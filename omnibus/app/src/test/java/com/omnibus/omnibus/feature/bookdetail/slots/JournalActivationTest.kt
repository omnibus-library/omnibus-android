package com.omnibus.omnibus.feature.bookdetail.slots

import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.highlights.HighlightsSection
import com.omnibus.omnibus.feature.bookdetail.journal.JournalSection
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.types.shouldBeInstanceOf

class JournalActivationTest : DescribeSpec({

    describe("journal slot") {

        // Asserts membership and position only, never the whole list, so the next ticket's line can land beside it.
        it("TC-47: the journal section registers after the highlights section") {
            highlightsSections shouldContain JournalSection
            highlightsSections.indexOf(JournalSection) shouldBeGreaterThan highlightsSections.indexOf(HighlightsSection)
            BookDetailSlots.Registered.highlightsSections shouldContain JournalSection
            JournalSection.shouldBeInstanceOf<BookDetailSection>()
        }
    }
})
