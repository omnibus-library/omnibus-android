package com.omnibus.omnibus.feature.bookdetail.reviews.mine

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.reviews.Review
import com.omnibus.omnibus.screenshot.Shot

private val Tint = Color(0xFFB8864A)

/** No review yet: the unrated note, the write prompt and no trash. */
@PreviewTest
@PreviewLightDark
@Composable
fun MyReviewCardNew() {
    MyReviewShot(MyReviewModel.Known(null))
}

/** A spoiler review shown in full, with no veil, above the private notes box; the trash ends the header. */
@PreviewTest
@PreviewLightDark
@Composable
fun MyReviewCardWithNotes() {
    MyReviewShot(
        MyReviewModel.Known(
            review(rating = 4.5f, text = "Paragraph *one* of the review.\n\nParagraph two.", notes = "Only mine", spoiler = true),
        ),
    )
}

/** Text without private notes has no notes box. */
@PreviewTest
@PreviewLightDark
@Composable
fun MyReviewCardTextOnly() {
    MyReviewShot(MyReviewModel.Known(review(rating = 4f, text = "Just the text")))
}

/** Only private notes: the write prompt stays. */
@PreviewTest
@PreviewLightDark
@Composable
fun MyReviewCardNotesOnly() {
    MyReviewShot(MyReviewModel.Known(review(notes = "private")))
}

@PreviewTest
@PreviewLightDark
@Composable
fun MyReviewCardLoading() {
    MyReviewShot(MyReviewModel.Unknown(UnknownReason.Loading))
}

@PreviewTest
@PreviewLightDark
@Composable
fun MyReviewCardOffline() {
    MyReviewShot(MyReviewModel.Unknown(UnknownReason.Offline))
}

@PreviewTest
@PreviewLightDark
@Composable
fun MyReviewCardFailed() {
    MyReviewShot(MyReviewModel.Unknown(UnknownReason.Failed))
}

private fun review(rating: Float? = null, text: String? = null, notes: String? = null, spoiler: Boolean = false) =
    Review(0L, "", false, rating, text, notes, isSpoiler = spoiler, updatedAtMillis = null)

@Composable
private fun MyReviewShot(model: MyReviewModel) {
    Shot {
        MyReviewCard(model, Tint, onRate = {}, onDelete = {}, onCompose = {}, modifier = Modifier.fillMaxWidth(), reducedMotion = true)
    }
}
