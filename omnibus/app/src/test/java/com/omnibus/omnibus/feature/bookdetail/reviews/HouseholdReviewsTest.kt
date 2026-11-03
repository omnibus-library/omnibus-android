package com.omnibus.omnibus.feature.bookdetail.reviews

import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusRecord
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.reviews.BookReviews
import com.omnibus.omnibus.data.reviews.Review
import com.omnibus.omnibus.data.reviews.ReviewSummary
import com.omnibus.omnibus.data.reviews.ReviewsState
import com.omnibus.omnibus.feature.bookdetail.BookDetailAnchor
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.BookDetailTabController
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.Locale

/** A reader's review with the fields the rules read. */
private fun review(
    userId: Long,
    name: String = "Reader $userId",
    hasAvatar: Boolean = false,
    rating: Float? = null,
    text: String? = null,
    spoiler: Boolean = false,
    updatedAtMillis: Long? = null,
) = Review(
    userId = userId,
    displayName = name,
    hasAvatar = hasAvatar,
    rating = rating,
    text = text,
    privateNotes = null,
    isSpoiler = spoiler,
    updatedAtMillis = updatedAtMillis,
)

/** The signed-in reader's own review, which the server does not attribute. */
private fun mine(rating: Float? = null, text: String? = null) = review(userId = 0L, name = "", rating = rating, text = text)

private val EMPTY_STATE = ReviewsState.Loaded(BookReviews(mine = null, others = emptyList(), summary = null))

