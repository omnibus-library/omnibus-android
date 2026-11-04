package com.omnibus.omnibus.data.audiobook.playback

/** A [CoverArtworkSource] that answers [answer] and records each book it was asked about. */
class FakeCoverArtworkSource : CoverArtworkSource {

    /** What [artwork] answers. */
    var answer: CoverArtwork = CoverArtwork.NoCover

    /** The book uuid of each [artwork] call, in call order. */
    val requests = mutableListOf<String>()

    override suspend fun artwork(bookUuid: String): CoverArtwork {
        requests += bookUuid
        return answer
    }
}
