package com.omnibus.omnibus.data.series

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomString

/** [SeriesMember.hasCover] says whether there is a cover, so it follows the cover's URL. */
fun randomSeriesMember(): SeriesMember {
    val coverUrl = if (randomBoolean()) "/covers/${randomString()}.jpg" else null
    return SeriesMember(
        uuid = randomString(),
        title = randomString(),
        seriesIndex = if (randomBoolean()) randomString() else null,
        coverUrl = coverUrl,
        accent = if (randomBoolean()) randomString() else null,
        hasCover = coverUrl != null,
    )
}
