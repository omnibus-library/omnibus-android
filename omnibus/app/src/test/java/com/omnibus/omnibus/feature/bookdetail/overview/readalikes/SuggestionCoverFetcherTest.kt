package com.omnibus.omnibus.feature.bookdetail.overview.readalikes

import android.content.Context
import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.omnibus.omnibus.data.suggestions.FakeSuggestionsRepository
import com.omnibus.omnibus.data.suggestions.SuggestionCoverResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import java.io.IOException

class SuggestionCoverFetcherTest : DescribeSpec({

    val path = "/api/suggestions/uuid-1/1/cover"
    val request = SuggestionCoverRequest(path)
    val options = Options(mockk<Context>(relaxed = true))

    fun fetcher(repository: FakeSuggestionsRepository) =
        SuggestionCoverFetcher.Factory(repository).create(request, options, mockk(relaxed = true))

    describe("SuggestionCoverFetcher") {

        listOf(
            "a missing cover" to SuggestionCoverResult.NotFound,
            "a failed cover" to SuggestionCoverResult.Failure,
        ).forEach { (name, result) ->
            it("#106 TC-51: $name throws IOException, so the plate stays visible") {
                val repository = FakeSuggestionsRepository().also { it.coverResult = result }

                shouldThrow<IOException> { fetcher(repository).fetch() }

                repository.coverPaths shouldContainExactly listOf(path)
            }
        }

        it("#106 TC-52: a found cover gives a network source result with its mime type") {
            val repository = FakeSuggestionsRepository().also {
                it.coverResult = SuggestionCoverResult.Found("art".encodeToByteArray(), "image/png")
            }

            val result = fetcher(repository).fetch().shouldBeInstanceOf<SourceFetchResult>()

            result.mimeType shouldBe "image/png"
            result.dataSource shouldBe DataSource.NETWORK
            result.source.source().readUtf8() shouldBe "art"
        }
    }
})
