package com.omnibus.omnibus.feature.bookdetail.reviews

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.reviews.BookReviews
import com.omnibus.omnibus.data.reviews.ReviewsState
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.BookDetailTabController
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.floor

/** The household's average rating and how many ratings it is from. */
internal data class HouseholdAverage(val average: Float, val count: Int)

/**
 * The household's average. The server's summary wins when it sent one, since it covers every reader including you.
 * Otherwise it is the mean of every rating on the page, yours included, so the average shows below the server's
 * two-rating threshold too. Null when nobody has rated the book.
 */
internal fun householdAverage(reviews: BookReviews): HouseholdAverage? {
    reviews.summary?.let { return HouseholdAverage(it.average, it.count) }
    val ratings = reviews.others.mapNotNull { it.rating } + listOfNotNull(reviews.mine?.rating)
    if (ratings.isEmpty()) return null
    return HouseholdAverage(ratings.sum() / ratings.size, ratings.size)
}

/** The nearest multiple of half a star, with halves rounding up, clamped to 0..5. NaN is zero. */
internal fun halfStarRound(value: Float): Float =
    if (value.isNaN()) 0f else (floor(value * 2f + 0.5f) / 2f).coerceIn(0f, 5f)

/**
 * A number for display: rounded half up to one decimal, with a trailing ".0" dropped and the locale's decimal
 * separator. It rounds the decimal text of the float, so 4.35 reads "4.4" as a reader expects.
 */
internal fun starNumber(value: Float, locale: Locale): String {
    if (!value.isFinite()) return "0"
    val rounded = BigDecimal(value.toString()).setScale(1, RoundingMode.HALF_UP)
    return DecimalFormat("0.#", DecimalFormatSymbols.getInstance(locale)).format(rounded)
}

/** How a review's text reads on the list: as text, as an image the app can't show, as a rating alone, or nothing. */
internal enum class ReviewBody { Text, ImageOnly, RatedOnly, None }

/**
 * One review as the list shows it, in the server's order.
 *
 * @property veiled The text is hidden behind "Show review": it is a spoiler, the reader has not finished the book, and
 *   they have not opened it.
 */
internal data class ReviewEntry(
    val userId: Long,
    val name: String,
    val hasAvatar: Boolean,
    val dateMillis: Long?,
    val rating: Float?,
    val markdown: String?,
    val body: ReviewBody,
    val veiled: Boolean,
)

/** Whether the reader has finished the book. Only a known Finished status counts. */
internal fun isFinished(status: ReadStatusState): Boolean =
    status is ReadStatusState.Known && status.record.status == ReadStatus.Finished

/**
 * One entry per other reader's review, in the server's order, never including your own. A spoiler is veiled until the
 * reader has finished the book or opened it, which is the [revealed] set of user ids.
 */
internal fun reviewEntries(reviews: BookReviews, finished: Boolean, revealed: Set<Long>): List<ReviewEntry> =
    reviews.others.map { review ->
        ReviewEntry(
            userId = review.userId,
            name = review.displayName,
            hasAvatar = review.hasAvatar,
            dateMillis = review.updatedAtMillis,
            rating = review.rating,
            markdown = review.text,
            body = reviewBody(review.text, review.rating),
            veiled = review.isSpoiler && review.text != null && !finished && review.userId !in revealed,
        )
    }

/** The body kind for a review. Text with nothing left after stripping Markdown is an image-only review. */
private fun reviewBody(text: String?, rating: Float?): ReviewBody = when {
    text != null && reviewPlainText(text, finished = true, inlineSpoiler = "") != null -> ReviewBody.Text
    text != null -> ReviewBody.ImageOnly
    rating != null -> ReviewBody.RatedOnly
    else -> ReviewBody.None
}

/** What the Reviews tab shows for a book's reviews state. */
internal sealed interface ReviewsTabModel {
    /** Nothing: the reviews are loading, or only your own review exists, which the card above the list shows. */
    data object Hidden : ReviewsTabModel

