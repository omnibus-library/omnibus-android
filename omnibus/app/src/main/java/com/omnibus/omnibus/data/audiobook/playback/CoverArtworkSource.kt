package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.cover.CoverImageResult
import com.omnibus.omnibus.data.cover.CoverRepository
import com.omnibus.omnibus.data.cover.CoverSize
import javax.inject.Inject
import okio.ByteString

/** The now-playing cover of a book, for the controller's artwork retry (I-SYS-4). */
interface CoverArtworkSource {

    /** The book's large cover, or why there isn't one. */
    suspend fun artwork(bookUuid: String): CoverArtwork
}

/** The outcome of [CoverArtworkSource.artwork]. */
sealed interface CoverArtwork {
    /** The cover image, encoded. */
    data class Found(val bytes: ByteString) : CoverArtwork

    /** The book has no cover, so the service draws a plate. Not retried. */
    data object NoCover : CoverArtwork

    /** The cover couldn't be fetched. Retried on the artwork schedule. */
    data object Failed : CoverArtwork
}

/** Reads the large thumbnail through the cover pipeline. */
class DefaultCoverArtworkSource @Inject constructor(
    private val coverRepository: CoverRepository,
) : CoverArtworkSource {

    override suspend fun artwork(bookUuid: String): CoverArtwork =
        when (val result = coverRepository.thumbnail(bookUuid, CoverSize.Lg)) {
            is CoverImageResult.Found -> CoverArtwork.Found(result.image)
            CoverImageResult.NotFound -> CoverArtwork.NoCover
            CoverImageResult.Error -> CoverArtwork.Failed
        }
}
