package com.omnibus.omnibus.feature.bookdetail.actionbar

import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.bookprogress.BookProgressError
import com.omnibus.omnibus.data.bookprogress.BookProgressResult
import com.omnibus.omnibus.data.bookprogress.FakeBookProgressRepository
import com.omnibus.omnibus.data.bookprogress.chapters
import com.omnibus.omnibus.data.bookprogress.readingRecord
import com.omnibus.omnibus.data.readstatus.FakeReadStatusRepository
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusRecord
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.feature.bookdetail.progress.bookReadingProgress
import com.omnibus.omnibus.testing.CollectingScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private const val UUID = "uuid-1"

private fun known(status: ReadStatus) = ReadStatusState.Known(ReadStatusRecord(status, 1L, null))

/** A ViewModel with its fakes, and its state kept active by [collecting] until the test ends. */
private class Harness {
    val progress = FakeBookProgressRepository()
    val status = FakeReadStatusRepository()
    val collecting = CollectingScope()
    val vm = ActionBarViewModel(UUID, progress, status).also { collecting.collect(it.uiState) }
}

/** Runs [block] on a [Harness], and cancels its collection afterwards. */
private inline fun withHarness(block: Harness.() -> Unit) {
    val harness = Harness()
    try {
        harness.block()
    } finally {
        harness.collecting.cancel()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ActionBarViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("loading") {

        it("TC-33: starts unknown with no progress and reads nothing") {
            withHarness {
                vm.uiState.value shouldBe ActionBarUIState(status = ReadStatusState.Unknown, progress = null)
                progress.progressCollections.size shouldBe 0
                progress.chaptersCalls.size shouldBe 0
            }
        }

        it("TC-37: reload loads once per key") {
            withHarness {
                progress.progressFor(UUID).value = BookProgressResult.Success(listOf(readingRecord(40.0, 100)), fromServer = false)
                vm.reload(1)
                vm.reload(1)
                vm.reload(1)
                vm.reload(1)
                progress.progressCollections.size shouldBe 1
            }
        }

        it("TC-38: a new reload key re-reads progress") {
            withHarness {
                progress.progressFor(UUID).value = BookProgressResult.Success(listOf(readingRecord(40.0, 100)), fromServer = false)
                vm.reload(1)
                vm.uiState.value.progress?.fraction!! shouldBe (0.4f plusOrMinus 1e-4f)

                progress.progressFor(UUID).value = BookProgressResult.Success(listOf(readingRecord(70.0, 200)), fromServer = false)
                vm.reload(2)
                progress.progressCollections.size shouldBe 2
                vm.uiState.value.progress?.fraction!! shouldBe (0.7f plusOrMinus 1e-4f)
            }
        }

        it("TC-39: chapters are loaded once") {
            withHarness {
                progress.chaptersResults.addLast(BookChaptersResult.Success(chapters(0f, 0.3f), emptyList(), null, null))
                vm.reload(1)
                vm.reload(2)
                progress.chaptersCalls.size shouldBe 1
            }
        }

        it("TC-40: chapters are retried after a failure") {
            withHarness {
                progress.progressFor(UUID).value = BookProgressResult.Success(listOf(readingRecord(40.0, 100)), fromServer = false)
                progress.chaptersResults.addLast(BookChaptersResult.Failed(BookProgressError.Unreachable))
                progress.chaptersResults.addLast(BookChaptersResult.Success(chapters(0f, 0.3f), emptyList(), null, null))

                vm.reload(1)
                vm.uiState.value.progress?.chapterNumber.shouldBeNull()

                vm.reload(2)
                progress.chaptersCalls.size shouldBe 2
                vm.uiState.value.progress?.chapterNumber shouldBe 2
            }
        }
    }

    describe("the status and the progress") {

        it("TC-34: status changes flow into the state") {
            withHarness {
                status.seed(UUID, ReadStatusState.Unknown)
                vm.uiState.value.status shouldBe ReadStatusState.Unknown

                status.seed(UUID, known(ReadStatus.Reading))
                vm.uiState.value.status shouldBe known(ReadStatus.Reading)

                status.seed(UUID, known(ReadStatus.Finished))
                vm.uiState.value.status shouldBe known(ReadStatus.Finished)

                status.seed(UUID, ReadStatusState.Failed)
                vm.uiState.value.status shouldBe ReadStatusState.Failed
            }
        }

        it("TC-35: progress records become the reading progress") {
            withHarness {
                val records = listOf(readingRecord(40.0, 100))
                val chaptersLoaded = BookChaptersResult.Success(chapters(0f, 0.3f), emptyList(), null, null)
                progress.progressFor(UUID).value = BookProgressResult.Success(records, fromServer = false)
                progress.chaptersResults.addLast(chaptersLoaded)

                vm.reload(1)

                val expected = bookReadingProgress(records, chaptersLoaded)
                vm.uiState.value.progress shouldBe expected
                vm.uiState.value.progress?.fraction!! shouldBe (0.4f plusOrMinus 1e-4f)
                vm.uiState.value.progress?.chapterNumber shouldBe 2
                vm.uiState.value.progress?.latestMedium shouldBe expected?.latestMedium
            }
        }

        it("TC-36: the status updates before progress arrives") {
            withHarness {
                vm.reload(1)
                status.seed(UUID, known(ReadStatus.Reading))

                vm.uiState.value.status shouldBe known(ReadStatus.Reading)
                vm.uiState.value.progress.shouldBeNull()
            }
        }

        it("TC-41: a failed progress read still uses its records") {
            withHarness {
                val records = listOf(readingRecord(40.0, 100))
                progress.progressFor(UUID).value = BookProgressResult.Failed(BookProgressError.Unreachable, records)

                vm.reload(1)

                vm.uiState.value.progress shouldBe bookReadingProgress(records, null)
                vm.uiState.value.progress?.fraction!! shouldBe (0.4f plusOrMinus 1e-4f)
            }
        }

        it("TC-42: a failed read with no records means no position") {
            withHarness {
                progress.progressFor(UUID).value = BookProgressResult.Failed(BookProgressError.Unreachable, emptyList())

                vm.reload(1)

                vm.uiState.value.progress.shouldBeNull()
            }
        }
    }
})
