package com.omnibus.omnibus.data.reader

import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream

/**
 * Answers `/book/<uuid>.epub` with the book's bytes, the Android counterpart of iOS's
 * `omnibus-reader://book/` handler.
 *
 * The WebView calls this on one of its own background threads, so blocking on the source is fine;
 * the work itself still runs on the IO dispatcher. The body is streamed rather than buffered, and
 * `WebViewAssetLoader` has no `Range` support, so epub.js gets the whole file.
 *
 * @param onFailure told about every book that couldn't be opened, on the WebView's thread. epub.js
 *   never reports a book whose bytes don't arrive, so this is how the host learns to show an error.
 */
class BookPathHandler(
    private val ioDispatcher: CoroutineDispatcher,
    private val bookFileSource: BookFileSource,
    private val onFailure: (BookFileResult.Failure) -> Unit,
) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse {
        val uuid = ReaderAssets.bookUuidFor(path)
            ?: return respond(BookHttpResponse.of(BookFileResult.NotFound))
        val result = runBlocking(ioDispatcher) { bookFileSource.open(uuid) }
        if (result is BookFileResult.Failure) onFailure(result)
        return respond(BookHttpResponse.of(result))
    }

    private fun respond(response: BookHttpResponse) =
        WebResourceResponse(
            BookHttpResponse.EPUB_MIME_TYPE,
            null,
            response.status,
            response.reason,
            response.headers,
            response.body ?: ByteArrayInputStream(ByteArray(0)),
        )
}
