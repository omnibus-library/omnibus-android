package com.omnibus.omnibus.feature.reader

import app.cash.turbine.test
import com.omnibus.omnibus.data.progress.PositionSync
import com.omnibus.omnibus.data.progress.ReadingPosition
import com.omnibus.omnibus.data.progress.ServerPositionResult
import com.omnibus.omnibus.data.progress.ServerReadingPosition
import com.omnibus.omnibus.data.progress.TestProgressRepository
import com.omnibus.omnibus.data.progress.randomReadingPosition
import com.omnibus.omnibus.data.progress.randomServerReadingPosition
import com.omnibus.omnibus.feature.reader.ui.ReaderEvent
import com.omnibus.omnibus.feature.reader.ui.ReaderLoadStatus
import com.omnibus.omnibus.feature.reader.ui.randomCfi
import com.omnibus.omnibus.feature.reader.ui.randomRelocateData
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private val DEADLINE = PositionSync.OPEN_DEADLINE.inWholeMilliseconds

/** Runs [block] with the main dispatcher on a fresh virtual clock, so a deadline is exact. */
@OptIn(ExperimentalCoroutinesApi::class)
private inline fun onVirtualTime(block: (TestCoroutineScheduler) -> Unit) {
    val scheduler = TestCoroutineScheduler()
    Dispatchers.setMain(StandardTestDispatcher(scheduler))
    try {
        block(scheduler)
    } finally {
        Dispatchers.resetMain()
    }
}

