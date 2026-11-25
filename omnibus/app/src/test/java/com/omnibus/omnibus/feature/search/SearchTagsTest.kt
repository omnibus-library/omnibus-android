package com.omnibus.omnibus.feature.search

import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.search.LibrarySearchResults
import com.omnibus.omnibus.data.search.PaletteSearchResult
import com.omnibus.omnibus.data.search.SearchMatches
import com.omnibus.omnibus.data.search.TestLibrarySearchRepository
import com.omnibus.omnibus.data.search.randomBookMatches
import com.omnibus.omnibus.data.search.randomTagMatch
import com.omnibus.omnibus.data.search.randomTagMatches
import com.omnibus.omnibus.feature.search.seeall.SearchSeeAllRoute
import com.omnibus.omnibus.feature.search.seeall.SearchSeeAllViewModel
import com.omnibus.omnibus.feature.search.seeall.SeeAllBody
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Runs one Search case on virtual time, after the answers are scripted. The ViewModel's scope is cancelled afterwards. */
private fun runCase(
    repo: TestLibrarySearchRepository,
    block: suspend TestScope.(SearchViewModel) -> Unit,
) = runTest {
    val vm = SearchViewModel(repo)
    try {
        block(vm)
    } finally {
        vm.viewModelScope.cancel()
    }
}

/** Opens the See all list for [route] on virtual time, after the answers are scripted. */
private fun runSeeAll(
    repo: TestLibrarySearchRepository,
    route: SearchSeeAllRoute,
    block: suspend TestScope.(SearchSeeAllViewModel) -> Unit,
) = runTest {
    val vm = SearchSeeAllViewModel(route, repo)
    try {
        runCurrent()
        block(vm)
    } finally {
        vm.viewModelScope.cancel()
    }
}

class SearchTagsTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(StandardTestDispatcher()) }

    afterSpec { Dispatchers.resetMain() }

    describe("listing the sections of an answer") {

        context("when the answer has books and tags") {
            it("#352 TC-8: lists the Books section, then the Tags section") {
                val results = LibrarySearchResults(books = randomBookMatches(), tags = randomTagMatches())

                results.sections().map { it.kind } shouldBe listOf(SearchSectionKind.Books, SearchSectionKind.Tags)
            }

            it("#352 TC-8: gives the Tags section the tags it was answered with") {
                val tags = randomTagMatches()
                val results = LibrarySearchResults(books = randomBookMatches(), tags = tags)

                results.sections().last().matches shouldBe tags
            }
        }

        context("when the answer has books and no tags") {
            it("#352 TC-10: has no Tags section") {
                val results = LibrarySearchResults(books = randomBookMatches(), tags = SearchMatches.empty())

                results.sections().map { it.kind } shouldBe listOf(SearchSectionKind.Books)
            }
        }
    }

    describe("answering a search with tags only") {

        context("when the answer has tags and no books") {
            it("#352 TC-9: shows the Tags section as the results, not as no matches") {
                val tags = randomTagMatches()
                val repo = TestLibrarySearchRepository().apply {
                    answerLibrary(
                        PaletteSearchResult.Success(LibrarySearchResults(books = SearchMatches.empty(), tags = tags)),
                    )
                }

                runCase(repo) { vm ->
                    vm.updateQuery("wood")
                    advanceUntilIdle()

                    vm.uiState.value.body shouldBe
                        SearchBody.Results("wood", listOf(SearchSection(SearchSectionKind.Tags, tags)))
                }
            }
        }
    }

    describe("opening the Tags See all list") {

        context("when the section has more tags than the cap") {
            it("#352 TC-12: lists the first 500 tags with the section's total") {
                val tags = List(501) { randomTagMatch() }
                val repo = TestLibrarySearchRepository().apply {
                    answerLibrary(
                        PaletteSearchResult.Success(
                            LibrarySearchResults(books = SearchMatches.empty(), tags = SearchMatches(tags, total = 812)),
                        ),
                    )
                }

                runSeeAll(repo, SearchSeeAllRoute(SearchSectionKind.Tags, "a")) { vm ->
                    vm.uiState.value.body shouldBe SeeAllBody.Loaded(SearchMatches(tags.take(500), total = 812))
                }
            }
        }
    }
})
