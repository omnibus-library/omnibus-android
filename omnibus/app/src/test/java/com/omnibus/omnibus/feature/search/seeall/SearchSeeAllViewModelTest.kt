package com.omnibus.omnibus.feature.search.seeall

import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.requests.randomLibraryHit
import com.omnibus.omnibus.data.search.LibrarySearchError
import com.omnibus.omnibus.data.search.LibrarySearchResults
import com.omnibus.omnibus.data.search.PaletteSearchResult
import com.omnibus.omnibus.data.search.SearchLibraryCall
import com.omnibus.omnibus.data.search.SearchMatches
import com.omnibus.omnibus.data.search.TestLibrarySearchRepository
import com.omnibus.omnibus.data.search.randomLibrarySearchResults
import com.omnibus.omnibus.feature.search.SearchFailure
import com.omnibus.omnibus.feature.search.SearchSectionKind
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Opens the See all list for [route] on virtual time, after the answers are scripted. The ViewModel's scope is cancelled
 * afterwards, so a load still waiting does not outlive the case.
 */
private fun runCase(
    repo: TestLibrarySearchRepository,
    route: SearchSeeAllRoute = SearchSeeAllRoute(SearchSectionKind.Books, randomString()),
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

class SearchSeeAllViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(StandardTestDispatcher()) }

    afterSpec { Dispatchers.resetMain() }

    describe("opening the list") {

        it("#351 TC-40: asks the server once for the query with the cap of 500 rows") {
            val repo = TestLibrarySearchRepository().apply {
                answerLibrary(PaletteSearchResult.Success(randomLibrarySearchResults()))
            }

            runCase(repo, SearchSeeAllRoute(SearchSectionKind.Books, "wood")) {

                repo.libraryCalls shouldBe listOf(SearchLibraryCall("wood", 500))
            }
        }

        it("#351 TC-40: lists the first 500 matches with the section's total") {
            val hits = List(501) { randomLibraryHit() }
            val repo = TestLibrarySearchRepository().apply {
                answerLibrary(
                    PaletteSearchResult.Success(
                        LibrarySearchResults(books = SearchMatches(hits, total = 900)),
                    ),
                )
            }

            runCase(repo) { vm ->

                vm.uiState.value.body shouldBe SeeAllBody.Loaded(SearchMatches(hits.take(500), total = 900))
            }
        }
    }

    describe("answering") {

        it("#351 TC-41: shows no matches when the section has no items") {
            val repo = TestLibrarySearchRepository().apply {
                answerLibrary(PaletteSearchResult.Success(LibrarySearchResults(books = SearchMatches.empty())))
            }

            runCase(repo) { vm ->

                vm.uiState.value.body shouldBe SeeAllBody.NoMatches
            }
        }

        it("#351 TC-41: shows the unreachable state when the server cannot be reached") {
            val repo = TestLibrarySearchRepository().apply {
                answerLibrary(PaletteSearchResult.Failure(LibrarySearchError.Unreachable))
            }

            runCase(repo) { vm ->

                vm.uiState.value.body shouldBe SeeAllBody.Failed(SearchFailure.Unreachable)
            }
        }

        it("#351 TC-41: shows the not-working state for a server error or an unreadable answer") {
            checkAll(
                Exhaustive.of(
                    LibrarySearchError.Http(500),
                    LibrarySearchError.Http(429),
                    LibrarySearchError.UnexpectedResponse,
                ),
            ) { error ->
                val repo = TestLibrarySearchRepository().apply {
                    answerLibrary(PaletteSearchResult.Failure(error))
                }

                runCase(repo) { vm ->

                    vm.uiState.value.body shouldBe SeeAllBody.Failed(SearchFailure.NotWorking)
                }
            }
        }

        it("#351 TC-41: shows nothing when the session ended or no server is saved") {
            checkAll(
                Exhaustive.of(
                    PaletteSearchResult.Failure(LibrarySearchError.SessionExpired),
                    PaletteSearchResult.NoServer,
                ),
            ) { ending ->
                val repo = TestLibrarySearchRepository().apply { answerLibrary(ending) }

                runCase(repo) { vm ->

                    vm.uiState.value.body shouldBe SeeAllBody.Ended
                }
            }
        }
    }

    describe("retrying") {

        it("#351 TC-41: shows the loading state again at once") {
            val repo = TestLibrarySearchRepository().apply {
                answerLibrary(PaletteSearchResult.Failure(LibrarySearchError.Unreachable))
                answerLibrary(PaletteSearchResult.Success(randomLibrarySearchResults()))
            }

            runCase(repo) { vm ->

                vm.retry()

                vm.uiState.value.body shouldBe SeeAllBody.Loading
            }
        }

        it("#351 TC-41: asks the server a second time") {
            val repo = TestLibrarySearchRepository().apply {
                answerLibrary(PaletteSearchResult.Failure(LibrarySearchError.Unreachable))
                answerLibrary(PaletteSearchResult.Success(randomLibrarySearchResults()))
            }

            runCase(repo, SearchSeeAllRoute(SearchSectionKind.Books, "wood")) { vm ->

                vm.retry()
                runCurrent()

                repo.libraryCalls shouldBe listOf(SearchLibraryCall("wood", 500), SearchLibraryCall("wood", 500))
            }
        }
    }
})
