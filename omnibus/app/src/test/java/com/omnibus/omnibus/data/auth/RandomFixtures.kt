package com.omnibus.omnibus.data.auth

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString

fun randomCurrentUser(): CurrentUser = CurrentUser(
    id = randomLong(),
    canEdit = randomBoolean(),
    canUpload = randomBoolean(),
    hiddenFormats = List(randomInt(0..3)) { randomString().lowercase() }.toSet(),
    isAdmin = randomBoolean(),
    canDownload = randomBoolean(),
    kindleEmail = if (randomBoolean()) randomString() else null,
)
