package com.omnibus.omnibus.data.suggestions

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString

fun randomBookSuggestion(): BookSuggestion = BookSuggestion(
    hardcoverId = randomLong(),
    hardcoverUrl = "https://hardcover.app/books/${randomString()}",
    title = randomString(),
    author = randomString(),
    coverPath = if (randomBoolean()) "/api/suggestions/${randomString()}/${randomLong()}/cover" else null,
)
