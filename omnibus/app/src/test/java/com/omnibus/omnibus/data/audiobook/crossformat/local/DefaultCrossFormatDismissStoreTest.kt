package com.omnibus.omnibus.data.audiobook.crossformat.local

import com.omnibus.omnibus.data.audiobook.TempDataStore
import com.omnibus.omnibus.data.audiobook.unreadableDataStore
import com.omnibus.omnibus.data.audiobook.unwritableDataStore
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class DefaultCrossFormatDismissStoreTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("remembering a dismissed clock") {

        // R8
        it("#166 TC-20: keeps the latest clock of a book (P-SYNC-AC6)") {
            TempDataStore().use { temp ->
                val store = DefaultCrossFormatDismissStore(temp.dataStore)
                val book = randomString()
                val latest = randomLong()
                store.store(book, randomLong())

                store.store(book, latest)

                store.dismissedClock(book) shouldBe latest
            }
        }

        // R8, P-SYNC-AC6: nothing is dismissed until the listener dismisses it, and the memory is per book
        it("#166 TC-19, TC-21: reads null for a book that was not dismissed (P-SYNC-AC6)") {
            TempDataStore().use { temp ->
                val store = DefaultCrossFormatDismissStore(temp.dataStore)
                val book = randomString()
                store.store(book, randomLong())

                store.dismissedClock("$book-other").shouldBeNull()
            }
        }

        // R8: the memory survives an app restart
        it("#166 TC-22: keeps the clock across a restart (P-SYNC-AC6)") {
            TempDataStore().use { temp ->
                val book = randomString()
                val clock = randomLong()
                DefaultCrossFormatDismissStore(temp.dataStore).store(book, clock)

                val reopened = DefaultCrossFormatDismissStore(temp.reopen())

                reopened.dismissedClock(book) shouldBe clock
            }
        }

        context("when the file can't be read") {
            // R8: an unreadable memory reads as absent, so the offer shows again
            it("#166 TC-23: reads null (P-SYNC-AC6)") {
                DefaultCrossFormatDismissStore(unreadableDataStore()).dismissedClock(randomString()).shouldBeNull()
            }
        }

        context("when the file can't be written") {
            // R8
            it("#166 TC-24: does not throw (I-SAVE-3)") {
                shouldNotThrowAny {
                    DefaultCrossFormatDismissStore(unwritableDataStore()).store(randomString(), randomLong())
                }
            }
        }
    }
})