    /** The device has no connection, so the reviews could not load. */
    data object Offline : ReviewsTabModel

    /** The reviews could not load for another reason. The tab offers a retry. */
    data object Failed : ReviewsTabModel

    /** Nobody in the household has rated or reviewed the book. */
    data object Empty : ReviewsTabModel

    /**
     * The household's reviews. [average] is the raw average, which the composable formats in the screen's locale.
     * [finished] is carried so the composable can show every text open.
     */
    data class Listed(
        val count: Int,
        val average: Float?,
        val entries: List<ReviewEntry>,
        val finished: Boolean,
    ) : ReviewsTabModel
}

/** The Reviews tab's model for a reviews state. */
internal fun reviewsTabModel(state: ReviewsState, finished: Boolean, revealed: Set<Long>): ReviewsTabModel =
    when (state) {
        ReviewsState.Loading -> ReviewsTabModel.Hidden
        ReviewsState.Offline -> ReviewsTabModel.Offline
        ReviewsState.Failed -> ReviewsTabModel.Failed
        is ReviewsState.Loaded -> {
            val reviews = state.reviews
            val mine = reviews.mine
            when {
                reviews.others.isEmpty() && mine?.rating == null && mine?.text == null -> ReviewsTabModel.Empty
                // Only your own review: #108's card shows it, and there is no household list to add to it.
                reviews.others.isEmpty() -> ReviewsTabModel.Hidden
                else -> ReviewsTabModel.Listed(
                    count = reviews.others.size,
                    average = householdAverage(reviews)?.average,
                    entries = reviewEntries(reviews, finished, revealed),
                    finished = finished,
                )
            }
        }
    }

/** What the stage's rating shows. Every state except [Average] has no stars, and [dimmed] marks the ones not yet known. */
internal sealed interface StageRatingModel {
    /** The stars to draw, from 0 to 5 in halves. */
    val stars: Float

    /** Whether the rating is drawn at reduced opacity, because the reviews are not known yet. */
    val dimmed: Boolean

    /** The reviews are loading. */
    data object Checking : StageRatingModel {
        override val stars: Float = 0f
        override val dimmed: Boolean = true
    }

    /** The device has no connection. */
    data object NeedsConnection : StageRatingModel {
        override val stars: Float = 0f
        override val dimmed: Boolean = true
    }

    /** The reviews could not load for another reason. */
    data object CouldNotLoad : StageRatingModel {
        override val stars: Float = 0f
        override val dimmed: Boolean = true
    }

    /** The reviews loaded and nobody has rated the book. */
    data object NoReviews : StageRatingModel {
        override val stars: Float = 0f
        override val dimmed: Boolean = false
    }

    /**
     * The household's average. [stars] is the average snapped to the nearest half star. [average] and [count] are the
     * raw values, for the description.
     */
    data class Average(override val stars: Float, val average: Float, val count: Int) : StageRatingModel {
        override val dimmed: Boolean = false
    }
}

/** The stage's rating model for a reviews state. */
internal fun stageRatingModel(state: ReviewsState): StageRatingModel = when (state) {
    ReviewsState.Loading -> StageRatingModel.Checking
    ReviewsState.Offline -> StageRatingModel.NeedsConnection
    ReviewsState.Failed -> StageRatingModel.CouldNotLoad
    is ReviewsState.Loaded -> householdAverage(state.reviews)
        ?.let { StageRatingModel.Average(halfStarRound(it.average), it.average, it.count) }
        ?: StageRatingModel.NoReviews
}

/** Whether others could have read the book: it has an ebook, an audiobook or a physical copy. */
internal fun showsReviews(book: Book): Boolean = book.hasEbook || book.hasAudiobook || book.hasPhysical

/** Selects the Reviews tab and brings the strip to the top, which is what the stage's rating does. */
internal fun openReviews(tabs: BookDetailTabController) = tabs.jumpTo(BookDetailTab.Reviews)
