package com.omnibus.omnibus.data.audiobook.position

import com.omnibus.omnibus.data.audiobook.playback.UnloadReason
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.playingBook
import com.omnibus.omnibus.data.readstatus.TestReadStatusRepository
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusError
import com.omnibus.omnibus.data.readstatus.ReadStatusRecord
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.readstatus.ReadStatusWriteResult
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** A known read status of [status], with no times. */
private fun known(status: ReadStatus): ReadStatusState =
    ReadStatusState.Known(ReadStatusRecord(status, updatedAtMillis = 1L, finishedAtMillis = null))

/**
 * First-play and end-of-book read status (I-SESS-4, P-SYNC-5). Each marker call is followed by a run of the virtual
 * scheduler, so the launched fetch and write have finished before the assertion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadStatusMarkerTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    lateinit var scheduler: TestCoroutineScheduler
    lateinit var scope: CoroutineScope
    lateinit var readStatus: TestReadStatusRepository
    lateinit var marker: ReadStatusMarker

    beforeEach {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        readStatus = TestReadStatusRepository()
        marker = ReadStatusMarker(readStatus, scope)
    }

    afterEach {
        scope.cancel()
        Dispatchers.resetMain()
    }

    describe("first play") {
        // TC-74: the first play marks only an unread book reading (I-SESS-4)
        it("TC-74: the first play marks only an unread book reading (I-SESS-4)") {
            val rows = listOf<Pair<ReadStatusState, List<Pair<String, ReadStatus>>>>(
                known(ReadStatus.Unread) to listOf("book-1" to ReadStatus.Reading),
                known(ReadStatus.Reading) to emptyList(),
                known(ReadStatus.Finished) to emptyList(),
                ReadStatusState.Unknown to emptyList(),
                ReadStatusState.Failed to emptyList(),
            )
            rows.forEach { (state, expectedCalls) ->
                val fresh = TestReadStatusRepository()
                val freshMarker = ReadStatusMarker(fresh, scope)
                fresh.readResults.addLast(state)
                val playing = playbackState(isPlaying = true)
                freshMarker.onSettled(playing, 0.0)
                freshMarker.onPlay(playing)
                scheduler.runCurrent()
                withClue("read result $state") {
                    fresh.setStatusCalls shouldBe expectedCalls
                }
            }
        }

        // TC-75: once per load, re-armed by each settle (I-SESS-4)
        it("TC-75: once per load, re-armed by each settle (I-SESS-4)") {
            val playing = playbackState(isPlaying = true)
            marker.onSettled(playing, 0.0)
            marker.onPlay(playing)
            scheduler.runCurrent()
            marker.onPlay(playing)
            scheduler.runCurrent()
            readStatus.fetchCalls.size shouldBe 1

            marker.onSettled(playing, 0.0)
            marker.onPlay(playing)
            scheduler.runCurrent()
            readStatus.fetchCalls.size shouldBe 2
        }

        // TC-76: a play that is not the first of the settled load does nothing
        it("TC-76: a play that is not the first of the settled load does nothing") {
            val book1 = playbackState(isPlaying = true)
            val book2 = playbackState(book = playingBook(bookUuid = "book-2"), isPlaying = true)

            val noSettle = TestReadStatusRepository()
            ReadStatusMarker(noSettle, scope).onPlay(book1)
            scheduler.runCurrent()
            withClue("a play with no settle") {
                noSettle.fetchCalls.shouldBeEmpty()
            }

            val otherBook = TestReadStatusRepository()
            val otherMarker = ReadStatusMarker(otherBook, scope)
            otherMarker.onSettled(book1, 0.0)
            otherMarker.onPlay(book2)
            scheduler.runCurrent()
            withClue("a play of another book") {
                otherBook.fetchCalls.shouldBeEmpty()
            }

            val unloaded = TestReadStatusRepository()
            val unloadedMarker = ReadStatusMarker(unloaded, scope)
            unloadedMarker.onSettled(book1, 0.0)
            unloadedMarker.onUnload(book1, UnloadReason.Stop)
            unloadedMarker.onPlay(book1)
            scheduler.runCurrent()
            withClue("a play after unload") {
                unloaded.fetchCalls.shouldBeEmpty()
            }
        }

        // TC-77: a failed mark is not retried (I-SESS-4)
        it("TC-77: a failed mark is not retried (I-SESS-4)") {
            readStatus.writeResults.addLast(ReadStatusWriteResult.Failed(ReadStatusError.Unreachable))
            val playing = playbackState(isPlaying = true)
            marker.onSettled(playing, 0.0)
            marker.onPlay(playing)
            scheduler.runCurrent()
            readStatus.setStatusCalls.size shouldBe 1

            scheduler.advanceTimeBy(60_000)
            scheduler.runCurrent()
            readStatus.setStatusCalls.size shouldBe 1
        }
    }

    describe("finished") {
        // TC-78: the end marks a known, unfinished book finished (I-SESS-4, P-SYNC-5)
        it("TC-78: the end marks a known, unfinished book finished (I-SESS-4, P-SYNC-5)") {
            val rows = listOf<Pair<ReadStatusState, List<Pair<String, ReadStatus>>>>(
                known(ReadStatus.Reading) to listOf("book-1" to ReadStatus.Finished),
                known(ReadStatus.Unread) to listOf("book-1" to ReadStatus.Finished),
                known(ReadStatus.Finished) to emptyList(),
                ReadStatusState.Unknown to emptyList(),
                ReadStatusState.Failed to emptyList(),
            )
            rows.forEach { (state, expectedCalls) ->
                val fresh = TestReadStatusRepository()
                val freshMarker = ReadStatusMarker(fresh, scope)
                fresh.readResults.addLast(state)
                freshMarker.onEnded(playbackState(isPlaying = false, positionSeconds = 2_700.0))
                scheduler.runCurrent()
                withClue("read result $state") {
                    fresh.setStatusCalls shouldBe expectedCalls
                }
            }
        }
    }
})
