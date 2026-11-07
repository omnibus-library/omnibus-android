package com.omnibus.omnibus.feature.bookdetail.reviews

import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.readstatus.randomReadStatusRecord
import com.omnibus.omnibus.data.reviews.BookReviews
import com.omnibus.omnibus.data.reviews.Review
import com.omnibus.omnibus.data.reviews.ReviewSummary
import com.omnibus.omnibus.data.reviews.ReviewsState
import com.omnibus.omnibus.data.reviews.randomRating
import com.omnibus.omnibus.data.reviews.randomReview
import com.omnibus.omnibus.feature.bookdetail.BookDetailAnchor
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.BookDetailTabController
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of
import java.util.Locale

/** Records which tab the page's tab strip was asked to jump to. */
private class TestTabController : BookDetailTabController {
    val jumps = mutableListOf<BookDetailTab>()
    override val selected: BookDetailTab = BookDetailTab.Overview
    override fun select(tab: BookDetailTab) = Unit
    override fun jumpTo(tab: BookDetailTab) {
        jumps += tab
    }
    override fun scrollTo(anchor: BookDetailAnchor) = Unit
}

class HouseholdReviewsTest : DescribeSpec({

    /** A spoiler that has text to hide. */
    fun spoiler() = randomReview().copy(isSpoiler = true, text = randomString())

    fun others(vararg reviews: Review) =
        BookReviews(mine = randomReview(), others = reviews.toList(), summary = null)

    describe("the household's average") {
        context("when the server sent a summary") {
            it("TC-16: is the server's summary") {
                val summary = ReviewSummary(randomRating(), randomInt(1..99))
                val reviews = BookReviews(randomReview(), listOf(randomReview()), summary)

                householdAverage(reviews) shouldBe HouseholdAverage(summary.average, summary.count)
            }
        }

        context("when the server sent no summary") {
            it("TC-17: is the mean of every rating, yours included") {
                val reviews = BookReviews(
                    mine = randomReview().copy(rating = 3f),
                    others = listOf(4f, 5f, null).map { randomReview().copy(rating = it) },
                    summary = null,
                )

                householdAverage(reviews) shouldBe HouseholdAverage(4f, 3)
            }

            it("TC-17: is missing for a book nobody has rated") {
                val reviews = BookReviews(
                    mine = randomReview().copy(rating = null),
                    others = listOf(randomReview().copy(rating = null)),
                    summary = null,
                )

                householdAverage(reviews) shouldBe null
            }
        }
    }

    describe("the half star a value snaps to") {
        it("TC-18: is the nearest half with ties up, clamped to the row, for each of these values") {
            checkAll(
                Exhaustive.of(
                    4.24f to 4f,
                    4.25f to 4.5f,
                    4.75f to 5f,
                    0.2f to 0f,
                    0.25f to 0.5f,
                    -1f to 0f,
                    7f to 5f,
                    Float.NaN to 0f,
                    Float.POSITIVE_INFINITY to 5f,
                    Float.NEGATIVE_INFINITY to 0f,
                ),
            ) { (value, snapped) ->
                halfStarRound(value) shouldBe snapped
            }
        }
    }

    describe("a star number for display") {
        it("TC-19: has one decimal rounded half up without a trailing zero, in the notation of each locale") {
            checkAll(
                Exhaustive.of(
                    Triple(4.33f, Locale.US, "4.3"),
                    Triple(4.35f, Locale.US, "4.4"),
                    Triple(4f, Locale.US, "4"),
                    Triple(4.5f, Locale.GERMANY, "4,5"),
                    Triple(4.33f, Locale.FRANCE, "4,3"),
                ),
            ) { (value, locale, text) ->
                starNumber(value, locale) shouldBe text
            }
        }
    }

    describe("the review entries") {
        it("TC-20: are the other readers' reviews in the server's order") {
            val others = List(3) { randomReview() }
            val reviews = BookReviews(mine = randomReview(), others = others, summary = null)

            reviewEntries(reviews, randomBoolean(), emptySet()).map { it.userId } shouldBe others.map { it.userId }
        }

        it("TC-21: name the body kind of each kind of review") {
            checkAll(
                Exhaustive.of(
                    Triple("Great", 4f, ReviewBody.Text),
                    Triple(null, 3.5f, ReviewBody.RatedOnly),
                    Triple(null, null, ReviewBody.None),
                    Triple("![x](u)", 5f, ReviewBody.ImageOnly),
                ),
            ) { (text, rating, body) ->
                val reviews = others(randomReview().copy(text = text, rating = rating))

                reviewEntries(reviews, randomBoolean(), emptySet()).single().body shouldBe body
            }
        }

        it("TC-22: are veiled only for an unfinished reader's unopened spoiler with text, for each case") {
            val secret = spoiler()

            checkAll(
                Exhaustive.of(
                    Triple(secret.copy(), false to emptySet<Long>(), true),
                    Triple(secret.copy(), true to emptySet(), false),
                    Triple(secret.copy(), false to setOf(secret.userId), false),
                    Triple(secret.copy(), false to setOf(secret.userId + 1), true),
                    Triple(secret.copy(isSpoiler = false), false to emptySet(), false),
                    Triple(secret.copy(text = null), false to emptySet(), false),
                ),
            ) { (review, reader, veiled) ->
                val (finished, revealed) = reader

                reviewEntries(others(review), finished, revealed).single().veiled shouldBe veiled
            }
        }
    }

    describe("whether the reader has finished") {
        it("TC-23: is true only for a known Finished status, across every status") {
            fun known(status: ReadStatus) = ReadStatusState.Known(randomReadStatusRecord().copy(status = status))

            checkAll(
                Exhaustive.of(
                    known(ReadStatus.Finished) to true,
                    known(ReadStatus.Reading) to false,
                    known(ReadStatus.Unread) to false,
                    ReadStatusState.Unknown to false,
                    ReadStatusState.Failed to false,
                ),
            ) { (status, finished) ->
                isFinished(status) shouldBe finished
            }
        }
    }

    describe("the Reviews tab") {
        it("TC-24: follows each state that has no reviews yet") {
            checkAll(
                Exhaustive.of(
                    ReviewsState.Loading to ReviewsTabModel.Hidden,
                    ReviewsState.Offline to ReviewsTabModel.Offline,
                    ReviewsState.Failed to ReviewsTabModel.Failed,
                ),
            ) { (state, model) ->
                reviewsTabModel(state, randomBoolean(), emptySet()) shouldBe model
            }
        }

        context("when no other reader has reviewed the book") {
            it("TC-25: is Hidden only for a review of yours with content, across each kind of review") {
                checkAll(
                    Exhaustive.of(
                        null to ReviewsTabModel.Empty,
                        randomReview().copy(rating = null, text = null) to ReviewsTabModel.Empty,
                        randomReview().copy(rating = randomRating(), text = null) to ReviewsTabModel.Hidden,
                        randomReview().copy(rating = null, text = randomString()) to ReviewsTabModel.Hidden,
                    ),
                ) { (mine, model) ->
                    val reviews = BookReviews(mine = mine, others = emptyList(), summary = null)

                    reviewsTabModel(ReviewsState.Loaded(reviews), randomBoolean(), emptySet()) shouldBe model
                }
            }
        }

        context("when other readers have reviewed the book") {
            it("TC-26: lists them under the household's count, average") {
                val summary = ReviewSummary(randomRating(), randomInt(1..99))
                val reviews = BookReviews(randomReview(), List(3) { randomReview() }, summary)
                val finished = randomBoolean()

                reviewsTabModel(ReviewsState.Loaded(reviews), finished, emptySet()) shouldBe ReviewsTabModel.Listed(
                    count = 3,
                    average = summary.average,
                    entries = reviewEntries(reviews, finished, emptySet()),
                    finished = finished,
                )
            }
        }
    }

    describe("the stage rating") {
        it("TC-31: follows each state with no average") {
            checkAll(
                Exhaustive.of(
                    ReviewsState.Loading to StageRatingModel.Checking,
                    ReviewsState.Offline to StageRatingModel.NeedsConnection,
                    ReviewsState.Failed to StageRatingModel.CouldNotLoad,
                    ReviewsState.Loaded(BookReviews(null, emptyList(), null)) to StageRatingModel.NoReviews,
                ),
            ) { (state, model) ->
                stageRatingModel(state) shouldBe model
            }
        }

        context("when the household has an average") {
            it("TC-32: carries the snapped stars, the raw average, the count") {
                val reviews = BookReviews(randomReview(), listOf(randomReview()), ReviewSummary(4.3f, 3))

                stageRatingModel(ReviewsState.Loaded(reviews)) shouldBe StageRatingModel.Average(4.5f, 4.3f, 3)
            }
        }
    }

    describe("whether a book shows reviews") {
        it("TC-33: follows the owned formats, across every mix of ebook, audiobook, physical copy, wishlist") {
            val flag = Exhaustive.boolean()
            checkAll(flag, flag, flag, flag) { ebook, audiobook, physical, wishlisted ->
                val book = randomBook().copy(
                    formats = listOfNotNull("epub".takeIf { ebook }, "m4b".takeIf { audiobook }),
                    hasPhysical = physical,
                    isWishlisted = wishlisted,
                )

                showsReviews(book) shouldBe (ebook || audiobook || physical)
            }
        }
    }

    describe("opening the reviews") {
        it("TC-34: jumps to the Reviews tab") {
            val tabs = TestTabController()

            openReviews(tabs)

            tabs.jumps shouldBe listOf(BookDetailTab.Reviews)
        }
    }
})
