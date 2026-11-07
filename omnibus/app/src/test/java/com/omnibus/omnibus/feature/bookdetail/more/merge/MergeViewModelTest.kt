@file:OptIn(ExperimentalCoroutinesApi::class)

package com.omnibus.omnibus.feature.bookdetail.more.merge

import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.auth.randomCurrentUser
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.merge.MergeBooksResult
import com.omnibus.omnibus.data.merge.MergeCandidatesResult
import com.omnibus.omnibus.data.merge.MergeError
import com.omnibus.omnibus.data.merge.MergeReceipt
import com.omnibus.omnibus.data.merge.UndoMergeResult
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** The ViewModel under test and its fakes. */
private class Subject(
    val bookUuid: String,
    val repo: ScriptedMergeRepository,
    val monitor: TestConnectivityMonitor,
    val vm: MergeViewModel,
)

private fun candidate(): Book = randomBook().copy(uniqueIdentifier = randomString())

/**
 * Runs one case on virtual time, on the spec's Main scheduler, so time moves only when a case advances it. The ViewModel's
 * scope is cancelled afterwards, so its collectors do not outlive the case.
 */
private fun runCase(
    repo: ScriptedMergeRepository = ScriptedMergeRepository(),
    auth: TestAuthRepository = TestAuthRepository(),
    monitor: TestConnectivityMonitor = TestConnectivityMonitor(),
    block: suspend TestScope.(Subject) -> Unit,
) = runTest {
    val bookUuid = randomString()
    val vm = MergeViewModel(bookUuid, repo, auth, monitor)
    try {
        runCurrent()
        block(Subject(bookUuid, repo, monitor, vm))
    } finally {
        vm.viewModelScope.cancel()
    }
}

/** Runs one case signed in as an admin, the only user who can open the sheet. */
private fun runAsAdmin(
    repo: ScriptedMergeRepository = ScriptedMergeRepository(),
    monitor: TestConnectivityMonitor = TestConnectivityMonitor(),
    block: suspend TestScope.(Subject) -> Unit,
) = runCase(
    repo = repo,
    auth = TestAuthRepository().also { it.currentUserState.value = randomCurrentUser().copy(isAdmin = true) },
    monitor = monitor,
    block = block,
)

/** Opens the sheet, types [query] and runs its debounce, so the search is sent. */
private fun TestScope.searchFor(s: Subject, query: String) {
    s.vm.openSheet()
    s.vm.onQueryChange(query)
    advanceUntilIdle()
}

/** Merges [candidate] into the book through the sheet, and returns the merge's undo handle. */
private fun TestScope.merge(s: Subject, candidate: Book = candidate()): Long {
    s.vm.openSheet()
    s.vm.choose(candidate)
    s.vm.confirmMerge()
    runCurrent()
    return s.vm.uiState.value.outcome.shouldBeInstanceOf<MergeOutcome.Merged>().mergeLogId
}

class MergeViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(StandardTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("opening the sheet") {
        context("when the menu row is not enabled") {
            it("#120 TC-21: stays closed, for a signed-out, non-admin or offline user") {
                checkAll(
                    Exhaustive.of(
                        null to true,
                        randomCurrentUser().copy(isAdmin = false) to true,
                        randomCurrentUser().copy(isAdmin = true) to false,
                    ),
                ) { (user, online) ->
                    val auth = TestAuthRepository().also { it.currentUserState.value = user }

                    runCase(auth = auth, monitor = TestConnectivityMonitor(online)) { s ->
                        s.vm.openSheet()

                        s.vm.uiState.value.sheetOpen shouldBe false
                    }
                }
            }
        }

        context("when it was used before") {
            it("#120 TC-22: carries nothing over") {
                val found = candidate()
                val repo = ScriptedMergeRepository().apply { searchResult = MergeCandidatesResult.Success(listOf(found)) }

                runAsAdmin(repo) { s ->
                    searchFor(s, randomString())
                    s.vm.choose(found)
                    s.vm.closeSheet()

                    s.vm.openSheet()

                    with(s.vm.uiState.value) {
                        step shouldBe MergeStep.Search
                        query shouldBe ""
                        candidates.shouldBeEmpty()
                    }
                }
            }
        }
    }

    describe("closing the sheet") {
        context("when a search is debouncing") {
            it("#120 TC-22: sends no search") {
                runAsAdmin { s ->
                    s.vm.openSheet()
                    s.vm.onQueryChange(randomString())

                    s.vm.closeSheet()
                    advanceUntilIdle()

                    s.repo.searches.shouldBeEmpty()
                }
            }
        }
    }

    describe("going back from the confirmation") {
        it("#120 TC-23: restores the query with its results") {
            val found = candidate()
            val repo = ScriptedMergeRepository().apply { searchResult = MergeCandidatesResult.Success(listOf(found)) }

            runAsAdmin(repo) { s ->
                searchFor(s, randomString())
                val before = s.vm.uiState.value

                s.vm.choose(found)
                s.vm.back()

                s.vm.uiState.value shouldBe before
            }
        }
    }

    describe("searching") {
        it("#120 TC-24, TC-25: sends only the last trimmed query typed inside the debounce window") {
            runAsAdmin { s ->
                s.vm.openSheet()
                s.vm.onQueryChange("d")
                runCurrent()
                advanceTimeBy(MERGE_SEARCH_DEBOUNCE_MILLIS - 1)
                s.vm.onQueryChange("  dune  ")
                advanceUntilIdle()

                s.repo.searches shouldBe listOf("dune")
            }
        }

        it("#120 TC-31: never offers this book") {
            val found = candidate()
            val repo = ScriptedMergeRepository()

            runAsAdmin(repo) { s ->
                repo.searchResult = MergeCandidatesResult.Success(
                    listOf(randomBook().copy(uniqueIdentifier = s.bookUuid), found),
                )

                searchFor(s, randomString())

                s.vm.uiState.value.candidates shouldBe listOf(found)
            }
        }

        context("when the query is blank") {
            it("#120 TC-27: clears the results without a request") {
                val query = randomString()
                val repo = ScriptedMergeRepository().apply {
                    searchResult = MergeCandidatesResult.Success(listOf(candidate()))
                }

                runAsAdmin(repo) { s ->
                    searchFor(s, query)

                    s.vm.onQueryChange("   ")
                    advanceUntilIdle()

                    s.vm.uiState.value.candidates.shouldBeEmpty()
                    s.repo.searches shouldBe listOf(query)
                }
            }
        }

        context("when the query is over the cap") {
            it("#120 TC-28: keeps the previous query") {
                val query = randomString()

                runAsAdmin { s ->
                    searchFor(s, query)

                    s.vm.onQueryChange("a".repeat(1025))
                    advanceUntilIdle()

                    s.vm.uiState.value.query shouldBe query
                    s.repo.searches shouldBe listOf(query)
                }
            }
        }

        context("when an older response is still on its way") {
            it("#120 TC-26: drops it, regardless of which response arrives first") {
                checkAll(Exhaustive.boolean()) { staleFirst ->
                    val latest = listOf(candidate())
                    val stale = MergeCandidatesResult.Success(listOf(candidate()))
                    val repo = ScriptedMergeRepository()
                    val first = repo.holdSearch()
                    val second = repo.holdSearch()

                    runAsAdmin(repo) { s ->
                        searchFor(s, randomString())
                        s.vm.onQueryChange(randomString())
                        advanceUntilIdle()

                        if (staleFirst) first.complete(stale)
                        second.complete(MergeCandidatesResult.Success(latest))
                        runCurrent()
                        first.complete(stale)
                        runCurrent()

                        s.vm.uiState.value.candidates shouldBe latest
                    }
                }
            }
        }

        context("when the search fails") {
            it("#120 TC-32: shows the error without rows, for each kind of failure") {
                checkAll(
                    Exhaustive.of(
                        MergeCandidatesResult.Failure(MergeError.Unreachable),
                        MergeCandidatesResult.Failure(MergeError.Http(500, null)),
                        MergeCandidatesResult.Failure(MergeError.Forbidden),
                        MergeCandidatesResult.Failure(MergeError.SessionExpired),
                        MergeCandidatesResult.NoServer,
                    ),
                ) { failure ->
                    val repo = ScriptedMergeRepository().apply { searchResult = failure }

                    runAsAdmin(repo) { s ->
                        searchFor(s, randomString())

                        s.vm.uiState.value.searchFailed shouldBe true
                        s.vm.uiState.value.candidates.shouldBeEmpty()
                    }
                }
            }

            it("#120 TC-33: searches the same query again at once on retry") {
                val query = randomString()
                val found = candidate()
                val repo = ScriptedMergeRepository().apply { searchResult = MergeCandidatesResult.NoServer }

                runAsAdmin(repo) { s ->
                    searchFor(s, query)
                    repo.searchResult = MergeCandidatesResult.Success(listOf(found))

                    s.vm.retrySearch()
                    runCurrent()

                    s.repo.searches shouldBe listOf(query, query)
                    s.vm.uiState.value.candidates shouldBe listOf(found)
                }
            }
        }

        context("when connectivity returns") {
            context("when the sheet is searching") {
                it("#120 TC-29: runs the query it could not send") {
                    val query = randomString()
                    val monitor = TestConnectivityMonitor()

                    runAsAdmin(monitor = monitor) { s ->
                        s.vm.openSheet()
                        monitor.isOnline.value = false
                        runCurrent()
                        s.vm.onQueryChange(query)
                        advanceUntilIdle()
                        s.repo.searches.shouldBeEmpty()

                        monitor.isOnline.value = true
                        advanceUntilIdle()

                        s.repo.searches shouldBe listOf(query)
                    }
                }
            }
        }
    }

    describe("merging") {
        it("#120 TC-35: sends the candidate as the source, closing the sheet with the outcome") {
            val chosen = candidate()
            val logId = randomLong()
            val repo = ScriptedMergeRepository().apply {
                mergeResult = MergeBooksResult.Success(MergeReceipt(logId, randomString()))
            }

            runAsAdmin(repo) { s ->
                merge(s, chosen) shouldBe logId

                repo.merges shouldBe listOf(chosen.uuid to s.bookUuid)
                s.vm.uiState.value.sheetOpen shouldBe false
            }
        }

        it("#120 TC-35: clears the outcome only for its own id") {
            runAsAdmin { s ->
                merge(s)
                val outcome = checkNotNull(s.vm.uiState.value.outcome)

                s.vm.outcomeShown(outcome.eventId + 1)
                s.vm.uiState.value.outcome shouldBe outcome

                s.vm.outcomeShown(outcome.eventId)
                s.vm.uiState.value.outcome.shouldBeNull()
            }
        }

        context("when a merge is in flight") {
            it("#120 TC-36: locks the sheet, ignoring every other intent") {
                val chosen = candidate()
                val repo = ScriptedMergeRepository().apply { holdMerge() }

                runAsAdmin(repo) { s ->
                    s.vm.openSheet()
                    s.vm.choose(chosen)
                    s.vm.confirmMerge()
                    s.vm.confirmMerge()
                    runCurrent()

                    s.vm.closeSheet()
                    s.vm.back()
                    s.vm.choose(candidate())
                    s.vm.onQueryChange(randomString())
                    s.vm.retrySearch()
                    advanceUntilIdle()

                    s.vm.uiState.value.sheetOpen shouldBe true
                    s.vm.uiState.value.step shouldBe MergeStep.Confirm(chosen)
                    repo.merges.size shouldBe 1
                    repo.searches.shouldBeEmpty()
                }
            }
        }

        context("when the device is offline") {
            it("#120 TC-37: sends nothing") {
                val monitor = TestConnectivityMonitor()

                runAsAdmin(monitor = monitor) { s ->
                    s.vm.openSheet()
                    s.vm.choose(candidate())
                    monitor.isOnline.value = false
                    runCurrent()

                    s.vm.confirmMerge()
                    runCurrent()

                    s.repo.merges.shouldBeEmpty()
                }
            }
        }

        context("when the merge fails") {
            it("#120 TC-38: stays on the confirmation with the failure, for each kind") {
                checkAll(
                    Exhaustive.of(
                        MergeBooksResult.Failure(MergeError.NotFound) to MergeFailure.Gone,
                        MergeBooksResult.Failure(MergeError.Unreachable) to MergeFailure.Generic,
                        MergeBooksResult.Failure(MergeError.Http(500, null)) to MergeFailure.Generic,
                        MergeBooksResult.Failure(MergeError.Forbidden) to MergeFailure.Generic,
                        MergeBooksResult.Failure(MergeError.SessionExpired) to MergeFailure.Generic,
                        MergeBooksResult.Failure(MergeError.Conflict(null)) to MergeFailure.Generic,
                        MergeBooksResult.NoServer to MergeFailure.Generic,
                    ),
                ) { (result, expected) ->
                    val chosen = candidate()
                    val repo = ScriptedMergeRepository().apply { mergeResult = result }

                    runAsAdmin(repo) { s ->
                        s.vm.openSheet()
                        s.vm.choose(chosen)

                        s.vm.confirmMerge()
                        runCurrent()

                        s.vm.uiState.value.step shouldBe MergeStep.Confirm(chosen)
                        s.vm.uiState.value.mergeError shouldBe expected
                    }
                }
            }
        }
    }

    describe("undoing a merge") {
        context("when the outcome was shown") {
            it("#120 TC-39: still sends the undo") {
                runAsAdmin { s ->
                    val handle = merge(s)
                    s.vm.outcomeShown(checkNotNull(s.vm.uiState.value.outcome).eventId)
                    s.vm.openSheet()
                    s.vm.closeSheet()

                    s.vm.undo(handle)
                    runCurrent()

                    s.repo.undos shouldBe listOf(handle)
                }
            }
        }

        context("when several merges were made") {
            it("#120 TC-40: keeps a handle for each") {
                val repo = ScriptedMergeRepository()

                runAsAdmin(repo) { s ->
                    val first = merge(s)
                    repo.mergeResult = MergeBooksResult.Success(MergeReceipt(first + 1, randomString()))
                    val second = merge(s)

                    s.vm.undo(first)
                    s.vm.undo(second)
                    runCurrent()

                    repo.undos shouldBe listOf(first, second)
                }
            }
        }

        context("when the undo succeeds") {
            it("#120 TC-41: releases the handle") {
                runAsAdmin { s ->
                    val handle = merge(s)

                    s.vm.undo(handle)
                    runCurrent()
                    s.vm.uiState.value.outcome.shouldBeInstanceOf<MergeOutcome.Undone>().mergeLogId shouldBe handle

                    s.vm.undo(handle)
                    runCurrent()
                    s.repo.undos.size shouldBe 1
                }
            }
        }

        context("when the failure is terminal") {
            it("#120 TC-42: releases the handle, for a 404 or a 409") {
                checkAll(Exhaustive.of(MergeError.NotFound, MergeError.Conflict(null))) { error ->
                    val repo = ScriptedMergeRepository().apply { undoResult = UndoMergeResult.Failure(error) }

                    runAsAdmin(repo) { s ->
                        val handle = merge(s)

                        s.vm.undo(handle)
                        runCurrent()
                        s.vm.uiState.value.outcome.shouldBeInstanceOf<MergeOutcome.UndoFailed>().retryable shouldBe false

                        s.vm.undo(handle)
                        runCurrent()
                        s.repo.undos.size shouldBe 1
                    }
                }
            }
        }

        context("when the failure can be retried") {
            it("#120 TC-43: keeps the handle, for each kind of failure") {
                checkAll(
                    Exhaustive.of(
                        UndoMergeResult.Failure(MergeError.Unreachable),
                        UndoMergeResult.Failure(MergeError.UnexpectedResponse),
                        UndoMergeResult.Failure(MergeError.Http(500, null)),
                        UndoMergeResult.Failure(MergeError.Forbidden),
                        UndoMergeResult.Failure(MergeError.SessionExpired),
                        UndoMergeResult.NoServer,
                    ),
                ) { failure ->
                    val repo = ScriptedMergeRepository().apply { undoResult = failure }

                    runAsAdmin(repo) { s ->
                        val handle = merge(s)

                        s.vm.undo(handle)
                        runCurrent()
                        s.vm.uiState.value.outcome.shouldBeInstanceOf<MergeOutcome.UndoFailed>().retryable shouldBe true

                        s.vm.undo(handle)
                        runCurrent()
                        s.repo.undos shouldBe listOf(handle, handle)
                    }
                }
            }
        }

        context("when an undo of the handle is in flight") {
            it("#120 TC-45: ignores a second tap") {
                val repo = ScriptedMergeRepository()

                runAsAdmin(repo) { s ->
                    val handle = merge(s)
                    repo.holdUndo()

                    s.vm.undo(handle)
                    runCurrent()
                    s.vm.undo(handle)
                    runCurrent()

                    repo.undos.size shouldBe 1
                }
            }
        }
    }
})
