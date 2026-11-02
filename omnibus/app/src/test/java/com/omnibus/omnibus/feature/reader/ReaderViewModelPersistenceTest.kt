package com.omnibus.omnibus.feature.reader

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.omnibus.omnibus.data.library.FakeLibraryRepository
import com.omnibus.omnibus.data.progress.FakeProgressRepository
import com.omnibus.omnibus.data.progress.PositionSaveResult
import com.omnibus.omnibus.data.progress.ProgressError
import com.omnibus.omnibus.data.progress.ReadingPosition
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.reader.ui.ReaderEvent
import com.omnibus.omnibus.feature.reader.ui.RelocateData
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** The reader's position persistence: what is written, pushed and flushed, and when. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelPersistenceTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    lateinit var library: FakeLibraryRepository
    lateinit var fake: FakeProgressRepository
    lateinit var persistScope: CoroutineScope
    var now = 5_000_500L

    beforeEach {
        // The book stays Loading, so these cases only see the position state.
        library = FakeLibraryRepository().apply { pendingBook = CompletableDeferred() }
        fake = FakeProgressRepository()
        now = 5_000_500L
        persistScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    afterEach { persistScope.cancel() }

    /** The one place these cases build the ViewModel. Reads [fake], [now] and [persistScope] when called. */
    fun viewModel() = ReaderViewModel(
        bookUuid = "book-1",
        libraryRepository = library,
        assetLoaderFactory = ReaderAssetLoaderFactory(
            mockk(relaxed = true),
            Dispatchers.Unconfined,
            BookFileSource { BookFileResult.NotFound },
        ),
        progressRepository = fake,
        timeSource = TimeSource { now },
        persistScope = persistScope,
    )

    fun relocate(cfi: String?, pct: Int = 10, totalPages: Int = 100, echo: Boolean = false) =
        ReaderEvent.Relocate(RelocateData(cfi = cfi, pct = pct, totalPages = totalPages, echo = echo))

    /** The open's landing: the first relocate, which is never written. */
    fun ReaderViewModel.land() = onPageEvent(relocate("epubcfi(/6/2)"))

    fun ReaderViewModel.move(cfi: String, pct: Int = 10) = onPageEvent(relocate(cfi, pct = pct))

    fun position(cfi: String, percent: Int? = 10, at: Long = 5_000) =
        ReadingPosition("book-1", cfi, percent, at)

    describe("recording movements") {

        it("TC-22: a movement is written with its cfi, percent and a seconds clock") {
            val vm = viewModel()
            vm.land()

            vm.move("epubcfi(/6/8)", pct = 40)
            // totalPages 0 means the percent is unknown, so it is not written.
            vm.onPageEvent(relocate("epubcfi(/6/10)", pct = 7, totalPages = 0))

            fake.saves shouldBe listOf(
                FakeProgressRepository.Save(position("epubcfi(/6/8)", percent = 40), push = false),
                FakeProgressRepository.Save(position("epubcfi(/6/10)", percent = null), push = true),
            )
        }

        it("TC-25: the landing, blank cfis and an unmoved cfi are not written") {
            val vm = viewModel()
            vm.land()
            vm.onPageEvent(relocate(""))
            vm.onPageEvent(relocate("  "))
            fake.saves shouldBe emptyList()

            vm.move("epubcfi(/6/8)")
            vm.move("epubcfi(/6/8)")
            fake.saves.map { it.position.cfi } shouldBe listOf("epubcfi(/6/8)")

            vm.move("epubcfi(/6/10)")
            fake.saves.map { it.position.cfi } shouldBe listOf("epubcfi(/6/8)", "epubcfi(/6/10)")
        }

        it("TC-27: the local write is never gated by the throttle") {
            val vm = viewModel()
            vm.land()

            (1..10).forEach { vm.move("epubcfi(/6/${it * 2})") }

            fake.saves.size shouldBe 10
            fake.saves.count { it.push } shouldBe 1
        }

        it("TC-28: an echo is never written or pushed, but it does update the location") {
            val vm = viewModel()
            vm.land()
            vm.onPageEvent(relocate("epubcfi(/6/8)", echo = true))

            vm.uiState.value.location?.cfi shouldBe "epubcfi(/6/8)"
            vm.uiState.value.location?.echo shouldBe true

            vm.onReaderClosed()
            fake.saves shouldBe emptyList()
        }
    }

    describe("flushing") {

        it("TC-30: closing or stopping flushes the latest position with a forced push") {
            val triggers = listOf<Pair<String, (ReaderViewModel) -> Unit>>(
                "onReaderClosed" to { vm -> vm.onReaderClosed() },
                "onReaderStopped" to { vm -> vm.onReaderStopped() },
            )
            triggers.forEach { (name, trigger) ->
                fake = FakeProgressRepository()
                val vm = viewModel()
                vm.land()
                // The first movement is suppressed, the second pushes, and the third is inside the window.
                vm.move("epubcfi(/6/8)")
                vm.move("epubcfi(/6/10)")
                vm.move("epubcfi(/6/12)")

                trigger(vm)

                withClue(name) {
                    fake.saves.last() shouldBe FakeProgressRepository.Save(position("epubcfi(/6/12)"), push = true)
                }
            }
        }

        it("TC-32: a flush with nothing unpushed sends nothing") {
            // No movement since the open, then a close.
            fake = FakeProgressRepository()
            val idle = viewModel()
            idle.land()
            idle.onReaderClosed()
            fake.saves shouldBe emptyList()

            // The latest position was already pushed, so a close adds nothing.
            fake = FakeProgressRepository()
            val pushed = viewModel()
            pushed.land()
            pushed.move("epubcfi(/6/8)")
            pushed.move("epubcfi(/6/10)")
            val beforeClose = fake.saves.toList()
            pushed.onReaderClosed()
            fake.saves shouldBe beforeClose

            // A position still pending is flushed once, and the stop and close after it add nothing.
            fake = FakeProgressRepository()
            val pending = viewModel()
            pending.land()
            pending.move("epubcfi(/6/8)")
            pending.move("epubcfi(/6/10)")
            pending.move("epubcfi(/6/12)")
            pending.onReaderClosed()
            val afterFlush = fake.saves.toList()
            afterFlush.size shouldBe 4
            pending.onReaderStopped()
            pending.onReaderClosed()
            fake.saves shouldBe afterFlush
        }

        it("TC-34: the flush outlives the view model, both after a close and when only cleared") {
            // (a) The forced save is still in flight when the view model is cleared, and it completes after.
            val store = ViewModelStore()
            val factory = viewModelFactory { initializer { viewModel() } }
            val closed = ViewModelProvider.create(store, factory)[ReaderViewModel::class]
            closed.land()
            closed.move("epubcfi(/6/8)")
            fake.holdSaves = CompletableDeferred()

            closed.onReaderClosed()
            store.clear()
            fake.completedSaves.contains(FakeProgressRepository.Save(position("epubcfi(/6/8)"), true)) shouldBe false

            fake.holdSaves!!.complete(Unit)
            fake.completedSaves.last() shouldBe FakeProgressRepository.Save(position("epubcfi(/6/8)"), push = true)

            // (b) Cleared with no close: onCleared still sends the pending position.
            fake = FakeProgressRepository()
            val cleared = ViewModelStore()
            val clearedVm = ViewModelProvider.create(cleared, viewModelFactory { initializer { viewModel() } })[ReaderViewModel::class]
            clearedVm.land()
            clearedVm.move("epubcfi(/6/8)")
            fake.saves.last() shouldBe FakeProgressRepository.Save(position("epubcfi(/6/8)"), push = false)

            cleared.clear()
            fake.saves.last() shouldBe FakeProgressRepository.Save(position("epubcfi(/6/8)"), push = true)
        }

        it("TC-36: a failed push stays pending and goes out with the next push") {
            val failures = listOf<PositionSaveResult>(
                PositionSaveResult.Failed(ProgressError.Unreachable),
                PositionSaveResult.NoServer,
            )
            failures.forEach { failure ->
                fake = FakeProgressRepository().apply { pushResult = failure }
                val vm = viewModel()
                vm.land()
                vm.move("epubcfi(/6/8)")
                vm.move("epubcfi(/6/10)")
                vm.move("epubcfi(/6/12)")

                // The second movement pushed and failed, so the third is written unpushed and stays pending.
                fake.saves shouldBe listOf(
                    FakeProgressRepository.Save(position("epubcfi(/6/8)"), push = false),
                    FakeProgressRepository.Save(position("epubcfi(/6/10)"), push = true),
                    FakeProgressRepository.Save(position("epubcfi(/6/12)"), push = false),
                )

                vm.onReaderClosed()

                fake.saves.last() shouldBe FakeProgressRepository.Save(position("epubcfi(/6/12)"), push = true)
            }
        }

        it("TC-37: a skipped push is superseded by the next one, and no push carries the skipped position") {
            val vm = viewModel()
            vm.land()
            vm.move("epubcfi(/6/8)")
            now += 4_001
            vm.move("epubcfi(/6/10)")

            fake.saves shouldBe listOf(
                FakeProgressRepository.Save(position("epubcfi(/6/8)"), push = false),
                FakeProgressRepository.Save(position("epubcfi(/6/10)", at = 5_004), push = true),
            )
        }
    }
})
