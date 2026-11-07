package com.omnibus.omnibus.data.cover.local

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import okio.ByteString.Companion.encodeUtf8

fun randomCachedCover(): CachedCover = CachedCover(
    bytes = randomString().encodeUtf8(),
    etag = if (randomBoolean()) "\"${randomString()}\"" else null,
    storedAtMillis = randomLong(),
)
