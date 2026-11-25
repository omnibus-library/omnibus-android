package com.omnibus.omnibus.feature.search

import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.search.LibrarySearchResults
import com.omnibus.omnibus.data.search.PaletteSearchResult
import com.omnibus.omnibus.data.search.SearchMatches
import com.omnibus.omnibus.data.search.TestLibrarySearchRepository
import com.omnibus.omnibus.data.search.randomBookMatches
import com.omnibus.omnibus.data.search.randomGenreMatch
import com.omnibus.omnibus.data.search.randomGenreMatches
import com.omnibus.omnibus.data.search.randomTagMatches
import com.omnibus.omnibus.feature.search.seeall.SearchSeeAllRoute
import com.omnibus.omnibus.feature.search.seeall.SearchSeeAllViewModel
import com.omnibus.omnibus.feature.search.seeall.SeeAllBody
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

class SearchGenresTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(StandardTestDispatcher()) }

    afterSpec { Dispatchers.resetMain() }

    describe("listing the sections of an answer") {

        it("#353 TC-4: lists Books, Tags, then Genres, the spec order") {
            val results = LibrarySearchResults(
                books = randomBookMatches(),
                tags = randomTagMatches(),
                genres = randomGenreMatches(),
            )

            results.sections().map { it.kind } shouldBe
                listOf(SearchSectionKind.Books, SearchSectionKind.Tags, SearchSectionKind.Genres)
        }

        it("#353 TC-4: gives the Genres section the genres it was answered with") {
            val genres = randomGenreMatches()
            val results = LibrarySearchResults(books = randomBookMatches(), genres = genres)

            results.sections().last().matches shouldBe genres
        }

        it("#353 TC-5: has no Genres section when there are no genres") {
            val results = LibrarySearchResults(books = randomBookMatches(), genres = SearchMatches.empty())

            results.sections().map { it.kind } shouldBe listOf(SearchSectionKind.Books)
        }
    }

    describe("answering a search with genres only") {
        it("#353 TC-6: shows the Genres section as the results, not as no matches") {
            val genres = randomGenreMatches()
            val repo = TestLibrarySearchRepository().apply {
                answerLibrary(
                    PaletteSearchResult.Success(LibrarySearchResults(books = SearchMatches.empty(), genres = genres)),
                )
            }

            runTest {
                val vm = SearchViewModel(repo)
                try {
                    vm.updateQuery("fan")
                    advanceUntilIdle()

                    vm.uiState.value.body shouldBe
                        SearchBody.Results("fan", listOf(SearchSection(SearchSectionKind.Genres, genres)))
                } finally {
                    vm.viewModelScope.cancel()
                }
            }
        }
    }

    describe("opening the Genres See all list") {
        it("#353 TC-7: lists the first 500 genres with the section's total") {
            val genres = List(501) { randomGenreMatch() }
            val repo = TestLibrarySearchRepository().apply {
                answerLibrary(
                    PaletteSearchResult.Success(
                        LibrarySearchResults(books = SearchMatches.empty(), genres = SearchMatches(genres, total = 700)),
                    ),
                )
            }

            runTest {
                val vm = SearchSeeAllViewModel(SearchSeeAllRoute(SearchSectionKind.Genres, "a"), repo)
                try {
                    runCurrent()

                    vm.uiState.value.body shouldBe SeeAllBody.Loaded(SearchMatches(genres.take(500), total = 700))
                } finally {
                    vm.viewModelScope.cancel()
                }
            }
        }
    }
})