class HouseholdReviewsTest : DescribeSpec({

    describe("householdAverage") {

        it("TC-16: the server summary wins") {
            val reviews = BookReviews(
                mine = mine(rating = 2f),
                others = listOf(review(1, rating = 1f)),
                summary = ReviewSummary(4.3f, 3),
            )

            householdAverage(reviews) shouldBe HouseholdAverage(4.3f, 3)
        }

        it("TC-17: without a summary the mean of every rating, yours included, or null") {
            householdAverage(
                BookReviews(mine = mine(rating = 3f), others = listOf(review(1, rating = 4f), review(2, rating = 5f), review(3)), summary = null),
            ) shouldBe HouseholdAverage(4.0f, 3)
            householdAverage(BookReviews(mine = mine(rating = 3.5f), others = emptyList(), summary = null)) shouldBe
                HouseholdAverage(3.5f, 1)
            householdAverage(BookReviews(mine = null, others = listOf(review(1, rating = 4f)), summary = null)) shouldBe
                HouseholdAverage(4.0f, 1)
            householdAverage(
                BookReviews(mine = mine(text = "Lovely"), others = listOf(review(1, text = "Fine")), summary = null),
            ) shouldBe null
            householdAverage(BookReviews(mine = null, others = emptyList(), summary = null)) shouldBe null
        }
    }

    describe("halfStarRound and starNumber") {

        it("TC-18: halfStarRound snaps to the nearest half with ties up, clamped, NaN to zero") {
            val values = listOf(4.3f, 4.24f, 4.25f, 0.2f, 5.4f, 4.75f, 2.75f, 0.25f, 0f, -1f, 7f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
            val expected = listOf(4.5f, 4.0f, 4.5f, 0.0f, 5.0f, 5.0f, 3.0f, 0.5f, 0f, 0f, 5f, 0f, 5f, 0f)

            values.zip(expected).forEach { (value, rounded) -> halfStarRound(value) shouldBe rounded }
        }

        it("TC-19: starNumber rounds half up to one decimal, drops .0 and uses the locale separator") {
            starNumber(4.33f, Locale.US) shouldBe "4.3"
            starNumber(4.0f, Locale.US) shouldBe "4"
            starNumber(4.5f, Locale.US) shouldBe "4.5"
            starNumber(4.35f, Locale.US) shouldBe "4.4"
            starNumber(0f, Locale.US) shouldBe "0"
            starNumber(5f, Locale.US) shouldBe "5"
            starNumber(4.5f, Locale.GERMANY) shouldBe "4,5"
            starNumber(4.0f, Locale.GERMANY) shouldBe "4"
            starNumber(4.33f, Locale.FRANCE) shouldBe "4,3"
        }
    }

    describe("reviewEntries") {

        it("TC-20: entries keep the server order and never include mine") {
            val reviews = BookReviews(
                mine = mine(rating = 4f, text = "Mine"),
                others = listOf(review(9, "Zed"), review(2, "Amy"), review(5, "Bo")),
                summary = null,
            )

            val entries = reviewEntries(reviews, finished = false, revealed = emptySet())

            entries.map { it.userId } shouldBe listOf(9L, 2L, 5L)
            entries.size shouldBe 3
            entries.any { it.userId == 0L } shouldBe false
        }

        it("TC-21: entry fields and the body kind") {
            val reviews = BookReviews(
                mine = null,
                others = listOf(
                    review(1, "Maren", hasAvatar = true, rating = 4f, text = "Great", updatedAtMillis = 123L),
                    review(2, "Theo", rating = 3.5f),
                    review(3, "Jo"),
                    review(4, "Ada", rating = 5f, text = "![x](u)"),
                ),
                summary = null,
            )

            val entries = reviewEntries(reviews, finished = false, revealed = emptySet())

            val first = entries[0]
            first.userId shouldBe 1L
            first.name shouldBe "Maren"
            first.hasAvatar shouldBe true
            first.dateMillis shouldBe 123L
            first.rating shouldBe 4f
            first.markdown shouldBe "Great"
            first.body shouldBe ReviewBody.Text
            entries[1].body shouldBe ReviewBody.RatedOnly
            entries[2].body shouldBe ReviewBody.None
            entries[3].body shouldBe ReviewBody.ImageOnly
        }

        it("TC-22: spoilers are veiled until finished or revealed") {
            val spoilers = listOf(
                review(9, text = "Butler", spoiler = true),
                review(8, text = "Twist", spoiler = true),
            )
            val reviews = BookReviews(mine = null, others = spoilers, summary = null)

            reviewEntries(reviews, finished = false, revealed = emptySet()).map { it.veiled } shouldBe listOf(true, true)
            reviewEntries(reviews, finished = true, revealed = emptySet()).map { it.veiled } shouldBe listOf(false, false)
            reviewEntries(reviews, finished = false, revealed = setOf(9L)).map { it.veiled } shouldBe listOf(false, true)

            val plain = BookReviews(mine = null, others = listOf(review(1, text = "Fine")), summary = null)
            reviewEntries(plain, finished = false, revealed = emptySet()).single().veiled shouldBe false
            val noText = BookReviews(mine = null, others = listOf(review(1, rating = 4f, spoiler = true)), summary = null)
            reviewEntries(noText, finished = false, revealed = emptySet()).single().veiled shouldBe false
        }
    }

    describe("isFinished") {

        it("TC-23: only a Known Finished status counts as finished") {
            isFinished(ReadStatusState.Known(ReadStatusRecord(ReadStatus.Finished, 1L, 1L))) shouldBe true
            isFinished(ReadStatusState.Known(ReadStatusRecord(ReadStatus.Unread, null, null))) shouldBe false
            isFinished(ReadStatusState.Known(ReadStatusRecord(ReadStatus.Reading, 1L, null))) shouldBe false
            isFinished(ReadStatusState.Unknown) shouldBe false
            isFinished(ReadStatusState.Failed) shouldBe false
        }
    }

    describe("reviewsTabModel") {

        it("TC-24: non-loaded states map to Hidden, Offline and Failed") {
            reviewsTabModel(ReviewsState.Loading, finished = false, revealed = emptySet()) shouldBe ReviewsTabModel.Hidden
            reviewsTabModel(ReviewsState.Offline, finished = false, revealed = emptySet()) shouldBe ReviewsTabModel.Offline
            reviewsTabModel(ReviewsState.Failed, finished = false, revealed = emptySet()) shouldBe ReviewsTabModel.Failed
        }

        it("TC-25: nobody reviewed is Empty, only your own review is Hidden") {
            val nobody = listOf(
                BookReviews(mine = null, others = emptyList(), summary = null),
                BookReviews(mine = mine(), others = emptyList(), summary = null),
            )
            nobody.map { reviewsTabModel(ReviewsState.Loaded(it), finished = false, revealed = emptySet()) } shouldBe
                listOf(ReviewsTabModel.Empty, ReviewsTabModel.Empty)

            val yours = listOf(
                BookReviews(mine = mine(rating = 3f), others = emptyList(), summary = null),
                BookReviews(mine = mine(text = "Mine"), others = emptyList(), summary = null),
            )
            yours.map { reviewsTabModel(ReviewsState.Loaded(it), finished = false, revealed = emptySet()) } shouldBe
                listOf(ReviewsTabModel.Hidden, ReviewsTabModel.Hidden)
        }

        it("TC-26: a populated tab is Listed with count, average and finished") {
            val rated = BookReviews(
                mine = null,
                others = listOf(review(1, rating = 4f), review(2, rating = 4.5f), review(3, rating = 4f)),
                summary = ReviewSummary(4.33f, 3),
            )

            val listed = reviewsTabModel(ReviewsState.Loaded(rated), finished = true, revealed = emptySet())
                .shouldBeInstanceOf<ReviewsTabModel.Listed>()
            listed.count shouldBe 3
            listed.average shouldBe 4.33f
            listed.entries.size shouldBe 3
            listed.finished shouldBe true

            val textOnly = BookReviews(mine = null, others = listOf(review(1, text = "Fine")), summary = null)
            reviewsTabModel(ReviewsState.Loaded(textOnly), finished = false, revealed = emptySet())
                .shouldBeInstanceOf<ReviewsTabModel.Listed>()
                .average shouldBe null
        }
    }

    describe("stageRatingModel") {

        it("TC-31: stageRatingModel maps every non-average state") {
            stageRatingModel(ReviewsState.Loading) shouldBe StageRatingModel.Checking
            stageRatingModel(ReviewsState.Offline) shouldBe StageRatingModel.NeedsConnection
            stageRatingModel(ReviewsState.Failed) shouldBe StageRatingModel.CouldNotLoad
            stageRatingModel(EMPTY_STATE) shouldBe StageRatingModel.NoReviews

            listOf(StageRatingModel.Checking, StageRatingModel.NeedsConnection, StageRatingModel.CouldNotLoad).forEach {
                it.dimmed shouldBe true
                it.stars shouldBe 0f
            }
            StageRatingModel.NoReviews.dimmed shouldBe false
            StageRatingModel.NoReviews.stars shouldBe 0f
        }

        it("TC-32: Average carries half-star stars, the raw average and the count") {
            val summarised = ReviewsState.Loaded(
                BookReviews(mine = null, others = listOf(review(1, rating = 4f)), summary = ReviewSummary(4.3f, 3)),
            )
            val average = stageRatingModel(summarised).shouldBeInstanceOf<StageRatingModel.Average>()
            average.stars shouldBe 4.5f
            average.average shouldBe 4.3f
            average.count shouldBe 3
            average.dimmed shouldBe false

            val threeFours = ReviewsState.Loaded(
                BookReviews(mine = null, others = listOf(review(1, rating = 4f), review(2, rating = 4f), review(3, rating = 4f)), summary = null),
            )
            stageRatingModel(threeFours) shouldBe StageRatingModel.Average(4.0f, 4.0f, 3)
        }
    }

    describe("showsReviews") {

        it("TC-33: showsReviews follows the owned formats") {
            for (ebook in listOf(false, true)) {
                for (audiobook in listOf(false, true)) {
                    for (physical in listOf(false, true)) {
                        for (wishlisted in listOf(false, true)) {
                            val formats = buildList {
                                if (ebook) add("epub")
                                if (audiobook) add("m4b")
                            }
                            val book = testBook(1).copy(formats = formats, hasPhysical = physical, isWishlisted = wishlisted)

                            showsReviews(book) shouldBe (ebook || audiobook || physical)
                        }
                    }
                }
            }
        }
    }

    describe("openReviews") {

        it("TC-34: openReviews jumps to the Reviews tab") {
            val tabs = RecordingTabs()

            openReviews(tabs)

            tabs.jumps shouldBe listOf(BookDetailTab.Reviews)
            tabs.others shouldBe emptyList()
        }
    }
})

/** Records what the page's tab strip was asked to do. */
private class RecordingTabs : BookDetailTabController {
    val jumps = mutableListOf<BookDetailTab>()
    val others = mutableListOf<String>()
    override val selected: BookDetailTab = BookDetailTab.Overview
    override fun select(tab: BookDetailTab) {
        others += "select:$tab"
    }
    override fun jumpTo(tab: BookDetailTab) {
        jumps += tab
    }
    override fun scrollTo(anchor: BookDetailAnchor) {
        others += "scrollTo:$anchor"
    }
}
