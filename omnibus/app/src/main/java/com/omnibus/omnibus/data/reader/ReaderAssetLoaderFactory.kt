package com.omnibus.omnibus.data.reader

import android.content.Context
import androidx.webkit.WebViewAssetLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Builds the [WebViewAssetLoader] a reader WebView routes its requests through.
 *
 * Each WebView gets its own loader so one reader never shares handler state with another.
 */
class ReaderAssetLoaderFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun create(): WebViewAssetLoader =
        WebViewAssetLoader.Builder()
            .setDomain(ReaderAssets.HOST)
            .addPathHandler(ReaderAssets.READER_PATH, ReaderAssetPathHandler(context))
            .build()
}
