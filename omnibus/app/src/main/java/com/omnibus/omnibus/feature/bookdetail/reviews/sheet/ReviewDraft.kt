package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import com.omnibus.omnibus.data.reviews.Patch
import com.omnibus.omnibus.data.reviews.Review
import com.omnibus.omnibus.data.reviews.ReviewPatch

/** One of the review's four fields, in the conflict note's order. */
enum class ReviewField { Rating, Text, Notes, Spoiler }

/** A review's values. Text and notes are never blank: blank is null. */
data class ReviewFields(val rating: Float?, val text: String?, val notes: String?, val spoiler: Boolean) {

    /** Whether the review has text or private notes, which makes the sheet's title "Edit review". */
    val hasWriting: Boolean get() = text != null || notes != null

    companion object {
        /** No rating, text, notes, or spoiler flag. */
        val Empty = ReviewFields(null, null, null, false)

        /** The values of [review], or [Empty] for no review. */
        fun of(review: Review?): ReviewFields = if (review == null) Empty else
            ReviewFields(review.rating, review.text.clean(), review.privateNotes.clean(), review.isSpoiler)
    }
}

/** The sheet's title, chosen once when the sheet opens. */
enum class ReviewSheetTitle { Write, Edit }

/** The title for a sheet opened on [opened]: "Edit review" when the review already has text or notes. */
fun titleFor(opened: ReviewFields): ReviewSheetTitle = if (opened.hasWriting) ReviewSheetTitle.Edit else ReviewSheetTitle.Write

/**
 * What the reader has touched. [Patch.Set] means touched. A touched field stays set even when typed back to its opened
 * value, so the field being typed in never jumps to a server value.
 */
data class ReviewDraft(
    val rating: Patch<Float?> = Patch.Unchanged,
    val text: Patch<String?> = Patch.Unchanged,
    val notes: Patch<String?> = Patch.Unchanged,
    val spoiler: Patch<Boolean> = Patch.Unchanged,
)

/** This text with blank made null. */
internal fun String?.clean(): String? = this?.takeIf { it.isNotBlank() }

/** The draft's value for each touched field and [server]'s value for each untouched one. */
fun ReviewDraft.displayed(server: ReviewFields): ReviewFields = ReviewFields(
    rating = rating.or(server.rating),
    text = text.or(server.text),
    notes = notes.or(server.notes),
    spoiler = spoiler.or(server.spoiler),
)

/** The touched fields whose clean value differs from [base]. */
private fun ReviewDraft.changedFrom(base: ReviewFields): List<ReviewField> = buildList {
    if (rating is Patch.Set && rating.value != base.rating) add(ReviewField.Rating)
    if (text is Patch.Set && text.value.clean() != base.text) add(ReviewField.Text)
    if (notes is Patch.Set && notes.value.clean() != base.notes) add(ReviewField.Notes)
    if (spoiler is Patch.Set && spoiler.value != base.spoiler) add(ReviewField.Spoiler)
}

/** Whether any touched field differs from [opened]. This drives Save and the discard confirmation. */
fun ReviewDraft.isDirty(opened: ReviewFields): Boolean = changedFrom(opened).isNotEmpty()

/**
 * The patch for [server]: each touched field whose clean value differs from it. Blank text and notes go as null, and a
 * touched field changed elsewhere still sends the reader's value.
 */
fun ReviewDraft.savePatch(server: ReviewFields): ReviewPatch {
    val changed = changedFrom(server)
    return ReviewPatch(
        rating = if (ReviewField.Rating in changed) rating else Patch.Unchanged,
        text = if (ReviewField.Text in changed) Patch.Set((text as Patch.Set).value.clean()) else Patch.Unchanged,
        privateNotes = if (ReviewField.Notes in changed) Patch.Set((notes as Patch.Set).value.clean()) else Patch.Unchanged,
        isSpoiler = if (ReviewField.Spoiler in changed) spoiler else Patch.Unchanged,
    )
}

/**
 * The touched fields that [server] changed since [opened] to something other than the reader's value. The sheet's own
 * optimistic save makes the server equal the draft, so it is not a conflict.
 */
fun ReviewDraft.conflicts(opened: ReviewFields, server: ReviewFields): List<ReviewField> {
    val movedOnServer = buildList {
        if (server.rating != opened.rating) add(ReviewField.Rating)
        if (server.text != opened.text) add(ReviewField.Text)
        if (server.notes != opened.notes) add(ReviewField.Notes)
        if (server.spoiler != opened.spoiler) add(ReviewField.Spoiler)
    }
    val differsFromReader = changedFrom(server)
    return ReviewField.entries.filter { it in movedOnServer && it in differsFromReader }
}

private fun <T> Patch<T>.or(current: T): T = if (this is Patch.Set) value else current
