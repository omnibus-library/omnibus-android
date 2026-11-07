package com.omnibus.omnibus.data.audiobook.position.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.audiobook.TempDataStore
import com.omnibus.omnibus.data.audiobook.position.AudioPosition
import com.omnibus.omnibus.data.audiobook.position.randomAudioPosition
import com.omnibus.omnibus.data.audiobook.position.randomListeningSessionReport
import com.omnibus.omnibus.data.audiobook.unreadableDataStore
import com.omnibus.omnibus.data.audiobook.unwritableDataStore
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/** Writes [values] to the store under raw string keys, as an earlier version or a corrupted file would have. */
private suspend fun DataStore<Preferences>.seed(vararg values: Pair<String, String>) {
    updateData {
        mutablePreferencesOf().apply {
            values.forEach { (name, value) -> this[stringPreferencesKey(name)] = value }
        }
    }
}

private fun storedJson(position: AudioPosition): String =
    """{"positionSeconds":${position.positionSeconds},"bookFileId":${position.bookFileId},"clientUpdatedAt":${position.clientUpdatedAt}}"""

class DefaultAudioPositionStoreTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("saving a position") {

        // TC-1
        it("TC-1: makes it the only pending write of its book (I-SAVE-4)") {
            TempDataStore().use { temp ->
                val store = DefaultAudioPositionStore(temp.dataStore)
                val position = randomAudioPosition()
                store.save(position.copy(positionSeconds = position.positionSeconds + 1))

                store.save(position)

                store.allPending() shouldBe listOf(position)
            }
        }

        // TC-2
        it("TC-2: keeps a pending write for each book (I-SAVE-4)") {
            TempDataStore().use { temp ->
                val store = DefaultAudioPositionStore(temp.dataStore)
                val first = randomAudioPosition()
                val second = randomAudioPosition().copy(bookUuid = "${first.bookUuid}-other")

                store.save(first)
                store.save(second)

                store.allPending() shouldContainExactlyInAnyOrder listOf(first, second)
            }
        }
    }

    describe("reading what is stored") {

        // TC-3: the keys and field names are a stored format, and changing one strands what is on the device
        it("TC-3: reads a position stored by an earlier version") {
            TempDataStore().use { temp ->
                val position = randomAudioPosition()
                temp.dataStore.seed("audio.cached.${position.bookUuid}" to storedJson(position))

                DefaultAudioPositionStore(temp.dataStore).cached(position.bookUuid) shouldBe position
            }
        }

        // TC-4
        it("TC-4: keeps what is stored across a restart (I-SAVE-4)") {
            TempDataStore().use { temp ->
                val position = randomAudioPosition()
                val report = randomListeningSessionReport()
                val store = DefaultAudioPositionStore(temp.dataStore)
                store.save(position)
                store.queueSessions(listOf(report))

                val reopened = DefaultAudioPositionStore(temp.reopen())

                reopened.pending(position.bookUuid) to reopened.sessions() shouldBe (position to listOf(report))
            }
        }

        context("when a position is corrupt") {
            // TC-5
            it("TC-5: skips only that book") {
                TempDataStore().use { temp ->
                    val valid = randomAudioPosition()
                    temp.dataStore.seed(
                        "audio.pending.${valid.bookUuid}-corrupt" to "{not json",
                        "audio.pending.${valid.bookUuid}" to storedJson(valid),
                    )

                    DefaultAudioPositionStore(temp.dataStore).allPending() shouldBe listOf(valid)
                }
            }
        }

        context("when the session queue is corrupt") {
            // TC-5
            it("TC-5: starts a new queue") {
                TempDataStore().use { temp ->
                    val store = DefaultAudioPositionStore(temp.dataStore)
                    val report = randomListeningSessionReport()
                    temp.dataStore.seed("audio.sessions" to "{not json")

                    store.queueSessions(listOf(report))

                    store.sessions() shouldBe listOf(report)
                }
            }
        }

        context("when the file can't be read") {
            // TC-6
            it("TC-6: reads as empty") {
                val store = DefaultAudioPositionStore(unreadableDataStore())

                store.cached(randomString()).shouldBeNull()
                store.pending(randomString()).shouldBeNull()
                store.allPending().shouldBeEmpty()
                store.sessions().shouldBeEmpty()
            }
        }

        context("when the file can't be written") {
            // TC-7
            it("TC-7: throws nothing") {
                val store = DefaultAudioPositionStore(unwritableDataStore())
                val position = randomAudioPosition()
                val report = randomListeningSessionReport()

                shouldNotThrowAny {
                    store.save(position)
                    store.clearPendingIf(position)
                    store.queueSessions(listOf(report))
                    store.removeSessions(setOf(report.clientId))
                }
            }
        }
    }

    describe("clearing a sent position") {

        context("when it is still the pending write") {
            // TC-8
            it("TC-8: clears the pending write (I-SAVE-4)") {
                TempDataStore().use { temp ->
                    val store = DefaultAudioPositionStore(temp.dataStore)
                    val sent = randomAudioPosition()
                    store.save(sent)

                    store.clearPendingIf(sent)

                    store.pending(sent.bookUuid).shouldBeNull()
                }
            }

            // TC-8
            it("TC-8: keeps the cached position (I-SAVE-4)") {
                TempDataStore().use { temp ->
                    val store = DefaultAudioPositionStore(temp.dataStore)
                    val sent = randomAudioPosition()
                    store.save(sent)

                    store.clearPendingIf(sent)

                    store.cached(sent.bookUuid) shouldBe sent
                }
            }
        }

        context("when a newer write is pending") {
            // TC-8
            it("TC-8: keeps the newer write (I-SAVE-4)") {
                TempDataStore().use { temp ->
                    val store = DefaultAudioPositionStore(temp.dataStore)
                    val sent = randomAudioPosition()
                    val newer = sent.copy(clientUpdatedAt = sent.clientUpdatedAt + 1)
                    store.save(newer)

                    store.clearPendingIf(sent)

                    store.pending(sent.bookUuid) shouldBe newer
                }
            }
        }
    }

    describe("queueing sessions") {

        // TC-9
        it("TC-9: removes only the given client ids (I-SESS-3)") {
            TempDataStore().use { temp ->
                val store = DefaultAudioPositionStore(temp.dataStore)
                val kept = randomListeningSessionReport()
                val removed = randomListeningSessionReport().copy(clientId = "${kept.clientId}-other")
                store.queueSessions(listOf(kept, removed))

                store.removeSessions(setOf(removed.clientId))

                store.sessions() shouldBe listOf(kept)
            }
        }
    }
})
