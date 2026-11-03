@file:OptIn(ExperimentalCoroutinesApi::class)

package com.omnibus.omnibus.feature.bookdetail.more.merge

import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.data.auth.FakeAuthRepository
import com.omnibus.omnibus.data.merge.MergeBooksResult
import com.omnibus.omnibus.data.merge.MergeCandidatesResult
import com.omnibus.omnibus.data.merge.MergeError
import com.omnibus.omnibus.data.merge.MergeReceipt
import com.omnibus.omnibus.data.merge.UndoMergeResult
import com.omnibus.omnibus.data.network.FakeConnectivityMonitor
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** The state of one case: the ViewModel under test and its fakes. */
private class Subject(
    val repo: ScriptedMergeRepository,
    val auth: FakeAuthRepository,
    val monitor: FakeConnectivityMonitor,
    val vm: MergeViewModel,
)

private const val BOOK_UUID = "book-1"

private val adminUser = CurrentUser(id = 1, canEdit = true, canUpload = false, hiddenFormats = emptySet(), isAdmin = true)

private val other = mergeTestBook(2, uuid = "u-2", title = "The Hound", author = "Ann", published = "1902-01-01")
private val another = mergeTestBook(3, uuid = "u-3", title = "Study in Scarlet", author = "Ann", seriesId = 5L)

/**
 * Runs one case on virtual time. Main is a standard test dispatcher, so time moves only when a case advances it. The
 * ViewModel's scope is cancelled afterwards, so its collectors do not outlive the case.
 */
private fun runMergeCase(
    repo: ScriptedMergeRepository = ScriptedMergeRepository(),
    online: Boolean = true,
    user: CurrentUser? = adminUser,
    block: suspend TestScope.(Subject) -> Unit,
) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    val auth = FakeAuthRepository().also { it.currentUserState.value = user }
    val monitor = FakeConnectivityMonitor(online)
    val vm = MergeViewModel(BOOK_UUID, repo, auth, monitor)
    try {
        runCurrent()
        block(Subject(repo, auth, monitor, vm))
    } finally {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }
}

/** Opens the sheet, types [query] and runs its debounce, so the search is sent. */
private suspend fun TestScope.searchFor(s: Subject, query: String) {
    s.vm.openSheet()
    s.vm.onQueryChange(query)
    runCurrent()
    advanceTimeBy(MERGE_SEARCH_DEBOUNCE_MILLIS)
    runCurrent()
}

