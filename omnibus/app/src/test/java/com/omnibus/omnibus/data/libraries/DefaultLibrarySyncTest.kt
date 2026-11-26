package com.omnibus.omnibus.data.libraries

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** A participant whose answers the test sets. */
private class FakeParticipant(
    val pending: Boolean = false,
    val throwsOnCheck: Boolean = false,
    val send: suspend () -> Boolean = { true },
) : LibrarySyncParticipant {

    /** How many times [sendPending] ran. */
    var sends = 0

    override suspend fun hasPending(): Boolean {
        if (throwsOnCheck) error("check failed")
        return pending
    }

    override suspend fun sendPending(): Boolean {
        sends++
        return send()
    }
}

/** The ways one participant's send can fail. */
private enum class FailedSend { ReturnsFalse, Throws }

private fun failingParticipant(failure: FailedSend): FakeParticipant = FakeParticipant(
    send = {
        when (failure) {
            FailedSend.ReturnsFalse -> false
            FailedSend.Throws -> error("send failed")
        }
    },
)

class DefaultLibrarySyncTest : DescribeSpec({

    fun TestScope.sut(participants: Set<LibrarySyncParticipant> = emptySet()): DefaultLibrarySync =
        DefaultLibrarySync(participants = participants, scope = backgroundScope)

    describe("reporting unsynced changes") {

        it("#369 TC-3: is true when one participant has a change waiting") {
            runTest {
                val waiting = FakeParticipant(pending = true)
                val idle = FakeParticipant(pending = false)

                sut(setOf(waiting, idle)).hasUnsyncedChanges().shouldBeTrue()
            }
        }

        it("#369 TC-3: is false when no participant has a change waiting") {
            runTest {
                sut(setOf(FakeParticipant(), FakeParticipant())).hasUnsyncedChanges().shouldBeFalse()
            }
        }

        it("#369 TC-3: is false when there are no participants") {
            runTest {
                sut(emptySet()).hasUnsyncedChanges().shouldBeFalse()
            }
        }

        it("#369 TC-3: is true when a participant's check throws") {
            runTest {
                val broken = FakeParticipant(throwsOnCheck = true)

                sut(setOf(broken)).hasUnsyncedChanges().shouldBeTrue()
            }
        }
    }

    describe("syncing the active library") {

        it("#369 TC-4: is Synced when every participant sends") {
            runTest {
                val participants = setOf(FakeParticipant(), FakeParticipant(), FakeParticipant())

                sut(participants).syncActive() shouldBe LibrarySyncResult.Synced
                participants.forEach { it.sends shouldBe 1 }
            }
        }

        it("#369 TC-4: is Failed when one participant's send fails") {
            runTest {
                checkAll(Exhaustive.enum<FailedSend>()) { failure ->
                    val participants = setOf(failingParticipant(failure), FakeParticipant())

                    sut(participants).syncActive() shouldBe LibrarySyncResult.Failed
                }
            }
        }

        it("#369 TC-4: still runs the other participants when one participant's send fails") {
            runTest {
                checkAll(Exhaustive.enum<FailedSend>()) { failure ->
                    val other = FakeParticipant()
                    val participants = setOf(failingParticipant(failure), other)

                    sut(participants).syncActive()

                    other.sends shouldBe 1
                }
            }
        }
    }

    describe("the sync limit") {

        /** A participant whose send waits on [gate], which the test opens or leaves shut. */
        fun gatedParticipant(gate: CompletableDeferred<Boolean>, finished: () -> Unit) = FakeParticipant(
            send = { gate.await().also { finished() } },
        )

        it("#369 TC-5: is Failed once the limit has passed") {
            runTest {
                val gate = CompletableDeferred<Boolean>()
                val sync = sut(setOf(gatedParticipant(gate) {}))
                val result = async { sync.syncActive() }

                advanceTimeBy(SYNC_LIMIT)
                runCurrent()

                result.await() shouldBe LibrarySyncResult.Failed
            }
        }

        it("#369 TC-5: is not returned before the limit") {
            runTest {
                val gate = CompletableDeferred<Boolean>()
                val sync = sut(setOf(gatedParticipant(gate) {}))
                val result = async { sync.syncActive() }

                advanceTimeBy(SYNC_LIMIT - 1.milliseconds)
                runCurrent()

                result.isCompleted.shouldBeFalse()
            }
        }

        it("#369 TC-5: does not cancel a send still running at the limit") {
            runTest {
                val gate = CompletableDeferred<Boolean>()
                var finished = false
                val sync = sut(setOf(gatedParticipant(gate) { finished = true }))
                val result = async { sync.syncActive() }
                advanceTimeBy(SYNC_LIMIT)
                runCurrent()
                result.await()

                gate.complete(true)
                runCurrent()

                finished.shouldBeTrue()
            }
        }
    }
})
