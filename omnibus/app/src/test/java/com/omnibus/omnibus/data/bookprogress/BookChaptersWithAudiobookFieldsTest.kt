package com.omnibus.omnibus.data.bookprogress

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.progress.TestProgressRepository
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest

private const val UUID = "book-1"
private const val ALIGNMENT = "/api/books/book-1/alignment"
private const val MANIFEST = "/api/audiobooks/book-1/manifest"
private const val ALIGNMENT_ONE_FILE = """{"audio_files":[{"book_file_id":7,"duration_seconds":1000.0,"chapter_starts":[0.0,250.0]}]}"""

/** A manifest carrying the `parts` and `playlist_url` fields the player reads. */
private const val MANIFEST_WITH_PARTS = """{"mode":"direct","book_file_id":7,"total_duration_seconds":1000.0,"chapters":[
    {"title":"","start_seconds":0.0,"duration_seconds":250.0},{"title":"","start_seconds":250.0,"duration_seconds":750.0}],
    "parts":[{"ordinal":1,"url":"/api/audiobooks/book-1/parts/1?file_id=7","duration_seconds":1000.0,"mime":"audio/mpeg"}],
    "playlist_url":"/api/audiobooks/book-1/playlist.m3u8"}"""

private fun reply(body: String): MockResponse = MockResponse.Builder().code(200).body(body).build()

/** The book-progress chapters read does not change when the manifest carries the player's extra fields. */
class BookChaptersWithAudiobookFieldsTest : DescribeSpec({

    lateinit var server: MockWebServer

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                    ALIGNMENT -> reply(ALIGNMENT_ONE_FILE)
                    MANIFEST -> reply(MANIFEST_WITH_PARTS)
                    else -> MockResponse.Builder().code(500).build()
                }
            }
            start()
        }
    }

    afterEach { server.close() }

    // TC-20
    it("getChapters ignores the added manifest fields (AND-RUSE-1)") {
        val repository = DefaultBookProgressRepository(
            ioDispatcher = Dispatchers.Unconfined,
            omnibusHttpClient = OmnibusHttpClient(
                baseUrlProvider = TestServerBaseUrlProvider(baseUrl = server.url("/")),
                authTokenProvider = TestAuthTokenProvider(token = "secret"),
                omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
            ),
            progressRepository = TestProgressRepository(),
        )

        repository.getChapters(UUID) shouldBe BookChaptersResult.Success(
            ebookChapters = emptyList(),
            audioChapters = listOf(BookChapter(0, null, 0f), BookChapter(1, null, .25f)),
            audioFileId = 7L,
            audioTotalSeconds = 1000L,
        )
    }
})
