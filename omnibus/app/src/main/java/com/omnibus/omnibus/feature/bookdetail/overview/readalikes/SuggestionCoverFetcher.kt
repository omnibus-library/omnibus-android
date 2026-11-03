package com.omnibus.omnibus.feature.bookdetail.overview.readalikes

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.omnibus.omnibus.data.suggestions.SuggestionCoverResult
import com.omnibus.omnibus.data.suggestions.SuggestionsRepository
import okio.Buffer
import java.io.IOException

/** The image request for one suggestion cover: its server-relative [path], such as `/api/suggestions/{uuid}/{rank}/cover`. */
data class SuggestionCoverRequest(val path: String)

/**
 * Serves a suggestion cover from a [SuggestionsRepository], which adds the bearer token, so the image loader's own
 * network layer stays out of it. A thrown [IOException] makes the image fail, and the plate under it stays visible.
 */
class SuggestionCoverFetcher(
    private val data: SuggestionCoverRequest,
    private val options: Options,
    private val repository: SuggestionsRepository,
) : Fetcher {

    override suspend fun fetch(): FetchResult =
        when (val result = repository.getCover(data.path)) {
            is SuggestionCoverResult.Found -> SourceFetchResult(
                source = ImageSource(Buffer().write(result.bytes), options.fileSystem),
                mimeType = result.mimeType,
                dataSource = DataSource.NETWORK,
            )
            SuggestionCoverResult.NotFound -> throw IOException("No cover at ${data.path}")
            SuggestionCoverResult.Failure -> throw IOException("Couldn't load the cover at ${data.path}")
        }

    /** Creates a [SuggestionCoverFetcher] per request, all backed by [repository]. */
    class Factory(private val repository: SuggestionsRepository) : Fetcher.Factory<SuggestionCoverRequest> {
        override fun create(data: SuggestionCoverRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            SuggestionCoverFetcher(data, options, repository)
    }
}
