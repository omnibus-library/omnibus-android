package com.omnibus.omnibus.data.reader

import android.content.Context
import androidx.webkit.WebViewAssetLoader
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

/**
 * Builds the [WebViewAssetLoader] a reader WebView routes its requests through: the bundled reader
 * files under `/reader/` and the book under `/book/`.
 */
class ReaderAssetLoaderFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val bookFileSource: BookFileSource,
) {
    /**
     * @param onBookFailure called when the book can't be opened, on a WebView background thread.
     */
    fun create(onBookFailure: (BookFileResult.Failure) -> Unit = {}): WebViewAssetLoader =
        WebViewAssetLoader.Builder()
            .setDomain(ReaderAssets.HOST)
            .addPathHandler(ReaderAssets.READER_PATH, ReaderAssetPathHandler(context))
            .addPathHandler(ReaderAssets.BOOK_PATH, BookPathHandler(ioDispatcher, bookFileSource, onBookFailure))
            .build()
}
