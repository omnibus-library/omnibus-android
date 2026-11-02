package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.FakeLibraryRepository
import com.omnibus.omnibus.data.progress.FakeProgressRepository
import com.omnibus.omnibus.data.progress.ReadingPosition
import com.omnibus.omnibus.data.progress.ServerPositionResult
import com.omnibus.omnibus.data.progress.ServerReadingPosition
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.reader.ui.ReaderEvent
import com.omnibus.omnibus.feature.reader.ui.ReaderLoadStatus
import com.omnibus.omnibus.feature.reader.ui.RelocateData
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Opening against the server: which position the page boots at, when a late answer is offered, and
 * how an offer is accepted or dismissed. Runs on virtual time, so the 1200 ms deadline is exact.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelResumeTest : DescribeSpec({

    lateinit var scheduler: TestCoroutineScheduler
    lateinit var library: FakeLibraryRepository
    lateinit var fake: FakeProgressRepository
    lateinit var persistScope: CoroutineScope
    var now = 5_000_500L

    val local = ReadingPosition("book-1", "epubcfi(/6/2)", 3, 100)
    val localStart = ReaderStart.AtCfi("epubcfi(/6/2)")

    beforeEach {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        library = FakeLibraryRepository()
        fake = FakeProgressRepository()
        now = 5_000_500L
        persistScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    afterEach {
        persistScope.cancel()
        Dispatchers.resetMain()
    }

    /** The one place these cases build the ViewModel. Reads [fake], [library], [now] and [persistScope] when called. */
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

    /** Starts a ViewModel and lets its init run up to the first suspension. */
    fun openedViewModel(): ReaderViewModel = viewModel().also { scheduler.runCurrent() }

    /** Moves virtual time forward by [millis], running everything due at the new time. Scenarios share one clock, so each counts from its own open. */
    fun advance(millis: Long) {
        scheduler.advanceTimeBy(millis)
        scheduler.runCurrent()
    }

    /** Collects the commands [vm] sends, from now on. */
    fun ReaderViewModel.collectCommands(): MutableList<ReaderCommand> {
        val received = mutableListOf<ReaderCommand>()
        persistScope.launch { commands.collect { received += it } }
        scheduler.runCurrent()
        return received
    }

    fun record(cfi: String? = "epubcfi(/6/10)", client: Long? = 200, percent: Int? = 57, updated: Long = 0) =
        ServerReadingPosition(cfi = cfi, percent = percent, derivedCfi = null, updatedAt = updated, clientUpdatedAt = client)

    fun ReaderViewModel.landing() = onPageEvent(ReaderEvent.Relocate(RelocateData(cfi = "epubcfi(/6/2)", pct = 3, totalPages = 100)))

    fun ReaderViewModel.jump(cfi: String, pct: Int = 60) =
        onPageEvent(ReaderEvent.Relocate(RelocateData(cfi = cfi, pct = pct, totalPages = 100)))

    describe("the deadline") {

        // TC-64: the server answers at 1199 ms (in time) or 1201 ms (late), or never answers.
        it("TC-64: the deadline decides between adopting and offering") {
            fake.stored["book-1"] = local

            // Answers at 1199 ms: adopted.
            fake.serverResult = ServerPositionResult.Found(record(cfi = "epubcfi(/6/10)"))
            fake.serverDelayMillis = 1199
            val inTime = openedViewModel()
            advance(1198)
            withClue("in time at 1198") { inTime.uiState.value.opening shouldBe ReaderOpening.Resolving }
            advance(1)
            withClue("in time at 1199") {
                inTime.uiState.value.opening shouldBe ReaderOpening.Resolved(ReaderStart.AtCfi("epubcfi(/6/10)"))
                inTime.uiState.value.syncOffer shouldBe null
            }

            // Answers at 1201 ms: late, so the page opens locally and the server's position is offered.
            fake = FakeProgressRepository().apply {
                stored["book-1"] = local
                serverResult = ServerPositionResult.Found(record(cfi = "epubcfi(/6/10)"))
                serverDelayMillis = 1201
            }
            val late = openedViewModel()
            advance(1199)
            withClue("late at 1199") { late.uiState.value.opening shouldBe ReaderOpening.Resolving }
            advance(1)
            withClue("late at 1200") { late.uiState.value.opening shouldBe ReaderOpening.Resolved(localStart) }
            advance(1)
            withClue("late at 1201") {
                late.uiState.value.syncOffer shouldBe SyncOffer(ReaderStart.AtCfi("epubcfi(/6/10)"))
                late.uiState.value.opening shouldBe ReaderOpening.Resolved(localStart)
                fake.serverReads.size shouldBe 1
            }

            // Never answers: the page opens locally at the deadline, and nothing is offered.
            fake = FakeProgressRepository().apply {
                stored["book-1"] = local
                serverAnswer = CompletableDeferred()
            }
            val silent = openedViewModel()
            advance(1200)
            withClue("never") {
                silent.uiState.value.opening shouldBe ReaderOpening.Resolved(localStart)
                silent.uiState.value.syncOffer shouldBe null
            }
        }

        // TC-65: an answer at exactly 1200 ms is late, so it is offered and never adopted.
        it("TC-65: an answer exactly at the deadline is late") {
            fake.stored["book-1"] = local
            fake.serverResult = ServerPositionResult.Found(record(cfi = "epubcfi(/6/10)"))
            fake.serverDelayMillis = 1200
            val vm = openedViewModel()

            advance(1200)

            vm.uiState.value.opening shouldBe ReaderOpening.Resolved(localStart)
            vm.uiState.value.syncOffer shouldBe SyncOffer(ReaderStart.AtCfi("epubcfi(/6/10)"))
            fake.saves.shouldBeEmpty()
        }

        // TC-66: a failed or empty server read opens locally without waiting for the deadline.
        it("TC-66: a failed or empty server read opens locally without delay") {
            val rows = listOf(
                ServerPositionResult.Error to 100L,
                ServerPositionResult.None to 100L,
                ServerPositionResult.Error to 0L,
            )
            rows.forEach { (answer, delayMillis) ->
                withClue("$answer after $delayMillis ms") {
                    fake = FakeProgressRepository().apply {
                        stored["book-1"] = local
                        serverResult = answer
                        serverDelayMillis = delayMillis
                    }
                    val started = scheduler.currentTime
                    val vm = openedViewModel()

                    advance(delayMillis)

                    (scheduler.currentTime - started) shouldBe delayMillis
                    vm.uiState.value.opening shouldBe ReaderOpening.Resolved(localStart)
                    vm.uiState.value.syncOffer shouldBe null
                }
            }
        }

        // TC-69: the deadline runs alongside the book load, so it does not add to it.
        it("TC-69: the deadline runs concurrently with the book load") {
            fake.stored["book-1"] = local
            fake.serverAnswer = CompletableDeferred()
            library.pendingBook = CompletableDeferred()
            val vm = openedViewModel()

            advance(800)
            library.pendingBook!!.complete(library.bookResult)
            scheduler.runCurrent()
            withClue("book at 800") {
                vm.uiState.value.book.shouldBeTypeOf<ReaderBookState.Ready>()
                vm.uiState.value.opening shouldBe ReaderOpening.Resolving
            }

            advance(400)
            vm.uiState.value.opening shouldBe ReaderOpening.Resolved(localStart)
        }
    }

    describe("in time") {

        // TC-70: an in-time newer record is the opening position, with or without a local one.
        it("TC-70: an in-time newer server position is the opening position") {
            val rows = listOf<ReadingPosition?>(local, null)
            rows.forEach { stored ->
                withClue("local=$stored") {
                    fake = FakeProgressRepository().apply {
                        if (stored != null) this.stored["book-1"] = stored
                        serverResult = ServerPositionResult.Found(record(cfi = "epubcfi(/6/10)", client = 200))
                        serverDelayMillis = 100
                    }
                    val vm = openedViewModel()

                    advance(100)

                    vm.uiState.value.opening shouldBe ReaderOpening.Resolved(ReaderStart.AtCfi("epubcfi(/6/10)"))
                    vm.uiState.value.syncOffer shouldBe null
                }
            }
        }

        // TC-71: an in-time answer that isn't a newer position is ignored: the page opens locally, no offer, no save.
        it("TC-71: in-time answers that are not newer positions are ignored") {
            val rows = listOf(
                "older" to record(client = 50),
                "tied" to record(client = 100),
                "no position" to record(cfi = "", percent = null, client = Long.MAX_VALUE / 2),
                "non-cfi anchor" to record(cfi = "pdf-page:3", percent = null, client = 1_000),
            )
            rows.forEach { (label, answer) ->
                withClue(label) {
                    fake = FakeProgressRepository().apply {
                        stored["book-1"] = local
                        serverResult = ServerPositionResult.Found(answer)
                        serverDelayMillis = 100
                    }
                    val vm = openedViewModel()

                    advance(100)

                    vm.uiState.value.opening shouldBe ReaderOpening.Resolved(localStart)
                    vm.uiState.value.syncOffer shouldBe null
                    fake.saves.shouldBeEmpty()
                }
            }
        }

        // TC-74: an adopted position is saved locally (no push), and the next open starts there with the server gone.
        it("TC-74: an adopted position is saved locally without a push") {
            fake.stored["book-1"] = local
            fake.serverResult = ServerPositionResult.Found(record(cfi = "epubcfi(/6/10)", percent = 57, client = 200))
            fake.serverDelayMillis = 100
            openedViewModel()
            advance(100)

            fake.saves shouldContainExactly listOf(
                FakeProgressRepository.Save(ReadingPosition("book-1", "epubcfi(/6/10)", 57, 200), push = false),
            )

            // A later open with the server failing starts at the adopted position.
            fake.serverResult = ServerPositionResult.Error
            fake.serverDelayMillis = 0
            val reopened = openedViewModel()
            advance(0)
            reopened.uiState.value.opening shouldBe ReaderOpening.Resolved(ReaderStart.AtCfi("epubcfi(/6/10)"))
        }
    }

    describe("late answers") {

        // TC-77: a late newer record is offered, and never moves the page or is saved.
        it("TC-77: a late newer position becomes an offer and never moves the page") {
            val rows = listOf(
                record(cfi = "epubcfi(/6/10)") to ReaderStart.AtCfi("epubcfi(/6/10)"),
                record(cfi = null, percent = 57) to ReaderStart.AtPercent(0.57, null),
            )
            rows.forEach { (answer, target) ->
                withClue("$answer") {
                    fake = FakeProgressRepository().apply {
                        stored["book-1"] = local
                        serverResult = ServerPositionResult.Found(answer)
                        serverDelayMillis = 1300
                    }
                    val vm = openedViewModel()
                    val commands = vm.collectCommands()

                    advance(1300)

                    vm.uiState.value.syncOffer shouldBe SyncOffer(target)
                    vm.uiState.value.opening shouldBe ReaderOpening.Resolved(localStart)
                    commands.shouldBeEmpty()
                    fake.saves.shouldBeEmpty()
                }
            }
        }

        // TC-78: a late answer that isn't a different newer position makes no offer.
        it("TC-78: late answers that aren't a different newer position make no offer") {
            val rows = listOf(
                "older" to ServerPositionResult.Found(record(client = 50)),
                "tied" to ServerPositionResult.Found(record(client = 100)),
                "no position" to ServerPositionResult.Found(record(cfi = "", percent = null, client = 500)),
                "none" to ServerPositionResult.None,
                "error" to ServerPositionResult.Error,
                "same start" to ServerPositionResult.Found(record(cfi = "epubcfi(/6/2)", percent = null, client = 200)),
            )
            rows.forEach { (label, answer) ->
                withClue(label) {
                    fake = FakeProgressRepository().apply {
                        stored["book-1"] = local
                        serverResult = answer
                        serverDelayMillis = 1300
                    }
                    val vm = openedViewModel()

                    advance(1300)

                    vm.uiState.value.syncOffer shouldBe null
                }
            }
        }

        // TC-81: accepting moves to the offer exactly once, and the landing of that move is saved.
        it("TC-81: accepting moves to the offer once and the landing is saved") {
            // A CFI offer.
            fake = FakeProgressRepository().apply {
                stored["book-1"] = local
                serverResult = ServerPositionResult.Found(record(cfi = "epubcfi(/6/10)"))
                serverDelayMillis = 1300
            }
            val cfiVm = openedViewModel()
            val cfiCommands = cfiVm.collectCommands()
            advance(1300)
            cfiVm.onSyncOfferAccepted()
            cfiVm.onSyncOfferAccepted()
            scheduler.runCurrent()
            cfiCommands shouldContainExactly listOf(ReaderCommand.Display("epubcfi(/6/10)"))
            cfiVm.uiState.value.syncOffer shouldBe null
            cfiVm.landing()
            cfiVm.jump("epubcfi(/6/10)")
            fake.saves.map { it.position.cfi } shouldContainExactly listOf("epubcfi(/6/10)")

            // A percent-only offer, on its own open.
            fake = FakeProgressRepository().apply {
                stored["book-1"] = local
                serverResult = ServerPositionResult.Found(record(cfi = null, percent = 57))
                serverDelayMillis = 1300
            }
            val percentVm = openedViewModel()
            val percentCommands = percentVm.collectCommands()
            advance(1300)
            percentVm.onSyncOfferAccepted()
            percentVm.onSyncOfferAccepted()
            scheduler.runCurrent()
            percentCommands shouldContainExactly listOf(ReaderCommand.Seek(0.57))
            percentVm.uiState.value.syncOffer shouldBe null
        }

        // TC-82: a dismissed offer is final, and accepting afterwards does nothing.
        it("TC-82: dismissing is final") {
            fake.stored["book-1"] = local
            fake.serverResult = ServerPositionResult.Found(record(cfi = "epubcfi(/6/10)"))
            fake.serverDelayMillis = 1300
            val vm = openedViewModel()
            val commands = vm.collectCommands()
            advance(1300)

            vm.onSyncOfferDismissed()
            vm.uiState.value.syncOffer shouldBe null
            vm.onSyncOfferAccepted()
            scheduler.runCurrent()

            commands.shouldBeEmpty()
            vm.uiState.value.syncOffer shouldBe null
        }
    }

    describe("percent-only opening") {

        // TC-85: a percent-only record with a derived CFI boots at that CFI, with no seek.
        it("TC-85: percent-only with a derived cfi boots there") {
            fake.serverResult = ServerPositionResult.Found(
                ServerReadingPosition(cfi = null, percent = 57, derivedCfi = "epubcfi(/6/9)", updatedAt = 0, clientUpdatedAt = 200),
            )
            fake.serverDelayMillis = 100
            val vm = openedViewModel()
            val commands = vm.collectCommands()
            advance(100)

            vm.onPageEvent(ReaderEvent.LoadStatusChanged(ReaderLoadStatus.Ready))
            scheduler.runCurrent()

            vm.uiState.value.opening shouldBe ReaderOpening.Resolved(ReaderStart.AtPercent(0.57, "epubcfi(/6/9)"))
            commands.shouldBeEmpty()
        }

        // TC-86: without a derived CFI the seek is sent once, when the page first becomes ready.
        it("TC-86: percent-only without a derived cfi seeks once when ready") {
            fake.serverResult = ServerPositionResult.Found(record(cfi = null, percent = 57))
            fake.serverDelayMillis = 100
            val vm = openedViewModel()
            val commands = vm.collectCommands()
            advance(100)
            commands.shouldBeEmpty()

            vm.onPageEvent(ReaderEvent.LoadStatusChanged(ReaderLoadStatus.Ready))
            scheduler.runCurrent()
            commands shouldContainExactly listOf(ReaderCommand.Seek(0.57))

            vm.onPageEvent(ReaderEvent.LoadStatusChanged(ReaderLoadStatus.Loading))
            vm.onPageEvent(ReaderEvent.LoadStatusChanged(ReaderLoadStatus.Ready))
            scheduler.runCurrent()
            commands shouldContainExactly listOf(ReaderCommand.Seek(0.57))
        }

        // TC-88: a local percent-only position opens near its percent when the server has nothing newer.
        it("TC-88: a local percent-only position opens near its percent") {
            fake.stored["book-1"] = ReadingPosition("book-1", null, 40, 100)
            fake.serverResult = ServerPositionResult.Error
            val vm = openedViewModel()
            val commands = vm.collectCommands()
            advance(0)

            vm.uiState.value.opening shouldBe ReaderOpening.Resolved(ReaderStart.AtPercent(0.4, null))
            vm.onPageEvent(ReaderEvent.LoadStatusChanged(ReaderLoadStatus.Ready))
            scheduler.runCurrent()
            commands shouldContainExactly listOf(ReaderCommand.Seek(0.4))
        }
    }
})
