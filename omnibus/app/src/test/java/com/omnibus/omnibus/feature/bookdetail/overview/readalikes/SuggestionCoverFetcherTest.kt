package com.omnibus.omnibus.feature.bookdetail.overview.readalikes

import android.content.Context
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.omnibus.omnibus.data.suggestions.SuggestionCoverResult
import com.omnibus.omnibus.data.suggestions.TestSuggestionsRepository
import com.omnibus.omnibus.testing.randomString
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import io.mockk.mockk
import java.io.IOException

class SuggestionCoverFetcherTest : DescribeSpec({
    fun sut(result: SuggestionCoverResult): Fetcher =
        SuggestionCoverFetcher.Factory(TestSuggestionsRepository().also { it.coverResult = result }).create(
            SuggestionCoverRequest(randomString()),
            Options(mockk<Context>(relaxed = true)),
            mockk(relaxed = true),
        )

    describe("fetching a suggestion's cover") {
        context("when there is no cover to give") {
            it("#106 TC-51: throws IOException so the plate stays visible, for a missing or a failed cover") {
                checkAll(Exhaustive.of(SuggestionCoverResult.NotFound, SuggestionCoverResult.Failure)) { result ->
                    shouldThrow<IOException> { sut(result).fetch() }
                }
            }
        }

        context("when the cover is found") {
            it("#106 TC-52: gives its bytes with its mime type") {
                val art = randomString()

                val result = sut(SuggestionCoverResult.Found(art.encodeToByteArray(), "image/png"))
                    .fetch().shouldBeInstanceOf<SourceFetchResult>()

                result.mimeType shouldBe "image/png"
                result.source.source().readUtf8() shouldBe art
            }
        }
    }
})
