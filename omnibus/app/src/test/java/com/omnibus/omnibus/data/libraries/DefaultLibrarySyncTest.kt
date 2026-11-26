package com.omnibus.omnibus.data.libraries

import com.omnibus.omnibus.data.audiobook.position.randomAudioPosition
import com.omnibus.omnibus.data.audiobook.position.randomListeningSessionReport
import com.omnibus.omnibus.data.libraries.local.LibraryTokenStore
import com.omnibus.omnibus.data.libraries.local.TestLibraryTokenStore
import com.omnibus.omnibus.data.libraries.remote.InactiveLibraryApi
import com.omnibus.omnibus.data.libraries.remote.TestInactiveLibraryApi
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl

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

/** A library that is not the active one. */
private fun inactiveLibrary(): Library = randomLibrary().copy(isActive = false)

/** Puts one pending position, two queued sessions and one pending rate in [rows] for [libraryId]. */
private fun addPendingRows(rows: TestInactiveLibraryRows, libraryId: String) {
    rows.positions[libraryId] = mutableListOf(randomAudioPosition())
    rows.sessions[libraryId] = mutableListOf(randomListeningSessionReport(), randomListeningSessionReport())
    rows.rates[libraryId] = mutableMapOf(randomString() to randomDouble(0.5, 3.0))
}

/** The ways one send of an inactive library's pending rows can fail. */
private enum class FailedInactiveSend { PositionRefused, PositionThrows, SessionsRefused, RateRefused, RateThrows, RowReadThrows }

/** Makes [api] or [rows] fail as [failure] describes. */
private fun applyFailure(failure: FailedInactiveSend, api: TestInactiveLibraryApi, rows: TestInactiveLibraryRows) {
    when (failure) {
        FailedInactiveSend.PositionRefused -> api.positionAnswer = { false }
        FailedInactiveSend.PositionThrows -> api.positionAnswer = { error("send failed") }
        FailedInactiveSend.SessionsRefused -> api.sessionsAnswer = { false }
        FailedInactiveSend.RateRefused -> api.rateAnswer = { _, _ -> false }
        FailedInactiveSend.RateThrows -> api.rateAnswer = { _, _ -> error("send failed") }
        FailedInactiveSend.RowReadThrows -> rows.readFailure = IOException("unreadable")
    }
}

/** Setups in which [syncInactive] must not reach a server: the library is unknown, or it can't be called. */
private enum class Unusable { UnknownLibrary, AddressDoesNotParse, NoToken, BlankToken }

/** The libraries and tokens for [case], with [library] as the library being synced. */
private fun unusableSetup(case: Unusable, library: Library): Pair<List<Library>, Map<String, String>> = when (case) {
    Unusable.UnknownLibrary -> emptyList<Library>() to mapOf(library.id to randomString())
    Unusable.AddressDoesNotParse -> listOf(library.copy(address = "")) to mapOf(library.id to randomString())
    Unusable.NoToken -> listOf(library) to emptyMap()
    Unusable.BlankToken -> listOf(library) to mapOf(library.id to " ")
}

