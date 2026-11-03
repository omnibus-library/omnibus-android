package com.omnibus.omnibus.data.reviews.remote

import com.omnibus.omnibus.data.reviews.BookReviews
import com.omnibus.omnibus.data.reviews.Review
import com.omnibus.omnibus.data.reviews.ReviewSummary
import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The body of `GET api/reviews/{uuid}`. Every field is optional on the wire, so a missing one takes its default.
 * `body_html`, `buckets`, `rated_at` and `created_at` are not read.
 */
@Serializable
data class BookReviewsDto(
    @SerialName("mine") val mine: MyReviewDto? = null,
    @SerialName("others") val others: List<AttributedReviewDto> = emptyList(),
    @SerialName("summary") val summary: ReviewSummaryDto? = null,
) {
    /** These reviews as domain reviews. Rows without a user id are dropped, and the order of [others] is kept. */
    fun toDomain(): BookReviews = BookReviews(
        mine = mine?.toDomain(),
        others = others.mapNotNull { it.toDomain() },
        summary = summary?.toDomain(),
    )
}

/** The signed-in reader's own review. The server does not attribute it, so it has no user fields. */
@Serializable
data class MyReviewDto(
    @SerialName("stars") val stars: Float? = null,
    @SerialName("body_md") val bodyMd: String? = null,
    @SerialName("notes_md") val notesMd: String? = null,
    @SerialName("spoiler") val spoiler: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long? = null,
    @SerialName("updated_at_iso") val updatedAtIso: String? = null,
) {
    /** This review with no attribution, and its private notes. */
    fun toDomain(): Review = Review(
        userId = 0L,
        displayName = "",
        hasAvatar = false,
        rating = rating(stars),
        text = text(bodyMd),
        privateNotes = text(notesMd),
        isSpoiler = spoiler,
        updatedAtMillis = updatedAtMillis(updatedAt, updatedAtIso),
    )
}

/** Another reader's review. Its private notes are never sent to the app, so they are not read. */
@Serializable
data class AttributedReviewDto(
    @SerialName("user_id") val userId: Long? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("has_avatar") val hasAvatar: Boolean = false,
    @SerialName("stars") val stars: Float? = null,
    @SerialName("body_md") val bodyMd: String? = null,
    @SerialName("spoiler") val spoiler: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long? = null,
    @SerialName("updated_at_iso") val updatedAtIso: String? = null,
) {
    /** This reader's review, or null when the row has no user id. The name is trimmed. */
    fun toDomain(): Review? {
        val id = userId ?: return null
        return Review(
            userId = id,
            displayName = username?.trim().orEmpty(),
            hasAvatar = hasAvatar,
            rating = rating(stars),
            text = text(bodyMd),
            privateNotes = null,
            isSpoiler = spoiler,
            updatedAtMillis = updatedAtMillis(updatedAt, updatedAtIso),
        )
    }
}

/** The server's rating summary. It is only a summary when at least one reader rated the book. */
@Serializable
data class ReviewSummaryDto(
    @SerialName("average_stars") val averageStars: Float? = null,
    @SerialName("ratings") val ratings: Int = 0,
) {
    /** The summary as a domain summary, or null when there is no average or no rating. */
    fun toDomain(): ReviewSummary? =
        averageStars?.takeIf { ratings >= 1 }?.let { ReviewSummary(average = it, count = ratings) }
}

/** A star rating from 0.5 to 5, or null for anything outside that range. */
private fun rating(stars: Float?): Float? = stars?.takeIf { it in 0.5f..5f }

/** The review text as sent, or null when it is blank. */
private fun text(markdown: String?): String? = markdown?.takeIf { it.isNotBlank() }

/** The last change in milliseconds: the seconds field, else the ISO timestamp, else null. */
private fun updatedAtMillis(seconds: Long?, iso: String?): Long? =
    seconds?.times(1_000L) ?: BookDetailFormat.parseServerTimestamp(iso)
