package com.omnibus.omnibus.data.reviews

/**
 * One field of a [ReviewPatch]. [Unchanged] keeps the review's current value, and [Set] replaces it. A set of null clears
 * the field.
 */
sealed interface Patch<out T> {
    /** Keep the current value. */
    data object Unchanged : Patch<Nothing>

    /** Replace the current value with [value]. */
    data class Set<T>(val value: T) : Patch<T>
}

/**
 * A change to the signed-in reader's review. The fields are independent, so setting the rating keeps the text and the
 * notes. Blank text or notes count as null.
 *
 * @property rating The stars, from 0.5 to 5 in halves, or null to clear them.
 * @property text The review's Markdown source, or null to clear it.
 * @property privateNotes The private notes, or null to clear them.
 * @property isSpoiler Whether the text gives away the story.
 */
data class ReviewPatch(
    val rating: Patch<Float?> = Patch.Unchanged,
    val text: Patch<String?> = Patch.Unchanged,
    val privateNotes: Patch<String?> = Patch.Unchanged,
    val isSpoiler: Patch<Boolean> = Patch.Unchanged,
)

/**
 * [this] review with [patch] applied as the server would apply it: blank text is null, and a review with nothing left
 * is null. A null review gets a new one with no attribution, and the last change time is kept.
 */
internal fun Review?.applying(patch: ReviewPatch): Review? {
    val base = this ?: Review(0L, "", false, null, null, null, isSpoiler = false, updatedAtMillis = null)
    val next = base.copy(
        rating = patch.rating.or(base.rating),
        text = patch.text.or(base.text)?.takeIf { it.isNotBlank() },
        privateNotes = patch.privateNotes.or(base.privateNotes)?.takeIf { it.isNotBlank() },
        isSpoiler = patch.isSpoiler.or(base.isSpoiler),
    )
    return next.takeIf { it.rating != null || it.text != null || it.privateNotes != null }
}

/** Whether this patch would leave [mine] as it is, so no request is needed. */
internal fun ReviewPatch.isNoOpFor(mine: Review?): Boolean = mine.applying(this) == mine

private fun <T> Patch<T>.or(current: T): T = if (this is Patch.Set) value else current
