package com.omnibus.omnibus.data.highlights

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString

private fun <T : Any> T.orNull(): T? = takeIf { randomBoolean() }

fun randomHighlight(): Highlight = Highlight(
    id = randomLong(),
    text = randomString().orNull(),
    note = randomString().orNull(),
    color = randomEnum(),
    location = randomString().orNull(),
    format = randomString().orNull(),
    createdAtMillis = randomLong().orNull(),
    fraction = randomDouble().toFloat().orNull(),

/** The server sends whole seconds, so the creation time is whole seconds in milliseconds. */
