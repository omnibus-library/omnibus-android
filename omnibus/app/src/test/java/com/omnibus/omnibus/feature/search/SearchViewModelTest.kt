package com.omnibus.omnibus.feature.search

import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.search.LibrarySearchError
import com.omnibus.omnibus.data.search.LibrarySearchResults
import com.omnibus.omnibus.data.search.PaletteSearchResult
import com.omnibus.omnibus.data.search.SearchMatches
import com.omnibus.omnibus.data.search.TestLibrarySearchRepository
import com.omnibus.omnibus.data.search.randomLibrarySearchResults
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Runs one case on virtual time, on the spec's Main scheduler, so time moves only when a case advances it. The ViewModel's
 * scope is cancelled afterwards, so a search still waiting does not outlive the case.
 */
private fun runCase(
    repo: TestLibrarySearchRepository = TestLibrarySearchRepository(),
    block: suspend TestScope.(SearchViewModel) -> Unit,
) = runTest {
    val vm = SearchViewModel(repo)
    try {
        block(vm)
    } finally {
        vm.viewModelScope.cancel()
    }
}

/** Advances past the debounce, then runs the search it started. */
private fun TestScope.passDebounce() {
    advanceTimeBy(SEARCH_DEBOUNCE_MILLIS)
    runCurrent()
}

class SearchViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(StandardTestDispatcher()) }

    afterSpec { Dispatchers.resetMain() }

    describe("debouncing a search") {

        it("#351 TC-6: searches 200ms after the last change") {
            val repo = TestLibrarySearchRepository().apply { answerLibrary(PaletteSearchResult.Success(randomLibrarySearchResults())) }

            runCase(repo) { vm ->
                vm.updateQuery("wood")
                advanceTimeBy(SEARCH_DEBOUNCE_MILLIS - 1)
                runCurrent()
                repo.libraryCalls shouldBe emptyList()

                advanceTimeBy(1)
                runCurrent()
                repo.libraryCalls shouldBe listOf("wood")
            }
        }

        it("#351 TC-6: restarts the wait on each change, so only the last query is sent") {
            val repo = TestLibrarySearchRepository().apply { answerLibrary(PaletteSearchResult.Success(randomLibrarySearchResults())) }

            runCase(repo) { vm ->
                vm.updateQuery("wo")
                advanceTimeBy(150)
                runCurrent()
                vm.updateQuery("wood")
                passDebounce()

                repo.libraryCalls shouldBe listOf("wood")
            }
        }
    }

    describe("typing a space after the query") {

        it("#351 TC-7: sends no second search") {
            val repo = TestLibrarySearchRepository().apply { answerLibrary(PaletteSearchResult.Success(randomLibrarySearchResults())) }

            runCase(repo) { vm ->
                vm.updateQuery("wood")
                passDebounce()
                vm.updateQuery("wood ")
                passDebounce()

                repo.libraryCalls shouldBe listOf("wood")
            }
        }

        it("#351 TC-7: keeps the body of the answer already shown") {
            val repo = TestLibrarySearchRepository().apply { answerLibrary(PaletteSearchResult.Success(randomLibrarySearchResults())) }

            runCase(repo) { vm ->
                vm.updateQuery("wood")
                passDebounce()
                val shown = vm.uiState.value.body
                vm.updateQuery("wood ")
                passDebounce()

                vm.uiState.value.body shouldBe shown
            }
        }
    }

    describe("a blank query") {

        it("#351 TC-8: sends nothing for a query of only spaces, or none") {
            checkAll(Exhaustive.of("", " ", "   ")) { blank ->
                val repo = TestLibrarySearchRepository()

                runCase(repo) { vm ->
                    vm.updateQuery(blank)
                    passDebounce()

                    repo.libraryCalls shouldBe emptyList()
                }
            }
        }

        it("#351 TC-8: shows the Prompt for a query of only spaces, or none") {
            checkAll(Exhaustive.of("", " ", "   ")) { blank ->
                runCase { vm ->
                    vm.updateQuery(blank)
                    passDebounce()

                    vm.uiState.value.body shouldBe SearchBody.Prompt
                }
            }
        }

        it("#351 TC-8: never sends a search that was pending when the query was cleared") {
            val repo = TestLibrarySearchRepository()

            runCase(repo) { vm ->
                vm.updateQuery("wood")
                vm.clearQuery()
                passDebounce()

                repo.libraryCalls shouldBe emptyList()
                vm.uiState.value.body shouldBe SearchBody.Prompt
            }
        }
    }

    describe("a late answer") {

        it("#351 TC-9: shows the newer query's answer when the older one arrives last") {
            val repo = TestLibrarySearchRepository()
            val older = repo.holdLibrary()
            val newer = repo.holdLibrary()
            val newerResults = randomLibrarySearchResults()

            runCase(repo) { vm ->
                vm.updateQuery("wo")
                passDebounce()
                vm.updateQuery("wood")
                passDebounce()

                newer.complete(PaletteSearchResult.Success(newerResults))
                runCurrent()
                older.complete(PaletteSearchResult.Success(randomLibrarySearchResults()))
                runCurrent()

                vm.uiState.value.body shouldBe SearchBody.Results("wood", newerResults.sections())
            }
        }
    }

    describe("searching") {

        context("when nothing is on screen") {

            it("#351 TC-10: shows the Searching state while the answer is pending") {
                val repo = TestLibrarySearchRepository().apply { holdLibrary() }

                runCase(repo) { vm ->
                    vm.updateQuery("wood")
                    passDebounce()

                    vm.uiState.value.body shouldBe SearchBody.Searching
                }
            }
        }

        context("when results are on screen") {

            it("#351 TC-10: keeps the results until the newer answer arrives") {
                val results = randomLibrarySearchResults()
                val repo = TestLibrarySearchRepository().apply {
                    answerLibrary(PaletteSearchResult.Success(results))
                    holdLibrary()
                }

                runCase(repo) { vm ->
                    vm.updateQuery("wood")
                    passDebounce()
                    vm.updateQuery("woods")
                    passDebounce()

                    vm.uiState.value.body shouldBe SearchBody.Results("wood", results.sections())
                }
            }
        }
    }

    describe("answering a search") {

        it("#351 TC-11: shows the sections of the answer, under the trimmed query") {
            val results = randomLibrarySearchResults()
            val repo = TestLibrarySearchRepository().apply { answerLibrary(PaletteSearchResult.Success(results)) }

            runCase(repo) { vm ->
                vm.updateQuery("  wood ")
                passDebounce()

                vm.uiState.value.body shouldBe
                    SearchBody.Results("wood", listOf(SearchSection(SearchSectionKind.Books, results.books)))
            }
        }

        it("#351 TC-11: shows no matches when the answer has no items") {
            val repo = TestLibrarySearchRepository().apply {
                answerLibrary(PaletteSearchResult.Success(LibrarySearchResults(SearchMatches.empty())))
            }

            runCase(repo) { vm ->
                vm.updateQuery("  wood ")
                passDebounce()

                vm.uiState.value.body shouldBe SearchBody.NoMatches("wood")
            }
        }

        it("#351 TC-11: keeps the typed text, untrimmed, in the field") {
            val repo = TestLibrarySearchRepository().apply {
                answerLibrary(PaletteSearchResult.Success(LibrarySearchResults(SearchMatches.empty())))
            }

            runCase(repo) { vm ->
                vm.updateQuery("  wood ")
                passDebounce()

                vm.uiState.value.query shouldBe "  wood "
            }
        }
    }

    describe("a failed search") {

        context("when nothing is on screen") {

            it("#351 TC-12: shows the unreachable state when the server cannot be reached") {
                val repo = TestLibrarySearchRepository().apply {
                    answerLibrary(PaletteSearchResult.Failure(LibrarySearchError.Unreachable))
                }

                runCase(repo) { vm ->
                    vm.updateQuery("wood")
                    passDebounce()

                    vm.uiState.value.body shouldBe SearchBody.Failed(SearchFailure.Unreachable)
                }
            }

            it("#351 TC-12: shows the not-working state for a server error or an unreadable answer") {
                checkAll(
                    Exhaustive.of(
                        LibrarySearchError.Http(500),
                        LibrarySearchError.Http(429),
                        LibrarySearchError.UnexpectedResponse,
                    ),
                ) { error ->
                    val repo = TestLibrarySearchRepository().apply { answerLibrary(PaletteSearchResult.Failure(error)) }

                    runCase(repo) { vm ->
                        vm.updateQuery("wood")
                        passDebounce()

                        vm.uiState.value.body shouldBe SearchBody.Failed(SearchFailure.NotWorking)
                    }
                }
            }

            it("#351 TC-12: keeps the typed text in the field") {
                val repo = TestLibrarySearchRepository().apply {
                    answerLibrary(PaletteSearchResult.Failure(LibrarySearchError.Unreachable))
                }

                runCase(repo) { vm ->
                    vm.updateQuery("  wood ")
                    passDebounce()

                    vm.uiState.value.query shouldBe "  wood "
                }
            }
        }

        context("when results are on screen") {

            it("#351 TC-14: keeps the results on screen") {
                val results = randomLibrarySearchResults()
                val repo = TestLibrarySearchRepository().apply {
                    answerLibrary(PaletteSearchResult.Success(results))
                    answerLibrary(PaletteSearchResult.Failure(LibrarySearchError.Unreachable))
                }

                runCase(repo) { vm ->
                    vm.updateQuery("wood")
                    passDebounce()
                    vm.updateQuery("woods")
                    passDebounce()

                    vm.uiState.value.body shouldBe SearchBody.Results("wood", results.sections())
                }
            }

            it("#351 TC-14: raises a notice with the failure") {
                val repo = TestLibrarySearchRepository().apply {
                    answerLibrary(PaletteSearchResult.Success(randomLibrarySearchResults()))
                    answerLibrary(PaletteSearchResult.Failure(LibrarySearchError.Unreachable))
                }

                runCase(repo) { vm ->
                    vm.updateQuery("wood")
                    passDebounce()
                    vm.updateQuery("woods")
                    passDebounce()

                    vm.uiState.value.notice?.failure shouldBe SearchFailure.Unreachable
                }
            }

            it("#351 TC-14: clears the notice once it is shown") {
                val repo = TestLibrarySearchRepository().apply {
                    answerLibrary(PaletteSearchResult.Success(randomLibrarySearchResults()))
                    answerLibrary(PaletteSearchResult.Failure(LibrarySearchError.Unreachable))
                }

                runCase(repo) { vm ->
                    vm.updateQuery("wood")
                    passDebounce()
                    vm.updateQuery("woods")
                    passDebounce()
                    vm.noticeShown()

                    vm.uiState.value.notice.shouldBeNull()
                }
            }

            it("#351 TC-14: clears the notice when a later search answers") {
                val repo = TestLibrarySearchRepository().apply {
                    answerLibrary(PaletteSearchResult.Success(randomLibrarySearchResults()))
                    answerLibrary(PaletteSearchResult.Failure(LibrarySearchError.Unreachable))
                    answerLibrary(PaletteSearchResult.Success(randomLibrarySearchResults()))
                }

                runCase(repo) { vm ->
                    vm.updateQuery("wood")
                    passDebounce()
                    vm.updateQuery("woods")
                    passDebounce()
                    vm.updateQuery("woodsy")
                    passDebounce()

                    vm.uiState.value.notice.shouldBeNull()
                }
            }
        }
    }

    describe("retrying") {

        it("#351 TC-13: searches the current query again at once, without the debounce") {
            val repo = TestLibrarySearchRepository().apply {
                answerLibrary(PaletteSearchResult.Failure(LibrarySearchError.Unreachable))
                answerLibrary(PaletteSearchResult.Success(randomLibrarySearchResults()))
            }

            runCase(repo) { vm ->
                vm.updateQuery("wood")
                passDebounce()
                vm.retry()
                runCurrent()

                repo.libraryCalls shouldBe listOf("wood", "wood")
            }
        }
    }

    describe("a search that ends the session") {

        val endings = Exhaustive.of(
            PaletteSearchResult.Failure(LibrarySearchError.SessionExpired),
            PaletteSearchResult.NoServer,
        )

        context("when nothing is on screen") {

            it("#351 TC-15: shows nothing in the body") {
                checkAll(endings) { ending ->
                    val repo = TestLibrarySearchRepository().apply { answerLibrary(ending) }

                    runCase(repo) { vm ->
                        vm.updateQuery("wood")
                        passDebounce()

                        vm.uiState.value.body shouldBe SearchBody.Ended
                    }
                }
            }

            it("#351 TC-15: raises no notice") {
                checkAll(endings) { ending ->
                    val repo = TestLibrarySearchRepository().apply { answerLibrary(ending) }

                    runCase(repo) { vm ->
                        vm.updateQuery("wood")
                        passDebounce()

                        vm.uiState.value.notice.shouldBeNull()
                    }
                }
            }
        }

        context("when results are on screen") {

            it("#351 TC-15: keeps the results") {
                checkAll(endings) { ending ->
                    val results = randomLibrarySearchResults()
                    val repo = TestLibrarySearchRepository().apply {
                        answerLibrary(PaletteSearchResult.Success(results))
                        answerLibrary(ending)
                    }

                    runCase(repo) { vm ->
                        vm.updateQuery("wood")
                        passDebounce()
                        vm.updateQuery("woods")
                        passDebounce()

                        vm.uiState.value.body shouldBe SearchBody.Results("wood", results.sections())
                    }
                }
            }

            it("#351 TC-15: raises no notice") {
                checkAll(endings) { ending ->
                    val repo = TestLibrarySearchRepository().apply {
                        answerLibrary(PaletteSearchResult.Success(randomLibrarySearchResults()))
                        answerLibrary(ending)
                    }

                    runCase(repo) { vm ->
                        vm.updateQuery("wood")
                        passDebounce()
                        vm.updateQuery("woods")
                        passDebounce()

                        vm.uiState.value.notice.shouldBeNull()
                    }
                }
            }
        }
    }

    describe("re-selecting the tab") {

        it("#351 TC-16: leaves the query and results alone on the first call") {
            val repo = TestLibrarySearchRepository().apply { answerLibrary(PaletteSearchResult.Success(randomLibrarySearchResults())) }

            runCase(repo) { vm ->
                vm.updateQuery("wood")
                passDebounce()
                val before = vm.uiState.value

                vm.tabReselected(0)

                vm.uiState.value shouldBe before
            }
        }

        it("#351 TC-16: clears the query and returns to the Prompt on a later call") {
            val repo = TestLibrarySearchRepository().apply { answerLibrary(PaletteSearchResult.Success(randomLibrarySearchResults())) }

            runCase(repo) { vm ->
                vm.updateQuery("wood")
                passDebounce()
                vm.tabReselected(0)

                vm.tabReselected(1)

                vm.uiState.value.query shouldBe ""
                vm.uiState.value.body shouldBe SearchBody.Prompt
            }
        }

        it("#351 TC-16: sends no search when re-selecting clears the query") {
            val repo = TestLibrarySearchRepository().apply { answerLibrary(PaletteSearchResult.Success(randomLibrarySearchResults())) }

            runCase(repo) { vm ->
                vm.updateQuery("wood")
                passDebounce()
                vm.tabReselected(0)
                val calls = repo.libraryCalls.toList()

                vm.tabReselected(1)
                advanceTimeBy(1_000)
                runCurrent()

                repo.libraryCalls shouldBe calls
            }
        }
    }
})
