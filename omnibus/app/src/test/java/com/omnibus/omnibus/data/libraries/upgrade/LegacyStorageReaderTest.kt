package com.omnibus.omnibus.data.libraries.upgrade

import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.audiobook.position.AudioPosition
import com.omnibus.omnibus.data.audiobook.position.ListeningSessionReport
import com.omnibus.omnibus.data.audiobook.unreadableDataStore
import com.omnibus.omnibus.data.library.local.TestPreferencesDataStore
import com.omnibus.omnibus.data.progress.ReadingPosition
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class LegacyStorageReaderTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    describe("reading the legacy reading positions") {
        it("TC-46: reads each stored position under its book uuid") {
            val bookUuid = randomString()
            val store = TestPreferencesDataStore(
                preferencesOf(
                    stringPreferencesKey("position.$bookUuid") to
                        """{"cfi":"epubcfi(/6/2)","percent":42,"clientUpdatedAt":1234}""",
                ),
            )

            LegacyStorageReader.readReadingPositions(store) shouldBe listOf(
                ReadingPosition(bookUuid, cfi = "epubcfi(/6/2)", percent = 42, clientUpdatedAt = 1234L),
            )
        }

        it("TC-46: reads a percent-only position with no cfi") {
            val bookUuid = randomString()
            val store = TestPreferencesDataStore(
                preferencesOf(stringPreferencesKey("position.$bookUuid") to """{"percent":7,"clientUpdatedAt":5}"""),
            )

            LegacyStorageReader.readReadingPositions(store) shouldBe listOf(
                ReadingPosition(bookUuid, cfi = null, percent = 7, clientUpdatedAt = 5L),
            )
        }
    }

    describe("reading the legacy listening positions") {
        it("TC-46: keeps cached and pending positions apart under their book uuid") {
            val bookUuid = randomString()
            val store = TestPreferencesDataStore(
                preferencesOf(
                    stringPreferencesKey("audio.cached.$bookUuid") to
                        """{"positionSeconds":10.5,"bookFileId":3,"clientUpdatedAt":100}""",
                    stringPreferencesKey("audio.pending.$bookUuid") to
                        """{"positionSeconds":11.5,"bookFileId":4,"clientUpdatedAt":101}""",
                ),
            )

            val listening = LegacyStorageReader.readListening(store)

            listening.cached shouldBe mapOf(bookUuid to AudioPosition(bookUuid, 10.5, 3L, 100L))
            listening.pending shouldBe mapOf(bookUuid to AudioPosition(bookUuid, 11.5, 4L, 101L))
        }

        it("TC-46: keeps the queued sessions in list order") {
            val first = session("book-1", "client-1")
            val second = session("book-2", "client-2")
            val store = TestPreferencesDataStore(
                preferencesOf(stringPreferencesKey("audio.sessions") to sessionsJson(first, second)),
            )

            LegacyStorageReader.readListening(store).sessions shouldBe listOf(first, second)
        }
    }

    describe("reading the legacy playback rates") {
        it("TC-46: keeps cached and pending rates apart by book uuid") {
            val bookUuid = randomString()
            val store = TestPreferencesDataStore(
                preferencesOf(
                    doublePreferencesKey("cached:$bookUuid") to 1.25,
                    doublePreferencesKey("pending:$bookUuid") to 1.5,
                ),
            )

            val rates = LegacyStorageReader.readRates(store)

            rates.cached shouldBe mapOf(bookUuid to 1.25)
            rates.pending shouldBe mapOf(bookUuid to 1.5)
        }
    }

    describe("reading the legacy resume offers") {
        it("TC-46: reads each dismissed source clock by book uuid") {
            val bookUuid = randomString()
            val store = TestPreferencesDataStore(
                preferencesOf(longPreferencesKey("dismissed_clock:$bookUuid:audio") to 987L),
            )

            LegacyStorageReader.readDismissed(store) shouldBe mapOf(bookUuid to 987L)
        }
    }

    describe("reading a file with unreadable entries") {
        it("TC-47: drops an unreadable reading position and keeps the rest") {
            val readable = randomString()
            val corrupt = randomString()
            val store = TestPreferencesDataStore(
                preferencesOf(
                    stringPreferencesKey("position.$corrupt") to "not json",
                    stringPreferencesKey("position.$readable") to """{"percent":3,"clientUpdatedAt":9}""",
                ),
            )

            LegacyStorageReader.readReadingPositions(store) shouldBe listOf(
                ReadingPosition(readable, cfi = null, percent = 3, clientUpdatedAt = 9L),
            )
        }

        it("TC-47: ignores unknown fields in an entry") {
            val bookUuid = randomString()
            val store = TestPreferencesDataStore(
                preferencesOf(
                    stringPreferencesKey("position.$bookUuid") to
                        """{"percent":3,"clientUpdatedAt":9,"unknownField":true}""",
                ),
            )

            LegacyStorageReader.readReadingPositions(store) shouldBe listOf(
                ReadingPosition(bookUuid, cfi = null, percent = 3, clientUpdatedAt = 9L),
            )
        }

        it("TC-47: reads a corrupt session queue as empty") {
            val store = TestPreferencesDataStore(
                preferencesOf(stringPreferencesKey("audio.sessions") to "[not, a, queue"),
            )

            LegacyStorageReader.readListening(store).sessions.shouldBeEmpty()
        }
    }

    describe("reading a file that can't be read") {
        it("TC-48: reads an unreadable file as empty") {
            val store = unreadableDataStore()

            LegacyStorageReader.readAddress(store).shouldBeNull()
            LegacyStorageReader.readReadingPositions(store).shouldBeEmpty()
            LegacyStorageReader.readListening(store).sessions.shouldBeEmpty()
            LegacyStorageReader.readRates(store).cached.shouldBeEmpty()
            LegacyStorageReader.readDismissed(store).shouldBeEmpty()
        }
    }
})

private fun session(bookUuid: String, clientId: String) = ListeningSessionReport(
    bookUuid = bookUuid,
    startedAt = 1L,
    endedAt = 2L,
    progressUnits = 3L,
    clientId = clientId,
    utcOffsetMinutes = 60,
    timeZone = "Europe/Paris",
)

/** The legacy session queue JSON, in the field names the old builds wrote. */
private fun sessionsJson(vararg sessions: ListeningSessionReport): String =
    sessions.joinToString(separator = ",", prefix = "[", postfix = "]") { s ->
        "{\"bookUuid\":\"${s.bookUuid}\",\"startedAt\":${s.startedAt},\"endedAt\":${s.endedAt}," +
            "\"progressUnits\":${s.progressUnits},\"clientId\":\"${s.clientId}\"," +
            "\"utcOffsetMinutes\":${s.utcOffsetMinutes},\"timeZone\":\"${s.timeZone}\"}"
    }
