package com.omnibus.omnibus.data.reviews

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString

/** A rating as the app offers it: 0.5 to 5, in halves. */
fun randomRating(): Float = randomInt(1..10) / 2f

/** The server sends whole seconds, so the update time is whole seconds in milliseconds. */
fun randomReview(): Review = Review(
    userId = randomLong(),
    displayName = randomString(),
    hasAvatar = randomBoolean(),
    rating = if (randomBoolean()) randomRating() else null,
    text = if (randomBoolean()) randomString() else null,
    privateNotes = if (randomBoolean()) randomString() else null,
    isSpoiler = randomBoolean(),
    updatedAtMillis = if (randomBoolean()) randomLong(0L..2_000_000_000L) * 1_000L else null,
)
