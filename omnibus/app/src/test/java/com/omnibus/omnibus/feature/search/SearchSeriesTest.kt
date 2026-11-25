package com.omnibus.omnibus.feature.search

import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.search.LibrarySearchResults
import com.omnibus.omnibus.data.search.PaletteSearchResult
import com.omnibus.omnibus.data.search.SearchMatches
import com.omnibus.omnibus.data.search.TestLibrarySearchRepository
import com.omnibus.omnibus.data.search.randomBookMatches
import com.omnibus.omnibus.data.search.randomSeriesMatch
import com.omnibus.omnibus.data.search.randomSeriesMatches
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

class SearchSeriesTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(StandardTestDispatcher()) }

    afterSpec { Dispatchers.resetMain() }

    describe("listing the sections of an answer") {

        it("#355 TC-4: lists Books, Series, then Tags, the spec order") {
            val results = LibrarySearchResults(
                books = randomBookMatches(),
                series = randomSeriesMatches(),
                tags = randomTagMatches(),
            )

            results.sections().map { it.kind } shouldBe
                listOf(SearchSectionKind.Books, SearchSectionKind.Series, SearchSectionKind.Tags)
        }

        it("#355 TC-4: gives the Series section the series it was answered with") {
            val series = randomSeriesMatches()
            val results = LibrarySearchResults(books = randomBookMatches(), series = series)

            results.sections().last().matches shouldBe series
        }

        it("#355 TC-5: has no Series section when there are no series") {
            val results = LibrarySearchResults(books = randomBookMatches(), series = SearchMatches.empty())

            results.sections().map { it.kind } shouldBe listOf(SearchSectionKind.Books)
        }
    }

    describe("answering a search with series only") {
        it("#355 TC-6: shows the Series section as the results, not as no matches") {
            val series = randomSeriesMatches()
            val repo = TestLibrarySearchRepository().apply {
                answerLibrary(
                    PaletteSearchResult.Success(LibrarySearchResults(books = SearchMatches.empty(), series = series)),
                )
            }

            runTest {
                val vm = SearchViewModel(repo)
                try {
                    vm.updateQuery("dun")
                    advanceUntilIdle()

                    vm.uiState.value.body shouldBe
                        SearchBody.Results("dun", listOf(SearchSection(SearchSectionKind.Series, series)))
                } finally {
                    vm.viewModelScope.cancel()
                }
            }
        }
    }

    describe("opening the Series See all list") {
        it("#355 TC-7: lists the first 500 series with the section's total") {
            val series = List(501) { randomSeriesMatch() }
            val repo = TestLibrarySearchRepository().apply {
                answerLibrary(
                    PaletteSearchResult.Success(
                        LibrarySearchResults(books = SearchMatches.empty(), series = SearchMatches(series, total = 700)),
                    ),
                )
            }

            runTest {
                val vm = SearchSeeAllViewModel(SearchSeeAllRoute(SearchSectionKind.Series, "a"), repo)
                try {
                    runCurrent()

                    vm.uiState.value.body shouldBe SeeAllBody.Loaded(SearchMatches(series.take(500), total = 700))
                } finally {
                    vm.viewModelScope.cancel()
                }
            }
        }
    }
})
