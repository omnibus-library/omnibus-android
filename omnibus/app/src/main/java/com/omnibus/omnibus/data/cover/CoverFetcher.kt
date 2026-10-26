package com.omnibus.omnibus.data.cover

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.omnibus.omnibus.di.TimeSource
import okio.Buffer
import java.io.IOException

/** Gives each [CoverThumbnail] request the memory key from [CoverCacheKeys.memoryKey]. */
class CoverKeyer(private val timeSource: TimeSource) : Keyer<CoverThumbnail> {
    override fun key(data: CoverThumbnail, options: Options): String =
        CoverCacheKeys.memoryKey(data, timeSource.nowMillis())
}

/**
 * Serves [CoverThumbnail] requests from a [CoverRepository], which owns authentication and the
 * disk cache, so the image loader's own network and disk layers stay out of it.
 *
 * Known limit: Coil stores the decoded bitmap in its memory cache after this returns, so an
 * invalidation that lands mid-fetch can leave one stale memory entry until its revalidation window
 * rolls over. The repository's disk tier is not affected.
 */
class CoverFetcher(
    private val thumbnail: CoverThumbnail,
    private val options: Options,
    private val repository: CoverRepository,
) : Fetcher {

    override suspend fun fetch(): FetchResult =
        when (val result = repository.thumbnail(thumbnail.uuid, thumbnail.size)) {
            is CoverImageResult.Found -> SourceFetchResult(
                source = ImageSource(Buffer().write(result.image), options.fileSystem),
                mimeType = "image/webp",
                dataSource = if (result.fromCache) DataSource.DISK else DataSource.NETWORK,
            )
            CoverImageResult.NotFound -> throw IOException("No cover for ${thumbnail.uuid}")
            CoverImageResult.Error -> throw IOException("Couldn't load the cover for ${thumbnail.uuid}")
        }

    /** Creates a [CoverFetcher] per request, all backed by [repository]. */
    class Factory(private val repository: CoverRepository) : Fetcher.Factory<CoverThumbnail> {
        override fun create(data: CoverThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            CoverFetcher(data, options, repository)
    }
}
