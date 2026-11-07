package com.omnibus.omnibus.feature.bookdetail.reviews

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.screenshot.Shot

// Instants are at noon UTC, so they fall on the same date in every zone the shots could be taken in.
/** 2025-11-01 12:00 UTC. */
private const val NOW_MILLIS = 1_761_998_400_000L

/** 2025-06-14 12:00 UTC. */
private const val JUN_14_2025 = 1_749_902_400_000L

private val Tint = Color(0xFFB8864A)

@PreviewTest
@PreviewLightDark
@Composable
fun HouseholdReviewsEmpty() {
    HouseholdReviewsShot(ReviewsTabModel.Empty)
}

/**
 * The heading with its count and average, then an entry with every part, one with only a name and text, a rated-only
 * entry and an image-only one, which show their notes and no Read more.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun HouseholdReviewsListed() {
    HouseholdReviewsShot(
        listed(
            entry(3, "Maren", date = JUN_14_2025, rating = 4.5f, markdown = "Loved it.", body = ReviewBody.Text),
            entry(4, "Theo", markdown = "Slow start.", body = ReviewBody.Text),
            entry(5, "Jonas", rating = 3.5f, body = ReviewBody.RatedOnly),
            entry(6, "Ada", rating = 4f, markdown = "![x](u)", body = ReviewBody.ImageOnly),
            average = 4.3f,
        ),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun HouseholdReviewsVeiled() {
    HouseholdReviewsShot(
        listed(entry(7, "Priya", rating = 4f, markdown = "The butler did it.", body = ReviewBody.Text, veiled = true)),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun HouseholdReviewsOffline() {
    HouseholdReviewsShot(ReviewsTabModel.Offline)
}

@PreviewTest
@PreviewLightDark
@Composable
fun HouseholdReviewsFailed() {
    HouseholdReviewsShot(ReviewsTabModel.Failed)
}

private fun entry(
    id: Long,
    name: String,
    date: Long? = null,
    rating: Float? = null,
    markdown: String? = null,
    body: ReviewBody = ReviewBody.None,
    veiled: Boolean = false,
) = ReviewEntry(
    userId = id,
    name = name,
    hasAvatar = false,
    dateMillis = date,
    rating = rating,
    markdown = markdown,
    body = body,
    veiled = veiled,
)

private fun listed(vararg entries: ReviewEntry, average: Float? = null) =
    ReviewsTabModel.Listed(count = entries.size, average = average, entries = entries.toList(), finished = false)

@Composable
private fun HouseholdReviewsShot(model: ReviewsTabModel) {
    Shot {
        HouseholdReviews(model, Tint, NOW_MILLIS, onReveal = {}, onRetry = {}, modifier = Modifier.fillMaxWidth())
    }
}
