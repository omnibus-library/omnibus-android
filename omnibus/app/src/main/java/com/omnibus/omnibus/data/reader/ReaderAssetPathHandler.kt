package com.omnibus.omnibus.data.reader

import android.content.Context
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Serves the bundled reader files from `assets/reader/` with the MIME types epub.js needs.
 *
 * Used in place of [WebViewAssetLoader.AssetsPathHandler], which takes its MIME types from
 * Android's extension guess. An unknown file is answered with a 404 rather than null, because a
 * null falls through to the network, which for this origin is a request that can only hang.
 */
class ReaderAssetPathHandler(private val context: Context) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse {
        val assetPath = ReaderAssets.assetPathFor(path) ?: return notFound()
        val stream = try {
            context.assets.open(assetPath)
        } catch (_: FileNotFoundException) {
            return notFound()
        } catch (_: IOException) {
            return notFound()
        }
        return WebResourceResponse(ReaderAssets.mimeTypeFor(assetPath), UTF_8, stream)
    }

    private fun notFound() =
        WebResourceResponse("text/plain", UTF_8, 404, "Not Found", emptyMap(), "".byteInputStream())

    private companion object {
        const val UTF_8 = "utf-8"
    }
}
