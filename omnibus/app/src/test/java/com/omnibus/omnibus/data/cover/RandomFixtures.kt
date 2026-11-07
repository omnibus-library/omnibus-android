package com.omnibus.omnibus.data.cover

import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomString

fun randomCoverThumbnail(): CoverThumbnail = CoverThumbnail(randomString(8..16), randomEnum<CoverSize>())
