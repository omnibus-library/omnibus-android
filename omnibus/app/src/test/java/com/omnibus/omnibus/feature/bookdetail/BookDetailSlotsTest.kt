package com.omnibus.omnibus.feature.bookdetail

import com.omnibus.omnibus.feature.bookdetail.files.details.BookDetailsSection
import com.omnibus.omnibus.feature.bookdetail.finish.FinishCardSection
import com.omnibus.omnibus.feature.bookdetail.highlights.HighlightsSection
import com.omnibus.omnibus.feature.bookdetail.journal.JournalSection
import com.omnibus.omnibus.feature.bookdetail.overview.activity.HouseholdActivitySection
import com.omnibus.omnibus.feature.bookdetail.overview.author.MoreByAuthorSection
import com.omnibus.omnibus.feature.bookdetail.overview.chips.GenresTagsSection
import com.omnibus.omnibus.feature.bookdetail.overview.description.BookDescriptionSection
import com.omnibus.omnibus.feature.bookdetail.overview.readalikes.ReadAlikesSection
import com.omnibus.omnibus.feature.bookdetail.overview.reading.ReadingCardSection
import com.omnibus.omnibus.feature.bookdetail.overview.series.SeriesFanSection
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingProgressSection
import com.omnibus.omnibus.feature.bookdetail.reviews.HouseholdReviewsSection
import com.omnibus.omnibus.feature.bookdetail.reviews.mine.MyReviewSection
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe

// Order only, never the whole list, so a new section can land beside these without a false failure.
class BookDetailSlotsTest : DescribeSpec({

    describe("the sections the book page shows") {

        it("#93 TC-39 / TC-42 / #105 TC-27: the Overview tab runs from the description to the read-alikes") {
            BookDetailSlots.Registered.overviewSections.shouldContainInOrder(
                BookDescriptionSection,
                GenresTagsSection,
                ReadingCardSection,
                HouseholdActivitySection,
                SeriesFanSection,
                MoreByAuthorSection,
                ReadAlikesSection,
            )
        }

        it("TC-52: the Reviews tab puts the reader's review ahead of the household's") {
            BookDetailSlots.Registered.reviewsSections.shouldContainInOrder(MyReviewSection, HouseholdReviewsSection)
        }

        it("TC-35 / TC-47: the Highlights tab puts the highlights ahead of the journal") {
            BookDetailSlots.Registered.highlightsSections.shouldContainInOrder(HighlightsSection, JournalSection)
        }

        it("#114: the Files tab ends with the Details list") {
            BookDetailSlots.Registered.filesSections.last() shouldBe BookDetailsSection
        }

        it("TC-78 / TC-27: the stage puts the reading progress ahead of the finish card") {
            BookDetailSlots.Registered.stageProgress.shouldContainInOrder(ReadingProgressSection, FinishCardSection)
        }
    }
})
