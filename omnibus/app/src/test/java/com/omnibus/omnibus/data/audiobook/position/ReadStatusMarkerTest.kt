package com.omnibus.omnibus.data.audiobook.position

import com.omnibus.omnibus.data.audiobook.playback.UnloadReason
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlayingBook
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusRecord
import com.omnibus.omnibus.data.readstatus.ReadStatusRepository
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.readstatus.TestReadStatusRepository
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private fun known(status: ReadStatus): ReadStatusState =
    ReadStatusState.Known(ReadStatusRecord(status, updatedAtMillis = randomLong(), finishedAtMillis = null))

/** First-play and end-of-book read status (I-SESS-4, P-SYNC-5). */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadStatusMarkerTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    fun TestScope.sut(readStatus: ReadStatusRepository = TestReadStatusRepository()) =
        ReadStatusMarker(readStatus, backgroundScope)

    describe("the first play of a load") {

        context("when the book is unread") {
            // TC-74
            it("TC-74: marks it reading (I-SESS-4)") {
                runTest {
                    val book = randomPlayingBook()
                    val state = playbackState(book, isPlaying = true)
                    val readStatus = TestReadStatusRepository()
                    readStatus.seed(book.bookUuid, known(ReadStatus.Unread))
                    val marker = sut(readStatus)
                    marker.onSettled(state, randomDouble())

                    marker.onPlay(state)
                    runCurrent()

                    readStatus.setStatusCalls shouldBe listOf(book.bookUuid to ReadStatus.Reading)
                }
            }

            // TC-76
            it("TC-76: leaves a play of an unloaded book alone (I-SESS-4)") {
                runTest {
                    val book = randomPlayingBook()
                    val state = playbackState(book, isPlaying = true)
                    val readStatus = TestReadStatusRepository()
                    readStatus.seed(book.bookUuid, known(ReadStatus.Unread))
                    val marker = sut(readStatus)
                    marker.onSettled(state, randomDouble())
                    marker.onUnload(state, randomEnum<UnloadReason>())

                    marker.onPlay(state)
                    runCurrent()

                    readStatus.setStatusCalls.shouldBeEmpty()
                }
            }
        }

        // TC-74
        it("TC-74: leaves each other read status alone (I-SESS-4)") {
            checkAll(
                Exhaustive.of(
                    known(ReadStatus.Reading),
                    known(ReadStatus.Finished),
                    ReadStatusState.Unknown,
                    ReadStatusState.Failed,
                ),
            ) { status ->
                runTest {
                    val book = randomPlayingBook()
                    val state = playbackState(book, isPlaying = true)
                    val readStatus = TestReadStatusRepository()
                    readStatus.readResults.addLast(status)
                    val marker = sut(readStatus)
                    marker.onSettled(state, randomDouble())

                    marker.onPlay(state)
                    runCurrent()

                    readStatus.setStatusCalls.shouldBeEmpty()
                }
            }
        }

        context("when the listener marks the book unread again") {
            // TC-75: only the first play of a load marks
            it("TC-75: leaves it unread on a later play of the load (I-SESS-4)") {
                runTest {
                    val book = randomPlayingBook()
                    val state = playbackState(book, isPlaying = true)
                    val readStatus = TestReadStatusRepository()
                    readStatus.seed(book.bookUuid, known(ReadStatus.Unread))
                    val marker = sut(readStatus)
                    marker.onSettled(state, randomDouble())
                    marker.onPlay(state)
                    runCurrent()
                    readStatus.seed(book.bookUuid, known(ReadStatus.Unread))

                    marker.onPlay(state)
                    runCurrent()

                    readStatus.setStatusCalls.size shouldBe 1
                }
            }

            // TC-75
            it("TC-75: marks it reading on the first play of the next load (I-SESS-4)") {
                runTest {
                    val book = randomPlayingBook()
                    val state = playbackState(book, isPlaying = true)
                    val readStatus = TestReadStatusRepository()
                    readStatus.seed(book.bookUuid, known(ReadStatus.Unread))
                    val marker = sut(readStatus)
                    marker.onSettled(state, randomDouble())
                    marker.onPlay(state)
                    runCurrent()
                    readStatus.seed(book.bookUuid, known(ReadStatus.Unread))

                    marker.onSettled(state, randomDouble())
                    marker.onPlay(state)
                    runCurrent()

                    readStatus.setStatusCalls.size shouldBe 2
                }
            }
        }
    }

    describe("the end of the book") {

        // TC-78
        it("TC-78: marks a known, unfinished book finished, for each such status (I-SESS-4, P-SYNC-5)") {
            checkAll(Exhaustive.of(ReadStatus.Reading, ReadStatus.Unread)) { status ->
                runTest {
                    val book = randomPlayingBook()
                    val readStatus = TestReadStatusRepository()
                    readStatus.readResults.addLast(known(status))

                    sut(readStatus).onEnded(playbackState(book))
                    runCurrent()

                    readStatus.setStatusCalls shouldBe listOf(book.bookUuid to ReadStatus.Finished)
                }
            }
        }

        // TC-78
        it("TC-78: writes nothing for a finished book or an unknown status (I-SESS-4, P-SYNC-5)") {
            checkAll(Exhaustive.of(known(ReadStatus.Finished), ReadStatusState.Unknown, ReadStatusState.Failed)) { status ->
                runTest {
                    val readStatus = TestReadStatusRepository()
                    readStatus.readResults.addLast(status)

                    sut(readStatus).onEnded(playbackState(randomPlayingBook()))
                    runCurrent()

                    readStatus.setStatusCalls.shouldBeEmpty()
                }
            }
        }
    }
})