/** Moves virtual time forward by [millis], running everything due at the new time. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun TestCoroutineScheduler.advance(millis: Long) {
    advanceTimeBy(millis)
    runCurrent()
}

/**
 * Opening against the server: which position the page boots at, when a late answer is offered, and
 * how an offer is accepted or dismissed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelResumeTest : DescribeSpec({

    fun localPosition() = randomReadingPosition().copy(cfi = randomCfi(), clientUpdatedAt = randomLong(1_000L..1_000_000L))

    fun localPercentOnly() = randomReadingPosition().copy(cfi = null, percent = 57, clientUpdatedAt = randomLong(1_000L..1_000_000L))

    /** A server position with its own cfi, newer than [local]. */
    fun newerThan(local: ReadingPosition) = randomServerReadingPosition()
        .copy(cfi = randomCfi(), clientUpdatedAt = local.clientUpdatedAt + randomLong(1L..1_000L))

    /** A server position that carries only a percent, newer than any local position. */
    fun percentOnlyRecord() = randomServerReadingPosition()
        .copy(cfi = null, derivedCfi = null, percent = 57, clientUpdatedAt = randomLong(1L..1_000L))

    fun progressFor(local: ReadingPosition, answer: ServerPositionResult, afterMillis: Long) =
        TestProgressRepository().apply {
            stored[local.bookUuid] = local
            serverResult = answer
            serverDelayMillis = afterMillis
        }

    fun found(record: ServerReadingPosition) = ServerPositionResult.Found(record)

    fun ReaderViewModel.pageReady() = onPageEvent(ReaderEvent.LoadStatusChanged(ReaderLoadStatus.Ready))

    fun ReaderViewModel.relocateTo(cfi: String?, pct: Int = 50, echo: Boolean = false, settled: Int? = null) = onPageEvent(
        ReaderEvent.Relocate(randomRelocateData().copy(cfi = cfi, pct = pct, totalPages = 100, echo = echo, settledSeek = settled)),
    )

    describe("the opening deadline") {
        context("with an answer just before the deadline") {
            it("TC-64: adopts a newer server position") {
                onVirtualTime { scheduler ->
                    val local = localPosition()
                    val record = newerThan(local)
                    val vm = sut(local.bookUuid, progressRepository = progressFor(local, found(record), DEADLINE - 1))
                    scheduler.advance(DEADLINE - 2)
                    vm.uiState.value.opening shouldBe ReaderOpening.Resolving

                    scheduler.advance(1)

                    vm.uiState.value.opening shouldBe ReaderOpening.Resolved(ReaderStart.AtCfi(record.cfi!!))
                }
            }
        }

        context("with an answer exactly at the deadline") {
            it("TC-65: opens locally, offers the position and saves nothing") {
                onVirtualTime { scheduler ->
                    val local = localPosition()
                    val record = newerThan(local)
                    val progress = progressFor(local, found(record), DEADLINE)
                    val vm = sut(local.bookUuid, progressRepository = progress)

                    scheduler.advance(DEADLINE)

                    vm.uiState.value.opening shouldBe ReaderOpening.Resolved(ReaderStart.AtCfi(local.cfi!!))
                    vm.uiState.value.syncOffer shouldBe SyncOffer(ReaderStart.AtCfi(record.cfi!!))
                    progress.saves.shouldBeEmpty()
                }
            }
        }

        context("when the server never answers") {
            it("TC-64: opens locally at the deadline") {
                onVirtualTime { scheduler ->
                    val local = localPosition()
                    val progress = progressFor(local, ServerPositionResult.None, 0).apply {
                        serverAnswer = CompletableDeferred()
                    }
                    val vm = sut(local.bookUuid, progressRepository = progress)
                    scheduler.advance(DEADLINE - 1)
                    vm.uiState.value.opening shouldBe ReaderOpening.Resolving

                    scheduler.advance(1)

                    vm.uiState.value.opening shouldBe ReaderOpening.Resolved(ReaderStart.AtCfi(local.cfi!!))
                }
            }
        }

        context("with a failed or empty answer") {
            it("TC-66: opens locally without waiting, for an error or no position") {
                checkAll(Exhaustive.of(ServerPositionResult.Error, ServerPositionResult.None)) { answer ->
                    onVirtualTime { scheduler ->
                        val local = localPosition()
                        val vm = sut(local.bookUuid, progressRepository = progressFor(local, answer, 100))

                        scheduler.advance(100)

                        vm.uiState.value.opening shouldBe ReaderOpening.Resolved(ReaderStart.AtCfi(local.cfi!!))
                    }
                }
            }
        }
    }

    describe("an answer in time") {
        context("with an older position") {
            it("TC-71: opens locally and saves nothing") {
                onVirtualTime { scheduler ->
                    val local = localPosition()
                    val record = newerThan(local).copy(clientUpdatedAt = local.clientUpdatedAt - 1)
                    val progress = progressFor(local, found(record), 100)
                    val vm = sut(local.bookUuid, progressRepository = progress)

                    scheduler.advance(100)

                    vm.uiState.value.opening shouldBe ReaderOpening.Resolved(ReaderStart.AtCfi(local.cfi!!))
                    progress.saves.shouldBeEmpty()
                }
            }
        }

        context("with a newer position") {
            it("TC-74: saves it locally without a push") {
                onVirtualTime { scheduler ->
                    val local = localPosition()
                    val record = newerThan(local)
                    val progress = progressFor(local, found(record), 100)
                    sut(local.bookUuid, progressRepository = progress)

                    scheduler.advance(100)

                    progress.saves shouldContainExactly listOf(
                        TestProgressRepository.Save(
                            ReadingPosition(local.bookUuid, record.cfi, record.percent, record.orderingClock),
                            push = false,
                        ),
                    )
                }
            }
        }
    }

    describe("a late answer") {
        context("without a different newer position") {
            it("TC-78: makes no offer, for an older record, the same start or an error") {
                val local = localPosition()

                checkAll(
                    Exhaustive.of(
                        found(newerThan(local).copy(clientUpdatedAt = local.clientUpdatedAt - 1)),
                        found(newerThan(local).copy(cfi = local.cfi)),
                        ServerPositionResult.Error,
                    ),
                ) { answer ->
                    onVirtualTime { scheduler ->
                        val vm = sut(local.bookUuid, progressRepository = progressFor(local, answer, DEADLINE + 100))

                        scheduler.advance(DEADLINE + 100)

                        vm.uiState.value.syncOffer shouldBe null
                    }
                }
            }
        }

        context("when the offer is accepted") {
            it("TC-81: moves to it once, for a cfi or a percent-only offer") {
                val local = localPosition()
                val cfiRecord = newerThan(local)

                checkAll(
                    Exhaustive.of(
                        cfiRecord to ReaderCommand.Display(cfiRecord.cfi!!),
                        percentOnlyRecord().copy(clientUpdatedAt = cfiRecord.clientUpdatedAt) to ReaderCommand.Seek(0.57),
                    ),
                ) { (record, command) ->
                    onVirtualTime { scheduler ->
                        val vm = sut(local.bookUuid, progressRepository = progressFor(local, found(record), DEADLINE + 100))
                        scheduler.advance(DEADLINE + 100)

                        vm.onSyncOfferAccepted()
                        vm.onSyncOfferAccepted()

                        vm.uiState.value.syncOffer shouldBe null
                        vm.commands.test {
                            awaitItem() shouldBe command
                            expectNoEvents()
                        }
                    }
                }
            }
        }

        context("when the offer is dismissed") {
            it("TC-82: is gone for good") {
                onVirtualTime { scheduler ->
                    val local = localPosition()
                    val vm = sut(local.bookUuid, progressRepository = progressFor(local, found(newerThan(local)), DEADLINE + 100))
                    scheduler.advance(DEADLINE + 100)

                    vm.onSyncOfferDismissed()
                    vm.onSyncOfferAccepted()

                    vm.uiState.value.syncOffer shouldBe null
                    vm.commands.test { expectNoEvents() }
                }
            }
        }
    }

    describe("a percent-only opening") {
        context("with a derived cfi") {
            it("TC-85: boots there without a seek") {
                onVirtualTime { scheduler ->
                    val uuid = randomString()
                    val derived = randomCfi()
                    val progress = TestProgressRepository().apply {
                        serverResult = found(percentOnlyRecord().copy(derivedCfi = derived))
                    }
                    val vm = sut(uuid, progressRepository = progress)
                    scheduler.runCurrent()

                    vm.pageReady()

                    vm.uiState.value.opening shouldBe ReaderOpening.Resolved(ReaderStart.AtPercent(0.57, derived))
                    vm.commands.test { expectNoEvents() }
                }
            }
        }

        context("without a derived cfi") {
            it("TC-86: seeks once, the first time the page is ready") {
                onVirtualTime { scheduler ->
                    val progress = TestProgressRepository().apply { serverResult = found(percentOnlyRecord()) }
                    val vm = sut(progressRepository = progress)
                    scheduler.runCurrent()

                    vm.pageReady()
                    vm.onPageEvent(ReaderEvent.LoadStatusChanged(ReaderLoadStatus.Loading))
                    vm.pageReady()

                    vm.commands.test {
                        awaitItem() shouldBe ReaderCommand.Seek(0.57)
                        expectNoEvents()
                    }
                }
            }
        }

        context("until the reader moves") {
            it("TC-98: writes nothing, past the boot landing, the seek's landing, a stop or a close") {
                onVirtualTime { scheduler ->
                    val local = localPercentOnly()
                    val progress = progressFor(local, ServerPositionResult.None, 0)
                    val vm = sut(local.bookUuid, progressRepository = progress)
                    scheduler.runCurrent()
                    vm.pageReady()

                    vm.relocateTo(randomCfi(), pct = 3)
                    vm.relocateTo(randomCfi(), pct = 57, settled = 1)
                    vm.onReaderStopped()
                    vm.onReaderClosed()

                    progress.saves.shouldBeEmpty()
                }
            }
        }

        context("when the reader moves off the seek's landing") {
            it("TC-99: writes the move") {
                onVirtualTime { scheduler ->
                    val local = localPercentOnly()
                    val progress = progressFor(local, ServerPositionResult.None, 0)
                    val vm = sut(local.bookUuid, progressRepository = progress)
                    scheduler.runCurrent()
                    vm.pageReady()
                    vm.relocateTo(randomCfi(), pct = 3)
                    vm.relocateTo(randomCfi(), pct = 57, settled = 1)
                    val moved = randomCfi()

                    vm.relocateTo(moved, pct = 60)

                    progress.saves.map { it.position.cfi } shouldContainExactly listOf(moved)
                }
            }
        }

        context("when an offer is accepted during the hold") {
            it("TC-99: writes the landing of that move") {
                onVirtualTime { scheduler ->
                    val local = localPercentOnly()
                    val record = newerThan(local)
                    val progress = progressFor(local, found(record), DEADLINE + 100)
                    val vm = sut(local.bookUuid, progressRepository = progress)
                    scheduler.advance(DEADLINE)
                    vm.pageReady()
                    vm.relocateTo(randomCfi(), pct = 3)
                    scheduler.advance(100)

                    vm.onSyncOfferAccepted()
                    vm.relocateTo(record.cfi, pct = 60)

                    progress.saves.map { it.position.cfi } shouldContainExactly listOf(record.cfi)
                }
            }
        }
    }

    describe("reading forward from an adopted position") {
        it("TC-105: never lowers its stored percent") {
            onVirtualTime { scheduler ->
                val local = localPosition()
                val record = newerThan(local).copy(percent = 57)
                val progress = progressFor(local, found(record), 100)
                val vm = sut(local.bookUuid, progressRepository = progress)
                scheduler.advance(100)
                vm.relocateTo(record.cfi, pct = 50, echo = true)
                val moved = randomCfi()

                vm.relocateTo(moved, pct = 52)

                progress.saves.last().position.let { it.cfi to it.percent } shouldBe (moved to 57)
            }
        }
    }

    describe("an explicit start") {
        context("with an EPUB cfi") {
            it("#117 TC-49: opens there with no server read") {
                onVirtualTime { scheduler ->
                    val local = localPosition()
                    val progress = progressFor(local, found(newerThan(local)), 100)
                    val start = randomCfi()

                    val vm = sut(local.bookUuid, progressRepository = progress, startCfi = start)
                    scheduler.advance(DEADLINE + 100)

                    vm.uiState.value.opening shouldBe ReaderOpening.Resolved(ReaderStart.AtCfi(start))
                    progress.serverReads.shouldBeEmpty()
                }
            }
        }

        context("without an EPUB cfi") {
            it("#117 TC-50: takes the normal resume path") {
                onVirtualTime { scheduler ->
                    val local = localPosition()
                    val record = newerThan(local)
                    val progress = progressFor(local, found(record), 100)

                    val vm = sut(local.bookUuid, progressRepository = progress, startCfi = "chapter-3")
                    scheduler.advance(100)

                    vm.uiState.value.opening shouldBe ReaderOpening.Resolved(ReaderStart.AtCfi(record.cfi!!))
                }
            }
        }
    }
})