class MergeViewModelTest : DescribeSpec({

    describe("gate inputs") {

        it("#120 TC-21: the admin flag follows the signed-in user, and connectivity follows the monitor") {
            runMergeCase(user = null) { s ->
                s.vm.uiState.value.isAdmin.shouldBeNull()

                s.auth.currentUserState.value = adminUser.copy(isAdmin = false)
                runCurrent()
                s.vm.uiState.value.isAdmin shouldBe false
                s.vm.openSheet()
                s.vm.uiState.value.sheetOpen shouldBe false

                s.auth.currentUserState.value = adminUser
                runCurrent()
                s.vm.uiState.value.isAdmin shouldBe true

                s.monitor.isOnline.value = false
                runCurrent()
                s.vm.uiState.value.isOnline shouldBe false
                s.vm.openSheet()
                s.vm.uiState.value.sheetOpen shouldBe false

                s.monitor.isOnline.value = true
                runCurrent()
                s.vm.openSheet()
                s.vm.uiState.value.sheetOpen shouldBe true
            }
        }
    }

    describe("sheet") {

        it("#120 TC-22: opening resets the sheet, and closing cancels the search") {
            val repo = ScriptedMergeRepository()
            repo.searchResult = MergeCandidatesResult.Success(listOf(other))
            runMergeCase(repo) { s ->
                searchFor(s, "dune")
                s.vm.uiState.value.candidates shouldBe listOf(other)
                s.vm.choose(other)
                s.vm.back()
                s.vm.onQueryChange("dunes")
                s.vm.closeSheet()
                s.vm.openSheet()

                with(s.vm.uiState.value) {
                    sheetOpen shouldBe true
                    step shouldBe MergeStep.Search
                    query shouldBe ""
                    candidates.shouldBeEmpty()
                    searchFailed shouldBe false
                    mergeError.shouldBeNull()
                }
            }

            runMergeCase(ScriptedMergeRepository()) { s ->
                s.vm.openSheet()
                s.vm.onQueryChange("dune")
                runCurrent()
                s.vm.closeSheet()
                advanceTimeBy(1_000)
                runCurrent()

                s.vm.uiState.value.sheetOpen shouldBe false
                s.repo.searches.shouldBeEmpty()
            }
        }

        it("#120 TC-23: back from the confirmation keeps the query and the results") {
            val repo = ScriptedMergeRepository()
            repo.searchResult = MergeCandidatesResult.Success(listOf(other, another))
            runMergeCase(repo) { s ->
                searchFor(s, "ann")
                val before = s.vm.uiState.value

                s.vm.choose(other)
                s.vm.uiState.value.step shouldBe MergeStep.Confirm(other)
                s.vm.back()

                s.vm.uiState.value shouldBe before
            }
        }

        it("#120 TC-27: a blank query clears the results at once and sends no request") {
            val repo = ScriptedMergeRepository()
            repo.searchResult = MergeCandidatesResult.Success(listOf(other))
            runMergeCase(repo) { s ->
                searchFor(s, "dune")
                s.vm.uiState.value.candidates shouldBe listOf(other)

                s.vm.onQueryChange("   ")
                s.vm.uiState.value.candidates.shouldBeEmpty()
                s.vm.uiState.value.searching shouldBe false

                advanceTimeBy(1_000)
                runCurrent()
                s.repo.searches shouldBe listOf("dune")
            }
        }
    }

    describe("debounced search") {

        it("#120 TC-24: a trimmed query is searched once, at exactly 250 ms") {
            runMergeCase { s ->
                s.vm.openSheet()
                s.vm.onQueryChange("  dune  ")
                runCurrent()

                advanceTimeBy(MERGE_SEARCH_DEBOUNCE_MILLIS - 1)
                runCurrent()
                s.repo.searches.shouldBeEmpty()
                s.vm.uiState.value.searching shouldBe true

                advanceTimeBy(1)
                runCurrent()
                s.repo.searches shouldBe listOf("dune")
                s.vm.uiState.value.query shouldBe "  dune  "
            }
        }

        it("#120 TC-25: typing inside the debounce window sends only the last query") {
            runMergeCase { s ->
                s.vm.openSheet()
                runCurrent()
                s.vm.onQueryChange("d")
                runCurrent()
                advanceTimeBy(100)
                runCurrent()
                s.vm.onQueryChange("du")
                runCurrent()
                advanceTimeBy(100)
                runCurrent()
                s.vm.onQueryChange("dune")
                runCurrent()
                advanceTimeBy(250)
                runCurrent()

                s.repo.searches shouldBe listOf("dune")
            }
        }

        it("#120 TC-26: a response for a superseded query is dropped, in either order") {
            for (completeFirstOne in listOf(true, false)) {
                val repo = ScriptedMergeRepository()
                val first = repo.holdSearch()
                val second = repo.holdSearch()
                runMergeCase(repo) { s ->
                    s.vm.openSheet()
                    s.vm.onQueryChange("dune")
                    runCurrent()
                    advanceTimeBy(MERGE_SEARCH_DEBOUNCE_MILLIS)
                    runCurrent()
                    s.vm.onQueryChange("dune2")
                    runCurrent()
                    advanceTimeBy(MERGE_SEARCH_DEBOUNCE_MILLIS)
                    runCurrent()

                    val staleResult = MergeCandidatesResult.Success(listOf(mergeTestBook(9, uuid = "stale")))
                    val latestResult = listOf(other)
                    // The first request is the superseded one; only the order the responses arrive in varies.
                    if (completeFirstOne) {
                        first.complete(staleResult)
                        runCurrent()
                        second.complete(MergeCandidatesResult.Success(latestResult))
                    } else {
                        second.complete(MergeCandidatesResult.Success(latestResult))
                        runCurrent()
                        first.complete(staleResult)
                    }
                    runCurrent()

                    s.vm.uiState.value.candidates shouldBe latestResult
                }
            }
        }

        it("#120 TC-28: a query over 1024 UTF-8 bytes is not accepted, and 1024 bytes is") {
            runMergeCase { s ->
                searchFor(s, "dune")
                s.repo.searches shouldBe listOf("dune")

                s.vm.onQueryChange("a".repeat(1025))
                s.vm.onQueryChange("é".repeat(513))
                runCurrent()
                s.vm.uiState.value.query shouldBe "dune"
                advanceTimeBy(1_000)
                runCurrent()
                s.repo.searches shouldBe listOf("dune")

                s.vm.onQueryChange("a".repeat(1024))
                runCurrent()
                advanceTimeBy(MERGE_SEARCH_DEBOUNCE_MILLIS)
                runCurrent()
                s.vm.uiState.value.query shouldBe "a".repeat(1024)
                s.repo.searches shouldBe listOf("dune", "a".repeat(1024))
            }
        }

        it("#120 TC-29: offline sends nothing, and coming back online re-runs a live search") {
            runMergeCase(online = true) { s ->
                s.vm.openSheet()
                s.monitor.isOnline.value = false
                runCurrent()

                s.vm.onQueryChange("dune")
                runCurrent()
                advanceTimeBy(1_000)
                runCurrent()
                s.repo.searches.shouldBeEmpty()
                s.vm.uiState.value.searching shouldBe false

                s.monitor.isOnline.value = true
                runCurrent()
                advanceTimeBy(MERGE_SEARCH_DEBOUNCE_MILLIS)
                runCurrent()
                s.repo.searches shouldBe listOf("dune")

                s.vm.onQueryChange("dunes")
                runCurrent()
                advanceTimeBy(100)
                runCurrent()
                s.monitor.isOnline.value = false
                runCurrent()
                advanceTimeBy(1_000)
                runCurrent()
                s.repo.searches shouldBe listOf("dune")
            }
        }

        it("#120 TC-30: reconnecting re-runs nothing when the query is blank, the sheet is closed, or on the confirm step") {
            runMergeCase(online = true) { s ->
                s.vm.openSheet()
                s.monitor.isOnline.value = false
                runCurrent()
                s.vm.onQueryChange("   ")
                s.monitor.isOnline.value = true
                runCurrent()
                advanceTimeBy(1_000)
                runCurrent()
                s.repo.searches.shouldBeEmpty()
            }

            runMergeCase(online = true) { s ->
                s.vm.openSheet()
                s.monitor.isOnline.value = false
                runCurrent()
                s.vm.onQueryChange("dune")
                s.vm.closeSheet()
                s.monitor.isOnline.value = true
                runCurrent()
                advanceTimeBy(1_000)
                runCurrent()
                s.repo.searches.shouldBeEmpty()
            }

            runMergeCase(online = true) { s ->
                searchFor(s, "dune")
                s.vm.choose(other)
                s.monitor.isOnline.value = false
                runCurrent()
                s.monitor.isOnline.value = true
                runCurrent()
                advanceTimeBy(1_000)
                runCurrent()
                s.repo.searches shouldBe listOf("dune")
            }
        }

        it("#120 TC-31: this book is never a candidate, and a result with only this book reads as no match") {
            val repo = ScriptedMergeRepository()
            val target = mergeTestBook(1, uuid = BOOK_UUID, title = "The Sign of the Four")
            repo.searchResult = MergeCandidatesResult.Success(listOf(target, other))
            runMergeCase(repo) { s ->
                searchFor(s, "dune")
                s.vm.uiState.value.candidates shouldBe listOf(other)
            }

            repo.searchResult = MergeCandidatesResult.Success(listOf(target))
            runMergeCase(repo) { s ->
                searchFor(s, "dune")
                s.vm.uiState.value.candidates.shouldBeEmpty()
                mergeNote(
                    s.vm.uiState.value.query,
                    s.vm.uiState.value.searching,
                    s.vm.uiState.value.candidates.size,
                    s.vm.uiState.value.isOnline,
                    s.vm.uiState.value.searchFailed,
                ) shouldBe MergeNote.NoMatch("dune")
            }

            repo.searchResult = MergeCandidatesResult.Success(emptyList())
            runMergeCase(repo) { s ->
                searchFor(s, "dune")
                s.vm.uiState.value.candidates.shouldBeEmpty()
            }
        }

        it("#120 TC-32: any search failure shows the error and clears the rows, and editing clears the error") {
            val failures = listOf(
                MergeCandidatesResult.Failure(MergeError.Unreachable),
                MergeCandidatesResult.Failure(MergeError.Http(500, null)),
                MergeCandidatesResult.Failure(MergeError.Forbidden),
                MergeCandidatesResult.Failure(MergeError.SessionExpired),
                MergeCandidatesResult.NoServer,
            )
            for (failure in failures) {
                val repo = ScriptedMergeRepository()
                repo.searchResult = failure
                runMergeCase(repo) { s ->
                    searchFor(s, "dune")
                    with(s.vm.uiState.value) {
                        searchFailed shouldBe true
                        candidates.shouldBeEmpty()
                        searching shouldBe false
                    }

                    s.vm.onQueryChange("dunes")
                    s.vm.uiState.value.searchFailed shouldBe false
                }
            }
        }

        it("#120 TC-33: retry searches the current query at once, and a retry offline or with a blank query sends nothing") {
            val repo = ScriptedMergeRepository()
            repo.searchResult = MergeCandidatesResult.Failure(MergeError.Unreachable)
            runMergeCase(repo) { s ->
                searchFor(s, "dune")
                s.vm.uiState.value.searchFailed shouldBe true

                repo.searchResult = MergeCandidatesResult.Success(listOf(other))
                s.vm.retrySearch()
                runCurrent()
                s.repo.searches shouldBe listOf("dune", "dune")
                s.vm.uiState.value.searchFailed shouldBe false
                s.vm.uiState.value.candidates shouldBe listOf(other)
            }

            val offlineRepo = ScriptedMergeRepository()
            offlineRepo.searchResult = MergeCandidatesResult.Failure(MergeError.Unreachable)
            runMergeCase(offlineRepo) { s ->
                searchFor(s, "dune")
                s.monitor.isOnline.value = false
                runCurrent()
                s.vm.retrySearch()
                runCurrent()
                s.repo.searches shouldBe listOf("dune")
            }

            runMergeCase(ScriptedMergeRepository()) { s ->
                s.vm.openSheet()
                s.vm.onQueryChange("   ")
                s.vm.retrySearch()
                runCurrent()
                s.repo.searches.shouldBeEmpty()
            }
        }

        it("#120 TC-34: a response that arrives after the sheet closed changes nothing") {
            val repo = ScriptedMergeRepository()
            val held = repo.holdSearch()
            runMergeCase(repo) { s ->
                s.vm.openSheet()
                s.vm.onQueryChange("dune")
                runCurrent()
                advanceTimeBy(MERGE_SEARCH_DEBOUNCE_MILLIS)
                runCurrent()

                s.vm.closeSheet()
                held.complete(MergeCandidatesResult.Success(listOf(other)))
                runCurrent()

                s.vm.uiState.value.candidates.shouldBeEmpty()
                s.vm.uiState.value.sheetOpen shouldBe false
            }
        }
    }

    describe("merge") {

        it("#120 TC-35: a merge sends the candidate as the source, posts the outcome once, and closes the sheet with it") {
            val repo = ScriptedMergeRepository()
            runMergeCase(repo) { s ->
                searchFor(s, "dune")
                s.vm.choose(other)
                runCurrent()

                val states = mutableListOf<MergeUIState>()
                backgroundScope.launch { s.vm.uiState.collect { states += it } }
                runCurrent()

                s.vm.confirmMerge()
                runCurrent()

                repo.merges shouldBe listOf(other.uuid to BOOK_UUID)
                (states.none { !it.sheetOpen && it.outcome == null }) shouldBe true
                with(s.vm.uiState.value) {
                    sheetOpen shouldBe false
                    merging shouldBe false
                    outcome shouldBe MergeOutcome.Merged(1L, 42L)
                }

                s.vm.outcomeShown(1L)
                s.vm.uiState.value.outcome.shouldBeNull()

                s.vm.openSheet()
                s.vm.onQueryChange("dune")
                runCurrent()
                advanceTimeBy(MERGE_SEARCH_DEBOUNCE_MILLIS)
                runCurrent()
                s.vm.choose(another)
                s.vm.confirmMerge()
                runCurrent()

                s.vm.outcomeShown(1L)
                s.vm.uiState.value.outcome shouldBe MergeOutcome.Merged(2L, 42L)
            }
        }

        it("#120 TC-36: while a merge runs, the sheet is locked and every other intent is ignored") {
            val repo = ScriptedMergeRepository()
            val held = repo.holdMerge()
            runMergeCase(repo) { s ->
                searchFor(s, "dune")
                s.vm.choose(other)
                s.vm.confirmMerge()
                s.vm.confirmMerge()
                runCurrent()

                s.vm.closeSheet()
                s.vm.back()
                s.vm.choose(another)
                s.vm.onQueryChange("x")
                s.vm.retrySearch()
                runCurrent()

                with(s.vm.uiState.value) {
                    merging shouldBe true
                    step shouldBe MergeStep.Confirm(other)
                    sheetOpen shouldBe true
                }
                repo.merges.size shouldBe 1
                repo.searches shouldBe listOf("dune")

                held.complete(MergeBooksResult.Success(MergeReceipt(42, BOOK_UUID)))
                runCurrent()
            }
        }

        it("#120 TC-37: a merge is ignored offline and outside the confirmation step") {
            runMergeCase { s ->
                searchFor(s, "dune")
                s.vm.choose(other)
                s.monitor.isOnline.value = false
                runCurrent()
                s.vm.confirmMerge()
                runCurrent()
                s.repo.merges.shouldBeEmpty()
            }

            runMergeCase { s ->
                s.vm.openSheet()
                s.monitor.isOnline.value = false
                runCurrent()
                s.vm.confirmMerge()
                runCurrent()
                s.repo.merges.shouldBeEmpty()
            }
        }

        val failures = listOf(
            "not found" to (MergeBooksResult.Failure(MergeError.NotFound) to MergeFailure.Gone),
            "unreachable" to (MergeBooksResult.Failure(MergeError.Unreachable) to MergeFailure.Generic),
            "500" to (MergeBooksResult.Failure(MergeError.Http(500, null)) to MergeFailure.Generic),
            "422" to (MergeBooksResult.Failure(MergeError.Http(422, null)) to MergeFailure.Generic),
            "forbidden" to (MergeBooksResult.Failure(MergeError.Forbidden) to MergeFailure.Generic),
            "session expired" to (MergeBooksResult.Failure(MergeError.SessionExpired) to MergeFailure.Generic),
            "conflict" to (MergeBooksResult.Failure(MergeError.Conflict(null)) to MergeFailure.Generic),
            "no server" to (MergeBooksResult.NoServer to MergeFailure.Generic),
        )

        for ((label, case) in failures) {
            val (result, expected) = case

            it("#120 TC-38: a merge on $label keeps the sheet on the confirm step with $expected, then retries and goes back") {
                val repo = ScriptedMergeRepository()
                repo.mergeResult = result
                runMergeCase(repo) { s ->
                    searchFor(s, "dune")
                    s.vm.choose(other)
                    s.vm.confirmMerge()
                    runCurrent()

                    with(s.vm.uiState.value) {
                        sheetOpen shouldBe true
                        step shouldBe MergeStep.Confirm(other)
                        merging shouldBe false
                        mergeError shouldBe expected
                        outcome.shouldBeNull()
                    }

                    s.vm.confirmMerge()
                    runCurrent()
                    s.repo.merges.size shouldBe 2

                    s.vm.back()
                    s.vm.uiState.value.step shouldBe MergeStep.Search
                    s.vm.uiState.value.mergeError.shouldBeNull()
                }
            }
        }

        it("#120 TC-38: a retry clears the error as it starts and reports a new failure if it fails again") {
            val repo = ScriptedMergeRepository()
            repo.mergeResult = MergeBooksResult.Failure(MergeError.Unreachable)
            val held = repo.holdMerge()
            runMergeCase(repo) { s ->
                searchFor(s, "dune")
                s.vm.choose(other)
                s.vm.confirmMerge()
                runCurrent()
                s.vm.uiState.value.merging shouldBe true
                held.complete(MergeBooksResult.Failure(MergeError.Unreachable))
                runCurrent()
                s.vm.uiState.value.mergeError shouldBe MergeFailure.Generic

                val retry = repo.holdMerge()
                s.vm.confirmMerge()
                runCurrent()
                s.vm.uiState.value.mergeError.shouldBeNull()
                s.vm.uiState.value.merging shouldBe true

                retry.complete(MergeBooksResult.Failure(MergeError.Http(500, null)))
                runCurrent()
                s.vm.uiState.value.mergeError shouldBe MergeFailure.Generic
            }
        }
    }

    describe("undo handles") {

        it("#120 TC-39: the handle survives the sheet closing, reopening and the outcome being shown") {
            runMergeCase { s ->
                searchFor(s, "dune")
                s.vm.choose(other)
                s.vm.confirmMerge()
                runCurrent()
                s.vm.outcomeShown(1L)

                s.vm.openSheet()
                s.vm.onQueryChange("dune")
                s.vm.closeSheet()
                s.vm.openSheet()
                runCurrent()
                val before = s.vm.uiState.value

                s.vm.undo(42L)
                runCurrent()

                s.repo.undos shouldBe listOf(42L)
                s.vm.uiState.value.copy(outcome = before.outcome) shouldBe before
            }
        }

        it("#120 TC-40: each merge keeps its own handle") {
            val repo = ScriptedMergeRepository()
            runMergeCase(repo) { s ->
                searchFor(s, "dune")
                s.vm.choose(other)
                s.vm.confirmMerge()
                runCurrent()

                repo.mergeResult = MergeBooksResult.Success(MergeReceipt(43, BOOK_UUID))
                s.vm.openSheet()
                s.vm.onQueryChange("dune")
                runCurrent()
                advanceTimeBy(MERGE_SEARCH_DEBOUNCE_MILLIS)
                runCurrent()
                s.vm.choose(another)
                s.vm.confirmMerge()
                runCurrent()

                s.vm.undo(42L)
                runCurrent()
                s.vm.undo(43L)
                runCurrent()

                repo.undos shouldBe listOf(42L, 43L)
            }
        }

        it("#120 TC-41: a successful undo releases the handle, so a second undo sends nothing") {
            runMergeCase { s ->
                searchFor(s, "dune")
                s.vm.choose(other)
                s.vm.confirmMerge()
                runCurrent()

                s.vm.undo(42L)
                runCurrent()
                s.vm.uiState.value.outcome.shouldBeInstanceOf<MergeOutcome.Undone>().mergeLogId shouldBe 42L

                s.vm.undo(42L)
                runCurrent()
                s.repo.undos shouldBe listOf(42L)
            }
        }

        it("#120 TC-42: a 404 or a 409 releases the handle and shows the terminal failure, and a second undo sends nothing") {
            for (error in listOf(MergeError.NotFound, MergeError.Conflict(null))) {
                val repo = ScriptedMergeRepository()
                repo.undoResult = UndoMergeResult.Failure(error)
                runMergeCase(repo) { s ->
                    searchFor(s, "dune")
                    s.vm.choose(other)
                    s.vm.confirmMerge()
                    runCurrent()

                    s.vm.undo(42L)
                    runCurrent()
                    s.vm.uiState.value.outcome shouldBe MergeOutcome.UndoFailed(2L, 42L, retryable = false)

                    s.vm.undo(42L)
                    runCurrent()
                    s.repo.undos shouldBe listOf(42L)
                }
            }
        }

        it("#120 TC-43: a retryable failure keeps the handle, and a retry that succeeds ends the undo") {
            val failures = listOf(
                MergeError.Unreachable,
                MergeError.UnexpectedResponse,
                MergeError.Http(500, null),
                MergeError.Forbidden,
                MergeError.SessionExpired,
            )
            val repo = ScriptedMergeRepository()
            runMergeCase(repo) { s ->
                searchFor(s, "dune")
                s.vm.choose(other)
                s.vm.confirmMerge()
                runCurrent()

                for (error in failures) {
                    repo.undoResult = UndoMergeResult.Failure(error)
                    s.vm.undo(42L)
                    runCurrent()
                    s.vm.uiState.value.outcome.shouldBeInstanceOf<MergeOutcome.UndoFailed>().retryable shouldBe true
                }

                repo.undoResult = UndoMergeResult.Success("source-1")
                s.vm.undo(42L)
                runCurrent()

                repo.undos shouldBe List(failures.size + 1) { 42L }
                s.vm.uiState.value.outcome.shouldBeInstanceOf<MergeOutcome.Undone>().mergeLogId shouldBe 42L
            }
        }

        it("#120 TC-43: no server is retryable too") {
            val repo = ScriptedMergeRepository()
            repo.undoResult = UndoMergeResult.NoServer
            runMergeCase(repo) { s ->
                searchFor(s, "dune")
                s.vm.choose(other)
                s.vm.confirmMerge()
                runCurrent()

                s.vm.undo(42L)
                runCurrent()
                s.vm.uiState.value.outcome.shouldBeInstanceOf<MergeOutcome.UndoFailed>().retryable shouldBe true
            }
        }

        it("#120 TC-44: an unknown handle sends nothing, and a new ViewModel holds no handles") {
            runMergeCase { s ->
                searchFor(s, "dune")
                s.vm.choose(other)
                s.vm.confirmMerge()
                runCurrent()
                s.vm.outcomeShown(1L)
                val before = s.vm.uiState.value

                s.vm.undo(999L)
                runCurrent()
                s.repo.undos.shouldBeEmpty()
                s.vm.uiState.value shouldBe before
            }

            runMergeCase { s ->
                s.vm.undo(42L)
                runCurrent()
                s.repo.undos.shouldBeEmpty()
                s.vm.uiState.value.outcome.shouldBeNull()
            }
        }

        it("#120 TC-45: an undo in flight blocks a second tap on the same handle, but not another handle") {
            val repo = ScriptedMergeRepository()
            val first = repo.holdUndo()
            runMergeCase(repo) { s ->
                searchFor(s, "dune")
                s.vm.choose(other)
                s.vm.confirmMerge()
                runCurrent()
                repo.mergeResult = MergeBooksResult.Success(MergeReceipt(43, BOOK_UUID))
                s.vm.openSheet()
                s.vm.onQueryChange("dune")
                runCurrent()
                advanceTimeBy(MERGE_SEARCH_DEBOUNCE_MILLIS)
                runCurrent()
                s.vm.choose(another)
                s.vm.confirmMerge()
                runCurrent()

                s.vm.undo(42L)
                runCurrent()
                s.vm.undo(42L)
                runCurrent()
                s.vm.undo(43L)
                runCurrent()
                repo.undos shouldBe listOf(42L, 43L)

                first.complete(UndoMergeResult.Failure(MergeError.Unreachable))
                runCurrent()
                s.vm.uiState.value.outcome.shouldBeInstanceOf<MergeOutcome.UndoFailed>().retryable shouldBe true

                s.vm.undo(42L)
                runCurrent()
                repo.undos shouldBe listOf(42L, 43L, 42L)
            }
        }
    }
})
