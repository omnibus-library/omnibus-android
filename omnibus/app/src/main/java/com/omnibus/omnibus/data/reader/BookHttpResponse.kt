package com.omnibus.omnibus.data.reader

import java.io.InputStream

/**
 * What the `/book/` path handler answers epub.js with: [BookFileResult] expressed as an HTTP
 * response. Failures become real status codes, so epub.js sees a failed request, not a blank body.
 */
class BookHttpResponse(
    val status: Int,
    val reason: String,
    val headers: Map<String, String>,
    val body: InputStream?,
) {
    companion object {
        const val EPUB_MIME_TYPE = "application/epub+zip"

        fun of(result: BookFileResult): BookHttpResponse =
            when (result) {
                is BookFileResult.Opened -> BookHttpResponse(
                    status = 200,
                    reason = "OK",
                    headers = buildMap {
                        put("Access-Control-Allow-Origin", "*")
                        if (result.length >= 0) put("Content-Length", result.length.toString())
                    },
                    body = result.stream,
                )
                BookFileResult.Unauthenticated -> failure(401, "Unauthorized")
                BookFileResult.NotFound -> failure(404, "Not Found")
                BookFileResult.NoServer -> failure(503, "No Server")
                BookFileResult.Error -> failure(502, "Bad Gateway")
            }

        private fun failure(status: Int, reason: String) =
            BookHttpResponse(status, reason, mapOf("Access-Control-Allow-Origin" to "*"), body = null)
    }
}
