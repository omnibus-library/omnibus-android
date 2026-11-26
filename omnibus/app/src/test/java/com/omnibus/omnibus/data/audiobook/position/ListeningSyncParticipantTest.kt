package com.omnibus.omnibus.data.audiobook.position

import com.omnibus.omnibus.data.audiobook.position.local.AudioPositionStore
import com.omnibus.omnibus.data.audiobook.position.local.TestAudioPositionStore
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.testing.ScriptedServer
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.reply
import com.omnibus.omnibus.testing.testOmnibusHttpClient
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.withScriptedServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.HttpUrl

/** Runs [block] with a scripted server and an application scope that flushes on the calling thread. */
private inline fun withServer(block: ScriptedServer.(CoroutineScope) -> Unit) {
    withScriptedServer {
        withScope { scope -> block(scope) }
    }
}

private inline fun withScope(block: (CoroutineScope) -> Unit) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
        block(scope)
    } finally {
        scope.cancel()
    }
}

/** The participant over a repository for [baseUrl] and [store]. Build it before any row is written, so no start-up flush sends one. */
private fun CoroutineScope.participant(
    baseUrl: HttpUrl,
    store: AudioPositionStore,
    connectivity: ConnectivityMonitor,
): ListeningSyncParticipant = ListeningSyncParticipant(
    repository = DefaultListeningProgressRepository(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = testOmnibusHttpClient(baseUrl),
        store = store,
        connectivity = connectivity,
        applicationScope = this,
    ),
    store = store,
)

class ListeningSyncParticipantTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("reporting listening writes waiting to be sent") {

        it("#369 TC-6: has nothing pending when the store is empty") {
            withServer { scope ->
                scope.participant(url, TestAudioPositionStore(), TestConnectivityMonitor(online = true))
                    .hasPending().shouldBeFalse()
            }
        }

        it("#369 TC-6: has a pending position once one is saved") {
            withServer { scope ->
                val store = TestAudioPositionStore()
                val participant = scope.participant(url, store, TestConnectivityMonitor(online = false))

                store.save(randomAudioPosition())

                participant.hasPending().shouldBeTrue()
            }
        }

        it("#369 TC-6: has a pending session once one is queued") {
            withServer { scope ->
                val store = TestAudioPositionStore()
                val participant = scope.participant(url, store, TestConnectivityMonitor(online = false))

                store.queueSessions(listOf(randomListeningSessionReport()))

                participant.hasPending().shouldBeTrue()
            }
        }
    }

    describe("sending the listening writes") {

        it("#369 TC-6: is true with nothing pending, even offline") {
            withServer { scope ->
                val participant = scope.participant(url, TestAudioPositionStore(), TestConnectivityMonitor(online = false))

                participant.sendPending() shouldBe true
            }
        }

        it("#369 TC-6: is true when every pending position is accepted") {
            withServer { scope ->
                val store = TestAudioPositionStore()
                val participant = scope.participant(url, store, TestConnectivityMonitor(online = true))
                store.save(randomAudioPosition())
                script.answer(200)

                participant.sendPending() shouldBe true
                store.pendingPositions.shouldBeEmpty()
            }
        }

        it("#369 TC-6: is true when every queued session is accepted") {
            withServer { scope ->
                val store = TestAudioPositionStore()
                val participant = scope.participant(url, store, TestConnectivityMonitor(online = true))
                store.queueSessions(listOf(randomListeningSessionReport()))
                script.answer(200)

                participant.sendPending() shouldBe true
                store.sessions().shouldBeEmpty()
            }
        }

        it("#369 TC-6: is false when the server keeps the position (unreachable, 401, 408, 429, 5xx)") {
            withServer { scope ->
                checkAll(Exhaustive.of(reply(500), reply(503), reply(401), reply(408), reply(429))) { response ->
                    val store = TestAudioPositionStore()
                    val participant = scope.participant(url, store, TestConnectivityMonitor(online = true))
                    store.save(randomAudioPosition())
                    script.answer(response)

                    participant.sendPending() shouldBe false
                }
            }
        }

        it("#369 TC-6: is false when the server rejects the position (4xx)") {
            withServer { scope ->
                checkAll(Exhaustive.of(reply(400), reply(404), reply(422))) { response ->
                    val store = TestAudioPositionStore()
                    val participant = scope.participant(url, store, TestConnectivityMonitor(online = true))
                    store.save(randomAudioPosition())
                    script.answer(response)

                    participant.sendPending() shouldBe false
                }
            }
        }

        it("#369 TC-6: is false while offline") {
            withServer { scope ->
                val store = TestAudioPositionStore()
                val participant = scope.participant(url, store, TestConnectivityMonitor(online = false))
                store.save(randomAudioPosition())

                participant.sendPending() shouldBe false
            }
        }

        it("#369 TC-6: keeps a position the server did not accept in the store") {
            withServer { scope ->
                val store = TestAudioPositionStore()
                val participant = scope.participant(url, store, TestConnectivityMonitor(online = true))
                store.save(randomAudioPosition())
                script.answer(500)

                participant.sendPending()

                participant.hasPending().shouldBeTrue()
            }
        }

        it("#369 TC-6: keeps a position unsent while offline in the store") {
            withServer { scope ->
                val store = TestAudioPositionStore()
                val participant = scope.participant(url, store, TestConnectivityMonitor(online = false))
                store.save(randomAudioPosition())

                participant.sendPending()

                participant.hasPending().shouldBeTrue()
            }
        }
    }
})
