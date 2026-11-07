package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import com.omnibus.omnibus.data.reviews.randomRating
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomString

private fun <T : Any> T.orNull(): T? = takeIf { randomBoolean() }

/** Text and notes are never blank, as [ReviewFields] promises. */
fun randomReviewFields(): ReviewFields = ReviewFields(
    rating = randomRating().orNull(),
    text = randomString().orNull(),
    notes = randomString().orNull(),
    spoiler = randomBoolean(),
)
