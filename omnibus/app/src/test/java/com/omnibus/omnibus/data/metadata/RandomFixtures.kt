package com.omnibus.omnibus.data.metadata

import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString

fun randomVocabularyEntry(): VocabularyEntry = VocabularyEntry(name = randomString(), count = randomLong(0L..10_000L))
