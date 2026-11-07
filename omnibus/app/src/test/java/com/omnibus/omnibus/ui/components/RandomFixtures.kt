package com.omnibus.omnibus.ui.components

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomString

fun randomCoverIdentity(): CoverIdentity = CoverIdentity(
    uuid = randomString(),
    title = randomString(),
    creator = if (randomBoolean()) randomString() else null,
    accent = if (randomBoolean()) randomString() else null,
    hasCover = randomBoolean(),
)
