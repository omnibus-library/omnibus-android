package com.omnibus.omnibus.feature.bookdetail.progress

import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong

private fun <T : Any> T.orNull(): T? = takeIf { randomBoolean() }

fun randomBookReadingProgress(): BookReadingProgress = BookReadingProgress(
    fraction = randomDouble().toFloat().orNull(),
    chapterNumber = randomInt(1..100).orNull(),
    latestMedium = randomEnum<ProgressFormat>().orNull(),
    audioFileId = randomLong().orNull(),
    audioElapsedSeconds = randomLong(0L..100_000L).orNull(),
    audioTotalSeconds = randomLong(1L..100_000L).orNull(),
)
