package com.omnibus.omnibus.data.bookmarks

import com.omnibus.omnibus.testing.TEST_RANDOM
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString

private fun <T> randomNullable(value: () -> T): T? = if (randomBoolean()) value() else null

fun randomBookmark(): Bookmark = Bookmark(
    id = randomNullable { randomLong() },
    clientId = randomNullable { randomString(8..16) },
    bookUuid = randomString(8..16),
    position = randomString(),
    title = randomNullable { randomString() },
    chapterTitle = randomNullable { randomString() },
    fraction = randomNullable { TEST_RANDOM.nextFloat() },
    createdAtSeconds = randomNullable { randomLong() },
)

fun randomBookmarkDraft(): BookmarkDraft =
    checkNotNull(BookmarkDraft.create(randomString(8..16), randomString(), randomNullable { randomString() }))
