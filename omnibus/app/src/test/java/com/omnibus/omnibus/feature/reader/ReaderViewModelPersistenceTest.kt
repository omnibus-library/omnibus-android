package com.omnibus.omnibus.feature.reader

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.omnibus.omnibus.data.library.TestLibraryRepository
import com.omnibus.omnibus.data.progress.PositionSaveResult
import com.omnibus.omnibus.data.progress.ProgressError
import com.omnibus.omnibus.data.progress.ReadingPosition
import com.omnibus.omnibus.data.progress.ServerPositionResult
import com.omnibus.omnibus.data.progress.TestProgressRepository
import com.omnibus.omnibus.data.progress.randomReadingPosition
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.reader.ui.ReaderEvent
import com.omnibus.omnibus.feature.reader.ui.randomCfi
import com.omnibus.omnibus.feature.reader.ui.randomRelocateData
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** A repository whose pushes succeed. */
private fun pushing() = TestProgressRepository().apply { pushResult = PositionSaveResult.Pushed }

/** The reader's position persistence: what is written, pushed and flushed, and when. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelPersistenceTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun ReaderViewModel.relocateTo(cfi: String?, pct: Int = randomInt(0..100), echo: Boolean = false) = onPageEvent(
        ReaderEvent.Relocate(
            randomRelocateData().copy(cfi = cfi, pct = pct, totalPages = randomInt(1..1000), echo = echo, settledSeek = null),
        ),
    )

    /** The open's landing, then a suppressed first push and a real second one, so the next movement stays unpushed. */
    fun ReaderViewModel.landAndPush() {
        repeat(3) { relocateTo(randomCfi()) }
    }

    fun ViewModelStore.sut(progress: TestProgressRepository) =
        ViewModelProvider.create(this, viewModelFactory { initializer { sut(progressRepository = progress) } })[ReaderViewModel::class]

    describe("recording movements") {
        it("TC-22: writes a movement with its cfi, percent and a seconds clock") {
            val uuid = randomString()
            val now = randomLong()
            val progress = pushing()
            val vm = sut(bookUuid = uuid, progressRepository = progress, timeSource = TimeSource { now })
            vm.relocateTo(randomCfi())
            val cfi = randomCfi()
            val pct = randomInt(0..100)

            vm.relocateTo(cfi, pct)

            progress.saves.single().position shouldBe ReadingPosition(uuid, cfi, pct, now / 1000)
        }

        it("TC-25: skips the landing, a blank cfi or an unmoved cfi") {
            val progress = pushing()
            val vm = sut(progressRepository = progress)
            val cfi = randomCfi()

            vm.relocateTo(randomCfi())
            vm.relocateTo("")
            vm.relocateTo("  ")
            progress.saves.shouldBeEmpty()

            vm.relocateTo(cfi)
            vm.relocateTo(cfi)
            progress.saves.map { it.position.cfi } shouldBe listOf(cfi)
        }

        it("TC-27: writes every movement locally, however fast they come") {
            val progress = pushing()
            val vm = sut(progressRepository = progress)
            vm.relocateTo(randomCfi())

            repeat(10) { vm.relocateTo(randomCfi()) }

            progress.saves.size shouldBe 10
        }

        it("TC-28: never writes an echo") {
            val progress = pushing()
            val vm = sut(progressRepository = progress)
            vm.relocateTo(randomCfi())

            vm.relocateTo(randomCfi(), echo = true)
            vm.onReaderClosed()

            progress.saves.shouldBeEmpty()
        }
    }

    describe("flushing") {
        context("with a position not yet pushed") {
            it("TC-30: pushes it on a close or a stop") {
                checkAll(
                    Exhaustive.of<(ReaderViewModel) -> Unit>({ it.onReaderClosed() }, { it.onReaderStopped() }),
                ) { leave ->
                    val progress = pushing()
                    val vm = sut(progressRepository = progress)
                    vm.landAndPush()
                    val cfi = randomCfi()
                    vm.relocateTo(cfi)

                    leave(vm)

                    progress.saves.last().let { it.position.cfi to it.push } shouldBe (cfi to true)
                }
            }

            it("TC-32: pushes it only once") {
                val progress = pushing()
                val vm = sut(progressRepository = progress)
                vm.landAndPush()
                vm.relocateTo(randomCfi())
                vm.onReaderClosed()
                val afterFlush = progress.saves.toList()

                vm.onReaderStopped()
                vm.onReaderClosed()

                progress.saves shouldBe afterFlush
            }

            context("when the view model is cleared without a close") {
                it("TC-34: still pushes it") {
                    val progress = pushing()
                    val store = ViewModelStore()
                    val vm = store.sut(progress)
                    vm.relocateTo(randomCfi())
                    val cfi = randomCfi()
                    vm.relocateTo(cfi)

                    store.clear()

                    progress.saves.last().let { it.position.cfi to it.push } shouldBe (cfi to true)
                }
            }
        }

        context("with a push in flight when the view model is cleared") {
            it("TC-34: completes the push") {
                val progress = pushing()
                val store = ViewModelStore()
                val vm = store.sut(progress)
                vm.relocateTo(randomCfi())
                vm.relocateTo(randomCfi())
                val hold = CompletableDeferred<Unit>()
                progress.holdSaves = hold
                vm.onReaderClosed()
                store.clear()
                progress.completedSaves.count { it.push } shouldBe 0

                hold.complete(Unit)

                progress.completedSaves.count { it.push } shouldBe 1
            }
        }

        context("after a failed push") {
            it("TC-36: pushes the position again, for an unreachable or missing server") {
                checkAll(
                    Exhaustive.of<PositionSaveResult>(
                        PositionSaveResult.Failed(ProgressError.Unreachable),
                        PositionSaveResult.NoServer,
                    ),
                ) { failure ->
                    val progress = TestProgressRepository().apply { pushResult = failure }
                    val vm = sut(progressRepository = progress)
                    vm.relocateTo(randomCfi())
                    vm.relocateTo(randomCfi())
                    val cfi = randomCfi()
                    vm.relocateTo(cfi)

                    vm.onReaderClosed()

                    progress.saves.count { it.position.cfi == cfi && it.push } shouldBe 2
                }
            }
        }
    }

    describe("opening") {
        context("while the book is still loading") {
            it("TC-41: resolves the stored position") {
                val uuid = randomString()
                val cfi = randomCfi()
                val progress = TestProgressRepository().apply {
                    stored[uuid] = randomReadingPosition().copy(bookUuid = uuid, cfi = cfi)
                    serverResult = ServerPositionResult.None
                }
                val library = TestLibraryRepository().apply { pendingBook = CompletableDeferred() }

                val vm = sut(bookUuid = uuid, progressRepository = progress, libraryRepository = library)

                vm.uiState.value.opening shouldBe ReaderOpening.Resolved(ReaderStart.AtCfi(cfi))
            }
        }

        context("with an explicit start") {
            it("#117 TC-51: writes a percent below the stored one as it is") {
                val uuid = randomString()
                val start = randomCfi()
                val progress = TestProgressRepository().apply {
                    stored[uuid] = randomReadingPosition().copy(bookUuid = uuid, percent = 57)
                }
                val vm = sut(bookUuid = uuid, progressRepository = progress, startCfi = start)
                vm.relocateTo(start, pct = 20)

                vm.relocateTo(randomCfi(), pct = 3)

                progress.saves.single().position.percent shouldBe 3
            }
        }
    }
})
