package com.omnibus.omnibus.feature.search

import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.search.LibrarySearchResults
import com.omnibus.omnibus.data.search.PaletteSearchResult
import com.omnibus.omnibus.data.search.SearchMatches
import com.omnibus.omnibus.data.search.TestLibrarySearchRepository
import com.omnibus.omnibus.data.search.randomBookMatches
import com.omnibus.omnibus.data.search.randomAuthorMatch
import com.omnibus.omnibus.data.search.randomAuthorMatches
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

class SearchAuthorsTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(StandardTestDispatcher()) }

    afterSpec { Dispatchers.resetMain() }

    describe("listing the sections of an answer") {

        it("#354 TC-4: lists Books, Authors, then Tags, the spec order") {
            val results = LibrarySearchResults(
                books = randomBookMatches(),
                authors = randomAuthorMatches(),
                tags = randomTagMatches(),
            )

            results.sections().map { it.kind } shouldBe
                listOf(SearchSectionKind.Books, SearchSectionKind.Authors, SearchSectionKind.Tags)
        }

        it("#354 TC-4: gives the Authors section the authors it was answered with") {
            val authors = randomAuthorMatches()
            val results = LibrarySearchResults(books = randomBookMatches(), authors = authors)

            results.sections().last().matches shouldBe authors
        }

        it("#354 TC-5: has no Authors section when there are no authors") {
            val results = LibrarySearchResults(books = randomBookMatches(), authors = SearchMatches.empty())

            results.sections().map { it.kind } shouldBe listOf(SearchSectionKind.Books)
        }
    }

    describe("answering a search with authors only") {
        it("#354 TC-6: shows the Authors section as the results, not as no matches") {
            val authors = randomAuthorMatches()
            val repo = TestLibrarySearchRepository().apply {
                answerLibrary(
                    PaletteSearchResult.Success(LibrarySearchResults(books = SearchMatches.empty(), authors = authors)),
                )
            }

            runTest {
                val vm = SearchViewModel(repo)
                try {
                    vm.updateQuery("her")
                    advanceUntilIdle()

                    vm.uiState.value.body shouldBe
                        SearchBody.Results("her", listOf(SearchSection(SearchSectionKind.Authors, authors)))
                } finally {
                    vm.viewModelScope.cancel()
                }
            }
        }
    }

    describe("opening the Authors See all list") {
        it("#354 TC-7: lists the first 500 authors with the section's total") {
            val authors = List(501) { randomAuthorMatch() }
            val repo = TestLibrarySearchRepository().apply {
                answerLibrary(
                    PaletteSearchResult.Success(
                        LibrarySearchResults(books = SearchMatches.empty(), authors = SearchMatches(authors, total = 700)),
                    ),
                )
            }

            runTest {
                val vm = SearchSeeAllViewModel(SearchSeeAllRoute(SearchSectionKind.Authors, "a"), repo)
                try {
                    runCurrent()

                    vm.uiState.value.body shouldBe SeeAllBody.Loaded(SearchMatches(authors.take(500), total = 700))
                } finally {
                    vm.viewModelScope.cancel()
                }
            }
        }
    }
})