class DefaultLibrarySyncTest : DescribeSpec({

    fun TestScope.sut(
        participants: Set<LibrarySyncParticipant> = emptySet(),
        libraries: LibrariesRepository = TestLibrariesRepository(),
        tokens: LibraryTokenStore = TestLibraryTokenStore(),
        rows: InactiveLibraryRows = TestInactiveLibraryRows(),
        api: InactiveLibraryApi = TestInactiveLibraryApi(),
    ): DefaultLibrarySync = DefaultLibrarySync(participants, backgroundScope, libraries, tokens, rows, api)

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

    describe("syncing an inactive library") {

        context("when the library has pending changes") {

            it("#373 TC-1: is Synced when the server accepts every change") {
                runTest {
                    val library = inactiveLibrary()
                    val rows = TestInactiveLibraryRows().also { addPendingRows(it, library.id) }

                    val result = sut(
                        libraries = TestLibrariesRepository(listOf(library)),
                        tokens = TestLibraryTokenStore(mapOf(library.id to randomString())),
                        rows = rows,
                        api = TestInactiveLibraryApi(),
                    ).syncInactive(library.id)

                    result shouldBe LibrarySyncResult.Synced
                }
            }

            it("#373 TC-1: clears every change the server accepted") {
                runTest {
                    val library = inactiveLibrary()
                    val rows = TestInactiveLibraryRows().also { addPendingRows(it, library.id) }

                    sut(
                        libraries = TestLibrariesRepository(listOf(library)),
                        tokens = TestLibraryTokenStore(mapOf(library.id to randomString())),
                        rows = rows,
                    ).syncInactive(library.id)

                    rows.pendingPositions(library.id) shouldBe emptyList()
                    rows.sessions(library.id) shouldBe emptyList()
                    rows.pendingRates(library.id) shouldBe emptyMap()
                }
            }
        }

        context("when nothing is pending") {

            it("#373 TC-1: is Synced") {
                runTest {
                    val library = inactiveLibrary()

                    val result = sut(
                        libraries = TestLibrariesRepository(listOf(library)),
                        tokens = TestLibraryTokenStore(mapOf(library.id to randomString())),
                    ).syncInactive(library.id)

                    result shouldBe LibrarySyncResult.Synced
                }
            }

            it("#373 TC-1: makes no request") {
                runTest {
                    val library = inactiveLibrary()
                    val api = TestInactiveLibraryApi()

                    sut(
                        libraries = TestLibrariesRepository(listOf(library)),
                        tokens = TestLibraryTokenStore(mapOf(library.id to randomString())),
                        api = api,
                    ).syncInactive(library.id)

                    api.calls shouldBe emptyList()
                }
            }
        }

        context("when a change cannot be sent") {

            it("#373 TC-2: is Failed when a send is refused or throws, or the rows can't be read") {
                runTest {
                    checkAll(Exhaustive.enum<FailedInactiveSend>()) { failure ->
                        val library = inactiveLibrary()
                        val rows = TestInactiveLibraryRows().also { addPendingRows(it, library.id) }
                        val api = TestInactiveLibraryApi().also { applyFailure(failure, it, rows) }

                        val result = sut(
                            libraries = TestLibrariesRepository(listOf(library)),
                            tokens = TestLibraryTokenStore(mapOf(library.id to randomString())),
                            rows = rows,
                            api = api,
                        ).syncInactive(library.id)

                        result shouldBe LibrarySyncResult.Failed
                    }
                }
            }

            it("#373 TC-2: keeps the change that failed and clears the other changes the server accepted") {
                runTest {
                    checkAll(
                        Exhaustive.of(
                            FailedInactiveSend.PositionRefused,
                            FailedInactiveSend.SessionsRefused,
                            FailedInactiveSend.RateRefused,
                        ),
                    ) { failure ->
                        val library = inactiveLibrary()
                        val rows = TestInactiveLibraryRows().also { addPendingRows(it, library.id) }
                        val pending = rows.positions.getValue(library.id).toList()
                        val queued = rows.sessions.getValue(library.id).toList()
                        val rates = rows.rates.getValue(library.id).toMap()
                        val api = TestInactiveLibraryApi().also { applyFailure(failure, it, rows) }

                        sut(
                            libraries = TestLibrariesRepository(listOf(library)),
                            tokens = TestLibraryTokenStore(mapOf(library.id to randomString())),
                            rows = rows,
                            api = api,
                        ).syncInactive(library.id)

                        rows.pendingPositions(library.id) shouldBe
                            if (failure == FailedInactiveSend.PositionRefused) pending else emptyList()
                        rows.sessions(library.id) shouldBe
                            if (failure == FailedInactiveSend.SessionsRefused) queued else emptyList()
                        rows.pendingRates(library.id) shouldBe
                            if (failure == FailedInactiveSend.RateRefused) rates else emptyMap()
                    }
                }
            }
        }

        context("when the sessions are more than one batch") {

            it("#373 TC-3: sends the queued sessions in batches of 500, in queue order") {
                runTest {
                    val library = inactiveLibrary()
                    val queued = List(1_001) { randomListeningSessionReport().copy(clientId = "session-$it") }
                    val rows = TestInactiveLibraryRows().also { it.sessions[library.id] = queued.toMutableList() }
                    val api = TestInactiveLibraryApi()

                    sut(
                        libraries = TestLibrariesRepository(listOf(library)),
                        tokens = TestLibraryTokenStore(mapOf(library.id to randomString())),
                        rows = rows,
                        api = api,
                    ).syncInactive(library.id)

                    val batches = api.calls.filterIsInstance<TestInactiveLibraryApi.SendSessions>().map { it.batch }
                    batches.map { it.size } shouldBe listOf(500, 500, 1)
                    batches.flatten() shouldBe queued
                }
            }

            it("#373 TC-3: stops at a refused batch, keeps the rest, and sends no later batch") {
                runTest {
                    val library = inactiveLibrary()
                    val queued = List(1_001) { randomListeningSessionReport().copy(clientId = "session-$it") }
                    val rows = TestInactiveLibraryRows().also { it.sessions[library.id] = queued.toMutableList() }
                    val api = TestInactiveLibraryApi()
                    var batchesSeen = 0
                    api.sessionsAnswer = { ++batchesSeen == 1 }

                    sut(
                        libraries = TestLibrariesRepository(listOf(library)),
                        tokens = TestLibraryTokenStore(mapOf(library.id to randomString())),
                        rows = rows,
                        api = api,
                    ).syncInactive(library.id)

                    api.calls.filterIsInstance<TestInactiveLibraryApi.SendSessions>().size shouldBe 2
                    rows.sessions(library.id) shouldBe queued.drop(500)
                }
            }
        }

        context("when the library can't be called") {

            it("#373 TC-4: is Failed with no request, for each unusable setup") {
                runTest {
                    checkAll(Exhaustive.enum<Unusable>()) { case ->
                        val library = inactiveLibrary()
                        val (libraries, tokens) = unusableSetup(case, library)
                        val rows = TestInactiveLibraryRows().also { addPendingRows(it, library.id) }
                        val api = TestInactiveLibraryApi()

                        val result = sut(
                            libraries = TestLibrariesRepository(libraries),
                            tokens = TestLibraryTokenStore(tokens),
                            rows = rows,
                            api = api,
                        ).syncInactive(library.id)

                        result shouldBe LibrarySyncResult.Failed
                        api.calls shouldBe emptyList()
                    }
                }
            }
        }

        context("when a send never answers") {

            it("#373 TC-5: is still waiting just before the limit") {
                runTest {
                    val library = inactiveLibrary()
                    val rows = TestInactiveLibraryRows().also { addPendingRows(it, library.id) }
                    val api = TestInactiveLibraryApi().also { it.sendGate = CompletableDeferred() }
                    val sync = sut(
                        libraries = TestLibrariesRepository(listOf(library)),
                        tokens = TestLibraryTokenStore(mapOf(library.id to randomString())),
                        rows = rows,
                        api = api,
                    )
                    val result = async { sync.syncInactive(library.id) }

                    advanceTimeBy(SYNC_LIMIT - 1.milliseconds)
                    runCurrent()

                    result.isCompleted.shouldBeFalse()
                }
            }

            it("#373 TC-5: is Failed once the limit has passed") {
                runTest {
                    val library = inactiveLibrary()
                    val rows = TestInactiveLibraryRows().also { addPendingRows(it, library.id) }
                    val api = TestInactiveLibraryApi().also { it.sendGate = CompletableDeferred() }
                    val sync = sut(
                        libraries = TestLibrariesRepository(listOf(library)),
                        tokens = TestLibraryTokenStore(mapOf(library.id to randomString())),
                        rows = rows,
                        api = api,
                    )
                    val result = async { sync.syncInactive(library.id) }

                    advanceTimeBy(SYNC_LIMIT)
                    runCurrent()

                    result.await() shouldBe LibrarySyncResult.Failed
                }
            }
        }

        it("#373 TC-6: sends only with the removed library's server and token, never the active library's") {
            runTest {
                val active = randomLibrary().copy(isActive = true)
                val removed = inactiveLibrary()
                val removedToken = randomString()
                val rows = TestInactiveLibraryRows().also { addPendingRows(it, removed.id) }
                val api = TestInactiveLibraryApi()

                sut(
                    libraries = TestLibrariesRepository(listOf(active, removed)),
                    tokens = TestLibraryTokenStore(mapOf(active.id to randomString(), removed.id to removedToken)),
                    rows = rows,
                    api = api,
                ).syncInactive(removed.id)

                api.calls.size shouldBe 3
                api.calls.forEach { call ->
                    call.baseUrl shouldBe removed.address.toHttpUrl()
                    call.token shouldBe removedToken
                }
            }
        }
    }
})
