package com.omnibus.omnibus.data.audiobook.position

import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.PendingResume
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.testing.TestTimeSource
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** A local position of [seconds], saved against [file] at [clock]. */
private fun local(seconds: Double, file: Long, clock: Long) =
    randomAudioPosition().copy(positionSeconds = seconds, bookFileId = file, clientUpdatedAt = clock)

/** Starts [PendingResume.settle] for [file] on the background scope and runs what is due. */
private fun TestScope.settle(pending: PendingResume, file: Long): Deferred<Double?> =
    backgroundScope.async { pending.settle(file, randomDouble(1.0, 40_000.0)) }.also { runCurrent() }

/** Moves the virtual clock by [millis], running everything that falls due. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.advance(millis: Long) {
    advanceTimeBy(millis)
    runCurrent()
}

/**
 * The resume decision (C5): the local read at begin, the server read with its 1200 ms deadline, the choice of start, the
 * late offer and cancellation. All time is virtual.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultResumeStartResolverTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    fun TestScope.syncOver(repository: ListeningProgressRepository) = DefaultListeningPositionSync(
        repository,
        dagger.Lazy<AudiobookController> { TestAudiobookController() },
        TestTimeSource(),
        backgroundScope,
    )

    fun TestScope.sut(
        repository: ListeningProgressRepository = TestListeningProgressRepository(),
        sync: DefaultListeningPositionSync = syncOver(repository),
    ) = DefaultResumeStartResolver(repository, sync, backgroundScope)

    describe("starting at an explicit position") {

        // TC-36: even one under the 1 s floor
        it("TC-36: starts there as given, for each position over the server's (I-RES-4)") {
            checkAll(Exhaustive.of(42.5, 0.5)) { explicit ->
                runTest {
                    val book = randomString()
                    val repository = TestListeningProgressRepository()
                    repository.serverAnswers[book] = CompletableDeferred(randomServerAudioPosition())

                    val pending = sut(repository).begin(book, null, explicit)

                    settle(pending, randomLong(1L..100L)).getCompleted() shouldBe explicit
                }
            }
        }
    }

    describe("the saved file") {

        // TC-37
        it("TC-37: answers from local storage with the server silent, for each saved position (I-RES-1)") {
            checkAll(Exhaustive.of(randomAudioPosition(), null)) { saved ->
                runTest {
                    val book = randomString()
                    val repository = TestListeningProgressRepository()
                    repository.locals[book] = saved
                    repository.serverAnswers[book] = CompletableDeferred()
                    val pending = sut(repository).begin(book, null, null)

                    val fileId = backgroundScope.async { pending.savedFileId() }
                    runCurrent()

                    fileId.getCompleted() shouldBe saved?.bookFileId
                }
            }
        }
    }

    describe("choosing the start") {

        // TC-31, TC-38: a tie keeps local, and a server position of 0 never wins
        it("TC-31, TC-38: starts at the newer position, for each pair of clocks (I-RES-1)") {
            checkAll(
                Exhaustive.of(
                    Triple(local(100.0, 6, 10), ServerAudioPosition(200.0, 6, 20), 200.0),
                    Triple(local(100.0, 6, 20), ServerAudioPosition(200.0, 6, 10), 100.0),
                    Triple(local(100.0, 6, 10), ServerAudioPosition(200.0, 6, 10), 100.0),
                    Triple(local(100.0, 6, 10), ServerAudioPosition(0.0, 6, 20), 100.0),
                    Triple(null, ServerAudioPosition(200.0, 6, 20), 200.0),
                    Triple(local(100.0, 6, 10), null, 100.0),
                ),
            ) { (saved, answer, expected) ->
                runTest {
                    val book = randomString()
                    val repository = TestListeningProgressRepository()
                    repository.locals[book] = saved
                    answer?.let { repository.serverAnswers[book] = CompletableDeferred(it) }

                    val pending = sut(repository).begin(book, null, null)

                    settle(pending, 6L).getCompleted() shouldBe expected
                }
            }
        }

        // TC-32, TC-38: a null record file or a loaded file of 0 matches any
        it("TC-32, TC-38: applies the position only to a matching file, for each pair of files (I-RES-3)") {
            checkAll(
                Exhaustive.of<Triple<Long?, Long, Double?>>(
                    Triple(6L, 6L, 200.0),
                    Triple(5L, 6L, null),
                    Triple(null, 6L, 200.0),
                    Triple(5L, 0L, 200.0),
                ),
            ) { (recordFile, loadedFile, expected) ->
                runTest {
                    val book = randomString()
                    val repository = TestListeningProgressRepository()
                    repository.serverAnswers[book] = CompletableDeferred(ServerAudioPosition(200.0, recordFile, 20))

                    val pending = sut(repository).begin(book, null, null)

                    settle(pending, loadedFile).getCompleted() shouldBe expected
                }
            }
        }

        // TC-33, TC-38: the 1 s floor is exclusive
        it("TC-33, TC-38: starts at 0 for a position of 1 s or less (I-RES-3)") {
            checkAll(Exhaustive.of<Pair<Double, Double?>>(1.0 to null, 1.01 to 1.01)) { (seconds, expected) ->
                runTest {
                    val book = randomString()
                    val repository = TestListeningProgressRepository()
                    repository.serverAnswers[book] = CompletableDeferred(ServerAudioPosition(seconds, 6, 20))

                    val pending = sut(repository).begin(book, null, null)

                    settle(pending, 6L).getCompleted() shouldBe expected
                }
            }
        }

        context("when neither side has a position") {
            // TC-39
            it("TC-39: starts at 0 with no wait (I-RES-2)") {
                runTest {
                    val pending = sut().begin(randomString(), null, null)

                    settle(pending, randomLong(1L..100L)).getCompleted().shouldBeNull()
                }
            }
        }
    }

    describe("the server's deadline") {

        context("when the server answers just inside it") {
            // TC-40
            it("TC-40: starts at the server's position (I-RES-2)") {
                runTest {
                    val book = randomString()
                    val gate = CompletableDeferred<ServerAudioPosition?>()
                    val repository = TestListeningProgressRepository()
                    repository.locals[book] = local(100.0, 6, 10)
                    repository.serverAnswers[book] = gate
                    val result = settle(sut(repository).begin(book, null, null), 6L)

                    advance(1_199)
                    gate.complete(ServerAudioPosition(200.0, 6, 20))
                    runCurrent()

                    result.getCompleted() shouldBe 200.0
                }
            }
        }

        context("when the server stays silent") {
            // TC-40
            it("TC-40: starts at the local position at 1200 ms, not before (I-RES-2)") {
                runTest {
                    val book = randomString()
                    val repository = TestListeningProgressRepository()
                    repository.locals[book] = local(100.0, 6, 10)
                    repository.serverAnswers[book] = CompletableDeferred()
                    val result = settle(sut(repository).begin(book, null, null), 6L)

                    advance(1_199)
                    result.isCompleted shouldBe false

                    advance(1)
                    result.getCompleted() shouldBe 100.0
                }
            }
        }

        context("when the settle comes late") {
            // TC-41: the deadline runs from begin
            it("TC-41: waits only for what is left of the deadline (I-RES-2)") {
                runTest {
                    val book = randomString()
                    val repository = TestListeningProgressRepository()
                    repository.locals[book] = local(100.0, 6, 10)
                    repository.serverAnswers[book] = CompletableDeferred()
                    val pending = sut(repository).begin(book, null, null)
                    advance(1_000)
                    val result = settle(pending, 6L)

                    advance(199)
                    result.isCompleted shouldBe false

                    advance(1)
                    result.getCompleted() shouldBe 100.0
                }
            }

            // TC-42
            it("TC-42: uses an answer already in hand (I-RES-2)") {
                runTest {
                    val book = randomString()
                    val gate = CompletableDeferred<ServerAudioPosition?>()
                    val repository = TestListeningProgressRepository()
                    repository.locals[book] = local(100.0, 6, 10)
                    repository.serverAnswers[book] = gate
                    val pending = sut(repository).begin(book, null, null)
                    advance(500)
                    gate.complete(ServerAudioPosition(200.0, 6, 20))
                    advance(2_500)

                    settle(pending, 6L).getCompleted() shouldBe 200.0
                }
            }
        }
    }

    describe("the late offer") {

        context("when an answer lands after the deadline") {
            // TC-44
            it("TC-44: offers a newer position ahead of the start (I-RES-6)") {
                runTest {
                    val book = randomString()
                    val gate = CompletableDeferred<ServerAudioPosition?>()
                    val repository = TestListeningProgressRepository()
                    repository.locals[book] = local(30.0, 6, 10)
                    repository.serverAnswers[book] = gate
                    val sync = syncOver(repository)
                    settle(sut(repository, sync).begin(book, null, null), 6L)
                    advance(1_200)

                    gate.complete(ServerAudioPosition(50.0, 6, 20))
                    runCurrent()

                    sync.lateServerPosition.value shouldBe LatePosition(book, 6, 50.0)
                }
            }

            // TC-34, TC-45
            it("TC-34, TC-45: offers nothing for each answer that fails a rule (I-RES-6)") {
                checkAll(
                    Exhaustive.of(
                        ServerAudioPosition(31.0, 6, 20),
                        ServerAudioPosition(60.0, 6, 10),
                        ServerAudioPosition(60.0, 6, 9),
                        ServerAudioPosition(60.0, 7, 20),
                        null,
                    ),
                ) { late ->
                    runTest {
                        val book = randomString()
                        val gate = CompletableDeferred<ServerAudioPosition?>()
                        val repository = TestListeningProgressRepository()
                        repository.locals[book] = local(30.0, 6, 10)
                        repository.serverAnswers[book] = gate
                        val sync = syncOver(repository)
                        settle(sut(repository, sync).begin(book, null, null), 6L)
                        advance(1_200)

                        gate.complete(late)
                        runCurrent()

                        sync.lateServerPosition.value.shouldBeNull()
                    }
                }
            }

            context("when the load was replaced") {
                // TC-47
                it("TC-47: offers nothing (I-RES-6)") {
                    runTest {
                        val book = randomString()
                        val gate = CompletableDeferred<ServerAudioPosition?>()
                        val repository = TestListeningProgressRepository()
                        repository.locals[book] = local(30.0, 6, 10)
                        repository.serverAnswers[book] = gate
                        val sync = syncOver(repository)
                        val resolver = sut(repository, sync)
                        settle(resolver.begin(book, null, null), 6L)
                        advance(1_200)
                        resolver.begin("$book-other", null, null)
                        runCurrent()

                        gate.complete(ServerAudioPosition(60.0, 6, 20))
                        runCurrent()

                        sync.lateServerPosition.value.shouldBeNull()
                    }
                }
            }

            context("when the load was cancelled") {
                // TC-48
                it("TC-48: offers nothing (CC1)") {
                    runTest {
                        val book = randomString()
                        val gate = CompletableDeferred<ServerAudioPosition?>()
                        val repository = TestListeningProgressRepository()
                        repository.locals[book] = local(30.0, 6, 10)
                        repository.serverAnswers[book] = gate
                        val sync = syncOver(repository)
                        val pending = sut(repository, sync).begin(book, null, null)
                        settle(pending, 6L)
                        advance(1_200)
                        pending.cancel()
                        runCurrent()

                        gate.complete(ServerAudioPosition(60.0, 6, 20))
                        runCurrent()

                        sync.lateServerPosition.value.shouldBeNull()
                    }
                }
            }
        }

        context("when the answer arrived in time") {
            // TC-46
            it("TC-46: offers nothing later (I-RES-6)") {
                runTest {
                    val book = randomString()
                    val repository = TestListeningProgressRepository()
                    repository.serverAnswers[book] = CompletableDeferred(ServerAudioPosition(500.0, 6, 20))
                    val sync = syncOver(repository)
                    settle(sut(repository, sync).begin(book, null, null), 6L)

                    advance(10_000)

                    sync.lateServerPosition.value.shouldBeNull()
                }
            }
        }
    }

    describe("abandoning a load") {

        context("when it is cancelled during the wait") {
            // TC-48
            it("TC-48: stops the server read (CC1)") {
                runTest {
                    val book = randomString()
                    val repository = TestListeningProgressRepository()
                    repository.serverAnswers[book] = CompletableDeferred()
                    val pending = sut(repository).begin(book, null, null)
                    settle(pending, 6L)

                    pending.cancel()
                    runCurrent()

                    repository.serverCancellations shouldBe 1
                }
            }
        }
    }

    describe("settling again") {

        // TC-50
        it("TC-50: answers the first start, whatever has changed since (CC1)") {
            runTest {
                val book = randomString()
                val repository = TestListeningProgressRepository()
                repository.locals[book] = local(100.0, 6, 10)
                val pending = sut(repository).begin(book, null, null)
                settle(pending, 6L)

                settle(pending, 7L).getCompleted() shouldBe 100.0
            }
        }
    }
})
